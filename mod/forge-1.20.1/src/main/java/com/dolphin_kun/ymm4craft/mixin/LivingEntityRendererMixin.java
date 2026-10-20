package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelBends;
import com.dolphin_kun.ymm4craft.feature.scene.ModelParts;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRendering;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRoots;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noNameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SceneModels.isModel(entity)) cir.setReturnValue(false);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            shift = At.Shift.AFTER))
    private void ymm4craft$tilt(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelRenderInfo info = ModelRendering.current();
        if (info == null || (info.tilt() == 0 && info.roll() == 0)) return;
        float center = entity.getBbHeight() / 2f / Math.max(entity.getScale(), 0.0001f);
        poseStack.translate(0, center, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(-info.tilt()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(info.roll()));
        poseStack.translate(0, -center, 0);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;speed(F)F"))
    private float ymm4craft$walkSpeed(WalkAnimationState walk, float partialTick) {
        ModelRenderInfo info = ModelRendering.current();
        return info != null && !Float.isNaN(info.walkPos()) ? info.walkSpeed() : walk.speed(partialTick);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/WalkAnimationState;position(F)F"))
    private float ymm4craft$walkPosition(WalkAnimationState walk, float partialTick) {
        ModelRenderInfo info = ModelRendering.current();
        return info != null && !Float.isNaN(info.walkPos()) ? info.walkPos() : walk.position(partialTick);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", shift = At.Shift.AFTER))
    private void ymm4craft$pose(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelRenderInfo info = ModelRendering.current();
        if (info == null) return;
        EntityModel<?> model = ((LivingEntityRenderer<?, ?>) (Object) this).getModel();
        ModelPart root = ModelRoots.of(model);
        if (root == null) return;
        if (!info.pose().isEmpty()) {
            ModelParts.applyPose(root, info.pose());
            ModelBends.begin(root, info.pose());
        }
        SceneModels.onModelPrepared(info, model, root, poseStack.last().pose());
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void ymm4craft$endBends(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (ModelRendering.current() != null) ModelBends.end();
    }
}
