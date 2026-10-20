package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.CommandException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

public final class SceneSkins {
    public static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_SKINS = 128;

    private static final Map<String, ResourceLocation> textures = new HashMap<>();
    private static final Map<String, SceneSkin[]> byName = new HashMap<>();

    private SceneSkins() {
    }

    public static String register(byte[] png) throws CommandException {
        String hash = sha1(png);
        if (textures.containsKey(hash)) return hash;
        if (textures.size() >= MAX_SKINS) throw new CommandException("スキンが多すぎます（" + MAX_SKINS + "枚まで）");

        NativeImage image;
        try {
            image = NativeImage.read(png);
        } catch (IOException e) {
            throw new CommandException("スキンの画像を読めませんでした: " + e.getMessage());
        }
        if (image.getWidth() != image.getHeight() || image.getWidth() % 64 != 0 || image.getWidth() > 1024) {
            int w = image.getWidth(), h = image.getHeight();
            image.close();
            throw new CommandException("スキンは 64x64 の画像にしてください（" + w + "x" + h + "）");
        }
        ResourceLocation id = new ResourceLocation("ymm4craft", "skin/" + hash);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        textures.put(hash, id);
        return hash;
    }

    public static SceneSkin byName(String name, SceneSkin fallback) {
        String key = name.toLowerCase(Locale.ROOT);
        SceneSkin[] found = byName.get(key);
        if (found == null) {
            SceneSkin[] slot = new SceneSkin[1];
            byName.put(key, slot);
            Minecraft mc = Minecraft.getInstance();
            SkullBlockEntity.updateGameprofile(new GameProfile(null, name), profile -> mc.getSkinManager().registerSkins(profile, (type, location, texture) -> {
                SceneSkin current = slot[0] != null ? slot[0] : new SceneSkin(null, null, "default");
                if (type == MinecraftProfileTexture.Type.SKIN) {
                    String model = "slim".equals(texture.getMetadata("model")) ? "slim" : "default";
                    slot[0] = new SceneSkin(location, current.cape(), model);
                    Ymm4Craft.LOGGER.info("プレイヤー {} のスキンを取得しました", name);
                } else if (type == MinecraftProfileTexture.Type.CAPE) {
                    slot[0] = new SceneSkin(current.texture(), location, current.model());
                }
            }, true));
            return fallback;
        }
        SceneSkin skin = found[0];
        if (skin == null || skin.texture() == null) return fallback;
        return skin;
    }

    public static SceneSkin skin(String hash, SceneSkin fallback) {
        ResourceLocation id = textures.get(hash);
        if (id == null) return fallback;
        return new SceneSkin(id, null, fallback.model());
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
