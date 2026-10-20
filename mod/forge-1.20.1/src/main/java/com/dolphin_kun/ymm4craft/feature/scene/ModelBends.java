package com.dolphin_kun.ymm4craft.feature.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class ModelBends {
    private static final Map<ModelPart, float[]> activeBends = new IdentityHashMap<>();

    private ModelBends() {
    }

    public static void begin(ModelPart root, List<PartRotation> pose) {
        activeBends.clear();
        for (PartRotation r : pose) {
            if (!r.bent()) continue;
            ModelPart part = ModelParts.find(root, r.name());
            if (part != null) add(part, new float[]{r.bendX(), r.bendZ()});
        }
    }

    private static void add(ModelPart part, float[] bend) {
        activeBends.put(part, bend);
        for (ModelPart child : ModelParts.children(part).values()) add(child, bend);
    }

    public static void end() {
        activeBends.clear();
    }

    static float[] bendOf(ModelPart part) {
        return activeBends.get(part);
    }

    public static boolean draw(ModelPart part, PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, float red, float green, float blue, float alpha) {
        if (activeBends.isEmpty()) return false;
        float[] bend = activeBends.get(part);
        if (bend == null) return false;
        float[] color = {red, green, blue, alpha};
        for (ModelPart.Cube cube : ModelParts.cubes(part)) drawCube(cube, bend, pose, builder, light, overlay, color);
        return true;
    }

    private static void drawCube(ModelPart.Cube cube, float[] bend, PoseStack.Pose pose, VertexConsumer builder, int light, int overlay, float[] color) {
        float mid = (cube.minY + cube.maxY) / 2f;
        Vector3f pivot = new Vector3f((cube.minX + cube.maxX) / 2f, mid, (cube.minZ + cube.maxZ) / 2f);
        Quaternionf full = new Quaternionf().rotationZYX(bend[1], 0, -bend[0]);
        Quaternionf half = new Quaternionf().rotationZYX(bend[1] / 2f, 0, -bend[0] / 2f);
        for (ModelPart.Polygon polygon : ModelParts.polygons(cube)) {
            ModelPart.Vertex[] v = polygon.vertices;
            boolean flat = true;
            for (ModelPart.Vertex vertex : v) flat &= Math.abs(vertex.pos.y() - v[0].pos.y()) < 1e-4f;
            if (flat) {
                emit(v, v[0].pos.y() > mid ? full : null, polygon.normal, pivot, mid, full, half, pose, builder, light, overlay, color);
            } else {
                emit(split(v, mid, true), null, polygon.normal, pivot, mid, full, half, pose, builder, light, overlay, color);
                emit(split(v, mid, false), full, polygon.normal, pivot, mid, full, half, pose, builder, light, overlay, color);
            }
        }
    }

    private static ModelPart.Vertex[] split(ModelPart.Vertex[] v, float mid, boolean upper) {
        ModelPart.Vertex[] out = new ModelPart.Vertex[v.length];
        for (int i = 0; i < v.length; i++) {
            ModelPart.Vertex a = v[i];
            boolean below = a.pos.y() > mid;
            if (upper != below) {
                out[i] = a;
                continue;
            }
            ModelPart.Vertex b = a;
            for (ModelPart.Vertex c : v) {
                if (c != a && c.pos.x() == a.pos.x() && c.pos.z() == a.pos.z()) {
                    b = c;
                    break;
                }
            }
            float t = b.pos.y() == a.pos.y() ? 0 : (mid - a.pos.y()) / (b.pos.y() - a.pos.y());
            out[i] = new ModelPart.Vertex(a.pos.x(), mid, a.pos.z(), a.u + (b.u - a.u) * t, a.v + (b.v - a.v) * t);
        }
        return out;
    }

    private static void emit(ModelPart.Vertex[] v, Quaternionf normalRotation, Vector3fc normal, Vector3f pivot, float mid,
                             Quaternionf full, Quaternionf half, PoseStack.Pose pose,
                             VertexConsumer builder, int light, int overlay, float[] color) {
        Vector3f n = new Vector3f(normal);
        if (normalRotation != null) normalRotation.transform(n);
        n.mul(pose.normal());
        Vector3f p = new Vector3f();
        for (ModelPart.Vertex vertex : v) {
            p.set(vertex.pos.x(), vertex.pos.y(), vertex.pos.z());
            if (vertex.pos.y() > mid - 1e-4f) {
                p.sub(pivot);
                (Math.abs(vertex.pos.y() - mid) < 1e-4f ? half : full).transform(p);
                p.add(pivot);
            }
            Vector3f pos = pose.pose().transformPosition(p.x / 16f, p.y / 16f, p.z / 16f, new Vector3f());
            builder.vertex(pos.x(), pos.y(), pos.z(), color[0], color[1], color[2], color[3], vertex.u, vertex.v, overlay, light, n.x(), n.y(), n.z());
        }
    }

    public static void bendHand(ModelPart arm, String armName, ModelRenderInfo info, PoseStack poseStack) {
        if (info == null) return;
        for (PartRotation r : info.pose()) {
            if (!r.name().equals(armName) || !r.bent()) continue;
            List<ModelPart.Cube> cubes = ModelParts.cubes(arm);
            if (cubes.isEmpty()) return;
            ModelPart.Cube cube = cubes.get(0);
            float px = (cube.minX + cube.maxX) / 32f, py = (cube.minY + cube.maxY) / 32f, pz = (cube.minZ + cube.maxZ) / 32f;
            poseStack.translate(px, py, pz);
            poseStack.mulPose(new Quaternionf().rotationZYX(r.bendZ(), 0, -r.bendX()));
            poseStack.translate(-px, -py, -pz);
            return;
        }
    }
}
