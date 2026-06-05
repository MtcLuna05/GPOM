package com.l.gpom.client;

import net.minecraft.client.gui.GuiScreen;

public final class GpomWorldLoadingScreen extends GuiScreen {
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        WorldLoadingProgress.safeRender(mc, width, height, -1);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
