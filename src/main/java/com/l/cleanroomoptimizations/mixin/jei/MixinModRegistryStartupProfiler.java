package com.l.cleanroomoptimizations.mixin.jei;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
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
    private long cleanroomoptimizations$createRecipeRegistryStartedAt;

    @Inject(method = "createRecipeRegistry", at = @At("HEAD"))
    private void cleanroomoptimizations$beginCreateRecipeRegistry(CallbackInfoReturnable<Object> cir) {
        cleanroomoptimizations$createRecipeRegistryStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "createRecipeRegistry", at = @At("RETURN"))
    private void cleanroomoptimizations$endCreateRecipeRegistry(CallbackInfoReturnable<Object> cir) {
        StartupProfiler.endProbe("HEI ModRegistry.createRecipeRegistry", cleanroomoptimizations$createRecipeRegistryStartedAt);
        cleanroomoptimizations$createRecipeRegistryStartedAt = 0L;
    }
}
