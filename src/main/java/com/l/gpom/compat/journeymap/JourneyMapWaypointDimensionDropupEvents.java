package com.l.gpom.compat.journeymap;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class JourneyMapWaypointDimensionDropupEvents {
    private static final JourneyMapWaypointDimensionDropupEvents INSTANCE = new JourneyMapWaypointDimensionDropupEvents();
    private static boolean registered;

    private JourneyMapWaypointDimensionDropupEvents() {
    }

    public static void register() {
        if (registered
                || !GpomEarlyConfig.journeyMapWaypointDimensionDropupEnabled()
                || !classPresent("journeymap.client.ui.waypoint.WaypointManager")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        JourneyMapWaypointDimensionDropup.draw(event.getGui(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (JourneyMapWaypointDimensionDropup.mouseInput(event.getGui())) {
            cancel(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (JourneyMapWaypointDimensionDropup.actionPerformed(event.getGui(), event.getButton())) {
            cancel(event);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (JourneyMapWaypointDimensionDropup.keyboardInput(event.getGui())) {
            cancel(event);
        }
    }

    private static void cancel(Event event) {
        try {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, JourneyMapWaypointDimensionDropupEvents.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
