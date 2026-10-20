package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRendering;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$onlyModels(Entity entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (SceneFeature.transparentBackground() && !SceneModels.isModel(entity)) cir.setReturnValue(false);
        else if (SceneFeature.hidesEntity(entity)) cir.setReturnValue(false);
    }

    @Inject(method = RENDER, at = @At("HEAD"))
    private void ymm4craft$begin(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelRendering.push(SceneModels.renderInfoOf(entity));
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void ymm4craft$end(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack poseStack,
                               MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelRendering.pop();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V", ordinal = 0))
    private void ymm4craft$parentTranslate(PoseStack poseStack, double x, double y, double z) {
        ModelRenderInfo info = ModelRendering.current();
        Matrix4f parent = info == null ? null : info.parentWorld();
        if (parent == null) {
            poseStack.translate(x, y, z);
            return;
        }
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        poseStack.mulPose(new Matrix4f().translation((float) -camera.x, (float) -camera.y, (float) -camera.z).mul(parent));
    }

    @Inject(method = "renderShadow", at = @At("HEAD"), cancellable = true)
    private static void ymm4craft$noShadow(CallbackInfo ci) {
        if (SceneFeature.transparentBackground()) ci.cancel();
    }
}
