package com.l.gpom.profiling;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RuntimeSinkProfiler {
    private static final boolean ENABLED = GpomEarlyConfig.runtimeSinkProfilerEnabled();
    private static final boolean PROFILE_FORGE_EVENTS = GpomEarlyConfig.runtimeSinkProfilerForgeEventsEnabled();
    private static final boolean PROFILE_ALL_FORGE_EVENTS = GpomEarlyConfig.runtimeSinkProfilerAllForgeEventsEnabled();
    private static final boolean IMMEDIATE_SLOW_LOGS = GpomEarlyConfig.runtimeSinkProfilerImmediateSlowLogsEnabled();
    private static final long SUMMARY_INTERVAL_NANOS = Math.max(1, GpomEarlyConfig.runtimeSinkProfilerSummaryIntervalSeconds()) * 1_000_000_000L;
    private static final long SLOW_THRESHOLD_NANOS = Math.max(1, GpomEarlyConfig.runtimeSinkProfilerSlowThresholdMillis()) * 1_000_000L;
    private static final int TOP_COUNT = Math.max(1, GpomEarlyConfig.runtimeSinkProfilerTopCount());
    private static final Object LOCK = new Object();
    private static final Map<String, SinkData> SINKS = new LinkedHashMap<>();
    private static long nextSummaryAtNanos;
    private static long windowStartedAtNanos = System.nanoTime();

    private RuntimeSinkProfiler() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static long begin() {
        return ENABLED ? System.nanoTime() : 0L;
    }

    public static void end(String category, String label, long startedAt) {
        if (!ENABLED || startedAt == 0L) {
            return;
        }
        record(category, label, System.nanoTime() - startedAt);
    }

    public static void record(String category, String label, long elapsedNanos) {
        if (!ENABLED || elapsedNanos <= 0L) {
            return;
        }
        String safeCategory = clean(category, "unknown");
        String safeLabel = clean(label, "unknown");
        String key = safeCategory + ' ' + safeLabel;
        long now = System.nanoTime();
        synchronized (LOCK) {
            SINKS.computeIfAbsent(key, ignored -> new SinkData(safeCategory, safeLabel)).add(elapsedNanos);
            if (IMMEDIATE_SLOW_LOGS && elapsedNanos >= SLOW_THRESHOLD_NANOS) {
                AsyncProbeLogger.info(
                        "[RuntimeSink] slow category={} elapsed={}ms label={}",
                        safeCategory,
                        formatMillis(elapsedNanos),
                        safeLabel
                );
            }
            if (nextSummaryAtNanos == 0L) {
                nextSummaryAtNanos = now + SUMMARY_INTERVAL_NANOS;
            } else if (now >= nextSummaryAtNanos) {
                logSummaryLocked(now);
                SINKS.clear();
                windowStartedAtNanos = now;
                nextSummaryAtNanos = now + SUMMARY_INTERVAL_NANOS;
            }
        }
    }

    public static boolean shouldProfileForgeEvent(Event event) {
        if (!ENABLED || !PROFILE_FORGE_EVENTS || event == null) {
            return false;
        }
        if (PROFILE_ALL_FORGE_EVENTS) {
            return true;
        }
        String name = event.getClass().getName();
        return name.startsWith("net.minecraftforge.fml.common.gameevent.TickEvent$")
                || name.startsWith("net.minecraftforge.event.world.WorldEvent")
                || name.startsWith("net.minecraftforge.event.world.ChunkEvent")
                || name.equals("net.minecraftforge.client.event.RenderWorldLastEvent")
                || name.startsWith("net.minecraftforge.client.event.RenderGameOverlayEvent")
                || name.startsWith("net.minecraftforge.client.event.GuiScreenEvent");
    }

    public static String eventName(Event event) {
        if (event == null) {
            return "<null>";
        }
        return event.getClass().getName();
    }

    public static void recordEventPost(Event event, long startedAt) {
        if (startedAt == 0L) {
            return;
        }
        end("eventPost", eventName(event), startedAt);
    }

    public static void recordEventHandler(Event event, String owner, String readable, long startedAt) {
        if (startedAt == 0L) {
            return;
        }
        String label = eventName(event) + ' ' + clean(owner, "<unknown>") + ' ' + clean(readable, "<unknown>");
        end("eventHandler", label, startedAt);
    }

    private static void logSummaryLocked(long now) {
        if (SINKS.isEmpty()) {
            return;
        }
        long windowMillis = Math.max(1L, (now - windowStartedAtNanos) / 1_000_000L);
        List<SinkData> byTotal = new ArrayList<>(SINKS.values());
        byTotal.sort(Comparator.comparingLong(SinkData::totalNanos).reversed());

        StringBuilder builder = new StringBuilder(512);
        int limit = Math.min(TOP_COUNT, byTotal.size());
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                builder.append(" | ");
            }
            SinkData sink = byTotal.get(i);
            builder.append(sink.category)
                    .append(' ')
                    .append(sink.label)
                    .append(" count=")
                    .append(sink.count)
                    .append(" total=")
                    .append(formatMillis(sink.totalNanos))
                    .append("ms max=")
                    .append(formatMillis(sink.maxNanos))
                    .append("ms avg=")
                    .append(formatMillis(sink.totalNanos / Math.max(1L, sink.count)))
                    .append("ms");
        }
        AsyncProbeLogger.info("[RuntimeSinkSummary] window={}ms entries={} topByTotal={}", windowMillis, SINKS.size(), builder.toString());
    }

    private static String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed.replace('\n', ' ').replace('\r', ' ');
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static final class SinkData {
        private final String category;
        private final String label;
        private long count;
        private long totalNanos;
        private long maxNanos;

        private SinkData(String category, String label) {
            this.category = category;
            this.label = label;
        }

        private void add(long elapsedNanos) {
            count++;
            totalNanos += elapsedNanos;
            if (elapsedNanos > maxNanos) {
                maxNanos = elapsedNanos;
            }
        }

        private long totalNanos() {
            return totalNanos;
        }
    }
}
