package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
    @Inject(method = "getPlayerMode", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$hudMode(CallbackInfoReturnable<GameType> cir) {
        GameType recorded = ReplayPlayback.hudGameMode();
        if (recorded != null) cir.setReturnValue(recorded);
    }

    @Inject(method = "canHurtPlayer", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$hudCanHurt(CallbackInfoReturnable<Boolean> cir) {
        GameType recorded = ReplayPlayback.hudGameMode();
        if (recorded != null) cir.setReturnValue(recorded.isSurvival());
    }

    @Inject(method = "hasExperience", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$hudExperience(CallbackInfoReturnable<Boolean> cir) {
        GameType recorded = ReplayPlayback.hudGameMode();
        if (recorded != null) cir.setReturnValue(recorded.isSurvival());
    }
}
