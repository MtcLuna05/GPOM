package com.l.gpom.mixin.client;

import com.l.gpom.client.MainMenuWorldScreenshot;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMainMenu.class, remap = false)
public abstract class MixinGuiMainMenuWorldScreenshot extends GuiScreen {
    @Inject(
            method = {
                    "renderSkybox(IIF)V",
                    "func_73971_c(IIF)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$renderWorldScreenshotBackground(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (MainMenuWorldScreenshot.renderBackground(this)) {
            ci.cancel();
        }
    }
}
