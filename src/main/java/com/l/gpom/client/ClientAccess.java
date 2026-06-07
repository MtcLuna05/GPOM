package com.l.gpom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClientAccess {
    private static volatile Method getMinecraftMethod;
    private static volatile Method isMinecraftThreadMethod;
    private static volatile Method addScheduledTaskMethod;
    private static volatile Method fontWidthMethod;
    private static volatile Method drawStringWithShadowMethod;
    private static volatile Method drawRectMethod;
    private static volatile Field fontRendererField;

    private ClientAccess() {
    }

    public static Minecraft minecraft() {
        try {
            Method method = getMinecraftMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[0], "func_71410_x", "getMinecraft");
                getMinecraftMethod = method;
            }
            Object value = method == null ? null : method.invoke(null);
            return value instanceof Minecraft ? (Minecraft) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isMinecraftThread(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            Method method = isMinecraftThreadMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[0], "func_152345_ab", "isCallingFromMinecraftThread");
                isMinecraftThreadMethod = method;
            }
            Object value = method == null ? null : method.invoke(minecraft);
            return value instanceof Boolean ? (Boolean) value : "Client thread".equals(Thread.currentThread().getName());
        } catch (Throwable ignored) {
            return "Client thread".equals(Thread.currentThread().getName());
        }
    }

    public static boolean schedule(Minecraft minecraft, Runnable task) {
        if (minecraft == null || task == null) {
            return false;
        }
        try {
            Method method = addScheduledTaskMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[] {Runnable.class}, "func_152344_a", "addScheduledTask");
                addScheduledTaskMethod = method;
            }
            if (method == null) {
                return false;
            }
            method.invoke(minecraft, task);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static FontRenderer fontRenderer(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = fontRendererField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71466_p", "fontRenderer");
                fontRendererField = field;
            }
            Object value = field == null ? null : field.get(minecraft);
            return value instanceof FontRenderer ? (FontRenderer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int stringWidth(FontRenderer font, String text) {
        if (font == null || text == null) {
            return 0;
        }
        try {
            Method method = fontWidthMethod;
            if (method == null) {
                method = findMethod(FontRenderer.class, new Class<?>[] {String.class}, "func_78256_a", "getStringWidth");
                fontWidthMethod = method;
            }
            Object value = method == null ? null : method.invoke(font, text);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void drawStringWithShadow(FontRenderer font, String text, float x, float y, int color) {
        if (font == null || text == null) {
            return;
        }
        try {
            Method method = drawStringWithShadowMethod;
            if (method == null) {
                method = findMethod(FontRenderer.class, new Class<?>[] {String.class, float.class, float.class, int.class}, "func_175063_a", "drawStringWithShadow");
                drawStringWithShadowMethod = method;
            }
            if (method != null) {
                method.invoke(font, text, x, y, color);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Method method = drawRectMethod;
            if (method == null) {
                method = findMethod(Gui.class, new Class<?>[] {int.class, int.class, int.class, int.class, int.class}, "func_73734_a", "drawRect");
                drawRectMethod = method;
            }
            if (method != null) {
                method.invoke(null, left, top, right, bottom, color);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameters, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
