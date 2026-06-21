package com.l.gpom.proxy;

import com.l.gpom.compat.baubles.BaublesSideSlotsNetwork;
import com.l.gpom.compat.bloodmagic.BloodMagicWorldMapRecoveryEvents;
import com.l.gpom.compat.hei.HeiQuickCraftNetwork;
import com.l.gpom.compat.thaumcraft.ThaumcraftResearchRecoveryEvents;
import com.l.gpom.diagnostics.Ae2PatternDiagnostics;
import com.l.gpom.optimization.MissingMappingRepairs;
import com.l.gpom.profiling.RuntimeSinkProfilerEvents;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        BaublesSideSlotsNetwork.registerIfEnabled();
        HeiQuickCraftNetwork.registerIfNeeded();
        MissingMappingRepairs.register();
        BloodMagicWorldMapRecoveryEvents.register();
        ThaumcraftResearchRecoveryEvents.register();
        Ae2PatternDiagnostics.register();
        RuntimeSinkProfilerEvents.register();
    }

    public boolean isClient() {
        return false;
    }
}
