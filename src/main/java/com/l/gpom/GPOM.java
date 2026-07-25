package com.l.gpom;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.compat.actuallyadditions.ActuallyAdditionsTileEntityMappingFix;
import com.l.gpom.compat.architecturecraft.ArchitectureCraftTileEntityMappingFix;
import com.l.gpom.compat.enderio.EnderIOTileEntityMappingFix;
import com.l.gpom.compat.industrialforegoing.IndustrialForegoingTileEntityMappingFix;
import com.l.gpom.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        acceptableRemoteVersions = "*",
        dependencies = Reference.DEPENDENCIES
)
public final class GPOM {
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @SidedProxy(
            modId = Reference.MOD_ID,
            clientSide = "com.l.gpom.proxy.ClientProxy",
            serverSide = "com.l.gpom.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    static {
        GpomEarlyConfig.silenceGpomLoggersIfDisabled();
    }

    @Mod.EventHandler
    public void onConstruction(FMLConstructionEvent event) {
        invokeOptional("superfactorymanager", "com.l.gpom.compat.sfm.integration.SfmMagicalCapabilityIntegration", "registerIfNeeded");
    }

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            LOGGER.info("{} initialized on {} side", Reference.MOD_NAME, proxy.isClient() ? "client" : "server");
        }
    }

    @Mod.EventHandler
    public void onLoadComplete(FMLLoadCompleteEvent event) {
        ActuallyAdditionsTileEntityMappingFix.repairIfEnabled();
        ArchitectureCraftTileEntityMappingFix.repairIfEnabled();
        EnderIOTileEntityMappingFix.repairIfEnabled();
        IndustrialForegoingTileEntityMappingFix.repairIfEnabled();
    }

    private static void invokeOptional(String modId, String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, true, GPOM.class.getClassLoader());
            Method method = type.getMethod(methodName);
            method.invoke(null);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                LOGGER.info("[GPOM Optional] Skipping {} integration because {} is unavailable", modId, className);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException throwable) {
            LOGGER.warn("[GPOM Optional] Failed to initialize {} integration {}", modId, className, throwable);
        }
    }

}
