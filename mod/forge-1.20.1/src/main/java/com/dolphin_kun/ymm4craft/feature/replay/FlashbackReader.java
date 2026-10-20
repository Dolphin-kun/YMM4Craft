package com.dolphin_kun.ymm4craft.feature.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class FlashbackReader implements ReplaySource {
    static final String METADATA = "metadata.json";

    FlashbackReader(Path file) throws IOException {
        throw new IOException("この版の Minecraft では Flashback のリプレイは開けません");
    }

    static JsonObject readMetadata(Path root) throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve(METADATA), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Override
    public ReplayEntry next() throws IOException {
        return null;
    }

    @Override
    public long index() {
        return 0;
    }

    @Override
    public void close() {
    }
}
