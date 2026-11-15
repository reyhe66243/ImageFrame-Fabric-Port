package com.loohp.imageframe.fabric.nms;

import com.loohp.imageframe.fabric.FabricImageMapManager.FabricImageMap;
import com.loohp.imageframe.fabric.ImageFrameMod;
import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.FilledMapItemInfo;
import com.loohp.imageframe.utils.UUIDUtils;
import net.kyori.adventure.key.Key;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Utilidades nativas de mapas y paquetes de red para Fabric 26.x (Mojang Mappings).
 * Esta clase no posee ninguna referencia a Bukkit/Spigot.
 */
public class FabricMapHelper {

    public static final int COLOR_ARRAY_LENGTH = 16384;

    private static final Field LOCKED_FIELD;
    private static final Field FIXED_FIELD;

    static {
        Field locked = null;
        try {
            locked = MapItemSavedData.class.getDeclaredField("locked");
            locked.setAccessible(true);
        } catch (Throwable t) {
            ImageFrameMod.LOGGER.warn("[ImageFrame] Could not reflect MapItemSavedData.locked field: {}", t.getMessage());
        }
        LOCKED_FIELD = locked;

        Field fixed = null;
        try {
            fixed = ItemFrame.class.getDeclaredField("fixed");
            fixed.setAccessible(true);
        } catch (Throwable t) {
            ImageFrameMod.LOGGER.warn("[ImageFrame] Could not reflect ItemFrame.fixed field: {}", t.getMessage());
        }
        FIXED_FIELD = fixed;
    }

    public static boolean isItemFrameFixed(ItemFrame itemFrame) {
        if (itemFrame == null || FIXED_FIELD == null) {
            return false;
        }
        try {
            return FIXED_FIELD.getBoolean(itemFrame);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setColors(MapItemSavedData mapData, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be " + COLOR_ARRAY_LENGTH);
        }
        mapData.colors = colors;
        if (LOCKED_FIELD != null) {
            try {
                LOCKED_FIELD.setBoolean(mapData, true);
            } catch (Throwable e) {
                ImageFrameMod.LOGGER.debug("[ImageFrame] Failed to lock MapItemSavedData: {}", e.getMessage());
            }
        }
        mapData.setDirty();
    }

    public static ClientboundMapItemDataPacket createMapPacket(int mapId, byte[] colors, Collection<MapDecoration> cursors) {
        MapItemSavedData.MapPatch mapPatch = colors == null ? null : new MapItemSavedData.MapPatch(0, 0, 128, 128, colors);
        return new ClientboundMapItemDataPacket(new MapId(mapId), (byte) 0, false, Optional.ofNullable(cursors == null ? null : new ArrayList<>(cursors)), Optional.ofNullable(mapPatch));
    }

    public static ClientboundSetEntityDataPacket createItemFrameItemChangePacket(int entityId, ItemStack itemStack, SynchedEntityData.DataValue<ItemStack> dataValue) {
        List<SynchedEntityData.DataValue<?>> dataValues = Collections.singletonList(dataValue);
        return new ClientboundSetEntityDataPacket(entityId, dataValues);
    }

    public static void sendPacket(ServerPlayer player, Packet<?> packet) {
        player.connection.send(packet);
    }

    public static Key getWorldNamespacedKey(ServerLevel serverLevel) {
        try {
            ResourceKey<Level> dimension = serverLevel.dimension();
            // Resolve ResourceKey location using reflection to be robust against all Yarn/Mojang mappings
            Object loc;
            try {
                loc = dimension.getClass().getMethod("location").invoke(dimension);
            } catch (NoSuchMethodException e) {
                loc = dimension.getClass().getMethod("getValue").invoke(dimension);
            }
            String namespace = (String) loc.getClass().getMethod("getNamespace").invoke(loc);
            String path = (String) loc.getClass().getMethod("getPath").invoke(loc);
            return Key.key(namespace, path);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public static CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack) {
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(CombinedMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.getIntOr(CombinedMapItemInfo.KEY, -1);
        if (!tag.contains(CombinedMapItemInfo.PLACEMENT_UUID_KEY) || !tag.contains(CombinedMapItemInfo.PLACEMENT_YAW_KEY)) {
            return new CombinedMapItemInfo(imageMapIndex);
        }
        float yaw = tag.getFloatOr(CombinedMapItemInfo.PLACEMENT_YAW_KEY, 0F);
        UUID uuid = UUIDUtils.fromIntArray(tag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY).get());
        return new CombinedMapItemInfo(imageMapIndex, new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
    }

    public static ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo) {
        CustomData customData = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(CombinedMapItemInfo.KEY, combinedMapItemInfo.getImageMapIndex());
        if (combinedMapItemInfo.hasPlacement()) {
            CombinedMapItemInfo.PlacementInfo placement = combinedMapItemInfo.getPlacement();
            tag.putFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY, placement.getYaw());
            tag.putIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placement.getUniqueId()));
        }
        itemStack.applyComponents(DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build());
        return itemStack;
    }

    public static FilledMapItemInfo getFilledMapItemInfo(ItemStack itemStack) {
        CustomData customData = itemStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(FilledMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.getIntOr(FilledMapItemInfo.KEY, -1);
        int mapPartIndex = tag.getIntOr(FilledMapItemInfo.INDEX_KEY, -1);
        return new FilledMapItemInfo(imageMapIndex, mapPartIndex);
    }

    public static ItemStack withFilledMapItemInfo(ItemStack itemStack, FilledMapItemInfo filledMapItemInfo) {
        CustomData customData = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putInt(FilledMapItemInfo.KEY, filledMapItemInfo.getImageMapIndex());
        tag.putInt(FilledMapItemInfo.INDEX_KEY, filledMapItemInfo.getMapPartIndex());
        itemStack.applyComponents(DataComponentPatch.builder().set(DataComponents.CUSTOM_DATA, CustomData.of(tag)).build());
        return itemStack;
    }

    public static ItemStack withInvisibleItemFrameMeta(ItemStack itemStack) {
        ItemLore itemLore = itemStack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
        List<net.minecraft.network.chat.Component> loreLines = new ArrayList<>(itemLore.lines());
        loreLines.add(0, net.minecraft.network.chat.Component.translatable("effect.minecraft.invisibility").withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(false)));
        
        CustomData customData = itemStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putBoolean("invisible", true);
        
        itemStack.applyComponents(DataComponentPatch.builder()
            .set(DataComponents.LORE, new ItemLore(loreLines))
            .set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            .build());
        return itemStack;
    }

    public static int countEmptyMaps(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(net.minecraft.world.item.Items.MAP)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean removeEmptyMaps(ServerPlayer player, int amount) {
        if (countEmptyMaps(player) < amount) {
            return false;
        }
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(net.minecraft.world.item.Items.MAP)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        player.containerMenu.broadcastChanges();
        return true;
    }

    /**
     * Gives an item to the player, dropping it at their feet if the inventory is full.
     */
    public static void giveItemOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack)) {
            net.minecraft.world.entity.item.ItemEntity dropped = new net.minecraft.world.entity.item.ItemEntity(
                    player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
            player.level().addFreshEntity(dropped);
        }
    }

    public static ItemStack createMapItem(FabricImageMap map, int tileIndex) {
        ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
        mapItem.set(DataComponents.MAP_ID, new MapId(map.mapIds.get(tileIndex)));
        withFilledMapItemInfo(mapItem, new FilledMapItemInfo(map.index, tileIndex));

        String format = ImageFrameMod.instance != null ? ImageFrameMod.instance.getMapItemFormat() : null;
        if (format != null && !format.trim().isEmpty()) {
            String formattedName = format.replace('&', '§')
                    .replace("{ImageID}", String.valueOf(map.index))
                    .replace("{Name}", map.name)
                    .replace("{X}", String.valueOf(tileIndex % map.width))
                    .replace("{Y}", String.valueOf(tileIndex / map.width))
                    .replace("{Width}", String.valueOf(map.width))
                    .replace("{Height}", String.valueOf(map.height));
            mapItem.set(DataComponents.CUSTOM_NAME, Component.literal(formattedName));
        }
        return mapItem;
    }

    /**
     * Fills empty item frames with the given map's tiles.
     * Spigot parity: fixed or occupied frames are left untouched and their map item is given to the player instead.
     *
     * @return true if at least one frame was already occupied or fixed
     */
    public static boolean fillItemFrames(ServerPlayer player, List<ItemFrame> frames, FabricImageMap map) {
        boolean occupiedFound = false;
        int tiles = Math.min(frames.size(), map.mapIds.size());
        for (int i = 0; i < tiles; i++) {
            ItemFrame frame = frames.get(i);
            ItemStack mapItem = createMapItem(map, i);

            if (isItemFrameFixed(frame) || !frame.getItem().isEmpty()) {
                giveItemOrDrop(player, mapItem);
                occupiedFound = true;
                continue;
            }
            frame.setItem(mapItem);
        }
        return occupiedFound;
    }

    /**
     * Fills empty item frames with the given map IDs.
     * Spigot parity: occupied frames are left untouched and their map item is given to the player instead.
     *
     * @return true if at least one frame was already occupied
     */
    public static boolean fillItemFrames(ServerPlayer player, List<ItemFrame> frames, List<Integer> mapIds) {
        boolean occupiedFound = false;
        int tiles = Math.min(frames.size(), mapIds.size());
        for (int i = 0; i < tiles; i++) {
            ItemFrame frame = frames.get(i);
            ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
            mapItem.set(DataComponents.MAP_ID, new MapId(mapIds.get(i)));

            if (isItemFrameFixed(frame) || !frame.getItem().isEmpty()) {
                giveItemOrDrop(player, mapItem);
                occupiedFound = true;
                continue;
            }
            frame.setItem(mapItem);
        }
        return occupiedFound;
    }
}
