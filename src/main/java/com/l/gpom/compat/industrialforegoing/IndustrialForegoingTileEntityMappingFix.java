package com.l.gpom.compat.industrialforegoing;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class IndustrialForegoingTileEntityMappingFix {
    private static final String BLOCK_LIST_CLASS = "com.buuz135.industrial.tile.block.CustomOrientedBlock";
    private static final String ORIENTED_BLOCK_CLASS = "net.ndrei.teslacorelib.blocks.OrientedBlock";

    private static volatile boolean attempted;
    private static volatile Object tileEntityRegistry;
    private static volatile Method getNameForObjectMethod;
    private static volatile Method getObjectMethod;

    private IndustrialForegoingTileEntityMappingFix() {
    }

    public static void repairIfEnabled() {
        if (attempted
                || !GpomEarlyConfig.industrialForegoingMissingTileEntityMappingRepairEnabled()
                || !Loader.isModLoaded("industrialforegoing")) {
            return;
        }
        attempted = true;

        try {
            int repaired = repairFromBlockList();
            if (repaired > 0) {
                GPOM.LOGGER.warn("[GPOM Industrial Foregoing] Repaired {} missing TeslaCoreLib TileEntity mapping(s)", repaired);
            } else if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Industrial Foregoing] TeslaCoreLib TileEntity mappings already present");
            }
        } catch (ClassNotFoundException ignored) {
            // Industrial Foregoing/TeslaCoreLib is optional and may be absent in other packs.
        } catch (ReflectiveOperationException | LinkageError exception) {
            GPOM.LOGGER.warn("[GPOM Industrial Foregoing] Unable to repair TeslaCoreLib TileEntity mappings", exception);
        }
    }

    private static int repairFromBlockList() throws ReflectiveOperationException {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : IndustrialForegoingTileEntityMappingFix.class.getClassLoader();
        Class<?> blockListClass = Class.forName(BLOCK_LIST_CLASS, true, loader);
        Class<?> orientedBlockClass = Class.forName(ORIENTED_BLOCK_CLASS, false, loader);
        Field blockListField = findField(blockListClass, "blockList");
        Field tileClassField = findField(orientedBlockClass, "teClass");
        Method registryNameMethod = findMethod(orientedBlockClass, "getRegistryName", "func_149739_a");

        Object rawBlockList = blockListField.get(null);
        if (!(rawBlockList instanceof List)) {
            return 0;
        }

        int repaired = 0;
        for (Object block : (List<?>) rawBlockList) {
            if (block == null || !orientedBlockClass.isInstance(block)) {
                continue;
            }
            Object rawTileClassObject = tileClassField.get(block);
            if (!(rawTileClassObject instanceof Class)) {
                continue;
            }
            Class<? extends TileEntity> tileClass = ((Class<?>) rawTileClassObject).asSubclass(TileEntity.class);
            Object rawRegistryName = registryNameMethod.invoke(block);
            if (!(rawRegistryName instanceof ResourceLocation) || registeredName(tileClass) != null) {
                continue;
            }

            ResourceLocation blockRegistryName = (ResourceLocation) rawRegistryName;
            ResourceLocation tileRegistryName = new ResourceLocation(blockRegistryName.toString() + "_tile");
            Class<? extends TileEntity> existingClass = registeredClass(tileRegistryName);
            if (existingClass != null && existingClass != tileClass) {
                GPOM.LOGGER.warn(
                        "[GPOM Industrial Foregoing] Skipping TileEntity mapping repair for {} because {} already maps to {}",
                        tileClass.getName(),
                        tileRegistryName,
                        existingClass.getName()
                );
                continue;
            }

            GameRegistry.registerTileEntity(tileClass, tileRegistryName);
            repaired++;
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Industrial Foregoing] Registered missing TileEntity mapping {} -> {}", tileRegistryName, tileClass.getName());
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

    private static Method findMethod(Class<?> owner, String... names) throws NoSuchMethodException {
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
