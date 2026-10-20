package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.mixin.BossHealthOverlayAccessor;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityLinkPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPassengersPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.scores.Objective;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.world.scores.Score;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class ReplaySnapshotCapture {
    private static final int FALLBACK_RADIUS = 32;

    private ReplaySnapshotCapture() {
    }

    static List<Packet<?>> capture(Minecraft mc, ReplayStreamState stream) {
        ClientLevel level = mc.level;
        LocalPlayer camera = mc.player;
        List<Packet<?>> packets = new ArrayList<>();
        if (stream.respawn() != null) packets.add(stream.respawn());
        packets.addAll(stream.worldPackets());
        packets.add(time(level));
        packets.add(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
        weather(level, packets);
        packets.addAll(stream.playerInfo());
        scoreboard(level.getScoreboard(), packets);
        for (var boss : ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).ymm4craft$events().values()) {
            packets.add(ClientboundBossEventPacket.createAddPacket(boss));
        }
        chunks(level, camera, stream, packets);
        entities(level, camera, stream, packets);
        packets.add(new ClientboundPlayerPositionPacket(camera.getX(), camera.getY(), camera.getZ(), camera.getYRot(), camera.getXRot(), Set.of(), 0));
        return packets;
    }

    private static ClientboundSetTimePacket time(ClientLevel level) {
        return new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), true);
    }

    private static void weather(ClientLevel level, List<Packet<?>> packets) {
        float rain = level.getRainLevel(1f);
        if (rain <= 0) return;
        packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
        packets.add(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, level.getThunderLevel(1f) / rain));
    }

    private static void scoreboard(Scoreboard scoreboard, List<Packet<?>> packets) {
        for (Objective objective : scoreboard.getObjectives()) {
            packets.add(new ClientboundSetObjectivePacket(objective, ClientboundSetObjectivePacket.METHOD_ADD));
        }
        for (int slot = 0; slot < Scoreboard.DISPLAY_SLOTS; slot++) {
            Objective objective = scoreboard.getDisplayObjective(slot);
            if (objective != null) packets.add(new ClientboundSetDisplayObjectivePacket(slot, objective));
        }
        for (Objective objective : scoreboard.getObjectives()) {
            for (Score score : scoreboard.getPlayerScores(objective)) {
                packets.add(new ClientboundSetScorePacket(ServerScoreboard.Method.CHANGE, objective.getName(), score.getOwner(), score.getScore()));
            }
        }
        for (PlayerTeam team : scoreboard.getPlayerTeams()) {
            packets.add(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        }
    }

    private static void chunks(ClientLevel level, LocalPlayer camera, ReplayStreamState stream, List<Packet<?>> packets) {
        int cx = stream.center() != null ? stream.center().getX() : camera.chunkPosition().x;
        int cz = stream.center() != null ? stream.center().getZ() : camera.chunkPosition().z;
        int radius = (stream.radius() != null ? stream.radius().getRadius() : FALLBACK_RADIUS) + 3;
        var source = level.getChunkSource();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                LevelChunk chunk = source.getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                try {
                    packets.add(new ClientboundLevelChunkWithLightPacket(chunk, level.getLightEngine(), null, null));
                } catch (RuntimeException e) {
                    Ymm4Craft.LOGGER.debug("チャンク {}, {} を保存できませんでした", x, z, e);
                }
            }
        }
    }

    private static void entities(ClientLevel level, LocalPlayer camera, ReplayStreamState stream, List<Packet<?>> packets) {
        List<Entity> kept = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == camera || entity.getId() < 0 || entity.isRemoved()) continue;
            kept.add(entity);
            ClientboundAddEntityPacket spawn = stream.spawnOf(entity.getId());
            if (entity instanceof net.minecraft.world.entity.player.Player player) packets.add(new net.minecraft.network.protocol.game.ClientboundAddPlayerPacket(player));
            else packets.add(new ClientboundAddEntityPacket(entity.getId(), entity.getUUID(), entity.getX(), entity.getY(), entity.getZ(),
                    entity.getXRot(), entity.getYRot(), entity.getType(), spawn != null ? spawn.getData() : 0,
                    entity.getDeltaMovement(), entity.getYHeadRot()));
            List<SynchedEntityData.DataValue<?>> data = entity.getEntityData().getNonDefaultValues();
            if (data != null && !data.isEmpty()) packets.add(new ClientboundSetEntityDataPacket(entity.getId(), data));
            if (entity instanceof LivingEntity living) living(living, packets);
        }
        for (Entity entity : kept) {
            if (!entity.getPassengers().isEmpty()) packets.add(new ClientboundSetPassengersPacket(entity));
            if (entity instanceof Mob mob && mob.getLeashHolder() != null) {
                packets.add(new ClientboundSetEntityLinkPacket(entity, mob.getLeashHolder()));
            }
        }
    }

    private static void living(LivingEntity living, List<Packet<?>> packets) {
        List<Pair<EquipmentSlot, ItemStack>> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = living.getItemBySlot(slot);
            if (!stack.isEmpty()) equipment.add(Pair.of(slot, stack.copy()));
        }
        if (!equipment.isEmpty()) packets.add(new ClientboundSetEquipmentPacket(living.getId(), equipment));
        var attributes = living.getAttributes().getSyncableAttributes();
        if (!attributes.isEmpty()) packets.add(new ClientboundUpdateAttributesPacket(living.getId(), attributes));
        for (MobEffectInstance effect : living.getActiveEffects()) {
            packets.add(new ClientboundUpdateMobEffectPacket(living.getId(), effect));
        }
    }
}
