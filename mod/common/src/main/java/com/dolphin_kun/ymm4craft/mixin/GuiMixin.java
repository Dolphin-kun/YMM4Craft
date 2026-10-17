package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Gui.class)
public abstract class GuiMixin {
    @Redirect(method = "addInitialScreens", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;onboardAccessibility:Z"))
    private boolean ymm4craft$noOnboarding(Options options) {
        return options.onboardAccessibility && !Ymm4Craft.isActive();
    }
}
