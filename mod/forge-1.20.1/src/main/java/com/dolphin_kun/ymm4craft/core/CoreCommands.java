package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public final class CoreCommands {
    private static FrameCapture capture;
    private static volatile int renderFpsLimit = 60;

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
                renderFpsLimit = (int) Math.max(15, Math.min(300, fps * 2L));
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
        var screen = mc.screen;
        if (screen != null) {
            o.addProperty("screen", screen.getClass().getSimpleName());
            o.addProperty("screenTitle", screen.getTitle().getString());
        }
        if (mc.getOverlay() != null) o.addProperty("overlay", mc.getOverlay().getClass().getSimpleName());
        o.addProperty("fps", mc.getFps());
        o.addProperty("backend", GlUtil.getRenderer() + " / OpenGL " + GlUtil.getOpenGLVersion());
        o.addProperty("wglDxInterop", false);
        if (mc.player != null) {
            o.addProperty("player", String.format(Locale.ROOT, "%.1f %.1f %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            var server = mc.getSingleplayerServer();
            var serverPlayer = server == null ? null : server.getPlayerList().getPlayer(mc.player.getUUID());
            if (serverPlayer != null) {
                o.addProperty("serverPlayer", String.format(Locale.ROOT, "%.1f %.1f %.1f",
                        serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ()));
            }
            o.addProperty("loadedChunks", mc.level.getChunkSource().getLoadedChunksCount());
            o.addProperty("visibleSections", mc.levelRenderer.countRenderedChunks());
            Camera cam = mc.gameRenderer.getMainCamera();
            o.addProperty("cameraExact", String.format(Locale.ROOT, "%.4f %.4f %.4f yaw %.3f",
                    cam.getPosition().x, cam.getPosition().y, cam.getPosition().z, cam.getYRot()));
            o.addProperty("camera", String.format(Locale.ROOT, "%.1f %.1f %.1f yaw %.0f pitch %.0f",
                    cam.getPosition().x, cam.getPosition().y, cam.getPosition().z, cam.getYRot(), cam.getXRot()));
            JsonObject view = new JsonObject();
            view.addProperty("x", cam.getPosition().x);
            view.addProperty("y", cam.getPosition().y);
            view.addProperty("z", cam.getPosition().z);
            view.addProperty("yaw", Mth.wrapDegrees(cam.getYRot()));
            view.addProperty("pitch", cam.getXRot());
            o.add("view", view);
        }
        o.addProperty("width", mc.getWindow().getWidth());
        o.addProperty("height", mc.getWindow().getHeight());
        return o;
    }

    private static CompletableFuture<JsonElement> setResolution(CommandParams p) throws CommandException {
        int width = p.getInt("width", 16, 3840);
        int height = p.getInt("height", 16, 2160);
        return CommandRegistry.onMainThread(() -> {
            long handle = Minecraft.getInstance().getWindow().getWindow();
            GLFW.glfwSetWindowSizeLimits(handle, 1, 1, GLFW.GLFW_DONT_CARE, GLFW.GLFW_DONT_CARE);
            GLFW.glfwSetWindowSize(handle, width, height);
            return null;
        });
    }

    private static CompletableFuture<JsonElement> gpuShare(CommandParams p) throws CommandException {
        if (p.has("disable") && p.getBoolean("disable")) return CompletableFuture.completedFuture(null);
        throw new CommandException("この版の Minecraft では GPU での共有に対応していません");
    }
}
