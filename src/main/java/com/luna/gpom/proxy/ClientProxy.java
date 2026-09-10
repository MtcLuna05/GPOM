package com.luna.gpom.proxy;

import com.luna.gpom.compat.advancedrocketry.AdvancedRocketryOxygenOverlayHandoffGuard;
import com.luna.gpom.compat.betterportals.BetterPortalsClientWorldCleanup;
import com.luna.gpom.compat.jecalculation.JecPinnedCraftOverlay;
import com.luna.gpom.compat.journeymap.JourneyMapLeakCleanupEvents;
import com.luna.gpom.compat.journeymap.JourneyMapWaypointDimensionDropupEvents;
import com.luna.gpom.compat.mousetweaks.MouseTweaksDragCompat;
import com.luna.gpom.compat.thaumcraft.ThaumcraftResearchClientProbe;
import com.luna.gpom.compat.randomthings.client.RandomThingsRuneClientEvents;
import com.luna.gpom.client.ClientOnlyModeEvents;
import com.luna.gpom.client.MainMenuStartupOverlayEvents;
import com.luna.gpom.profiling.WorldLifecycleProfilerEvents;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public final class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientOnlyModeEvents.register();
        BetterPortalsClientWorldCleanup.register();
        JourneyMapLeakCleanupEvents.register();
        JourneyMapWaypointDimensionDropupEvents.register();
        JecPinnedCraftOverlay.register();
        ThaumcraftResearchClientProbe.register();
        MainMenuStartupOverlayEvents.register();
        WorldLifecycleProfilerEvents.register();
        AdvancedRocketryOxygenOverlayHandoffGuard.register();
        RandomThingsRuneClientEvents.register(event);
        MouseTweaksDragCompat.register();
    }

    @Override
    public boolean isClient() {
        return true;
    }
}
