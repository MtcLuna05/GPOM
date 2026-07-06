package com.l.gpom.compat.actuallyadditions;

import com.l.gpom.GPOM;
import com.l.gpom.compat.tileentity.TileEntityMappingRegistry;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;

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
        ClassLoader loader = TileEntityMappingRegistry.launchClassLoader(ActuallyAdditionsTileEntityMappingFix.class);
        Class<?> tileBaseClass = Class.forName(TILE_BASE_CLASS, false, loader);
        Field nameField = TileEntityMappingRegistry.findField(tileBaseClass, "name");

        int repaired = 0;
        for (String simpleName : TILE_CLASSES) {
            Class<?> rawTileClass = Class.forName(TILE_PACKAGE + simpleName, true, loader);
            if (!tileBaseClass.isAssignableFrom(rawTileClass)) {
                continue;
            }
            Class<? extends TileEntity> tileClass = rawTileClass.asSubclass(TileEntity.class);
            if (TileEntityMappingRegistry.registeredName(tileClass) != null) {
                continue;
            }

            Object tileInstance = rawTileClass.getConstructor().newInstance();
            Object rawName = nameField.get(tileInstance);
            if (!(rawName instanceof String) || ((String) rawName).isEmpty()) {
                continue;
            }

            ResourceLocation registryName = new ResourceLocation("actuallyadditions", (String) rawName);
            if (TileEntityMappingRegistry.registerIfMissing(tileClass, registryName, "[GPOM Actually Additions]")) {
                repaired++;
            }
        }
        return repaired;
    }

}
