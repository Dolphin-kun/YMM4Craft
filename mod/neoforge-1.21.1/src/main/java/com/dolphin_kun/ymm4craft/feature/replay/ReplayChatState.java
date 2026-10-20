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

    boolean capture(Packet<?> packet) {
        Component content = switch (packet) {
            case ClientboundPlayerChatPacket chat ->
                    chat.chatType().decorate(chat.unsignedContent() != null ? chat.unsignedContent() : Component.literal(chat.body().content()));
            case ClientboundDisguisedChatPacket chat -> chat.chatType().decorate(chat.message());
            case ClientboundSystemChatPacket chat -> chat.overlay() ? null : chat.content();
            default -> null;
        };
        if (content == null) {
            return packet instanceof ClientboundPlayerChatPacket || packet instanceof ClientboundDisguisedChatPacket
                    || packet instanceof ClientboundSystemChatPacket || packet instanceof ClientboundDeleteChatPacket;
        }
        messages.add(new Message(tick, content));
        if (messages.size() > MAX_MESSAGES) messages.removeFirst();
        revision++;
        return true;
    }
}
