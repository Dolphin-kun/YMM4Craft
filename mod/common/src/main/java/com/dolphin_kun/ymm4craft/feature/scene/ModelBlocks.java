package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.mixin.BlockDisplayAccessor;
import com.dolphin_kun.ymm4craft.mixin.DisplayAccessor;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.IdentityHashMap;
import java.util.Map;

final class ModelBlocks {
    private record Applied(String state, float yaw, float tilt, float roll, double scale) {
        boolean sameShape(ModelSpec s) {
            return yaw == s.yaw() && tilt == s.tilt() && roll == s.roll() && scale == s.scale();
        }
    }

    private static final Map<Entity, Applied> applied = new IdentityHashMap<>();

    private ModelBlocks() {
    }

    static boolean isAir(Block block) {
        return block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR;
    }

    static Block block(Identifier id) {
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
            throw new IllegalArgumentException("ブロックが見つかりません: " + id);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (isAir(block)) throw new IllegalArgumentException("このブロックは置けません: " + id);
        return block;
    }

    static Display.BlockDisplay create(ClientLevel level, Identifier id) {
        Block block = block(id);
        Display.BlockDisplay display = new Display.BlockDisplay(EntityTypes.BLOCK_DISPLAY, level);
        ((BlockDisplayAccessor) display).ymm4craft$setBlockState(block.defaultBlockState());
        return display;
    }

    static void place(Display.BlockDisplay display, ModelSpec s, double x, double y, double z) {
        display.snapTo(x, y, z, 0, 0);
        Applied last = applied.get(display);
        boolean stateChanged = last == null || !last.state().equals(s.blockState());
        boolean shapeChanged = last == null || !last.sameShape(s);
        if (!stateChanged && !shapeChanged) return;
        applied.put(display, new Applied(s.blockState(), s.yaw(), s.tilt(), s.roll(), s.scale()));
        if (stateChanged) {
            ((BlockDisplayAccessor) display).ymm4craft$setBlockState(stateOf(block(s.type()), s.blockState()));
        }
        if (shapeChanged) {
            Quaternionf rotation = new Quaternionf()
                    .rotateY((float) Math.toRadians(-s.yaw()))
                    .rotateX((float) Math.toRadians(s.tilt()))
                    .rotateZ((float) Math.toRadians(s.roll()));
            float k = (float) s.scale();
            Vector3f offset = rotation.transform(new Vector3f(0.5f * k, 0, 0.5f * k)).negate();
            ((DisplayAccessor) display).ymm4craft$setTransformation(new Transformation(offset, rotation, new Vector3f(k, k, k), new Quaternionf()));
        }
        display.tick();
    }

    static void forget(Entity entity) {
        applied.remove(entity);
    }

    static void clear() {
        applied.clear();
    }

    private static BlockState stateOf(Block block, String text) {
        BlockState state = block.defaultBlockState();
        if (text.isEmpty()) return state;
        for (String pair : text.split(",")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            Property<?> property = block.getStateDefinition().getProperty(pair.substring(0, eq));
            if (property != null) state = with(state, property, pair.substring(eq + 1));
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
