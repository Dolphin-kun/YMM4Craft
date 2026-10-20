package com.dolphin_kun.ymm4craft.feature.replay;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import net.minecraft.network.protocol.game.ClientboundDisguisedChatPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

import java.util.ArrayList;
import java.util.List;

final class ReplayChatState {
    static final int MAX_MESSAGES = 100;

    record Message(int tick, Component content) {
    }

    private final List<Message> messages;
    private int tick;
    private long revision;

    ReplayChatState() {
        this(new ArrayList<>(), 0);
    }

    private ReplayChatState(List<Message> messages, int tick) {
        this.messages = messages;
        this.tick = tick;
    }

    void reset() {
        messages.clear();
        tick = 0;
        revision++;
    }

    void setTick(int tick) {
        this.tick = tick;
    }

    long revision() {
        return revision;
    }

    List<Message> messages() {
        return messages;
    }

    ReplayChatState copy() {
        return new ReplayChatState(new ArrayList<>(messages), tick);
    }

    private static Component decorate(net.minecraft.network.chat.ChatType.BoundNetwork type, Component content) {
        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null) return content;
        return type.resolve(connection.registryAccess()).map(bound -> bound.decorate(content)).orElse(content);
    }

    boolean capture(Packet<?> packet) {
        Component content = null;
        if (packet instanceof ClientboundPlayerChatPacket chat) {
            content = decorate(chat.chatType(), chat.unsignedContent() != null ? chat.unsignedContent() : Component.literal(chat.body().content()));
        } else if (packet instanceof ClientboundDisguisedChatPacket chat) {
            content = decorate(chat.chatType(), chat.message());
        } else if (packet instanceof ClientboundSystemChatPacket chat) {
            content = chat.overlay() ? null : chat.content();
        }
        if (content == null) {
            return packet instanceof ClientboundPlayerChatPacket || packet instanceof ClientboundDisguisedChatPacket
                    || packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundDeleteChatPacket;
        }
        messages.add(new Message(tick, content));
        if (messages.size() > MAX_MESSAGES) messages.remove(0);
        revision++;
        return true;
    }
}
