package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Unique
    private static final Identifier ymm4craft$CURSOR = Identifier.fromNamespaceAndPath("ymm4craft", "cursor");

    @Inject(method = {"init(II)V", "rebuildWidgets"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;init()V", shift = At.Shift.AFTER))
    private void ymm4craft$afterInit(CallbackInfo ci) {
        ClientHooks.fireScreenInit((Screen) (Object) this);
    }

    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void ymm4craft$replayCursor(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!ReplayPlayback.showsCursorOn((Screen) (Object) this)) return;
        graphics.nextStratum();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ymm4craft$CURSOR, mouseX, mouseY, 7, 11);
    }
}
