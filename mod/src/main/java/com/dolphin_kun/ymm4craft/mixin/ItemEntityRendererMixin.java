package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

    @Unique
    private static ModelRenderInfo ymm4craft$current;

    @Inject(method = SUBMIT, at = @At("HEAD"))
    private void ymm4craft$begin(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ymm4craft$current = state.getData(SceneModels.MODEL);
    }

    @Inject(method = SUBMIT, at = @At("RETURN"))
    private void ymm4craft$end(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ymm4craft$current = null;
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(D)F"))
    private float ymm4craft$noBob(double value) {
        return ymm4craft$current != null ? -1f : Mth.sin(value);
    }

    @Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(FF)F"))
    private float ymm4craft$noSpin(float ageInTicks, float bobOffset) {
        return ymm4craft$current != null ? 0f : ItemEntity.getSpin(ageInTicks, bobOffset);
    }

    @Inject(method = SUBMIT, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V", shift = At.Shift.AFTER))
    private void ymm4craft$rotate(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ModelRenderInfo info = ymm4craft$current;
        if (info == null) return;
        poseStack.rotateDegrees(Axis.YP, -info.yaw());
        if (info.tilt() == 0 && info.roll() == 0) return;
        AABB box = state.item.getModelBoundingBox();
        float center = 0.0625f + (float) (box.maxY - box.minY) / 2f;
        poseStack.translate(0, center, 0);
        poseStack.rotateDegrees(Axis.XP, info.tilt());
        poseStack.rotateDegrees(Axis.ZP, info.roll());
        poseStack.translate(0, -center, 0);
    }
}
