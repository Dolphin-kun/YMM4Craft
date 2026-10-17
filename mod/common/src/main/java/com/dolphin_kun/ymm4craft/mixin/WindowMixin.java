package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.mojang.blaze3d.platform.Window;
import org.lwjgl.sdl.SDLVideo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Window.class)
public abstract class WindowMixin {
    @ModifyArg(
            method = "createWindow",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuBackend;createWindow(Ljava/lang/String;IIJ)J"),
            index = 3)
    private long ymm4craft$hideWindow(long flags) {
        if (!Ymm4Craft.isActive()) return flags;
        return flags | SDLVideo.SDL_WINDOW_HIDDEN;
    }

    @ModifyVariable(method = "onFocus", at = @At("HEAD"), argsOnly = true)
    private boolean ymm4craft$neverFocused(boolean focused) {
        if (!Ymm4Craft.isActive()) return focused;
        return false;
    }
}
