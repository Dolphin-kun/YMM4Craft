package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.dolphin_kun.ymm4craft.core.ModConfig;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
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
        if (!failed && msg instanceof ByteBuf buf) {
            try {
                record(ctx, buf);
            } catch (Throwable t) {
                failed = true;
                Ymm4Craft.LOGGER.warn("リプレイの録画に失敗したので、この接続の録画をやめます", t);
                if (recording != null) recording.finish();
            }
        }
        super.channelRead(ctx, msg);
    }

    private void record(ChannelHandlerContext ctx, ByteBuf buf) {
        ConnectionProtocol protocol = ReplayRecorder.protocolOf(ctx.pipeline());
        if (protocol == null) return;
        byte kind = switch (protocol) {
            case LOGIN -> ReplayFiles.LOGIN;
            case CONFIGURATION -> ReplayFiles.CONFIGURATION;
            case PLAY -> ReplayFiles.PLAY;
            default -> -1;
        };
        if (kind < 0) return;
        byte[] bytes = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), bytes);

        if (recording == null) {
            if (kind == ReplayFiles.LOGIN) {
                pendingLogin.add(bytes);
                return;
            }
            if (kind != ReplayFiles.CONFIGURATION) return;
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
        recording.write(recording.timeMs(), kind, bytes);
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
