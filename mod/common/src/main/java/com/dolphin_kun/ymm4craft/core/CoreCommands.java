package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.core.gpu.GpuFrameShare;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.opengl.WGL;
import org.lwjgl.opengl.WGLARBExtensionsString;
import org.lwjgl.sdl.SDLVideo;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CoreCommands {
    private static FrameCapture capture;
    private static volatile int renderFpsLimit = 60;
    private static Boolean wglDxInterop;

    private CoreCommands() {
    }

    public static void onSessionStarted(Session session) {
        if (capture != null) capture.close();
        capture = new FrameCapture(session);
        renderFpsLimit = 60;
    }

    public static void onSessionEnded() {
        WorldCommands.onSessionEnded();
        if (capture != null) capture.close();
        capture = null;
    }

    public static void register(CommandRegistry registry) {
        WorldCommands.register(registry);
        registry.register("core.status", p -> CommandRegistry.onMainThread(CoreCommands::status));
        registry.register("core.setResolution", CoreCommands::setResolution);
        registry.register("core.capture", p -> {
            int settleFrames = p.has("settleFrames") ? p.getInt("settleFrames", 0, 600) : 2;
            return CommandRegistry.onMainThread(() -> {
                if (capture == null) throw new RuntimeException(new CommandException("YMM4と接続していません"));
                return capture.request(settleFrames);
            }).thenCompose(f -> f);
        });
        registry.register("core.stream", p -> {
            int fps = p.getInt("fps", 0, 240);
            return CommandRegistry.onMainThread(() -> {
                if (capture != null) capture.setStreamFps(fps);
                renderFpsLimit = Math.clamp(fps * 2L, 15, 300);
                return null;
            });
        });
        registry.register("core.gpuShare", CoreCommands::gpuShare);
        registry.register("core.shutdown", p -> CommandRegistry.onMainThread(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.execute(mc::stop);
            return null;
        }));
    }

    public static int renderFpsLimit() {
        return renderFpsLimit;
    }

    public static void onFrameRendered() {
        try {
            if (capture != null) capture.onFrameRendered();
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("フレームの処理に失敗しました", t);
        }
    }

    private static JsonElement status() {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o = new JsonObject();
        String openedWorld = WorldCommands.openedWorld();
        String state;
        if (mc.level != null && mc.player != null) state = "world";
        else if (openedWorld != null) state = "loading";
        else state = "menu";
        o.addProperty("state", state);
        if (openedWorld != null) o.addProperty("world", openedWorld);
        var screen = mc.gui.screen();
        if (screen != null) {
            o.addProperty("screen", screen.getClass().getSimpleName());
            o.addProperty("screenTitle", screen.getTitle().getString());
        }
        if (mc.gui.overlay() != null) o.addProperty("overlay", mc.gui.overlay().getClass().getSimpleName());
        o.addProperty("fps", mc.getFps());
        o.addProperty("backend", RenderSystem.getDevice().getDeviceInfo().toString());
        o.addProperty("wglDxInterop", hasWglDxInterop());
        if (mc.player != null) {
            o.addProperty("player", String.format(Locale.ROOT, "%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            var server = mc.getSingleplayerServer();
            var serverPlayer = server == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
            if (serverPlayer != null) {
                o.addProperty("serverPlayer", String.format(Locale.ROOT, "%.1f %.1f %.1f",
                        serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()));
            }
            o.addProperty("loadedChunks", mc.level.getChunkSource().getLoadedChunksCount());
            o.addProperty("visibleSections", mc.levelRenderer.visibleSections().size());
            Camera cam = mc.gameRenderer.mainCamera();
            o.addProperty("cameraExact", String.format(Locale.ROOT, "%.4f %.4f %.4f yaw %.3f",
                    cam.position().x, cam.position().y, cam.position().z, cam.yRot()));
            o.addProperty("camera", String.format(Locale.ROOT, "%.1f %.1f %.1f yaw %.0f pitch %.0f",
                    cam.position().x, cam.position().y, cam.position().z, cam.yRot(), cam.xRot()));
            JsonObject view = new JsonObject();
            view.addProperty("x", cam.position().x);
            view.addProperty("y", cam.position().y);
            view.addProperty("z", cam.position().z);
            view.addProperty("yaw", Mth.wrapDegrees(cam.yRot()));
            view.addProperty("pitch", cam.xRot());
            o.add("view", view);
        }
        o.addProperty("width", mc.getWindow().getWidth());
        o.addProperty("height", mc.getWindow().getHeight());
        return o;
    }

    private static boolean hasWglDxInterop() {
        if (wglDxInterop == null) {
            try {
                long hdc = WGL.wglGetCurrentDC();
                if (hdc == 0) return false;
                String extensions = WGLARBExtensionsString.wglGetExtensionsStringARB(hdc);
                wglDxInterop = extensions != null && extensions.contains("WGL_NV_DX_interop2");
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.info("WGL拡張を調べられませんでした（OpenGL以外のバックエンドなど）: {}", t.toString());
                wglDxInterop = false;
            }
        }
        return wglDxInterop;
    }

    private static CompletableFuture<JsonElement> setResolution(CommandParams p) throws CommandException {
        int width = p.getInt("width", 16, 3840);
        int height = p.getInt("height", 16, 2160);
        return CommandRegistry.onMainThread(() -> {
            long handle = Minecraft.getInstance().getWindow().handle();
            float density = SDLVideo.SDL_GetWindowPixelDensity(handle);
            if (!(density > 0)) density = 1f;
            SDLVideo.SDL_SetWindowMinimumSize(handle, 1, 1);
            SDLVideo.SDL_SetWindowSize(handle, Math.round(width / density), Math.round(height / density));
            return null;
        });
    }

    private static CompletableFuture<JsonElement> gpuShare(CommandParams p) throws CommandException {
        if (p.has("disable") && p.getBoolean("disable")) {
            return CommandRegistry.onMainThread(() -> {
                if (capture != null) capture.setGpuShare(null);
                return null;
            });
        }
        int generation = p.getInt("generation", 0, Integer.MAX_VALUE);
        long luidLow = p.getLong("luidLow", 0, 0xFFFFFFFFL);
        int luidHigh = (int) p.getLong("luidHigh", Integer.MIN_VALUE, Integer.MAX_VALUE);
        int width = p.getInt("width", 16, 3840);
        int height = p.getInt("height", 16, 2160);
        CommandParams handlesParam = p.getObject("handles");
        int count = handlesParam.getInt("count", 1, 4);
        long[] handles = new long[count];
        for (int i = 0; i < count; i++) handles[i] = handlesParam.getLong(String.valueOf(i), 1, Long.MAX_VALUE);

        return CommandRegistry.<JsonElement>onMainThread(() -> {
            if (!hasWglDxInterop()) {
                throw new RuntimeException(new CommandException("このGPU・ドライバはOpenGLとDirect3Dの共有（WGL_NV_DX_interop2）に対応していません"));
            }
            if (capture == null) throw new RuntimeException(new CommandException("YMM4と接続していません"));
            try {
                capture.setGpuShare(GpuFrameShare.open(generation, luidLow, luidHigh, width, height, handles));
            } catch (Throwable t) {
                Ymm4Craft.LOGGER.warn("GPUでの共有を始められませんでした", t);
                capture.setGpuShare(null);
                throw new RuntimeException(new CommandException("GPUでの共有を始められませんでした: " + t.getMessage()));
            }
            return null;
        }).exceptionallyCompose(CommandRegistry::unwrapFailure);
    }
}
