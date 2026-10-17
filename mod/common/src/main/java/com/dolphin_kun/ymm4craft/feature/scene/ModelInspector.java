package com.dolphin_kun.ymm4craft.feature.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ModelInspector {
    private static final Set<String> OVERLAY_PARTS = Set.of("hat", "jacket", "right_sleeve", "left_sleeve", "right_pants", "left_pants");
    private static String inspectKey;
    private static final Map<Object, List<PartBox>> inspectedParts = new IdentityHashMap<>();

    private record PartBox(String name, Matrix4f toCamera, List<float[]> cubes, Matrix4f parent, float[] transform) {
        Matrix4f withRotation(float xRot, float yRot, float zRot) {
            float[] t = transform;
            Matrix4f m = new Matrix4f(parent).translate(t[0] / 16f, t[1] / 16f, t[2] / 16f);
            m.rotate(new Quaternionf().rotationZYX(zRot, yRot, xRot));
            if (t[6] != 1f || t[7] != 1f || t[8] != 1f) m.scale(t[6], t[7], t[8]);
            return m;
        }
    }

    private ModelInspector() {
    }

    public static void inspect(String key) {
        if (!Objects.equals(inspectKey, key)) inspectedParts.clear();
        inspectKey = key;
    }

    static void clear() {
        inspectedParts.clear();
    }

    static boolean isInspected(String key) {
        return inspectKey != null && inspectKey.equals(key);
    }

    static void record(Object model, ModelPart root, Matrix4fc pose) {
        List<PartBox> boxes = new ArrayList<>();
        PoseStack stack = new PoseStack();
        stack.last().pose().set(pose);
        root.translateAndRotate(stack);
        collectBoxes(root, stack, boxes);
        inspectedParts.put(model, boxes);
    }

    private static void collectBoxes(ModelPart part, PoseStack stack, List<PartBox> out) {
        for (Map.Entry<String, ModelPart> entry : ModelParts.children(part).entrySet()) {
            ModelPart child = entry.getValue();
            if (!child.visible) continue;
            Matrix4f parent = new Matrix4f(stack.last().pose());
            stack.pushPose();
            child.translateAndRotate(stack);
            if (!OVERLAY_PARTS.contains(entry.getKey())) {
                List<float[]> cubes = new ArrayList<>();
                for (ModelPart.Cube cube : ModelParts.cubes(child)) {
                    cubes.add(new float[]{cube.minX / 16f, cube.minY / 16f, cube.minZ / 16f, cube.maxX / 16f, cube.maxY / 16f, cube.maxZ / 16f});
                }
                float[] transform = {child.x, child.y, child.z, child.xRot, child.yRot, child.zRot, child.xScale, child.yScale, child.zScale};
                if (!cubes.isEmpty()) out.add(new PartBox(entry.getKey(), new Matrix4f(stack.last().pose()), cubes, parent, transform));
            }
            collectBoxes(child, stack, out);
            stack.popPose();
        }
    }

    public static String pick(Vector3fc dir) {
        String best = null;
        float bestDistance = Float.MAX_VALUE;
        Matrix4f inverse = new Matrix4f();
        Vector3f origin = new Vector3f();
        Vector3f direction = new Vector3f();
        for (List<PartBox> boxes : inspectedParts.values()) {
            for (PartBox box : boxes) {
                box.toCamera().invert(inverse);
                inverse.transformPosition(0, 0, 0, origin);
                inverse.transformDirection(dir, direction);
                for (float[] c : box.cubes()) {
                    float t = intersect(origin, direction, c);
                    if (t < 0) continue;
                    Vector3f hit = new Vector3f(direction).mul(t).add(origin);
                    float distance = box.toCamera().transformPosition(hit).length();
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = box.name();
                    }
                }
            }
        }
        return best;
    }

    public static JsonObject gizmo(String partName, ScreenProjector projector) {
        PartBox box = null;
        for (List<PartBox> boxes : inspectedParts.values()) {
            for (PartBox b : boxes) {
                if (b.name().equals(partName)) {
                    box = b;
                    break;
                }
            }
            if (box != null) break;
        }
        if (box == null) return null;

        float[] t = box.transform();
        Vector3f pivot = box.toCamera().transformPosition(new Vector3f());
        float[] pivotScreen = projector.project(pivot);
        if (pivotScreen == null) return null;

        Matrix4f afterZ = box.withRotation(0, 0, t[5]);
        Matrix4f afterZY = box.withRotation(0, t[4], t[5]);
        Vector3f[] axes = {
                afterZY.transformDirection(new Vector3f(1, 0, 0)).normalize(),
                afterZ.transformDirection(new Vector3f(0, 1, 0)).normalize(),
                box.parent().transformDirection(new Vector3f(0, 0, 1)).normalize(),
        };

        float size = 0;
        for (float[] c : box.cubes()) size = Math.max(size, Math.max(c[3] - c[0], Math.max(c[4] - c[1], c[5] - c[2])));
        Vector3f scaleProbe = box.toCamera().transformDirection(new Vector3f(1, 0, 0));
        float radius = Math.max(0.15f, size * scaleProbe.length() * 0.75f);

        JsonArray rings = new JsonArray();
        for (Vector3f axis : axes) {
            Vector3f u = new Vector3f(axis).cross(Math.abs(axis.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f v = new Vector3f(axis).cross(u).normalize();
            JsonArray ring = new JsonArray();
            for (int i = 0; i <= 48; i++) {
                double a = Math.PI * 2 * i / 48;
                Vector3f point = new Vector3f(pivot)
                        .add(new Vector3f(u).mul((float) (Math.cos(a) * radius)))
                        .add(new Vector3f(v).mul((float) (Math.sin(a) * radius)));
                float[] s = projector.project(point);
                if (s == null) continue;
                JsonArray xy = new JsonArray();
                xy.add(s[0]);
                xy.add(s[1]);
                ring.add(xy);
            }
            rings.add(ring);
        }

        Vector3f tip = new Vector3f();
        float far = -1;
        for (float[] c : box.cubes()) {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f p = new Vector3f((corner & 1) == 0 ? c[0] : c[3], (corner & 2) == 0 ? c[1] : c[4], (corner & 4) == 0 ? c[2] : c[5]);
                if (p.lengthSquared() > far) {
                    far = p.lengthSquared();
                    tip.set(p);
                }
            }
        }
        float[] tipScreen = projector.project(box.toCamera().transformPosition(new Vector3f(tip)));
        JsonArray drag = new JsonArray();
        float step = (float) Math.toRadians(1);
        for (int axis = 0; axis < 3; axis++) {
            float[] r = {t[3], t[4], t[5]};
            r[axis] += step;
            float[] moved = projector.project(box.withRotation(r[0], r[1], r[2]).transformPosition(new Vector3f(tip)));
            JsonArray d = new JsonArray();
            d.add(tipScreen == null || moved == null ? 0 : moved[0] - tipScreen[0]);
            d.add(tipScreen == null || moved == null ? 0 : moved[1] - tipScreen[1]);
            drag.add(d);
        }

        JsonObject result = new JsonObject();
        JsonArray pivotJson = new JsonArray();
        pivotJson.add(pivotScreen[0]);
        pivotJson.add(pivotScreen[1]);
        result.add("pivot", pivotJson);
        if (tipScreen != null) {
            JsonArray tipJson = new JsonArray();
            tipJson.add(tipScreen[0]);
            tipJson.add(tipScreen[1]);
            result.add("tip", tipJson);
        }
        result.add("rings", rings);
        result.add("drag", drag);
        return result;
    }

    private static float intersect(Vector3fc o, Vector3fc d, float[] c) {
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float origin = o.get(axis);
            float dir = d.get(axis);
            float min = c[axis];
            float max = c[axis + 3];
            if (Math.abs(dir) < 1e-8f) {
                if (origin < min || origin > max) return -1;
                continue;
            }
            float t1 = (min - origin) / dir;
            float t2 = (max - origin) / dir;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
            if (tMin > tMax) return -1;
        }
        if (tMax < 0) return -1;
        return Math.max(tMin, 0);
    }
}
