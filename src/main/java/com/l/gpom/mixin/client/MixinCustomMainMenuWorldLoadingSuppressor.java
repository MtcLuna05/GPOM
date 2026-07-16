package com.l.gpom.mixin.client;

import com.l.gpom.client.WorldLoadingProgress;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "lumien.custommainmenu.gui.GuiCustom", remap = false)
public abstract class MixinCustomMainMenuWorldLoadingSuppressor extends GuiScreen {
    @Inject(
            method = {
                    "func_73863_a(IIF)V",
                    "drawScreen(IIF)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$suppressCustomMainMenuDuringWorldLoading(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.safeRenderCurrentMinecraft(-1, false);
        ci.cancel();
    }
}
