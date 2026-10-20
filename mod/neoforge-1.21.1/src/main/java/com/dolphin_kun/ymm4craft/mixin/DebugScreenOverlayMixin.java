package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DebugScreenOverlay.class)
public abstract class DebugScreenOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noOverlay(CallbackInfo ci) {
        if (Ymm4Craft.isActive()) ci.cancel();
    }

    @Inject(method = "showDebugScreen", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$hidden(CallbackInfoReturnable<Boolean> cir) {
        if (Ymm4Craft.isActive()) cir.setReturnValue(false);
    }
}
