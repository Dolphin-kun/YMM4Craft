package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderHolder;
import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.ModelParts;
import com.dolphin_kun.ymm4craft.feature.scene.ModelAim;
import com.dolphin_kun.ymm4craft.feature.scene.ModelBends;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
    @Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V", shift = At.Shift.AFTER))
    private void ymm4craft$pose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        if (!(submit.state() instanceof ModelRenderHolder holder)) return;
        ModelRenderInfo info = holder.ymm4craft$modelInfo();
        if (info == null) return;
        if (!info.pose().isEmpty()) {
            ModelParts.applyPose(submit.model().root(), info.pose());
            ModelAim.apply(submit.model().root(), info.pose(), submit.pose().pose());
            ModelBends.begin(submit.model().root(), info.pose());
        }
        SceneModels.onModelPrepared(info, submit.model(), submit.model().root(), submit.pose().pose());
    }

    @Inject(method = "prepareModel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", shift = At.Shift.AFTER))
    private void ymm4craft$endBends(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
        ModelBends.end();
    }
}
