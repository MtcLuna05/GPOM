package com.l.cleanroomoptimizations.mixin.thaumcraft;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
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
    private static long cleanroomoptimizations$postInitEntitySpawnsStartedAt;

    @Inject(method = "postInitEntitySpawns", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginPostInitEntitySpawns(CallbackInfo ci) {
        cleanroomoptimizations$postInitEntitySpawnsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInitEntitySpawns", at = @At("RETURN"))
    private static void cleanroomoptimizations$endPostInitEntitySpawns(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ConfigEntities.postInitEntitySpawns", cleanroomoptimizations$postInitEntitySpawnsStartedAt);
        cleanroomoptimizations$postInitEntitySpawnsStartedAt = 0L;
    }
}
