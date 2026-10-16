package com.dolphin_kun.ymm4craft.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FrameFile implements AutoCloseable {
    public static final int MAGIC = 0x46433459;
    public static final int LAYOUT_VERSION = 2;
    public static final int HEADER_SIZE = 64;
    public static final int SLOT_HEADER_SIZE = 64;
    public static final int FORMAT_RGBA8_BOTTOM_UP = 2;
    public static final int FORMAT_BGRA8_BOTTOM_UP = 3;
    private static final int MAX_SLOTS = 3;
    private static final int SLOT_ALIGN = 4096;

    private final FileChannel channel;
    private final MappedByteBuffer buffer;
    private long sequence;

    private FrameFile(FileChannel channel, MappedByteBuffer buffer) {
        this.channel = channel;
        this.buffer = buffer;
        this.buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.buffer.putInt(0, MAGIC);
        this.buffer.putInt(4, LAYOUT_VERSION);
    }

    public static FrameFile open(Path path, long capacity) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("フレーム用ファイルがありません");
        FileChannel ch = FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE);
        try {
            if (ch.size() < capacity) throw new IOException("フレーム用ファイルの容量が足りません");
            return new FrameFile(ch, ch.map(FileChannel.MapMode.READ_WRITE, 0, capacity));
        } catch (IOException | RuntimeException e) {
            ch.close();
            throw e;
        }
    }

    public boolean fits(int width, int height) {
        return slotSize(width, height) <= buffer.capacity() - HEADER_SIZE;
    }

    private static long slotSize(int width, int height) {
        long size = SLOT_HEADER_SIZE + (long) width * height * 4;
        return (size + SLOT_ALIGN - 1) / SLOT_ALIGN * SLOT_ALIGN;
    }

    public record Written(long sequence, long offset, int width, int height, long revision, boolean settled) {
    }

    public synchronized Written write(int width, int height, int format, long revision, boolean settled, ByteBuffer pixels) {
        long slotSize = slotSize(width, height);
        int slots = (int) Math.min(MAX_SLOTS, (buffer.capacity() - HEADER_SIZE) / slotSize);
        if (slots <= 0) throw new IllegalArgumentException("フレームが大きすぎます: " + width + "x" + height);

        long seq = ++sequence;
        int offset = (int) (HEADER_SIZE + (seq % slots) * slotSize);
        int bytes = width * height * 4;

        buffer.putLong(offset, 0L);
        buffer.put(offset + SLOT_HEADER_SIZE, pixels, 0, bytes);
        buffer.putInt(offset + 8, width);
        buffer.putInt(offset + 12, height);
        buffer.putInt(offset + 16, width * 4);
        buffer.putInt(offset + 20, format);
        buffer.putLong(offset + 24, revision);
        buffer.putInt(offset + 32, settled ? 1 : 0);
        buffer.putLong(offset, seq);
        return new Written(seq, offset, width, height, revision, settled);
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
