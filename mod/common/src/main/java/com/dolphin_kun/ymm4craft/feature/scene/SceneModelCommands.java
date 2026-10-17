package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.core.CommandParams;
import com.dolphin_kun.ymm4craft.core.CommandRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.dolphin_kun.ymm4craft.feature.replay.ReplayPlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.Identifier;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

final class SceneModelCommands {
    private static final Set<String> PART_KINDS = Set.of("mob", "player");
    private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    private static final Pattern BASE64 = Pattern.compile("[A-Za-z0-9+/=]{1," + (SceneSkins.MAX_BYTES / 3 * 4 + 4) + "}");
    private static final Pattern HOME_PLAYER = Pattern.compile("[A-Za-z0-9_.-]{0,32}");

    private SceneModelCommands() {
    }

    static void register(CommandRegistry registry) {
        registry.register("scene.catalog", p -> CommandRegistry.onMainThread(ModelCatalog::catalog));
        registry.register("scene.modelParts", SceneModelCommands::modelParts);
        registry.register("scene.blockProperties", SceneModelCommands::blockProperties);
        registry.register("scene.uploadSkin", SceneModelCommands::uploadSkin);
        registry.register("scene.ground", SceneModelCommands::ground);
        registry.register("scene.home", p -> {
            String player = p.has("player") ? p.getString("player", HOME_PLAYER) : "";
            return CommandRegistry.onMainThread(() -> home(player));
        });
    }

    private static CompletableFuture<JsonElement> modelParts(CommandParams p) throws CommandException {
        String kind = p.getChoice("kind", PART_KINDS);
        Identifier type = kind.equals("mob") ? Identifier.tryParse(p.getString("type", REGISTRY_ID)) : null;
        boolean baby = p.has("baby") && p.getBoolean("baby");
        Minecraft mc = Minecraft.getInstance();
        return CommandRegistry.onMainThread(() -> {
            if (mc.level == null) throw new RuntimeException(new CommandException("ワールドを開いていません"));
            try {
                return ModelCatalog.parts(mc.level, kind, type, baby);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(new CommandException(e.getMessage()));
            }
        });
    }

    private static CompletableFuture<JsonElement> blockProperties(CommandParams p) throws CommandException {
        Identifier type = Identifier.tryParse(p.getString("type", REGISTRY_ID));
        return CommandRegistry.onMainThread(() -> {
            try {
                return ModelCatalog.blockProperties(type);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException(new CommandException(e.getMessage()));
            }
        });
    }

    private static CompletableFuture<JsonElement> uploadSkin(CommandParams p) throws CommandException {
        byte[] png;
        try {
            png = Base64.getDecoder().decode(p.getString("png", BASE64));
        } catch (IllegalArgumentException e) {
            throw new CommandException("png は Base64 で指定してください");
        }
        if (png.length > SceneSkins.MAX_BYTES) throw new CommandException("スキンの画像が大きすぎます");
        return CommandRegistry.onMainThread(() -> {
            try {
                JsonObject result = new JsonObject();
                result.addProperty("skin", SceneSkins.register(png));
                return result;
            } catch (CommandException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static JsonElement home(String player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) throw new RuntimeException(new CommandException("ワールドを開いていません"));
        JsonObject result = new JsonObject();
        Entity named = ReplayPlayback.active() && !player.isEmpty() ? ReplayPlayback.playerNamed(player) : null;
        Entity recorded = named != null ? named : ReplayPlayback.active() ? ReplayPlayback.recordedPlayer() : null;
        if (recorded != null) {
            Vec3 eye = recorded.getEyePosition();
            result.addProperty("kind", named != null && named != ReplayPlayback.recordedPlayer() ? "player" : "recorded");
            result.addProperty("x", eye.x);
            result.addProperty("y", eye.y);
            result.addProperty("z", eye.z);
            result.addProperty("yaw", Mth.wrapDegrees(recorded.getYHeadRot()));
            result.addProperty("pitch", recorded.getXRot());
            return result;
        }
        LevelData.RespawnData spawn = mc.level.getRespawnData();
        BlockPos pos = spawn.pos();
        result.addProperty("kind", "spawn");
        result.addProperty("x", pos.getX() + 0.5);
        result.addProperty("y", pos.getY() + 1.62);
        result.addProperty("z", pos.getZ() + 0.5);
        result.addProperty("yaw", Mth.wrapDegrees(spawn.yaw()));
        result.addProperty("pitch", spawn.pitch());
        return result;
    }

    private static CompletableFuture<JsonElement> ground(CommandParams p) throws CommandException {
        double x = p.getDouble("x", -29_999_984, 29_999_984);
        double z = p.getDouble("z", -29_999_984, 29_999_984);
        Minecraft mc = Minecraft.getInstance();
        return CommandRegistry.onMainThread(() -> {
            if (mc.level == null) throw new RuntimeException(new CommandException("ワールドを開いていません"));
            JsonObject result = new JsonObject();
            result.addProperty("y", SceneModels.groundY(mc.level, x, z));
            return result;
        });
    }
}
