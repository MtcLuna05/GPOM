package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class ExistingChunkPrefetchController {
    private static final long STALE_NANOS = 30_000_000_000L;
    private static final Map<PrefetchKey, Long> OUTSTANDING = new ConcurrentHashMap<>();
    private static final long TELEMETRY_INTERVAL_NANOS = 10_000_000_000L;
    private static final LongAdder MOVING_PLAYERS = new LongAdder();
    private static final LongAdder ALREADY_LOADED = new LongAdder();
    private static final LongAdder ABSENT_ON_DISK = new LongAdder();
    private static final LongAdder DUPLICATES = new LongAdder();
    private static final LongAdder SUBMITTED = new LongAdder();
    private static final LongAdder ASYNC_COMPLETIONS = new LongAdder();
    private static final LongAdder SYNCHRONOUS_COMPLETIONS = new LongAdder();
    private static final LongAdder EXPIRED = new LongAdder();
    private static final LongAdder CAPACITY_LIMITS = new LongAdder();
    private static long nextTelemetryNanos = System.nanoTime() + TELEMETRY_INTERVAL_NANOS;
    private static int ticks;

    private ExistingChunkPrefetchController() {
    }

    public static void tick(MinecraftServer server) {
        int interval = GpomEarlyConfig.existingChunkPrefetchIntervalTicks();
        if (++ticks % interval != 0) {
            return;
        }
        expireStale();
        logTelemetryIfDue();
        int maximum = GpomEarlyConfig.existingChunkPrefetchMaxOutstanding();
        for (EntityPlayerMP player : MinecraftMappingCompat.minecraftServerPlayers(server)) {
            if (OUTSTANDING.size() >= maximum) {
                CAPACITY_LIMITS.increment();
                return;
            }
            prefetchAhead(player);
        }
    }

    private static void prefetchAhead(EntityPlayerMP player) {
        double motionX = MinecraftMappingCompat.entityMotionX(player);
        double motionZ = MinecraftMappingCompat.entityMotionZ(player);
        double speed = Math.sqrt(motionX * motionX + motionZ * motionZ);
        if (speed < GpomEarlyConfig.existingChunkPrefetchMinimumSpeed()) {
            return;
        }
        MOVING_PLAYERS.increment();
        World world = MinecraftMappingCompat.entityWorld(player);
        if (!(world instanceof WorldServer)) {
            return;
        }
        ChunkProviderServer provider = MinecraftMappingCompat.worldServerChunkProvider((WorldServer) world);
        if (provider == null) {
            return;
        }
        double blocksAhead = GpomEarlyConfig.existingChunkPrefetchDistanceChunks() * 16.0D;
        int chunkX = floorChunk(MinecraftMappingCompat.entityPosX(player) + motionX / speed * blocksAhead);
        int chunkZ = floorChunk(MinecraftMappingCompat.entityPosZ(player) + motionZ / speed * blocksAhead);
        if (MinecraftMappingCompat.chunkProviderLoadedChunk(provider, chunkX, chunkZ) != null) {
            ALREADY_LOADED.increment();
            return;
        }
        if (!MinecraftMappingCompat.chunkProviderChunkExistsOnDisk(provider, chunkX, chunkZ)) {
            ABSENT_ON_DISK.increment();
            return;
        }
        PrefetchKey key = new PrefetchKey(provider, chunkX, chunkZ);
        if (OUTSTANDING.putIfAbsent(key, System.nanoTime()) != null) {
            DUPLICATES.increment();
            return;
        }
        SUBMITTED.increment();
        Runnable completion = () -> {
            if (OUTSTANDING.remove(key) != null) {
                ASYNC_COMPLETIONS.increment();
            }
        };
        if (MinecraftMappingCompat.chunkProviderLoadChunkAsync(provider, chunkX, chunkZ, completion) != null) {
            if (OUTSTANDING.remove(key) != null) {
                SYNCHRONOUS_COMPLETIONS.increment();
            }
        }
    }

    private static int floorChunk(double blockCoordinate) {
        return (int) Math.floor(blockCoordinate / 16.0D);
    }

    private static void expireStale() {
        long cutoff = System.nanoTime() - STALE_NANOS;
        for (Map.Entry<PrefetchKey, Long> entry : OUTSTANDING.entrySet()) {
            if (entry.getValue() < cutoff) {
                if (OUTSTANDING.remove(entry.getKey(), entry.getValue())) {
                    EXPIRED.increment();
                }
            }
        }
    }

    private static void logTelemetryIfDue() {
        long now = System.nanoTime();
        if (now < nextTelemetryNanos) {
            return;
        }
        GPOM.LOGGER.info(
                "[GPOM Existing Chunk Prefetch] movingPlayers={} submitted={} asyncCompleted={} "
                        + "syncCompleted={} alreadyLoaded={} absentOnDisk={} duplicates={} expired={} "
                        + "capacityLimits={} outstanding={}",
                MOVING_PLAYERS.sumThenReset(), SUBMITTED.sumThenReset(), ASYNC_COMPLETIONS.sumThenReset(),
                SYNCHRONOUS_COMPLETIONS.sumThenReset(), ALREADY_LOADED.sumThenReset(),
                ABSENT_ON_DISK.sumThenReset(), DUPLICATES.sumThenReset(), EXPIRED.sumThenReset(),
                CAPACITY_LIMITS.sumThenReset(), OUTSTANDING.size());
        nextTelemetryNanos = now + TELEMETRY_INTERVAL_NANOS;
    }

    private static final class PrefetchKey {
        private final ChunkProviderServer provider;
        private final int chunkX;
        private final int chunkZ;

        private PrefetchKey(ChunkProviderServer provider, int chunkX, int chunkZ) {
            this.provider = provider;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PrefetchKey)) {
                return false;
            }
            PrefetchKey key = (PrefetchKey) other;
            return provider == key.provider && chunkX == key.chunkX && chunkZ == key.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(provider);
            result = 31 * result + chunkX;
            return 31 * result + chunkZ;
        }
    }
}
