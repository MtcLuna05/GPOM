package com.l.gpom.compat.journeymap;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ITeleporter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class JourneyMapBetterPortalsTeleportCompat {
    private static final String TRANSITION_HANDLER = "de.johni0702.minecraft.betterportals.impl.transition.server.DimensionTransitionHandler";
    private static final Set<String> FAILURE_LOG_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile Object transitionHandlerInstance;
    private static volatile Method getEnabledMethod;
    private static volatile Method transferPlayerToDimensionMethod;
    private static volatile Method getXMethod;
    private static volatile Method getYMethod;
    private static volatile Method getZMethod;
    private static volatile Method getDimMethod;
    private static volatile Field worldProviderField;
    private static volatile Method providerDimensionMethod;
    private static volatile Field entityPitchField;
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
            World startWorld = player.getEntityWorld();
            if (startWorld == null || startWorld == destinationWorld || !player.isEntityAlive()) {
                return false;
            }

            Object handler = transitionHandler();
            if (handler == null || !betterPortalsTransitionEnabled(handler)) {
                return false;
            }

            int dimension = intLocation(location, "getDim");
            double x = doubleLocation(location, "getX") + 0.5D;
            double y = doubleLocation(location, "getY");
            double z = doubleLocation(location, "getZ") + 0.5D;
            if (dimension != worldDimension(destinationWorld)) {
                return false;
            }

            player.dismountRidingEntity();
            WaypointTeleporter teleporter = new WaypointTeleporter(x, y, z, yaw, entityPitch(player));
            Object result = transferPlayerToDimension(handler).invoke(handler, player, Integer.valueOf(dimension), teleporter);
            boolean handled = result instanceof Boolean && ((Boolean) result).booleanValue();
            if (handled && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[JourneyMapBetterPortals] Routed waypoint teleport for {} to dim {} through BetterPortals transition",
                        player.getName(),
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

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
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
        throw new NoSuchMethodException(type.getName() + " " + java.util.Arrays.toString(names));
    }

    private static void logFailure(String key, Throwable throwable) {
        if (!FAILURE_LOG_KEYS.add(key)) {
            return;
        }
        GPOM.LOGGER.warn("[JourneyMapBetterPortals] {} failed; falling back to JourneyMap teleport: {}", key, throwable.toString());
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
