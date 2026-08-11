package com.loohp.imageframe.fabric.client.tooltip;

import com.loohp.imageframe.fabric.client.ImageFrameClientHandler;
import com.loohp.imageframe.fabric.client.ImageMapData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

public class ImageMapTooltipComponent implements MapTooltipComponent {

    private final float BG_INSET = 1F;
    private final float PAD_PX = 2F;

    private final Identifier background = Identifier.withDefaultNamespace("textures/map/map_background.png");
    private final MapRenderState mapRenderState;
    private final int index;

    public ImageMapTooltipComponent(int index) {
        this.mapRenderState = new MapRenderState();
        this.index = index;
    }

    @Override
    public int getHeight(Font font) {
        return ImageFrameClientHandler.INSTANCE == null || ImageFrameClientHandler.INSTANCE.getOrRequestImageMapData(index) == null ? 0 : 66;
    }

    @Override
    public int getWidth(Font font) {
        if (ImageFrameClientHandler.INSTANCE == null) return 0;
        ImageMapData data = ImageFrameClientHandler.INSTANCE.getOrRequestImageMapData(index);
        if (data == null) {
            return 0;
        }
        return getWidth(data);
    }

    public int getWidth(ImageMapData data) {
        int cols = data.width();
        int rows = data.height();
        float availableH = 64f - BG_INSET * 2f;
        float usableH = Math.max(0f, availableH - PAD_PX * 2f);
        int tileSizePxInt = Math.max(1, (int) Math.floor(usableH / rows));
        float totalWidth = cols * tileSizePxInt + PAD_PX * 2f + BG_INSET * 2f;
        return (int) Math.ceil(totalWidth);
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || ImageFrameClientHandler.INSTANCE == null) {
            return;
        }
        ImageMapData imageMapData = ImageFrameClientHandler.INSTANCE.getOrRequestImageMapData(index);
        if (imageMapData == null) {
            return;
        }
        int gridCols = imageMapData.width();
        int gridRows = imageMapData.height();
        float availableH = 64f - BG_INSET * 2f;
        float usableH = Math.max(0f, availableH - PAD_PX * 2f);
        int tileSizePxInt = Math.max(1, (int) Math.floor(usableH / gridRows));
        int mapWidth = getWidth(imageMapData);
        graphics.blit(RenderPipelines.GUI_TEXTURED, background, x, y, 0, 0, mapWidth, 64, mapWidth, 64);
        float gridW = gridCols * tileSizePxInt + PAD_PX * 2f;
        float gridH = gridRows * tileSizePxInt + PAD_PX * 2f;
        float originX = x + Math.round((mapWidth - gridW) * 0.5f);
        float originY = y + Math.round((64f - gridH) * 0.5f);
        float overlap = 0.25f;
        float tileScale = (tileSizePxInt + overlap) / 128f;
        MapRenderer mapRenderer = client.getMapRenderer();
        Matrix3x2fStack matrix = graphics.pose();
        for (int i = 0; i < imageMapData.mapIds().size(); i++) {
            MapId id = new MapId(imageMapData.mapIds().getInt(i));
            int col = i % gridCols;
            int row = i / gridCols;
            MapItemSavedData data = level.getMapData(id);
            if (data == null) {
                continue;
            }
            mapRenderer.extractRenderState(id, data, mapRenderState);
            float drawX = originX + PAD_PX + col * tileSizePxInt;
            float drawY = originY + PAD_PX + row * tileSizePxInt;
            matrix.pushMatrix();
            matrix.translate(drawX, drawY);
            matrix.translate(-overlap * 0.5f, -overlap * 0.5f);
            matrix.scale(tileScale, tileScale);
            graphics.map(mapRenderState);
            matrix.popMatrix();
        }
    }
}
