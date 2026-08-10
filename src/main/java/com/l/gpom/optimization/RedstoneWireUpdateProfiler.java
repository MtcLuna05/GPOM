package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.LongAdder;

public final class RedstoneWireUpdateProfiler {
    private static final ThreadLocal<Deque<Long>> STARTS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final LongAdder CALLS = new LongAdder();
    private static final LongAdder NANOS = new LongAdder();
    private static volatile long lastReportNanos = System.nanoTime();

    private RedstoneWireUpdateProfiler() {
    }

    public static void enter() {
        STARTS.get().push(System.nanoTime());
    }

    public static void exit() {
        Deque<Long> starts = STARTS.get();
        if (starts.isEmpty()) {
            return;
        }
        NANOS.add(System.nanoTime() - starts.pop());
        CALLS.increment();
        reportIfDue();
    }

    private static void reportIfDue() {
        long now = System.nanoTime();
        long interval = GpomEarlyConfig.redstoneProfilerIntervalSeconds() * 1_000_000_000L;
        if (now - lastReportNanos < interval) {
            return;
        }
        synchronized (RedstoneWireUpdateProfiler.class) {
            if (now - lastReportNanos < interval) {
                return;
            }
            long calls = CALLS.sumThenReset();
            long nanos = NANOS.sumThenReset();
            lastReportNanos = now;
            GPOM.LOGGER.info("[GPOM Redstone Profiler] wireUpdates={} totalMillis={} averageMicros={}",
                    calls, nanos / 1_000_000L, calls == 0L ? 0L : nanos / calls / 1_000L);
        }
    }
}
