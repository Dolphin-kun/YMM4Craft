package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ModConfig;
import com.dolphin_kun.ymm4craft.mixin.PacketDecoderAccessor;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketDecoder;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSwingAnimationPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

import java.time.format.DateTimeFormatter;

public final class ReplayRecorder {
    private static final String HANDLER_NAME = "ymm4craft_recorder";
    static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    static volatile ReplayRecording active;

    private ReplayRecorder() {
    }

    static boolean enabled() {
        return ModConfig.get().recordReplays && !Ymm4Craft.isActive();
    }

    public static void onConnectionCreated(Connection connection, ChannelPipeline pipeline) {
        try {
            if (!enabled()) return;
            String before = pipeline.get("decoder") != null ? "decoder" : "inbound_config";
            if (pipeline.get(before) == null || pipeline.get(HANDLER_NAME) != null) return;
            pipeline.addBefore(before, HANDLER_NAME, new ReplayRecorderHandler(connection));
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("リプレイの録画を始められませんでした", t);
        }
    }

    public static void onClientTick(Minecraft mc) {
        ReplayRecording r = active;
        if (r == null) return;
        try {
            r.onClientTick(mc);
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.warn("リプレイの録画に失敗したので、録画をやめます", t);
            r.finish();
        }
    }

    public static void onSelfSwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation) {
        ReplayRecording r = active;
        if (r == null) return;
        try {
            r.writeSynthetic(new ClientboundSwingAnimationPacket(player.getId(), hand, animation));
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.debug("腕の振りを記録できませんでした", t);
        }
    }

    public static void onSelfPacket(Packet<?> packet) {
        ReplayRecording r = active;
        if (r == null) return;
        try {
            r.writeSynthetic(packet);
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.debug("自分の動きを記録できませんでした", t);
        }
    }

    public static void onGameStopping() {
        ReplayRecording r = active;
        if (r != null) r.finish();
    }

    static ConnectionProtocol protocolOf(ChannelPipeline pipeline) {
        ProtocolInfo<?> info = protocolInfoOf(pipeline);
        return info == null ? null : info.id();
    }

    static ProtocolInfo<?> protocolInfoOf(ChannelPipeline pipeline) {
        ChannelHandler decoder = pipeline.get("decoder");
        return decoder instanceof PacketDecoder<?> d ? ((PacketDecoderAccessor) d).ymm4craft$getProtocolInfo() : null;
    }

}
