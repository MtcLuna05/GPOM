package com.l.cleanroomoptimizations.mixin.thaumcraft;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
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
    private static long cleanroomoptimizations$parseAllResearchStartedAt;

    @Inject(method = "parseAllResearch", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginParseAllResearch(CallbackInfo ci) {
        cleanroomoptimizations$parseAllResearchStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "parseAllResearch", at = @At("RETURN"))
    private static void cleanroomoptimizations$endParseAllResearch(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ResearchManager.parseAllResearch", cleanroomoptimizations$parseAllResearchStartedAt);
        cleanroomoptimizations$parseAllResearchStartedAt = 0L;
    }
}
