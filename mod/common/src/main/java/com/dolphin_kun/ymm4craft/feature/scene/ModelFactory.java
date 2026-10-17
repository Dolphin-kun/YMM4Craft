package com.dolphin_kun.ymm4craft.feature.scene;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.mixin.AvatarAccessor;
import com.dolphin_kun.ymm4craft.mixin.CreeperAccessor;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class ModelFactory {
    private ModelFactory() {
    }

    static Entity create(ClientLevel level, ModelSpec spec) {
        Entity entity = switch (spec.kind()) {
            case "player" -> {
                RemotePlayer player = new RemotePlayer(level, new GameProfile(playerUuid(spec.key()), "Model"));
                player.getEntityData().set(AvatarAccessor.ymm4craft$modeCustomisation(), (byte) 0x7F);
                yield player;
            }
            case "block" -> ModelBlocks.create(level, spec.type());
            case "item" -> {
                ItemStack stack = stack(spec.type());
                if (stack.isEmpty()) throw new IllegalArgumentException("アイテムが見つかりません: " + spec.type());
                yield new ItemEntity(level, spec.x(), spec.y(), spec.z(), stack);
            }
            default -> {
                EntityType<?> type = mobType(spec.type());
                Entity made = type.create(level, EntitySpawnReason.COMMAND);
                if (made == null) throw new IllegalArgumentException("この種類は置けません: " + spec.type());
                yield made;
            }
        };
        if (spec.tag() != null) {
            try {
                entity.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), spec.tag()));
            } catch (RuntimeException e) {
                Ymm4Craft.LOGGER.warn("モデルのNBTを反映できませんでした（{}）: {}", spec.key(), e.getMessage());
            }
        }
        if (spec.baby() && entity instanceof Mob mob) mob.setBaby(true);
        return entity;
    }

    static UUID playerUuid(String key) {
        return UUID.nameUUIDFromBytes(("ymm4craft:" + key).getBytes(StandardCharsets.UTF_8));
    }

    static EntityType<?> mobType(Identifier id) {
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            throw new IllegalArgumentException("Mobの種類が見つかりません: " + id);
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
        if (type == EntityTypes.PLAYER || !type.canSummon()) {
            throw new IllegalArgumentException("この種類は置けません: " + id);
        }
        return type;
    }

    private static ItemStack stack(Identifier id) {
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    static void equip(Entity entity, ModelSpec s) {
        if (!(entity instanceof LivingEntity living)) return;
        setSlot(living, EquipmentSlot.MAINHAND, s.mainHand());
        setSlot(living, EquipmentSlot.OFFHAND, s.offHand());
        setSlot(living, EquipmentSlot.HEAD, s.head());
        setSlot(living, EquipmentSlot.CHEST, s.chest());
        setSlot(living, EquipmentSlot.LEGS, s.legs());
        setSlot(living, EquipmentSlot.FEET, s.feet());
        AttributeInstance scale = living.getAttribute(Attributes.SCALE);
        if (scale != null && scale.getBaseValue() != s.scale()) scale.setBaseValue(s.scale());
    }

    private static void setSlot(LivingEntity living, EquipmentSlot slot, Identifier id) {
        ItemStack stack = stack(id);
        if (!ItemStack.matches(living.getItemBySlot(slot), stack)) living.setItemSlot(slot, stack);
    }

    static void applyEffects(Entity e, ModelEffects fx) {
        e.setSharedFlagOnFire(fx.fire());
        if (e.isInvisible() != fx.invisible()) e.setInvisible(fx.invisible());
        if (e instanceof LivingEntity living) {
            living.hurtTime = fx.hurt() ? 10 : 0;
            living.deathTime = fx.dying() ? 20 : 0;
        }
        if (e instanceof Creeper creeper) {
            CreeperAccessor a = (CreeperAccessor) creeper;
            int swell = Math.round(fx.swell() * (a.ymm4craft$maxSwell() - 2));
            a.ymm4craft$setSwell(swell);
            a.ymm4craft$setOldSwell(swell);
        }
    }
}
