package com.l.gpom.client;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class MainMenuStartupOverlayEvents {
    private static final MainMenuStartupOverlayEvents INSTANCE = new MainMenuStartupOverlayEvents();
    private static boolean registered;

    private MainMenuStartupOverlayEvents() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDrawScreenPost(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (!MainMenuStartupOverlay.isSupportedMainMenu(screen)) {
            return;
        }
        MainMenuStartupOverlay.renderFromScreen(screen, screen.getClass().getName());
    }
}
