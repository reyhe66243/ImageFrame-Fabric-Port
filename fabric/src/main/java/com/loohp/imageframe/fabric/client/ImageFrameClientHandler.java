package com.loohp.imageframe.fabric.client;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.loohp.imageframe.fabric.payload.*;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImageFrameClientHandler implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("imageframe-client");
    public static ImageFrameClientHandler INSTANCE;

    private final AtomicBoolean currentServerSupported = new AtomicBoolean(false);
    private final Int2ObjectMap<Optional<Identifier>> loadedHdImages = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<Map<Integer, Identifier>> loadedHdFrameTextures = new Int2ObjectOpenHashMap<>();
    private final Int2IntMap activeMapFrameIndex = new Int2IntOpenHashMap();
    private final Int2ObjectMap<Optional<ImageMapData>> imageMapData = new Int2ObjectOpenHashMap<>();
    private final Cache<Integer, MultipartHdMapInfo> pendingMultipart = CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.of(10, ChronoUnit.SECONDS)).build();

    public static boolean useNativeResMapImages = true;
    public static boolean previewMapsInTooltip = true;
    public static boolean previewPaintingsInTooltip = true;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        LOGGER.info("[ImageFrame] Client-side HD rendering initialized with Multi-Frame GIF sync.");

        // Auto-disable ImmediatelyFast map_atlas_generation for HD map compatibility
        try {
            Class<?> clazz = Class.forName("net.raphimc.immediatelyfast.ImmediatelyFast");
            Object runtimeConfig = clazz.getField("runtimeConfig").get(null);
            if (runtimeConfig != null) {
                java.lang.reflect.Field field = runtimeConfig.getClass().getField("map_atlas_generation");
                field.setBoolean(runtimeConfig, false);
                LOGGER.info("[ImageFrame] Disabled ImmediatelyFast map_atlas_generation for HD map compatibility.");
            }
        } catch (Throwable ignored) {
        }

        ClientPlayNetworking.registerGlobalReceiver(ClientboundAcknowledgement.ID, (payload, context) -> {
            context.client().execute(() -> {
                ServerboundAcknowledgement reply = new ServerboundAcknowledgement(payload.id());
                if (ClientPlayNetworking.canSend(ServerboundAcknowledgement.ID)) {
                    ClientPlayNetworking.send(reply);
                }
                currentServerSupported.set(true);
                LOGGER.debug("[ImageFrame] Handshake complete! Server supports HD.");
                
                try {
                    net.minecraft.client.gui.components.toasts.ToastManager toastManager = getToastManager(Minecraft.getInstance());
                    if (toastManager != null) {
                        net.minecraft.client.gui.components.toasts.SystemToast.add(
                            toastManager,
                            net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.NARRATOR_TOGGLE,
                            net.minecraft.network.chat.Component.literal("ImageFrame HD").withStyle(net.minecraft.ChatFormatting.GOLD),
                            net.minecraft.network.chat.Component.literal("Server with HD support detected.")
                        );
                    }
                } catch (Throwable ignored) {
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientboundHdImageResponse.ID, (payload, context) -> {
            context.client().execute(() -> {
                LOGGER.debug("[ImageFrame] Received HD Image Response for map {} (frame {}/{}). Accepted={}", payload.mapId(), payload.frameIndex(), payload.totalFrames(), payload.requestAccepted());
                if (useNativeResMapImages) {
                    try {
                        int mapId = payload.mapId();
                        int frameIndex = payload.frameIndex();
                        int totalFrames = payload.totalFrames();

                        if (payload.requestAccepted()) {
                            Optional<Integer> opt = payload.multipart();
                            byte[] data = payload.data();
                            if (opt.isPresent()) {
                                MultipartHdMapInfo info = new MultipartHdMapInfo();
                                info.put(0, data);
                                pendingMultipart.put(opt.get(), info);
                            } else {
                                if (data.length > 0) {
                                    NativeImage nativeImage = NativeImage.read(data);
                                    Identifier id = Identifier.fromNamespaceAndPath("imageframe", "hdmap_" + mapId + "_f" + frameIndex);
                                    DynamicTexture tex = new DynamicTexture(id::getPath, nativeImage);
                                    Minecraft.getInstance().getTextureManager().register(id, tex);
                                    
                                    Map<Integer, Identifier> frameMap = loadedHdFrameTextures.computeIfAbsent(mapId, k -> new HashMap<>());
                                    frameMap.put(frameIndex, id);
                                    loadedHdImages.put(mapId, Optional.of(id));

                                    // Pre-fetch missing frames if animated GIF
                                    if (totalFrames > 1 && frameMap.size() < totalFrames) {
                                        for (int f = 0; f < totalFrames; f++) {
                                            if (!frameMap.containsKey(f)) {
                                                if (currentServerSupported.get() || ClientPlayNetworking.canSend(ServerboundHdImageRequest.ID)) {
                                                    ClientPlayNetworking.send(new ServerboundHdImageRequest(mapId, f));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            loadedHdImages.remove(mapId);
                            loadedHdFrameTextures.remove(mapId);
                        }
                    } catch (IOException e) {
                        LOGGER.error("[ImageFrame] Error loading HD image: ", e);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientboundHdImageMultipartResponse.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (useNativeResMapImages) {
                    try {
                        int mapId = payload.mapId();
                        int multipartId = payload.multipart();
                        MultipartHdMapInfo info = pendingMultipart.getIfPresent(multipartId);
                        if (info != null) {
                            byte[] data = payload.data();
                            int index = payload.index();
                            if (data.length > 0) {
                                info.put(index, data);
                            }
                            if (payload.end()) {
                                info.setLastIndex(index);
                            }
                            if (info.isCompleted()) {
                                pendingMultipart.invalidate(multipartId);
                                NativeImage nativeImage = NativeImage.read(info.complete());
                                Identifier id = Identifier.fromNamespaceAndPath("imageframe", "hdmap_" + mapId + "_f0");
                                DynamicTexture tex = new DynamicTexture(id::getPath, nativeImage);
                                Minecraft.getInstance().getTextureManager().register(id, tex);
                                
                                Map<Integer, Identifier> frameMap = loadedHdFrameTextures.computeIfAbsent(mapId, k -> new HashMap<>());
                                frameMap.put(0, id);
                                loadedHdImages.put(mapId, Optional.of(id));
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.error("[ImageFrame] Error loading multipart HD image: ", e);
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientboundImageUpdatedSignal.ID, (payload, context) -> {
            context.client().execute(() -> {
                for (int index : payload.indexes()) {
                    imageMapData.remove(index);
                }
                int signalFrameIndex = payload.frameIndex();
                if (signalFrameIndex >= 0) {
                    // Frame advance signal for animated maps
                    for (int mapId : payload.mapIds()) {
                        activeMapFrameIndex.put(mapId, signalFrameIndex);
                    }
                } else {
                    // Invalidation signal
                    for (int mapId : payload.mapIds()) {
                        activeMapFrameIndex.remove(mapId);
                        Map<Integer, Identifier> frameMap = loadedHdFrameTextures.remove(mapId);
                        if (frameMap != null) {
                            for (Identifier id : frameMap.values()) {
                                Minecraft.getInstance().getTextureManager().release(id);
                            }
                        }
                        Optional<Identifier> id = loadedHdImages.remove(mapId);
                        if (id != null && id.isPresent()) {
                            Minecraft.getInstance().getTextureManager().release(id.get());
                        }
                    }
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientboundImageMapDetailsResponse.ID, (payload, context) -> {
            context.client().execute(() -> {
                if (payload.width() > 0 && payload.height() > 0) {
                    imageMapData.put(payload.index(), Optional.of(new ImageMapData(payload.width(), payload.height(), payload.mapIds())));
                }
            });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Minecraft.getInstance().execute(() -> {
                imageMapData.clear();
                activeMapFrameIndex.clear();
                for (Map<Integer, Identifier> frameMap : loadedHdFrameTextures.values()) {
                    for (Identifier id : frameMap.values()) {
                        Minecraft.getInstance().getTextureManager().release(id);
                    }
                }
                loadedHdFrameTextures.clear();
                for (int mapId : new IntOpenHashSet(loadedHdImages.keySet())) {
                    Optional<Identifier> id = loadedHdImages.remove(mapId);
                    if (id != null && id.isPresent()) {
                        Minecraft.getInstance().getTextureManager().release(id.get());
                    }
                }
                currentServerSupported.set(false);
            });
        });
    }

    public Identifier getOrRequestLoadedHdMap(int mapId) {
        Map<Integer, Identifier> frameMap = loadedHdFrameTextures.get(mapId);
        if (frameMap != null && !frameMap.isEmpty()) {
            int activeFrame = activeMapFrameIndex.get(mapId);
            Identifier id = frameMap.get(activeFrame);
            if (id != null) {
                return id;
            }
            return frameMap.values().iterator().next();
        }

        Optional<Identifier> result = loadedHdImages.get(mapId);
        if (result == null) {
            if (currentServerSupported.get() || ClientPlayNetworking.canSend(ServerboundHdImageRequest.ID)) {
                ServerboundHdImageRequest request = new ServerboundHdImageRequest(mapId, 0);
                ClientPlayNetworking.send(request);
                loadedHdImages.put(mapId, Optional.empty());
                LOGGER.info("[ImageFrame] Sent HD Request for map {}", mapId);
            }
            return null;
        }
        return result.orElse(null);
    }

    @SuppressWarnings("OptionalAssignedToNull")
    public ImageMapData getOrRequestImageMapData(int index) {
        Optional<ImageMapData> result = imageMapData.get(index);
        if (result == null) {
            if (currentServerSupported.get() || ClientPlayNetworking.canSend(ServerboundImageMapDetailsRequest.ID)) {
                ServerboundImageMapDetailsRequest request = new ServerboundImageMapDetailsRequest(index);
                ClientPlayNetworking.send(request);
                imageMapData.put(index, Optional.empty());
            }
            return null;
        }
        return result.orElse(null);
    }

    private static net.minecraft.client.gui.components.toasts.ToastManager getToastManager(Minecraft client) {
        try {
            Object gui = client.gui;
            try {
                return (net.minecraft.client.gui.components.toasts.ToastManager) gui.getClass().getMethod("toastManager").invoke(gui);
            } catch (NoSuchMethodException e) {
                return (net.minecraft.client.gui.components.toasts.ToastManager) gui.getClass().getMethod("getToastManager").invoke(gui);
            }
        } catch (Throwable t) {
            return null;
        }
    }
}
