package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.moulberry.flashback.Flashback", remap = false)
public abstract class FlashbackMixin {
    @Inject(method = "startRecordingReplay()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void ymm4craft$noRecording(CallbackInfo ci) {
        if (Ymm4Craft.isActive()) ci.cancel();
    }
}
