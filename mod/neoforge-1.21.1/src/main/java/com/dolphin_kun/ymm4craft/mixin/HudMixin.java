package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class HudMixin {
    @Shadow
    private void renderChat(GuiGraphics graphics, DeltaTracker deltaTracker) {
    }

    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayHideChat(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ReplayPlayback.hidesChat()) ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ymm4craft$beginHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ReplayPlayback.setHudRendering(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ymm4craft$replayChatWithoutHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        ReplayPlayback.setHudRendering(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui && ReplayPlayback.showsChat() && !((mc.screen instanceof LevelLoadingScreen || mc.screen instanceof ReceivingLevelScreen))) {
            renderChat(graphics, deltaTracker);
        }
    }
}
