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
        if (packet instanceof ClientboundAddEntityPacket add) {
            spawned.put(add.getId(), add);
        } else if (packet instanceof ClientboundRemoveEntitiesPacket remove) {
            remove.getEntityIds().forEach((int id) -> spawned.remove(id));
        } else if (packet instanceof ClientboundLoginPacket) {
            spawned.clear();
            respawn = null;
        } else if (packet instanceof ClientboundRespawnPacket r) {
            spawned.clear();
            respawn = r;
        } else if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
            if (!info.actions().equals(LATENCY_ONLY)) playerInfo.add(info);
        } else if (packet instanceof ClientboundPlayerInfoRemovePacket info) {
            playerInfo.add(info);
        } else if (packet instanceof ClientboundSetTimePacket t) {
            time = t;
        } else if (packet instanceof ClientboundSetChunkCacheCenterPacket c) {
            center = c;
        } else if (packet instanceof ClientboundSetChunkCacheRadiusPacket r) {
            radius = r;
        } else if (packet instanceof ClientboundSetSimulationDistancePacket s) {
            simulation = s;
        } else if (packet instanceof ClientboundSetDefaultSpawnPositionPacket s) {
            spawn = s;
        } else if (packet instanceof ClientboundChangeDifficultyPacket d) {
            difficulty = d;
        } else if (packet instanceof ClientboundTabListPacket t) {
            tabList = t;
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
