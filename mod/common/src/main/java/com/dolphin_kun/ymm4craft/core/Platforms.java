package com.dolphin_kun.ymm4craft.core;

import java.util.ServiceLoader;

public final class Platforms {
    private static volatile Platform current;

    private Platforms() {
    }

    public static Platform get() {
        Platform p = current;
        if (p == null) {
            synchronized (Platforms.class) {
                if (current == null) {
                    current = ServiceLoader.load(Platform.class, Platforms.class.getClassLoader()).findFirst()
                            .orElseThrow(() -> new IllegalStateException("YMM4Craft の実行環境が見つかりません"));
                }
                p = current;
            }
        }
        return p;
    }
}
