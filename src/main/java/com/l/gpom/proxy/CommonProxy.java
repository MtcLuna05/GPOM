package com.l.gpom.proxy;

import com.l.gpom.compat.baubles.BaublesSideSlotsNetwork;
import com.l.gpom.compat.multipart.GpomMultipartCompatBootstrap;
import com.l.gpom.compat.multipart.GpomMultipartSafetyWarnings;
import com.l.gpom.diagnostics.Ae2PatternDiagnostics;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        BaublesSideSlotsNetwork.registerIfEnabled();
        GpomMultipartSafetyWarnings.register();
        GpomMultipartCompatBootstrap.preInit();
        Ae2PatternDiagnostics.register();
    }

    public boolean isClient() {
        return false;
    }
}
