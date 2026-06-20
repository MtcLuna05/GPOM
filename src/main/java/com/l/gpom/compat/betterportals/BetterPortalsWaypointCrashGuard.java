package com.l.gpom.compat.betterportals;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
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
    private static volatile Method managerGetPlayerMethod;
    private static volatile Method playerGetServerMethod;
    private static volatile Method serverGetWorldMethod;
    private static volatile Field playerChunkMapWorldField;
    private static volatile Field managerPlayerField;
    private static volatile Field playerServerField;
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

    public static boolean shouldSkipUnsafeThirdPartyTransition(EntityPlayerMP player, int targetDimension, ITeleporter teleporter) {
        if (!GpomEarlyConfig.betterPortalsSkipUnsafeThirdPartyTransitionEnabled() || player == null || teleporter == null) {
            return false;
        }
        try {
            Object worldsManager = worldsManagerImpl(player);
            if (worldsManager == null) {
                return false;
            }
            Object managersObject = getWorldManagers(worldsManager);
            if (!(managersObject instanceof Map)) {
                return false;
            }

            Map<?, ?> managers = (Map<?, ?>) managersObject;
            WorldServer targetWorld = serverWorldForDimension(player, targetDimension);
            Object targetManager = targetWorld == null ? null : managers.get(targetWorld);
            if (isMainPlayerManager(targetManager)) {
                logOnce("unsafe-third-party-target-main",
                        "Declined BetterPortals enhanced third-party transfer because the target world manager is already a main-player manager");
                return true;
            }

            int mainManagers = 0;
            for (Object manager : managers.values()) {
                if (isMainPlayerManager(manager)) {
                    mainManagers++;
                    if (mainManagers > 1) {
                        logOnce("unsafe-third-party-multiple-main",
                                "Declined BetterPortals enhanced third-party transfer because multiple main-player world managers are present");
                        return true;
                    }
                }
            }
        } catch (Throwable throwable) {
            logFailure("unsafe-third-party-transition", throwable);
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
            method = findMethod(worldsManager.getClass(), "getWorldManagers");
            getWorldManagersMethod = method;
        }
        return method.invoke(worldsManager);
    }

    private static WorldServer serverWorldForDimension(EntityPlayerMP player, int dimension) throws ReflectiveOperationException {
        Object server = invokePlayerGetServer(player);
        if (!(server instanceof MinecraftServer)) {
            return null;
        }
        Method method = serverGetWorldMethod;
        if (method == null) {
            method = findMethod(server.getClass(), new Class<?>[]{Integer.TYPE}, "getWorld", "func_71218_a");
            serverGetWorldMethod = method;
        }
        Object world = method.invoke(server, dimension);
        return world instanceof WorldServer ? (WorldServer) world : null;
    }

    private static Object invokePlayerGetServer(EntityPlayerMP player) throws ReflectiveOperationException {
        Method method = playerGetServerMethod;
        if (method == null) {
            method = findMethodOrNull(player.getClass(), "getServer", "func_184102_h");
            playerGetServerMethod = method;
        }
        if (method != null) {
            return method.invoke(player);
        }

        Field field = playerServerField;
        if (field == null) {
            field = findField(player.getClass(), "mcServer", "field_71133_b");
            playerServerField = field;
        }
        return field.get(player);
    }

    private static boolean isMainPlayerManager(Object manager) throws ReflectiveOperationException {
        if (manager == null) {
            return false;
        }
        Object managerPlayer = managerPlayer(manager);
        return managerPlayer instanceof EntityPlayerMP && !isBetterPortalsViewEntity(managerPlayer);
    }

    private static Object managerPlayer(Object manager) throws ReflectiveOperationException {
        Method method = managerGetPlayerMethod;
        if (method == null) {
            method = findMethodOrNull(manager.getClass(), "getPlayer");
            managerGetPlayerMethod = method;
        }
        if (method != null) {
            return method.invoke(manager);
        }

        Field field = managerPlayerField;
        if (field == null) {
            field = findField(manager.getClass(), "player");
            managerPlayerField = field;
        }
        return field.get(manager);
    }

    private static boolean isBetterPortalsViewEntity(Object player) {
        Class<?> current = player.getClass();
        while (current != null) {
            String name = current.getName();
            if ("de.johni0702.minecraft.view.impl.server.ViewEntity".equals(name)
                    || name.endsWith(".ViewEntity")) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String... names) throws NoSuchMethodException {
        Method method = findMethodOrNull(type, names);
        if (method != null) {
            return method;
        }
        throw new NoSuchMethodException(type.getName() + " " + java.util.Arrays.toString(names));
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        Method method = findMethodOrNull(type, parameterTypes, names);
        if (method != null) {
            return method;
        }
        throw new NoSuchMethodException(type.getName() + " " + java.util.Arrays.toString(names));
    }

    private static Method findMethodOrNull(Class<?> type, String... names) {
        return findMethodOrNull(type, new Class<?>[0], names);
    }

    private static Method findMethodOrNull(Class<?> type, Class<?>[] parameterTypes, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
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
            if (throwable instanceof InvocationTargetException
                    && ((InvocationTargetException) throwable).getCause() != null) {
                throwable = ((InvocationTargetException) throwable).getCause();
            }
            GPOM.LOGGER.warn("[GPOM BetterPortals Guard] {} failed: {}", key, throwable.toString());
        }
    }
}
