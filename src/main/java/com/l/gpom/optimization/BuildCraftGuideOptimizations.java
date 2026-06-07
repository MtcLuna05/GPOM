package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import java.lang.reflect.Method;

public final class BuildCraftGuideOptimizations {
    private static final boolean DEFER_GUIDE_RELOAD = Boolean.parseBoolean(System.getProperty("gpom.buildcraft.deferGuideReload", "true"));
    private static final Object LOCK = new Object();

    private static volatile IReloadableResourceManager deferredManager;
    private static volatile IResourceManagerReloadListener deferredListener;
    private static volatile boolean listenerRegistered;
    private static volatile boolean deferLogged;
    private static volatile boolean fallbackLogged;

    private BuildCraftGuideOptimizations() {
    }

    public static void registerGuideReloadListenerDeferred(IReloadableResourceManager manager, IResourceManagerReloadListener listener) {
        if (!DEFER_GUIDE_RELOAD
                || manager == null
                || listener == null
                || !TargetedModVersions.isBuildCraftCoreClass("buildcraft.lib.client.guide.GuideManager")
                || !"buildcraft.lib.client.guide.GuideManager".equals(listener.getClass().getName())) {
            registerNow(manager, listener);
            return;
        }

        deferredManager = manager;
        deferredListener = listener;
        listenerRegistered = false;
        if (!deferLogged) {
            deferLogged = true;
            GPOM.LOGGER.info("Deferred BuildCraft guide resource reload until the guide is opened");
        }
    }

    public static void ensureGuideReady() {
        if (!DEFER_GUIDE_RELOAD || listenerRegistered || deferredManager == null || deferredListener == null) {
            return;
        }
        synchronized (LOCK) {
            if (listenerRegistered || deferredManager == null || deferredListener == null) {
                return;
            }
            long startedAt = StartupProfiler.beginProbe();
            registerNow(deferredManager, deferredListener);
            listenerRegistered = true;
            deferredManager = null;
            deferredListener = null;
            StartupProfiler.endProbeAlways("BC GuideManager deferred reload realization", startedAt);
        }
    }

    private static void registerNow(IReloadableResourceManager manager, IResourceManagerReloadListener listener) {
        if (manager == null || listener == null) {
            return;
        }
        try {
            Method register = findMethod(manager.getClass(), "registerReloadListener", "func_110542_a");
            if (register == null) {
                throw new NoSuchMethodException(manager.getClass().getName() + ".registerReloadListener");
            }
            register.invoke(manager, listener);
        } catch (Throwable throwable) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                GPOM.LOGGER.warn("BuildCraft guide reload listener registration failed", throwable);
            }
        }
    }

    private static Method findMethod(Class<?> type, String deobfuscatedName, String runtimeName) {
        for (String name : new String[] {deobfuscatedName, runtimeName}) {
            try {
                Method method = type.getMethod(name, IResourceManagerReloadListener.class);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) {
            }
        }
        return null;
    }
}
