package com.luna.gpom.optimization;

import com.luna.gpom.GPOM;
import com.luna.gpom.config.GpomEarlyConfig;
import com.luna.gpom.core.TargetedModVersions;
import com.luna.gpom.profiling.StartupProfiler;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

public final class AstralSorceryResourceReloadOptimizations {
    private static final boolean DEFER_ASSET_LIBRARY_RELOAD = Boolean.parseBoolean(System.getProperty(
            "gpom.astralSorcery.deferAssetLibraryReload",
            "true"
    ));
    private static volatile boolean deferLogged;
    private static volatile boolean fallbackLogged;

    private AstralSorceryResourceReloadOptimizations() {
    }

    public static void registerAssetLibraryReloadListener(IReloadableResourceManager manager, IResourceManagerReloadListener listener) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            if (shouldDefer(listener) && appendReloadListener(manager, listener)) {
                if (!deferLogged && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    deferLogged = true;
                    GPOM.LOGGER.info("Deferred Astral Sorcery AssetLibrary immediate resource reload during PreInit");
                }
                return;
            }
            registerNow(manager, listener);
        } finally {
            StartupProfiler.endProbeAlways("ASTRAL ClientProxy.preInit register AssetLibrary reload listener", startedAt);
        }
    }

    private static boolean shouldDefer(IResourceManagerReloadListener listener) {
        return DEFER_ASSET_LIBRARY_RELOAD
                && listener != null
                && TargetedModVersions.isAstralSorceryClass("hellfirepvp.astralsorcery.client.util.resource.AssetLibrary")
                && "hellfirepvp.astralsorcery.client.util.resource.AssetLibrary".equals(listener.getClass().getName());
    }

    @SuppressWarnings("unchecked")
    private static boolean appendReloadListener(IReloadableResourceManager manager, IResourceManagerReloadListener listener) {
        try {
            return ResourceReloadHelper.appendReloadListener(manager, listener);
        } catch (Throwable throwable) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                GPOM.LOGGER.warn("Astral Sorcery AssetLibrary reload listener append failed; using stock registration", throwable);
            }
            return false;
        }
    }

    private static void registerNow(IReloadableResourceManager manager, IResourceManagerReloadListener listener) {
        try {
            ResourceReloadHelper.registerReloadListener(manager, listener);
        } catch (Throwable throwable) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                GPOM.LOGGER.warn("Astral Sorcery AssetLibrary reload listener registration failed", throwable);
            }
        }
    }
}
