package com.l.gpom.compat.journeymap;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JourneyMapBetterPortalsTeleportCompat {
    private static final String TRANSITION_HANDLER = "de.johni0702.minecraft.betterportals.impl.transition.server.DimensionTransitionHandler";
    private static final String SERVER_WORLDS_MANAGER_KT = "de.johni0702.minecraft.view.server.ServerWorldsManagerKt";
    private static final boolean SRG_RUNTIME = detectSrgRuntime();
    private static final Set<String> FAILURE_LOG_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> SKIP_LOG_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile Object transitionHandlerInstance;
    private static volatile Method getEnabledMethod;
    private static volatile Method transferPlayerToDimensionMethod;
    private static volatile Method serverWorldsManagerMethod;
    private static volatile Method managerViewsMethod;
    private static volatile Method transitionViewsMethod;
    private static volatile Method getXMethod;
    private static volatile Method getYMethod;
    private static volatile Method getZMethod;
    private static volatile Method getDimMethod;
    private static volatile Field worldProviderField;
    private static volatile Method providerDimensionMethod;
    private static volatile Field entityPitchField;
    private static volatile Method entityWorldMethod;
    private static volatile Method entityAliveMethod;
    private static volatile Method entityDismountMethod;
    private static volatile Method entityNameMethod;
    private static volatile Method setLocationAndAnglesMethod;
    private static volatile Boolean betterPortalsTransitionPresent;

    private JourneyMapBetterPortalsTeleportCompat() {
    }

    public static boolean tryTeleportWithBetterPortalsTransition(
            MinecraftServer server,
            World destinationWorld,
            Entity entity,
            Object location,
            float yaw
    ) {
        if (!GpomEarlyConfig.betterPortalsJourneyMapWaypointTeleportTransitionEnabled()) {
            return false;
        }
        if (server == null || !(destinationWorld instanceof WorldServer) || !(entity instanceof EntityPlayerMP) || location == null) {
            return false;
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;

        try {
            World startWorld = entityWorld(player);
            if (startWorld == null || startWorld == destinationWorld || !entityAlive(player)) {
                return false;
            }

            Object handler = transitionHandler();
            if (handler == null || !betterPortalsTransitionEnabled(handler)) {
                return false;
            }

            if (!betterPortalsWaypointStateSafe(handler, player, startWorld)) {
                return false;
            }

            int dimension = intLocation(location, "getDim");
            double x = doubleLocation(location, "getX") + 0.5D;
            double y = doubleLocation(location, "getY");
            double z = doubleLocation(location, "getZ") + 0.5D;
            if (dimension != worldDimension(destinationWorld)) {
                return false;
            }

            dismountEntity(player);
            WaypointTeleporter teleporter = new WaypointTeleporter(x, y, z, yaw, entityPitch(player));
            Object result = transferPlayerToDimension(handler).invoke(handler, player, Integer.valueOf(dimension), teleporter);
            boolean handled = result instanceof Boolean && ((Boolean) result).booleanValue();
            if (handled && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[JourneyMapBetterPortals] Routed waypoint teleport for {} to dim {} through BetterPortals transition",
                        entityName(player),
                        Integer.valueOf(dimension)
                );
            }
            return handled;
        } catch (Throwable throwable) {
            logFailure("transition", throwable);
            return false;
        }
    }

    private static Object transitionHandler() throws ReflectiveOperationException {
        Boolean present = betterPortalsTransitionPresent;
        if (present != null && !present.booleanValue()) {
            return null;
        }

        Object instance = transitionHandlerInstance;
        if (instance != null) {
            return instance;
        }

        try {
            Class<?> clazz = Class.forName(TRANSITION_HANDLER, false, JourneyMapBetterPortalsTeleportCompat.class.getClassLoader());
            Field instanceField = clazz.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            instance = instanceField.get(null);
            transitionHandlerInstance = instance;
            betterPortalsTransitionPresent = Boolean.TRUE;
            return instance;
        } catch (ClassNotFoundException exception) {
            betterPortalsTransitionPresent = Boolean.FALSE;
            return null;
        }
    }

    private static boolean betterPortalsTransitionEnabled(Object handler) throws ReflectiveOperationException {
        Method method = getEnabledMethod;
        if (method == null) {
            method = handler.getClass().getDeclaredMethod("getEnabled");
            method.setAccessible(true);
            getEnabledMethod = method;
        }
        Object result = method.invoke(handler);
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private static boolean betterPortalsWaypointStateSafe(Object handler, EntityPlayerMP player, World startWorld) throws ReflectiveOperationException {
        Object manager = serverWorldsManager(player);
        if (manager == null) {
            logSkip("no-manager", "BetterPortals has no server worlds manager for the player");
            return false;
        }
        if (hasPendingTransitionView(handler, manager)) {
            logSkip("pending-transition", "BetterPortals already has a pending dimension-transition view");
            return false;
        }
        if (!GpomEarlyConfig.betterPortalsJourneyMapWaypointTeleportRequireActiveViewEnabled()) {
            return true;
        }
        if (!hasActiveViewForWorld(manager, startWorld)) {
            logSkip("no-active-view", "BetterPortals has no active non-main view for the source world");
            return false;
        }
        return true;
    }

    private static Object serverWorldsManager(EntityPlayerMP player) throws ReflectiveOperationException {
        Method method = serverWorldsManagerMethod;
        if (method == null) {
            Class<?> clazz = Class.forName(SERVER_WORLDS_MANAGER_KT, false, JourneyMapBetterPortalsTeleportCompat.class.getClassLoader());
            method = clazz.getDeclaredMethod("getWorldsManager", EntityPlayerMP.class);
            method.setAccessible(true);
            serverWorldsManagerMethod = method;
        }
        return method.invoke(null, player);
    }

    private static boolean hasPendingTransitionView(Object handler, Object manager) throws ReflectiveOperationException {
        Method method = transitionViewsMethod;
        if (method == null) {
            method = handler.getClass().getDeclaredMethod("getViews");
            method.setAccessible(true);
            transitionViewsMethod = method;
        }
        Object views = method.invoke(handler);
        if (!(views instanceof Map)) {
            return false;
        }
        Object playerViews = ((Map<?, ?>) views).get(manager);
        return playerViews instanceof Map && !((Map<?, ?>) playerViews).isEmpty();
    }

    private static boolean hasActiveViewForWorld(Object manager, World startWorld) throws ReflectiveOperationException {
        Method method = managerViewsMethod;
        if (method == null) {
            method = manager.getClass().getMethod("getViews");
            method.setAccessible(true);
            managerViewsMethod = method;
        }
        Object views = method.invoke(manager);
        if (!(views instanceof Map)) {
            return false;
        }
        Object sourceWorldViews = ((Map<?, ?>) views).get(startWorld);
        return sourceWorldViews instanceof List && !((List<?>) sourceWorldViews).isEmpty();
    }

    private static Method transferPlayerToDimension(Object handler) throws ReflectiveOperationException {
        Method method = transferPlayerToDimensionMethod;
        if (method == null) {
            method = handler.getClass().getDeclaredMethod(
                    "transferPlayerToDimension",
                    EntityPlayerMP.class,
                    int.class,
                    ITeleporter.class
            );
            method.setAccessible(true);
            transferPlayerToDimensionMethod = method;
        }
        return method;
    }

    private static double doubleLocation(Object location, String getterName) throws ReflectiveOperationException {
        Object value = locationMethod(location, getterName).invoke(location);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("JourneyMap Location " + getterName + " returned " + value);
        }
        return ((Number) value).doubleValue();
    }

    private static int intLocation(Object location, String getterName) throws ReflectiveOperationException {
        Object value = locationMethod(location, getterName).invoke(location);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("JourneyMap Location " + getterName + " returned " + value);
        }
        return ((Number) value).intValue();
    }

    private static Method locationMethod(Object location, String getterName) throws ReflectiveOperationException {
        if ("getX".equals(getterName)) {
            Method method = getXMethod;
            if (method == null) {
                method = resolveLocationMethod(location, getterName);
                getXMethod = method;
            }
            return method;
        }
        if ("getY".equals(getterName)) {
            Method method = getYMethod;
            if (method == null) {
                method = resolveLocationMethod(location, getterName);
                getYMethod = method;
            }
            return method;
        }
        if ("getZ".equals(getterName)) {
            Method method = getZMethod;
            if (method == null) {
                method = resolveLocationMethod(location, getterName);
                getZMethod = method;
            }
            return method;
        }
        Method method = getDimMethod;
        if (method == null) {
            method = resolveLocationMethod(location, getterName);
            getDimMethod = method;
        }
        return method;
    }

    private static Method resolveLocationMethod(Object location, String getterName) throws ReflectiveOperationException {
        Method method = location.getClass().getDeclaredMethod(getterName);
        method.setAccessible(true);
        return method;
    }

    private static int worldDimension(World world) throws ReflectiveOperationException {
        Field field = worldProviderField;
        if (field == null) {
            field = findField(world.getClass(), "provider", "field_73011_w");
            worldProviderField = field;
        }
        Object provider = field.get(world);
        if (provider == null) {
            throw new IllegalStateException("World provider is null");
        }
        Method method = providerDimensionMethod;
        if (method == null) {
            method = provider.getClass().getMethod("getDimension");
            method.setAccessible(true);
            providerDimensionMethod = method;
        }
        Object result = method.invoke(provider);
        if (!(result instanceof Number)) {
            throw new IllegalStateException("WorldProvider.getDimension returned " + result);
        }
        return ((Number) result).intValue();
    }

    private static float entityPitch(Entity entity) throws ReflectiveOperationException {
        Field field = entityPitchField;
        if (field == null) {
            field = findField(entity.getClass(), "rotationPitch", "field_70125_A");
            entityPitchField = field;
        }
        Object result = field.get(entity);
        if (!(result instanceof Number)) {
            throw new IllegalStateException("Entity rotation pitch field returned " + result);
        }
        return ((Number) result).floatValue();
    }

    private static World entityWorld(Entity entity) throws ReflectiveOperationException {
        Method method = entityWorldMethod;
        if (method == null) {
            method = findMethod(entity.getClass(), new Class<?>[0], "func_130014_f_", "getEntityWorld");
            entityWorldMethod = method;
        }
        Object result = method.invoke(entity);
        return result instanceof World ? (World) result : null;
    }

    private static boolean entityAlive(Entity entity) throws ReflectiveOperationException {
        Method method = entityAliveMethod;
        if (method == null) {
            method = findMethod(entity.getClass(), new Class<?>[0], "func_70089_S", "isEntityAlive");
            entityAliveMethod = method;
        }
        Object result = method.invoke(entity);
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private static void dismountEntity(Entity entity) throws ReflectiveOperationException {
        Method method = entityDismountMethod;
        if (method == null) {
            method = findMethod(entity.getClass(), new Class<?>[0], "func_184210_p", "dismountRidingEntity");
            entityDismountMethod = method;
        }
        method.invoke(entity);
    }

    private static String entityName(Entity entity) throws ReflectiveOperationException {
        Method method = entityNameMethod;
        if (method == null) {
            method = findMethod(entity.getClass(), new Class<?>[0], "func_70005_c_", "getName");
            entityNameMethod = method;
        }
        Object result = method.invoke(entity);
        return result instanceof String ? (String) result : String.valueOf(entity);
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        String[] orderedNames = orderedNames(names);
        return ReflectionLookup.findField(type, orderedNames);
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        String[] orderedNames = orderedNames(names);
        return ReflectionLookup.findMethod(type, orderedNames, parameterTypes);
    }

    private static String[] orderedNames(String... names) {
        if (names.length < 2) {
            return names;
        }
        String[] ordered = new String[names.length];
        int index = 0;
        for (String name : names) {
            if (isSrgName(name) == SRG_RUNTIME) {
                ordered[index++] = name;
            }
        }
        for (String name : names) {
            if (isSrgName(name) != SRG_RUNTIME) {
                ordered[index++] = name;
            }
        }
        return ordered;
    }

    private static boolean isSrgName(String name) {
        return name.startsWith("func_") || name.startsWith("field_");
    }

    private static boolean detectSrgRuntime() {
        try {
            Class<?> stateClass = Class.forName(
                    "net.minecraft.block.state.IBlockState",
                    false,
                    JourneyMapBetterPortalsTeleportCompat.class.getClassLoader()
            );
            stateClass.getMethod("func_177230_c");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void logFailure(String key, Throwable throwable) {
        if (!FAILURE_LOG_KEYS.add(key)) {
            return;
        }
        GPOM.LOGGER.warn("[JourneyMapBetterPortals] {} failed; falling back to JourneyMap teleport: {}", key, throwable.toString());
    }

    private static void logSkip(String key, String reason) {
        if (!GpomEarlyConfig.optimizationInfoLogsEnabled() || !SKIP_LOG_KEYS.add(key)) {
            return;
        }
        GPOM.LOGGER.info("[JourneyMapBetterPortals] Skipped BetterPortals waypoint transition: {}; falling back to JourneyMap teleport", reason);
    }

    private static final class WaypointTeleporter implements ITeleporter {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;

        private WaypointTeleporter(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void placeEntity(World world, Entity entity, float yaw) {
            tryLoadChunk(world, this.x, this.z);
            try {
                Method method = setLocationAndAnglesMethod;
                if (method == null) {
                    method = findMethod(
                            entity.getClass(),
                            new Class<?>[]{double.class, double.class, double.class, float.class, float.class},
                            "setLocationAndAngles",
                            "func_70012_b"
                    );
                    setLocationAndAnglesMethod = method;
                }
                method.invoke(entity, Double.valueOf(this.x), Double.valueOf(this.y), Double.valueOf(this.z), Float.valueOf(this.yaw), Float.valueOf(this.pitch));
            } catch (Throwable throwable) {
                throw new IllegalStateException("Failed to place JourneyMap BetterPortals transition entity", throwable);
            }
        }

        private static void tryLoadChunk(World world, double x, double z) {
            if (!(world instanceof WorldServer)) {
                return;
            }
            try {
                Method providerMethod = findMethod(world.getClass(), new Class<?>[0], "getChunkProvider", "func_72863_F");
                Object provider = providerMethod.invoke(world);
                Method loadChunk = findMethod(provider.getClass(), new Class<?>[]{int.class, int.class}, "loadChunk", "func_186028_c");
                loadChunk.invoke(provider, Integer.valueOf(MathHelper.floor(x) >> 4), Integer.valueOf(MathHelper.floor(z) >> 4));
            } catch (Throwable ignored) {
            }
        }
    }
}
