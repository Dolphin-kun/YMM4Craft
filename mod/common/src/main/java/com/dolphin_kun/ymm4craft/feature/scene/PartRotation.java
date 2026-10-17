package com.dolphin_kun.ymm4craft.feature.scene;

public record PartRotation(String name, float x, float y, float z, float bendX, float bendZ, double[] aim) {
    boolean bent() {
        return bendX != 0 || bendZ != 0;
    }

    boolean aims() {
        return aim != null;
    }
}
