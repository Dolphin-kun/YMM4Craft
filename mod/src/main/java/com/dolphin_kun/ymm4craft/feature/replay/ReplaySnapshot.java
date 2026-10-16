package com.dolphin_kun.ymm4craft.feature.replay;

import java.util.concurrent.CompletableFuture;

record ReplaySnapshot(int ticks, long entryIndex, ReplayEntry next, boolean ended, int rawSize,
                      CompletableFuture<byte[]> packets, ReplayPacketRewriter.State rewriter) {
}
