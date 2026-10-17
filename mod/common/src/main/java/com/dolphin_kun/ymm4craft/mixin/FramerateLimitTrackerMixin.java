package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CoreCommands;
import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitTrackerMixin {
    @Inject(method = "getThrottleReason", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noThrottle(CallbackInfoReturnable<FramerateLimitTracker.FramerateThrottleReason> cir) {
        if (!Ymm4Craft.isActive()) return;
        cir.setReturnValue(FramerateLimitTracker.FramerateThrottleReason.NONE);
    }

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$limitToStream(CallbackInfoReturnable<Integer> cir) {
        if (!Ymm4Craft.isActive()) return;
        cir.setReturnValue(CoreCommands.renderFpsLimit());
    }
}
