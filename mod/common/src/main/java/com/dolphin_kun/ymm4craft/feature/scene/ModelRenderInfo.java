package com.dolphin_kun.ymm4craft.feature.scene;

import org.joml.Matrix4f;

import java.util.List;

public record ModelRenderInfo(String key, List<PartRotation> pose, float yaw, float tilt, float roll, int glowColor, Matrix4f parentWorld,
                              float walkPos, float walkSpeed) {
}
