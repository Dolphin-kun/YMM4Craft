package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumWorldRendererMixin {
    @Inject(method = "drawChunkLayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void ymm4craft$noTerrain(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }

    @Inject(method = "extractBlockEntities", at = @At("HEAD"), cancellable = true, require = 0)
    private void ymm4craft$noBlockEntities(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }
}
