package com.l.gpom.mixin.thaumcraft;

import com.l.gpom.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.lib.research.ResearchManager", remap = false)
public abstract class MixinThaumcraftResearchManagerStartupProfiler {
    @Unique
    private static long gpom$parseAllResearchStartedAt;

    @Inject(method = "parseAllResearch", at = @At("HEAD"))
    private static void gpom$beginParseAllResearch(CallbackInfo ci) {
        gpom$parseAllResearchStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "parseAllResearch", at = @At("RETURN"))
    private static void gpom$endParseAllResearch(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ResearchManager.parseAllResearch", gpom$parseAllResearchStartedAt);
        gpom$parseAllResearchStartedAt = 0L;
    }
}
