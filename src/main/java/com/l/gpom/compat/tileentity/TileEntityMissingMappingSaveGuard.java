package com.l.gpom.compat.tileentity;

import com.l.gpom.GPOM;
import com.l.gpom.compat.actuallyadditions.ActuallyAdditionsTileEntityMappingFix;
import com.l.gpom.compat.enderio.EnderIOTileEntityMappingFix;
import com.l.gpom.compat.industrialforegoing.IndustrialForegoingTileEntityMappingFix;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TileEntityMissingMappingSaveGuard {
    private static final String ACTUALLY_ADDITIONS_TILE_BASE = "de.ellpeck.actuallyadditions.mod.tile.TileEntityBase";
    private static final Set<String> LOGGED_FALLBACKS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private static volatile Object tileEntityRegistry;
    private static volatile Method getNameForObjectMethod;
    private static volatile Method getObjectMethod;

    private TileEntityMissingMappingSaveGuard() {
    }

    public static boolean writeFallbackIdIfNeeded(TileEntity tile, NBTTagCompound compound) {
        if (!GpomEarlyConfig.tileEntityMissingMappingSaveGuardEnabled() || tile == null || compound == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends TileEntity> tileClass = (Class<? extends TileEntity>) tile.getClass();
        try {
            if (registeredName(tileClass) != null) {
                return false;
            }
        } catch (ReflectiveOperationException exception) {
            logFailureOnce(tileClass, "inspect vanilla TileEntity registry", exception);
            return false;
        }

        runKnownMappingRepairs();

        try {
            if (registeredName(tileClass) != null) {
                return false;
            }
        } catch (ReflectiveOperationException exception) {
            logFailureOnce(tileClass, "inspect repaired vanilla TileEntity registry", exception);
            return false;
        }

        ResourceLocation fallbackId = knownFallbackId(tile);
        if (fallbackId == null) {
            logFailureOnce(tileClass, "derive a safe fallback id", null);
            return false;
        }

        if (!GpomEarlyConfig.tileEntityMissingMappingSaveGuardWriteKnownFallbackIds()) {
            logFailureOnce(tileClass, "write fallback id because the fallback writer is disabled", null);
            return false;
        }

        try {
            Class<? extends TileEntity> existingClass = registeredClass(fallbackId);
            if (existingClass != null && existingClass != tileClass) {
                logFailureOnce(tileClass, "write fallback id " + fallbackId + " because it already maps to " + existingClass.getName(), null);
                return false;
            }
            if (existingClass == null) {
                try {
                    GameRegistry.registerTileEntity(tileClass, fallbackId);
                    if (registeredName(tileClass) != null) {
                        return false;
                    }
                } catch (RuntimeException exception) {
                    logFailureOnce(tileClass, "register fallback id " + fallbackId, exception);
                }
            }
        } catch (ReflectiveOperationException exception) {
            logFailureOnce(tileClass, "inspect fallback id " + fallbackId, exception);
            return false;
        }

        compound.setString("id", fallbackId.toString());
        if (LOGGED_FALLBACKS.add(tileClass.getName())) {
            GPOM.LOGGER.warn(
                    "[GPOM TileEntity SaveGuard] Wrote fallback id {} for missing TileEntity mapping {}; save data is preserved but the mapping source should still be fixed",
                    fallbackId,
                    tileClass.getName()
            );
        }
        return true;
    }

    private static void runKnownMappingRepairs() {
        ActuallyAdditionsTileEntityMappingFix.repairIfEnabled();
        EnderIOTileEntityMappingFix.repairIfEnabled();
        IndustrialForegoingTileEntityMappingFix.repairIfEnabled();
    }

    private static ResourceLocation knownFallbackId(TileEntity tile) {
        ResourceLocation actuallyAdditionsId = actuallyAdditionsFallbackId(tile);
        if (actuallyAdditionsId != null) {
            return actuallyAdditionsId;
        }
        return null;
    }

    private static ResourceLocation actuallyAdditionsFallbackId(TileEntity tile) {
        try {
            Class<?> tileClass = tile.getClass();
            if (!isInstanceOf(tileClass, ACTUALLY_ADDITIONS_TILE_BASE)) {
                return null;
            }
            Field nameField = findField(tileClass, "name");
            Object rawName = nameField.get(tile);
            if (!(rawName instanceof String) || ((String) rawName).isEmpty()) {
                return null;
            }
            return new ResourceLocation("actuallyadditions", (String) rawName);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static ResourceLocation registeredName(Class<? extends TileEntity> tileClass) throws ReflectiveOperationException {
        Method method = getNameForObjectMethod;
        if (method == null) {
            method = findRegistryMethod("getNameForObject", "func_177774_c");
            getNameForObjectMethod = method;
        }
        Object result = method.invoke(tileEntityRegistry(), tileClass);
        return result instanceof ResourceLocation ? (ResourceLocation) result : null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends TileEntity> registeredClass(ResourceLocation registryName) throws ReflectiveOperationException {
        Method method = getObjectMethod;
        if (method == null) {
            method = findRegistryMethod("getObject", "func_82594_a");
            getObjectMethod = method;
        }
        Object result = method.invoke(tileEntityRegistry(), registryName);
        if (!(result instanceof Class)) {
            return null;
        }
        return ((Class<?>) result).asSubclass(TileEntity.class);
    }

    private static Object tileEntityRegistry() throws ReflectiveOperationException {
        Object registry = tileEntityRegistry;
        if (registry != null) {
            return registry;
        }
        Field field = findField(TileEntity.class, "REGISTRY", "field_190562_f");
        registry = field.get(null);
        tileEntityRegistry = registry;
        return registry;
    }

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        Class<?> current = owner;
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
        throw new NoSuchFieldException(owner.getName() + "." + names[0]);
    }

    private static Method findRegistryMethod(String mcpName, String srgName) throws ReflectiveOperationException {
        Object registry = tileEntityRegistry();
        Class<?> owner = registry.getClass();
        while (owner != null) {
            for (Method method : owner.getDeclaredMethods()) {
                String name = method.getName();
                if ((mcpName.equals(name) || srgName.equals(name)) && method.getParameterTypes().length == 1) {
                    method.setAccessible(true);
                    return method;
                }
            }
            owner = owner.getSuperclass();
        }
        throw new NoSuchMethodException(registry.getClass().getName() + "." + mcpName);
    }

    private static boolean isInstanceOf(Class<?> type, String expectedName) {
        Class<?> current = type;
        while (current != null) {
            if (expectedName.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static void logFailureOnce(Class<? extends TileEntity> tileClass, String action, Throwable throwable) {
        if (!LOGGED_FAILURES.add(tileClass.getName() + ":" + action)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[GPOM TileEntity SaveGuard] Could not {} for missing TileEntity mapping {}", action, tileClass.getName());
        } else {
            GPOM.LOGGER.warn("[GPOM TileEntity SaveGuard] Could not {} for missing TileEntity mapping {}", action, tileClass.getName(), throwable);
        }
    }
}
