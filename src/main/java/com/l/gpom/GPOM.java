package com.l.gpom;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.compat.multipart.GpomMultipartCompatBootstrap;
import com.l.gpom.compat.multipart.GpomMultipartSafetyWarnings;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = Reference.DEPENDENCIES
)
public final class GPOM {
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    static {
        GpomEarlyConfig.silenceGpomLoggersIfDisabled();
    }

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        GpomMultipartSafetyWarnings.register();
        GpomMultipartCompatBootstrap.preInit();
        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            LOGGER.info("{} initialized", Reference.MOD_NAME);
        }
    }

}
