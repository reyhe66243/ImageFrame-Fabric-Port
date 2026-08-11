package com.loohp.imageframe.fabric.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

public record PaintingTooltipComponent(PaintingVariant paintingVariant) implements MapTooltipComponent {

    @Override
    public int getHeight(Font font) {
        return shouldFillWidth() ? Math.round(getHeightToWidthRatio() * 66F) : 66;
    }

    @Override
    public int getWidth(Font font) {
        return shouldFillWidth() ? 66 : Math.round(getWidthToHeightRatio() * 66F);
    }

    public boolean shouldFillWidth() {
        return paintingVariant.width() < paintingVariant.height();
    }

    public float getWidthToHeightRatio() {
        return (float) paintingVariant.width() / (float) paintingVariant.height();
    }

    public float getHeightToWidthRatio() {
        return (float) paintingVariant.height() / (float) paintingVariant.width();
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor guiGraphics) {
        Identifier painting = paintingVariant.assetId().withPrefix("textures/painting/").withSuffix(".png");
        int w = shouldFillWidth() ? 64 : Math.round(getWidthToHeightRatio() * 64F);
        int h = shouldFillWidth() ? Math.round(getHeightToWidthRatio() * 64F) : 64;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, painting, x, y, 0.0F, 0.0F, w, h, w, h);
    }
}
