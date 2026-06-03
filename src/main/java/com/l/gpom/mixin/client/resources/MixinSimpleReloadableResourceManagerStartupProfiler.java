package com.l.gpom.mixin.client.resources;

import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(SimpleReloadableResourceManager.class)
public abstract class MixinSimpleReloadableResourceManagerStartupProfiler {
    @Unique private long gpom$reloadResourcesStartedAt;
    @Unique private long gpom$reloadResourcePackStartedAt;
    @Unique private long gpom$notifyReloadListenersStartedAt;
    @Unique private long gpom$registerReloadListenerStartedAt;

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void gpom$beginReloadResources(List<IResourcePack> resourcePacks, CallbackInfo ci) {
        StartupProfiler.beginResourceReload(resourcePacks.size());
        gpom$reloadResourcesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void gpom$endReloadResources(List<IResourcePack> resourcePacks, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ResourceManager.reloadResources packs=" + resourcePacks.size(), gpom$reloadResourcesStartedAt);
        StartupProfiler.endResourceReload();
        gpom$reloadResourcesStartedAt = 0L;
    }

    @Inject(method = "reloadResourcePack", at = @At("HEAD"))
    private void gpom$beginReloadResourcePack(IResourcePack resourcePack, CallbackInfo ci) {
        gpom$reloadResourcePackStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "reloadResourcePack", at = @At("RETURN"))
    private void gpom$endReloadResourcePack(IResourcePack resourcePack, CallbackInfo ci) {
        long elapsed = System.nanoTime() - gpom$reloadResourcePackStartedAt;
        StartupProfiler.recordResourcePackReload(resourcePack.getPackName(), elapsed);
        StartupProfiler.endProbe("ResourceManager.reloadResourcePack " + resourcePack.getPackName(), gpom$reloadResourcePackStartedAt);
        gpom$reloadResourcePackStartedAt = 0L;
    }

    @Inject(method = "notifyReloadListeners", at = @At("HEAD"))
    private void gpom$beginNotifyReloadListeners(CallbackInfo ci) {
        gpom$notifyReloadListenersStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "notifyReloadListeners", at = @At("RETURN"))
    private void gpom$endNotifyReloadListeners(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ResourceManager.notifyReloadListeners", gpom$notifyReloadListenersStartedAt);
        gpom$notifyReloadListenersStartedAt = 0L;
    }

    @Inject(method = "registerReloadListener", at = @At("HEAD"))
    private void gpom$beginRegisterReloadListener(IResourceManagerReloadListener listener, CallbackInfo ci) {
        gpom$registerReloadListenerStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerReloadListener", at = @At("RETURN"))
    private void gpom$endRegisterReloadListener(IResourceManagerReloadListener listener, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ResourceManager.registerReloadListener " + listener.getClass().getName(), gpom$registerReloadListenerStartedAt);
        gpom$registerReloadListenerStartedAt = 0L;
    }

    @Redirect(
            method = "notifyReloadListeners",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/IResourceManagerReloadListener;onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V")
    )
    private void gpom$timeReloadListener(IResourceManagerReloadListener listener, IResourceManager resourceManager) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            listener.onResourceManagerReload(resourceManager);
        } finally {
            StartupProfiler.recordResourceReloadListener(listener.getClass().getName(), System.nanoTime() - startedAt, false);
            StartupProfiler.endProbe("Resource reload listener " + listener.getClass().getName(), startedAt);
        }
    }

    @Redirect(
            method = "registerReloadListener",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/IResourceManagerReloadListener;onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V")
    )
    private void gpom$timeRegisteredReloadListener(IResourceManagerReloadListener listener, IResourceManager resourceManager) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            listener.onResourceManagerReload(resourceManager);
        } finally {
            StartupProfiler.recordResourceReloadListener(listener.getClass().getName(), System.nanoTime() - startedAt, true);
            StartupProfiler.endProbeAlways("Registered resource reload listener " + listener.getClass().getName(), startedAt);
        }
    }
}
