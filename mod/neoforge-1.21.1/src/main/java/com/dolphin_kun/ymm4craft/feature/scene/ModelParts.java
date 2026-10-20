package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.mixin.ModelPartAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

import java.util.List;
import java.util.Map;

public final class ModelParts {
    private ModelParts() {
    }

    static Map<String, ModelPart> children(ModelPart part) {
        return ((ModelPartAccessor) (Object) part).ymm4craft$children();
    }

    static List<ModelPart.Cube> cubes(ModelPart part) {
        return ((ModelPartAccessor) (Object) part).ymm4craft$cubes();
    }

    static ModelPart find(ModelPart part, String name) {
        Map<String, ModelPart> children = children(part);
        ModelPart direct = children.get(name);
        if (direct != null) return direct;
        for (ModelPart child : children.values()) {
            ModelPart found = find(child, name);
            if (found != null) return found;
        }
        return null;
    }

    public static void applyPose(ModelPart root, List<PartRotation> pose) {
        for (PartRotation r : pose) {
            ModelPart part = find(root, r.name());
            if (part == null) continue;
            PartPose initial = part.getInitialPose();
            part.xRot = initial.xRot + r.x();
            part.yRot = initial.yRot + r.y();
            part.zRot = initial.zRot + r.z();
        }
    }
}
