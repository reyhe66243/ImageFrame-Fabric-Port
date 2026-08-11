package com.loohp.imageframe.fabric.mixin;

import com.loohp.imageframe.fabric.client.ImageFrameClientHandler;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(MapRenderer.class)
public class MapRendererMixin {

    private static Method ifSetAtlasTextureIdMethod;
    private static boolean ifMethodSearched = false;

    @Inject(at = @At("TAIL"), method = "extractRenderState")
    public void extractRenderState(MapId mapId, MapItemSavedData mapData, MapRenderState mapRenderState, CallbackInfo ci) {
        if (!ImageFrameClientHandler.useNativeResMapImages) return;
        if (mapRenderState.texture == null) return;
        if (ImageFrameClientHandler.INSTANCE == null) return;
        
        Identifier hdMapId = ImageFrameClientHandler.INSTANCE.getOrRequestLoadedHdMap(mapId.id());
        
        if (hdMapId != null) {
            mapRenderState.texture = hdMapId;

            // ImmediatelyFast compatibility: clear the atlas texture ID so ImmediatelyFast does not override UV coordinates
            try {
                if (!ifMethodSearched) {
                    ifMethodSearched = true;
                    try {
                        ifSetAtlasTextureIdMethod = mapRenderState.getClass().getMethod("immediatelyFast$setAtlasTextureId", Identifier.class);
                    } catch (NoSuchMethodException ignored) {
                        // ImmediatelyFast is not installed
                    }
                }
                if (ifSetAtlasTextureIdMethod != null) {
                    ifSetAtlasTextureIdMethod.invoke(mapRenderState, (Object) null);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}

