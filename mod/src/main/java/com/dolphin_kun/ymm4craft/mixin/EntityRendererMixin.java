package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.ModelRenderInfo;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void ymm4craft$modelState(Entity entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
        EntityRenderState state = cir.getReturnValue();
        ModelRenderInfo info = SceneModels.renderInfoOf(entity);
        state.setData(SceneModels.MODEL, info);
        if (info != null && info.glowColor() >= 0) state.outlineColor = ARGB.opaque(info.glowColor());
        if (info != null && !Float.isNaN(info.walkPos()) && state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            living.walkAnimationPos = info.walkPos();
            living.walkAnimationSpeed = info.walkSpeed();
        }
        if (SceneFeature.transparentBackground()) {
            state.shadowPieces.clear();
            state.shadowRadius = 0;
        }
    }
}
