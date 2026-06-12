package com.l.gpom.proxy;

import com.l.gpom.compat.journeymap.JourneyMapLeakCleanupEvents;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        JourneyMapLeakCleanupEvents.register();
    }

    @Override
    public boolean isClient() {
        return true;
    }
}
