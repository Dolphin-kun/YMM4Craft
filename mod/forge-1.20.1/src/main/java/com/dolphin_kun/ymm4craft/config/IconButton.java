package com.dolphin_kun.ymm4craft.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

final class IconButton extends Button {
    private final ResourceLocation icon;

    IconButton(int x, int y, ResourceLocation icon, Component message, OnPress onPress) {
        super(x, y, 20, 20, message, onPress, DEFAULT_NARRATION);
        this.icon = icon;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Component message = getMessage();
        setMessage(Component.empty());
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        setMessage(message);
        graphics.blit(icon, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
    }
}
