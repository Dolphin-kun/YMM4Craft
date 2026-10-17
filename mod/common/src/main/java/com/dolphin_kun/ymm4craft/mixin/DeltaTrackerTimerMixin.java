package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @Inject(method = "advanceGameTime", at = @At("RETURN"), cancellable = true)
    private void ymm4craft$fastForward(long currentMs, CallbackInfoReturnable<Integer> cir) {
        int pending = ReplayPlayback.pendingTicks();
        if (pending > cir.getReturnValueI()) cir.setReturnValue(pending);
    }

    @Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayPartialTick(boolean ignoreFrozenGame, CallbackInfoReturnable<Float> cir) {
        Float partial = ReplayPlayback.partialTick();
        if (partial != null) cir.setReturnValue(partial);
    }
}
