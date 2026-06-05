package com.l.gpom.mixin.client.resources;

import com.l.gpom.client.EarlySplashWindow;
import com.google.common.util.concurrent.ListenableFuture;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftResourceStartupProfiler {
    @Unique private long gpom$runStartedAt;
    @Unique private long gpom$initStartedAt;
    @Unique private long gpom$createDisplayStartedAt;
    @Unique private long gpom$drawSplashScreenStartedAt;
    @Unique private long gpom$refreshResourcesStartedAt;
    @Unique private long gpom$populateSearchTreeManagerStartedAt;
    @Unique private long gpom$scheduleResourcesRefreshStartedAt;

    @Inject(method = "run", at = @At("HEAD"))
    private void gpom$beginRun(CallbackInfo ci) {
        EarlySplashWindow.setStatus("Minecraft run loop");
        StartupProfiler.markBoot("Minecraft.run entered");
        gpom$runStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "run", at = @At("RETURN"))
    private void gpom$endRun(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.run", gpom$runStartedAt);
        gpom$runStartedAt = 0L;
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void gpom$beginInit(CallbackInfo ci) {
        EarlySplashWindow.setStatus("Minecraft init");
        StartupProfiler.markBoot("Minecraft.init entered");
        gpom$initStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void gpom$endInit(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.init", gpom$initStartedAt);
        gpom$initStartedAt = 0L;
    }

    @Inject(method = "createDisplay", at = @At("HEAD"))
    private void gpom$beginCreateDisplay(CallbackInfo ci) {
        EarlySplashWindow.close("Minecraft display starting");
        StartupProfiler.markBoot("Minecraft.createDisplay entered");
        gpom$createDisplayStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "createDisplay", at = @At("RETURN"))
    private void gpom$endCreateDisplay(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.createDisplay", gpom$createDisplayStartedAt);
        gpom$createDisplayStartedAt = 0L;
    }

    @Inject(method = "drawSplashScreen", at = @At("HEAD"))
    private void gpom$beginDrawSplashScreen(TextureManager textureManager, CallbackInfo ci) {
        EarlySplashWindow.close("Minecraft Forge splash starting");
        StartupProfiler.markBoot("Minecraft.drawSplashScreen entered");
        gpom$drawSplashScreenStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "drawSplashScreen", at = @At("RETURN"))
    private void gpom$endDrawSplashScreen(TextureManager textureManager, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("Minecraft.drawSplashScreen", gpom$drawSplashScreenStartedAt);
        gpom$drawSplashScreenStartedAt = 0L;
    }

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
