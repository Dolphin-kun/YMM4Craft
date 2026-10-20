package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noParticles(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$skipTick(CallbackInfo ci) {
        if (ReplayPlayback.skipsParticles()) ci.cancel();
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$skipAdd(Particle particle, CallbackInfo ci) {
        if (ReplayPlayback.skipsParticles()) ci.cancel();
    }
}
