package com.l.gpom.mixin.client;

import com.l.gpom.client.MainMenuWorldScreenshot;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "lumien.custommainmenu.gui.GuiCustom", remap = false)
public abstract class MixinCustomMainMenuWorldScreenshot extends GuiScreen {
    @Inject(
            method = "func_73863_a(IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/ArrayList;iterator()Ljava/util/Iterator;",
                    ordinal = 0
            ),
            require = 0
    )
    private void gpom$renderWorldScreenshotAfterConfiguredBackground(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MainMenuWorldScreenshot.renderBackground(this);
    }
}
