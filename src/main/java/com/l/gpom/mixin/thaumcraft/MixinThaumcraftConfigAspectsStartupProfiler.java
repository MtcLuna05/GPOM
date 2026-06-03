package com.l.gpom.mixin.thaumcraft;

import com.l.gpom.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ConfigAspects", remap = false)
public abstract class MixinThaumcraftConfigAspectsStartupProfiler {
    @Unique
    private static long gpom$postInitStartedAt;

    @Inject(method = "postInit", at = @At("HEAD"))
    private static void gpom$beginPostInit(CallbackInfo ci) {
        gpom$postInitStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInit", at = @At("RETURN"))
    private static void gpom$endPostInit(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigAspects.postInit", gpom$postInitStartedAt);
        gpom$postInitStartedAt = 0L;
    }
}
