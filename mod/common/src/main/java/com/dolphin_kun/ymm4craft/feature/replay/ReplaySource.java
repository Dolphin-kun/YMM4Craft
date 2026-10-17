package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.IOException;

interface ReplaySource extends AutoCloseable {
    ReplayEntry next() throws IOException;

    long index();

    @Override
    void close() throws IOException;
}
