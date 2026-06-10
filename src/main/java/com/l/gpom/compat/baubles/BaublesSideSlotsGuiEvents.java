package com.l.gpom.compat.baubles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class BaublesSideSlotsGuiEvents {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void guiPostInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.getGui() instanceof GuiInventory)) {
            return;
        }
        event.getButtonList().removeIf(BaublesSideSlotsGuiEvents::isManagedBaublesButton);
        if (!GpomEarlyConfig.baublesSideSlotsEnabled()) {
            return;
        }

        event.getButtonList().add(new SafeBaublesToggleButton(55, (GuiContainer) event.getGui()));
        BaublesSideSlotsClient.syncCosmeticArmorBaubleToggleButtons((GuiContainer) event.getGui(), event.getButtonList());
    }

    private static boolean isManagedBaublesButton(Object button) {
        return button instanceof SafeBaublesToggleButton
                || (button != null && "baubles.client.gui.GuiBaublesButton".equals(button.getClass().getName()));
    }

    private static final class SafeBaublesToggleButton extends GuiButton {
        private final GuiContainer parent;

        private SafeBaublesToggleButton(int id, GuiContainer parent) {
            super(id, 0, 0, 10, 10, "button.baubles");
            this.parent = parent;
        }

        @Override
        public boolean mousePressed(Minecraft minecraft, int mouseX, int mouseY) {
            return BaublesSideSlotsClient.handleToggleButtonClick(this, parent, minecraft, mouseX, mouseY);
        }

        public boolean func_146116_c(Minecraft minecraft, int mouseX, int mouseY) {
            return mousePressed(minecraft, mouseX, mouseY);
        }

        @Override
        public void drawButton(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            BaublesSideSlotsClient.drawToggleButton(this, parent, minecraft, mouseX, mouseY);
        }

        public void func_191745_a(Minecraft minecraft, int mouseX, int mouseY, float partialTicks) {
            drawButton(minecraft, mouseX, mouseY, partialTicks);
        }
    }
}
