package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

public final class MainMenuStartupOverlay {
    private MainMenuStartupOverlay() {
    }

    public static void render(int width, int height, String screenName) {
        if (!GpomEarlyConfig.mainMenuStartupTimeEnabled()) {
            return;
        }
        StartupProfiler.markMainMenuReached(screenName);

        try {
            Minecraft minecraft = ClientAccess.minecraft();
            FontRenderer font = ClientAccess.fontRenderer(minecraft);
            if (font == null) {
                return;
            }

            String text = StartupProfiler.mainMenuStartupTimeText();
            int textWidth = ClientAccess.stringWidth(font, text);
            int x = Math.max(4, width - textWidth - 6);
            int y = 4;
            ClientAccess.drawRect(x - 3, y - 3, x + textWidth + 4, y + 11, 0x88000000);
            ClientAccess.drawStringWithShadow(font, text, x, y, 0xD8D8D8);
        } catch (Throwable ignored) {
        }
    }
}
