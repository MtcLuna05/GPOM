package com.l.gpom.mixin.client.resources;

import com.l.gpom.profiling.StartupProfiler;
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
    @Unique private long gpom$loadTextureStartedAt;
    @Unique private long gpom$loadSpritesStartedAt;
    @Unique private long gpom$loadTextureAtlasStartedAt;

    @Inject(method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("HEAD"))
    private void gpom$beginLoadTexture(IResourceManager resourceManager, CallbackInfo ci) {
        gpom$loadTextureStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadTexture(Lnet/minecraft/client/resources/IResourceManager;)V", at = @At("RETURN"))
    private void gpom$endLoadTexture(IResourceManager resourceManager, CallbackInfo ci) {
        StartupProfiler.endProbe("TextureMap.loadTexture", gpom$loadTextureStartedAt);
        gpom$loadTextureStartedAt = 0L;
    }

    @Inject(method = "loadSprites", at = @At("HEAD"))
    private void gpom$beginLoadSprites(IResourceManager resourceManager, ITextureMapPopulator iconCreator, CallbackInfo ci) {
        gpom$loadSpritesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadSprites", at = @At("RETURN"))
    private void gpom$endLoadSprites(IResourceManager resourceManager, ITextureMapPopulator iconCreator, CallbackInfo ci) {
        StartupProfiler.endProbe("TextureMap.loadSprites", gpom$loadSpritesStartedAt);
        gpom$loadSpritesStartedAt = 0L;
    }

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void gpom$beginLoadTextureAtlas(IResourceManager resourceManager, CallbackInfo ci) {
        gpom$loadTextureAtlasStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "loadTextureAtlas", at = @At("RETURN"))
    private void gpom$endLoadTextureAtlas(IResourceManager resourceManager, CallbackInfo ci) {
        StartupProfiler.endProbe("TextureMap.loadTextureAtlas", gpom$loadTextureAtlasStartedAt);
        gpom$loadTextureAtlasStartedAt = 0L;
    }
}
