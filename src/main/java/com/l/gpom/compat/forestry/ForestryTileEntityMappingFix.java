package com.l.gpom.compat.forestry;

import com.l.gpom.GPOM;
import com.l.gpom.compat.tileentity.TileEntityMappingRegistry;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

public final class ForestryTileEntityMappingFix {
    private static final String TILE_APIARIST_CHEST = "forestry.apiculture.tiles.TileApiaristChest";
    private static final ResourceLocation APIARIST_CHEST_ID = new ResourceLocation("forestry", "api_chest");

    private static volatile boolean attempted;

    private ForestryTileEntityMappingFix() {
    }

    public static void repairIfEnabled() {
        if (attempted || !GpomEarlyConfig.forestryMissingTileEntityMappingRepairEnabled() || !Loader.isModLoaded("forestry")) {
            return;
        }
        attempted = true;

        try {
            ClassLoader loader = TileEntityMappingRegistry.launchClassLoader(ForestryTileEntityMappingFix.class);
            Class<? extends TileEntity> tileClass = Class.forName(TILE_APIARIST_CHEST, true, loader).asSubclass(TileEntity.class);
            if (TileEntityMappingRegistry.registerIfMissing(tileClass, APIARIST_CHEST_ID, "[GPOM Forestry]")) {
                GPOM.LOGGER.warn("[GPOM Forestry] Repaired missing Apiarist Chest TileEntity mapping");
            } else if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Forestry] Apiarist Chest TileEntity mapping already present");
            }
        } catch (ClassNotFoundException ignored) {
            // Forestry apiculture is optional in other packs or may be module-filtered by custom builds.
        } catch (ReflectiveOperationException | LinkageError exception) {
            GPOM.LOGGER.warn("[GPOM Forestry] Unable to repair Apiarist Chest TileEntity mapping", exception);
        }
    }
}
