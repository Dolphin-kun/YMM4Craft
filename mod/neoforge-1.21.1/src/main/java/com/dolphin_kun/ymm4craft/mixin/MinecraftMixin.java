package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.dolphin_kun.ymm4craft.core.CoreCommands;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.entity.Entity;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "runTick",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;unbindWrite()V", ordinal = 0))
    private void ymm4craft$onFrameRendered(boolean renderLevel, CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        CoreCommands.onFrameRendered();
    }

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$limitToStream(CallbackInfoReturnable<Integer> cir) {
        if (!Ymm4Craft.isActive()) return;
        cir.setReturnValue(CoreCommands.renderFpsLimit());
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$modelGlow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SceneModels.glowColorOf(entity) >= 0) cir.setReturnValue(true);
    }

    @Redirect(method = "addInitialScreens", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;onboardAccessibility:Z"))
    private boolean ymm4craft$noOnboarding(Options options) {
        return options.onboardAccessibility && !Ymm4Craft.isActive();
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
