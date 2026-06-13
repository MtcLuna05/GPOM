package com.l.gpom.compat.journeymap;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class JourneyMapLeakCleanup {
    private static final Set<String> FAILURE_LOG_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final AtomicBoolean CLIENT_CLEANUP_SCHEDULED = new AtomicBoolean();
    private static volatile Boolean journeyMapPresent;

    private JourneyMapLeakCleanup() {
    }

    public static void cleanup(String reason) {
        if (!GpomEarlyConfig.journeyMapCleanupLeaksEnabled() || !isJourneyMapPresent()) {
            return;
        }
        if (!isClientThread()) {
            scheduleCleanupOnClientThread(reason);
            return;
        }

        synchronized (JourneyMapLeakCleanup.class) {
            int actions = 0;
            Object client = journeyMapClient();
            if (client != null) {
                actions += invoke(client, "stopMapping", reason) ? 1 : 0;
                actions += clearMultithreadTaskController(client, reason) ? 1 : 0;
                actions += clearMainThreadTaskQueues(client, reason) ? 1 : 0;
            }
            actions += invokeStaticInstance("journeymap.client.feature.FeatureManager", "INSTANCE", "reset", reason) ? 1 : 0;
            actions += clearDataCache(reason) ? 1 : 0;
            actions += invokeStatic("journeymap.client.render.map.TileDrawStepCache", "clear", reason) ? 1 : 0;
            actions += clearRegionImageCache(reason) ? 1 : 0;
            actions += clearFileHandlerWorld(reason) ? 1 : 0;
            actions += clearDimensionsButtonProvider(reason) ? 1 : 0;

            if (actions > 0 && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[JourneyMapLeakCleanup] Cleared {} JourneyMap lifecycle states after {}", actions, reason);
            }
        }
    }

    private static boolean isClientThread() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            return minecraft != null && minecraft.isCallingFromMinecraftThread();
        } catch (Throwable ignored) {
            return "Client thread".equals(Thread.currentThread().getName());
        }
    }

    private static void scheduleCleanupOnClientThread(final String reason) {
        if (!CLIENT_CLEANUP_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                CLIENT_CLEANUP_SCHEDULED.set(false);
                return;
            }
            minecraft.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    CLIENT_CLEANUP_SCHEDULED.set(false);
                    cleanup(reason + " deferred to client thread");
                }
            });
        } catch (Throwable throwable) {
            CLIENT_CLEANUP_SCHEDULED.set(false);
            logFailure("scheduleClientCleanup", "JourneyMap cleanup client-thread scheduling during " + reason, throwable);
        }
    }

    private static boolean isJourneyMapPresent() {
        Boolean cached = journeyMapPresent;
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean present = findClass("journeymap.common.Journeymap") != null;
        journeyMapPresent = Boolean.valueOf(present);
        return present;
    }

    private static Object journeyMapClient() {
        Class<?> journeymap = findClass("journeymap.common.Journeymap");
        if (journeymap == null) {
            return null;
        }
        try {
            Method method = journeymap.getMethod("getClient");
            method.setAccessible(true);
            return method.invoke(null);
        } catch (Throwable throwable) {
            logFailure("getClient", "JourneyMap getClient", throwable);
            return null;
        }
    }

    private static boolean clearDataCache(String reason) {
        Object dataCache = staticFieldValue("journeymap.client.data.DataCache", "INSTANCE", reason);
        if (dataCache == null) {
            return false;
        }
        boolean changed = false;
        changed |= invoke(dataCache, "stopChunkMDRetention", reason);
        changed |= invoke(dataCache, "invalidateChunkMDCache", reason);
        changed |= invoke(dataCache, "purge", reason);
        return changed;
    }

    private static boolean clearRegionImageCache(String reason) {
        Object cache = staticFieldValue("journeymap.client.model.RegionImageCache", "INSTANCE", reason);
        if (cache == null) {
            return false;
        }
        boolean changed = false;
        changed |= invoke(cache, "flushToDisk", reason, Boolean.FALSE);
        changed |= invoke(cache, "clear", reason);
        return changed;
    }

    private static boolean clearFileHandlerWorld(String reason) {
        return setStaticField("journeymap.client.io.FileHandler", "theLastWorld", null, reason);
    }

    private static boolean clearDimensionsButtonProvider(String reason) {
        boolean changed = false;
        changed |= setStaticField("journeymap.client.ui.waypoint.DimensionsButton", "currentWorldProvider", null, reason);
        changed |= setStaticField("journeymap.client.ui.waypoint.DimensionsButton", "needInit", Boolean.TRUE, reason);
        return changed;
    }

    private static boolean clearMultithreadTaskController(Object client, String reason) {
        Object controller = fieldValue(client, "multithreadTaskController", reason);
        if (controller == null) {
            return false;
        }
        invoke(controller, "disableTasks", reason);
        invoke(controller, "clear", reason);
        return setField(client, "multithreadTaskController", null, reason);
    }

    private static boolean clearMainThreadTaskQueues(Object client, String reason) {
        Object controller = fieldValue(client, "mainThreadTaskController", reason);
        if (controller == null) {
            return false;
        }
        boolean changed = false;
        changed |= clearQueueField(controller, "currentQueue", reason);
        changed |= clearQueueField(controller, "deferredQueue", reason);
        return changed;
    }

    private static boolean clearQueueField(Object target, String fieldName, String reason) {
        Object queue = fieldValue(target, fieldName, reason);
        if (queue == null) {
            return false;
        }
        return invoke(queue, "clear", reason);
    }

    private static boolean invokeStaticInstance(String className, String fieldName, String methodName, String reason) {
        Object instance = staticFieldValue(className, fieldName, reason);
        return instance != null && invoke(instance, methodName, reason);
    }

    private static boolean invokeStatic(String className, String methodName, String reason) {
        Class<?> clazz = findClass(className);
        if (clazz == null) {
            return false;
        }
        try {
            Method method = findNoArgMethod(clazz, methodName);
            method.invoke(null);
            return true;
        } catch (Throwable throwable) {
            logFailure(className + '#' + methodName, "JourneyMap cleanup " + methodName + " during " + reason, throwable);
            return false;
        }
    }

    private static boolean invoke(Object target, String methodName, String reason, Object... args) {
        if (target == null) {
            return false;
        }
        try {
            Method method = findMethod(target.getClass(), methodName, args);
            method.invoke(target, args);
            return true;
        } catch (Throwable throwable) {
            logFailure(target.getClass().getName() + '#' + methodName, "JourneyMap cleanup " + methodName + " during " + reason, throwable);
            return false;
        }
    }

    private static Method findNoArgMethod(Class<?> clazz, String methodName) throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        Method method = clazz.getMethod(methodName);
        method.setAccessible(true);
        return method;
    }

    private static Method findMethod(Class<?> clazz, String methodName, Object[] args) throws NoSuchMethodException {
        Class<?> current = clazz;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && parametersMatch(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(methodName) && parametersMatch(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(clazz.getName() + '#' + methodName);
    }

    private static boolean parametersMatch(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                continue;
            }
            Class<?> parameterType = wrap(parameterTypes[i]);
            if (!parameterType.isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == Boolean.TYPE) {
            return Boolean.class;
        }
        if (type == Integer.TYPE) {
            return Integer.class;
        }
        if (type == Long.TYPE) {
            return Long.class;
        }
        if (type == Float.TYPE) {
            return Float.class;
        }
        if (type == Double.TYPE) {
            return Double.class;
        }
        if (type == Byte.TYPE) {
            return Byte.class;
        }
        if (type == Short.TYPE) {
            return Short.class;
        }
        if (type == Character.TYPE) {
            return Character.class;
        }
        return type;
    }

    private static Object staticFieldValue(String className, String fieldName, String reason) {
        Class<?> clazz = findClass(className);
        if (clazz == null) {
            return null;
        }
        try {
            Field field = findField(clazz, fieldName);
            return field.get(null);
        } catch (Throwable throwable) {
            logFailure(className + '#' + fieldName, "JourneyMap cleanup read field during " + reason, throwable);
            return null;
        }
    }

    private static Object fieldValue(Object target, String fieldName, String reason) {
        if (target == null) {
            return null;
        }
        try {
            Field field = findField(target.getClass(), fieldName);
            return field.get(target);
        } catch (Throwable throwable) {
            logFailure(target.getClass().getName() + '#' + fieldName, "JourneyMap cleanup read field during " + reason, throwable);
            return null;
        }
    }

    private static boolean setStaticField(String className, String fieldName, Object value, String reason) {
        Class<?> clazz = findClass(className);
        if (clazz == null) {
            return false;
        }
        try {
            Field field = findField(clazz, fieldName);
            field.set(null, value);
            return true;
        } catch (Throwable throwable) {
            logFailure(className + '#' + fieldName, "JourneyMap cleanup set field during " + reason, throwable);
            return false;
        }
    }

    private static boolean setField(Object target, String fieldName, Object value, String reason) {
        if (target == null) {
            return false;
        }
        try {
            Field field = findField(target.getClass(), fieldName);
            field.set(target, value);
            return true;
        } catch (Throwable throwable) {
            logFailure(target.getClass().getName() + '#' + fieldName, "JourneyMap cleanup set field during " + reason, throwable);
            return false;
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        Field field = clazz.getField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className, false, JourneyMapLeakCleanup.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logFailure(String key, String message, Throwable throwable) {
        if (!GpomEarlyConfig.optimizationInfoLogsEnabled() || !FAILURE_LOG_KEYS.add(key)) {
            return;
        }
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        GPOM.LOGGER.warn("[JourneyMapLeakCleanup] {} failed: {}", message, cause.toString());
    }
}
