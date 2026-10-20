package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public record Session(
        int protocol,
        long hostPid,
        long createdAtUnixMs,
        String token,
        List<String> features,
        Path socketPath,
        Path frameFile,
        long frameCapacity) {
    public static final int PROTOCOL_VERSION = 1;

    static final Duration MAX_AGE = Duration.ofMinutes(3);
    static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    static final long MAX_FRAME_CAPACITY = 64L + 7680L * 4320L * 4L;

    public static final String REQUEST_FILE = "session.json";
    public static final String ACCEPTED_FILE = "session.accepted.json";

    static final String MANUAL_PROPERTY = "ymm4craft.manual";

    public static boolean startedForYmm4() {
        return Boolean.getBoolean(MANUAL_PROPERTY);
    }

    public static Path baseDir() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) return null;
        return Path.of(local, "YMM4Craft");
    }

    public static Session tryAccept() throws IOException {
        Path dir = baseDir();
        if (dir == null) return null;
        Path request = dir.resolve(REQUEST_FILE);
        if (!Files.isRegularFile(request)) return null;

        if (!startedForYmm4()) return null;

        Session candidate = parse(Files.readString(request, StandardCharsets.UTF_8), dir);
        String problem = candidate.validate(System.currentTimeMillis());
        if (problem != null) {
            if (candidate.otherVersion()) SessionRejection.reportVersion(dir, candidate);
            Ymm4Craft.LOGGER.info("セッション要求を無視しました: {}", problem);
            return null;
        }

        Path accepted = dir.resolve(ACCEPTED_FILE);
        try {
            Files.move(request, accepted, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException e) {
            Ymm4Craft.LOGGER.info("セッション要求は別のプロセスが受理済みでした");
            return null;
        }

        Session session = parse(Files.readString(accepted, StandardCharsets.UTF_8), dir);
        problem = session.validate(System.currentTimeMillis());
        if (problem != null) {
            Ymm4Craft.LOGGER.info("セッション要求を無視しました: {}", problem);
            return null;
        }
        Ymm4Craft.LOGGER.info("YMM4用の起動構成（-Dymm4craft.manual=true）で起動されたMinecraftです");
        return session;
    }

    public static Session peekWaiting() {
        Session candidate = readWaiting();
        if (candidate == null) return null;
        if (candidate.otherVersion()) {
            SessionRejection.reportVersion(baseDir(), candidate);
            return null;
        }
        return candidate.validate(System.currentTimeMillis()) == null ? candidate : null;
    }

    public static boolean waitingWithOtherVersion() {
        Session candidate = readWaiting();
        return candidate != null && candidate.otherVersion();
    }

    private static Session readWaiting() {
        try {
            Path dir = baseDir();
            if (dir == null) return null;
            Path request = dir.resolve(REQUEST_FILE);
            if (!Files.isRegularFile(request)) return null;
            Session candidate = parse(Files.readString(request, StandardCharsets.UTF_8), dir);
            return candidate.freshness(System.currentTimeMillis()) == null ? candidate : null;
        } catch (Exception e) {
            return null;
        }
    }

    boolean otherVersion() {
        return protocol != PROTOCOL_VERSION && freshness(System.currentTimeMillis()) == null;
    }

    public static Session acceptByUser() throws IOException {
        Path dir = baseDir();
        if (dir == null) throw new IOException("YMM4Craftのフォルダが見つかりません");
        Path request = dir.resolve(REQUEST_FILE);
        if (!Files.isRegularFile(request)) throw new IOException("接続を待っているYMM4がありません");
        Path accepted = dir.resolve(ACCEPTED_FILE);
        try {
            Files.move(request, accepted, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (NoSuchFileException e) {
            throw new IOException("ほかのMinecraftが先に接続しました");
        }
        Session session = parse(Files.readString(accepted, StandardCharsets.UTF_8), dir);
        String problem = session.validate(System.currentTimeMillis());
        if (problem != null && session.otherVersion()) {
            SessionRejection.reportVersion(dir, session);
            throw new IOException("YMM4 のプラグインと Mod の版が合いません。両方を同じ版にそろえてください");
        }
        if (problem != null) throw new IOException(problem);
        Ymm4Craft.LOGGER.info("ゲーム内のボタンで、YMM4と接続します");
        return session;
    }

    static Session parse(String json, Path baseDir) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        List<String> features = new ArrayList<>();
        if (o.has("features")) {
            for (JsonElement e : o.getAsJsonArray("features")) features.add(e.getAsString());
        }
        return new Session(
                o.get("protocol").getAsInt(),
                o.get("hostPid").getAsLong(),
                o.get("createdAtUnixMs").getAsLong(),
                o.get("token").getAsString(),
                List.copyOf(features),
                insideBase(baseDir, o.get("socketPath").getAsString()),
                insideBase(baseDir, o.get("frameFile").getAsString()),
                o.get("frameCapacity").getAsLong());
    }

    private static Path insideBase(Path baseDir, String raw) {
        Path p = Path.of(raw).toAbsolutePath().normalize();
        if (!p.startsWith(baseDir.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("YMM4Craftのフォルダの外を指すパスは使えません: " + raw);
        }
        return p;
    }

    String validate(long nowUnixMs) {
        if (protocol != PROTOCOL_VERSION) {
            return "プロトコルのバージョンが違います（要求: " + protocol + ", Mod: " + PROTOCOL_VERSION + "）";
        }
        String stale = freshness(nowUnixMs);
        if (stale != null) return stale;
        if (frameCapacity <= 64 || frameCapacity > MAX_FRAME_CAPACITY) return "フレーム用ファイルの容量が不正です";
        return null;
    }

    private String freshness(long nowUnixMs) {
        long age = nowUnixMs - createdAtUnixMs;
        if (age > MAX_AGE.toMillis()) return "要求の有効期限が切れています";
        if (age < -CLOCK_SKEW.toMillis()) return "要求の作成時刻が未来になっています";
        if (token == null || token.length() < 32) return "トークンが短すぎます";
        if (!isProcessAlive(hostPid)) return "要求を書き出したYMM4のプロセスが終了しています";
        return null;
    }

    private static boolean isProcessAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    @Override
    public String toString() {
        return "Session[protocol=" + protocol + ", hostPid=" + hostPid + ", features=" + features + "]";
    }
}
