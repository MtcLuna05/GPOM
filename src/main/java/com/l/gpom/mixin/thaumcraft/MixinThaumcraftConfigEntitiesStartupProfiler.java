package com.l.gpom.mixin.thaumcraft;

import com.l.gpom.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ConfigEntities", remap = false)
public abstract class MixinThaumcraftConfigEntitiesStartupProfiler {
    @Unique
    private static long gpom$postInitEntitySpawnsStartedAt;

    @Inject(method = "postInitEntitySpawns", at = @At("HEAD"))
    private static void gpom$beginPostInitEntitySpawns(CallbackInfo ci) {
        gpom$postInitEntitySpawnsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInitEntitySpawns", at = @At("RETURN"))
    private static void gpom$endPostInitEntitySpawns(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigEntities.postInitEntitySpawns", gpom$postInitEntitySpawnsStartedAt);
        gpom$postInitEntitySpawnsStartedAt = 0L;
    }
}
