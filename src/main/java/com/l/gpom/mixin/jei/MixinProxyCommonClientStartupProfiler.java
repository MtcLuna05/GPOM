package com.l.gpom.mixin.jei;

import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.startup.ProxyCommonClient", remap = false)
public abstract class MixinProxyCommonClientStartupProfiler {
    @Unique
    private long gpom$loadCompleteStartedAt;

    @Inject(method = "loadComplete", at = @At("HEAD"))
    private void gpom$beginLoadComplete(FMLLoadCompleteEvent event, CallbackInfo ci) {
        gpom$loadCompleteStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadComplete", at = @At("RETURN"))
    private void gpom$endLoadComplete(FMLLoadCompleteEvent event, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("HEI ProxyCommonClient.loadComplete", gpom$loadCompleteStartedAt);
        gpom$loadCompleteStartedAt = 0L;
    }
}
