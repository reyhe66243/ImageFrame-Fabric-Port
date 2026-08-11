package com.loohp.imageframe.fabric.mixin;

import com.loohp.imageframe.fabric.client.ImageFrameClientHandler;
import com.loohp.imageframe.fabric.client.tooltip.FilledMapTooltipComponent;
import com.loohp.imageframe.fabric.client.tooltip.ImageMapTooltipComponent;
import com.loohp.imageframe.fabric.client.tooltip.PaintingTooltipComponent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.saveddata.maps.MapId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Item.class)
public class ItemMixin {

    @Unique
    private static final String KEY = "CombinedImageMap";

    @Inject(at = @At("HEAD"), cancellable = true, method = "getTooltipImage")
    public void getTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        Item item = stack.getItem();
        if (ImageFrameClientHandler.previewPaintingsInTooltip && Items.PAINTING.equals(item)) {
            Holder<PaintingVariant> paintingVariantComponent = stack.get(DataComponents.PAINTING_VARIANT);
            if (paintingVariantComponent != null) {
                PaintingVariant paintingVariant = paintingVariantComponent.value();
                cir.setReturnValue(Optional.of(new PaintingTooltipComponent(paintingVariant)));
                cir.cancel();
            }
        } else if (ImageFrameClientHandler.previewMapsInTooltip && Items.PAPER.equals(item)) {
            CustomData customDataComponent = stack.get(DataComponents.CUSTOM_DATA);
            if (customDataComponent != null) {
                CompoundTag tag = customDataComponent.copyTag();
                Optional<Integer> optIndex = tag.getInt(KEY);
                if (optIndex.isPresent()) {
                    cir.setReturnValue(Optional.of(new ImageMapTooltipComponent(optIndex.get())));
                    cir.cancel();
                }
            }
        } else if (ImageFrameClientHandler.previewMapsInTooltip && Items.FILLED_MAP.equals(item)) {
            MapId mapIdComponent = stack.get(DataComponents.MAP_ID);
            if (mapIdComponent != null) {
                cir.setReturnValue(Optional.of(new FilledMapTooltipComponent(mapIdComponent.id())));
                cir.cancel();
            }
        }
    }
}
