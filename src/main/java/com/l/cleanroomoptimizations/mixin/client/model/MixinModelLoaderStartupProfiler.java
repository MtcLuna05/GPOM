package com.l.cleanroomoptimizations.mixin.client.model;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelLoader.class, remap = false)
public abstract class MixinModelLoaderStartupProfiler {
    @Unique private long cleanroomoptimizations$setupModelRegistryStartedAt;
    @Unique private long cleanroomoptimizations$loadVariantModelsStartedAt;
    @Unique private long cleanroomoptimizations$loadMultipartVariantModelsStartedAt;
    @Unique private long cleanroomoptimizations$loadBlocksStartedAt;
    @Unique private long cleanroomoptimizations$loadItemModelsStartedAt;
    @Unique private long cleanroomoptimizations$onPostBakeEventStartedAt;

    @Inject(method = "setupModelRegistry", at = @At("HEAD"))
    private void cleanroomoptimizations$beginSetupModelRegistry(CallbackInfoReturnable<IRegistry<ModelResourceLocation, IBakedModel>> cir) {
        cleanroomoptimizations$setupModelRegistryStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "setupModelRegistry", at = @At("RETURN"))
    private void cleanroomoptimizations$endSetupModelRegistry(CallbackInfoReturnable<IRegistry<ModelResourceLocation, IBakedModel>> cir) {
        StartupProfiler.endProbeAlways("ModelLoader.setupModelRegistry", cleanroomoptimizations$setupModelRegistryStartedAt);
        cleanroomoptimizations$setupModelRegistryStartedAt = 0L;
    }

    @Inject(method = "loadVariantModels", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadVariantModels(CallbackInfo ci) {
        cleanroomoptimizations$loadVariantModelsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadVariantModels", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadVariantModels(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadVariantModels", cleanroomoptimizations$loadVariantModelsStartedAt);
        cleanroomoptimizations$loadVariantModelsStartedAt = 0L;
    }

    @Inject(method = "loadMultipartVariantModels", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadMultipartVariantModels(CallbackInfo ci) {
        cleanroomoptimizations$loadMultipartVariantModelsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadMultipartVariantModels", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadMultipartVariantModels(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadMultipartVariantModels", cleanroomoptimizations$loadMultipartVariantModelsStartedAt);
        cleanroomoptimizations$loadMultipartVariantModelsStartedAt = 0L;
    }

    @Inject(method = "loadBlocks", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadBlocks(CallbackInfo ci) {
        cleanroomoptimizations$loadBlocksStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadBlocks", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadBlocks(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadBlocks", cleanroomoptimizations$loadBlocksStartedAt);
        cleanroomoptimizations$loadBlocksStartedAt = 0L;
    }

    @Inject(method = "loadItemModels", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadItemModels(CallbackInfo ci) {
        cleanroomoptimizations$loadItemModelsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadItemModels", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadItemModels(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadItemModels", cleanroomoptimizations$loadItemModelsStartedAt);
        cleanroomoptimizations$loadItemModelsStartedAt = 0L;
    }

    @Inject(method = "onPostBakeEvent", at = @At("HEAD"))
    private void cleanroomoptimizations$beginOnPostBakeEvent(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, CallbackInfo ci) {
        cleanroomoptimizations$onPostBakeEventStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "onPostBakeEvent", at = @At("RETURN"))
    private void cleanroomoptimizations$endOnPostBakeEvent(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.onPostBakeEvent", cleanroomoptimizations$onPostBakeEventStartedAt);
        cleanroomoptimizations$onPostBakeEventStartedAt = 0L;
    }
}
