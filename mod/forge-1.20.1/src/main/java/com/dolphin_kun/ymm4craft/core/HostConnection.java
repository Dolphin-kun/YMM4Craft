package com.dolphin_kun.ymm4craft.core;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class HostConnection {
    private static final long HEARTBEAT_TIMEOUT_MS = 10_000;
    private static final long CONNECT_TIMEOUT_MS = 5_000;
    private static final long HELLO_TIMEOUT_MS = 5_000;
    private static final int MAX_LINE_BYTES = 16 << 20;

    private static final Gson GSON = new Gson();

    private final Session session;
    private final CommandRegistry registry;
    private final Object writeLock = new Object();

    private SocketChannel channel;
    private volatile long lastReceivedAt;
    private volatile boolean closed;

    public HostConnection(Session session, CommandRegistry registry) {
        this.session = session;
        this.registry = registry;
    }

    public void start() throws IOException {
        channel = connectWithRetry();

        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("protocol", Session.PROTOCOL_VERSION);
        hello.addProperty("token", session.token());
        hello.addProperty("modVersion", modVersion());
        hello.addProperty("gameVersion", SharedConstants.getCurrentVersion().getName());
        JsonArray commands = new JsonArray();
        registry.types().stream().sorted().forEach(commands::add);
        hello.add("commands", commands);
        send(hello);

        Thread helloTimer = new Thread(() -> {
            try {
                Thread.sleep(HELLO_TIMEOUT_MS);
                if (lastReceivedAt == 0) closeQuietly();
            } catch (InterruptedException ignored) {
            }
        }, "YMM4Craft-HelloTimer");
        helloTimer.setDaemon(true);
        helloTimer.start();

        JsonObject welcome;
        try {
            welcome = readMessage();
            lastReceivedAt = System.currentTimeMillis();
        } catch (IOException e) {
            throw new IOException("ホストから応答がありません", e);
        } finally {
            helloTimer.interrupt();
        }
        if (welcome == null || !"welcome".equals(stringOrNull(welcome, "type"))) {
            throw new IOException("ホストに接続を拒否されました");
        }
        lastReceivedAt = System.currentTimeMillis();
        Ymm4Craft.LOGGER.info("YMM4と接続しました");

        Thread reader = new Thread(this::readLoop, "YMM4Craft-Reader");
        reader.setDaemon(true);
        reader.start();

        Thread watchdog = new Thread(this::watchLoop, "YMM4Craft-Watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private SocketChannel connectWithRetry() throws IOException {
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(session.socketPath());
        long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
        while (true) {
            SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
            try {
                ch.connect(address);
                return ch;
            } catch (IOException e) {
                ch.close();
                if (System.currentTimeMillis() > deadline) throw e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    public void send(JsonObject message) throws IOException {
        byte[] bytes = (GSON.toJson(message) + "\n").getBytes(StandardCharsets.UTF_8);
        synchronized (writeLock) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) channel.write(buf);
        }
    }

    public void sendEvent(String name, JsonElement data) {
        JsonObject event = new JsonObject();
        event.addProperty("type", "event");
        event.addProperty("name", name);
        if (data != null) event.add("data", data);
        try {
            send(event);
        } catch (IOException e) {
            lost("イベントの送信に失敗: " + e.getMessage());
        }
    }

    private final ByteBuffer readBuffer = ByteBuffer.allocate(64 * 1024);
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();

    private JsonObject readMessage() throws IOException {
        while (true) {
            byte[] data = pending.toByteArray();
            for (int i = 0; i < data.length; i++) {
                if (data[i] == '\n') {
                    String line = new String(data, 0, i, StandardCharsets.UTF_8);
                    pending.reset();
                    pending.write(data, i + 1, data.length - i - 1);
                    JsonElement parsed = JsonParser.parseString(line);
                    if (!parsed.isJsonObject()) throw new IOException("メッセージがJSONオブジェクトではありません");
                    return parsed.getAsJsonObject();
                }
            }
            if (data.length > MAX_LINE_BYTES) throw new IOException("メッセージが長すぎます");

            readBuffer.clear();
            int n = channel.read(readBuffer);
            if (n < 0) return null;
            pending.write(readBuffer.array(), 0, n);
        }
    }

    private void readLoop() {
        try {
            while (!closed) {
                JsonObject message = readMessage();
                if (message == null) {
                    lost("ホストが接続を閉じました");
                    return;
                }
                lastReceivedAt = System.currentTimeMillis();
                dispatch(message);
            }
        } catch (Throwable t) {
            if (!closed) lost("受信エラー: " + t.getMessage());
        }
    }

    private void dispatch(JsonObject message) throws IOException {
        String type = stringOrNull(message, "type");
        if ("ping".equals(type)) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "pong");
            send(pong);
            return;
        }
        if (!"request".equals(type)) {
            return;
        }

        long id = message.has("id") ? message.get("id").getAsLong() : -1;
        String command = stringOrNull(message, "command");
        CommandRegistry.Handler handler = command == null ? null : registry.find(command);
        if (handler == null) {
            respondError(id, "対応していないコマンドです: " + command);
            return;
        }

        CompletableFuture<JsonElement> future;
        try {
            JsonElement params = message.get("params");
            future = handler.handle(new CommandParams(
                    params != null && params.isJsonObject() ? params.getAsJsonObject() : null));
        } catch (CommandException e) {
            respondError(id, e.getMessage());
            return;
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("コマンド {} の処理に失敗しました", command, t);
            respondError(id, "Mod内部のエラー: " + t);
            return;
        }

        future.whenComplete((result, error) -> {
            try {
                if (error == null) {
                    respondOk(id, result);
                } else {
                    Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                    if (!(cause instanceof CommandException)) {
                        Ymm4Craft.LOGGER.warn("コマンド {} の処理に失敗しました", command, cause);
                    }
                    respondError(id, cause instanceof CommandException ? cause.getMessage() : "Mod内部のエラー: " + cause);
                }
            } catch (IOException e) {
                lost("応答の送信に失敗: " + e.getMessage());
            }
        });
    }

    private void respondOk(long id, JsonElement result) throws IOException {
        JsonObject r = new JsonObject();
        r.addProperty("type", "response");
        r.addProperty("id", id);
        r.addProperty("ok", true);
        if (result != null) r.add("result", result);
        send(r);
    }

    private void respondError(long id, String error) throws IOException {
        JsonObject r = new JsonObject();
        r.addProperty("type", "response");
        r.addProperty("id", id);
        r.addProperty("ok", false);
        r.addProperty("error", error);
        send(r);
    }

    private void watchLoop() {
        while (!closed) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }
            if (System.currentTimeMillis() - lastReceivedAt > HEARTBEAT_TIMEOUT_MS) {
                lost("ハートビートが途絶えました");
                return;
            }
        }
    }

    private void lost(String reason) {
        if (closed) return;
        closeQuietly();
        Ymm4Craft.onHostLost(reason);
    }

    public void closeQuietly() {
        closed = true;
        try {
            if (channel != null) channel.close();
        } catch (IOException ignored) {
        }
    }

    private static String stringOrNull(JsonObject o, String name) {
        JsonElement e = o.get(name);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static String modVersion() {
        return Platforms.get().modVersion();
    }
}
