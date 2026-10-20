package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.dolphin_kun.ymm4craft.core.CoreCommands;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "pauseIfInactive", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noAutoPause(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ci.cancel();
    }

    @Inject(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;endFrame()V"))
    private void ymm4craft$onFrameRendered(boolean advanceGameTime, CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        CoreCommands.onFrameRendered();
    }

    @ModifyArg(method = "renderFrame", at = @At(value = "INVOKE",
            target = "Lcom/mojang/renderpearl/api/device/GpuSurface$PresentMode;getSupportedVsyncMode(Ljava/util/Collection;Z)Lcom/mojang/renderpearl/api/device/GpuSurface$PresentMode;"),
            index = 1)
    private boolean ymm4craft$noVsyncWhileLinked(boolean vsync) {
        return vsync && !Ymm4Craft.isActive();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void ymm4craft$replayTick(CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        ReplayPlayback.onTickStart((Minecraft) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void ymm4craft$endTick(CallbackInfo ci) {
        ClientHooks.fireEndTick((Minecraft) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void ymm4craft$stopping(CallbackInfo ci) {
        ClientHooks.fireStopping((Minecraft) (Object) this);
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    private void ymm4craft$runCommands(boolean renderLevel, CallbackInfo ci) {
        CommandRegistry.runPending();
    }

    @ModifyConstant(method = "runTick", constant = @Constant(intValue = 10))
    private int ymm4craft$replayTicksPerFrame(int original) {
        return ReplayPlayback.pendingTicks() > 0 ? Math.max(original, ReplayPlayback.FAST_FORWARD_PER_FRAME) : original;
    }
}
