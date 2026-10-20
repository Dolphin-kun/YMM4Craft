package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRendering;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;sin(F)F"))
    private float ymm4craft$noBob(float value) {
        return ModelRendering.current() != null ? -1f : Mth.sin(value);
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;getSpin(F)F"))
    private float ymm4craft$noSpin(ItemEntity entity, float partialTick) {
        return ModelRendering.current() != null ? 0f : entity.getSpin(partialTick);
    }

    @Inject(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionf;)V", shift = At.Shift.AFTER, ordinal = 0))
    private void ymm4craft$rotate(ItemEntity entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, CallbackInfo ci) {
        ModelRenderInfo info = ModelRendering.current();
        if (info == null) return;
        poseStack.mulPose(Axis.YP.rotationDegrees(-info.yaw()));
        if (info.tilt() == 0 && info.roll() == 0) return;
        float center = 0.125f;
        poseStack.translate(0, center, 0);
        poseStack.mulPose(Axis.XP.rotationDegrees(info.tilt()));
        poseStack.mulPose(Axis.ZP.rotationDegrees(info.roll()));
        poseStack.translate(0, -center, 0);
    }
}
