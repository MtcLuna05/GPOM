package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

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
        if (manager == null || listener == null) {
            return false;
        }
        try {
            Field field = findField(manager.getClass(), "reloadListeners", "field_110546_b");
            if (field == null) {
                return false;
            }
            field.setAccessible(true);
            Object value = field.get(manager);
            if (!(value instanceof List)) {
                return false;
            }
            List<IResourceManagerReloadListener> listeners = (List<IResourceManagerReloadListener>) value;
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
            return true;
        } catch (Throwable throwable) {
            if (!fallbackLogged) {
                fallbackLogged = true;
                GPOM.LOGGER.warn("Astral Sorcery AssetLibrary reload listener append failed; using stock registration", throwable);
            }
            return false;
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
                GPOM.LOGGER.warn("Astral Sorcery AssetLibrary reload listener registration failed", throwable);
            }
        }
    }

    private static Field findField(Class<?> type, String deobfuscatedName, String runtimeName) {
        Class<?> current = type;
        while (current != null) {
            for (String name : new String[] {deobfuscatedName, runtimeName}) {
                try {
                    return current.getDeclaredField(name);
                } catch (ReflectiveOperationException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
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
