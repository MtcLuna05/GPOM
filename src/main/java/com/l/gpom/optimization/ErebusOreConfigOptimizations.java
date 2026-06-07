package com.l.gpom.optimization;

import com.l.gpom.GPOM;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ErebusOreConfigOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.erebus.deferOreConfigs", "true"));
    private static final ThreadLocal<Boolean> INITIALIZING = new ThreadLocal<Boolean>();
    private static volatile boolean deferred;
    private static volatile boolean initialized;

    private ErebusOreConfigOptimizations() {
    }

    public static boolean shouldSkipInit() {
        if (!ENABLED || Boolean.TRUE.equals(INITIALIZING.get())) {
            return false;
        }
        if (!initialized && !deferred) {
            deferred = true;
            return true;
        }
        return false;
    }

    public static void markInitialized() {
        initialized = true;
    }

    public static void ensureInitialized() {
        if (!ENABLED || initialized || !deferred || Boolean.TRUE.equals(INITIALIZING.get())) {
            return;
        }
        synchronized (ErebusOreConfigOptimizations.class) {
            if (initialized || !deferred) {
                return;
            }
            INITIALIZING.set(Boolean.TRUE);
            try {
                ClassLoader loader = ErebusOreConfigOptimizations.class.getClassLoader();
                Class<?> configHandler = Class.forName("erebus.core.handler.configs.ConfigHandler", false, loader);
                Field instanceField = configHandler.getField("INSTANCE");
                Object instance = instanceField.get(null);
                Method init = configHandler.getDeclaredMethod("initOreConfigs");
                init.invoke(instance);
                initialized = true;
            } catch (Throwable throwable) {
                initialized = true;
                GPOM.LOGGER.warn("[Erebus Optimizations] Deferred ore config initialization failed", throwable);
            } finally {
                INITIALIZING.remove();
            }
        }
    }
}
