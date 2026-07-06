package com.l.gpom.compat.architecturecraft;

import com.l.gpom.GPOM;
import com.l.gpom.compat.tileentity.TileEntityMappingRegistry;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

public final class ArchitectureCraftTileEntityMappingFix {
    private static final String MOD_ID = "architecturecraft";
    private static final String TILE_SHAPE_CLASS = "com.elytradev.architecture.common.tile.TileShape";
    private static final String TILE_SAWBENCH_CLASS = "com.elytradev.architecture.common.tile.TileSawbench";

    private static volatile boolean attempted;

    private ArchitectureCraftTileEntityMappingFix() {
    }

    public static void repairIfEnabled() {
        if (attempted
                || !GpomEarlyConfig.architectureCraftMissingTileEntityMappingRepairEnabled()
                || !Loader.isModLoaded(MOD_ID)) {
            return;
        }
        attempted = true;

        try {
            int repaired = 0;
            repaired += register(TILE_SHAPE_CLASS, new ResourceLocation(MOD_ID, "shape"));
            repaired += register(TILE_SAWBENCH_CLASS, new ResourceLocation(MOD_ID, "sawbench"));
            if (repaired > 0) {
                GPOM.LOGGER.warn("[GPOM ArchitectureCraft] Repaired {} missing TileEntity mapping(s)", repaired);
            } else if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM ArchitectureCraft] TileEntity mappings already present");
            }
        } catch (ClassNotFoundException ignored) {
            // ArchitectureCraft is optional and may be absent in other packs.
        } catch (ReflectiveOperationException | LinkageError exception) {
            GPOM.LOGGER.warn("[GPOM ArchitectureCraft] Unable to repair TileEntity mappings", exception);
        }
    }

    private static int register(String className, ResourceLocation registryName) throws ReflectiveOperationException {
        ClassLoader loader = TileEntityMappingRegistry.launchClassLoader(ArchitectureCraftTileEntityMappingFix.class);
        Class<? extends TileEntity> tileClass = Class.forName(className, true, loader).asSubclass(TileEntity.class);
        return TileEntityMappingRegistry.registerIfMissing(tileClass, registryName, "[GPOM ArchitectureCraft]") ? 1 : 0;
    }
}
