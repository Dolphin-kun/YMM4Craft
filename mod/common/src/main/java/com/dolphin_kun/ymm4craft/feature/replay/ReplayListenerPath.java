package com.dolphin_kun.ymm4craft.feature.replay;

import java.util.List;

final class ReplayListenerPath {
    private final double from;
    private final double[][] samples;
    private final List<float[]> recorded;

    private ReplayListenerPath(double from, double[][] samples, List<float[]> recorded) {
        this.from = from;
        this.samples = samples;
        this.recorded = recorded;
    }

    static ReplayListenerPath camera(double from, double[][] samples) {
        return new ReplayListenerPath(from, samples, null);
    }

    static ReplayListenerPath recordedPlayer(List<float[]> recorded) {
        return new ReplayListenerPath(0, null, recorded);
    }

    float[] at(int tick) {
        if (recorded != null) {
            if (recorded.isEmpty()) return null;
            for (int i = Math.clamp(tick - 1, 0, recorded.size() - 1); i >= 0; i--) {
                if (recorded.get(i) != null) return recorded.get(i);
            }
            return null;
        }
        if (samples.length == 0) return null;
        double t = from + tick / 20.0;
        if (t <= samples[0][0]) return toFloat(samples[0]);
        for (int i = 1; i < samples.length; i++) {
            if (samples[i][0] >= t) {
                double[] a = samples[i - 1];
                double[] b = samples[i];
                double k = b[0] > a[0] ? (t - a[0]) / (b[0] - a[0]) : 1;
                float[] r = new float[5];
                for (int j = 0; j < 5; j++) r[j] = (float) (a[j + 1] + (b[j + 1] - a[j + 1]) * k);
                return r;
            }
        }
        return toFloat(samples[samples.length - 1]);
    }

    private static float[] toFloat(double[] s) {
        return new float[]{(float) s[1], (float) s[2], (float) s[3], (float) s[4], (float) s[5]};
    }
}
