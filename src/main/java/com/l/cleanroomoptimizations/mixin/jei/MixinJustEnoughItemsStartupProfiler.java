package com.l.cleanroomoptimizations.mixin.jei;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.JustEnoughItems", remap = false)
public abstract class MixinJustEnoughItemsStartupProfiler {
    @Unique
    private long cleanroomoptimizations$loadCompleteStartedAt;

    @Inject(method = "loadComplete", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadComplete(FMLLoadCompleteEvent event, CallbackInfo ci) {
        cleanroomoptimizations$loadCompleteStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadComplete", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadComplete(FMLLoadCompleteEvent event, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("HEI JustEnoughItems.loadComplete", cleanroomoptimizations$loadCompleteStartedAt);
        cleanroomoptimizations$loadCompleteStartedAt = 0L;
    }
}
