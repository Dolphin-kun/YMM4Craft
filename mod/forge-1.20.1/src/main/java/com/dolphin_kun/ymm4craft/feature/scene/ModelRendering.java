package com.dolphin_kun.ymm4craft.feature.scene;

import java.util.ArrayDeque;
import java.util.List;

public final class ModelRendering {
    private static final ModelRenderInfo NONE = new ModelRenderInfo(null, List.of(), 0, 0, 0, -1, null, Float.NaN, 0);
    private static final ArrayDeque<ModelRenderInfo> stack = new ArrayDeque<>();

    private ModelRendering() {
    }

    public static void push(ModelRenderInfo info) {
        stack.push(info == null ? NONE : info);
    }

    public static void pop() {
        if (!stack.isEmpty()) stack.pop();
    }

    public static ModelRenderInfo current() {
        ModelRenderInfo info = stack.peek();
        return info == NONE ? null : info;
    }
}
