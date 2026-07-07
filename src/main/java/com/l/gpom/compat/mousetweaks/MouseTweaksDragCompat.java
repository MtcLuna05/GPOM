package com.l.gpom.compat.mousetweaks;

import com.l.gpom.GPOM;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Method;

public final class MouseTweaksDragCompat {
    private static final String MOD_ID = "mousetweaks";
    private static final String MAIN_CLASS = "yalter.mousetweaks.Main";
    private static final MouseTweaksDragCompat INSTANCE = new MouseTweaksDragCompat();

    private static boolean registered;
    private static boolean loggedFailure;
    private static Method onUpdateInGame;
    private static Method onMouseInput;

    private MouseTweaksDragCompat() {
    }

    public static void register() {
        if (registered || !Loader.isModLoaded(MOD_ID)) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawContainer(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }
        invoke("onUpdateInGame", true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.getGui() instanceof GuiContainer)) {
            return;
        }
        int button = Mouse.getEventButton();
        if (button == 0 || button == 1) {
            invoke("onMouseInput", false);
        }
    }

    private static void invoke(String methodName, boolean update) {
        try {
            Method method = update ? onUpdateInGame : onMouseInput;
            if (method == null) {
                method = Class.forName(MAIN_CLASS).getMethod(methodName);
                method.setAccessible(true);
                if (update) {
                    onUpdateInGame = method;
                } else {
                    onMouseInput = method;
                }
            }
            method.invoke(null);
        } catch (Throwable throwable) {
            if (!loggedFailure) {
                loggedFailure = true;
                GPOM.LOGGER.warn("[GPOM MouseTweaks] Failed to run Mouse Tweaks drag compatibility hook", throwable);
            }
        }
    }
}
