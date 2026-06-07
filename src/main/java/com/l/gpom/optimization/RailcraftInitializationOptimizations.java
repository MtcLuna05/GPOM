package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class RailcraftInitializationOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.railcraftLazyItemConditions", "false"));
    private static final boolean DEFER_MODULE_IC2_CONTAINERS = Boolean.parseBoolean(System.getProperty("gpom.railcraft.deferModuleIC2Containers", "false"));
    private static final boolean DEFER_MODULE_CONTAINERS = Boolean.parseBoolean(System.getProperty(
            "gpom.railcraft.deferModuleContainers",
            System.getProperty("gpom.railcraft.deferModuleIC2Containers", "false")))
            || Boolean.parseBoolean(System.getProperty("gpom.railcraft.deferSelectedModuleContainers", "false"));
    private static final ConcurrentHashMap<String, Boolean> LOGGED = new ConcurrentHashMap<>();
    private static final Set<Object> DEFERRED_MODULE_IC2_BASE = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static final Set<Object> DEFERRED_MODULE_IC2_CLASSIC = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static final Set<Object> DEFERRED_MODULE_CONTAINERS = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
    private static volatile boolean moduleIC2Realized;

    private RailcraftInitializationOptimizations() {
    }

    public static Object addLazyContainerCondition(Object conditional, String containerClassName, String fieldName) {
        if (!ENABLED || conditional == null || containerClassName == null || fieldName == null
                || !TargetedModVersions.isRailcraftClass(containerClassName)) {
            return addEagerContainerCondition(conditional, containerClassName, fieldName);
        }

        try {
            Method add = conditional.getClass().getMethod("add", BooleanSupplier.class, Supplier.class);
            BooleanSupplier predicate = new BooleanSupplier() {
                @Override
                public boolean getAsBoolean() {
                    Object container = resolveContainer(containerClassName, fieldName);
                    if (container == null) {
                        return false;
                    }
                    return isObjectDefined(container) && isEnabled(container);
                }
            };
            Supplier<String> failure = new Supplier<String>() {
                @Override
                public String get() {
                    return containerClassName + '.' + fieldName + " is not enabled";
                }
            };
            return add.invoke(conditional, predicate, failure);
        } catch (Throwable throwable) {
            logOnce("lazy-condition-" + containerClassName + '.' + fieldName,
                    "Railcraft lazy item condition failed for " + containerClassName + '.' + fieldName + "; falling back to eager condition", throwable);
            return addEagerContainerCondition(conditional, containerClassName, fieldName);
        }
    }

    public static void deferModuleIC2Containers(Object module, String variant) {
        if (!DEFER_MODULE_IC2_CONTAINERS || module == null || variant == null
                || !TargetedModVersions.isRailcraftClass("mods.railcraft.common.modules.ModuleIC2")) {
            return;
        }
        synchronized (RailcraftInitializationOptimizations.class) {
            if ("classic".equals(variant)) {
                DEFERRED_MODULE_IC2_CLASSIC.add(module);
            } else {
                DEFERRED_MODULE_IC2_BASE.add(module);
            }
            moduleIC2Realized = false;
        }
    }

    public static void deferModuleContainers(Object module, String encodedDescriptors) {
        if (module == null || encodedDescriptors == null || encodedDescriptors.isEmpty()
                || !TargetedModVersions.isRailcraftClass(module.getClass().getName())) {
            return;
        }
        String[][] descriptors = decodeDescriptors(encodedDescriptors);
        if (!DEFER_MODULE_CONTAINERS) {
            addContainersDirect(module, descriptors);
            return;
        }
        long startedAt = com.l.gpom.profiling.StartupProfiler.beginProbe();
        try {
            addContainerProxies(module, descriptors);
            synchronized (RailcraftInitializationOptimizations.class) {
                DEFERRED_MODULE_CONTAINERS.add(module);
            }
        } finally {
            com.l.gpom.profiling.StartupProfiler.endProbe("RC deferred module container proxy insertion", startedAt);
        }
    }

    public static void realizeDeferredModuleContainersForStage(Object module, boolean enabled, Object stage) {
        if (!DEFER_MODULE_CONTAINERS || module == null || !enabled || stage == null || !"CONSTRUCTION".equals(String.valueOf(stage))
                || !TargetedModVersions.isRailcraftClass(module.getClass().getName())) {
            return;
        }
        synchronized (RailcraftInitializationOptimizations.class) {
            if (!DEFERRED_MODULE_CONTAINERS.remove(module)) {
                return;
            }
        }
        long startedAt = com.l.gpom.profiling.StartupProfiler.beginProbe();
        try {
            realizeModuleContainerProxies(module);
        } finally {
            com.l.gpom.profiling.StartupProfiler.endProbe("RC deferred module container realization", startedAt);
        }
    }

    public static void realizeDeferredModuleIC2Containers(Object stage) {
        if (stage == null || !"PRE_INIT".equals(String.valueOf(stage)) || !DEFER_MODULE_IC2_CONTAINERS || moduleIC2Realized
                || !TargetedModVersions.isRailcraftClass("mods.railcraft.common.modules.ModuleIC2")) {
            return;
        }
        synchronized (RailcraftInitializationOptimizations.class) {
            if (moduleIC2Realized) {
                return;
            }
            long startedAt = com.l.gpom.profiling.StartupProfiler.beginProbe();
            for (Object module : DEFERRED_MODULE_IC2_BASE) {
                addContainersDirect(module, new String[][] {
                        {"mods.railcraft.common.carts.RailcraftCarts", "ENERGY_BATBOX"},
                        {"mods.railcraft.common.carts.RailcraftCarts", "ENERGY_MFE"},
                        {"mods.railcraft.common.carts.RailcraftCarts", "ENERGY_CESU"},
                        {"mods.railcraft.common.blocks.RailcraftBlocks", "MANIPULATOR"}
                });
            }
            for (Object module : DEFERRED_MODULE_IC2_CLASSIC) {
                addContainersDirect(module, new String[][] {
                        {"mods.railcraft.common.carts.RailcraftCarts", "ENERGY_MFSU"}
                });
            }
            DEFERRED_MODULE_IC2_BASE.clear();
            DEFERRED_MODULE_IC2_CLASSIC.clear();
            moduleIC2Realized = true;
            com.l.gpom.profiling.StartupProfiler.endProbeAlways("RC ModuleIC2 deferred container realization", startedAt);
        }
    }

    public static void beginModuleStageProbe(Object stage, Object module, boolean enabled) {
        com.l.gpom.profiling.StartupProfiler.beginNamedProbe(moduleStageLabel(stage, module, enabled));
    }

    public static void endModuleStageProbe(Object stage, Object module, boolean enabled) {
        com.l.gpom.profiling.StartupProfiler.endNamedProbe(moduleStageLabel(stage, module, enabled));
    }

    private static Object addEagerContainerCondition(Object conditional, String containerClassName, String fieldName) {
        if (conditional == null || containerClassName == null || fieldName == null) {
            return conditional;
        }
        try {
            Object container = resolveContainer(containerClassName, fieldName);
            if (container == null) {
                return conditional;
            }
            Class<?> containerType = Class.forName("mods.railcraft.common.core.IRailcraftObjectContainer");
            Method add = conditional.getClass().getMethod("add", containerType);
            return add.invoke(conditional, container);
        } catch (Throwable throwable) {
            logOnce("eager-condition-" + containerClassName + '.' + fieldName,
                    "Railcraft eager condition fallback failed for " + containerClassName + '.' + fieldName, throwable);
            return conditional;
        }
    }

    @SuppressWarnings("unchecked")
    private static void addContainersDirect(Object module, String[][] descriptors) {
        if (module == null || descriptors == null) {
            return;
        }
        try {
            Field field = findField(module.getClass(), "objectContainers");
            field.setAccessible(true);
            Object value = field.get(module);
            if (!(value instanceof Set)) {
                return;
            }
            Set<Object> containers = (Set<Object>) value;
            for (String[] descriptor : descriptors) {
                Object container = resolveContainer(descriptor[0], descriptor[1]);
                if (container != null) {
                    containers.add(container);
                }
            }
        } catch (Throwable throwable) {
            logOnce("module-ic2-direct-add", "Railcraft deferred ModuleIC2 container realization failed", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addContainerProxies(Object module, String[][] descriptors) {
        if (module == null || descriptors == null) {
            return;
        }
        try {
            Field field = findField(module.getClass(), "objectContainers");
            field.setAccessible(true);
            Object value = field.get(module);
            if (!(value instanceof Set)) {
                return;
            }
            Set<Object> containers = (Set<Object>) value;
            Class<?> containerType = Class.forName("mods.railcraft.common.core.IRailcraftObjectContainer");
            for (String[] descriptor : descriptors) {
                containers.add(createContainerProxy(containerType, descriptor[0], descriptor[1]));
            }
        } catch (Throwable throwable) {
            logOnce("module-proxy-add-" + module.getClass().getName(), "Railcraft deferred module container proxy insertion failed", throwable);
            addContainersDirect(module, descriptors);
        }
    }

    @SuppressWarnings("unchecked")
    private static void realizeModuleContainerProxies(Object module) {
        try {
            Field field = findField(module.getClass(), "objectContainers");
            field.setAccessible(true);
            Object value = field.get(module);
            if (!(value instanceof Set)) {
                return;
            }
            Set<Object> containers = (Set<Object>) value;
            List<Object> realized = new ArrayList<>(containers.size());
            boolean changed = false;
            for (Object container : containers) {
                if (Proxy.isProxyClass(container.getClass())) {
                    InvocationHandler handler = Proxy.getInvocationHandler(container);
                    if (handler instanceof DeferredContainerInvocationHandler) {
                        Object resolved = ((DeferredContainerInvocationHandler) handler).resolve();
                        if (resolved != null) {
                            realized.add(resolved);
                            changed = true;
                            continue;
                        }
                    }
                }
                realized.add(container);
            }
            if (changed) {
                containers.clear();
                containers.addAll(realized);
            }
        } catch (Throwable throwable) {
            logOnce("module-proxy-realize-" + module.getClass().getName(), "Railcraft deferred module container realization failed", throwable);
        }
    }

    private static Object createContainerProxy(Class<?> containerType, String containerClassName, String fieldName) {
        return Proxy.newProxyInstance(
                containerType.getClassLoader(),
                new Class<?>[] {containerType},
                new DeferredContainerInvocationHandler(containerClassName, fieldName)
        );
    }

    private static String[][] decodeDescriptors(String encodedDescriptors) {
        String[] parts = encodedDescriptors.split(";");
        String[][] descriptors = new String[parts.length][2];
        for (int i = 0; i < parts.length; i++) {
            int separator = parts[i].lastIndexOf('#');
            if (separator <= 0 || separator == parts[i].length() - 1) {
                descriptors[i][0] = "";
                descriptors[i][1] = "";
            } else {
                descriptors[i][0] = parts[i].substring(0, separator);
                descriptors[i][1] = parts[i].substring(separator + 1);
            }
        }
        return descriptors;
    }

    private static final class DeferredContainerInvocationHandler implements InvocationHandler {
        private final String containerClassName;
        private final String fieldName;
        private volatile Object resolved;

        private DeferredContainerInvocationHandler(String containerClassName, String fieldName) {
            this.containerClassName = containerClassName;
            this.fieldName = fieldName;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("getBaseTag".equals(name) && method.getParameterTypes().length == 0) {
                return baseTag(containerClassName, fieldName);
            }
            if ("toString".equals(name) && method.getParameterTypes().length == 0) {
                return containerClassName + '.' + fieldName + " (deferred)";
            }
            if ("hashCode".equals(name) && method.getParameterTypes().length == 0) {
                return (containerClassName + '#' + fieldName).hashCode();
            }
            if ("equals".equals(name) && method.getParameterTypes().length == 1) {
                return proxy == args[0];
            }
            Object target = resolve();
            if (target == null) {
                return defaultValue(method.getReturnType());
            }
            return method.invoke(target, args);
        }

        private Object resolve() {
            Object current = resolved;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                if (resolved == null) {
                    resolved = resolveContainer(containerClassName, fieldName);
                }
                return resolved;
            }
        }

        private static String baseTag(String containerClassName, String fieldName) {
            if ("mods.railcraft.common.blocks.aesthetics.brick.BrickTheme".equals(containerClassName)) {
                return brickThemeBaseTag(fieldName);
            }
            if ("mods.railcraft.common.blocks.tracks.outfitted.TrackKits".equals(containerClassName)) {
                if ("BUFFER_STOP".equals(fieldName)) {
                    return "buffer";
                }
                if ("DISEMBARK".equals(fieldName)) {
                    return "disembarking";
                }
                if ("HIGH_SPEED_TRANSITION".equals(fieldName)) {
                    return "transition";
                }
            }
            return fieldName.toLowerCase(java.util.Locale.ENGLISH);
        }

        private static String brickThemeBaseTag(String fieldName) {
            if ("BLEACHEDBONE".equals(fieldName)) {
                return "bleached_bone_brick";
            }
            if ("BLOODSTAINED".equals(fieldName)) {
                return "blood_stained_brick";
            }
            if ("FROSTBOUND".equals(fieldName)) {
                return "frost_bound_brick";
            }
            return fieldName.toLowerCase(java.util.Locale.ENGLISH) + "_brick";
        }

        private static Object defaultValue(Class<?> type) {
            if (type == Void.TYPE) {
                return null;
            }
            if (type == Boolean.TYPE) {
                return false;
            }
            if (type == Byte.TYPE) {
                return (byte) 0;
            }
            if (type == Short.TYPE) {
                return (short) 0;
            }
            if (type == Integer.TYPE) {
                return 0;
            }
            if (type == Long.TYPE) {
                return 0L;
            }
            if (type == Float.TYPE) {
                return 0.0F;
            }
            if (type == Double.TYPE) {
                return 0.0D;
            }
            if (type == Character.TYPE) {
                return (char) 0;
            }
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object resolveContainer(String containerClassName, String fieldName) {
        try {
            Class<?> type = Class.forName(containerClassName);
            if (!TargetedModVersions.isRailcraftClass(type.getName())) {
                return null;
            }
            Field field = type.getField(fieldName);
            return field.get(null);
        } catch (Throwable throwable) {
            logOnce("resolve-" + containerClassName + '.' + fieldName,
                    "Railcraft lazy condition could not resolve " + containerClassName + '.' + fieldName, throwable);
            return null;
        }
    }

    private static String moduleStageLabel(Object stage, Object module, boolean enabled) {
        String stageName = stage == null ? "unknown" : String.valueOf(stage);
        String moduleName = module == null ? "unknown" : module.getClass().getName();
        return "RC RailcraftModuleManager.processStage module " + stageName + ' ' + (enabled ? "enabled " : "disabled ") + moduleName;
    }

    private static boolean isObjectDefined(Object container) {
        try {
            Class<?> containerType = Class.forName("mods.railcraft.common.core.IRailcraftObjectContainer");
            Class<?> manager = Class.forName("mods.railcraft.common.modules.RailcraftModuleManager");
            Method method = manager.getMethod("isObjectDefined", containerType);
            Object value = method.invoke(null, container);
            return Boolean.TRUE.equals(value);
        } catch (Throwable throwable) {
            logOnce("isObjectDefined", "Railcraft lazy condition could not query RailcraftModuleManager.isObjectDefined", throwable);
            return false;
        }
    }

    private static boolean isEnabled(Object container) {
        try {
            Method method = container.getClass().getMethod("isEnabled");
            Object value = method.invoke(container);
            return Boolean.TRUE.equals(value);
        } catch (Throwable throwable) {
            logOnce("isEnabled-" + container.getClass().getName(), "Railcraft lazy condition could not query isEnabled", throwable);
            return false;
        }
    }

    private static void logOnce(String key, String message, Throwable throwable) {
        if (LOGGED.putIfAbsent(key, Boolean.TRUE) == null) {
            GPOM.LOGGER.warn(message, throwable);
        }
    }
}
