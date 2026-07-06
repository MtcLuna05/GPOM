package com.l.gpom.compat.enderio;

import com.l.gpom.GPOM;
import com.l.gpom.compat.tileentity.TileEntityMappingRegistry;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;

public final class EnderIOTileEntityMappingFix {
    private static final String[] ENDERIO_TILE_ENUMS = {
            "crazypants.enderio.conduits.init.ConduitTileEntity",
            "crazypants.enderio.machines.init.MachineTileEntity",
            "crazypants.enderio.invpanel.init.InvpanelTileEntity",
            "crazypants.enderio.powertools.init.PowerToolTileEntity"
    };

    private static volatile boolean attempted;

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
        ClassLoader loader = TileEntityMappingRegistry.launchClassLoader(EnderIOTileEntityMappingFix.class);
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
            if (TileEntityMappingRegistry.registerIfMissing(tileClass, registryName, "[GPOM EnderIO]")) {
                repaired++;
            }
        }
        return repaired;
    }

}
