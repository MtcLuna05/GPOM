package com.l.gpom.mixin.thaumcraft;

import com.l.gpom.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.common.config.ModConfig", remap = false)
public abstract class MixinThaumcraftModConfigStartupProfiler {
    @Unique private static long gpom$postInitLootStartedAt;
    @Unique private static long gpom$postInitMiscStartedAt;

    @Inject(method = "postInitLoot", at = @At("HEAD"))
    private static void gpom$beginPostInitLoot(CallbackInfo ci) {
        gpom$postInitLootStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInitLoot", at = @At("RETURN"))
    private static void gpom$endPostInitLoot(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ModConfig.postInitLoot", gpom$postInitLootStartedAt);
        gpom$postInitLootStartedAt = 0L;
    }

    @Inject(method = "postInitMisc", at = @At("HEAD"))
    private static void gpom$beginPostInitMisc(CallbackInfo ci) {
        gpom$postInitMiscStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInitMisc", at = @At("RETURN"))
    private static void gpom$endPostInitMisc(CallbackInfo ci) {
        StartupProfiler.endProbe("Thaumcraft ModConfig.postInitMisc", gpom$postInitMiscStartedAt);
        gpom$postInitMiscStartedAt = 0L;
    }
}
