package com.l.gpom.compat.betterportals;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BetterPortalsWaypointCrashGuard {
    private static final String SERVER_WORLDS_MANAGER_IMPL_HELPER = "de.johni0702.minecraft.view.impl.ViewAPIImplKt";
    private static final Set<String> LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile Method getWorldsManagerImplMethod;
    private static volatile Method getWorldManagersMethod;
    private static volatile Field playerChunkMapWorldField;
    private static volatile Field clientMainViewField;

    private BetterPortalsWaypointCrashGuard() {
    }

    public static boolean shouldSkipDestroyState(Object manager, Object state) {
        if (manager == null || state == null) {
            return false;
        }
        try {
            Object mainView = clientMainView(manager);
            if (mainView != null && mainView == state) {
                logOnce("client-main-destroy", "Skipped BetterPortals destroyState for the client main view");
                return true;
            }
        } catch (Throwable throwable) {
            logFailure("client-main-destroy", throwable);
        }
        return false;
    }

    public static boolean shouldCancelMissingWorldManagerUpdate(Object playerChunkMap, EntityPlayerMP player) {
        if (playerChunkMap == null || player == null) {
            return false;
        }
        try {
            WorldServer world = playerChunkMapWorld(playerChunkMap);
            if (world == null) {
                return false;
            }
            Object worldsManager = worldsManagerImpl(player);
            if (worldsManager == null) {
                return false;
            }
            Object managers = getWorldManagers(worldsManager);
            if (!(managers instanceof Map)) {
                return false;
            }
            if (((Map<?, ?>) managers).get(world) == null) {
                logOnce("server-missing-world-manager", "Skipped BetterPortals PlayerChunkMap update while its world manager is missing");
                return true;
            }
        } catch (Throwable throwable) {
            logFailure("server-missing-world-manager", throwable);
        }
        return false;
    }

    private static Object clientMainView(Object manager) throws ReflectiveOperationException {
        Field field = clientMainViewField;
        if (field == null) {
            field = findField(manager.getClass(), "mainView");
            clientMainViewField = field;
        }
        return field.get(manager);
    }

    private static WorldServer playerChunkMapWorld(Object playerChunkMap) throws ReflectiveOperationException {
        Field field = playerChunkMapWorldField;
        if (field == null) {
            field = findField(playerChunkMap.getClass(), "world", "field_72701_a");
            playerChunkMapWorldField = field;
        }
        Object value = field.get(playerChunkMap);
        return value instanceof WorldServer ? (WorldServer) value : null;
    }

    private static Object worldsManagerImpl(EntityPlayerMP player) throws ReflectiveOperationException {
        Method method = getWorldsManagerImplMethod;
        if (method == null) {
            Class<?> helper = Class.forName(SERVER_WORLDS_MANAGER_IMPL_HELPER, false, BetterPortalsWaypointCrashGuard.class.getClassLoader());
            method = helper.getDeclaredMethod("getWorldsManagerImpl", EntityPlayerMP.class);
            method.setAccessible(true);
            getWorldsManagerImplMethod = method;
        }
        return method.invoke(null, player);
    }

    private static Object getWorldManagers(Object worldsManager) throws ReflectiveOperationException {
        Method method = getWorldManagersMethod;
        if (method == null) {
            method = worldsManager.getClass().getMethod("getWorldManagers");
            method.setAccessible(true);
            getWorldManagersMethod = method;
        }
        return method.invoke(worldsManager);
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException(type.getName() + " " + java.util.Arrays.toString(names));
    }

    private static void logOnce(String key, String message) {
        if (GpomEarlyConfig.optimizationInfoLogsEnabled() && LOGGED.add(key)) {
            GPOM.LOGGER.info("[GPOM BetterPortals Guard] {}", message);
        }
    }

    private static void logFailure(String key, Throwable throwable) {
        if (GpomEarlyConfig.optimizationInfoLogsEnabled() && LOGGED.add(key + ":failure")) {
            GPOM.LOGGER.warn("[GPOM BetterPortals Guard] {} failed: {}", key, throwable.toString());
        }
    }
}
