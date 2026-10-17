package com.dolphin_kun.ymm4craft.config;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ModConfig;
import com.dolphin_kun.ymm4craft.core.Session;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayFiles;
import com.mojang.blaze3d.Blaze3D;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class Ymm4CraftScreen extends Screen {
    private static final int WIDTH = 310;
    private static final int HALF = 150;

    private final Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 33, 33);
    private MultiLineTextWidget status;
    private Button connectButton;
    private Component error;
    private int refreshTicks;

    public Ymm4CraftScreen(Screen parent) {
        super(Component.translatable("ymm4craft.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.get();
        layout.addTitleHeader(title, font);
        LinearLayout content = layout.addToContents(LinearLayout.vertical().spacing(6));
        content.defaultCellSetting().alignHorizontallyCenter();

        content.addChild(heading("ymm4craft.screen.connection"));
        status = content.addChild(new MultiLineTextWidget(Component.empty(), font).setMaxWidth(WIDTH).setCentered(true));
        GridLayout connection = new GridLayout().columnSpacing(10);
        GridLayout.RowHelper connectionRow = connection.createRowHelper(2);
        connectButton = connectionRow.addChild(Button.builder(Component.translatable("ymm4craft.screen.connect"), b -> connect()).width(HALF).build());
        connectionRow.addChild(toggle(config.autoConnect, "ymm4craft.config.link", v -> config.autoConnect = v));
        content.addChild(connection);

        content.addChild(heading("ymm4craft.screen.replay"));
        GridLayout replay = new GridLayout().columnSpacing(10).rowSpacing(4);
        GridLayout.RowHelper rows = replay.createRowHelper(2);
        rows.addChild(toggle(config.recordReplays, "ymm4craft.config.record", v -> config.recordReplays = v));
        rows.addChild(toggle(config.notifyRecording, "ymm4craft.config.record.notify", v -> config.notifyRecording = v));
        rows.addChild(toggle(config.recordSingleplayer, "ymm4craft.config.record.singleplayer", v -> config.recordSingleplayer = v));
        rows.addChild(toggle(config.recordMultiplayer, "ymm4craft.config.record.multiplayer", v -> config.recordMultiplayer = v));
        content.addChild(replay);
        content.addChild(Button.builder(Component.translatable("ymm4craft.config.openFolder"), b -> openReplayFolder()).width(WIDTH).build());

        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        refreshStatus();
        repositionElements();
    }

    private MultiLineTextWidget heading(String key) {
        return new MultiLineTextWidget(Component.translatable(key).withStyle(ChatFormatting.YELLOW), font).setMaxWidth(WIDTH).setCentered(true);
    }

    private CycleButton<Boolean> toggle(boolean value, String key, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(value)
                .withTooltip(v -> WideTooltips.create(Component.translatable(key + ".tooltip")))
                .create(0, 0, HALF, 20, Component.translatable(key), (button, v) -> setter.accept(v));
    }

    private void refreshStatus() {
        if (status == null) return;
        Session waiting = Ymm4Craft.isActive() ? null : Session.peekWaiting();
        Component text;
        if (Ymm4Craft.isActive()) {
            text = Component.translatable("ymm4craft.screen.status.linked");
        } else if (waiting != null) {
            text = Component.translatable("ymm4craft.screen.status.waiting");
        } else if (Session.waitingWithOtherVersion()) {
            text = Component.translatable("ymm4craft.screen.status.version");
        } else {
            text = Component.translatable("ymm4craft.screen.status.none");
        }
        if (error != null) text = Component.empty().append(text).append("\n").append(error.copy().withStyle(ChatFormatting.RED));
        status.setMessage(text);
        connectButton.active = waiting != null;
        repositionElements();
    }

    private void connect() {
        try {
            Ymm4Craft.attachByUser();
        } catch (IOException | RuntimeException e) {
            error = Component.translatable("ymm4craft.screen.connectFailed", e.getMessage() == null ? e.toString() : e.getMessage());
            Ymm4Craft.LOGGER.warn("YMM4と接続できませんでした", e);
            refreshStatus();
        }
    }

    @Override
    public void tick() {
        if (++refreshTicks >= 20) {
            refreshTicks = 0;
            refreshStatus();
        }
    }

    private void openReplayFolder() {
        Path dir = ReplayFiles.folder(minecraft.gameDirectory.toPath());
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        Blaze3D.openPath(dir);
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void removed() {
        ModConfig.get().save();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
