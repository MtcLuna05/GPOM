package com.l.gpom.mixin.baubles;

import com.l.gpom.compat.baubles.BaublesSideSlotsClient;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(value = GuiContainer.class, remap = false)
public abstract class MixinGuiContainerBaublesQuickEquip {
    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$arrangeBaublesSideSlots(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.arrangeSlots((GuiContainer) (Object) this);
    }

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/inventory/GuiContainer;drawGuiContainerBackgroundLayer(FII)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$drawBaublesSideSlotsAfterBackgroundMcp(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.drawPanel((GuiContainer) (Object) this);
    }

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/inventory/GuiContainer;func_146976_a(FII)V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$drawBaublesSideSlotsAfterBackgroundSrg(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.drawPanel((GuiContainer) (Object) this);
    }

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$syncBaublesHoveredSlotAfterForegroundEvent(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.syncHoveredSlotAndDrawFallback((GuiContainer) (Object) this, mouseX, mouseY);
    }

    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$drawEmptyBaublesSlotTooltipAtReturn(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        BaublesSideSlotsClient.drawEmptySlotTooltip((GuiContainer) (Object) this, mouseX, mouseY);
    }

    @Inject(
            method = {
                    "drawSlot(Lnet/minecraft/inventory/Slot;)V",
                    "func_146977_a(Lnet/minecraft/inventory/Slot;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$skipClosedBaublesSideSlotRendering(Slot slot, CallbackInfo ci) {
        if (BaublesSideSlotsClient.shouldSkipSlotRender((GuiContainer) (Object) this, slot)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                    "mouseClicked(III)V",
                    "func_73864_a(III)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$quickEquipBauble(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) throws IOException {
        if (BaublesSideSlotsClient.handleCosmeticArmorBaubleToggleClick((GuiContainer) (Object) this, mouseX, mouseY, mouseButton)) {
            ci.cancel();
            return;
        }
        if (BaublesSideSlotsClient.handlePanelClick((GuiContainer) (Object) this, mouseX, mouseY, mouseButton)) {
            ci.cancel();
            return;
        }
        if (BaublesSideSlotsClient.handleSideRailSlotClick((GuiContainer) (Object) this, mouseX, mouseY, mouseButton)) {
            ci.cancel();
            return;
        }
        if (BaublesSideSlotsClient.tryQuickEquip((GuiContainer) (Object) this, mouseX, mouseY, mouseButton)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                    "handleMouseInput()V",
                    "func_146274_d()V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$scrollBaublesSideSlots(CallbackInfo ci) throws IOException {
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0 && BaublesSideSlotsClient.handleScroll((GuiContainer) (Object) this, wheel)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                    "onGuiClosed()V",
                    "func_146281_b()V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$removeGuiInventoryBaublesSideSlots(CallbackInfo ci) {
        BaublesSideSlotsClient.removeGuiInventorySideSlots((GuiContainer) (Object) this);
    }
}
