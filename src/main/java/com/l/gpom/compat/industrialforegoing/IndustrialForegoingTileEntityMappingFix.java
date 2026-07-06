package com.l.gpom.compat.industrialforegoing;

import com.l.gpom.GPOM;
import com.l.gpom.compat.tileentity.TileEntityMappingRegistry;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class IndustrialForegoingTileEntityMappingFix {
    private static final String BLOCK_LIST_CLASS = "com.buuz135.industrial.tile.block.CustomOrientedBlock";
    private static final String ORIENTED_BLOCK_CLASS = "net.ndrei.teslacorelib.blocks.OrientedBlock";

    private static volatile boolean attempted;

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
        ClassLoader loader = TileEntityMappingRegistry.launchClassLoader(IndustrialForegoingTileEntityMappingFix.class);
        Class<?> blockListClass = Class.forName(BLOCK_LIST_CLASS, true, loader);
        Class<?> orientedBlockClass = Class.forName(ORIENTED_BLOCK_CLASS, false, loader);
        Field blockListField = TileEntityMappingRegistry.findField(blockListClass, "blockList");
        Field tileClassField = TileEntityMappingRegistry.findField(orientedBlockClass, "teClass");
        Method registryNameMethod = TileEntityMappingRegistry.findMethod(orientedBlockClass, "getRegistryName", "func_149739_a");

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
            if (!(rawRegistryName instanceof ResourceLocation) || TileEntityMappingRegistry.registeredName(tileClass) != null) {
                continue;
            }

            ResourceLocation blockRegistryName = (ResourceLocation) rawRegistryName;
            ResourceLocation tileRegistryName = new ResourceLocation(blockRegistryName.toString() + "_tile");
            if (TileEntityMappingRegistry.registerIfMissing(tileClass, tileRegistryName, "[GPOM Industrial Foregoing]")) {
                repaired++;
            }
        }
        return repaired;
    }

}
