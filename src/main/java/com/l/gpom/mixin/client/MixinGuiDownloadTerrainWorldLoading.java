package com.l.gpom.mixin.client;

import com.l.gpom.client.WorldLoadingProgress;
import net.minecraft.client.gui.GuiDownloadTerrain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiDownloadTerrain.class, remap = false)
public abstract class MixinGuiDownloadTerrainWorldLoading {
    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$drawWorldLoadingTerrain(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!WorldLoadingProgress.enabled()) {
            return;
        }
        WorldLoadingProgress.beginTerrainIfNeeded();
        WorldLoadingProgress.safeRenderCurrentMinecraft(-1, false);
        ci.cancel();
    }
}
