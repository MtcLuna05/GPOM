package com.l.cleanroomoptimizations.mixin.client.resources;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
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
    @Unique private long cleanroomoptimizations$reloadResourcesStartedAt;
    @Unique private long cleanroomoptimizations$reloadResourcePackStartedAt;
    @Unique private long cleanroomoptimizations$notifyReloadListenersStartedAt;
    @Unique private long cleanroomoptimizations$registerReloadListenerStartedAt;

    @Inject(method = "reloadResources", at = @At("HEAD"))
    private void cleanroomoptimizations$beginReloadResources(List<IResourcePack> resourcePacks, CallbackInfo ci) {
        StartupProfiler.beginResourceReload(resourcePacks.size());
        cleanroomoptimizations$reloadResourcesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "reloadResources", at = @At("RETURN"))
    private void cleanroomoptimizations$endReloadResources(List<IResourcePack> resourcePacks, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ResourceManager.reloadResources packs=" + resourcePacks.size(), cleanroomoptimizations$reloadResourcesStartedAt);
        StartupProfiler.endResourceReload();
        cleanroomoptimizations$reloadResourcesStartedAt = 0L;
    }

    @Inject(method = "reloadResourcePack", at = @At("HEAD"))
    private void cleanroomoptimizations$beginReloadResourcePack(IResourcePack resourcePack, CallbackInfo ci) {
        cleanroomoptimizations$reloadResourcePackStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "reloadResourcePack", at = @At("RETURN"))
    private void cleanroomoptimizations$endReloadResourcePack(IResourcePack resourcePack, CallbackInfo ci) {
        long elapsed = System.nanoTime() - cleanroomoptimizations$reloadResourcePackStartedAt;
        StartupProfiler.recordResourcePackReload(resourcePack.getPackName(), elapsed);
        StartupProfiler.endProbe("ResourceManager.reloadResourcePack " + resourcePack.getPackName(), cleanroomoptimizations$reloadResourcePackStartedAt);
        cleanroomoptimizations$reloadResourcePackStartedAt = 0L;
    }

    @Inject(method = "notifyReloadListeners", at = @At("HEAD"))
    private void cleanroomoptimizations$beginNotifyReloadListeners(CallbackInfo ci) {
        cleanroomoptimizations$notifyReloadListenersStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "notifyReloadListeners", at = @At("RETURN"))
    private void cleanroomoptimizations$endNotifyReloadListeners(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ResourceManager.notifyReloadListeners", cleanroomoptimizations$notifyReloadListenersStartedAt);
        cleanroomoptimizations$notifyReloadListenersStartedAt = 0L;
    }

    @Inject(method = "registerReloadListener", at = @At("HEAD"))
    private void cleanroomoptimizations$beginRegisterReloadListener(IResourceManagerReloadListener listener, CallbackInfo ci) {
        cleanroomoptimizations$registerReloadListenerStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerReloadListener", at = @At("RETURN"))
    private void cleanroomoptimizations$endRegisterReloadListener(IResourceManagerReloadListener listener, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ResourceManager.registerReloadListener " + listener.getClass().getName(), cleanroomoptimizations$registerReloadListenerStartedAt);
        cleanroomoptimizations$registerReloadListenerStartedAt = 0L;
    }

    @Redirect(
            method = "notifyReloadListeners",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/IResourceManagerReloadListener;onResourceManagerReload(Lnet/minecraft/client/resources/IResourceManager;)V")
    )
    private void cleanroomoptimizations$timeReloadListener(IResourceManagerReloadListener listener, IResourceManager resourceManager) {
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
    private void cleanroomoptimizations$timeRegisteredReloadListener(IResourceManagerReloadListener listener, IResourceManager resourceManager) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            listener.onResourceManagerReload(resourceManager);
        } finally {
            StartupProfiler.recordResourceReloadListener(listener.getClass().getName(), System.nanoTime() - startedAt, true);
            StartupProfiler.endProbeAlways("Registered resource reload listener " + listener.getClass().getName(), startedAt);
        }
    }
}
