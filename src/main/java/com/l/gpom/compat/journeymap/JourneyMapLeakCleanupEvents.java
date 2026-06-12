package com.l.gpom.compat.journeymap;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class JourneyMapLeakCleanupEvents {
    private static boolean registered;

    private JourneyMapLeakCleanupEvents() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.journeyMapCleanupLeaksEnabled()) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new JourneyMapLeakCleanupEvents());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void worldUnload(WorldEvent.Unload event) {
        try {
            if (event.getWorld() == null || event.getWorld().isRemote) {
                JourneyMapLeakCleanup.cleanup("client world unload");
            }
        } catch (Throwable ignored) {
            JourneyMapLeakCleanup.cleanup("client world unload fallback");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void clientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        JourneyMapLeakCleanup.cleanup("client disconnect");
    }

}
