package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
    @Shadow
    @Final
    private long window;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ymm4craft$hideWindow(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        GLFW.glfwHideWindow(window);
    }

    @ModifyVariable(method = "onFocus", at = @At("HEAD"), argsOnly = true)
    private boolean ymm4craft$neverFocused(boolean focused) {
        if (!Ymm4Craft.isActive()) return focused;
        return false;
    }

    @ModifyVariable(method = "updateVsync", at = @At("HEAD"), argsOnly = true)
    private boolean ymm4craft$noVsyncWhileLinked(boolean vsync) {
        return vsync && !Ymm4Craft.isActive();
    }
}
