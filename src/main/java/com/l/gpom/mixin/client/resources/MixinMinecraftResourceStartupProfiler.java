package com.l.gpom.mixin.client.resources;

import com.google.common.util.concurrent.ListenableFuture;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftResourceStartupProfiler {
    @Unique private long gpom$refreshResourcesStartedAt;
    @Unique private long gpom$populateSearchTreeManagerStartedAt;
    @Unique private long gpom$scheduleResourcesRefreshStartedAt;

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void gpom$beginRefreshResources(CallbackInfo ci) {
        gpom$refreshResourcesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void gpom$endRefreshResources(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.refreshResources", gpom$refreshResourcesStartedAt);
        gpom$refreshResourcesStartedAt = 0L;
    }

    @Inject(method = "populateSearchTreeManager", at = @At("HEAD"))
    private void gpom$beginPopulateSearchTreeManager(CallbackInfo ci) {
        gpom$populateSearchTreeManagerStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "populateSearchTreeManager", at = @At("RETURN"))
    private void gpom$endPopulateSearchTreeManager(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.populateSearchTreeManager", gpom$populateSearchTreeManagerStartedAt);
        gpom$populateSearchTreeManagerStartedAt = 0L;
    }

    @Inject(method = "scheduleResourcesRefresh", at = @At("HEAD"))
    private void gpom$beginScheduleResourcesRefresh(CallbackInfoReturnable<ListenableFuture<Object>> cir) {
        gpom$scheduleResourcesRefreshStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "scheduleResourcesRefresh", at = @At("RETURN"))
    private void gpom$endScheduleResourcesRefresh(CallbackInfoReturnable<ListenableFuture<Object>> cir) {
        StartupProfiler.endProbeAlways("Minecraft.scheduleResourcesRefresh", gpom$scheduleResourcesRefreshStartedAt);
        gpom$scheduleResourcesRefreshStartedAt = 0L;
    }
}
