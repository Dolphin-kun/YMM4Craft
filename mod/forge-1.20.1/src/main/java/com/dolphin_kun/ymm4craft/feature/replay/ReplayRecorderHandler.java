package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ModConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.List;

final class ReplayRecorderHandler extends ChannelInboundHandlerAdapter {
    private final Connection connection;
    private final List<byte[]> pendingLogin = new ArrayList<>();
    private ReplayRecording recording;
    private boolean failed;

    ReplayRecorderHandler(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!failed && msg instanceof Packet<?> packet) {
            try {
                record(ctx, packet);
            } catch (Throwable t) {
                failed = true;
                Ymm4Craft.LOGGER.warn("リプレイの録画に失敗したので、この接続の録画をやめます", t);
                if (recording != null) recording.finish();
            }
        }
        super.channelRead(ctx, msg);
    }

    private void record(ChannelHandlerContext ctx, Packet<?> packet) {
        ConnectionProtocol protocol = ReplayRecorder.protocolOf(ctx.pipeline());
        if (protocol == null) return;
        byte kind = protocol == ConnectionProtocol.LOGIN ? ReplayFiles.LOGIN : protocol == ConnectionProtocol.PLAY ? ReplayFiles.PLAY : -1;
        if (kind < 0) return;

        if (recording == null) {
            if (kind == ReplayFiles.LOGIN) {
                pendingLogin.add(ReplayProtocol.encode(protocol, packet));
                return;
            }
            boolean singleplayer = connection.isMemoryConnection();
            ModConfig config = ModConfig.get();
            if (!ReplayRecorder.enabled() || (singleplayer ? !config.recordSingleplayer : !config.recordMultiplayer)) {
                failed = true;
                return;
            }
            recording = ReplayRecording.start(ctx.channel(), singleplayer);
            if (recording == null) {
                failed = true;
                return;
            }
            for (byte[] login : pendingLogin) recording.write(0, ReplayFiles.LOGIN, login);
            pendingLogin.clear();
        }
        if (recording.finished) return;
        int time = recording.timeMs();
        if (packet instanceof BundlePacket<?> bundle) {
            byte[] delimiter = ReplayProtocol.encode(protocol, new BundleDelimiterPacket<>());
            recording.write(time, kind, delimiter);
            for (Packet<?> sub : bundle.subPackets()) recording.write(time, kind, ReplayProtocol.encode(protocol, sub));
            recording.write(time, kind, delimiter);
            return;
        }
        recording.write(time, kind, ReplayProtocol.encode(protocol, packet));
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (recording != null) recording.finish();
        super.channelInactive(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        if (recording != null) recording.finish();
    }
}
