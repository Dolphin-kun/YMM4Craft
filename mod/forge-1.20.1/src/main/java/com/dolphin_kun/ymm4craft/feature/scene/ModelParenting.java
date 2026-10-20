package com.dolphin_kun.ymm4craft.feature.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class ModelParenting {
    private static final Map<String, String> effectiveParent = new HashMap<>();
    private static final Set<String> parentKeys = new HashSet<>();
    private static final Map<String, Map<String, PartFrame>> partMatrices = new HashMap<>();

    private record PartFrame(Matrix4f base, Vector3f pivot, float[] bend) {
        Matrix4f at(Vector3f localPoint) {
            Matrix4f m = new Matrix4f(base);
            if (bend == null) return m;
            float d = localPoint.y - pivot.y;
            if (d < -0.01f) return m;
            float k = d <= 0.01f ? 0.5f : 1f;
            return m.translate(pivot).rotate(new Quaternionf().rotationZYX(bend[1] * k, 0, -bend[0] * k)).translate(-pivot.x, -pivot.y, -pivot.z);
        }
    }

    private ModelParenting() {
    }

    static void update(Collection<ModelSpec> specs, Function<String, ModelSpec> byKey, List<String> problems) {
        effectiveParent.clear();
        int count = specs.size();
        for (ModelSpec spec : specs) {
            String parent = spec.parentKey();
            if (parent == null || parent.equals(spec.key()) || byKey.apply(parent) == null) continue;
            String cursor = parent;
            boolean loop = false;
            for (int depth = 0; cursor != null && depth <= count; depth++) {
                if (cursor.equals(spec.key())) {
                    loop = true;
                    break;
                }
                ModelSpec up = byKey.apply(cursor);
                cursor = up == null ? null : up.parentKey();
            }
            if (loop) {
                problems.add(spec.key() + ": 親子付けが輪になっているので、親子付けを外しました");
                continue;
            }
            effectiveParent.put(spec.key(), parent);
        }
        parentKeys.clear();
        parentKeys.addAll(effectiveParent.values());
        partMatrices.keySet().retainAll(parentKeys);
    }

    static void clear() {
        effectiveParent.clear();
        parentKeys.clear();
        partMatrices.clear();
    }

    static boolean isParent(String key) {
        return parentKeys.contains(key);
    }

    static Matrix4f parentMatrix(ModelSpec s) {
        String parent = effectiveParent.get(s.key());
        if (parent == null) return null;
        Map<String, PartFrame> parts = partMatrices.get(parent);
        if (parts == null) return null;
        PartFrame part = parts.get(s.parentPart());
        if (part == null) part = parts.get("");
        if (part == null) return null;
        Vector3f local = new Vector3f((float) s.x(), (float) -s.y(), (float) -s.z());
        return part.at(local).scale(-1, -1, 1).rotateY((float) Math.PI).translate((float) s.x(), (float) s.y(), (float) s.z());
    }

    static void recordPartMatrices(String key, ModelPart root, Matrix4fc pose) {
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Matrix4f toWorld = new Matrix4f().translation((float) camera.x, (float) camera.y, (float) camera.z);
        Map<String, PartFrame> out = partMatrices.computeIfAbsent(key, k -> new HashMap<>());
        PoseStack stack = new PoseStack();
        stack.last().pose().set(pose);
        root.translateAndRotate(stack);
        out.put("", new PartFrame(new Matrix4f(toWorld).mul(stack.last().pose()), new Vector3f(), null));
        recordChildren(root, stack, toWorld, out);
    }

    private static void recordChildren(ModelPart part, PoseStack stack, Matrix4f toWorld, Map<String, PartFrame> out) {
        for (Map.Entry<String, ModelPart> entry : ModelParts.children(part).entrySet()) {
            stack.pushPose();
            entry.getValue().translateAndRotate(stack);
            Matrix4f m = new Matrix4f(toWorld).mul(stack.last().pose());
            float[] bend = ModelBends.bendOf(entry.getValue());
            List<ModelPart.Cube> cubes = ModelParts.cubes(entry.getValue());
            Vector3f pivot = new Vector3f();
            if (bend != null && !cubes.isEmpty()) {
                ModelPart.Cube cube = cubes.get(0);
                pivot.set((cube.minX + cube.maxX) / 32f, (cube.minY + cube.maxY) / 32f, (cube.minZ + cube.maxZ) / 32f);
            }
            out.put(entry.getKey(), new PartFrame(m, pivot, bend == null ? null : bend.clone()));
            recordChildren(entry.getValue(), stack, toWorld, out);
            stack.popPose();
        }
    }
}
