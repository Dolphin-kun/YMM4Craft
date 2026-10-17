package com.dolphin_kun.ymm4craft;

import com.dolphin_kun.ymm4craft.config.AutoConnect;
import com.dolphin_kun.ymm4craft.config.TitleScreenButton;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.dolphin_kun.ymm4craft.core.CoreCommands;
import com.dolphin_kun.ymm4craft.core.HostConnection;
import com.dolphin_kun.ymm4craft.core.Session;
import com.dolphin_kun.ymm4craft.core.TempWorlds;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayFeature;
import com.dolphin_kun.ymm4craft.feature.scene.SceneFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.sdl.SDLVideo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public final class Ymm4Craft {
    public static final String MOD_ID = "ymm4craft";
    public static final Logger LOGGER = LoggerFactory.getLogger("YMM4Craft");

    private static volatile Session session;
    private static volatile boolean evaluated;
    private static volatile boolean active;
    private static volatile boolean attachedByUser;
    private static int[] windowSizeBeforeAttach;

    private static HostConnection connection;
    private static CommandRegistry registry;

    public static boolean isActive() {
        if (!evaluated) {
            evaluate();
        }
        return active;
    }

    public static Session session() {
        return session;
    }

    private static synchronized void evaluate() {
        if (evaluated) return;
        try {
            Session accepted = Session.tryAccept();
            if (accepted != null) {
                session = accepted;
                active = true;
                LOGGER.info("YMM4からのセッション要求を受理しました。連携状態で起動します");
            }
        } catch (Throwable t) {
            LOGGER.warn("セッション要求の判定に失敗したため、休眠状態で起動します", t);
            session = null;
            active = false;
        } finally {
            evaluated = true;
        }
    }

    private Ymm4Craft() {
    }

    public static void initialize() {
        try {
            ReplayFeature.registerRecorder();
        } catch (Throwable t) {
            LOGGER.warn("リプレイの録画の準備に失敗しました", t);
        }
        try {
            TitleScreenButton.register();
        } catch (Throwable t) {
            LOGGER.warn("タイトル画面のボタンを追加できませんでした", t);
        }
        try {
            AutoConnect.register();
        } catch (Throwable t) {
            LOGGER.warn("自動で接続する準備に失敗しました", t);
        }
        if (!isActive()) return;

        try {
            TempWorlds.cleanupLeftovers(Minecraft.getInstance().gameDirectory.toPath());
            startLink(session);
        } catch (Throwable t) {
            fallbackToVanilla("連携の開始に失敗しました", t);
        }
    }

    private static void startLink(Session s) throws IOException {
        if (registry == null) {
            CommandRegistry r = new CommandRegistry();
            CoreCommands.register(r);
            SceneFeature.register(r);
            ReplayFeature.register(r);
            registry = r;
        }
        CoreCommands.onSessionStarted(s);
        connection = new HostConnection(s, registry);
        connection.start();
    }

    public static void attachByUser() throws IOException {
        if (active) throw new IOException("すでにYMM4と接続しています");
        Session s = Session.acceptByUser();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.disconnectWithProgressScreen();
        TempWorlds.cleanupLeftovers(mc.gameDirectory.toPath());

        long handle = mc.getWindow().handle();
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.IntBuffer w = stack.mallocInt(1);
            java.nio.IntBuffer h = stack.mallocInt(1);
            SDLVideo.SDL_GetWindowSize(handle, w, h);
            windowSizeBeforeAttach = new int[]{w.get(0), h.get(0)};
        }

        session = s;
        attachedByUser = true;
        active = true;
        try {
            startLink(s);
        } catch (IOException | RuntimeException e) {
            active = false;
            attachedByUser = false;
            session = null;
            CoreCommands.onSessionEnded();
            throw e;
        }
        SDLVideo.SDL_HideWindow(handle);
        mc.gui.setScreen(new TitleScreen());
    }

    public static HostConnection connection() {
        return connection;
    }

    public static void fallbackToVanilla(String reason, Throwable cause) {
        LOGGER.error("{}。休眠状態に戻ります", reason, cause);
        active = false;
        HostConnection c = connection;
        connection = null;
        if (c != null) c.closeQuietly();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.execute(() -> {
            try {
                long handle = mc.getWindow().handle();
                if (handle != 0L) SDLVideo.SDL_ShowWindow(handle);
            } catch (Throwable t) {
                LOGGER.warn("ウィンドウの再表示に失敗しました", t);
            }
        });
    }

    public static void onHostLost(String reason) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (attachedByUser) {
            LOGGER.info("YMM4との接続が切れました（{}）。普段のプレイに戻ります", reason);
            mc.execute(Ymm4Craft::detachToVanilla);
            return;
        }
        LOGGER.warn("YMM4との接続が切れました（{}）。ゲームを終了します", reason);
        mc.execute(mc::stop);
    }

    private static void detachToVanilla() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        HostConnection c = connection;
        connection = null;
        if (c != null) c.closeQuietly();
        try {
            CoreCommands.onSessionEnded();
            SceneFeature.reset();
        } catch (Throwable t) {
            LOGGER.warn("連携の後始末に失敗しました", t);
        }
        active = false;
        attachedByUser = false;
        session = null;
        try {
            long handle = mc.getWindow().handle();
            SDLVideo.SDL_SetWindowMinimumSize(handle, 320, 240);
            if (windowSizeBeforeAttach != null) SDLVideo.SDL_SetWindowSize(handle, windowSizeBeforeAttach[0], windowSizeBeforeAttach[1]);
            SDLVideo.SDL_ShowWindow(handle);
            SDLVideo.SDL_RaiseWindow(handle);
        } catch (Throwable t) {
            LOGGER.warn("ウィンドウの再表示に失敗しました", t);
        }
        windowSizeBeforeAttach = null;
        mc.gui.setScreen(new TitleScreen());
    }
}
