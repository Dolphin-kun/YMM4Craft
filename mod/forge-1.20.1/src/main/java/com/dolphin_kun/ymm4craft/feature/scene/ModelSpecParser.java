package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.dolphin_kun.ymm4craft.core.CommandParams;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ModelSpecParser {
    public static final int MAX_MODELS = 64;
    public static final int MAX_POSE_PARTS = 64;
    private static final Set<String> KINDS = Set.of("mob", "item", "player", "block");
    private static final Set<String> SKIN_MODELS = Set.of("auto", "wide", "slim");
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9-]{1,64}");
    private static final Pattern REGISTRY_ID = Pattern.compile("[a-z0-9_.-]{1,64}:[a-z0-9_./-]{1,128}");
    private static final Pattern PART_NAME = Pattern.compile("[a-z0-9_]{1,64}");
    private static final Pattern PART_NAME_OR_EMPTY = Pattern.compile("[a-z0-9_]{0,64}");
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern SKIN_ID = Pattern.compile("[0-9a-f]{40}");
    private static final Pattern BLOCK_STATE = Pattern.compile("[a-z0-9_]{1,64}=[a-z0-9_]{1,64}(,[a-z0-9_]{1,64}=[a-z0-9_]{1,64}){0,31}");
    private static final Pattern NBT = Pattern.compile("(?s).{0,8192}");

    private ModelSpecParser() {
    }

    public static List<ModelSpec> parse(List<CommandParams> list) throws CommandException {
        List<ModelSpec> specs = new ArrayList<>(list.size());
        Set<String> keys = new HashSet<>();
        for (CommandParams m : list) {
            String key = m.getString("id", KEY);
            if (!keys.add(key)) throw new CommandException("models の id が重複しています: " + key);
            String kind = m.getChoice("kind", KINDS);
            ResourceLocation type = kind.equals("player") ? null : id(m, "type");

            String nbt = m.has("nbt") ? m.getString("nbt", NBT).trim() : "";
            CompoundTag tag = null;
            if (!nbt.isEmpty() && !kind.equals("player")) {
                try {
                    tag = TagParser.parseTag(nbt);
                } catch (Exception e) {
                    Ymm4Craft.LOGGER.warn("モデルのNBTを読めませんでした（{}）: {}", key, e.getMessage());
                    nbt = "";
                }
            }

            String blockState = kind.equals("block") && m.has("state") ? m.getString("state", BLOCK_STATE) : "";

            List<PartRotation> pose = m.has("pose") ? parsePose(m.getObjectArray("pose", MAX_POSE_PARTS)) : List.of();

            specs.add(new ModelSpec(key, kind, type, nbt, tag, blockState,
                    m.has("baby") && m.getBoolean("baby"),
                    m.has("scale") ? m.getDouble("scale", 0.0625, 16) : 1,
                    optionalId(m, "mainHand"), optionalId(m, "offHand"),
                    optionalId(m, "head"), optionalId(m, "chest"), optionalId(m, "legs"), optionalId(m, "feet"),
                    m.has("skin") ? m.getString("skin", SKIN_ID) : null,
                    m.has("skinName") ? m.getString("skinName", PLAYER_NAME) : null,
                    m.has("skinModel") ? m.getChoice("skinModel", SKIN_MODELS) : "auto",
                    List.copyOf(pose),
                    m.getDouble("x", -29_999_984, 29_999_984),
                    m.getDouble("y", -2048, 4096),
                    m.getDouble("z", -29_999_984, 29_999_984),
                    m.has("yaw") ? (float) m.getDouble("yaw", -360, 360) : 0f,
                    m.has("headYaw") ? (float) m.getDouble("headYaw", -180, 180) : 0f,
                    m.has("headPitch") ? (float) m.getDouble("headPitch", -90, 90) : 0f,
                    m.has("tilt") ? (float) m.getDouble("tilt", -360, 360) : 0f,
                    m.has("roll") ? (float) m.getDouble("roll", -360, 360) : 0f,
                    new ModelEffects(
                            m.has("fire") && m.getBoolean("fire"),
                            m.has("glowColor") ? m.getInt("glowColor", 0, 0xFFFFFF) : -1,
                            m.has("invisible") && m.getBoolean("invisible"),
                            m.has("hurt") && m.getBoolean("hurt"),
                            m.has("dying") && m.getBoolean("dying"),
                            m.has("swell") ? (float) m.getDouble("swell", 0, 1) : 0f),
                    m.has("parent") ? m.getString("parent", KEY) : null,
                    m.has("parentPart") ? m.getString("parentPart", PART_NAME_OR_EMPTY) : "",
                    m.has("walkPos") ? (float) m.getDouble("walkPos", -1e7, 1e7) : Float.NaN,
                    m.has("walkSpeed") ? (float) m.getDouble("walkSpeed", 0, 1.5) : 0f));
        }
        return specs;
    }

    public static List<PartRotation> parsePose(List<CommandParams> parts) throws CommandException {
        List<PartRotation> pose = new ArrayList<>(parts.size());
        for (CommandParams part : parts) {
            pose.add(new PartRotation(part.getString("name", PART_NAME),
                    (float) Math.toRadians(part.has("x") ? part.getDouble("x", -720, 720) : 0),
                    (float) Math.toRadians(part.has("y") ? part.getDouble("y", -720, 720) : 0),
                    (float) Math.toRadians(part.has("z") ? part.getDouble("z", -720, 720) : 0),
                    (float) Math.toRadians(part.has("bend") ? part.getDouble("bend", -180, 180) : 0),
                    (float) Math.toRadians(part.has("bendZ") ? part.getDouble("bendZ", -180, 180) : 0)));
        }
        return pose;
    }

    private static ResourceLocation optionalId(CommandParams m, String name) throws CommandException {
        return m.has(name) ? id(m, name) : null;
    }

    private static ResourceLocation id(CommandParams m, String name) throws CommandException {
        ResourceLocation id = ResourceLocation.tryParse(m.getString(name, REGISTRY_ID));
        if (id == null) throw new CommandException(name + " の値が不正です");
        return id;
    }
}
