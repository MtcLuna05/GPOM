package com.l.gpom.compat.industrialforegoing;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IndustrialForegoingMobCrusherLoadGuard {
    private static final String MOB_RELOCATOR_TILE = "com.buuz135.industrial.tile.mob.MobRelocatorTile";
    private static final String ADDON_ITEMS_SYNC_KEY = "addonItems";
    private static final ThreadLocal<Object> READING_TILE = new ThreadLocal<>();
    private static final Map<Object, Boolean> DEFERRED_REFRESHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean REFRESH_WARNING_LOGGED = new AtomicBoolean();

    private IndustrialForegoingMobCrusherLoadGuard() {
    }

    public static void beginRead(Object tile) {
        if (enabled() && isMobCrusher(tile)) {
            READING_TILE.set(tile);
        }
    }

    public static void endRead(Object tile) {
        if (READING_TILE.get() == tile) {
            READING_TILE.remove();
        }
    }

    public static boolean suppressPartialSync(Object tile, String key) {
        return enabled() && ADDON_ITEMS_SYNC_KEY.equals(key) && READING_TILE.get() == tile;
    }

    public static boolean suppressUpgradeRefresh(Object tile) {
        if (!enabled() || READING_TILE.get() != tile) {
            return false;
        }
        DEFERRED_REFRESHES.put(tile, Boolean.TRUE);
        return true;
    }

    public static void refreshIfNeeded(Object tile) {
        if (!enabled() || !isMobCrusher(tile) || DEFERRED_REFRESHES.remove(tile) == null) {
            return;
        }
        try {
            invokeNoArg(tile, "updateWorkEnergyRate");
            invokeNoArg(tile, "updateWorkEnergyCapacity");
        } catch (Throwable throwable) {
            if (REFRESH_WARNING_LOGGED.compareAndSet(false, true)) {
                GPOM.LOGGER.warn("[GPOM Industrial Foregoing] Failed to refresh Mob Crusher TeslaCoreLib upgrade state after load", throwable);
            }
        }
    }

    private static boolean enabled() {
        return GpomEarlyConfig.industrialForegoingMobCrusherTeslaUpgradeLoadGuardEnabled();
    }

    private static boolean isMobCrusher(Object tile) {
        return tile != null && MOB_RELOCATOR_TILE.equals(tile.getClass().getName());
    }

    private static void invokeNoArg(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }
}
