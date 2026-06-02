package com.l.cleanroomoptimizations.mixin.client.resources;

import com.google.common.util.concurrent.ListenableFuture;
import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftResourceStartupProfiler {
    @Unique private long cleanroomoptimizations$refreshResourcesStartedAt;
    @Unique private long cleanroomoptimizations$populateSearchTreeManagerStartedAt;
    @Unique private long cleanroomoptimizations$scheduleResourcesRefreshStartedAt;

    @Inject(method = "refreshResources", at = @At("HEAD"))
    private void cleanroomoptimizations$beginRefreshResources(CallbackInfo ci) {
        cleanroomoptimizations$refreshResourcesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "refreshResources", at = @At("RETURN"))
    private void cleanroomoptimizations$endRefreshResources(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.refreshResources", cleanroomoptimizations$refreshResourcesStartedAt);
        cleanroomoptimizations$refreshResourcesStartedAt = 0L;
    }

    @Inject(method = "populateSearchTreeManager", at = @At("HEAD"))
    private void cleanroomoptimizations$beginPopulateSearchTreeManager(CallbackInfo ci) {
        cleanroomoptimizations$populateSearchTreeManagerStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "populateSearchTreeManager", at = @At("RETURN"))
    private void cleanroomoptimizations$endPopulateSearchTreeManager(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.populateSearchTreeManager", cleanroomoptimizations$populateSearchTreeManagerStartedAt);
        cleanroomoptimizations$populateSearchTreeManagerStartedAt = 0L;
    }

    @Inject(method = "scheduleResourcesRefresh", at = @At("HEAD"))
    private void cleanroomoptimizations$beginScheduleResourcesRefresh(CallbackInfoReturnable<ListenableFuture<Object>> cir) {
        cleanroomoptimizations$scheduleResourcesRefreshStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "scheduleResourcesRefresh", at = @At("RETURN"))
    private void cleanroomoptimizations$endScheduleResourcesRefresh(CallbackInfoReturnable<ListenableFuture<Object>> cir) {
        StartupProfiler.endProbeAlways("Minecraft.scheduleResourcesRefresh", cleanroomoptimizations$scheduleResourcesRefreshStartedAt);
        cleanroomoptimizations$scheduleResourcesRefreshStartedAt = 0L;
    }
}
