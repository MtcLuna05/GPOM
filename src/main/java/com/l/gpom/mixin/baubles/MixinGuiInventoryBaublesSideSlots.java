package com.l.gpom.mixin.baubles;

import com.l.gpom.compat.baubles.BaublesSideSlotsClient;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiInventory.class, remap = false)
public abstract class MixinGuiInventoryBaublesSideSlots {
    @Inject(
            method = {
                    "initGui()V",
                    "func_73866_w_()V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$prepareBaublesSideSlots(CallbackInfo ci) {
        BaublesSideSlotsClient.prepareGuiInventorySideSlots((GuiContainer) (Object) this);
    }
}
