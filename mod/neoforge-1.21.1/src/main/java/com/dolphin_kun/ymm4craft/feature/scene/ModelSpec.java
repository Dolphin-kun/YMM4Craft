package com.dolphin_kun.ymm4craft.feature.scene;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record ModelSpec(String key, String kind, ResourceLocation type, String nbt, CompoundTag tag, String blockState, boolean baby, double scale,
                        ResourceLocation mainHand, ResourceLocation offHand,
                        ResourceLocation head, ResourceLocation chest, ResourceLocation legs, ResourceLocation feet,
                        String skin, String skinName, String skinModel, List<PartRotation> pose,
                        double x, double y, double z, float yaw, float headYaw, float headPitch, float tilt, float roll,
                        ModelEffects effects, String parentKey, String parentPart, float walkPos, float walkSpeed) {
    ModelSpec withPose(List<PartRotation> newPose) {
        return new ModelSpec(key, kind, type, nbt, tag, blockState, baby, scale, mainHand, offHand, head, chest, legs, feet,
                skin, skinName, skinModel, List.copyOf(newPose), x, y, z, yaw, headYaw, headPitch, tilt, roll, effects,
                parentKey, parentPart, walkPos, walkSpeed);
    }

    boolean sameBody(ModelSpec other) {
        return kind.equals(other.kind) && java.util.Objects.equals(type, other.type)
                && nbt.equals(other.nbt) && baby == other.baby;
    }
}
