package com.l.gpom.compat.tileentity;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class TileEntityMappingRegistry {
    private static volatile Object tileEntityRegistry;
    private static volatile Method getNameForObjectMethod;
    private static volatile Method getObjectMethod;

    private TileEntityMappingRegistry() {
    }

    public static ClassLoader launchClassLoader(Class<?> fallbackOwner) {
        return Launch.classLoader != null ? Launch.classLoader : fallbackOwner.getClassLoader();
    }

    public static ResourceLocation registeredName(Class<? extends TileEntity> tileClass) throws ReflectiveOperationException {
        Method method = getNameForObjectMethod;
        if (method == null) {
            method = findRegistryMethod("getNameForObject", "func_177774_c");
            getNameForObjectMethod = method;
        }
        Object result = method.invoke(tileEntityRegistry(), tileClass);
        return result instanceof ResourceLocation ? (ResourceLocation) result : null;
    }

    @SuppressWarnings("unchecked")
    public static Class<? extends TileEntity> registeredClass(ResourceLocation registryName) throws ReflectiveOperationException {
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

    public static void registerTileEntity(Class<? extends TileEntity> tileClass, ResourceLocation registryName) {
        GameRegistry.registerTileEntity(tileClass, registryName);
    }

    public static boolean registerIfMissing(Class<? extends TileEntity> tileClass,
                                            ResourceLocation registryName,
                                            String logPrefix) throws ReflectiveOperationException {
        if (tileClass == null || registryName == null || registeredName(tileClass) != null) {
            return false;
        }

        Class<? extends TileEntity> existingClass = registeredClass(registryName);
        if (existingClass != null && existingClass != tileClass) {
            GPOM.LOGGER.warn(
                    "{} Skipping TileEntity mapping repair for {} because {} already maps to {}",
                    logPrefix,
                    tileClass.getName(),
                    registryName,
                    existingClass.getName()
            );
            return false;
        }

        registerTileEntity(tileClass, registryName);
        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            GPOM.LOGGER.info("{} Registered missing TileEntity mapping {} -> {}", logPrefix, registryName, tileClass.getName());
        }
        return true;
    }

    public static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
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

    public static Method findMethod(Class<?> owner, String... names) throws NoSuchMethodException {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(owner.getName() + "." + names[0]);
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
