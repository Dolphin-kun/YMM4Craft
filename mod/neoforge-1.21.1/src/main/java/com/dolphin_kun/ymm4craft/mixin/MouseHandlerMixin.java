package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noGrab(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ci.cancel();
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noRelease(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ci.cancel();
    }
}
