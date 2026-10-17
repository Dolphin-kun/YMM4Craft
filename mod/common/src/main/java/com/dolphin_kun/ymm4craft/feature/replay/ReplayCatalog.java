package com.dolphin_kun.ymm4craft.feature.replay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class ReplayCatalog {
    static final String MCPR = ".mcpr";
    static final String FLASHBACK = ".zip";

    private ReplayCatalog() {
    }

    record Located(ReplayInput input, JsonObject info) {
    }

    static JsonArray list(Path gameDir) throws IOException {
        List<JsonObject> found = new ArrayList<>();
        Path folder = ReplayFiles.folder(gameDir);
        for (String name : ReplayFiles.list(folder)) {
            JsonObject o = new JsonObject();
            o.addProperty("name", name);
            JsonObject info = ReplayFiles.readInfo(folder, name);
            if (info != null) {
                for (String key : new String[]{"durationMs", "world", "player", "startedAt", "gameVersion"}) {
                    if (info.has(key)) o.add(key, info.get(key));
                }
            }
            o.addProperty("playable", playable(info));
            found.add(o);
        }
        for (Path file : files(ReplayFiles.replayModFolder(gameDir), MCPR)) {
            JsonObject info = replayModInfo(file);
            JsonObject o = new JsonObject();
            o.addProperty("name", file.getFileName().toString());
            o.addProperty("source", "ReplayMod");
            for (String key : new String[]{"durationMs", "world", "startedAt", "gameVersion"}) {
                if (info.has(key)) o.add(key, info.get(key));
            }
            o.addProperty("playable", playable(info));
            found.add(o);
        }
        for (Path file : files(ReplayFiles.flashbackFolder(gameDir), FLASHBACK)) {
            JsonObject info = flashbackInfo(file);
            JsonObject o = new JsonObject();
            o.addProperty("name", file.getFileName().toString());
            o.addProperty("source", "Flashback");
            for (String key : new String[]{"durationMs", "world", "startedAt", "gameVersion"}) {
                if (info.has(key)) o.add(key, info.get(key));
            }
            o.addProperty("playable", playable(info));
            found.add(o);
        }
        found.sort(Comparator.comparing((JsonObject o) -> o.has("startedAt") ? o.get("startedAt").getAsString() : "").reversed());
        JsonArray array = new JsonArray();
        found.forEach(array::add);
        return array;
    }

    static Located locate(Path gameDir, String name) throws IOException {
        if (name.endsWith(MCPR)) {
            Path file = ReplayFiles.replayModFolder(gameDir).resolve(name);
            if (!Files.isRegularFile(file)) throw new IOException("リプレイが見つかりません: " + name);
            return new Located(new McprInput(file), replayModInfo(file));
        }
        if (name.endsWith(FLASHBACK)) {
            Path file = ReplayFiles.flashbackFolder(gameDir).resolve(name);
            if (!Files.isRegularFile(file)) throw new IOException("リプレイが見つかりません: " + name);
            return new Located(new FlashbackInput(file), flashbackInfo(file));
        }
        Path folder = ReplayFiles.folder(gameDir);
        Path file = ReplayFiles.dataFile(folder, name);
        if (!Files.isRegularFile(file)) throw new IOException("リプレイが見つかりません: " + name);
        JsonObject info = ReplayFiles.readInfo(folder, name);
        return new Located(new YmrInput(file, ReplayBlockIndex.of(info)), info);
    }

    static boolean playable(JsonObject info) {
        return info == null || !info.has("protocol") || info.get("protocol").getAsInt() == SharedConstants.getProtocolVersion();
    }

    private static JsonObject replayModInfo(Path file) {
        JsonObject info = new JsonObject();
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry(McprReader.META);
            if (entry == null) return info;
            try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                JsonObject meta = JsonParser.parseReader(reader).getAsJsonObject();
                if (meta.has("protocol")) info.add("protocol", meta.get("protocol"));
                if (meta.has("mcversion")) info.add("gameVersion", meta.get("mcversion"));
                if (meta.has("duration")) info.add("durationMs", meta.get("duration"));
                if (meta.has("serverName")) info.add("world", meta.get("serverName"));
                if (meta.has("date")) {
                    info.addProperty("startedAt", LocalDateTime.ofInstant(Instant.ofEpochMilli(meta.get("date").getAsLong()), ZoneId.systemDefault()).toString());
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return info;
    }

    private static JsonObject flashbackInfo(Path file) {
        JsonObject info = new JsonObject();
        try (FileSystem zip = FileSystems.newFileSystem(file)) {
            JsonObject meta = FlashbackReader.readMetadata(zip.getPath("/"));
            if (meta.has("protocol_version")) info.add("protocol", meta.get("protocol_version"));
            if (meta.has("version_string")) info.add("gameVersion", meta.get("version_string"));
            if (meta.has("total_ticks")) info.addProperty("durationMs", meta.get("total_ticks").getAsLong() * 50);
            if (meta.has("world_name")) info.add("world", meta.get("world_name"));
            info.addProperty("startedAt", LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault()).toString());
        } catch (IOException | RuntimeException ignored) {
        }
        return info;
    }

    private static List<Path> files(Path folder, String extension) throws IOException {
        if (!Files.isDirectory(folder)) return List.of();
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(extension)
                    && ReplayFiles.NAME.matcher(p.getFileName().toString()).matches()).toList();
        }
    }
}
