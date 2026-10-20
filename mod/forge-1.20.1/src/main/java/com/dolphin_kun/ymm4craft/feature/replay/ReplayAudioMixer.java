package com.dolphin_kun.ymm4craft.feature.replay;

import com.dolphin_kun.ymm4craft.Ymm4Craft;
import com.mojang.blaze3d.audio.OggAudioStream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.sound.sampled.AudioFormat;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleConsumer;

final class ReplayAudioMixer {
    static final int SAMPLE_RATE = 48000;
    private static final int SAMPLES_PER_TICK = SAMPLE_RATE / 20;

    private record Pcm(float[] left, float[] right, int rate, boolean stereo) {
    }

    private final ResourceManager resources;
    private final Map<ResourceLocation, Pcm> decoded = new HashMap<>();

    ReplayAudioMixer(ResourceManager resources) {
        this.resources = resources;
    }

    void mix(List<ReplaySoundEvent> events, ReplayListenerPath listener, int ticks, Path out, DoubleConsumer progress) throws IOException {
        int total = ticks * SAMPLES_PER_TICK;
        float[] left = new float[total];
        float[] right = new float[total];
        for (int i = 0; i < events.size(); i++) {
            ReplaySoundEvent event = events.get(i);
            Pcm pcm = decode(event.path);
            if (pcm != null) mixEvent(event, pcm, listener, ticks, left, right);
            progress.accept((i + 1) / (double) events.size());
        }
        write(out, left, right);
    }

    private static void mixEvent(ReplaySoundEvent event, Pcm pcm, ReplayListenerPath listener, int ticks, float[] left, float[] right) {
        double position = 0;
        int length = pcm.left.length;
        if (length == 0) return;
        for (int tick = Math.max(event.startTick, 0); tick < ticks; tick++) {
            float[] state = event.stateAt(tick);
            if (state == null) return;
            float[] gains = gains(event, pcm, state, listener.at(tick));
            double rate = state[4] * pcm.rate / (double) SAMPLE_RATE;
            int base = tick * SAMPLES_PER_TICK;
            for (int s = 0; s < SAMPLES_PER_TICK; s++) {
                if (position >= length) {
                    if (!event.looping) return;
                    position -= length;
                }
                int i = (int) position;
                float k = (float) (position - i);
                int j = i + 1 < length ? i + 1 : event.looping ? 0 : i;
                float l = pcm.left[i] + (pcm.left[j] - pcm.left[i]) * k;
                float r = pcm.right[i] + (pcm.right[j] - pcm.right[i]) * k;
                left[base + s] += l * gains[0];
                right[base + s] += r * gains[1];
                position += rate;
            }
        }
    }

    private static float[] gains(ReplaySoundEvent event, Pcm pcm, float[] state, float[] ear) {
        float volume = Math.max(0f, Math.min(state[3], 1f));
        if (pcm.stereo || event.relative || ear == null) return new float[]{volume, volume};
        double dx = state[0] - ear[0];
        double dy = state[1] - ear[1];
        double dz = state[2] - ear[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (event.linear) volume *= (float) Math.max(0, 1 - distance / Math.max(event.attenuation, 0.001));
        double pan = 0;
        if (distance > 1e-4) {
            double yaw = Math.toRadians(ear[3]);
            pan = Math.max(-1, Math.min((dx * -Math.cos(yaw) + dz * -Math.sin(yaw)) / distance, 1));
        }
        double angle = (pan + 1) * Math.PI / 4;
        return new float[]{(float) (volume * Math.cos(angle)), (float) (volume * Math.sin(angle))};
    }

    private Pcm decode(ResourceLocation path) {
        if (decoded.containsKey(path)) return decoded.get(path);
        Pcm pcm = null;
        try (InputStream in = resources.open(path); OggAudioStream stream = new OggAudioStream(in)) {
            AudioFormat format = stream.getFormat();
            pcm = toPcm(stream.readAll(), format);
        } catch (IOException | RuntimeException e) {
            Ymm4Craft.LOGGER.debug("音を読めませんでした: {}", path, e);
        }
        decoded.put(path, pcm);
        return pcm;
    }

    private static Pcm toPcm(ByteBuffer bytes, AudioFormat format) {
        if (format.getSampleSizeInBits() != 16) return null;
        ByteBuffer data = bytes.duplicate().order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        int channels = Math.max(1, format.getChannels());
        int frames = data.remaining() / (2 * channels);
        float[] left = new float[frames];
        float[] right = channels == 1 ? left : new float[frames];
        for (int f = 0; f < frames; f++) {
            left[f] = data.getShort() / 32768f;
            if (channels > 1) {
                right[f] = data.getShort() / 32768f;
                for (int c = 2; c < channels; c++) data.getShort();
            }
        }
        return new Pcm(left, right, (int) format.getSampleRate(), channels > 1);
    }

    private static void write(Path out, float[] left, float[] right) throws IOException {
        int dataSize = left.length * 4;
        try (OutputStream raw = Files.newOutputStream(out); DataOutputStream o = new DataOutputStream(new BufferedOutputStream(raw, 1 << 16))) {
            o.writeBytes("RIFF");
            o.writeInt(Integer.reverseBytes(36 + dataSize));
            o.writeBytes("WAVEfmt ");
            o.writeInt(Integer.reverseBytes(16));
            o.writeShort(Short.reverseBytes((short) 1));
            o.writeShort(Short.reverseBytes((short) 2));
            o.writeInt(Integer.reverseBytes(SAMPLE_RATE));
            o.writeInt(Integer.reverseBytes(SAMPLE_RATE * 4));
            o.writeShort(Short.reverseBytes((short) 4));
            o.writeShort(Short.reverseBytes((short) 16));
            o.writeBytes("data");
            o.writeInt(Integer.reverseBytes(dataSize));
            for (int i = 0; i < left.length; i++) {
                o.writeShort(Short.reverseBytes(toShort(left[i])));
                o.writeShort(Short.reverseBytes(toShort(right[i])));
            }
        }
    }

    private static short toShort(float v) {
        return (short) Math.round(Math.max(-1f, Math.min(v, 1f)) * 32767f);
    }
}
