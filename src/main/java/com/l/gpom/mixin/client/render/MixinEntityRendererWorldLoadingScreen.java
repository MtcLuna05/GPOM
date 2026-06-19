package com.l.gpom.mixin.client.render;

import com.l.gpom.client.WorldLoadingProgress;
import com.l.gpom.profiling.RuntimeSinkProfiler;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, remap = false)
public abstract class MixinEntityRendererWorldLoadingScreen {
    private long gpom$renderWorldStartedAt;

    @Inject(
            method = {
                    "renderWorld(FJ)V",
                    "func_78471_a(FJ)V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$beginRenderWorldProbe(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        gpom$renderWorldStartedAt = RuntimeSinkProfiler.begin();
    }

    @Inject(
            method = {
                    "renderWorld(FJ)V",
                    "func_78471_a(FJ)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$finishWorldLoadingAfterWorldRender(float partialTicks, long finishTimeNano, CallbackInfo ci) {
        RuntimeSinkProfiler.end("render", "EntityRenderer.renderWorld", gpom$renderWorldStartedAt);
        gpom$renderWorldStartedAt = 0L;
        WorldLoadingProgress.finishAfterFirstWorldRender("first world render completed");
    }
}
