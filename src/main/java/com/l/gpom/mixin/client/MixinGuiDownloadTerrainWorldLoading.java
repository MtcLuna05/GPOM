package com.l.gpom.mixin.client;

import com.l.gpom.client.WorldLoadingProgress;
import com.l.gpom.profiling.RuntimeSinkProfiler;
import net.minecraft.client.gui.GuiDownloadTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiDownloadTerrain.class, remap = false)
public abstract class MixinGuiDownloadTerrainWorldLoading {
    private long gpom$drawScreenStartedAt;

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$beginDrawWorldLoadingTerrainProbe(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        gpom$drawScreenStartedAt = RuntimeSinkProfiler.begin();
    }

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$drawWorldLoadingTerrain(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        RuntimeSinkProfiler.end("worldLoad", "GuiDownloadTerrain.drawScreen", gpom$drawScreenStartedAt);
        gpom$drawScreenStartedAt = 0L;
        if (!WorldLoadingProgress.enabled()) {
            return;
        }
        WorldLoadingProgress.beginTerrainIfNeeded();
        WorldLoadingProgress.safeRenderCurrentMinecraft(-1, false);
    }
}
