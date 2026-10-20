package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.core.CommandException;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.level.block.entity.SkullBlockEntity;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

public final class SceneSkins {
    public static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_SKINS = 128;

    private static final Map<String, ResourceLocation> textures = new HashMap<>();

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
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("ymm4craft", "skin/" + hash);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        textures.put(hash, id);
        return hash;
    }

    private static final Map<String, java.util.concurrent.CompletableFuture<java.util.Optional<PlayerSkin>>> byName = new HashMap<>();

    public static PlayerSkin byName(String name, PlayerSkin fallback) {
        String key = name.toLowerCase(java.util.Locale.ROOT);
        var future = byName.computeIfAbsent(key, k -> {
            Minecraft mc = Minecraft.getInstance();
            return SkullBlockEntity.fetchGameProfile(name)
                    .thenComposeAsync(profile -> profile.isPresent()
                            ? mc.getSkinManager().getOrLoad(profile.get()).thenApply(java.util.Optional::of)
                            : java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.<PlayerSkin>empty()), mc)
                    .whenComplete((skin, e) -> {
                        if (e == null) com.dolphin_kun.ymm4craft.Ymm4Craft.LOGGER.info("プレイヤー {} のスキン: {}", name, skin.isPresent() ? "取得しました" : "見つかりませんでした");
                    })
                    .exceptionally(e -> {
                        com.dolphin_kun.ymm4craft.Ymm4Craft.LOGGER.warn("プレイヤー {} のスキンを取得できませんでした: {}", name, e.getMessage());
                        return java.util.Optional.empty();
                    });
        });
        java.util.Optional<PlayerSkin> skin = future.getNow(null);
        return skin != null && skin.isPresent() ? skin.get() : fallback;
    }

    public static PlayerSkin skin(String hash, PlayerSkin fallback) {
        ResourceLocation id = textures.get(hash);
        if (id == null) return fallback;
        return new PlayerSkin(id, null, null, null, fallback.model(), false);
    }

    private static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
