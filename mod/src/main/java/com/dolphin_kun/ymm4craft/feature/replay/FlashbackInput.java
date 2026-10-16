package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.IOException;
import java.nio.file.Path;

record FlashbackInput(Path file) implements ReplayInput {
    @Override
    public ReplaySource open() throws IOException {
        return new FlashbackReader(file);
    }

    @Override
    public ReplaySource openAt(long entry) throws IOException {
        FlashbackReader reader = new FlashbackReader(file);
        try {
            while (reader.index() < entry) {
                if (reader.next() == null) break;
            }
            return reader;
        } catch (IOException | RuntimeException e) {
            reader.close();
            throw e;
        }
    }

    @Override
    public boolean marksReady() {
        return true;
    }
}
