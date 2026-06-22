package com.l.gpom.mixin.client.render;

import com.l.gpom.client.RenderUpdateDeduplicator;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RenderGlobal.class, remap = false)
public abstract class MixinRenderGlobalRenderUpdateDedupe {
    @Inject(
            method = {
                    "markBlockRangeForRenderUpdate(IIIIII)V",
                    "func_147585_a(IIIIII)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$dedupeSameTickSectionUpdate(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo ci) {
        if (RenderUpdateDeduplicator.shouldSuppress(this, minX, minY, minZ, maxX, maxY, maxZ)) {
            ci.cancel();
        }
    }
}
