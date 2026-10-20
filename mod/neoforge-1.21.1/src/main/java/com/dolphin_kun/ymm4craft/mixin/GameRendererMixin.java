package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.renderer.GameRenderer;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayHands;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isWindowActive()Z"))
    private boolean ymm4craft$noAutoPause(Minecraft mc) {
        return mc.isWindowActive() || Ymm4Craft.isActive();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;xpos()D"))
    private double ymm4craft$replayCursorX(MouseHandler mouse) {
        Window w = Minecraft.getInstance().getWindow();
        double[] cursor = ReplayPlayback.screenCursor(w.getGuiScaledWidth(), w.getGuiScaledHeight());
        return cursor != null ? cursor[0] * w.getScreenWidth() / w.getGuiScaledWidth() : mouse.xpos();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MouseHandler;ypos()D"))
    private double ymm4craft$replayCursorY(MouseHandler mouse) {
        Window w = Minecraft.getInstance().getWindow();
        double[] cursor = ReplayPlayback.screenCursor(w.getGuiScaledWidth(), w.getGuiScaledHeight());
        return cursor != null ? cursor[1] * w.getScreenHeight() / w.getGuiScaledHeight() : mouse.ypos();
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void ymm4craft$overrideFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (!useFovSetting || !Ymm4Craft.isActive()) return;
        Float fov = SceneFeature.fovOverride();
        if (fov != null) cir.setReturnValue((double) fov);
    }

    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;getPlayerMode()Lnet/minecraft/world/level/GameType;"))
    private GameType ymm4craft$replayHandMode(MultiPlayerGameMode gameMode) {
        return ReplayPlayback.povHands() != null ? GameType.SURVIVAL : gameMode.getPlayerMode();
    }

    @Redirect(method = "renderItemInHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/player/LocalPlayer;I)V"))
    private void ymm4craft$replayHands(ItemInHandRenderer renderer, float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                       LocalPlayer player, int light) {
        ReplayHands hands = ReplayPlayback.povHands();
        Player pov = ReplayPlayback.povPlayer();
        if (hands != null && pov instanceof AbstractClientPlayer recorded) {
            Float partial = ReplayPlayback.partialTick();
            hands.render(renderer, partial != null ? partial : partialTicks, poseStack, buffers, recorded, light);
            return;
        }
        renderer.renderHandsWithItems(partialTicks, poseStack, buffers, player, light);
    }

    @Redirect(method = "renderItemInHand", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;hideGui:Z"))
    private boolean ymm4craft$replayHandWithoutHud(Options options) {
        return ReplayPlayback.povHands() == null && options.hideGui;
    }
}
