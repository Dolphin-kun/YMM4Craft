package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.WorldBorderRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({CloudRenderer.class, WeatherEffectRenderer.class, WorldBorderRenderer.class})
public abstract class CloudRendererMixin {
    @Inject(method = {"render", "renderOit"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void ymm4craft$noBackground(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }
}
