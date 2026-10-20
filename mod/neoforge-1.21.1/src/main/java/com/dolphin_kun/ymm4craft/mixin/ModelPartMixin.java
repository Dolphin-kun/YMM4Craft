package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelBends;
import com.dolphin_kun.ymm4craft.feature.scene.ModelPartParent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModelPart.class)
public abstract class ModelPartMixin implements ModelPartParent {
    @Unique
    private ModelPart ymm4craft$parent;

    @Override
    public ModelPart ymm4craft$parent() {
        return ymm4craft$parent;
    }

    @Override
    public void ymm4craft$setParent(ModelPart parent) {
        ymm4craft$parent = parent;
    }

    @Inject(method = "getChild", at = @At("RETURN"))
    private void ymm4craft$rememberParent(String name, CallbackInfoReturnable<ModelPart> cir) {
        ((ModelPartParent) (Object) cir.getReturnValue()).ymm4craft$setParent((ModelPart) (Object) this);
    }

    @Inject(method = "compile(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$bend(PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, int color, CallbackInfo ci) {
        if (ModelBends.draw((ModelPart) (Object) this, pose, builder, light, overlay, color)) ci.cancel();
    }
}
