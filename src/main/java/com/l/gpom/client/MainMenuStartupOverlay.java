package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import java.lang.reflect.Field;

public final class MainMenuStartupOverlay {
    private MainMenuStartupOverlay() {
    }

    public static void renderFromScreen(Object screen, String screenName) {
        if (screen == null) {
            return;
        }
        int width = screenIntField(screen, "field_146294_l", "width");
        int height = screenIntField(screen, "field_146295_m", "height");
        if (width <= 0 || height <= 0) {
            Minecraft minecraft = ClientAccess.minecraft();
            width = minecraft == null ? width : minecraft.displayWidth;
            height = minecraft == null ? height : minecraft.displayHeight;
        }
        render(width, height, screenName);
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

    private static int screenIntField(Object screen, String primaryName, String fallbackName) {
        try {
            Field field = findField(screen.getClass(), primaryName);
            if (field == null) {
                field = findField(screen.getClass(), fallbackName);
            }
            if (field != null) {
                field.setAccessible(true);
                return field.getInt(screen);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }
}
