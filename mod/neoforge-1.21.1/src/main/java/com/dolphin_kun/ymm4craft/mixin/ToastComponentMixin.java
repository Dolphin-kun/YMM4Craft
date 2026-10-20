package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noToasts(Toast toast, CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ci.cancel();
    }
}
