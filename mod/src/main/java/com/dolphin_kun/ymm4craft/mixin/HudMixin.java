package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    private static final String GET_MODE = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;";
    private static final String CAN_HURT = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;canHurtPlayer()Z";
    private static final String HAS_XP = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;hasExperience()Z";

    @Shadow
    private boolean isHidden;

    @Shadow
    private void extractChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$replayHideChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ReplayPlayback.hidesChat()) ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void ymm4craft$replayChatWithoutHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (isHidden && ReplayPlayback.showsChat() && !(Minecraft.getInstance().gui.screen() instanceof LevelLoadingScreen)) {
            extractChat(graphics, deltaTracker);
        }
    }

    @Redirect(method = {"extractCrosshair", "extractHotbarAndDecorations"}, at = @At(value = "INVOKE", target = GET_MODE))
    private GameType ymm4craft$replayMode(MultiPlayerGameMode gameMode) {
        GameType recorded = ReplayPlayback.povGameMode();
        return recorded != null ? recorded : gameMode.getPlayerMode();
    }

    @Redirect(method = {"extractHotbarAndDecorations", "extractSelectedItemName"}, at = @At(value = "INVOKE", target = CAN_HURT))
    private boolean ymm4craft$replayCanHurt(MultiPlayerGameMode gameMode) {
        GameType recorded = ReplayPlayback.povGameMode();
        return recorded != null ? recorded.isSurvival() : gameMode.canHurtPlayer();
    }

    @Redirect(method = {"extractHotbarAndDecorations", "nextContextualInfoState"}, at = @At(value = "INVOKE", target = HAS_XP))
    private boolean ymm4craft$replayHasXp(MultiPlayerGameMode gameMode) {
        GameType recorded = ReplayPlayback.povGameMode();
        return recorded != null ? recorded.isSurvival() : gameMode.hasExperience();
    }
}
