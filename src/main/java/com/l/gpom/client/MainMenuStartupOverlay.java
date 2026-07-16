package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MainMenuStartupOverlay {
    private static final ConcurrentMap<String, Field> SCREEN_INT_FIELDS = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_SCREEN_INT_FIELDS = ConcurrentHashMap.newKeySet();

    private MainMenuStartupOverlay() {
    }

    public static boolean isSupportedMainMenu(Object screen) {
        return screen instanceof GuiMainMenu
                || (screen != null && "lumien.custommainmenu.gui.GuiCustom".equals(screen.getClass().getName()));
    }

    public static void renderFromScreen(Object screen, String screenName) {
        if (screen == null) {
            return;
        }
        int width = screenIntField(screen, "field_146294_l", "width");
        int height = screenIntField(screen, "field_146295_m", "height");
        if (width <= 0 || height <= 0) {
            Minecraft minecraft = ClientAccess.minecraft();
            width = minecraft == null ? width : ClientAccess.displayWidth(minecraft);
            height = minecraft == null ? height : ClientAccess.displayHeight(minecraft);
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
            renderIsolated(font, text, x, y, textWidth);
        } catch (Throwable ignored) {
        }
    }

    private static void renderIsolated(FontRenderer font, String text, int x, int y, int textWidth) {
        int previousMatrixMode = GL11.GL_MODELVIEW;
        boolean pushedAttrib = false;
        boolean pushedProjection = false;
        boolean pushedModelView = false;
        try {
            previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            pushedAttrib = true;
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            pushedProjection = true;
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            pushedModelView = true;

            ClientAccess.drawRect(x - 3, y - 3, x + textWidth + 4, y + 11, 0x88000000);
            ClientAccess.drawStringWithShadow(font, text, x, y, 0xD8D8D8);
        } catch (Throwable ignored) {
        } finally {
            try {
                if (pushedModelView) {
                    GL11.glMatrixMode(GL11.GL_MODELVIEW);
                    GL11.glPopMatrix();
                }
                if (pushedProjection) {
                    GL11.glMatrixMode(GL11.GL_PROJECTION);
                    GL11.glPopMatrix();
                }
                GL11.glMatrixMode(previousMatrixMode);
                if (pushedAttrib) {
                    GL11.glPopAttrib();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static int screenIntField(Object screen, String primaryName, String fallbackName) {
        try {
            Field field = findCachedField(screen.getClass(), primaryName, fallbackName);
            if (field != null) {
                return field.getInt(screen);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static Field findCachedField(Class<?> type, String primaryName, String fallbackName) {
        String key = type.getName() + '#' + primaryName + '#' + fallbackName;
        Field cached = SCREEN_INT_FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_SCREEN_INT_FIELDS.contains(key)) {
            return null;
        }

        Field field = findField(type, primaryName, fallbackName);
        if (field != null) {
            Field existing = SCREEN_INT_FIELDS.putIfAbsent(key, field);
            return existing == null ? field : existing;
        }
        MISSING_SCREEN_INT_FIELDS.add(key);
        return null;
    }

    private static Field findField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    // Try every known runtime name once, then cache the miss.
                } catch (Throwable ignored) {
                    return null;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
