package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

final class ReplayWriter implements AutoCloseable {
    private static final long BLOCK_BYTES = 4L * 1024 * 1024;

    private final CountingStream raw;
    private GZIPOutputStream gzip;
    private DataOutputStream out;
    private long entries;
    private long blockBytes;
    private final List<ReplayBlock> blocks = new ArrayList<>();

    ReplayWriter(Path file) throws IOException {
        raw = new CountingStream(new BufferedOutputStream(Files.newOutputStream(file), 64 * 1024));
        openBlock();
        out.write(ReplayFiles.MAGIC);
    }

    void write(int timeMs, byte kind, byte[] data, int offset, int length) throws IOException {
        if (blockBytes >= BLOCK_BYTES) {
            out.flush();
            gzip.finish();
            blocks.add(new ReplayBlock(entries, raw.count));
            openBlock();
        }
        out.writeInt(timeMs);
        out.writeByte(kind);
        out.writeInt(length);
        out.write(data, offset, length);
        entries++;
        blockBytes += 9L + length;
    }

    List<ReplayBlock> blocks() {
        return List.copyOf(blocks);
    }

    void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
        gzip.finish();
        raw.closeTarget();
    }

    private void openBlock() throws IOException {
        gzip = new GZIPOutputStream(raw, 64 * 1024, true);
        out = new DataOutputStream(new BufferedOutputStream(gzip, 64 * 1024));
        blockBytes = 0;
    }

    private static final class CountingStream extends FilterOutputStream {
        long count;

        CountingStream(OutputStream target) {
            super(target);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        void closeTarget() throws IOException {
            out.close();
        }
    }
}
