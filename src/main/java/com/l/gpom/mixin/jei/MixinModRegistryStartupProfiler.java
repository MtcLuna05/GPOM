package com.l.gpom.mixin.jei;

import com.l.gpom.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.startup.ModRegistry", remap = false)
public abstract class MixinModRegistryStartupProfiler {
    @Unique
    private long gpom$createRecipeRegistryStartedAt;

    @Inject(method = "createRecipeRegistry", at = @At("HEAD"))
    private void gpom$beginCreateRecipeRegistry(CallbackInfoReturnable<Object> cir) {
        gpom$createRecipeRegistryStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "createRecipeRegistry", at = @At("RETURN"))
    private void gpom$endCreateRecipeRegistry(CallbackInfoReturnable<Object> cir) {
        StartupProfiler.endProbe("HEI ModRegistry.createRecipeRegistry", gpom$createRecipeRegistryStartedAt);
        gpom$createRecipeRegistryStartedAt = 0L;
    }
}
