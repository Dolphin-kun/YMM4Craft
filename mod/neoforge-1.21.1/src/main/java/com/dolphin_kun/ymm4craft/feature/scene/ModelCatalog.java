package com.dolphin_kun.ymm4craft.feature.scene;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ModelCatalog {
    private ModelCatalog() {
    }

    public static JsonObject catalog() {
        JsonArray mobs = new JsonArray();
        List<EntityType<?>> types = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (type != EntityType.PLAYER && type.canSummon()) types.add(type);
        }
        types.sort(Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()));
        for (EntityType<?> type : types) {
            JsonObject o = new JsonObject();
            o.addProperty("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            o.addProperty("name", type.getDescription().getString());
            mobs.add(o);
        }

        JsonArray items = new JsonArray();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", BuiltInRegistries.ITEM.getKey(item).toString());
            o.addProperty("name", new ItemStack(item).getHoverName().getString());
            items.add(o);
        }

        JsonArray blocks = new JsonArray();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (ModelBlocks.isAir(block)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("id", BuiltInRegistries.BLOCK.getKey(block).toString());
            o.addProperty("name", block.getName().getString());
            blocks.add(o);
        }

        JsonObject result = new JsonObject();
        result.add("mobs", mobs);
        result.add("items", items);
        result.add("blocks", blocks);
        return result;
    }

    public static JsonObject parts(ClientLevel level, String kind, ResourceLocation type, boolean baby) {
        Entity entity = kind.equals("player")
                ? new RemotePlayer(level, new GameProfile(ModelFactory.playerUuid("parts"), "Model"))
                : ModelFactory.mobType(type).create(level);
        if (entity == null) throw new IllegalArgumentException("この種類は置けません: " + type);
        if (baby && entity instanceof Mob mob) mob.setBaby(true);

        JsonArray parts = new JsonArray();
        EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (renderer instanceof LivingEntityRenderer<?, ?> living) {
            ModelPart root = ModelRoots.of(living.getModel());
            if (root != null) collect(root, 0, parts);
        }
        JsonObject result = new JsonObject();
        result.add("parts", parts);
        return result;
    }

    public static JsonObject blockProperties(ResourceLocation id) {
        Block block = ModelBlocks.block(id);
        BlockState defaults = block.defaultBlockState();
        JsonArray properties = new JsonArray();
        for (Property<?> property : block.getStateDefinition().getProperties()) {
            properties.add(describe(property, defaults));
        }
        JsonObject result = new JsonObject();
        result.add("properties", properties);
        return result;
    }

    private static <T extends Comparable<T>> JsonObject describe(Property<T> property, BlockState defaults) {
        JsonArray values = new JsonArray();
        for (T value : property.getPossibleValues()) values.add(property.getName(value));
        JsonObject o = new JsonObject();
        o.addProperty("name", property.getName());
        o.add("values", values);
        o.addProperty("default", property.getName(defaults.getValue(property)));
        return o;
    }

    private static void collect(ModelPart part, int depth, JsonArray out) {
        for (Map.Entry<String, ModelPart> child : ModelParts.children(part).entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", child.getKey());
            o.addProperty("depth", depth);
            out.add(o);
            collect(child.getValue(), depth + 1, out);
        }
    }
}
