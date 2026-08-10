package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

public final class EntityActivationController {
    private static final long TELEMETRY_INTERVAL_NANOS = 10_000_000_000L;
    private static long nextTelemetryNanos = System.nanoTime() + TELEMETRY_INTERVAL_NANOS;
    private static long evaluated;
    private static long protectedEntities;
    private static long nearbyEntities;
    private static long fullAiTicks;
    private static long skippedAiTicks;

    private EntityActivationController() {
    }

    public static boolean shouldSkipAi(EntityLiving entity) {
        if (entity == null || entity instanceof EntityTameable || entity instanceof EntityVillager) {
            recordProtected();
            return false;
        }
        World world = MinecraftMappingCompat.entityWorld(entity);
        if (world == null || MinecraftMappingCompat.worldIsRemote(world)
                || MinecraftMappingCompat.entityHasCustomName(entity)
                || MinecraftMappingCompat.entityIsRiding(entity)
                || MinecraftMappingCompat.entityIsBeingRidden(entity)
                || MinecraftMappingCompat.entityAttackTarget(entity) != null) {
            recordProtected();
            return false;
        }
        ResourceLocation id = MinecraftMappingCompat.entityRegistryName(entity);
        if (id != null && GpomEarlyConfig.entityActivationDenylist().contains(id.toString().toLowerCase(java.util.Locale.ROOT))) {
            recordProtected();
            return false;
        }
        double range = GpomEarlyConfig.entityActivationRange();
        if (MinecraftMappingCompat.worldClosestPlayer(world, entity, range) != null) {
            evaluated++;
            nearbyEntities++;
            fullAiTicks++;
            logTelemetryIfDue();
            return false;
        }
        int interval = GpomEarlyConfig.entityActivationIntervalTicks();
        int phase = Math.floorMod(System.identityHashCode(entity), interval);
        boolean skip = Math.floorMod(MinecraftMappingCompat.entityTicksExisted(entity), interval) != phase;
        evaluated++;
        if (skip) {
            skippedAiTicks++;
        } else {
            fullAiTicks++;
        }
        logTelemetryIfDue();
        return skip;
    }

    private static void recordProtected() {
        evaluated++;
        protectedEntities++;
        fullAiTicks++;
        logTelemetryIfDue();
    }

    private static void logTelemetryIfDue() {
        long now = System.nanoTime();
        if (now < nextTelemetryNanos) {
            return;
        }
        long total = evaluated;
        double skipPercent = total == 0L ? 0.0D : skippedAiTicks * 100.0D / total;
        GPOM.LOGGER.info(
                "[GPOM Entity Activation] evaluated={} skipped={} skipPercent={} fullAi={} nearby={} protected={}",
                total, skippedAiTicks, String.format(java.util.Locale.ROOT, "%.1f", skipPercent),
                fullAiTicks, nearbyEntities, protectedEntities);
        evaluated = 0L;
        protectedEntities = 0L;
        nearbyEntities = 0L;
        fullAiTicks = 0L;
        skippedAiTicks = 0L;
        nextTelemetryNanos = now + TELEMETRY_INTERVAL_NANOS;
    }
}
