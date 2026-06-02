package com.l.cleanroomoptimizations.mixin.client.resources;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraft.client.renderer.texture.ITextureMapPopulator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class MixinTextureMapStartupProfiler {
    @Unique private long cleanroomoptimizations$loadTextureStartedAt;
    @Unique private long cleanroomoptimizations$loadSpritesStartedAt;
    @Unique private long cleanroomoptimizations$loadTextureAtlasStartedAt;

    @Inject(method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadTexture(IResourceManager resourceManager, CallbackInfo ci) {
        cleanroomoptimizations$loadTextureStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadTexture(IResourceManager resourceManager, CallbackInfo ci) {
        StartupProfiler.endProbe("TextureMap.loadTexture", cleanroomoptimizations$loadTextureStartedAt);
        cleanroomoptimizations$loadTextureStartedAt = 0L;
    }

    @Inject(method = "loadSprites", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadSprites(IResourceManager resourceManager, ITextureMapPopulator iconCreator, CallbackInfo ci) {
        cleanroomoptimizations$loadSpritesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadSprites", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadSprites(IResourceManager resourceManager, ITextureMapPopulator iconCreator, CallbackInfo ci) {
        StartupProfiler.endProbe("TextureMap.loadSprites", cleanroomoptimizations$loadSpritesStartedAt);
        cleanroomoptimizations$loadSpritesStartedAt = 0L;
    }

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoadTextureAtlas(IResourceManager resourceManager, CallbackInfo ci) {
        cleanroomoptimizations$loadTextureAtlasStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoadTextureAtlas(IResourceManager resourceManager, CallbackInfo ci) {
        StartupProfiler.endProbe("TextureMap.loadTextureAtlas", cleanroomoptimizations$loadTextureAtlasStartedAt);
        cleanroomoptimizations$loadTextureAtlasStartedAt = 0L;
    }
}
