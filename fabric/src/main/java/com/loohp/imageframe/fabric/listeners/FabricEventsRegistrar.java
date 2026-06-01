package com.loohp.imageframe.fabric.listeners;

import com.loohp.imageframe.fabric.FabricImageMapManager;
import com.loohp.imageframe.fabric.FabricImageMapManager.FabricImageMap;
import com.loohp.imageframe.fabric.ImageFrameMod;
import com.loohp.imageframe.fabric.nms.FabricMapHelper;
import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.FilledMapItemInfo;
import com.loohp.imageframe.utils.UUIDUtils;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers and routes game events to achieve parity with the original plugin.
 */
public class FabricEventsRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger("imageframe-events");

    private static class BrokenFrameInfo {
        final double x, y, z;
        final long time;
        BrokenFrameInfo(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = System.currentTimeMillis();
        }
    }

    private static final Map<UUID, BrokenFrameInfo> brokenFrames = new ConcurrentHashMap<>();

    public static void registerBrokenInvisibleFrame(UUID uuid, double x, double y, double z) {
        brokenFrames.put(uuid, new BrokenFrameInfo(x, y, z));
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                brokenFrames.remove(uuid);
            }
        }, 2000L);
    }

    public static void register() {
        // Event 1: Player joins the server (send active map packets)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            LOGGER.info("[ImageFrame] Sending active map updates to player: " + player.getScoreboardName());
            
            for (FabricImageMap map : FabricImageMapManager.getInstance().getMaps().values()) {
                if (map.framesColors != null && !map.framesColors.isEmpty()) {
                    byte[][] currentColors = map.framesColors.get(map.currentFrameIndex);
                    for (int i = 0; i < map.mapIds.size(); i++) {
                        int mapId = map.mapIds.get(i);
                        FabricMapHelper.sendPacket(player, FabricMapHelper.createMapPacket(mapId, currentColors[i], null));
                    }
                }
            }
        });

        // Event 2: Item frame interaction (placement or update)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof ItemFrame && player instanceof ServerPlayer) {
                ItemFrame itemFrame = (ItemFrame) entity;
                ServerPlayer serverPlayer = (ServerPlayer) player;
                ItemStack handItem = player.getItemInHand(hand);

                // Selection check
                if (FabricImageMapManager.getInstance().isSelectionActive(serverPlayer.getUUID())) {
                    FabricImageMapManager.getInstance().handleItemFrameInteraction(serverPlayer, itemFrame);
                    return InteractionResult.SUCCESS;
                }

                // Placement of Combined maps (Paper)
                if (!handItem.isEmpty() && handItem.is(Items.PAPER)) {
                    CombinedMapItemInfo combInfo = FabricMapHelper.getCombinedMapItemInfo(handItem);
                    if (combInfo != null) {
                        FabricImageMap map = FabricImageMapManager.getInstance().getMaps().values().stream()
                            .filter(m -> m.index == combInfo.getImageMapIndex())
                            .findFirst()
                            .orElse(null);
                        if (map != null) {
                            placeCombinedMap(serverPlayer, itemFrame, map);
                            if (!serverPlayer.isCreative()) {
                                handItem.shrink(1);
                            }
                            return InteractionResult.SUCCESS;
                        }
                    }
                }

                // Update invisible item frames
                if (FabricImageMapManager.getInstance().isInvisibleFrame(itemFrame.getUUID())) {
                    ((ServerLevel) serverPlayer.level()).getServer().execute(() -> {
                        FabricImageMapManager.getInstance().updateInvisibleItemFrame(itemFrame);
                    });
                }
            }
            return InteractionResult.PASS;
        });

        // Event 3: Attack or breaking of item frames
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (entity instanceof ItemFrame && player instanceof ServerPlayer) {
                ItemFrame itemFrame = (ItemFrame) entity;
                ServerPlayer serverPlayer = (ServerPlayer) player;
                ItemStack frameItem = itemFrame.getItem();

                // Selection check
                if (FabricImageMapManager.getInstance().isSelectionActive(serverPlayer.getUUID())) {
                    FabricImageMapManager.getInstance().handleItemFrameInteraction(serverPlayer, itemFrame);
                    return InteractionResult.SUCCESS;
                }

                // Break linked combined frames
                if (!frameItem.isEmpty() && frameItem.is(Items.FILLED_MAP)) {
                    net.minecraft.world.item.component.CustomData frameData = frameItem.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if (frameData != null) {
                        net.minecraft.nbt.CompoundTag fTag = frameData.copyTag();
                        if (fTag.contains(CombinedMapItemInfo.PLACEMENT_UUID_KEY)) {
                            handleCombinedBreak(serverPlayer, itemFrame, frameItem);
                            return InteractionResult.SUCCESS;
                        }
                    }
                }

                // Break or remove invisible item frames
                UUID uuid = itemFrame.getUUID();
                if (FabricImageMapManager.getInstance().isInvisibleFrame(uuid)) {
                    double x = itemFrame.getX();
                    double y = itemFrame.getY();
                    double z = itemFrame.getZ();

                    ((ServerLevel) serverPlayer.level()).getServer().execute(() -> {
                        if (!itemFrame.isAlive()) {
                            registerBrokenInvisibleFrame(uuid, x, y, z);
                            FabricImageMapManager.getInstance().removeInvisibleFrame(uuid);
                        } else {
                            FabricImageMapManager.getInstance().updateInvisibleItemFrame(itemFrame);
                        }
                    });
                }
            }
            return InteractionResult.PASS;
        });

        // Event 4: Entity load and spawn events (for invisible item frames)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ItemFrame) {
                ItemFrame itemFrame = (ItemFrame) entity;
                UUID uuid = itemFrame.getUUID();
                if (FabricImageMapManager.getInstance().isInvisibleFrame(uuid)) {
                    FabricImageMapManager.getInstance().updateInvisibleItemFrame(itemFrame);
                } else {
                    double x = itemFrame.getX();
                    double y = itemFrame.getY();
                    double z = itemFrame.getZ();
                    for (ServerPlayer player : world.getServer().getPlayerList().getPlayers()) {
                        if (player.level() == world && player.distanceToSqr(x, y, z) < 36.0) {
                            if (FabricImageMapManager.isInvisibleItemFrame(player.getMainHandItem()) ||
                                FabricImageMapManager.isInvisibleItemFrame(player.getOffhandItem())) {
                                FabricImageMapManager.getInstance().addInvisibleFrame(uuid);
                                FabricImageMapManager.getInstance().updateInvisibleItemFrame(itemFrame);
                                break;
                            }
                        }
                    }
                }
            } else if (entity instanceof net.minecraft.world.entity.item.ItemEntity) {
                net.minecraft.world.entity.item.ItemEntity itemEntity = (net.minecraft.world.entity.item.ItemEntity) entity;
                ItemStack stack = itemEntity.getItem();
                if (!stack.isEmpty() && (stack.is(Items.ITEM_FRAME) || stack.is(Items.GLOW_ITEM_FRAME))) {
                    if (!FabricImageMapManager.isInvisibleItemFrame(stack)) {
                        long now = System.currentTimeMillis();
                        for (Map.Entry<UUID, BrokenFrameInfo> entry : brokenFrames.entrySet()) {
                            BrokenFrameInfo info = entry.getValue();
                            if (now - info.time < 2000 && itemEntity.distanceToSqr(info.x, info.y, info.z) < 2.25) {
                                ItemStack invisibleStack = FabricMapHelper.withInvisibleItemFrameMeta(stack);
                                itemEntity.setItem(invisibleStack);
                                brokenFrames.remove(entry.getKey());
                                break;
                            }
                        }
                    }
                }
            }
        });

        // Event 5: Global server tick events (Grindstone prevention and potion conversions)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // A. Prevent combination in Grindstones
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.containerMenu instanceof net.minecraft.world.inventory.GrindstoneMenu) {
                    net.minecraft.world.inventory.GrindstoneMenu menu = (net.minecraft.world.inventory.GrindstoneMenu) player.containerMenu;
                    ItemStack slot0 = menu.getSlot(0).getItem();
                    ItemStack slot1 = menu.getSlot(1).getItem();
                    if (FabricImageMapManager.isInvisibleItemFrame(slot0) || FabricImageMapManager.isInvisibleItemFrame(slot1)) {
                        menu.getSlot(2).set(ItemStack.EMPTY);
                    }
                }
            }

            // B. Scan and convert splash potions / area effect clouds in all worlds
            for (ServerLevel world : server.getAllLevels()) {
                for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
                    if (entity.getClass().getSimpleName().contains("Potion")) {
                        if (entity.isRemoved()) {
                            try {
                                ItemStack potionStack = (ItemStack) entity.getClass().getMethod("getItem").invoke(entity);
                                if (potionStack != null && !potionStack.isEmpty()) {
                                    net.minecraft.world.item.alchemy.PotionContents contents = potionStack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
                                    if (contents != null) {
                                        boolean hasInvis = false;
                                        for (net.minecraft.world.effect.MobEffectInstance effect : contents.getAllEffects()) {
                                            if (effect.getEffect() == net.minecraft.world.effect.MobEffects.INVISIBILITY) {
                                                hasInvis = true;
                                                break;
                                            }
                                        }
                                        if (hasInvis) {
                                            net.minecraft.world.phys.AABB box = entity.getBoundingBox().inflate(4.0, 2.0, 4.0);
                                            for (net.minecraft.world.entity.item.ItemEntity itemEntity : world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
                                                ItemStack stack = itemEntity.getItem();
                                                if (!stack.isEmpty() && (stack.is(Items.ITEM_FRAME) || stack.is(Items.GLOW_ITEM_FRAME))) {
                                                    if (!FabricImageMapManager.isInvisibleItemFrame(stack)) {
                                                        ItemStack invisibleStack = FabricMapHelper.withInvisibleItemFrameMeta(stack);
                                                        itemEntity.setItem(invisibleStack);
                                                        world.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                LOGGER.error("Error processing ThrownPotion.getItem via reflection: ", ex);
                            }
                        }
                    } else if (entity instanceof net.minecraft.world.entity.AreaEffectCloud) {
                        net.minecraft.world.entity.AreaEffectCloud cloud = (net.minecraft.world.entity.AreaEffectCloud) entity;
                        if (cloudHasInvisibility(cloud)) {
                            net.minecraft.world.phys.AABB box = cloud.getBoundingBox();
                            for (net.minecraft.world.entity.item.ItemEntity itemEntity : world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
                                ItemStack stack = itemEntity.getItem();
                                if (!stack.isEmpty() && (stack.is(Items.ITEM_FRAME) || stack.is(Items.GLOW_ITEM_FRAME))) {
                                    if (!FabricImageMapManager.isInvisibleItemFrame(stack)) {
                                        ItemStack invisibleStack = FabricMapHelper.withInvisibleItemFrameMeta(stack);
                                        itemEntity.setItem(invisibleStack);
                                        world.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // C. Update visible maps cache on the main thread for network optimization
            FabricImageMapManager.getInstance().updateVisibleMapsCache(server);
        });
    }

    private static boolean cloudHasInvisibility(net.minecraft.world.entity.AreaEffectCloud cloud) {
        try {
            Object basePotion = null;
            try {
                basePotion = cloud.getClass().getMethod("getPotion").invoke(cloud);
            } catch (Exception ex) {
                try {
                    java.lang.reflect.Field field = net.minecraft.world.entity.AreaEffectCloud.class.getDeclaredField("potion");
                    field.setAccessible(true);
                    basePotion = field.get(cloud);
                } catch (Exception e) {}
            }
            if (basePotion != null) {
                Object potion = basePotion.getClass().getMethod("value").invoke(basePotion);
                if (potion != null) {
                    List<?> effects = (List<?>) potion.getClass().getMethod("getEffects").invoke(potion);
                    if (effects != null) {
                        for (Object obj : effects) {
                            if (obj instanceof net.minecraft.world.effect.MobEffectInstance) {
                                net.minecraft.world.effect.MobEffectInstance effect = (net.minecraft.world.effect.MobEffectInstance) obj;
                                if (effect.getEffect() == net.minecraft.world.effect.MobEffects.INVISIBILITY) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error getting base potion from AreaEffectCloud: ", e);
        }

        try {
            List<?> customEffects = null;
            try {
                customEffects = (List<?>) cloud.getClass().getMethod("getEffects").invoke(cloud);
            } catch (Exception ex) {
                try {
                    java.lang.reflect.Field field = net.minecraft.world.entity.AreaEffectCloud.class.getDeclaredField("effects");
                    field.setAccessible(true);
                    customEffects = (List<?>) field.get(cloud);
                } catch (Exception e) {}
            }
            if (customEffects != null) {
                for (Object obj : customEffects) {
                    if (obj instanceof net.minecraft.world.effect.MobEffectInstance) {
                        net.minecraft.world.effect.MobEffectInstance effect = (net.minecraft.world.effect.MobEffectInstance) obj;
                        if (effect.getEffect() == net.minecraft.world.effect.MobEffects.INVISIBILITY) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error getting potion effects from AreaEffectCloud: ", e);
        }
        return false;
    }

    private static List<ItemFrame> findItemFrameGrid(ServerLevel world, ItemFrame originFrame, Direction facing, Direction upDir, Direction leftDir, int width, int height) {
        List<ItemFrame> list = new ArrayList<>();
        BlockPos originPos = originFrame.getPos();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                BlockPos targetPos = originPos.relative(leftDir, -x).relative(upDir, -y);
                ItemFrame found = getItemFrameAt(world, targetPos, facing);
                if (found == null) return null;
                list.add(found);
            }
        }
        return list;
    }

    private static ItemFrame getItemFrameAt(ServerLevel world, BlockPos pos, Direction facing) {
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos);
        for (ItemFrame frame : world.getEntitiesOfClass(ItemFrame.class, box)) {
            if (frame.getDirection() == facing) {
                return frame;
            }
        }
        return null;
    }

    private static void placeCombinedMap(ServerPlayer player, ItemFrame originFrame, FabricImageMap map) {
        Direction facing = originFrame.getDirection();
        if (!facing.getAxis().isHorizontal()) {
            player.sendSystemMessage(Component.literal("§c[ImageFrame] Combined maps can only be placed on vertical walls."));
            return;
        }

        Direction upDir = Direction.UP;
        Direction leftDir = facing.getCounterClockWise();

        List<ItemFrame> grid = findItemFrameGrid((ServerLevel) player.level(), originFrame, facing, upDir, leftDir, map.width, map.height);
        if (grid == null) {
            player.sendSystemMessage(Component.literal("§c[ImageFrame] There are not enough empty adjacent item frames. A grid of " + map.width + "x" + map.height + " is required."));
            return;
        }

        for (ItemFrame frame : grid) {
            if (!frame.getItem().isEmpty()) {
                player.sendSystemMessage(Component.literal("§c[ImageFrame] One or more item frames in the grid already contain items."));
                return;
            }
        }

        UUID placementUuid = UUID.randomUUID();
        float yaw = player.getYRot();

        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                int tileIndex = y * map.width + x;
                ItemFrame frame = grid.get(tileIndex);
                int mapId = map.mapIds.get(tileIndex);

                ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                mapItem.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(mapId));

                net.minecraft.world.item.component.CustomData customData = mapItem.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
                net.minecraft.nbt.CompoundTag tag = customData.copyTag();
                tag.putInt(FilledMapItemInfo.KEY, map.index);
                tag.putInt(FilledMapItemInfo.INDEX_KEY, tileIndex);

                tag.putInt(CombinedMapItemInfo.KEY, map.index);
                tag.putFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY, yaw);
                tag.putIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placementUuid));

                mapItem.applyComponents(net.minecraft.core.component.DataComponentPatch.builder()
                    .set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag))
                    .build());

                frame.setItem(mapItem);
            }
        }

        player.sendSystemMessage(Component.literal("§a[ImageFrame] Combined map \"" + map.name + "\" placed successfully!"));
    }

    private static void handleCombinedBreak(ServerPlayer player, ItemFrame brokenFrame, ItemStack mapItem) {
        net.minecraft.world.item.component.CustomData customData = mapItem.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        if (!tag.contains(CombinedMapItemInfo.PLACEMENT_UUID_KEY)) return;

        UUID placementUuid = UUIDUtils.fromIntArray(tag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY).get());
        int mapIndex = tag.getInt(CombinedMapItemInfo.KEY).orElse(-1);

        FabricImageMap map = FabricImageMapManager.getInstance().getMaps().values().stream()
            .filter(m -> m.index == mapIndex)
            .findFirst()
            .orElse(null);
        if (map == null) return;

        ServerLevel world = (ServerLevel) brokenFrame.level();
        List<ItemFrame> linkedFrames = new ArrayList<>();
        for (net.minecraft.world.entity.Entity entity : world.getAllEntities()) {
            if (entity instanceof ItemFrame) {
                ItemFrame frame = (ItemFrame) entity;
                ItemStack item = frame.getItem();
                if (!item.isEmpty() && item.is(Items.FILLED_MAP)) {
                    net.minecraft.world.item.component.CustomData frameData = item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if (frameData != null) {
                        net.minecraft.nbt.CompoundTag fTag = frameData.copyTag();
                        if (fTag.contains(CombinedMapItemInfo.PLACEMENT_UUID_KEY)) {
                            UUID fUuid = UUIDUtils.fromIntArray(fTag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY).get());
                            if (placementUuid.equals(fUuid)) {
                                linkedFrames.add(frame);
                            }
                        }
                    }
                }
            }
        }

        for (ItemFrame frame : linkedFrames) {
            frame.setItem(ItemStack.EMPTY);
        }

        ItemStack combinedPaper = new ItemStack(Items.PAPER);
        combinedPaper.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6Combined: " + map.name));
        
        net.minecraft.world.item.component.CustomData pData = combinedPaper.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        net.minecraft.nbt.CompoundTag pTag = pData.copyTag();
        pTag.putInt(CombinedMapItemInfo.KEY, map.index);
        combinedPaper.applyComponents(net.minecraft.core.component.DataComponentPatch.builder()
            .set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(pTag))
            .build());

        brokenFrame.spawnAtLocation((ServerLevel) brokenFrame.level(), combinedPaper);
    }
}

