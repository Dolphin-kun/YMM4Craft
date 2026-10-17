package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderHolder;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V";

    @Unique
    private static Matrix4f ymm4craft$parentWorld;
    @Unique
    private static CameraRenderState ymm4craft$camera;

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$onlyModels(Entity entity, Frustum culler, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        if (SceneFeature.transparentBackground() && !SceneModels.isModel(entity)) cir.setReturnValue(false);
        else if (SceneFeature.hidesEntity(entity)) cir.setReturnValue(false);
    }

    @Inject(method = SUBMIT, at = @At("HEAD"))
    private void ymm4craft$begin(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
                                 SubmitNodeCollector collector, CallbackInfo ci) {
        ModelRenderInfo info = ((ModelRenderHolder) state).ymm4craft$modelInfo();
        ymm4craft$parentWorld = info == null ? null : info.parentWorld();
        ymm4craft$camera = camera;
    }

    @Inject(method = SUBMIT, at = @At("RETURN"))
    private void ymm4craft$end(EntityRenderState state, CameraRenderState camera, double x, double y, double z, PoseStack poseStack,
                               SubmitNodeCollector collector, CallbackInfo ci) {
        ymm4craft$parentWorld = null;
        ymm4craft$camera = null;
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void ymm4craft$parentTranslate(PoseStack poseStack, double x, double y, double z) {
        Matrix4f parent = ymm4craft$parentWorld;
        CameraRenderState camera = ymm4craft$camera;
        if (parent == null || camera == null) {
            poseStack.translate(x, y, z);
            return;
        }
        Matrix4f relative = new Matrix4f().translation((float) -camera.pos.x, (float) -camera.pos.y, (float) -camera.pos.z).mul(parent);
        poseStack.mulPose(relative);
    }
}
