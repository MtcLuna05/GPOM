package com.l.gpom.util;

import com.l.gpom.config.GpomEarlyConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

public final class EarlySplashBridge {
    private static final Logger LOGGER = LogManager.getLogger("General Purpose Optimization Mod");
    private static final String EARLY_SPLASH_WINDOW = "com.l.gpom.client.EarlySplashWindow";
    private static volatile Method startIfEnabledMethod;
    private static volatile Method setStatusMethod;
    private static volatile Method setBootProgressMethod;
    private static volatile Method setPhaseProgressMethod;
    private static volatile Method closeMethod;

    private EarlySplashBridge() {
    }

    public static void startIfEnabled() {
        if (!GpomSide.isClientLaunch()) {
            return;
        }
        invoke(startIfEnabledMethod, "startIfEnabled", new Class<?>[0]);
    }

    public static void setStatus(String status) {
        if (!GpomSide.isClientLaunch()) {
            return;
        }
        invoke(setStatusMethod, "setStatus", new Class<?>[] {String.class}, status);
    }

    public static void setBootProgress(String stage, int done, int total) {
        if (!GpomSide.isClientLaunch()) {
            return;
        }
        invoke(setBootProgressMethod, "setBootProgress", new Class<?>[] {String.class, int.class, int.class}, stage, done, total);
    }

    public static void setPhaseProgress(String phase, int done, int total) {
        if (!GpomSide.isClientLaunch()) {
            return;
        }
        invoke(setPhaseProgressMethod, "setPhaseProgress", new Class<?>[] {String.class, int.class, int.class}, phase, done, total);
    }

    public static void close(String reason) {
        if (!GpomSide.isClientLaunch()) {
            return;
        }
        invoke(closeMethod, "close", new Class<?>[] {String.class}, reason);
    }

    private static void invoke(Method cached, String name, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = cached;
            if (method == null) {
                method = Class.forName(EARLY_SPLASH_WINDOW, true, EarlySplashBridge.class.getClassLoader())
                        .getMethod(name, parameterTypes);
                method.setAccessible(true);
                cache(name, method);
            }
            method.invoke(null, args);
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.gpomLoggingEnabled()) {
                LOGGER.warn("[EarlySplash] Client-only bridge call {} failed", name, throwable);
            }
        }
    }

    private static void cache(String name, Method method) {
        if ("startIfEnabled".equals(name)) {
            startIfEnabledMethod = method;
        } else if ("setStatus".equals(name)) {
            setStatusMethod = method;
        } else if ("setBootProgress".equals(name)) {
            setBootProgressMethod = method;
        } else if ("setPhaseProgress".equals(name)) {
            setPhaseProgressMethod = method;
        } else if ("close".equals(name)) {
            closeMethod = method;
        }
    }
}
