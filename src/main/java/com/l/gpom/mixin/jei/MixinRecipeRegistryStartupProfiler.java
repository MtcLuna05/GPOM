package com.l.gpom.mixin.jei;

import com.l.gpom.profiling.StartupProfiler;
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
    private long gpom$initStartedAt;

    @Inject(method = "<init>", at = @At("HEAD"))
    private void gpom$beginInit(CallbackInfo ci) {
        gpom$initStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void gpom$endInit(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI RecipeRegistry.<init>", gpom$initStartedAt);
        gpom$initStartedAt = 0L;
    }
}
