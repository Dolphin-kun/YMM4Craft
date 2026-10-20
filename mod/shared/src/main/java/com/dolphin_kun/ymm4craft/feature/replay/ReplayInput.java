package com.dolphin_kun.ymm4craft.feature.replay;

import java.io.IOException;

interface ReplayInput {
    ReplaySource open() throws IOException;

    ReplaySource openAt(long entry) throws IOException;

    boolean marksReady();
}
