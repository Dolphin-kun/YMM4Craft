package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noGrab(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ci.cancel();
    }

    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayCursorX(Window window, CallbackInfoReturnable<Double> cir) {
        double[] cursor = ReplayPlayback.screenCursor(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        if (cursor != null) cir.setReturnValue(cursor[0]);
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayCursorY(Window window, CallbackInfoReturnable<Double> cir) {
        double[] cursor = ReplayPlayback.screenCursor(window.getGuiScaledWidth(), window.getGuiScaledHeight());
        if (cursor != null) cir.setReturnValue(cursor[1]);
    }

    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noRelease(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ci.cancel();
    }
}
