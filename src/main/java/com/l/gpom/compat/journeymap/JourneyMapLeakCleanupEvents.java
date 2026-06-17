package com.l.gpom.compat.journeymap;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
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
        World world = event.getWorld();
        if (world instanceof WorldClient) {
            if (JourneyMapLeakCleanup.isClientWorldUnloadCleanupSuppressed()) {
                return;
            }
            JourneyMapLeakCleanup.cleanup("client world unload");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void clientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        JourneyMapLeakCleanup.cleanup("client disconnect");
    }

}
