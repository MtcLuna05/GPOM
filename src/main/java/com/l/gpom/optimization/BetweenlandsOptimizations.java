package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class BetweenlandsOptimizations {
    private static final String MOD_ID = "thebetweenlands";
    private static final int FIELD_ACCESS_SUMMARY_LIMIT = 20;
    private static final ConcurrentHashMap<String, LongAdder> BLOCK_FIELD_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> BLOCK_FIELD_CONTEXT_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> LAZY_BLOCK_FIELD_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> DIRECT_BLOCK_REGISTRATION_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> DIRECT_BLOCK_NULL_COUNTS = new ConcurrentHashMap<>();
    private static volatile int expectedDirectBlockRegistrations;
    private static Object betweenlandsInstance;

    private BetweenlandsOptimizations() {
    }

    public static void registerModEntity(Class<? extends Entity> entityClass, String name, int id, int range, int updateFrequency, boolean sendVelocityUpdates) {
        EntityRegistry.registerModEntity(
                new ResourceLocation(MOD_ID, name),
                entityClass,
                MOD_ID + '.' + name,
                id,
                betweenlandsInstance(),
                range,
                updateFrequency,
                sendVelocityUpdates
        );
    }

    public static void registerEgg(String name, int primaryColor, int secondaryColor) {
        EntityRegistry.registerEgg(new ResourceLocation(MOD_ID, name), primaryColor, secondaryColor);
    }

    public static void recordBlockRegistryFieldAccess(String context, String fieldName) {
        if (context == null || fieldName == null) {
            return;
        }
        BLOCK_FIELD_COUNTS.computeIfAbsent(fieldName, ignored -> new LongAdder()).increment();
        BLOCK_FIELD_CONTEXT_COUNTS.computeIfAbsent(context + " -> " + fieldName, ignored -> new LongAdder()).increment();
    }

    public static Block lazyBlockField(String fieldName) {
        LAZY_BLOCK_FIELD_COUNTS.computeIfAbsent(fieldName, ignored -> new LongAdder()).increment();
        try {
            Class<?> registry = Class.forName("thebetweenlands.common.registries.BlockRegistry");
            try {
                Method lazyMethod = registry.getMethod("gpom$lazyBlock$" + fieldName);
                Object value = lazyMethod.invoke(null);
                if (value instanceof Block) {
                    return (Block) value;
                }
            } catch (NoSuchMethodException ignored) {
                // Fields that remain eager do not have generated lazy constructors.
            }

            Field field = registry.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof Block) {
                return (Block) value;
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to resolve Betweenlands BlockRegistry." + fieldName, throwable);
        }

        throw new IllegalStateException("Betweenlands BlockRegistry." + fieldName + " was read before it was assigned");
    }

    public static void beginBlockRegistryDirectPreInit(int expectedRegistrations) {
        expectedDirectBlockRegistrations = expectedRegistrations;
        DIRECT_BLOCK_REGISTRATION_COUNTS.clear();
        DIRECT_BLOCK_NULL_COUNTS.clear();
    }

    public static Block recordBlockRegistryDirectPreInit(String fieldName, String registryName, Block block) {
        String key = fieldName + " -> thebetweenlands:" + registryName;
        DIRECT_BLOCK_REGISTRATION_COUNTS.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        if (block == null) {
            DIRECT_BLOCK_NULL_COUNTS.computeIfAbsent(key, ignored -> new LongAdder()).increment();
            GPOM.LOGGER.error("[StartupProfiler] [Probe] BL direct BlockRegistry preInit resolved null for {}", key);
        }
        return block;
    }

    public static void logBlockRegistryDirectPreInitSummary() {
        int total = sum(DIRECT_BLOCK_REGISTRATION_COUNTS);
        int nulls = sum(DIRECT_BLOCK_NULL_COUNTS);
        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL direct BlockRegistry preInit generated registrations={} expected={} nulls={}",
                total,
                expectedDirectBlockRegistrations,
                nulls
        );
        if (nulls > 0) {
            logTop("direct-registration nulls", DIRECT_BLOCK_NULL_COUNTS);
        }
    }

    public static void logBlockRegistryFieldAccessSummary() {
        int total = sum(BLOCK_FIELD_COUNTS);
        if (total == 0) {
            GPOM.LOGGER.info("[StartupProfiler] [Probe] BL lazy-field feasibility saw no BlockRegistry field reads during pre-init");
            return;
        }

        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL lazy-field feasibility BlockRegistry field reads total={} uniqueFields={} uniqueContexts={}",
                total,
                BLOCK_FIELD_COUNTS.size(),
                BLOCK_FIELD_CONTEXT_COUNTS.size()
        );
        logTop("fields", BLOCK_FIELD_COUNTS);
        logTop("contexts", BLOCK_FIELD_CONTEXT_COUNTS);
        logLazyBlockAccessorSummary();
    }

    private static Object betweenlandsInstance() {
        Object instance = betweenlandsInstance;
        if (instance != null) {
            return instance;
        }

        try {
            instance = Class.forName("thebetweenlands.common.TheBetweenlands").getField("instance").get(null);
            betweenlandsInstance = instance;
            return instance;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int sum(ConcurrentHashMap<String, LongAdder> counters) {
        int total = 0;
        for (LongAdder counter : counters.values()) {
            total += counter.intValue();
        }
        return total;
    }

    private static void logTop(String label, ConcurrentHashMap<String, LongAdder> counters) {
        List<Map.Entry<String, LongAdder>> entries = new ArrayList<>(counters.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, LongAdder> entry) -> entry.getValue().intValue()).reversed());
        int limit = Math.min(FIELD_ACCESS_SUMMARY_LIMIT, entries.size());
        for (int index = 0; index < limit; index++) {
            Map.Entry<String, LongAdder> entry = entries.get(index);
            GPOM.LOGGER.info(
                    "[StartupProfiler] [Probe] BL lazy-field feasibility top {} #{} count={} - {}",
                    label,
                    index + 1,
                    entry.getValue().intValue(),
                    entry.getKey()
            );
        }
    }

    private static void logLazyBlockAccessorSummary() {
        int total = sum(LAZY_BLOCK_FIELD_COUNTS);
        if (total == 0) {
            GPOM.LOGGER.info("[StartupProfiler] [Probe] BL lazy block accessors were not used during pre-init");
            return;
        }

        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL lazy block accessors resolved total={} uniqueFields={}",
                total,
                LAZY_BLOCK_FIELD_COUNTS.size()
        );
        logTop("lazy-accessor fields", LAZY_BLOCK_FIELD_COUNTS);
    }
}
