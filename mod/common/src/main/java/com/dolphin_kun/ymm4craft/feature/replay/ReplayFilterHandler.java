package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.RunningOnDifferentThreadException;

import java.util.function.Supplier;

final class ReplayFilterHandler extends ChannelInboundHandlerAdapter {
    private final ReplayPacketRewriter rewriter;
    private final Supplier<Connection> connection;

    ReplayFilterHandler(ReplayPacketRewriter rewriter, Supplier<Connection> connection) {
        this.rewriter = rewriter;
        this.connection = connection;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Packet<?> packet)) {
            ctx.fireChannelRead(msg);
            return;
        }
        Packet<?> replaced = rewriter.rewrite(packet);
        Connection c = connection.get();
        if (replaced == null || c == null) return;
        PacketListener listener = c.getPacketListener();
        if (listener == null || !listener.shouldHandleMessage(replaced)) return;
        try {
            handle(replaced, listener);
        } catch (RunningOnDifferentThreadException ignored) {
        } catch (Throwable t) {
            Ymm4Craft.LOGGER.debug("リプレイのパケットを処理できませんでした: {}", replaced.type(), t);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Ymm4Craft.LOGGER.debug("リプレイの通信路で例外が起きました", cause);
    }

    @SuppressWarnings("unchecked")
    private static <T extends PacketListener> void handle(Packet<T> packet, PacketListener listener) {
        packet.handle((T) listener);
    }
}
