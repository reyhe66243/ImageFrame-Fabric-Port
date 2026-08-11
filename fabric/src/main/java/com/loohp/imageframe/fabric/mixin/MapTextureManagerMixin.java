package com.loohp.imageframe.fabric.mixin;

import com.loohp.imageframe.fabric.client.ImageFrameClientHandler;
import net.minecraft.client.resources.MapTextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MapTextureManager.class)
public class MapTextureManagerMixin {

    @Inject(at = @At("RETURN"), method = "prepareMapTexture", cancellable = true)
    private void onPrepareMapTexture(MapId mapId, MapItemSavedData mapData, CallbackInfoReturnable<Identifier> cir) {
        if (!ImageFrameClientHandler.useNativeResMapImages) return;
        if (ImageFrameClientHandler.INSTANCE == null) return;
        Identifier hdMapId = ImageFrameClientHandler.INSTANCE.getOrRequestLoadedHdMap(mapId.id());
        if (hdMapId != null) {
            cir.setReturnValue(hdMapId);
        }
    }
}
