package com.l.gpom.compat.enderio;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class EnderIOTileEntityMappingFix {
    private static final String[] ENDERIO_TILE_ENUMS = {
            "crazypants.enderio.conduits.init.ConduitTileEntity",
            "crazypants.enderio.machines.init.MachineTileEntity",
            "crazypants.enderio.invpanel.init.InvpanelTileEntity",
            "crazypants.enderio.powertools.init.PowerToolTileEntity"
    };

    private static volatile boolean attempted;
    private static volatile Object tileEntityRegistry;
    private static volatile Method getNameForObjectMethod;
    private static volatile Method getObjectMethod;

    private EnderIOTileEntityMappingFix() {
    }

    public static void repairIfEnabled() {
        if (attempted || !GpomEarlyConfig.enderIOMissingTileEntityMappingRepairEnabled() || !Loader.isModLoaded("enderio")) {
            return;
        }
        attempted = true;

        int repaired = 0;
        int failures = 0;
        for (String enumName : ENDERIO_TILE_ENUMS) {
            try {
                repaired += repairEnum(enumName);
            } catch (ClassNotFoundException ignored) {
                // EnderIO modules are split across optional mod ids; absent enums are expected.
            } catch (ReflectiveOperationException | LinkageError exception) {
                failures++;
                GPOM.LOGGER.warn("[GPOM EnderIO] Unable to inspect {} for missing TileEntity mappings", enumName, exception);
            }
        }

        if (repaired > 0) {
            GPOM.LOGGER.warn("[GPOM EnderIO] Repaired {} missing TileEntity mapping(s) after EnderIO load", repaired);
        } else if (failures == 0 && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            GPOM.LOGGER.info("[GPOM EnderIO] TileEntity mappings already present");
        }
    }

    private static int repairEnum(String enumName) throws ReflectiveOperationException {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : EnderIOTileEntityMappingFix.class.getClassLoader();
        Class<?> enumClass = Class.forName(enumName, true, loader);
        Method valuesMethod = enumClass.getMethod("values");
        Method tileClassMethod = enumClass.getMethod("getTileEntityClass");
        Method registryNameMethod = enumClass.getMethod("getRegistryName");
        Object[] values = (Object[]) valuesMethod.invoke(null);

        int repaired = 0;
        for (Object value : values) {
            Object rawTileClassObject = tileClassMethod.invoke(value);
            if (!(rawTileClassObject instanceof Class)) {
                continue;
            }
            Class<?> rawTileClass = (Class<?>) rawTileClassObject;
            Class<? extends TileEntity> tileClass = rawTileClass.asSubclass(TileEntity.class);
            ResourceLocation registryName = (ResourceLocation) registryNameMethod.invoke(value);
            if (tileClass == null || registryName == null || registeredName(tileClass) != null) {
                continue;
            }

            Class<? extends TileEntity> existingClass = registeredClass(registryName);
            if (existingClass != null && existingClass != tileClass) {
                GPOM.LOGGER.warn(
                        "[GPOM EnderIO] Skipping TileEntity mapping repair for {} because {} already maps to {}",
                        tileClass.getName(),
                        registryName,
                        existingClass.getName()
                );
                continue;
            }

            GameRegistry.registerTileEntity(tileClass, registryName);
            repaired++;
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM EnderIO] Registered missing TileEntity mapping {} -> {}", registryName, tileClass.getName());
            }
        }
        return repaired;
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
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
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
}
