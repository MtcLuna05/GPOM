package com.l.cleanroomoptimizations.mixin.thaumcraft;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ConfigRecipes", remap = false)
public abstract class MixinThaumcraftConfigRecipesStartupProfiler {
    @Unique private static long cleanroomoptimizations$postAspectsStartedAt;
    @Unique private static long cleanroomoptimizations$compileGroupsStartedAt;

    @Inject(method = "postAspects", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginPostAspects(CallbackInfo ci) {
        cleanroomoptimizations$postAspectsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postAspects", at = @At("RETURN"))
    private static void cleanroomoptimizations$endPostAspects(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigRecipes.postAspects", cleanroomoptimizations$postAspectsStartedAt);
        cleanroomoptimizations$postAspectsStartedAt = 0L;
    }

    @Inject(method = "compileGroups", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginCompileGroups(CallbackInfo ci) {
        cleanroomoptimizations$compileGroupsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "compileGroups", at = @At("RETURN"))
    private static void cleanroomoptimizations$endCompileGroups(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigRecipes.compileGroups", cleanroomoptimizations$compileGroupsStartedAt);
        cleanroomoptimizations$compileGroupsStartedAt = 0L;
    }
}
