package com.l.cleanroomoptimizations.mixin.thaumcraft;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "thaumcraft.proxies.CommonProxy", remap = false)
public abstract class MixinThaumcraftCommonProxyStartupProfiler {
    @Unique
    private long cleanroomoptimizations$postInitStartedAt;

    @Inject(method = "postInit", at = @At("HEAD"))
    private void cleanroomoptimizations$beginPostInit(FMLPostInitializationEvent event, CallbackInfo ci) {
        cleanroomoptimizations$postInitStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "postInit", at = @At("RETURN"))
    private void cleanroomoptimizations$endPostInit(FMLPostInitializationEvent event, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Thaumcraft CommonProxy.postInit", cleanroomoptimizations$postInitStartedAt);
        cleanroomoptimizations$postInitStartedAt = 0L;
    }
}
