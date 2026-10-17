package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.mixin.BlockDisplayAccessor;
import com.dolphin_kun.ymm4craft.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class ModelBlocks {
    private ModelBlocks() {
    }

    static boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    static Display.BlockDisplay create(ClientLevel level, Identifier id) {
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("ブロックが見つかりません: " + id);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (isAir(block)) throw new IllegalArgumentException("このブロックは置けません: " + id);
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) display).ymm4craft$setBlockState(block.defaultBlockState());
        return display;
    }

    static void place(Display.BlockDisplay display, ModelSpec s, double x, double y, double z) {
        display.snapTo(x, y, z, 0, 0);
        Quaternionf rotation = new Quaternionf()
                .rotateY((float) Math.toRadians(-s.yaw()))
                .rotateX((float) Math.toRadians(s.tilt()))
                .rotateZ((float) Math.toRadians(s.roll()));
        float k = (float) s.scale();
        Vector3f offset = rotation.transform(new Vector3f(0.5f * k, 0, 0.5f * k)).negate();
        ((DisplayAccessor) display).ymm4craft$setTransformation(new Transformation(offset, rotation, new Vector3f(k, k, k), new Quaternionf()));
    }
}
