package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.core.ClientHooks;
import com.dolphin_kun.ymm4craft.core.WorldCommands;
import com.dolphin_kun.ymm4craft.core.CommandParams;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;

public final class ReplayFeature {
    private static final Pattern WAV_PATH = Pattern.compile("[^\\x00-\\x1f*?\"<>|]{1,1024}\\.wav");
    private static final int MAX_CAMERA_SAMPLES = 200_000;

    private ReplayFeature() {
    }

    public static void registerRecorder() {
        ClientHooks.onEndTick(ReplayRecorder::onClientTick);
        ClientHooks.onStopping(mc -> ReplayRecorder.onGameStopping());
    }

    public static void register(CommandRegistry registry) {
        registry.register("replay.list", p -> CommandRegistry.onMainThread(ReplayFeature::list));
        registry.register("replay.open", ReplayFeature::open);
        registry.register("replay.exportAudio", ReplayFeature::exportAudio);
        registry.register("replay.players", p -> CommandRegistry.onMainThread(ReplayFeature::players));
        WorldCommands.addCloseHook(ReplayPlayback::close);
    }

    private static Path gameDir() {
        return Minecraft.getInstance().gameDirectory.toPath();
    }

    private static JsonElement list() {
        JsonObject result = new JsonObject();
        try {
            result.add("replays", ReplayCatalog.list(gameDir()));
        } catch (IOException e) {
            throw new RuntimeException(new CommandException("リプレイの一覧を取得できませんでした: " + e.getMessage()));
        }
        return result;
    }

    private static CompletableFuture<JsonElement> exportAudio(CommandParams p) throws CommandException {
        double from = p.getDouble("from", 0, 7 * 24 * 3600);
        double to = p.getDouble("to", from, from + 6 * 3600);
        Path out = Path.of(p.getString("path", WAV_PATH));
        if (!out.isAbsolute() || out.getParent() == null || !Files.isDirectory(out.getParent())) throw new CommandException("書き出し先のフォルダがありません");
        double[][] camera = null;
        if (p.has("camera")) {
            List<CommandParams> samples = p.getObjectArray("camera", MAX_CAMERA_SAMPLES);
            camera = new double[samples.size()][];
            for (int i = 0; i < samples.size(); i++) {
                CommandParams c = samples.get(i);
                camera[i] = new double[]{c.getDouble("t", 0, 7 * 24 * 3600 + 6 * 3600),
                        c.getDouble("x", -29_999_984, 29_999_984), c.getDouble("y", -2048, 4096), c.getDouble("z", -29_999_984, 29_999_984),
                        c.getDouble("yaw", -360, 360), c.getDouble("pitch", -90, 90)};
            }
        }
        double[][] listener = camera;
        boolean exclusive = p.has("exclusive") && p.getBoolean("exclusive");
        return CommandRegistry.<CompletableFuture<JsonObject>>onMainThread(() -> ReplayPlayback.exportAudio(from, to, listener, out, exclusive))
                .thenCompose(f -> f)
                .<JsonElement>thenApply(o -> o)
                .exceptionallyCompose(t -> CompletableFuture.failedFuture(new CommandException("音を書き出せませんでした: " + rootMessage(t))));
    }

    private static JsonElement players() {
        JsonArray names = new JsonArray();
        ReplayPlayback.playerNames().forEach(names::add);
        JsonObject result = new JsonObject();
        result.addProperty("recorded", ReplayPlayback.recordedPlayerName());
        result.add("players", names);
        return result;
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private static CompletableFuture<JsonElement> open(CommandParams p) throws CommandException {
        String name = p.getString("name", ReplayFiles.NAME);
        return WorldCommands.closeWorldThen(() -> {
            try {
                ReplayPlayback.open(gameDir(), name);
            } catch (IOException e) {
                throw new RuntimeException(new CommandException(e.getMessage()));
            }
            WorldCommands.markOpened("replay:" + name);
            JsonObject o = new JsonObject();
            o.addProperty("replay", name);
            return o;
        });
    }
}
