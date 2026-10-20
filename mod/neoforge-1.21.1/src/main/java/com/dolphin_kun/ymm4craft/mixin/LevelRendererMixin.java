package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;clear(IZ)V", ordinal = 0))
    private void ymm4craft$clearColor(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) RenderSystem.clearColor(0, 0, 0, 0);
    }

    @Inject(method = {"renderSky", "renderSectionLayer", "renderClouds", "renderSnowAndRain", "renderWorldBorder"}, at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noBackground(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }
}
