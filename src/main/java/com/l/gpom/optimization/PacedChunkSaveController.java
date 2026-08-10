package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;

public final class PacedChunkSaveController {
    private static final long PROGRESS_INTERVAL_NANOS = 10_000_000_000L;
    private static final long[] LATENCY_BUCKET_UPPER_NANOS = {
            1_000_000L,
            4_000_000L,
            10_000_000L,
            25_000_000L,
            50_000_000L,
            100_000_000L,
            250_000_000L,
            1_000_000_000L,
            Long.MAX_VALUE
    };
    private static final Deque<ChunkProviderServer> PENDING = new ArrayDeque<>();
    private static final Set<ChunkProviderServer> PENDING_SET =
            Collections.newSetFromMap(new IdentityHashMap<ChunkProviderServer, Boolean>());
    private static final ThreadLocal<Boolean> DEFERRING_PERIODIC_SAVE =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final ThreadLocal<Boolean> ACCEPTING_NEW_PROVIDERS =
            ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static final long[] PROVIDER_LATENCY_BUCKETS = new long[LATENCY_BUCKET_UPPER_NANOS.length];
    private static MinecraftServer activeServer;
    private static long cycleStartedNanos;
    private static long nextProgressLogNanos;
    private static long metadataNanos;
    private static long providerNanos;
    private static long maximumProviderNanos;
    private static int scheduledProviders;
    private static int providerCalls;
    private static int overBudgetProviderCalls;
    private static int deferredFullSaveCalls;
    private static int metadataRefreshes;

    private PacedChunkSaveController() {
    }

    public static void beginAutosave(MinecraftServer server) {
        if (server == null) {
            return;
        }
        boolean refreshOnly = !PENDING.isEmpty();
        if (!refreshOnly) {
            resetCycleMetrics();
        }
        activeServer = server;
        long started = System.nanoTime();
        DEFERRING_PERIODIC_SAVE.set(Boolean.TRUE);
        ACCEPTING_NEW_PROVIDERS.set(!refreshOnly);
        boolean invoked;
        try {
            // Vanilla's periodic call passes true here to suppress its per-world
            // save messages. WorldServer still receives saveAllChunks(true).
            invoked = MinecraftMappingCompat.minecraftServerSaveAllWorlds(server, true);
        } finally {
            ACCEPTING_NEW_PROVIDERS.remove();
            DEFERRING_PERIODIC_SAVE.remove();
            metadataNanos += System.nanoTime() - started;
        }
        if (!invoked) {
            GPOM.LOGGER.warn("[GPOM Paced Save] Could not enter vanilla periodic save; falling back to a full save");
            fallBackToFullSave();
            return;
        }
        if (refreshOnly) {
            metadataRefreshes++;
            GPOM.LOGGER.warn("[GPOM Paced Save] Previous cycle still has {} provider(s) after {} ms; "
                            + "refreshed level metadata/events without re-enqueueing completed providers "
                            + "(refreshes={})",
                    PENDING.size(), elapsedMillis(), metadataRefreshes);
            return;
        }
        if (PENDING.isEmpty()) {
            finishCycle();
            return;
        }
        GPOM.LOGGER.info("[GPOM Paced Save] Scheduled {} world provider(s) from {} vanilla full-save call(s) "
                        + "after {} ms of level metadata/events",
                scheduledProviders, deferredFullSaveCalls, nanosToMillis(metadataNanos));
    }

    public static boolean saveChunksOrDefer(ChunkProviderServer provider, boolean all) {
        if (provider == null) {
            return false;
        }
        if (Boolean.TRUE.equals(DEFERRING_PERIODIC_SAVE.get())) {
            if (Boolean.TRUE.equals(ACCEPTING_NEW_PROVIDERS.get()) && PENDING_SET.add(provider)) {
                PENDING.addLast(provider);
                scheduledProviders++;
            }
            if (all) {
                deferredFullSaveCalls++;
            }
            return false;
        }
        Boolean result = MinecraftMappingCompat.chunkProviderSaveChunks(provider, all);
        return Boolean.TRUE.equals(result);
    }

    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + GpomEarlyConfig.pacedChunkSaveBudgetMillis() * 1_000_000L;
        int batches = GpomEarlyConfig.pacedChunkSaveMaxBatchesPerTick();
        while (batches-- > 0 && !PENDING.isEmpty()) {
            ChunkProviderServer provider = PENDING.removeFirst();
            long started = System.nanoTime();
            Boolean complete = MinecraftMappingCompat.chunkProviderSaveChunks(provider, false);
            long duration = System.nanoTime() - started;
            providerNanos += duration;
            providerCalls++;
            recordProviderLatency(duration);
            if (complete == null) {
                fallBackToFullSave();
                return;
            }
            if (!Boolean.TRUE.equals(complete)) {
                PENDING.addLast(provider);
            } else {
                PENDING_SET.remove(provider);
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (PENDING.isEmpty()) {
            finishCycle();
        } else {
            logProgressIfDue();
        }
    }

    private static void fallBackToFullSave() {
        PENDING.clear();
        PENDING_SET.clear();
        MinecraftServer server = activeServer;
        activeServer = null;
        if (server != null) {
            MinecraftMappingCompat.minecraftServerSaveAllWorlds(server, true);
        }
        resetCycleMetrics();
    }

    private static void finishCycle() {
        long elapsed = cycleStartedNanos == 0L ? 0L : System.nanoTime() - cycleStartedNanos;
        GPOM.LOGGER.info("[GPOM Paced Save] Completed providers={} deferredFullCalls={} calls={} "
                        + "metadataRefreshes={} metadataMillis={} providerMillis={} maxCallMillis={} "
                        + "p95UpperMillis={} overBudgetCalls={} elapsedMillis={}",
                scheduledProviders, deferredFullSaveCalls, providerCalls, metadataRefreshes,
                nanosToMillis(metadataNanos), nanosToMillis(providerNanos), nanosToMillis(maximumProviderNanos),
                p95UpperBoundMillis(), overBudgetProviderCalls, nanosToMillis(elapsed));
        activeServer = null;
        PENDING.clear();
        PENDING_SET.clear();
        resetCycleMetrics();
    }

    private static void resetCycleMetrics() {
        cycleStartedNanos = System.nanoTime();
        nextProgressLogNanos = cycleStartedNanos + PROGRESS_INTERVAL_NANOS;
        metadataNanos = 0L;
        providerNanos = 0L;
        maximumProviderNanos = 0L;
        scheduledProviders = 0;
        providerCalls = 0;
        overBudgetProviderCalls = 0;
        deferredFullSaveCalls = 0;
        metadataRefreshes = 0;
        Arrays.fill(PROVIDER_LATENCY_BUCKETS, 0L);
    }

    private static void recordProviderLatency(long duration) {
        maximumProviderNanos = Math.max(maximumProviderNanos, duration);
        if (duration > GpomEarlyConfig.pacedChunkSaveBudgetMillis() * 1_000_000L) {
            overBudgetProviderCalls++;
        }
        for (int index = 0; index < LATENCY_BUCKET_UPPER_NANOS.length; index++) {
            if (duration <= LATENCY_BUCKET_UPPER_NANOS[index]) {
                PROVIDER_LATENCY_BUCKETS[index]++;
                return;
            }
        }
    }

    private static void logProgressIfDue() {
        long now = System.nanoTime();
        if (now < nextProgressLogNanos) {
            return;
        }
        GPOM.LOGGER.info("[GPOM Paced Save] Progress pendingProviders={} calls={} providerMillis={} "
                        + "maxCallMillis={} p95UpperMillis={} overBudgetCalls={} metadataRefreshes={} "
                        + "elapsedMillis={}",
                PENDING.size(), providerCalls, nanosToMillis(providerNanos),
                nanosToMillis(maximumProviderNanos), p95UpperBoundMillis(), overBudgetProviderCalls,
                metadataRefreshes, elapsedMillis());
        nextProgressLogNanos = now + PROGRESS_INTERVAL_NANOS;
    }

    private static String p95UpperBoundMillis() {
        if (providerCalls == 0) {
            return "0";
        }
        long target = (providerCalls * 95L + 99L) / 100L;
        long accumulated = 0L;
        for (int index = 0; index < PROVIDER_LATENCY_BUCKETS.length; index++) {
            accumulated += PROVIDER_LATENCY_BUCKETS[index];
            if (accumulated >= target) {
                long upper = LATENCY_BUCKET_UPPER_NANOS[index];
                return upper == Long.MAX_VALUE ? ">1000" : Long.toString(nanosToMillis(upper));
            }
        }
        return ">1000";
    }

    private static long elapsedMillis() {
        return cycleStartedNanos == 0L ? 0L : nanosToMillis(System.nanoTime() - cycleStartedNanos);
    }

    private static long nanosToMillis(long nanos) {
        return nanos / 1_000_000L;
    }
}
