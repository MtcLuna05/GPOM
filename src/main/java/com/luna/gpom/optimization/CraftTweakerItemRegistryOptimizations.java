package com.luna.gpom.optimization;

import com.luna.gpom.GPOM;
import com.luna.gpom.config.GpomEarlyConfig;
import com.luna.gpom.profiling.StartupProfiler;

import java.lang.reflect.Method;

public final class CraftTweakerItemRegistryOptimizations {
    private static volatile boolean itemListDeferred;
    private static volatile Method createItemListMethod;

    private CraftTweakerItemRegistryOptimizations() {
    }

    public static void deferItemListBuild() {
        if (!GpomEarlyConfig.craftTweakerLazyItemListEnabled()) {
            buildItemListNow("CT fallback MCItemUtils.createItemList");
            return;
        }
        itemListDeferred = true;
        StartupProfiler.endProbe("POSTPRE CT deferred MCItemUtils.createItemList", StartupProfiler.beginProbe());
    }

    public static void ensureItemListBuilt() {
        if (!itemListDeferred) {
            return;
        }
        synchronized (CraftTweakerItemRegistryOptimizations.class) {
            if (!itemListDeferred) {
                return;
            }
            long startedAt = StartupProfiler.beginProbe();
            try {
                itemListDeferred = false;
                createItemListMethod().invoke(null);
                GPOM.LOGGER.info("[CraftTweaker Optimizations] Lazily built MCItemUtils item list");
            } catch (Throwable throwable) {
                itemListDeferred = false;
                GPOM.LOGGER.warn("[CraftTweaker Optimizations] Lazy MCItemUtils item-list build failed", throwable);
            } finally {
                StartupProfiler.endProbe("CT lazy MCItemUtils.createItemList", startedAt);
            }
        }
    }

    private static void buildItemListNow(String probeName) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            createItemListMethod().invoke(null);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[CraftTweaker Optimizations] MCItemUtils item-list build failed", throwable);
        } finally {
            StartupProfiler.endProbe(probeName, startedAt);
        }
    }

    private static Method createItemListMethod() throws ReflectiveOperationException {
        Method method = createItemListMethod;
        if (method == null) {
            Class<?> type = Class.forName(
                    "crafttweaker.mc1120.item.MCItemUtils",
                    false,
                    CraftTweakerItemRegistryOptimizations.class.getClassLoader()
            );
            method = type.getMethod("createItemList");
            method.setAccessible(true);
            createItemListMethod = method;
        }
        return method;
    }
}
