package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class ImmersiveEngineeringManualOptimizations {
    private static final boolean DEFER_MANUAL_INDEX = Boolean.parseBoolean(System.getProperty("gpom.immersiveengineering.deferManualIndex", "true"));
    private static final Object LOCK = new Object();
    private static final Set<Object> DEFERRED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> INDEXED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static volatile boolean deferLogged;
    private static volatile boolean failureLogged;

    private ImmersiveEngineeringManualOptimizations() {
    }

    public static void deferManualIndex(Object manual) {
        if (!DEFER_MANUAL_INDEX
                || manual == null
                || !TargetedModVersions.isImmersiveEngineeringClass("blusunrize.lib.manual.ManualInstance")) {
            invokeIndexRecipes(manual);
            return;
        }
        synchronized (LOCK) {
            DEFERRED.add(manual);
        }
        if (!deferLogged) {
            deferLogged = true;
            GPOM.LOGGER.info("Deferred Immersive Engineering manual recipe index until manual use");
        }
    }

    public static void ensureManualIndex(Object manual) {
        if (!DEFER_MANUAL_INDEX || manual == null) {
            return;
        }
        synchronized (LOCK) {
            if (INDEXED.contains(manual) || !DEFERRED.contains(manual)) {
                return;
            }
        }

        long startedAt = StartupProfiler.beginProbe();
        boolean indexed = invokeIndexRecipes(manual);
        StartupProfiler.endProbeAlways("IE ManualInstance deferred index realization", startedAt);
        synchronized (LOCK) {
            DEFERRED.remove(manual);
            if (indexed) {
                INDEXED.add(manual);
            }
        }
    }

    private static boolean invokeIndexRecipes(Object manual) {
        if (manual == null) {
            return false;
        }
        try {
            Method method = manual.getClass().getMethod("indexRecipes");
            method.setAccessible(true);
            method.invoke(manual);
            return true;
        } catch (Throwable throwable) {
            if (!failureLogged) {
                failureLogged = true;
                GPOM.LOGGER.warn("Immersive Engineering deferred manual recipe index failed", throwable);
            }
            return false;
        }
    }
}
