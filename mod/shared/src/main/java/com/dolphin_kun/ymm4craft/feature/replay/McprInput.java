package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.IOException;
import java.nio.file.Path;

record McprInput(Path file) implements ReplayInput {
    @Override
    public ReplaySource open() throws IOException {
        return new McprReader(file);
    }

    @Override
    public ReplaySource openAt(long entry) throws IOException {
        McprReader reader = new McprReader(file);
        try {
            reader.skipTo(entry);
            return reader;
        } catch (IOException e) {
            reader.close();
            throw e;
        }
    }

    @Override
    public boolean marksReady() {
        return false;
    }
}
