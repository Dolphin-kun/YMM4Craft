package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.NoChunkFade;
import net.minecraft.client.CameraType;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "getCameraType", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$firstPerson(CallbackInfoReturnable<CameraType> cir) {
        if (Ymm4Craft.isActive()) cir.setReturnValue(CameraType.FIRST_PERSON);
    }

    @Inject(method = "chunkSectionFadeInTime", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$noChunkFade(CallbackInfoReturnable<OptionInstance<Double>> cir) {
        if (Ymm4Craft.isActive()) cir.setReturnValue(NoChunkFade.OPTION);
    }
}
