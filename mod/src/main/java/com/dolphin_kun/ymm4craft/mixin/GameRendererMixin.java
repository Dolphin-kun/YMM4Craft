package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType ymm4craft$replayHandMode(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.povHands() != null ? GameType.SURVIVAL : gameMode.getPlayerMode();
    }

    @Redirect(method = "renderItemInHand", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;isHudHidden:Z"))
    private boolean ymm4craft$replayHandWithoutHud(GuiRenderState state) {
        return ReplayPlayback.povHands() == null && state.isHudHidden;
    }
}
