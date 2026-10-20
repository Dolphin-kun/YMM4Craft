package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelBends;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    protected abstract ModelPart getArm(HumanoidArm arm);

    @Inject(method = "translateToHand", at = @At("TAIL"))
    private void ymm4craft$bendHand(HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        ModelRenderInfo info = ModelRendering.current();
        if (info == null) return;
        ModelBends.bendHand(getArm(arm), arm == HumanoidArm.RIGHT ? "right_arm" : "left_arm", info, poseStack);
    }
}
