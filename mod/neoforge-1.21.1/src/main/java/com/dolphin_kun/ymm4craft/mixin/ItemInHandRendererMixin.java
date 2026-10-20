package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @ModifyVariable(method = "renderPlayerArm", at = @At("STORE"), ordinal = 0)
    private AbstractClientPlayer ymm4craft$recordedArm(AbstractClientPlayer player) {
        Player pov = ReplayPlayback.povHands() != null ? ReplayPlayback.povPlayer() : null;
        return pov instanceof AbstractClientPlayer recorded ? recorded : player;
    }
}
