package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class McprReader implements ReplaySource {
    static final String RECORDING = "recording.tmcpr";
    static final String META = "metaData.json";

    private final ZipFile zip;
    private final DataInputStream in;
    private boolean ended;
    private long index;

    McprReader(Path file) throws IOException {
        zip = new ZipFile(file.toFile());
        try {
            ZipEntry entry = zip.getEntry(RECORDING);
            if (entry == null) throw new IOException("ReplayMod のリプレイではありません（" + RECORDING + " がありません）");
            in = new DataInputStream(new BufferedInputStream(zip.getInputStream(entry), 64 * 1024));
        } catch (IOException e) {
            zip.close();
            throw e;
        }
    }

    @Override
    public ReplayEntry next() throws IOException {
        if (ended) return null;
        try {
            int time = in.readInt();
            int length = readLength();
            byte[] data = new byte[length];
            in.readFully(data);
            index++;
            return new ReplayEntry(time, ReplayFiles.ANY_PHASE, data);
        } catch (EOFException e) {
            ended = true;
            return null;
        }
    }

    void skipTo(long entry) throws IOException {
        while (index < entry) {
            in.readInt();
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
    public long index() {
        return index;
    }

    @Override
    public void close() throws IOException {
        try {
            in.close();
        } finally {
            zip.close();
        }
    }
}
