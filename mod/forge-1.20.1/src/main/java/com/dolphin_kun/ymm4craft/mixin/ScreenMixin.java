package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final ResourceLocation ymm4craft$CURSOR = new ResourceLocation("ymm4craft", "textures/gui/sprites/cursor.png");

    @Inject(method = {"init(Lnet/minecraft/client/Minecraft;II)V", "rebuildWidgets"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init()V", shift = At.Shift.AFTER))
    private void ymm4craft$afterInit(CallbackInfo ci) {
        ClientHooks.fireScreenInit((Screen) (Object) this);
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void ymm4craft$replayCursor(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (!ReplayPlayback.showsCursorOn((Screen) (Object) this)) return;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        graphics.blit(ymm4craft$CURSOR, mouseX, mouseY, 0, 0, 7, 11, 7, 11);
        graphics.pose().popPose();
    }
}
