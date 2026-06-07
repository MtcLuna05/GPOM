package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

public final class MainMenuStartupOverlay {
    private MainMenuStartupOverlay() {
    }

    public static void render(int width, int height, String screenName) {
        if (!GpomEarlyConfig.mainMenuStartupTimeEnabled()) {
            return;
        }
        StartupProfiler.markMainMenuReached(screenName);

        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.fontRenderer == null) {
                return;
            }

            FontRenderer font = minecraft.fontRenderer;
            String text = StartupProfiler.mainMenuStartupTimeText();
            int x = 4;
            int y = Math.max(4, height - 12);
            int textWidth = font.getStringWidth(text);
            Gui.drawRect(x - 3, y - 3, x + textWidth + 4, y + 11, 0x88000000);
            font.drawStringWithShadow(text, x, y, 0xD8D8D8);
        } catch (Throwable ignored) {
        }
    }
}
