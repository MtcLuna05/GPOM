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

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/inventory/GuiInventory;renderHoveredToolTip(II)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void gpom$syncBaublesHoveredSlotBeforeTooltipMcp(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.syncHoveredSlotForTooltip((GuiContainer) (Object) this, mouseX, mouseY);
    }

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/inventory/GuiInventory;func_191948_b(II)V",
                    shift = At.Shift.BEFORE
            ),
            require = 0
    )
    private void gpom$syncBaublesHoveredSlotBeforeTooltipSrg(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.syncHoveredSlotForTooltip((GuiContainer) (Object) this, mouseX, mouseY);
    }
}
