package com.dolphin_kun.ymm4craft.feature.replay;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket;
import net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket;
import net.minecraft.network.protocol.login.ClientboundGameProfilePacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ReplayPacketRewriter {
    static final int CAMERA_ENTITY_ID = Integer.MIN_VALUE + 7;
    static final UUID CAMERA_UUID = UUID.nameUUIDFromBytes("ymm4craft:replay-camera".getBytes(StandardCharsets.UTF_8));

    private static final Set<Class<?>> DROPPED = Set.of(
            ClientboundPlayerLookAtPacket.class,
            ClientboundSetHealthPacket.class, ClientboundSetExperiencePacket.class, ClientboundPlayerAbilitiesPacket.class,
            ClientboundContainerSetContentPacket.class, ClientboundContainerSetSlotPacket.class, ClientboundContainerSetDataPacket.class,
            ClientboundContainerClosePacket.class, ClientboundOpenScreenPacket.class, ClientboundOpenBookPacket.class,
            ClientboundHorseScreenOpenPacket.class, ClientboundMerchantOffersPacket.class,
            ClientboundSetCarriedItemPacket.class, ClientboundSetCameraPacket.class,
            ClientboundPlayerCombatKillPacket.class, ClientboundOpenSignEditorPacket.class,
            ClientboundTickingStatePacket.class, ClientboundTickingStepPacket.class,
            ClientboundDisconnectPacket.class, ClientboundTransferPacket.class,
            ClientboundResourcePackPushPacket.class, ClientboundResourcePackPopPacket.class);

    record State(int recordedPlayerId, GameType recordedGameMode, ReplayHudState hud, ReplayScreenState screens, ReplayChatState chat) {
    }

    private final ReplayStreamState stream = new ReplayStreamState();
    private ReplayHudState hud = new ReplayHudState();
    private ReplayScreenState screens = new ReplayScreenState();
    private ReplayChatState chat = new ReplayChatState();
    private int recordedPlayerId = Integer.MIN_VALUE;
    private GameType recordedGameMode = GameType.SURVIVAL;
    private boolean ready;
    private boolean sawPosition;

    void reset() {
        hud.reset();
        screens.reset();
        chat.reset();
        stream.reset();
        recordedPlayerId = Integer.MIN_VALUE;
        recordedGameMode = GameType.SURVIVAL;
        ready = false;
        sawPosition = false;
    }

    boolean sawPosition() {
        return sawPosition;
    }

    void markReady() {
        ready = true;
    }

    void beginRestore(State state) {
        stream.reset();
        ready = false;
        recordedPlayerId = state.recordedPlayerId();
        recordedGameMode = state.recordedGameMode();
        hud = state.hud().copy();
        screens = state.screens().copy();
        chat = state.chat().copy();
    }

    State save() {
        return new State(recordedPlayerId, recordedGameMode, hud.copy(), screens.copy(), chat.copy());
    }

    ReplayScreenState screens() {
        return screens;
    }

    ReplayChatState chat() {
        return chat;
    }

    ReplayStreamState stream() {
        return stream;
    }

    int recordedPlayerId() {
        return recordedPlayerId;
    }

    GameType recordedGameMode() {
        return recordedGameMode;
    }

    ReplayHudState hud() {
        return hud;
    }

    Packet<?> rewrite(Packet<?> packet) {
        if (packet instanceof BundlePacket<?> bundle) {
            List<Packet<? super ClientGamePacketListener>> kept = new ArrayList<>();
            for (Packet<?> sub : bundle.subPackets()) {
                Packet<?> t = rewrite(sub);
                if (t != null) {
                    @SuppressWarnings("unchecked")
                    Packet<? super ClientGamePacketListener> cast = (Packet<? super ClientGamePacketListener>) t;
                    kept.add(cast);
                }
            }
            return new ClientboundBundlePacket(kept);
        }
        stream.observe(packet);
        if (screens.capture(packet)) return null;
        if (hud.capture(packet)) return null;
        if (chat.capture(packet)) return null;
        if (DROPPED.contains(packet.getClass())) return null;

        if (packet instanceof ClientboundGameProfilePacket finished) {
            return new ClientboundGameProfilePacket(new GameProfile(CAMERA_UUID, "YMM4Craft"), false);
        }
        if (isLoginPhase(packet)) return null;

        if (packet instanceof ClientboundLoginPacket login) {
            recordedPlayerId = login.playerId();
            recordedGameMode = login.commonPlayerSpawnInfo().gameType();
            return new ClientboundLoginPacket(CAMERA_ENTITY_ID, login.hardcore(), login.levels(), login.maxPlayers(),
                    login.chunkRadius(), login.simulationDistance(), login.reducedDebugInfo(), false,
                    login.doLimitedCrafting(), spectator(login.commonPlayerSpawnInfo()), false);
        }
        if (packet instanceof ClientboundRespawnPacket respawn) {
            recordedGameMode = respawn.commonPlayerSpawnInfo().gameType();
            return new ClientboundRespawnPacket(spectator(respawn.commonPlayerSpawnInfo()), respawn.dataToKeep());
        }
        if (packet instanceof ClientboundGameEventPacket event) {
            var type = event.getEvent();
            if (type == ClientboundGameEventPacket.CHANGE_GAME_MODE) recordedGameMode = GameType.byId(Mth.floor(event.getParam() + 0.5F));
            if (type == ClientboundGameEventPacket.CHANGE_GAME_MODE || type == ClientboundGameEventPacket.WIN_GAME
                    || type == ClientboundGameEventPacket.DEMO_EVENT || type == ClientboundGameEventPacket.NO_RESPAWN_BLOCK_AVAILABLE) {
                return null;
            }
        }
        if (packet instanceof ClientboundPlayerPositionPacket) {
            if (ready) return null;
            sawPosition = true;
        }
        return packet;
    }

    private static boolean isLoginPhase(Packet<?> packet) {
        return packet instanceof ClientboundHelloPacket
                || packet instanceof ClientboundLoginCompressionPacket
                || packet instanceof ClientboundCustomQueryPacket
                || packet instanceof ClientboundLoginDisconnectPacket;
    }

    private static CommonPlayerSpawnInfo spectator(CommonPlayerSpawnInfo info) {
        return new CommonPlayerSpawnInfo(info.dimensionType(), info.dimension(), info.seed(), GameType.SPECTATOR,
                null, info.isDebug(), info.isFlat(), info.lastDeathLocation(), info.portalCooldown());
    }
}
