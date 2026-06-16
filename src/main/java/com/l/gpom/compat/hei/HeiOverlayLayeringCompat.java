package com.l.gpom.compat.hei;

import com.l.gpom.util.ReflectionFields;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Method;

public final class HeiOverlayLayeringCompat {
    private static final float POST_HEI_TOOLTIP_Z = 1000.0F;
    private static volatile Method updateGuiExclusionAreasMethod;
    private static volatile Method ingredientOverlayUpdateScreenMethod;
    private static volatile Method leftAreaUpdateScreenMethod;
    private static volatile Method baublesLateTooltipMethod;
    private static volatile Method containerRenderHoveredTooltipMethod;

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

    public static void drawAfterHeiPostTooltips(GuiScreen screen, int mouseX, int mouseY) {
        if (!(screen instanceof GuiContainer)) {
            return;
        }

        GuiContainer container = (GuiContainer) screen;
        if (drawBaublesLateTooltip(container, mouseX, mouseY)) {
            return;
        }
        drawContainerHoveredTooltip(container, mouseX, mouseY);
    }

    private static boolean drawBaublesLateTooltip(GuiContainer screen, int mouseX, int mouseY) {
        try {
            Method method = baublesLateTooltipMethod;
            if (method == null) {
                Class<?> owner = Class.forName(
                        "com.l.gpom.compat.baubles.BaublesSideSlotsClient",
                        false,
                        HeiOverlayLayeringCompat.class.getClassLoader()
                );
                method = findMethod(owner, new Class<?>[] {GuiContainer.class, int.class, int.class}, "drawLateSideSlotTooltip");
                baublesLateTooltipMethod = method;
            }
            if (method != null) {
                Object value = method.invoke(null, screen, mouseX, mouseY);
                return value instanceof Boolean && (Boolean) value;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void drawContainerHoveredTooltip(GuiContainer screen, int mouseX, int mouseY) {
        try {
            Method method = containerRenderHoveredTooltipMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(screen.getClass())) {
                method = findMethod(screen.getClass(), new Class<?>[] {int.class, int.class},
                        "func_191948_b",
                        "renderHoveredToolTip");
                containerRenderHoveredTooltipMethod = method;
            }
            if (method != null) {
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(0.0F, 0.0F, POST_HEI_TOOLTIP_Z);
                    method.invoke(screen, mouseX, mouseY);
                } finally {
                    GL11.glPopMatrix();
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
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
