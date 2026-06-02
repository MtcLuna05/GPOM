package com.l.cleanroomoptimizations.mixin.client.model;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
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
    private long cleanroomoptimizations$reloadStartedAt;

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void cleanroomoptimizations$beginReload(IResourceManager resourceManager, CallbackInfo ci) {
        cleanroomoptimizations$reloadStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void cleanroomoptimizations$endReload(IResourceManager resourceManager, CallbackInfo ci) {
        StartupProfiler.endProbeAlways("ModelManager.onResourceManagerReload", cleanroomoptimizations$reloadStartedAt);
        cleanroomoptimizations$reloadStartedAt = 0L;
    }
}
