package com.l.gpom.mixin.client;

import com.l.gpom.client.MainMenuStartupOverlay;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiMainMenu.class, remap = false)
public abstract class MixinGuiMainMenuStartupOverlay extends GuiScreen {
    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$drawStartupTime(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        MainMenuStartupOverlay.render(width, height, getClass().getName());
    }
}
