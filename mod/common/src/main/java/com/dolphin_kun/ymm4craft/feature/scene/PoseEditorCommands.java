package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.core.CommandParams;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

final class PoseEditorCommands {
    static final float EDITOR_FOV = 50f;
    private static final Pattern MODEL_KEY = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern PART_NAME = Pattern.compile("[a-z0-9_]{1,64}");

    private static volatile double[] camera;

    private PoseEditorCommands() {
    }

    static void register(CommandRegistry registry) {
        registry.register("scene.editor", PoseEditorCommands::editor);
        registry.register("scene.pick", PoseEditorCommands::pick);
        registry.register("scene.modelPose", PoseEditorCommands::modelPose);
        registry.register("scene.gizmo", PoseEditorCommands::gizmo);
    }

    static double[] camera() {
        return camera;
    }

    static void reset() {
        camera = null;
        ModelInspector.inspect(null);
    }

    private static CompletableFuture<JsonElement> editor(CommandParams p) throws CommandException {
        if (!p.has("model")) {
            return CommandRegistry.onMainThread(() -> {
                reset();
                return null;
            });
        }
        String key = p.getString("model", MODEL_KEY);
        double yaw = p.getDouble("yaw", -720, 720);
        double pitch = p.getDouble("pitch", -90, 90);
        double distance = p.getDouble("distance", 0.5, 64);
        return CommandRegistry.onMainThread(() -> {
            Entity entity = SceneModels.entityOf(key);
            if (entity == null) throw new RuntimeException(new CommandException("モデルが見つかりません（表示していないモデルは選べません）"));
            Vec3 center = entity.getBoundingBox().getCenter();
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            double fx = -Math.sin(yawRad) * Math.cos(pitchRad);
            double fy = -Math.sin(pitchRad);
            double fz = Math.cos(yawRad) * Math.cos(pitchRad);
            camera = new double[]{center.x - fx * distance, center.y - fy * distance, center.z - fz * distance, yaw, pitch};
            ModelInspector.inspect(key);
            JsonObject result = new JsonObject();
            JsonArray c = new JsonArray();
            c.add(center.x);
            c.add(center.y);
            c.add(center.z);
            result.add("center", c);
            result.addProperty("height", entity.getBbHeight());
            return result;
        });
    }

    private static CompletableFuture<JsonElement> pick(CommandParams p) throws CommandException {
        double x = p.getDouble("x", 0, 1);
        double y = p.getDouble("y", 0, 1);
        Minecraft mc = Minecraft.getInstance();
        return CommandRegistry.onMainThread(() -> {
            Camera cam = mc.gameRenderer.mainCamera();
            double tan = tanHalfFov(mc);
            double aspect = aspect(mc.getWindow());
            double sx = (2 * x - 1) * tan * aspect;
            double sy = (1 - 2 * y) * tan;
            Vector3fc f = cam.forwardVector();
            Vector3fc u = cam.upVector();
            Vector3fc l = cam.leftVector();
            Vector3f dir = new Vector3f(
                    (float) (f.x() + u.x() * sy - l.x() * sx),
                    (float) (f.y() + u.y() * sy - l.y() * sx),
                    (float) (f.z() + u.z() * sy - l.z() * sx)).normalize();
            JsonObject result = new JsonObject();
            result.addProperty("part", ModelInspector.pick(dir));
            return result;
        });
    }

    private static CompletableFuture<JsonElement> gizmo(CommandParams p) throws CommandException {
        String part = p.getString("part", PART_NAME);
        Minecraft mc = Minecraft.getInstance();
        return CommandRegistry.onMainThread(() -> {
            JsonObject result = ModelInspector.gizmo(part, projector(mc));
            return result != null ? result : new JsonObject();
        });
    }

    private static CompletableFuture<JsonElement> modelPose(CommandParams p) throws CommandException {
        String key = p.getString("model", MODEL_KEY);
        List<PartRotation> pose = ModelSpecParser.parsePose(p.getObjectArray("pose", ModelSpecParser.MAX_POSE_PARTS));
        return CommandRegistry.onMainThread(() -> {
            if (!SceneModels.overridePose(key, pose)) throw new RuntimeException(new CommandException("モデルが見つかりません"));
            return null;
        });
    }

    private static double tanHalfFov(Minecraft mc) {
        Float fov = SceneFeature.fovOverride();
        return Math.tan(Math.toRadians((fov != null ? fov : mc.options.fov().get()) / 2.0));
    }

    private static double aspect(Window window) {
        return (double) window.getWidth() / Math.max(1, window.getHeight());
    }

    private static ScreenProjector projector(Minecraft mc) {
        Camera cam = mc.gameRenderer.mainCamera();
        double tan = tanHalfFov(mc);
        double aspect = aspect(mc.getWindow());
        Vector3f f = new Vector3f(cam.forwardVector());
        Vector3f u = new Vector3f(cam.upVector());
        Vector3f l = new Vector3f(cam.leftVector());
        return point -> {
            float depth = point.dot(f);
            if (depth <= 0.01f) return null;
            double sx = -point.dot(l) / (depth * tan * aspect);
            double sy = point.dot(u) / (depth * tan);
            return new float[]{(float) ((sx + 1) / 2), (float) ((1 - sy) / 2)};
        };
    }
}
