package com.l.gpom.client;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class MouseFocusDeltaGuard {
    private static final int POST_GRAB_DROP_PAIRS = 2;
    private static volatile int pendingFocusDeltaDrops;
    private static volatile boolean droppingFocusDeltaPair;
    private static volatile boolean accessResolved;
    private static volatile Field currentScreenField;
    private static volatile Field worldField;
    private static volatile Field playerField;
    private static volatile Field inGameHasFocusField;
    private static volatile Method setIngameFocusMethod;

    private MouseFocusDeltaGuard() {
    }

    public static void afterGrab() {
        try {
            if (!Display.isCreated() || !Mouse.isCreated() || !Mouse.isGrabbed()) {
                return;
            }

            int width = Display.getWidth();
            int height = Display.getHeight();
            if (width > 0 && height > 0) {
                Mouse.setCursorPosition(width / 2, height / 2);
            }

            Mouse.getDX();
            Mouse.getDY();
            // Some platforms report one or more deferred cursor deltas after focus is returned.
            pendingFocusDeltaDrops = POST_GRAB_DROP_PAIRS;
            droppingFocusDeltaPair = false;
        } catch (Throwable ignored) {
        }
    }

    public static void afterUngrab() {
        pendingFocusDeltaDrops = 0;
        droppingFocusDeltaPair = false;
    }

    public static void ensureFirstPersonMouseGrabbed(Object minecraft) {
        try {
            if (minecraft == null
                    || !Display.isCreated()
                    || !Display.isActive()
                    || !Mouse.isCreated()
                    || Mouse.isGrabbed()) {
                return;
            }

            resolveAccess(minecraft.getClass());
            if (value(minecraft, worldField) == null || value(minecraft, playerField) == null) {
                setBoolean(minecraft, inGameHasFocusField, false);
                if (Mouse.isGrabbed()) {
                    Mouse.setGrabbed(false);
                }
                afterUngrab();
                return;
            }

            if (value(minecraft, currentScreenField) != null
                    || value(minecraft, worldField) == null
                    || value(minecraft, playerField) == null) {
                return;
            }

            if (!booleanValue(minecraft, inGameHasFocusField, false)
                    && invokeSetIngameFocus(minecraft)) {
                return;
            }

            Mouse.setGrabbed(true);
            setBoolean(minecraft, inGameHasFocusField, true);
            afterGrab();
        } catch (Throwable ignored) {
        }
    }

    public static int mouseDxForCamera() {
        if (shouldDropFocusDelta()) {
            Mouse.getDX();
            droppingFocusDeltaPair = true;
            return 0;
        }
        return Mouse.getDX();
    }

    public static int mouseDyForCamera() {
        if (droppingFocusDeltaPair) {
            Mouse.getDY();
            droppingFocusDeltaPair = false;
            pendingFocusDeltaDrops = Math.max(0, pendingFocusDeltaDrops - 1);
            return 0;
        }
        return Mouse.getDY();
    }

    private static boolean shouldDropFocusDelta() {
        return pendingFocusDeltaDrops > 0
                && Display.isCreated()
                && Mouse.isCreated()
                && Mouse.isGrabbed();
    }

    private static void resolveAccess(Class<?> minecraftClass) {
        if (accessResolved) {
            return;
        }
        synchronized (MouseFocusDeltaGuard.class) {
            if (accessResolved) {
                return;
            }
            currentScreenField = findField(minecraftClass, "field_71462_r", "currentScreen");
            worldField = findField(minecraftClass, "field_71441_e", "world");
            playerField = findField(minecraftClass, "field_71439_g", "player");
            inGameHasFocusField = findField(minecraftClass, "field_71415_G", "inGameHasFocus");
            setIngameFocusMethod = findMethod(minecraftClass, "func_71381_h", "setIngameFocus");
            accessResolved = true;
        }
    }

    private static Field findField(Class<?> type, String... names) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (String name : names) {
                try {
                    Method method = cursor.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        return null;
    }

    private static Object value(Object target, Field field) throws IllegalAccessException {
        return field == null ? null : field.get(target);
    }

    private static boolean booleanValue(Object target, Field field, boolean fallback) throws IllegalAccessException {
        return field == null ? fallback : field.getBoolean(target);
    }

    private static void setBoolean(Object target, Field field, boolean value) throws IllegalAccessException {
        if (field != null) {
            field.setBoolean(target, value);
        }
    }

    private static boolean invokeSetIngameFocus(Object minecraft) {
        Method method = setIngameFocusMethod;
        if (method == null) {
            return false;
        }
        try {
            method.invoke(minecraft);
            return Mouse.isGrabbed();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
