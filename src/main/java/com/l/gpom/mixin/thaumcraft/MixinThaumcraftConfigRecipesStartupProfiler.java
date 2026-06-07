package com.l.gpom.mixin.thaumcraft;

import com.l.gpom.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ConfigRecipes", remap = false)
public abstract class MixinThaumcraftConfigRecipesStartupProfiler {
    @Unique private static long gpom$postAspectsStartedAt;
    @Unique private static long gpom$compileGroupsStartedAt;

    @Inject(method = "postAspects", at = @At("HEAD"))
    private static void gpom$beginPostAspects(CallbackInfo ci) {
        gpom$postAspectsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postAspects", at = @At("RETURN"))
    private static void gpom$endPostAspects(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigRecipes.postAspects", gpom$postAspectsStartedAt);
        gpom$postAspectsStartedAt = 0L;
    }

    @Inject(method = "compileGroups", at = @At("HEAD"))
    private static void gpom$beginCompileGroups(CallbackInfo ci) {
        gpom$compileGroupsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "compileGroups", at = @At("RETURN"))
    private static void gpom$endCompileGroups(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigRecipes.compileGroups", gpom$compileGroupsStartedAt);
        gpom$compileGroupsStartedAt = 0L;
    }
}
