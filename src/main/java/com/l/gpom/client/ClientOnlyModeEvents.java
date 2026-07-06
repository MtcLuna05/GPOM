package com.l.gpom.client;

import com.l.gpom.util.GpomRemoteEnvironment;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ClientOnlyModeEvents {
    private static boolean registered;

    private ClientOnlyModeEvents() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new ClientOnlyModeEvents());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void clientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        GpomRemoteEnvironment.onClientConnected(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void clientDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        GpomRemoteEnvironment.onClientDisconnected();
    }
}
