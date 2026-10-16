package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class SessionRejection {
    static final String FILE = "session.rejected.json";

    private SessionRejection() {
    }

    static void reportVersion(Path dir, Session request) {
        if (dir == null) return;
        Path file = dir.resolve(FILE);
        try {
            if (Files.isRegularFile(file)) {
                JsonObject old = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                if (old.has("token") && old.get("token").getAsString().equals(request.token())) return;
            }
            JsonObject o = new JsonObject();
            o.addProperty("token", request.token());
            o.addProperty("reason", "version");
            o.addProperty("modProtocol", Session.PROTOCOL_VERSION);
            o.addProperty("modVersion", HostConnection.modVersion());
            Path tmp = dir.resolve(FILE + ".tmp");
            Files.writeString(tmp, o.toString(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            Ymm4Craft.LOGGER.warn("版が合わないことを YMM4 に伝えられませんでした", e);
        }
    }
}
