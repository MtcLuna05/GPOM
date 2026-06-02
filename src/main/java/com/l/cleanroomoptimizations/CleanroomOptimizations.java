package com.l.cleanroomoptimizations;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.VERSION,
        acceptedMinecraftVersions = "[1.12.2]"
)
public final class CleanroomOptimizations {
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        LOGGER.info("{} initialized", Reference.MOD_NAME);
    }
}
