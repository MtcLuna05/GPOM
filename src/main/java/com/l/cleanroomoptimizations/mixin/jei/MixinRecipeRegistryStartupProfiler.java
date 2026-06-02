package com.l.cleanroomoptimizations.mixin.jei;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.recipes.RecipeRegistry", remap = false)
public abstract class MixinRecipeRegistryStartupProfiler {
    @Unique
    private long cleanroomoptimizations$initStartedAt;

    @Inject(method = "<init>", at = @At("HEAD"))
    private void cleanroomoptimizations$beginInit(CallbackInfo ci) {
        cleanroomoptimizations$initStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cleanroomoptimizations$endInit(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI RecipeRegistry.<init>", cleanroomoptimizations$initStartedAt);
        cleanroomoptimizations$initStartedAt = 0L;
    }
}
