package com.loohp.imageframe.fabric.nms;

import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.FilledMapItemInfo;
import com.loohp.imageframe.utils.UUIDUtils;
import net.kyori.adventure.key.Key;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

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

    public static void setColors(MapItemSavedData mapData, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be " + COLOR_ARRAY_LENGTH);
        }
        mapData.colors = colors;
        try {
            java.lang.reflect.Field lockedField = MapItemSavedData.class.getDeclaredField("locked");
            lockedField.setAccessible(true);
            lockedField.setBoolean(mapData, true);
        } catch (Exception e) {
            e.printStackTrace();
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
}
