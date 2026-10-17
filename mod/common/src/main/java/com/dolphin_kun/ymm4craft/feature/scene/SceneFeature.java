package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.core.CommandParams;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import com.google.gson.JsonElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class SceneFeature {
    private static final Set<String> WEATHERS = Set.of("clear", "rain", "thunder");
    private static final Set<String> REPLAY_CAMERAS = Set.of("free", "pov");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_.-]{0,32}");

    private static volatile double[] cameraOverride;
    private static volatile Float fovOverride;
    private static volatile boolean transparent;
    private static volatile long appliedRevision;
    private static volatile boolean replayPov;

    private static ClientLevel preparedLevel;
    private static Integer sentTime;
    private static String sentWeather;

    private SceneFeature() {
    }

    public static double[] cameraOverride() {
        double[] editor = PoseEditorCommands.camera();
        if (editor != null) return editor;
        if (replayPov && ReplayPlayback.active()) {
            double[] pov = ReplayPlayback.povCamera();
            if (pov != null) return pov;
        }
        return cameraOverride;
    }

    public static boolean hidesEntity(Entity entity) {
        return replayPov && PoseEditorCommands.camera() == null && ReplayPlayback.active()
                && (ReplayPlayback.isPovTarget(entity) || entity == Minecraft.getInstance().player);
    }

    public static Float fovOverride() {
        return PoseEditorCommands.camera() != null ? Float.valueOf(PoseEditorCommands.EDITOR_FOV) : fovOverride;
    }

    public static boolean transparentBackground() {
        return transparent && Ymm4Craft.isActive();
    }

    public static long appliedRevision() {
        return appliedRevision;
    }

    public static void reset() {
        cameraOverride = null;
        fovOverride = null;
        transparent = false;
        setReplayPov(false);
        preparedLevel = null;
        sentTime = null;
        sentWeather = null;
        PoseEditorCommands.reset();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
    }

    public static void register(CommandRegistry registry) {
        registry.register("scene.apply", SceneFeature::apply);
        SceneModelCommands.register(registry);
        PoseEditorCommands.register(registry);
        ClientHooks.onEndTick(mc -> {
            if (!Ymm4Craft.isActive()) return;
            SceneModels.ensureLevel(mc.level);
            SceneModels.onClientTick();
        });
    }

    private static void setReplayPov(boolean value) {
        replayPov = value;
        ReplayPlayback.setPov(value);
    }

    private static CompletableFuture<JsonElement> apply(CommandParams p) throws CommandException {
        long revision = p.has("revision") ? p.getLong("revision", 0, Long.MAX_VALUE / 2) : -1;
        Integer time = p.has("time") ? p.getInt("time", 0, 23999) : null;
        String weather = p.has("weather") ? p.getChoice("weather", WEATHERS) : null;
        double[] camera = null;
        if (p.has("camera")) {
            CommandParams c = p.getObject("camera");
            camera = new double[]{
                    c.getDouble("x", -29_999_984, 29_999_984),
                    c.getDouble("y", -2048, 4096),
                    c.getDouble("z", -29_999_984, 29_999_984),
                    c.getDouble("yaw", -360, 360),
                    c.getDouble("pitch", -90, 90),
            };
        }
        Float fov = p.has("fov") ? (float) p.getDouble("fov", 1, 170) : null;
        Boolean hideHud = p.has("hideHud") ? p.getBoolean("hideHud") : null;
        Boolean transparentBackground = p.has("transparent") ? p.getBoolean("transparent") : null;
        List<ModelSpec> models = p.has("models")
                ? ModelSpecParser.parse(p.getObjectArray("models", ModelSpecParser.MAX_MODELS))
                : null;
        Double replayTime = null;
        Boolean pov = null;
        Boolean hand = null;
        Boolean screens = null;
        Boolean cursor = null;
        Boolean chat = null;
        boolean exact = false;
        String target = null;
        if (p.has("replay")) {
            CommandParams r = p.getObject("replay");
            if (r.has("time")) replayTime = r.getDouble("time", 0, 7 * 24 * 3600);
            if (r.has("camera")) pov = r.getChoice("camera", REPLAY_CAMERAS).equals("pov");
            if (r.has("hand")) hand = r.getBoolean("hand");
            if (r.has("screens")) screens = r.getBoolean("screens");
            if (r.has("cursor")) cursor = r.getBoolean("cursor");
            if (r.has("chat")) chat = r.getBoolean("chat");
            if (r.has("exact")) exact = r.getBoolean("exact");
            if (r.has("player")) target = r.getString("player", PLAYER_NAME);
        }
        final Double seekTo = replayTime;
        final Boolean povCamera = pov;
        final Boolean showHand = hand;
        final Boolean showScreens = screens;
        final Boolean showCursor = cursor;
        final Boolean showChat = chat;
        final boolean exactTime = exact;
        final String povTarget = target;
        final double[] cam = camera;
        Minecraft mc = Minecraft.getInstance();

        if (ReplayPlayback.currentName() != null) {
            return CommandRegistry.onMainThread(() -> {
                if (!ReplayPlayback.active()) throw new RuntimeException(new CommandException("リプレイを開いていません"));
                applyClientSide(mc, cam, fov, hideHud, transparentBackground);
                if (povCamera != null) setReplayPov(povCamera);
                if (showHand != null) ReplayPlayback.setShowHand(showHand);
                if (showScreens != null) ReplayPlayback.setShowScreens(showScreens);
                if (showCursor != null) ReplayPlayback.setShowCursor(showCursor);
                if (showChat != null) ReplayPlayback.setShowChat(showChat);
                if (povTarget != null) ReplayPlayback.setPovTarget(povTarget);
                if (models != null && mc.level != null) SceneModels.logProblems(SceneModels.apply(mc.level, models));
                CompletableFuture<Void> reached = seekTo != null ? ReplayPlayback.seek(seekTo, exactTime) : CompletableFuture.completedFuture(null);
                reached.thenCompose(ignored -> markAppliedLater(revision));
                return null;
            });
        }

        return CommandRegistry.<List<String>>onMainThread(() -> {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null || mc.player == null || mc.level == null) {
                throw new RuntimeException(new CommandException("ワールドを開いていません"));
            }
            setReplayPov(false);
            applyClientSide(mc, cam, fov, hideHud, transparentBackground);
            if (models != null) SceneModels.logProblems(SceneModels.apply(mc.level, models));
            return serverCommands(mc, time, weather);
        }).thenCompose(commands -> {
            if (commands.isEmpty()) {
                markApplied(revision);
                return CompletableFuture.completedFuture(null);
            }
            return SceneServerCommands.run(mc.getSingleplayerServer(), mc.player.getUUID(), commands)
                    .thenCompose(ignored -> markAppliedLater(revision));
        });
    }

    private static void applyClientSide(Minecraft mc, double[] cam, Float fov, Boolean hideHud, Boolean transparentBackground) {
        if (cam != null) cameraOverride = cam;
        if (fov != null) fovOverride = fov;
        if (hideHud != null && mc.gui.hud.isHidden() != hideHud) mc.gui.hud.toggle();
        if (transparentBackground != null) transparent = transparentBackground;
    }

    private static List<String> serverCommands(Minecraft mc, Integer time, String weather) {
        List<String> commands = new ArrayList<>();
        if (preparedLevel != mc.level) {
            preparedLevel = mc.level;
            sentTime = null;
            sentWeather = null;
            commands.add("tick freeze");
            commands.add("gamerule advance_time false");
            commands.add("gamerule advance_weather false");
            commands.add("gamerule spawn_mobs false");
        }
        if (time != null && !time.equals(sentTime)) {
            sentTime = time;
            commands.add("time set " + time);
        }
        if (weather != null && !Objects.equals(weather, sentWeather)) {
            sentWeather = weather;
            commands.add("weather " + weather);
            mc.level.setRainLevel(weather.equals("clear") ? 0f : 1f);
            mc.level.setThunderLevel(weather.equals("thunder") ? 1f : 0f);
        }
        return commands;
    }

    private static CompletableFuture<JsonElement> markAppliedLater(long revision) {
        return CommandRegistry.onMainThread(() -> {
            markApplied(revision);
            return null;
        });
    }

    private static void markApplied(long revision) {
        if (revision > appliedRevision) appliedRevision = revision;
    }
}
