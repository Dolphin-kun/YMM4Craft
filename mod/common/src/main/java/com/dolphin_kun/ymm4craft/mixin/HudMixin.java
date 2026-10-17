package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow
    private boolean isHidden;

    @Shadow
    private void extractChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayHideChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ReplayPlayback.hidesChat()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void ymm4craft$beginHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ReplayPlayback.setHudRendering(true);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ymm4craft$replayChatWithoutHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ReplayPlayback.setHudRendering(false);
        if (isHidden && ReplayPlayback.showsChat() && !(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) {
            extractChat(graphics, deltaTracker);
        }
    }
}
