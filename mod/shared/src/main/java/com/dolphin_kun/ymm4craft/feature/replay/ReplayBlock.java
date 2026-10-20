package com.dolphin_kun.ymm4craft.feature.replay;

record ReplayBlock(long entry, long offset) {
    static final ReplayBlock START = new ReplayBlock(0, 0);
}
