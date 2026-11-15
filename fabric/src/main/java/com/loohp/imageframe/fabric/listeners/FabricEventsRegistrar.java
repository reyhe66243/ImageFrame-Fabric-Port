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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static class CloudState {
        final net.minecraft.world.entity.AreaEffectCloud cloud;
        int conversionsRemaining;

        CloudState(net.minecraft.world.entity.AreaEffectCloud cloud, int conversionsRemaining) {
            this.cloud = cloud;
            this.conversionsRemaining = conversionsRemaining;
        }
    }

    private static final Map<UUID, BrokenFrameInfo> brokenFrames = new ConcurrentHashMap<>();
    private static final Map<UUID, CloudState> activeClouds = new ConcurrentHashMap<>();

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
        // Event 1: Item frame interaction (placement or update)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (entity instanceof ItemFrame && player instanceof ServerPlayer) {
                ItemFrame itemFrame = (ItemFrame) entity;
                ServerPlayer serverPlayer = (ServerPlayer) player;
                ItemStack handItem = player.getItemInHand(hand);

                // Selection check
                if (FabricImageMapManager.getInstance().isSelectionActive(serverPlayer.getUUID())) {
                    FabricImageMapManager.getInstance().handleItemFrameInteraction(serverPlayer, itemFrame);
                    return InteractionResult.SUCCESS;
                }

                if (FabricMapHelper.isItemFrameFixed(itemFrame)) {
                    return InteractionResult.PASS;
                }

                // Placement of Combined maps (supports both modern FILLED_MAP and legacy PAPER items)
                if (!handItem.isEmpty()) {
                    CombinedMapItemInfo combInfo = FabricMapHelper.getCombinedMapItemInfo(handItem);
                    if (combInfo != null) {
                        FabricImageMap map = FabricImageMapManager.getInstance().getMaps().values().stream()
                            .filter(m -> m.index == combInfo.getImageMapIndex())
                            .findFirst()
                            .orElse(null);
                        if (map != null) {
                            boolean placed = placeCombinedMap(serverPlayer, itemFrame, map);
                            if (placed && !serverPlayer.isCreative()) {
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
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (entity instanceof ItemFrame && player instanceof ServerPlayer) {
                ItemFrame itemFrame = (ItemFrame) entity;
                ServerPlayer serverPlayer = (ServerPlayer) player;
                ItemStack frameItem = itemFrame.getItem();

                // Selection check
                if (FabricImageMapManager.getInstance().isSelectionActive(serverPlayer.getUUID())) {
                    FabricImageMapManager.getInstance().handleItemFrameInteraction(serverPlayer, itemFrame);
                    return InteractionResult.SUCCESS;
                }

                if (FabricMapHelper.isItemFrameFixed(itemFrame)) {
                    return InteractionResult.PASS;
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
            } else if (entity instanceof net.minecraft.world.entity.AreaEffectCloud) {
                net.minecraft.world.entity.AreaEffectCloud cloud = (net.minecraft.world.entity.AreaEffectCloud) entity;
                int maxConversions = ImageFrameMod.instance.getInvisibleFrameMaxConversions();
                if (maxConversions != 0) {
                    activeClouds.put(cloud.getUUID(), new CloudState(cloud, maxConversions));
                }
            }
        });

        // Event 4b: Entity unload events (Splash potion invisibility conversion and AreaEffectCloud cleanup)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion
                    && !(entity instanceof net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion)) {
                if (entity.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.DISCARDED) {
                    net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion potion =
                            (net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion) entity;
                    if (hasInvisibilityEffect(potion.getItem())) {
                        int maxConversions = ImageFrameMod.instance.getInvisibleFrameMaxConversions();
                        if (maxConversions != 0) {
                            convertNearbyItemFrames(world, potion.getBoundingBox().inflate(4.0, 2.0, 4.0), maxConversions);
                        }
                    }
                }
            } else if (entity instanceof net.minecraft.world.entity.AreaEffectCloud) {
                activeClouds.remove(entity.getUUID());
            }
        });

        // Event 5: Global server tick events (Grindstone prevention and AreaEffectCloud potion conversions)
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

            // B. Convert nearby item frames around tracked AreaEffectClouds
            if (!activeClouds.isEmpty()) {
                Iterator<Map.Entry<UUID, CloudState>> it = activeClouds.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, CloudState> entry = it.next();
                    CloudState state = entry.getValue();
                    net.minecraft.world.entity.AreaEffectCloud cloud = state.cloud;
                    if (cloud == null || cloud.isRemoved() || !(cloud.level() instanceof ServerLevel)) {
                        it.remove();
                        continue;
                    }
                    if (cloud.tickCount % 5 == 0 && cloudHasInvisibility(cloud)) {
                        ServerLevel world = (ServerLevel) cloud.level();
                        int converted = convertNearbyItemFrames(world, cloud.getBoundingBox(), state.conversionsRemaining);
                        if (state.conversionsRemaining > 0) {
                            state.conversionsRemaining -= converted;
                            if (state.conversionsRemaining <= 0) {
                                it.remove();
                            }
                        }
                    }
                }
            }

            // C. Update visible maps cache on the main thread for network optimization
            if (server.getTickCount() % 5 == 0 && FabricImageMapManager.getInstance().hasAnimatedMaps()) {
                FabricImageMapManager.getInstance().updateVisibleMapsCache(server);
            }
        });
    }

    private static boolean hasInvisibilityEffect(ItemStack potionStack) {
        if (potionStack == null || potionStack.isEmpty()) {
            return false;
        }
        net.minecraft.world.item.alchemy.PotionContents contents = potionStack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (net.minecraft.world.effect.MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect() == net.minecraft.world.effect.MobEffects.INVISIBILITY) {
                return true;
            }
        }
        return false;
    }

    private static int convertNearbyItemFrames(ServerLevel world, net.minecraft.world.phys.AABB box, int maxConversions) {
        if (maxConversions == 0) {
            return 0;
        }
        int converted = 0;
        for (net.minecraft.world.entity.item.ItemEntity itemEntity : world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
            if (maxConversions > 0 && converted >= maxConversions) {
                break;
            }
            ItemStack stack = itemEntity.getItem();
            if (stack.isEmpty() || (!stack.is(Items.ITEM_FRAME) && !stack.is(Items.GLOW_ITEM_FRAME))) {
                continue;
            }
            if (FabricImageMapManager.isInvisibleItemFrame(stack)) {
                continue;
            }
            int count = stack.getCount();
            int remaining = maxConversions > 0 ? maxConversions - converted : count;
            if (remaining <= 0) {
                break;
            }
            int toConvert = Math.min(count, remaining);
            if (maxConversions > 0 && count > remaining) {
                ItemStack convertedStack = stack.copy();
                convertedStack.setCount(toConvert);
                FabricMapHelper.withInvisibleItemFrameMeta(convertedStack);

                stack.shrink(toConvert);
                itemEntity.setItem(stack.copy());

                net.minecraft.world.entity.item.ItemEntity extraEntity = new net.minecraft.world.entity.item.ItemEntity(
                        world, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(), convertedStack);
                extraEntity.setDeltaMovement(0, 0, 0);
                world.addFreshEntity(extraEntity);
            } else {
                ItemStack invisibleStack = FabricMapHelper.withInvisibleItemFrameMeta(stack.copy());
                itemEntity.setItem(invisibleStack);
            }
            world.playSound(null, itemEntity.getX(), itemEntity.getY(), itemEntity.getZ(),
                    net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            converted += toConvert;
        }
        return converted;
    }

    private static boolean cloudHasInvisibility(net.minecraft.world.entity.AreaEffectCloud cloud) {
        try {
            net.minecraft.world.item.alchemy.PotionContents contents = cloud.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
            if (contents != null) {
                for (net.minecraft.world.effect.MobEffectInstance effect : contents.getAllEffects()) {
                    if (effect.getEffect() == net.minecraft.world.effect.MobEffects.INVISIBILITY) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Exception ignored) {
        }
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

    private static List<ItemFrame> collectItemFrameGrid(ServerLevel world, BlockPos topLeft, Direction facing, Direction rowDir, Direction colDir, int width, int height) {
        List<ItemFrame> list = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                BlockPos targetPos = topLeft.relative(colDir, x).relative(rowDir, y);
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

    private static boolean placeCombinedMap(ServerPlayer player, ItemFrame originFrame, FabricImageMap map) {
        Direction facing = originFrame.getDirection();

        // Spigot parity: support walls, floors (UP) and ceilings (DOWN)
        Direction rowDir; // direction rows advance (top -> bottom of the image)
        Direction colDir; // direction columns advance (left -> right of the image)
        if (facing.getAxis().isHorizontal()) {
            rowDir = Direction.DOWN;
            colDir = facing.getCounterClockWise();
        } else {
            Direction viewDir = Direction.fromYRot(player.getYRot());
            if (viewDir.getAxis().isVertical()) {
                viewDir = Direction.NORTH;
            }
            colDir = viewDir.getClockWise();
            rowDir = facing == Direction.UP ? viewDir.getOpposite() : viewDir;
        }

        ServerLevel level = (ServerLevel) player.level();

        // Spigot parity: the clicked frame can be any cell of the grid, try them all
        List<ItemFrame> grid = null;
        boolean foundCompleteGrid = false;
        outer:
        for (int cy = 0; cy < map.height; cy++) {
            for (int cx = 0; cx < map.width; cx++) {
                BlockPos topLeft = originFrame.getPos().relative(colDir, -cx).relative(rowDir, -cy);
                List<ItemFrame> candidate = collectItemFrameGrid(level, topLeft, facing, rowDir, colDir, map.width, map.height);
                if (candidate == null) {
                    continue;
                }
                foundCompleteGrid = true;
                boolean empty = true;
                for (ItemFrame frame : candidate) {
                    if (FabricMapHelper.isItemFrameFixed(frame) || !frame.getItem().isEmpty()) {
                        empty = false;
                        break;
                    }
                }
                if (empty) {
                    grid = candidate;
                    break outer;
                }
            }
        }

        if (grid == null) {
            if (foundCompleteGrid) {
                player.sendSystemMessage(Component.literal("§c[ImageFrame] One or more item frames in the grid already contain items."));
            } else {
                player.sendSystemMessage(Component.literal("§c[ImageFrame] There are not enough empty adjacent item frames. A grid of " + map.width + "x" + map.height + " is required."));
            }
            return false;
        }

        UUID placementUuid = UUID.randomUUID();
        float yaw = player.getYRot();

        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                int tileIndex = y * map.width + x;
                ItemFrame frame = grid.get(tileIndex);

                ItemStack mapItem = FabricMapHelper.createMapItem(map, tileIndex);
                CombinedMapItemInfo combinedInfo = new CombinedMapItemInfo(map.index, new CombinedMapItemInfo.PlacementInfo(yaw, placementUuid));
                FabricMapHelper.withCombinedMapItemInfo(mapItem, combinedInfo);

                frame.setItem(mapItem);
            }
        }

        player.sendSystemMessage(Component.literal("§a[ImageFrame] Combined map \"" + map.name + "\" placed successfully!"));
        return true;
    }

    private static void handleCombinedBreak(ServerPlayer player, ItemFrame brokenFrame, ItemStack mapItem) {
        net.minecraft.world.item.component.CustomData customData = mapItem.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return;
        net.minecraft.nbt.CompoundTag tag = customData.copyTag();
        Optional<int[]> optUuid = tag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY);
        if (optUuid.isEmpty()) return;

        UUID placementUuid = UUIDUtils.fromIntArray(optUuid.get());
        int mapIndex = tag.getInt(CombinedMapItemInfo.KEY).orElse(-1);

        FabricImageMap map = FabricImageMapManager.getInstance().getMaps().values().stream()
            .filter(m -> m.index == mapIndex)
            .findFirst()
            .orElse(null);
        if (map == null) return;

        ServerLevel world = (ServerLevel) brokenFrame.level();
        List<ItemFrame> linkedFrames = new ArrayList<>();
        double searchRadius = Math.max(map.width, map.height) + 2.0;
        for (ItemFrame frame : world.getEntitiesOfClass(ItemFrame.class, brokenFrame.getBoundingBox().inflate(searchRadius))) {
            if (FabricMapHelper.isItemFrameFixed(frame)) {
                continue;
            }
            ItemStack item = frame.getItem();
            if (!item.isEmpty() && item.is(Items.FILLED_MAP)) {
                net.minecraft.world.item.component.CustomData frameData = item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                if (frameData != null) {
                    net.minecraft.nbt.CompoundTag fTag = frameData.copyTag();
                    Optional<int[]> optFrameUuid = fTag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY);
                    if (optFrameUuid.isPresent()) {
                        UUID fUuid = UUIDUtils.fromIntArray(optFrameUuid.get());
                        if (placementUuid.equals(fUuid)) {
                            linkedFrames.add(frame);
                        }
                    }
                }
            }
        }

        for (ItemFrame frame : linkedFrames) {
            frame.setItem(ItemStack.EMPTY);
        }

        ItemStack combinedItem = new ItemStack(Items.FILLED_MAP);
        combinedItem.set(net.minecraft.core.component.DataComponents.MAP_ID, new MapId(map.mapIds.get(0)));
        combinedItem.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6ImageMap: " + map.name));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("§7" + com.loohp.imageframe.fabric.language.FabricLanguageManager.getInstance().get("imageframe.settings.combined_map_item.lore.1", map.width, map.height)));
        lore.add(Component.literal("§8ImageID: " + map.index));
        lore.add(Component.literal("§8Size: " + map.width + "x" + map.height));
        combinedItem.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lore));
        
        CombinedMapItemInfo info = new CombinedMapItemInfo(map.index);
        FabricMapHelper.withCombinedMapItemInfo(combinedItem, info);

        brokenFrame.spawnAtLocation((ServerLevel) brokenFrame.level(), combinedItem);
    }
}

