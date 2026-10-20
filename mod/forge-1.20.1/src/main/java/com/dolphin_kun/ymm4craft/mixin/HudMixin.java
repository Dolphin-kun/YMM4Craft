package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ForgeGui.class)
public abstract class HudMixin {
    @Inject(method = "renderChat", at = @At("HEAD"), cancellable = true, remap = false)
    private void ymm4craft$replayHideChat(int width, int height, GuiGraphics graphics, CallbackInfo ci) {
        if (ReplayPlayback.hidesChat()) ci.cancel();
    }

    @Inject(method = "pre", at = @At("HEAD"), cancellable = true, remap = false)
    private void ymm4craft$chatOnly(NamedGuiOverlay overlay, GuiGraphics graphics, CallbackInfoReturnable<Boolean> cir) {
        if (Minecraft.getInstance().options.hideGui && !overlay.id().equals(VanillaGuiOverlay.CHAT_PANEL.id())) cir.setReturnValue(true);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void ymm4craft$beginHud(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        ReplayPlayback.setHudRendering(true);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void ymm4craft$endHud(GuiGraphics graphics, float partialTick, CallbackInfo ci) {
        ReplayPlayback.setHudRendering(false);
    }
}
