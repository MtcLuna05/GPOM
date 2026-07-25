package com.l.gpom.mixin.client.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reuses repeated AO lightmap interpolation within one block compile only. */
@Mixin(targets = "net.minecraftforge.client.model.pipeline.VertexLighterSmoothAo", remap = false)
public abstract class MixinVertexLighterSmoothAoLightmapCache {
    @Unique private boolean gpom$aoCacheValid;
    @Unique private int gpom$aoX;
    @Unique private int gpom$aoY;
    @Unique private int gpom$aoZ;
    @Unique private float gpom$aoLight0;
    @Unique private float gpom$aoLight1;

    @Inject(method = "updateBlockInfo", at = @At("HEAD"))
    private void gpom$invalidateAoCache(CallbackInfo ci) {
        gpom$aoCacheValid = false;
    }

    @Inject(method = "calcLightmap", at = @At("HEAD"), cancellable = true)
    private void gpom$readAoCache(float[] lightmap, float x, float y, float z, CallbackInfo ci) {
        if (!gpom$aoCacheValid
                || gpom$aoX != Float.floatToIntBits(x)
                || gpom$aoY != Float.floatToIntBits(y)
                || gpom$aoZ != Float.floatToIntBits(z)) {
            return;
        }
        lightmap[0] = gpom$aoLight0;
        lightmap[1] = gpom$aoLight1;
        ci.cancel();
    }

    @Inject(method = "calcLightmap", at = @At("RETURN"))
    private void gpom$writeAoCache(float[] lightmap, float x, float y, float z, CallbackInfo ci) {
        gpom$aoX = Float.floatToIntBits(x);
        gpom$aoY = Float.floatToIntBits(y);
        gpom$aoZ = Float.floatToIntBits(z);
        gpom$aoLight0 = lightmap[0];
        gpom$aoLight1 = lightmap[1];
        gpom$aoCacheValid = true;
    }
}
