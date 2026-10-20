package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.scene.SceneModels;
import com.dolphin_kun.ymm4craft.feature.scene.SceneSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$modelSkin(CallbackInfoReturnable<ResourceLocation> cir) {
        SceneSkin skin = SceneModels.skinOf((AbstractClientPlayer) (Object) this);
        if (skin != null) cir.setReturnValue(skin.texture());
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$modelArms(CallbackInfoReturnable<String> cir) {
        SceneSkin skin = SceneModels.skinOf((AbstractClientPlayer) (Object) this);
        if (skin != null) cir.setReturnValue(skin.model());
    }

    @Inject(method = {"getCloakTextureLocation", "getElytraTextureLocation"}, at = @At("HEAD"), cancellable = true)
    private void ymm4craft$modelCape(CallbackInfoReturnable<ResourceLocation> cir) {
        SceneSkin skin = SceneModels.skinOf((AbstractClientPlayer) (Object) this);
        if (skin != null) cir.setReturnValue(skin.cape());
    }

    @Inject(method = "isCapeLoaded", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$modelCapeLoaded(CallbackInfoReturnable<Boolean> cir) {
        SceneSkin skin = SceneModels.skinOf((AbstractClientPlayer) (Object) this);
        if (skin != null) cir.setReturnValue(skin.cape() != null);
    }
}
