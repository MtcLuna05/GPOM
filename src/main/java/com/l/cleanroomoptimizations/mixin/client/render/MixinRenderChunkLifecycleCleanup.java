package com.l.cleanroomoptimizations.mixin.client.render;

import com.l.cleanroomoptimizations.util.ReflectionFields;
import net.minecraft.client.renderer.chunk.RenderChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderChunk.class)
public abstract class MixinRenderChunkLifecycleCleanup {
    @Inject(method = "deleteGlResources", at = @At("RETURN"))
    private void cleanroomoptimizations$clearWorldViewAfterDelete(CallbackInfo ci) {
        ReflectionFields.set(this, null, "worldView", "worldView", "field_189564_r", "r");
    }
}
