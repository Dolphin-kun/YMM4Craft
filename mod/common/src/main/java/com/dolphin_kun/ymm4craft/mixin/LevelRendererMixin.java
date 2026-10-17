package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private Vector4f ymm4craft$clearColor(Vector4f fogColor) {
        return SceneFeature.transparentBackground() ? new Vector4f(0, 0, 0, 0) : fogColor;
    }

    @Inject(method = "addSkyPass", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noSky(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }

    @Inject(method = "submitBlockEntities", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noBlockEntities(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }
}
