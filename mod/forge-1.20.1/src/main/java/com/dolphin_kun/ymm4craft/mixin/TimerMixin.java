package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Timer.class)
public abstract class TimerMixin {
    @Inject(method = "advanceTime", at = @At("RETURN"), cancellable = true)
    private void ymm4craft$replayTime(long timeMs, CallbackInfoReturnable<Integer> cir) {
        int pending = ReplayPlayback.pendingTicks();
        if (pending > cir.getReturnValueI()) cir.setReturnValue(pending);
    }
}
