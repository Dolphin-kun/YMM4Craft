package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelParts;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noNameTag(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        if (SceneModels.isModel(entity)) cir.setReturnValue(false);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", shift = At.Shift.AFTER))
    private void ymm4craft$tilt(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ModelRenderInfo info = state.getData(SceneModels.MODEL);
        if (info == null || (info.tilt() == 0 && info.roll() == 0)) return;
        float center = state.boundingBoxHeight / 2f / Math.max(state.scale, 0.0001f);
        poseStack.translate(0, center, 0);
        poseStack.rotateDegrees(com.mojang.math.Axis.XP, -info.tilt());
        poseStack.rotateDegrees(com.mojang.math.Axis.ZP, info.roll());
        poseStack.translate(0, -center, 0);
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void ymm4craft$poseForLayers(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ModelRenderInfo info = state.getData(SceneModels.MODEL);
        if (info == null || info.pose().isEmpty()) return;
        EntityModel<?> model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        ModelParts.applyPose(model.root(), info.pose());
    }
}
