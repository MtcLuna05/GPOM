package com.l.gpom.compat.hei;

import com.l.gpom.util.ReflectionFields;
import net.minecraft.client.gui.GuiScreen;

import java.lang.reflect.Method;

public final class HeiOverlayLayeringCompat {
    private static volatile Method updateGuiExclusionAreasMethod;
    private static volatile Method ingredientOverlayUpdateScreenMethod;
    private static volatile Method leftAreaUpdateScreenMethod;

    private HeiOverlayLayeringCompat() {
    }

    public static void refreshLayoutBeforePostTooltips(Object guiEventHandler, GuiScreen screen) {
        if (guiEventHandler == null || screen == null) {
            return;
        }

        boolean exclusionsChanged = updateGuiExclusionAreas(guiEventHandler);
        Object ingredientOverlay = ReflectionFields.get(guiEventHandler, "ingredientListOverlay", "ingredientListOverlay");
        if (ingredientOverlay != null) {
            invokeUpdateScreen(ingredientOverlay, screen, exclusionsChanged, true);
        }
        Object leftAreaDispatcher = ReflectionFields.get(guiEventHandler, "leftAreaDispatcher", "leftAreaDispatcher");
        if (leftAreaDispatcher != null) {
            invokeUpdateScreen(leftAreaDispatcher, screen, exclusionsChanged, false);
        }
    }

    private static boolean updateGuiExclusionAreas(Object guiEventHandler) {
        Object helper = ReflectionFields.get(guiEventHandler, "guiScreenHelper", "guiScreenHelper");
        if (helper == null) {
            return false;
        }
        try {
            Method method = updateGuiExclusionAreasMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(helper.getClass())) {
                method = findMethod(helper.getClass(), new Class<?>[0], "updateGuiExclusionAreas");
                updateGuiExclusionAreasMethod = method;
            }
            Object value = method == null ? null : method.invoke(helper);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void invokeUpdateScreen(Object owner, GuiScreen screen, boolean exclusionsChanged, boolean ingredientOverlay) {
        try {
            Method method = ingredientOverlay ? ingredientOverlayUpdateScreenMethod : leftAreaUpdateScreenMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(owner.getClass())) {
                method = findMethod(owner.getClass(), new Class<?>[] {GuiScreen.class, boolean.class}, "updateScreen");
                if (ingredientOverlay) {
                    ingredientOverlayUpdateScreenMethod = method;
                } else {
                    leftAreaUpdateScreenMethod = method;
                }
            }
            if (method != null) {
                method.invoke(owner, screen, exclusionsChanged);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameters, String... names) {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
