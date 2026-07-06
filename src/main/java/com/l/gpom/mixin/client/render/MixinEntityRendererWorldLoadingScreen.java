package com.l.gpom.mixin.client.render;

import com.l.gpom.client.WorldLoadingProgress;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, remap = false)
public abstract class MixinEntityRendererWorldLoadingScreen {
    @Inject(
            method = {
                    "renderWorld(FJ)V",
                    "func_78471_a(FJ)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$finishWorldLoadingAfterWorldRender(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        WorldLoadingProgress.safeRenderCurrentMinecraft(-1, false);
        WorldLoadingProgress.finishAfterFirstWorldRender("first world render completed");
    }
}
