package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.IOException;
import java.nio.file.Path;

record YmrInput(Path file, ReplayBlockIndex blocks) implements ReplayInput {
    @Override
    public ReplaySource open() throws IOException {
        return new ReplayReader(file);
    }

    @Override
    public ReplaySource openAt(long entry) throws IOException {
        return ReplayReader.openAt(file, blocks, entry);
    }

    @Override
    public boolean marksReady() {
        return true;
    }
}
