package com.l.gpom.compat.baubles;

import com.l.gpom.GPOM;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

import java.lang.reflect.Method;

public final class BaublesSideSlotsVanillaBridge {
    private static volatile Method getBaublesHandlerMethod;
    private static boolean loggedFailure;

    private BaublesSideSlotsVanillaBridge() {
    }

    public static void prepare(Container container,
                               EntityPlayer player) {
        Object handler = baublesHandler(player);
        BaublesSideSlotsCommon.prepareVanillaInventoryContainerObject(container, handler, player);
    }

    private static Object baublesHandler(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        try {
            Method method = getBaublesHandlerMethod;
            if (method == null) {
                Class<?> apiClass = Class.forName("baubles.api.BaublesApi", false, BaublesSideSlotsVanillaBridge.class.getClassLoader());
                method = apiClass.getMethod("getBaublesHandler", EntityPlayer.class);
                getBaublesHandlerMethod = method;
            }
            return method.invoke(null, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            if (!loggedFailure) {
                loggedFailure = true;
                GPOM.LOGGER.warn("[GPOM Baubles] Could not resolve Baubles handler for vanilla inventory side slots", exception);
            }
            return null;
        }
    }
}
