package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelBends;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Shadow
    protected abstract net.minecraft.client.model.geom.ModelPart getArm(HumanoidArm arm);

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void ymm4craft$bendHand(HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        ModelRenderInfo info = state.getData(SceneModels.MODEL);
        if (info == null) return;
        ModelBends.bendHand(getArm(arm), arm == HumanoidArm.RIGHT ? "right_arm" : "left_arm", info, poseStack);
    }
}
