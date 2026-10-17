package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

final class ReplayStreamState {
    private static final EnumSet<ClientboundPlayerInfoUpdatePacket.Action> LATENCY_ONLY = EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY);

    private final Map<Integer, ClientboundAddEntityPacket> spawned = new HashMap<>();
    private final List<Packet<?>> playerInfo = new ArrayList<>();
    private ClientboundRespawnPacket respawn;
    private ClientboundSetTimePacket time;
    private ClientboundSetChunkCacheCenterPacket center;
    private ClientboundSetChunkCacheRadiusPacket radius;
    private ClientboundSetSimulationDistancePacket simulation;
    private ClientboundSetDefaultSpawnPositionPacket spawn;
    private ClientboundChangeDifficultyPacket difficulty;
    private ClientboundTabListPacket tabList;

    void reset() {
        spawned.clear();
        playerInfo.clear();
        respawn = null;
        time = null;
        center = null;
        radius = null;
        simulation = null;
        spawn = null;
        difficulty = null;
        tabList = null;
    }

    void observe(Packet<?> packet) {
        switch (packet) {
            case ClientboundAddEntityPacket add -> spawned.put(add.getId(), add);
            case ClientboundRemoveEntitiesPacket remove -> remove.entityIds().forEach((int id) -> spawned.remove(id));
            case ClientboundLoginPacket ignored -> {
                spawned.clear();
                respawn = null;
            }
            case ClientboundRespawnPacket r -> {
                spawned.clear();
                respawn = r;
            }
            case ClientboundPlayerInfoUpdatePacket info -> {
                if (!info.actions().equals(LATENCY_ONLY)) playerInfo.add(info);
            }
            case ClientboundPlayerInfoRemovePacket info -> playerInfo.add(info);
            case ClientboundSetTimePacket t -> time = t;
            case ClientboundSetChunkCacheCenterPacket c -> center = c;
            case ClientboundSetChunkCacheRadiusPacket r -> radius = r;
            case ClientboundSetSimulationDistancePacket s -> simulation = s;
            case ClientboundSetDefaultSpawnPositionPacket s -> spawn = s;
            case ClientboundChangeDifficultyPacket d -> difficulty = d;
            case ClientboundTabListPacket t -> tabList = t;
            default -> {
            }
        }
    }

    ClientboundAddEntityPacket spawnOf(int id) {
        return spawned.get(id);
    }

    ClientboundSetChunkCacheCenterPacket center() {
        return center;
    }

    ClientboundSetChunkCacheRadiusPacket radius() {
        return radius;
    }

    Packet<?> respawn() {
        return respawn;
    }

    List<Packet<?>> worldPackets() {
        return Stream.of(center, radius, simulation, spawn, difficulty, tabList).filter(Objects::nonNull).<Packet<?>>map(p -> p).toList();
    }

    List<Packet<?>> playerInfo() {
        return List.copyOf(playerInfo);
    }
}
