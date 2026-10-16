package com.dolphin_kun.ymm4craft.feature.replay;

import java.util.Map;
import java.util.TreeMap;

final class ReplaySnapshots {
    private static final int FIRST_INTERVAL = 20 * 30;
    private static final long BUDGET_BYTES = 1024L * 1024 * 1024;

    private final TreeMap<Integer, ReplaySnapshot> snapshots = new TreeMap<>();
    private int interval = FIRST_INTERVAL;
    private long totalBytes;

    boolean wants(int ticks) {
        return (ticks == 1 || ticks > 0 && ticks % interval == 0) && !snapshots.containsKey(ticks);
    }

    void add(ReplaySnapshot snapshot) {
        snapshots.put(snapshot.ticks(), snapshot);
        totalBytes += snapshot.rawSize();
        while (totalBytes > BUDGET_BYTES && snapshots.size() > 1) {
            interval *= 2;
            snapshots.entrySet().removeIf(e -> {
                if (e.getKey() == 1 || e.getKey() % interval == 0) return false;
                totalBytes -= e.getValue().rawSize();
                return true;
            });
        }
    }

    ReplaySnapshot floor(int ticks) {
        Map.Entry<Integer, ReplaySnapshot> e = snapshots.floorEntry(ticks);
        return e == null ? null : e.getValue();
    }

    void clear() {
        snapshots.clear();
        interval = FIRST_INTERVAL;
        totalBytes = 0;
    }
}
