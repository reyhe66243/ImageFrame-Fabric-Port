package com.loohp.imageframe.fabric.client.tooltip;

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

public class FilledMapTooltipComponent implements MapTooltipComponent {

    private final Identifier background = Identifier.withDefaultNamespace("textures/map/map_background.png");
    private final MapId id;
    private final MapRenderState mapRenderState;

    public FilledMapTooltipComponent(int mapId) {
        this.id = new MapId(mapId);
        this.mapRenderState = new MapRenderState();
    }

    @Override
    public int getHeight(Font font) {
        return 66;
    }

    @Override
    public int getWidth(Font font) {
        return 66;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, background, x, y, 0.0F, 0.0F, 64, 64, 64, 64);
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            return;
        }
        MapItemSavedData data = level.getMapData(id);
        if (data == null) {
            return;
        }
        Matrix3x2fStack matrix = graphics.pose();
        matrix.pushMatrix();
        matrix.translate(x + 3.2F, y + 3.2F);
        matrix.scale(0.45F, 0.45F);
        MapRenderer mapRenderer = client.getMapRenderer();
        mapRenderer.extractRenderState(id, data, mapRenderState);
        graphics.map(mapRenderState);
        matrix.popMatrix();
    }
}
