package com.dolphin_kun.ymm4craft.feature.replay;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

final class ReplayPacketCodec {
    private ReplayPacketCodec() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static byte[] encode(List<Packet<?>> packets, ProtocolInfo<?> info) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 20);
        DataOutputStream out = new DataOutputStream(bytes);
        ByteBuf buf = Unpooled.buffer(64 * 1024);
        try {
            for (Packet<?> packet : packets) {
                buf.clear();
                try {
                    ((StreamCodec) info.codec()).encode(buf, packet);
                } catch (RuntimeException e) {
                    continue;
                }
                out.writeInt(buf.readableBytes());
                buf.readBytes(out, buf.readableBytes());
            }
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            buf.release();
        }
        return bytes.toByteArray();
    }

    static byte[] compress(byte[] raw) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(1024, raw.length / 4));
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try (DeflaterOutputStream out = new DeflaterOutputStream(bytes, deflater, 64 * 1024)) {
            out.write(raw);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            deflater.end();
        }
        return bytes.toByteArray();
    }

    static List<byte[]> decompress(byte[] compressed) throws IOException {
        List<byte[]> packets = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(compressed)), 64 * 1024))) {
            while (true) {
                int length;
                try {
                    length = in.readInt();
                } catch (EOFException e) {
                    break;
                }
                byte[] packet = new byte[length];
                in.readFully(packet);
                packets.add(packet);
            }
        }
        return packets;
    }
}
