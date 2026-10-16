package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayHands;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extractPlayerState", at = @At("TAIL"))
    private void ymm4craft$replayHands(Camera camera, DeltaTracker deltaTracker, float worldPartialTicks, PlayerRenderState state, CallbackInfo ci) {
        ReplayHands hands = ReplayPlayback.povHands();
        Player player = ReplayPlayback.povPlayer();
        if (hands == null || player == null) return;
        Float partial = ReplayPlayback.partialTick();
        hands.extract(player, partial != null ? partial : worldPartialTicks, state);
    }
}
