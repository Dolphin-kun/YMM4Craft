package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FlashbackChunkCache implements AutoCloseable {
    private static final int LOADED_FILES = 2;

    private final Path folder;
    private final List<int[]> offsets = new ArrayList<>();
    private final List<Integer> firstIndex = new ArrayList<>();
    private final Map<Integer, byte[]> loaded = new LinkedHashMap<>(4, 0.75f, true);
    private int nextFile;
    private int indexed;

    FlashbackChunkCache(Path folder) {
        this.folder = folder;
    }

    byte[] get(int index) throws IOException {
        while (index >= indexed) {
            if (!indexNextFile()) return null;
        }
        int file = 0;
        while (file + 1 < firstIndex.size() && firstIndex.get(file + 1) <= index) file++;
        byte[] data = load(file);
        int local = index - firstIndex.get(file);
        int[] starts = offsets.get(file);
        int start = starts[local];
        int length = ByteBuffer.wrap(data, start, 4).getInt();
        return Arrays.copyOfRange(data, start + 4, start + 4 + length);
    }

    private boolean indexNextFile() throws IOException {
        Path path = folder.resolve(Integer.toString(nextFile));
        if (!Files.isRegularFile(path)) return false;
        byte[] data = Files.readAllBytes(path);
        List<Integer> starts = new ArrayList<>();
        int position = 0;
        while (position + 4 <= data.length) {
            int length = ByteBuffer.wrap(data, position, 4).getInt();
            if (length < 0 || position + 4L + length > data.length) throw new IOException("Flashback のチャンクの記録が壊れています");
            starts.add(position);
            position += 4 + length;
        }
        offsets.add(starts.stream().mapToInt(Integer::intValue).toArray());
        firstIndex.add(indexed);
        indexed += starts.size();
        remember(nextFile, data);
        nextFile++;
        return true;
    }

    private byte[] load(int file) throws IOException {
        byte[] data = loaded.get(file);
        if (data != null) return data;
        data = Files.readAllBytes(folder.resolve(Integer.toString(file)));
        remember(file, data);
        return data;
    }

    private void remember(int file, byte[] data) {
        loaded.put(file, data);
        while (loaded.size() > LOADED_FILES) loaded.remove(loaded.keySet().iterator().next());
    }

    @Override
    public void close() {
        loaded.clear();
    }
}
