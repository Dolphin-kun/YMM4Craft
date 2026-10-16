package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$mute(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!Ymm4Craft.isActive()) return;
        ReplayPlayback.onSoundPlayed(sound);
        cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
