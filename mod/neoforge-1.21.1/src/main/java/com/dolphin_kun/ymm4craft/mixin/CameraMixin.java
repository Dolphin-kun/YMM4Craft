package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "setup", at = @At("HEAD"))
    private void ymm4craft$movePlayerToCamera(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!Ymm4Craft.isActive()) return;
        double[] c = SceneFeature.cameraOverride();
        LocalPlayer player = Minecraft.getInstance().player;
        if (c == null || player == null) return;
        double y = c[1] - player.getEyeHeight();
        float yaw = (float) c[3];
        float pitch = (float) c[4];
        if (player.getX() != c[0] || player.getY() != y || player.getZ() != c[2]
                || player.getYRot() != yaw || player.getXRot() != pitch) {
            player.moveTo(c[0], y, c[2], yaw, pitch);
            player.setYHeadRot(yaw);
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOldPosAndRot();
    }

    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true)
    private float ymm4craft$replayPartial(float partialTick) {
        if (!Ymm4Craft.isActive()) return partialTick;
        Float partial = com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback.partialTick();
        return partial != null ? partial : partialTick;
    }
}
