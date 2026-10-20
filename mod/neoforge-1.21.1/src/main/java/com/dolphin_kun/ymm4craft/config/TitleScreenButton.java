package com.dolphin_kun.ymm4craft.config;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.mixin.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;


public final class TitleScreenButton {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(Ymm4Craft.MOD_ID, "ymm4");

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
                SpriteIconButton button = SpriteIconButton.builder(Component.translatable("ymm4craft.screen.title"),
                                b -> client.setScreen(new Ymm4CraftScreen(screen)), true)
                        .sprite(ICON, 16, 16)
                        .size(20, 20)
                        .build();
                button.setPosition(x, y);
                button.setTooltip(Tooltip.create(Component.translatable("ymm4craft.screen.button.tooltip")));
                ((ScreenAccessor) screen).ymm4craft$addRenderableWidget(button);
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.warn("タイトル画面にボタンを追加できませんでした", t);
            }
        });
    }
}
