package com.dolphin_kun.ymm4craft.feature.replay;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelPipeline;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

final class ReplayProtocol {
    private ReplayProtocol() {
    }

    static ConnectionProtocol of(ChannelPipeline pipeline) {
        return pipeline.channel().attr(Connection.ATTRIBUTE_PROTOCOL).get();
    }

    static void encode(ConnectionProtocol protocol, Packet<?> packet, FriendlyByteBuf buf) {
        int id = protocol.getPacketId(PacketFlow.CLIENTBOUND, packet);
        if (id < 0) throw new IllegalArgumentException("記録できないパケットです: " + packet.getClass().getSimpleName());
        buf.writeVarInt(id);
        packet.write(buf);
    }

    static byte[] encode(ConnectionProtocol protocol, Packet<?> packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer(64));
        try {
            encode(protocol, packet, buf);
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return bytes;
        } finally {
            buf.release();
        }
    }
}
