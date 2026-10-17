package com.dolphin_kun.ymm4craft.feature.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ModelAim {
    private ModelAim() {
    }

    public static void apply(ModelPart root, List<PartRotation> pose, Matrix4fc modelPose) {
        Vec3 camera = null;
        for (PartRotation r : pose) {
            if (!r.aims()) continue;
            List<ModelPart> path = new ArrayList<>();
            if (!findPath(root, r.name(), path)) continue;
            if (camera == null) camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
            ModelPart part = path.removeLast();
            PoseStack stack = new PoseStack();
            stack.last().pose().set(modelPose);
            root.translateAndRotate(stack);
            for (ModelPart ancestor : path) ancestor.translateAndRotate(stack);
            Vector3f target = new Vector3f((float) (r.aim()[0] - camera.x), (float) (r.aim()[1] - camera.y), (float) (r.aim()[2] - camera.z));
            Vector3f d = new Matrix4f(stack.last().pose()).invert().transformPosition(target)
                    .sub(part.x / 16f, part.y / 16f, part.z / 16f);
            if (d.lengthSquared() < 1e-8f) continue;
            d.normalize();
            if (r.name().contains("head")) {
                part.xRot = (float) Math.asin(Math.clamp(d.y, -1f, 1f));
                part.yRot = (float) Math.atan2(-d.x, -d.z);
                part.zRot = 0;
            } else {
                part.xRot = (float) Math.asin(Math.clamp(d.z, -1f, 1f));
                part.yRot = 0;
                part.zRot = (float) Math.atan2(-d.x, d.y);
            }
        }
    }

    private static boolean findPath(ModelPart from, String name, List<ModelPart> path) {
        for (Map.Entry<String, ModelPart> child : ModelParts.children(from).entrySet()) {
            path.add(child.getValue());
            if (child.getKey().equals(name) || findPath(child.getValue(), name, path)) return true;
            path.removeLast();
        }
        return false;
    }
}
