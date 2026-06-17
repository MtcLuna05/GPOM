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
        World startWorld = player.world;
        if (startWorld == null || startWorld == destinationWorld || !player.isEntityAlive()) {
            return false;
        }

        try {
            Object handler = transitionHandler();
            if (handler == null || !betterPortalsTransitionEnabled(handler)) {
                return false;
            }

            int dimension = intLocation(location, "getDim");
            double x = doubleLocation(location, "getX") + 0.5D;
            double y = doubleLocation(location, "getY");
            double z = doubleLocation(location, "getZ") + 0.5D;
            if (dimension != ((WorldServer) destinationWorld).provider.getDimension()) {
                return false;
            }

            player.dismountRidingEntity();
            WaypointTeleporter teleporter = new WaypointTeleporter(x, y, z, yaw, player.rotationPitch);
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
            if (world instanceof WorldServer) {
                ((WorldServer) world).getChunkProvider().loadChunk(MathHelper.floor(this.x) >> 4, MathHelper.floor(this.z) >> 4);
            }
            entity.setLocationAndAngles(this.x, this.y, this.z, this.yaw, this.pitch);
        }
    }
}
