package com.l.gpom.optimization;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.util.EnumParticleTypes;

import java.util.Arrays;

public final class ParticleSpawnThrottler {
    private static final long WINDOW_NANOS = 50_000_000L;
    private static final ThreadLocal<Window> WINDOWS = ThreadLocal.withInitial(Window::new);

    private ParticleSpawnThrottler() {
    }

    public static boolean shouldSuppress(EnumParticleTypes type) {
        if (type == null) {
            return false;
        }
        Window window = WINDOWS.get();
        long now = System.nanoTime();
        if (now - window.startedAtNanos >= WINDOW_NANOS || now < window.startedAtNanos) {
            window.startedAtNanos = now;
            Arrays.fill(window.counts, 0);
        }
        int ordinal = type.ordinal();
        if (ordinal >= window.counts.length) {
            int[] expanded = new int[Math.max(ordinal + 1, window.counts.length * 2)];
            System.arraycopy(window.counts, 0, expanded, 0, window.counts.length);
            window.counts = expanded;
        }
        int cap = GpomEarlyConfig.particleSpawnThrottleMaxPerTypePerTick();
        return window.counts[ordinal]++ >= cap;
    }

    private static final class Window {
        private long startedAtNanos = System.nanoTime();
        private int[] counts = new int[64];
    }
}
