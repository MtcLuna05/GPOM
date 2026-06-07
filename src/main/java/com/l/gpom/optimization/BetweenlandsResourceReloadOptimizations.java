package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class BetweenlandsResourceReloadOptimizations {
    private static final boolean SKIP_IMMEDIATE_SHADER_RELOAD = Boolean.parseBoolean(System.getProperty(
            "gpom.betweenlands.skipImmediateShaderReload",
            "true"
    ));
    private static final boolean DEFER_HERBLORE_RELOAD_TO_INIT = Boolean.parseBoolean(System.getProperty(
            "gpom.betweenlands.deferHerbloreReloadToInit",
            "true"
    ));
    private static final List<DeferredReload> DEFERRED_CLIENT_INIT_RELOADS = new ArrayList<>();
    private static volatile boolean shaderSkipLogged;
    private static volatile boolean herbloreSkipLogged;
    private static volatile boolean fallbackLogged;

    private BetweenlandsResourceReloadOptimizations() {
    }

    public static void registerReloadListener(IReloadableResourceManager manager, IResourceManagerReloadListener listener, String label) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            if (shouldAppendShaderWithoutImmediateReload(listener) && appendReloadListener(manager, listener)) {
                if (!shaderSkipLogged && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    shaderSkipLogged = true;
                    GPOM.LOGGER.info("Deferred Betweenlands ShaderHelper immediate resource reload during PreInit");
                }
                return;
            }
            if (shouldDeferHerbloreReloadToInit(listener, label) && appendReloadListener(manager, listener)) {
                addDeferredClientInitReload(manager, listener, "BL ClientProxy.init deferred reload listener HLEntryRegistry");
                if (!herbloreSkipLogged && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    herbloreSkipLogged = true;
                    GPOM.LOGGER.info("Deferred Betweenlands HLEntryRegistry immediate resource reload from PreInit to Init");
                }
                return;
            }
            registerNow(manager, listener);
        } finally {
            StartupProfiler.endProbeAlways(label, startedAt);
        }
    }

    public static void runDeferredClientInitReloads() {
        List<DeferredReload> reloads;
        synchronized (DEFERRED_CLIENT_INIT_RELOADS) {
            if (DEFERRED_CLIENT_INIT_RELOADS.isEmpty()) {
                return;
            }
            reloads = new ArrayList<>(DEFERRED_CLIENT_INIT_RELOADS);
            DEFERRED_CLIENT_INIT_RELOADS.clear();
        }

        for (DeferredReload reload : reloads) {
            long startedAt = StartupProfiler.beginProbe();
            try {
                invokeResourceReload(reload);
            } catch (Throwable throwable) {
                GPOM.LOGGER.warn("Betweenlands deferred resource reload failed for {}", reload.label, throwable);
                throw new RuntimeException("Failed to run deferred Betweenlands resource reload " + reload.label, throwable);
            } finally {
                StartupProfiler.endProbeAlways(reload.label, startedAt);
            }
        }
    }

    private static void invokeResourceReload(DeferredReload reload) throws ReflectiveOperationException {
        Method reloadMethod = findReloadMethod(reload.listener.getClass());
        if (reloadMethod == null) {
            throw new NoSuchMethodException(reload.listener.getClass().getName() + ".onResourceManagerReload/func_110549_a");
        }
        reloadMethod.invoke(reload.listener, reload.manager);
    }

    private static boolean shouldAppendShaderWithoutImmediateReload(IResourceManagerReloadListener listener) {
        return SKIP_IMMEDIATE_SHADER_RELOAD
                && listener != null
                && TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.render.shader.ShaderHelper")
                && "thebetweenlands.client.render.shader.ShaderHelper".equals(listener.getClass().getName());
    }

    private static boolean shouldDeferHerbloreReloadToInit(IResourceManagerReloadListener listener, String label) {
        return DEFER_HERBLORE_RELOAD_TO_INIT
                && listener != null
                && "BL ClientProxy.preInit reload listener HLEntryRegistry".equals(label)
                && TargetedModVersions.isBetweenlandsClass("thebetweenlands.common.herblore.book.HLEntryRegistry");
    }

    private static void addDeferredClientInitReload(IResourceManager manager, IResourceManagerReloadListener listener, String label) {
        synchronized (DEFERRED_CLIENT_INIT_RELOADS) {
            DEFERRED_CLIENT_INIT_RELOADS.add(new DeferredReload(manager, listener, label));
        }
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
                GPOM.LOGGER.warn("Betweenlands ShaderHelper reload listener append failed; using stock registration", throwable);
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
                GPOM.LOGGER.warn("Betweenlands reload listener registration failed", throwable);
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

    private static Method findReloadMethod(Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            for (String name : new String[] {"onResourceManagerReload", "func_110549_a"}) {
                try {
                    Method method = current.getDeclaredMethod(name, IResourceManager.class);
                    method.setAccessible(true);
                    return method;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static final class DeferredReload {
        private final IResourceManager manager;
        private final IResourceManagerReloadListener listener;
        private final String label;

        private DeferredReload(IResourceManager manager, IResourceManagerReloadListener listener, String label) {
            this.manager = manager;
            this.listener = listener;
            this.label = label;
        }
    }
}
