package com.dolphin_kun.ymm4craft.feature.scene;

import org.joml.Vector3fc;

public interface ScreenProjector {
    float[] project(Vector3fc point);
}
