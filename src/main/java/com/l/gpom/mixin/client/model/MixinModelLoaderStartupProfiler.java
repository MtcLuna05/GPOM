package com.l.gpom.mixin.client.model;

import com.l.gpom.profiling.StartupProfiler;
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
    @Unique private long gpom$setupModelRegistryStartedAt;
    @Unique private long gpom$loadVariantModelsStartedAt;
    @Unique private long gpom$loadMultipartVariantModelsStartedAt;
    @Unique private long gpom$loadBlocksStartedAt;
    @Unique private long gpom$loadItemModelsStartedAt;
    @Unique private long gpom$onPostBakeEventStartedAt;

    @Inject(method = "setupModelRegistry", at = @At("HEAD"))
    private void gpom$beginSetupModelRegistry(CallbackInfoReturnable<IRegistry<ModelResourceLocation, IBakedModel>> cir) {
        gpom$setupModelRegistryStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "setupModelRegistry", at = @At("RETURN"))
    private void gpom$endSetupModelRegistry(CallbackInfoReturnable<IRegistry<ModelResourceLocation, IBakedModel>> cir) {
        StartupProfiler.endProbeAlways("ModelLoader.setupModelRegistry", gpom$setupModelRegistryStartedAt);
        gpom$setupModelRegistryStartedAt = 0L;
    }

    @Inject(method = "loadVariantModels", at = @At("HEAD"))
    private void gpom$beginLoadVariantModels(CallbackInfo ci) {
        gpom$loadVariantModelsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadVariantModels", at = @At("RETURN"))
    private void gpom$endLoadVariantModels(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadVariantModels", gpom$loadVariantModelsStartedAt);
        gpom$loadVariantModelsStartedAt = 0L;
    }

    @Inject(method = "loadMultipartVariantModels", at = @At("HEAD"))
    private void gpom$beginLoadMultipartVariantModels(CallbackInfo ci) {
        gpom$loadMultipartVariantModelsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadMultipartVariantModels", at = @At("RETURN"))
    private void gpom$endLoadMultipartVariantModels(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadMultipartVariantModels", gpom$loadMultipartVariantModelsStartedAt);
        gpom$loadMultipartVariantModelsStartedAt = 0L;
    }

    @Inject(method = "loadBlocks", at = @At("HEAD"))
    private void gpom$beginLoadBlocks(CallbackInfo ci) {
        gpom$loadBlocksStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadBlocks", at = @At("RETURN"))
    private void gpom$endLoadBlocks(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadBlocks", gpom$loadBlocksStartedAt);
        gpom$loadBlocksStartedAt = 0L;
    }

    @Inject(method = "loadItemModels", at = @At("HEAD"))
    private void gpom$beginLoadItemModels(CallbackInfo ci) {
        gpom$loadItemModelsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadItemModels", at = @At("RETURN"))
    private void gpom$endLoadItemModels(CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.loadItemModels", gpom$loadItemModelsStartedAt);
        gpom$loadItemModelsStartedAt = 0L;
    }

    @Inject(method = "onPostBakeEvent", at = @At("HEAD"))
    private void gpom$beginOnPostBakeEvent(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, CallbackInfo ci) {
        gpom$onPostBakeEventStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "onPostBakeEvent", at = @At("RETURN"))
    private void gpom$endOnPostBakeEvent(IRegistry<ModelResourceLocation, IBakedModel> modelRegistry, CallbackInfo ci) {
        StartupProfiler.endProbe("ModelLoader.onPostBakeEvent", gpom$onPostBakeEventStartedAt);
        gpom$onPostBakeEventStartedAt = 0L;
    }
}
