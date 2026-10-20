package com.dolphin_kun.ymm4craft.feature.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class ReplayFiles {
    static final byte[] MAGIC = {'Y', 'M', 'R', '1'};
    public static final byte LOGIN = 0;
    public static final byte CONFIGURATION = 1;
    public static final byte PLAY = 2;
    public static final byte ANY_PHASE = 3;
    public static final byte DECODED = 4;
    public static final byte MARK_READY = 10;
    public static final byte INVENTORY_SCREEN = 11;
    public static final byte CURSOR = 12;
    public static final byte MERCHANT_SELECTION = 13;
    public static final byte CURSOR_GUI = 14;

    static final int MAX_ENTRY = 16 * 1024 * 1024;

    public static final Pattern NAME = Pattern.compile("[^/\\\\:*?\"<>|\\p{Cntrl}.][^/\\\\:*?\"<>|\\p{Cntrl}]{0,127}");

    private ReplayFiles() {
    }

    public static Path folder(Path gameDir) {
        return gameDir.resolve("ymm4craft").resolve("replays");
    }

    static Path flashbackFolder(Path gameDir) {
        return gameDir.resolve("flashback").resolve("replays");
    }

    static Path replayModFolder(Path gameDir) {
        return gameDir.resolve("replay_recordings");
    }

    static Path dataFile(Path folder, String name) {
        return folder.resolve(name + ".ymr");
    }

    static Path infoFile(Path folder, String name) {
        return folder.resolve(name + ".json");
    }

    static void writeInfo(Path folder, String name, JsonObject info) throws IOException {
        Path file = infoFile(folder, name);
        Path tmp = folder.resolve(name + ".json.tmp");
        Files.writeString(tmp, info.toString(), StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    static JsonObject readInfo(Path folder, String name) {
        Path file = infoFile(folder, name);
        try {
            if (!Files.isRegularFile(file)) return null;
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    static List<String> list(Path folder) throws IOException {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(folder)) return names;
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(p -> p.getFileName().toString().endsWith(".ymr") && Files.isRegularFile(p))
                    .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                    .forEach(p -> {
                        String file = p.getFileName().toString();
                        names.add(file.substring(0, file.length() - 4));
                    });
        }
        return names;
    }
}
