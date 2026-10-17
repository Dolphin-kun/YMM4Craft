package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SceneModels {
    private static final int FIRST_ID = -1_000_000;


    private record Placed(ModelSpec spec, Entity entity) {
    }

    private static final Map<String, Placed> placed = new HashMap<>();
    private static final Map<Entity, Placed> byEntity = new IdentityHashMap<>();
    private static ClientLevel placedLevel;
    private static int nextId = FIRST_ID;
    private static List<ModelSpec> lastSpecs;

    private SceneModels() {
    }

    public static boolean overridePose(String key, List<PartRotation> pose) {
        Placed p = placed.get(key);
        if (p == null) return false;
        Placed updated = new Placed(p.spec().withPose(pose), p.entity());
        placed.put(key, updated);
        byEntity.put(updated.entity(), updated);
        return true;
    }

    public static void ensureLevel(ClientLevel level) {
        if (level != null && placedLevel != level && lastSpecs != null && !lastSpecs.isEmpty()) {
            logProblems(apply(level, lastSpecs));
        }
    }

    public static List<String> apply(ClientLevel level, List<ModelSpec> specs) {
        lastSpecs = List.copyOf(specs);
        if (placedLevel != level) {
            placed.clear();
            byEntity.clear();
            ModelBlocks.clear();
            placedLevel = level;
        }

        List<String> problems = new ArrayList<>();
        Set<String> wanted = new HashSet<>();
        for (ModelSpec spec : specs) {
            wanted.add(spec.key());
            Placed current = placed.get(spec.key());
            if (current != null && current.spec().sameBody(spec) && !current.entity().isRemoved()) {
                track(new Placed(spec, current.entity()));
                continue;
            }
            if (current != null) remove(level, current);
            try {
                Entity entity = ModelFactory.create(level, spec);
                entity.setId(nextId--);
                Placed made = new Placed(spec, entity);
                track(made);
                level.addEntity(entity);
            } catch (RuntimeException e) {
                problems.add(spec.key() + ": " + e.getMessage());
            }
        }
        for (Placed p : List.copyOf(placed.values())) {
            if (!wanted.contains(p.spec().key())) remove(level, p);
        }
        ModelParenting.update(placed.values().stream().map(Placed::spec).toList(),
                key -> placed.containsKey(key) ? placed.get(key).spec() : null, problems);
        return problems;
    }

    private static void track(Placed p) {
        placed.put(p.spec().key(), p);
        byEntity.put(p.entity(), p);
        ModelFactory.equip(p.entity(), p.spec());
        place(p);
    }

    public static void clear(ClientLevel level) {
        if (level != null && level == placedLevel) {
            for (Placed p : List.copyOf(placed.values())) remove(level, p);
        }
        placed.clear();
        byEntity.clear();
        ModelBlocks.clear();
        ModelInspector.clear();
        ModelParenting.clear();
        placedLevel = null;
    }

    public static void onClientTick() {
        for (Placed p : placed.values()) {
            place(p);
            p.entity().tickCount = 0;
        }
    }

    public static boolean isModel(Entity entity) {
        return !byEntity.isEmpty() && byEntity.containsKey(entity);
    }

    public static ModelRenderInfo renderInfoOf(Entity entity) {
        if (byEntity.isEmpty()) return null;
        Placed p = byEntity.get(entity);
        if (p == null) return null;
        ModelSpec s = p.spec();
        return new ModelRenderInfo(s.key(), s.pose(), s.yaw(), s.tilt(), s.roll(),
                s.effects().glowColor(), ModelParenting.parentMatrix(s), s.walkPos(), s.walkSpeed());
    }

    public static Entity entityOf(String key) {
        Placed p = placed.get(key);
        return p == null || p.entity().isRemoved() ? null : p.entity();
    }

    public static void onModelPrepared(ModelRenderInfo info, Object model, ModelPart root, Matrix4fc pose) {
        if (ModelParenting.isParent(info.key())) ModelParenting.recordPartMatrices(info.key(), root, pose);
        if (ModelInspector.isInspected(info.key())) ModelInspector.record(model, root, pose);
    }

    public static Integer groundY(ClientLevel level, double x, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) return null;
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
    }

    public static PlayerSkin skinOf(Entity entity) {
        if (byEntity.isEmpty()) return null;
        Placed p = byEntity.get(entity);
        if (p == null || !p.spec().kind().equals("player")) return null;
        PlayerSkin base = DefaultPlayerSkin.get(entity.getUUID());
        PlayerSkin skin = p.spec().skin() != null ? SceneSkins.skin(p.spec().skin(), base)
                : p.spec().skinName() != null ? SceneSkins.byName(p.spec().skinName(), base)
                : base;
        return switch (p.spec().skinModel()) {
            case "wide" -> new PlayerSkin(skin.body(), skin.cape(), skin.elytra(), PlayerModelType.WIDE, false);
            case "slim" -> new PlayerSkin(skin.body(), skin.cape(), skin.elytra(), PlayerModelType.SLIM, false);
            default -> skin;
        };
    }

    private static void remove(ClientLevel level, Placed p) {
        placed.remove(p.spec().key());
        byEntity.remove(p.entity());
        ModelBlocks.forget(p.entity());
        level.removeEntity(p.entity().getId(), Entity.RemovalReason.DISCARDED);
    }

    private static void place(Placed p) {
        ModelSpec s = p.spec();
        Entity e = p.entity();
        Matrix4f parent = ModelParenting.parentMatrix(s);
        if (e instanceof Display.BlockDisplay block) {
            Vector3f at = parent != null ? parent.transformPosition(new Vector3f()) : new Vector3f((float) s.x(), (float) s.y(), (float) s.z());
            ModelBlocks.place(block, s, parent != null ? at.x : s.x(), parent != null ? at.y : s.y(), parent != null ? at.z : s.z());
            e.setOldPosAndRot();
            ModelFactory.applyEffects(e, s.effects());
            return;
        }
        if (parent != null) {
            Vector3f at = parent.transformPosition(new Vector3f());
            e.snapTo(at.x, at.y, at.z, s.yaw(), s.headPitch());
        } else {
            e.snapTo(s.x(), s.y(), s.z(), s.yaw(), s.headPitch());
        }
        e.setDeltaMovement(0, 0, 0);
        e.setOldPosAndRot();
        if (e instanceof LivingEntity living) {
            float head = s.yaw() + s.headYaw();
            living.yBodyRot = living.yBodyRotO = s.yaw();
            living.yHeadRot = living.yHeadRotO = head;
        }
        ModelFactory.applyEffects(e, s.effects());
    }

    static void logProblems(List<String> problems) {
        for (String p : problems) Ymm4Craft.LOGGER.warn("モデルを置けませんでした: {}", p);
    }
}
