package com.loohp.imageframe.fabric.network;

import com.loohp.imageframe.fabric.FabricImageMapManager;
import com.loohp.imageframe.fabric.FabricImageMapManager.FabricImageMap;
import com.loohp.imageframe.fabric.ImageFrameMod;
import com.loohp.imageframe.fabric.payload.*;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles all ImageFrame HD protocol networking on the server side.
 * Compatible with the official ImageFrameClient mod.
 */
public class ImageFrameNetworkHandler {

    private static final int MAX_PAYLOAD_SIZE = 900_000; // ~900KB per packet chunk
    private static final Set<UUID> hdClients = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger multipartIdCounter = new AtomicInteger(0);

    /**
     * Register all payload types. Must be called during ModInitializer.onInitialize().
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(ClientboundAcknowledgement.ID, ClientboundAcknowledgement.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ServerboundAcknowledgement.ID, ServerboundAcknowledgement.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ServerboundHdImageRequest.ID, ServerboundHdImageRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundHdImageResponse.ID, ClientboundHdImageResponse.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundHdImageMultipartResponse.ID, ClientboundHdImageMultipartResponse.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(ClientboundImageUpdatedSignal.ID, ClientboundImageUpdatedSignal.CODEC);

        PayloadTypeRegistry.serverboundPlay().register(ServerboundImageMapDetailsRequest.ID, ServerboundImageMapDetailsRequest.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ClientboundImageMapDetailsResponse.ID, ClientboundImageMapDetailsResponse.CODEC);
    }

    /**
     * Register server-side packet handlers. Must be called during ModInitializer.onInitialize().
     */
    public static void registerServerHandlers() {
        // Handle handshake acknowledgement from client
        ServerPlayNetworking.registerGlobalReceiver(ServerboundAcknowledgement.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            hdClients.add(player.getUUID());
            ImageFrameMod.LOGGER.info("[ImageFrame] Player {} has ImageFrame HD client mod.", player.getScoreboardName());
        });

        // Handle HD image requests
        ServerPlayNetworking.registerGlobalReceiver(ServerboundHdImageRequest.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            handleHdImageRequest(payload, player);
        });

        // Handle image map details requests
        ServerPlayNetworking.registerGlobalReceiver(ServerboundImageMapDetailsRequest.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            int index = payload.index();
            handleImageMapDetailsRequest(player, index);
        });

        // Clean up on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            hdClients.remove(handler.getPlayer().getUUID());
        });

        // Send handshake on join only if client supports the custom channel
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            if (ServerPlayNetworking.canSend(player, ClientboundAcknowledgement.ID)) {
                long handshakeId = ThreadLocalRandom.current().nextLong();
                ServerPlayNetworking.send(player, new ClientboundAcknowledgement(handshakeId));
            }
        });
    }

    private static void handleHdImageRequest(ServerboundHdImageRequest payload, ServerPlayer player) {
        int mapId = payload.mapId();
        int reqFrameIndex = payload.frameIndex();

        FabricImageMapManager manager = FabricImageMapManager.getInstance();
        FabricImageMap map = manager.getMapByMapId(mapId);

        if (map == null) {
            // Map not found, reject
            ServerPlayNetworking.send(player, new ClientboundHdImageResponse(mapId, false, new byte[0], Optional.empty(), 0, 1));
            return;
        }

        int tileIndex = manager.getTileIndex(map, mapId);
        if (tileIndex < 0) {
            ServerPlayNetworking.send(player, new ClientboundHdImageResponse(mapId, false, new byte[0], Optional.empty(), 0, 1));
            return;
        }

        // Read the PNG file from disk
        File dataFolder = new File(ImageFrameMod.instance.getConfigFolder(), "data");
        File mapFolder = new File(dataFolder, String.valueOf(map.index));

        // Determine the PNG filename
        String pngFilename;
        int numFrames = 1;
        int frameIndex = 0;

        if (map.isAnimated) {
            numFrames = map.framesColors != null ? map.framesColors.size() : 1;
            frameIndex = (reqFrameIndex >= 0 && reqFrameIndex < numFrames) ? reqFrameIndex : map.currentFrameIndex;
            pngFilename = (tileIndex * numFrames + frameIndex) + ".png";
        } else {
            pngFilename = tileIndex + ".png";
        }

        File pngFile = new File(mapFolder, pngFilename);
        if (!pngFile.exists()) {
            ServerPlayNetworking.send(player, new ClientboundHdImageResponse(mapId, false, new byte[0], Optional.empty(), frameIndex, numFrames));
            return;
        }

        try {
            byte[] imageData = Files.readAllBytes(pngFile.toPath());

            if (imageData.length <= MAX_PAYLOAD_SIZE) {
                // Small enough to send in one packet
                ServerPlayNetworking.send(player, new ClientboundHdImageResponse(mapId, true, imageData, Optional.empty(), frameIndex, numFrames));
            } else {
                // Need multipart transfer
                int multipartId = multipartIdCounter.incrementAndGet();
                int totalChunks = (int) Math.ceil((double) imageData.length / MAX_PAYLOAD_SIZE);

                // Send initial response with first chunk and multipart ID
                byte[] firstChunk = new byte[Math.min(MAX_PAYLOAD_SIZE, imageData.length)];
                System.arraycopy(imageData, 0, firstChunk, 0, firstChunk.length);
                ServerPlayNetworking.send(player, new ClientboundHdImageResponse(mapId, true, firstChunk, Optional.of(multipartId), frameIndex, numFrames));

                // Send remaining chunks
                for (int i = 1; i < totalChunks; i++) {
                    int offset = i * MAX_PAYLOAD_SIZE;
                    int length = Math.min(MAX_PAYLOAD_SIZE, imageData.length - offset);
                    byte[] chunk = new byte[length];
                    System.arraycopy(imageData, offset, chunk, 0, length);
                    boolean isLast = (i == totalChunks - 1);
                    ServerPlayNetworking.send(player, new ClientboundHdImageMultipartResponse(mapId, multipartId, i, chunk, isLast));
                }
            }
        } catch (IOException e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error reading HD image file: ", e);
            ServerPlayNetworking.send(player, new ClientboundHdImageResponse(mapId, false, new byte[0], Optional.empty(), frameIndex, numFrames));
        }
    }

    private static void handleImageMapDetailsRequest(ServerPlayer player, int index) {
        FabricImageMapManager manager = FabricImageMapManager.getInstance();
        FabricImageMap map = manager.getMapByIndex(index);

        if (map == null) {
            // Send empty/invalid response
            ServerPlayNetworking.send(player, new ClientboundImageMapDetailsResponse(index, 0, 0, new IntArrayList()));
            return;
        }

        IntList mapIds = new IntArrayList(map.mapIds);
        ServerPlayNetworking.send(player, new ClientboundImageMapDetailsResponse(index, map.width, map.height, mapIds));
    }

    /**
     * Returns true if the given player has the ImageFrame HD client mod.
     */
    public static boolean isHdClient(UUID playerUuid) {
        return hdClients.contains(playerUuid);
    }

    /**
     * Broadcast an image update signal to all connected HD clients.
     * Call this when a map is created, refreshed, or deleted.
     */
    public static void broadcastImageUpdate(FabricImageMap map) {
        if (map == null) return;

        IntSet indexes = new IntOpenHashSet();
        indexes.add(map.index);

        IntSet mapIds = new IntOpenHashSet(map.mapIds);

        ClientboundImageUpdatedSignal signal = new ClientboundImageUpdatedSignal(indexes, mapIds);

        try {
            net.minecraft.server.players.PlayerList playerList = FabricImageMapManager.getPlayerListSourceStatic();
            if (playerList != null) {
                for (ServerPlayer player : playerList.getPlayers()) {
                    if (hdClients.contains(player.getUUID())) {
                        ServerPlayNetworking.send(player, signal);
                    }
                }
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error broadcasting image update: ", e);
        }
    }

    /**
     * Broadcast an animation frame update to all connected HD clients.
     */
    public static void broadcastFrameUpdate(FabricImageMap map, int frameIndex) {
        if (map == null) return;

        IntSet mapIds = new IntOpenHashSet(map.mapIds);
        ClientboundImageUpdatedSignal signal = new ClientboundImageUpdatedSignal(new IntOpenHashSet(), mapIds, frameIndex);

        try {
            net.minecraft.server.players.PlayerList playerList = FabricImageMapManager.getPlayerListSourceStatic();
            if (playerList != null) {
                for (ServerPlayer player : playerList.getPlayers()) {
                    if (hdClients.contains(player.getUUID())) {
                        ServerPlayNetworking.send(player, signal);
                    }
                }
            }
        } catch (Exception e) {
            ImageFrameMod.LOGGER.error("[ImageFrame] Error broadcasting frame update: ", e);
        }
    }
}
