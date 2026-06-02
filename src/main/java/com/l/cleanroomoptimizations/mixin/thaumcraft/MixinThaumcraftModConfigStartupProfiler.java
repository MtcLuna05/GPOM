package com.l.cleanroomoptimizations.mixin.thaumcraft;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ModConfig", remap = false)
public abstract class MixinThaumcraftModConfigStartupProfiler {
    @Unique private static long cleanroomoptimizations$postInitLootStartedAt;
    @Unique private static long cleanroomoptimizations$postInitMiscStartedAt;

    @Inject(method = "postInitLoot", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginPostInitLoot(CallbackInfo ci) {
        cleanroomoptimizations$postInitLootStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInitLoot", at = @At("RETURN"))
    private static void cleanroomoptimizations$endPostInitLoot(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ModConfig.postInitLoot", cleanroomoptimizations$postInitLootStartedAt);
        cleanroomoptimizations$postInitLootStartedAt = 0L;
    }

    @Inject(method = "postInitMisc", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginPostInitMisc(CallbackInfo ci) {
        cleanroomoptimizations$postInitMiscStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInitMisc", at = @At("RETURN"))
    private static void cleanroomoptimizations$endPostInitMisc(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ModConfig.postInitMisc", cleanroomoptimizations$postInitMiscStartedAt);
        cleanroomoptimizations$postInitMiscStartedAt = 0L;
    }
}
