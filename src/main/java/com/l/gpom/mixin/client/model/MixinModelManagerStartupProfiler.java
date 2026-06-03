package com.l.gpom.mixin.client.model;

import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelManager.class)
public abstract class MixinModelManagerStartupProfiler {
    @Unique
    private long gpom$reloadStartedAt;

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void gpom$beginReload(IResourceManager resourceManager, CallbackInfo ci) {
        gpom$reloadStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void gpom$endReload(IResourceManager resourceManager, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ModelManager.onResourceManagerReload", gpom$reloadStartedAt);
        gpom$reloadStartedAt = 0L;
    }
}
