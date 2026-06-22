package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.Map;
import java.util.WeakHashMap;

public final class RenderUpdateDeduplicator {
    private static final Map<Object, State> STATES = new WeakHashMap<>();
    private static long clientTick;

    private RenderUpdateDeduplicator() {
    }

    public static void nextClientTick() {
        clientTick++;
        if (clientTick == Long.MAX_VALUE) {
            clientTick = 1L;
        }
    }

    public static boolean shouldSuppress(Object renderGlobal, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (!GpomEarlyConfig.dedupeRenderSectionUpdatesEnabled() || renderGlobal == null || clientTick <= 0L) {
            return false;
        }

        int sectionMinX = Math.min(minX, maxX) >> 4;
        int sectionMaxX = Math.max(minX, maxX) >> 4;
        int sectionMinY = Math.min(minY, maxY) >> 4;
        int sectionMaxY = Math.max(minY, maxY) >> 4;
        int sectionMinZ = Math.min(minZ, maxZ) >> 4;
        int sectionMaxZ = Math.max(minZ, maxZ) >> 4;
        if (sectionMinX != sectionMaxX || sectionMinY != sectionMaxY || sectionMinZ != sectionMaxZ) {
            return false;
        }

        long key = sectionKey(sectionMinX, sectionMinY, sectionMinZ);
        synchronized (STATES) {
            State state = STATES.get(renderGlobal);
            if (state == null) {
                state = new State();
                STATES.put(renderGlobal, state);
            }
            if (state.tick != clientTick) {
                state.tick = clientTick;
                state.sections.clear();
            }
            return !state.sections.add(key);
        }
    }

    private static long sectionKey(int sectionX, int sectionY, int sectionZ) {
        return ((long) sectionX & 0x3FFFFFFL) << 38
                | ((long) sectionZ & 0x3FFFFFFL) << 12
                | ((long) sectionY & 0xFFFL);
    }

    private static final class State {
        private long tick;
        private final LongOpenHashSet sections = new LongOpenHashSet();
    }
}
