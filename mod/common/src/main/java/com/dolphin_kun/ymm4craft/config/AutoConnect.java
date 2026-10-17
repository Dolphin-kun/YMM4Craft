package com.dolphin_kun.ymm4craft.config;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.core.ModConfig;
import com.dolphin_kun.ymm4craft.core.Session;
import net.minecraft.client.gui.screens.TitleScreen;

import java.io.IOException;

public final class AutoConnect {
    private static final int INTERVAL_TICKS = 20;

    private static int ticks;
    private static String failedToken;

    private AutoConnect() {
    }

    public static void register() {
        ClientHooks.onEndTick(mc -> {
            if (++ticks < INTERVAL_TICKS) return;
            ticks = 0;
            if (!ModConfig.get().autoConnect && !Session.startedForYmm4()) return;
            if (mc.level != null || !(mc.gui.screen() instanceof TitleScreen) || Ymm4Craft.isActive()) return;
            Session waiting = Session.peekWaiting();
            if (waiting == null || waiting.token().equals(failedToken)) return;
            try {
                Ymm4Craft.attachByUser();
            } catch (IOException | RuntimeException e) {
                failedToken = waiting.token();
                Ymm4Craft.LOGGER.warn("YMM4と自動で接続できませんでした", e);
            }
        });
    }
}
