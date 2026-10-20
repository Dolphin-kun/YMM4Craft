package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

final class ReplayReader implements ReplaySource {
    private final DataInputStream in;
    private boolean ended;
    private long index;

    ReplayReader(Path file) throws IOException {
        this(file, ReplayBlock.START);
    }

    private ReplayReader(Path file, ReplayBlock block) throws IOException {
        InputStream raw = Files.newInputStream(file);
        try {
            raw.skipNBytes(block.offset());
            in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new BufferedInputStream(raw, 64 * 1024), 64 * 1024), 64 * 1024));
            if (block.offset() == 0) {
                byte[] magic = new byte[ReplayFiles.MAGIC.length];
                in.readFully(magic);
                if (!Arrays.equals(magic, ReplayFiles.MAGIC)) throw new IOException("リプレイのファイルではありません");
            }
            index = block.entry();
        } catch (IOException e) {
            raw.close();
            throw e;
        }
    }

    static ReplayReader openAt(Path file, ReplayBlockIndex blocks, long entry) throws IOException {
        ReplayReader reader = new ReplayReader(file, blocks.floor(entry));
        try {
            reader.skipTo(entry);
            return reader;
        } catch (IOException e) {
            reader.close();
            throw e;
        }
    }

    @Override
    public long index() {
        return index;
    }

    @Override
    public ReplayEntry next() throws IOException {
        if (ended) return null;
        try {
            int time = in.readInt();
            byte kind = in.readByte();
            int length = readLength();
            byte[] data = new byte[length];
            in.readFully(data);
            index++;
            return new ReplayEntry(time, kind, data);
        } catch (EOFException e) {
            ended = true;
            return null;
        }
    }

    private void skipTo(long entry) throws IOException {
        while (index < entry) {
            in.readInt();
            in.readByte();
            in.skipNBytes(readLength());
            index++;
        }
    }

    private int readLength() throws IOException {
        int length = in.readInt();
        if (length < 0 || length > ReplayFiles.MAX_ENTRY) throw new IOException("リプレイのファイルが壊れています（記録の長さ " + length + "）");
        return length;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
