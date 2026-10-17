package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void ymm4craft$movePlayerToCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        double[] c = SceneFeature.cameraOverride();
        LocalPlayer player = Minecraft.getInstance().player;
        if (c == null || player == null) return;
        double y = c[1] - player.getEyeHeight();
        float yaw = (float) c[3];
        float pitch = (float) c[4];
        if (player.getX() != c[0] || player.getY() != y || player.getZ() != c[2]
                || player.getYRot() != yaw || player.getXRot() != pitch) {
            player.snapTo(c[0], y, c[2], yaw, pitch);
            player.setYHeadRot(yaw);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOldPosAndRot();
    }

    @Inject(method = "getCameraEntityPartialTicks", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayPartial(DeltaTracker deltaTracker, CallbackInfoReturnable<Float> cir) {
        if (!Ymm4Craft.isActive()) return;
        Float partial = com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback.partialTick();
        if (partial != null) cir.setReturnValue(partial);
    }

    @Inject(method = "calculateFov", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$overrideFov(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!Ymm4Craft.isActive()) return;
        Float fov = SceneFeature.fovOverride();
        if (fov != null) cir.setReturnValue(fov);
    }
}
