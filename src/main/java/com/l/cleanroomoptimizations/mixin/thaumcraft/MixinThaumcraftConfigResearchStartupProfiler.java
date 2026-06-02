package com.l.cleanroomoptimizations.mixin.thaumcraft;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ConfigResearch", remap = false)
public abstract class MixinThaumcraftConfigResearchStartupProfiler {
    @Unique
    private static long cleanroomoptimizations$postInitStartedAt;

    @Inject(method = "postInit", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginPostInit(CallbackInfo ci) {
        cleanroomoptimizations$postInitStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInit", at = @At("RETURN"))
    private static void cleanroomoptimizations$endPostInit(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigResearch.postInit", cleanroomoptimizations$postInitStartedAt);
        cleanroomoptimizations$postInitStartedAt = 0L;
    }
}
