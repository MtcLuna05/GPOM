package com.l.gpom.proxy;

import com.l.gpom.compat.betterportals.BetterPortalsClientWorldCleanup;
import com.l.gpom.compat.jecalculation.JecPinnedCraftOverlay;
import com.l.gpom.compat.journeymap.JourneyMapLeakCleanupEvents;
import com.l.gpom.compat.journeymap.JourneyMapWaypointDimensionDropupEvents;
import com.l.gpom.compat.thaumcraft.ThaumcraftResearchClientProbe;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        BetterPortalsClientWorldCleanup.register();
        JourneyMapLeakCleanupEvents.register();
        JourneyMapWaypointDimensionDropupEvents.register();
        JecPinnedCraftOverlay.register();
        ThaumcraftResearchClientProbe.register();
    }

    @Override
    public boolean isClient() {
        return true;
    }
}
