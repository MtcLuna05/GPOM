package com.l.gpom.compat.advancedrocketry;

import com.l.gpom.GPOM;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionFields;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AdvancedRocketryOxygenOverlayHandoffGuard {
    private static final String ROCKET_EVENT_HANDLER = "zmaster587.advancedRocketry.event.RocketEventHandler";
    private static final String ATMOSPHERE_HANDLER = "zmaster587.advancedRocketry.atmosphere.AtmosphereHandler";
    private static final long NO_RECENT_SUFFOCATION_TIME = -2147483648L;

    private static volatile boolean registered;
    private static volatile boolean unavailable;
    private static volatile boolean failureLogged;
    private static volatile long guardUntilNanos;
    private static volatile Field lastSuffocationTimeField;
    private static volatile Field suppressSuffocationWarningUntilField;
    private static volatile Field lastSuffocationWarningDimField;
    private static volatile Method worldTimeMethod;

    private AdvancedRocketryOxygenOverlayHandoffGuard() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.advancedRocketryOxygenOverlayHandoffGuardEnabled() || !isAdvancedRocketryPresent()) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new AdvancedRocketryOxygenOverlayHandoffGuard());
    }

    public static void beginGuard(String reason) {
        if (!GpomEarlyConfig.advancedRocketryOxygenOverlayHandoffGuardEnabled() || !isAdvancedRocketryPresent()) {
            return;
        }
        int ticks = GpomEarlyConfig.advancedRocketryOxygenOverlayHandoffGuardTicks();
        guardUntilNanos = System.nanoTime() + ticks * 50_000_000L;
        resetWarningState(reason, ticks);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientWorldLoad(WorldEvent.Load event) {
        if (event.getWorld() instanceof WorldClient) {
            beginGuard("client world load");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld() instanceof WorldClient) {
            beginGuard("client world unload");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        beginGuard("client disconnect");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && isGuardActive()) {
            resetWarningState("client tick guard", GpomEarlyConfig.advancedRocketryOxygenOverlayHandoffGuardTicks());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() == RenderGameOverlayEvent.ElementType.HOTBAR && isGuardActive()) {
            resetWarningState("overlay guard", GpomEarlyConfig.advancedRocketryOxygenOverlayHandoffGuardTicks());
        }
    }

    private static boolean isGuardActive() {
        return guardUntilNanos != 0L && System.nanoTime() < guardUntilNanos;
    }

    private static void resetWarningState(String reason, int ticks) {
        try {
            Class<?> atmosphereHandler = loadClass(ATMOSPHERE_HANDLER);
            Class<?> rocketEventHandler = loadClass(ROCKET_EVENT_HANDLER);
            if (atmosphereHandler == null || rocketEventHandler == null) {
                unavailable = true;
                return;
            }

            Field lastSuffocation = cachedField(atmosphereHandler, "lastSuffocationTime", "lastSuffocationTime");
            Field suppressUntil = cachedField(rocketEventHandler, "suppressSuffocationWarningUntil", "suppressSuffocationWarningUntil");
            Field lastWarningDim = cachedField(rocketEventHandler, "lastSuffocationWarningDim", "lastSuffocationWarningDim");

            if (lastSuffocation != null) {
                lastSuffocation.setLong(null, NO_RECENT_SUFFOCATION_TIME);
            }

            long worldTime = currentWorldTime();
            if (suppressUntil != null) {
                suppressUntil.setLong(null, worldTime + Math.max(1, ticks));
            }
            if (lastWarningDim != null) {
                lastWarningDim.setInt(null, currentPlayerDimension());
            }
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.optimizationInfoLogsEnabled() && !failureLogged) {
                failureLogged = true;
                GPOM.LOGGER.warn("[GPOM AdvancedRocketry] Failed to reset oxygen overlay warning state during {}", reason, throwable);
            }
        }
    }

    private static Field cachedField(Class<?> owner, String purpose, String name) {
        Field cached = cachedStaticField(purpose);
        if (cached != null) {
            return cached;
        }

        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            cacheStaticField(purpose, field);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field cachedStaticField(String purpose) {
        if ("lastSuffocationTime".equals(purpose)) {
            return lastSuffocationTimeField;
        }
        if ("suppressSuffocationWarningUntil".equals(purpose)) {
            return suppressSuffocationWarningUntilField;
        }
        if ("lastSuffocationWarningDim".equals(purpose)) {
            return lastSuffocationWarningDimField;
        }
        return null;
    }

    private static void cacheStaticField(String purpose, Field field) {
        if ("lastSuffocationTime".equals(purpose)) {
            lastSuffocationTimeField = field;
        } else if ("suppressSuffocationWarningUntil".equals(purpose)) {
            suppressSuffocationWarningUntilField = field;
        } else if ("lastSuffocationWarningDim".equals(purpose)) {
            lastSuffocationWarningDimField = field;
        }
    }

    private static long currentWorldTime() {
        Minecraft minecraft = ClientAccess.minecraft();
        Object world = minecraft == null ? null : ReflectionFields.get(minecraft, "world", "field_71441_e", "world");
        if (world == null) {
            return 0L;
        }
        try {
            Method method = worldTimeMethod;
            if (method == null) {
                method = findMethod(world.getClass(), "func_82737_E", "getTotalWorldTime");
                worldTimeMethod = method;
            }
            Object value = method == null ? null : method.invoke(world);
            return value instanceof Number ? ((Number) value).longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    private static int currentPlayerDimension() {
        Minecraft minecraft = ClientAccess.minecraft();
        Object player = minecraft == null ? null : ReflectionFields.get(minecraft, "player", "field_71439_g", "player");
        if (player == null) {
            return Integer.MIN_VALUE;
        }
        Object value = ReflectionFields.get(player, "dimension", "field_71093_bK", "dimension");
        return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
    }

    private static Method findMethod(Class<?> owner, String... names) {
        try {
            return ReflectionLookup.findMethod(owner, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean isAdvancedRocketryPresent() {
        return !unavailable && loadClass(ROCKET_EVENT_HANDLER) != null && loadClass(ATMOSPHERE_HANDLER) != null;
    }

    private static Class<?> loadClass(String className) {
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null) {
                return Class.forName(className, false, contextLoader);
            }
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(className, false, AdvancedRocketryOxygenOverlayHandoffGuard.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
