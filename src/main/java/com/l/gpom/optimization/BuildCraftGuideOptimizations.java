package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

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
        try {
            ResourceReloadHelper.registerReloadListener(manager, listener);
        } catch (Throwable throwable) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                GPOM.LOGGER.warn("BuildCraft guide reload listener registration failed", throwable);
            }
        }
    }
}
