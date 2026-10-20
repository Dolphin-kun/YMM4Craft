package com.dolphin_kun.ymm4craft.config;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.mixin.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;


public final class TitleScreenButton {
    private static final ResourceLocation ICON = new ResourceLocation(Ymm4Craft.MOD_ID, "textures/gui/sprites/ymm4.png");

    private TitleScreenButton() {
    }

    public static void register() {
        ClientHooks.onScreenInit(screen -> {
            if (!(screen instanceof TitleScreen) || Ymm4Craft.isActive()) return;
            try {
                Minecraft client = Minecraft.getInstance();
                int width = screen.width;
                AbstractWidget singleplayer = screen.children().stream()
                        .filter(w -> w instanceof AbstractWidget).map(w -> (AbstractWidget) w)
                        .filter(w -> w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals("menu.singleplayer"))
                        .findFirst().orElse(null);
                int x = singleplayer != null ? singleplayer.getX() - 24 : width - 24;
                int y = singleplayer != null ? singleplayer.getY() : 4;
                IconButton button = new IconButton(x, y, ICON, Component.translatable("ymm4craft.screen.title"),
                        b -> client.setScreen(new Ymm4CraftScreen(screen)));
                button.setTooltip(Tooltip.create(Component.translatable("ymm4craft.screen.button.tooltip")));
                screen.renderables.add(button);
                ((ScreenAccessor) screen).ymm4craft$children().add(button);
                ((ScreenAccessor) screen).ymm4craft$narratables().add(button);
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.warn("タイトル画面にボタンを追加できませんでした", t);
            }
        });
    }
}
