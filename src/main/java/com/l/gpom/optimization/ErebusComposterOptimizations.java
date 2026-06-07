package com.l.gpom.optimization;

import com.l.gpom.GPOM;

import java.lang.reflect.Method;

public final class ErebusComposterOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.erebus.deferComposterRegistry", "true"));
    private static final ThreadLocal<Boolean> INITIALIZING = new ThreadLocal<Boolean>();
    private static volatile boolean deferred;
    private static volatile boolean initialized;

    private ErebusComposterOptimizations() {
    }

    public static boolean shouldSkipInit() {
        if (!ENABLED || Boolean.TRUE.equals(INITIALIZING.get())) {
            return false;
        }
        if (initialized) {
            return true;
        }
        if (!deferred) {
            deferred = true;
            return true;
        }
        return false;
    }

    public static void markInitialized() {
        initialized = true;
    }

    public static void ensureInitialized() {
        if (!ENABLED || initialized || !deferred) {
            return;
        }
        synchronized (ErebusComposterOptimizations.class) {
            if (initialized || !deferred) {
                return;
            }
            INITIALIZING.set(Boolean.TRUE);
            try {
                ClassLoader loader = ErebusComposterOptimizations.class.getClassLoader();
                Class<?> registry = Class.forName("erebus.recipes.ComposterRegistry", false, loader);
                Method init = registry.getDeclaredMethod("init");
                init.invoke(null);
                initialized = true;
            } catch (Throwable throwable) {
                initialized = true;
                GPOM.LOGGER.warn("[Erebus Optimizations] Deferred composter registry initialization failed", throwable);
            } finally {
                INITIALIZING.remove();
            }
        }
    }
}
