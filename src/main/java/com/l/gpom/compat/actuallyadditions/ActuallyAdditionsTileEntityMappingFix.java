package com.l.gpom.compat.actuallyadditions;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ActuallyAdditionsTileEntityMappingFix {
    private static final String TILE_BASE_CLASS = "de.ellpeck.actuallyadditions.mod.tile.TileEntityBase";
    private static final String TILE_PACKAGE = "de.ellpeck.actuallyadditions.mod.tile.";
    private static final String[] TILE_CLASSES = {
            "TileEntityCompost",
            "TileEntityFeeder",
            "TileEntityGiantChest",
            "TileEntityGiantChestMedium",
            "TileEntityGiantChestLarge",
            "TileEntityGrinder",
            "TileEntityFurnaceDouble",
            "TileEntityInputter",
            "TileEntityFishingNet",
            "TileEntityFurnaceSolar",
            "TileEntityHeatCollector",
            "TileEntityItemRepairer",
            "TileEntityBreaker",
            "TileEntityDropper",
            "TileEntityInputterAdvanced",
            "TileEntityPlacer",
            "TileEntityGrinderDouble",
            "TileEntityCanolaPress",
            "TileEntityFermentingBarrel",
            "TileEntityOilGenerator",
            "TileEntityCoalGenerator",
            "TileEntityPhantomItemface",
            "TileEntityPhantomLiquiface",
            "TileEntityPhantomEnergyface",
            "TileEntityPlayerInterface",
            "TileEntityPhantomPlacer",
            "TileEntityPhantomBreaker",
            "TileEntityFluidCollector",
            "TileEntityFluidPlacer",
            "TileEntityLavaFactoryController",
            "TileEntityCoffeeMachine",
            "TileEntityPhantomBooster",
            "TileEntityEnergizer",
            "TileEntityEnervator",
            "TileEntityXPSolidifier",
            "TileEntitySmileyCloud",
            "TileEntityLeafGenerator",
            "TileEntityDirectionalBreaker",
            "TileEntityRangedCollector",
            "TileEntityAtomicReconstructor",
            "TileEntityMiner",
            "TileEntityFireworkBox",
            "TileEntityPhantomRedstoneface",
            "TileEntityLaserRelayItem",
            "TileEntityLaserRelayEnergy",
            "TileEntityLaserRelayEnergyAdvanced",
            "TileEntityLaserRelayEnergyExtreme",
            "TileEntityLaserRelayItemWhitelist",
            "TileEntityItemViewer",
            "TileEntityDisplayStand",
            "TileEntityShockSuppressor",
            "TileEntityEmpowerer",
            "TileEntityLaserRelayFluids",
            "TileEntityBioReactor",
            "TileEntityFarmer",
            "TileEntityItemViewerHopping",
            "TileEntityBatteryBox"
    };

    private static volatile boolean attempted;
    private static volatile Object tileEntityRegistry;
    private static volatile Method getNameForObjectMethod;
    private static volatile Method getObjectMethod;

    private ActuallyAdditionsTileEntityMappingFix() {
    }

    public static void repairIfEnabled() {
        if (attempted
                || !GpomEarlyConfig.actuallyAdditionsMissingTileEntityMappingRepairEnabled()
                || !Loader.isModLoaded("actuallyadditions")) {
            return;
        }
        attempted = true;

        try {
            int repaired = repairTiles();
            if (repaired > 0) {
                GPOM.LOGGER.warn("[GPOM Actually Additions] Repaired {} missing TileEntity mapping(s)", repaired);
            } else if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Actually Additions] TileEntity mappings already present");
            }
        } catch (ClassNotFoundException ignored) {
            // Actually Additions is optional and may be absent in other packs.
        } catch (ReflectiveOperationException | LinkageError exception) {
            GPOM.LOGGER.warn("[GPOM Actually Additions] Unable to repair TileEntity mappings", exception);
        }
    }

    private static int repairTiles() throws ReflectiveOperationException {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : ActuallyAdditionsTileEntityMappingFix.class.getClassLoader();
        Class<?> tileBaseClass = Class.forName(TILE_BASE_CLASS, false, loader);
        Field nameField = findField(tileBaseClass, "name");

        int repaired = 0;
        for (String simpleName : TILE_CLASSES) {
            Class<?> rawTileClass = Class.forName(TILE_PACKAGE + simpleName, true, loader);
            if (!tileBaseClass.isAssignableFrom(rawTileClass)) {
                continue;
            }
            Class<? extends TileEntity> tileClass = rawTileClass.asSubclass(TileEntity.class);
            if (registeredName(tileClass) != null) {
                continue;
            }

            Object tileInstance = rawTileClass.getConstructor().newInstance();
            Object rawName = nameField.get(tileInstance);
            if (!(rawName instanceof String) || ((String) rawName).isEmpty()) {
                continue;
            }

            ResourceLocation registryName = new ResourceLocation("actuallyadditions", (String) rawName);
            Class<? extends TileEntity> existingClass = registeredClass(registryName);
            if (existingClass != null && existingClass != tileClass) {
                GPOM.LOGGER.warn(
                        "[GPOM Actually Additions] Skipping TileEntity mapping repair for {} because {} already maps to {}",
                        tileClass.getName(),
                        registryName,
                        existingClass.getName()
                );
                continue;
            }

            GameRegistry.registerTileEntity(tileClass, registryName);
            repaired++;
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Actually Additions] Registered missing TileEntity mapping {} -> {}", registryName, tileClass.getName());
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
