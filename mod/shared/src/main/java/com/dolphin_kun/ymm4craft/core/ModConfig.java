package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public volatile boolean autoConnect;
    public volatile boolean recordReplays;
    public volatile boolean recordSingleplayer = true;
    public volatile boolean recordMultiplayer = true;
    public volatile boolean notifyRecording = true;

    private static volatile ModConfig current;

    private ModConfig() {
    }

    public static ModConfig get() {
        ModConfig c = current;
        if (c == null) {
            synchronized (ModConfig.class) {
                if (current == null) current = load();
                c = current;
            }
        }
        return c;
    }

    static Path file() {
        return Platforms.get().configDir().resolve("ymm4craft.json");
    }

    private static ModConfig load() {
        ModConfig c = new ModConfig();
        Path path = file();
        if (!Files.isRegularFile(path)) return c;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            c.autoConnect = bool(o, "autoConnect", bool(o, "linkWithoutLaunchToken", c.autoConnect));
            if (o.has("replay") && o.get("replay").isJsonObject()) {
                JsonObject r = o.getAsJsonObject("replay");
                c.recordReplays = bool(r, "record", c.recordReplays);
                c.recordSingleplayer = bool(r, "singleplayer", c.recordSingleplayer);
                c.recordMultiplayer = bool(r, "multiplayer", c.recordMultiplayer);
                c.notifyRecording = bool(r, "notify", c.notifyRecording);
            }
        } catch (Exception e) {
            Ymm4Craft.LOGGER.warn("設定ファイルを読めなかったので、既定の設定を使います: {}", e.toString());
            return new ModConfig();
        }
        return c;
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsBoolean() : fallback;
    }

    public void save() {
        JsonObject o = new JsonObject();
        o.addProperty("autoConnect", autoConnect);
        JsonObject r = new JsonObject();
        r.addProperty("record", recordReplays);
        r.addProperty("singleplayer", recordSingleplayer);
        r.addProperty("multiplayer", recordMultiplayer);
        r.addProperty("notify", notifyRecording);
        o.add("replay", r);
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("ymm4craft.json.tmp");
            Files.writeString(tmp, GSON.toJson(o), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Ymm4Craft.LOGGER.warn("設定ファイルを保存できませんでした", e);
        }
    }
}
