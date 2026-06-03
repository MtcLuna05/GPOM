package com.l.gpom.mixin.thaumcraft;

import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.Thaumcraft", remap = false)
public abstract class MixinThaumcraftStartupProfiler {
    @Unique
    private long gpom$postInitStartedAt;

    @Inject(method = "postInit", at = @At("HEAD"))
    private void gpom$beginPostInit(FMLPostInitializationEvent event, CallbackInfo ci) {
        gpom$postInitStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInit", at = @At("RETURN"))
    private void gpom$endPostInit(FMLPostInitializationEvent event, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Thaumcraft.postInit", gpom$postInitStartedAt);
        gpom$postInitStartedAt = 0L;
    }
}
