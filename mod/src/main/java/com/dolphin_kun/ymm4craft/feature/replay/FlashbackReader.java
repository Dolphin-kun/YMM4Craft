package com.dolphin_kun.ymm4craft.feature.replay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.minecraft.network.protocol.login.LoginProtocols;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.login.ClientboundLoginFinishedPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FlashbackReader implements ReplaySource {
    static final String METADATA = "metadata.json";
    private static final int MAGIC = 0xD780E884;
    private static final String ACTION = "flashback:action/";
    private static final UUID SESSION = new UUID(0, 0);

    private final FileSystem zip;
    private final Path root;
    private final List<String> chunkFiles = new ArrayList<>();
    private final List<Boolean> forceSnapshot = new ArrayList<>();
    private final FlashbackChunkCache cache;
    private final ArrayDeque<ReplayEntry> queued = new ArrayDeque<>();

    private int chunk = -1;
    private DataInputStream in;
    private String[] actions;
    private long snapshotRemaining;
    private boolean ready;
    private boolean inPlay;
    private int tick;
    private long index;
    private ReplayEntry localPlayer;
    private PositionMoveRotation localPosition;
    private int localPlayerId = Integer.MIN_VALUE;

    FlashbackReader(Path file) throws IOException {
        if (Files.isDirectory(file)) {
            zip = null;
            root = file;
        } else {
            zip = FileSystems.newFileSystem(file);
            root = zip.getPath("/");
        }
        try {
            JsonObject meta = readMetadata(root);
            if (meta.get("chunks") instanceof JsonObject chunks) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : chunks.entrySet()) {
                    chunkFiles.add(e.getKey());
                    JsonObject info = e.getValue().isJsonObject() ? e.getValue().getAsJsonObject() : new JsonObject();
                    forceSnapshot.add(info.has("forcePlaySnapshot") && info.get("forcePlaySnapshot").getAsBoolean());
                }
            }
            if (chunkFiles.isEmpty()) throw new IOException("Flashback のリプレイに記録がありません");
            cache = new FlashbackChunkCache(root.resolve("level_chunk_caches"));
            queued.add(new ReplayEntry(0, ReplayFiles.LOGIN, encode(LoginProtocols.CLIENTBOUND,
                    new ClientboundLoginFinishedPacket(new GameProfile(SESSION, "Flashback"), SESSION))));
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

    static JsonObject readMetadata(Path root) throws IOException {
        return JsonParser.parseString(Files.readString(root.resolve(METADATA), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Override
    public ReplayEntry next() throws IOException {
        while (queued.isEmpty()) {
            if (!readAction()) return null;
        }
        index++;
        return queued.poll();
    }

    @Override
    public long index() {
        return index;
    }

    private boolean readAction() throws IOException {
        if (in == null && !openNextChunk()) return false;
        boolean inSnapshot = snapshotRemaining > 0;
        int id;
        int size;
        try {
            id = readVarInt(in);
            size = in.readInt();
        } catch (EOFException e) {
            in.close();
            in = null;
            return true;
        }
        if (size < 0 || size > ReplayFiles.MAX_ENTRY) throw new IOException("Flashback のリプレイが壊れています（記録の長さ " + size + "）");
        byte[] payload = new byte[size];
        in.readFully(payload);
        if (inSnapshot) {
            snapshotRemaining -= varIntSize(id) + 4L + size;
            if (snapshotRemaining <= 0 && !ready) finishSnapshot();
        }
        String action = id >= 0 && id < actions.length ? actions[id] : "";
        int time = tick * 50;
        switch (action) {
            case ACTION + "next_tick" -> tick++;
            case ACTION + "configuration_packet" -> queued.add(new ReplayEntry(time, ReplayFiles.CONFIGURATION, payload));
            case ACTION + "game_packet" -> {
                if (!inPlay) {
                    inPlay = true;
                    queued.add(new ReplayEntry(time, ReplayFiles.CONFIGURATION, encode(ConfigurationProtocols.CLIENTBOUND, ClientboundFinishConfigurationPacket.INSTANCE)));
                }
                if (localPlayerId == Integer.MIN_VALUE && payload.length > 5) localPlayerId = loginPlayerId(payload);
                queued.add(new ReplayEntry(time, ReplayFiles.PLAY, payload));
            }
            case ACTION + "level_chunk_cached" -> {
                byte[] packet = cache.get(readVarInt(new DataInputStream(new ByteArrayInputStream(payload))));
                if (packet != null) queued.add(new ReplayEntry(time, ReplayFiles.PLAY, packet));
            }
            case ACTION + "create_local_player" -> localPlayer = createLocalPlayer(payload, time);
            case ACTION + "move_entities" -> moveEntities(payload, time);
            default -> {
            }
        }
        return true;
    }

    private boolean openNextChunk() throws IOException {
        if (in != null) in.close();
        in = null;
        chunk++;
        if (chunk >= chunkFiles.size()) {
            if (!ready) finishSnapshot();
            return false;
        }
        InputStream raw = Files.newInputStream(root.resolve(chunkFiles.get(chunk)));
        in = new DataInputStream(new BufferedInputStream(raw, 64 * 1024));
        if (in.readInt() != MAGIC) throw new IOException("Flashback のリプレイではありません");
        int count = readVarInt(in);
        actions = new String[count];
        for (int i = 0; i < count; i++) {
            byte[] name = new byte[readVarInt(in)];
            in.readFully(name);
            actions[i] = new String(name, StandardCharsets.UTF_8);
        }
        int snapshot = in.readInt();
        if (chunk == 0 || forceSnapshot.get(chunk)) {
            snapshotRemaining = snapshot;
        } else {
            in.skipNBytes(snapshot);
            snapshotRemaining = 0;
        }
        if (snapshotRemaining <= 0 && !ready) finishSnapshot();
        return true;
    }

    private void finishSnapshot() {
        ready = true;
        if (localPlayer != null) queued.add(localPlayer);
        if (localPosition != null) queued.add(ReplayEntry.of(tick * 50, new ClientboundPlayerPositionPacket(0, localPosition, Set.of())));
        queued.add(ReplayEntry.of(tick * 50, new ClientboundGameEventPacket(ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START, 0)));
        queued.add(new ReplayEntry(tick * 50, ReplayFiles.MARK_READY, new byte[0]));
    }

    private static int loginPlayerId(byte[] payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        buf.readVarInt();
        return buf.readInt();
    }

    private ReplayEntry createLocalPlayer(byte[] payload, int time) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        UUID uuid = buf.readUUID();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float xRot = buf.readFloat();
        float yRot = buf.readFloat();
        float headRot = buf.readFloat();
        Vec3 movement = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        localPosition = new PositionMoveRotation(new Vec3(x, y, z), Vec3.ZERO, yRot, xRot);
        return ReplayEntry.of(time, new ClientboundAddEntityPacket(localPlayerId, uuid, x, y, z, xRot, yRot,
                EntityTypes.PLAYER, 0, movement, headRot));
    }

    private void moveEntities(byte[] payload, int time) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        int dimensions = buf.readVarInt();
        for (int d = 0; d < dimensions; d++) {
            buf.readUtf();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                int id = buf.readVarInt();
                Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
                float yRot = buf.readFloat();
                float xRot = buf.readFloat();
                float headRot = buf.readFloat();
                boolean onGround = buf.readBoolean();
                queued.add(ReplayEntry.of(time, new ClientboundTeleportEntityPacket(id, new PositionMoveRotation(pos, Vec3.ZERO, yRot, xRot), Set.of(), onGround)));
                queued.add(ReplayEntry.of(time, rotateHead(id, headRot)));
            }
        }
    }

    private static ClientboundRotateHeadPacket rotateHead(int id, float headRot) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(8));
        buf.writeVarInt(id);
        buf.writeByte(Mth.packDegrees(headRot));
        return ClientboundRotateHeadPacket.STREAM_CODEC.decode(buf);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static byte[] encode(ProtocolInfo<?> protocol, Packet<?> packet) {
        ByteBuf buf = Unpooled.buffer(64);
        try {
            ((StreamCodec) protocol.codec()).encode(buf, packet);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }

    static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return value;
        }
        throw new IOException("Flashback のリプレイが壊れています（数値が長すぎます）");
    }

    private static int varIntSize(int value) {
        int size = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            size++;
        }
        return size;
    }

    @Override
    public void close() throws IOException {
        try {
            if (in != null) in.close();
            if (cache != null) cache.close();
        } finally {
            if (zip != null) zip.close();
        }
    }
}
