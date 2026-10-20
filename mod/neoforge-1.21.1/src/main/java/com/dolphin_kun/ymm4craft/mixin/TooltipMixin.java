package com.dolphin_kun.ymm4craft.mixin;

import com.dolphin_kun.ymm4craft.config.WideTooltips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(Tooltip.class)
public abstract class TooltipMixin {
    @Redirect(method = "toCharSequence", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/Tooltip;splitTooltip(Lnet/minecraft/client/Minecraft;Lnet/minecraft/network/chat/Component;)Ljava/util/List;"))
    private List<FormattedCharSequence> ymm4craft$wide(Minecraft minecraft, Component message) {
        if (WideTooltips.isWide((Tooltip) (Object) this)) return minecraft.font.split(message, WideTooltips.WIDTH);
        return Tooltip.splitTooltip(minecraft, message);
    }
}
