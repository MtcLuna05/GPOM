package com.l.gpom.util;

import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;

import java.util.Map;

public final class GpomRemoteEnvironment {
    private static volatile boolean connectedToRemoteServer;
    private static volatile boolean remoteServerHasGpom = true;
    private static volatile String remoteConnectionType = "NONE";

    private GpomRemoteEnvironment() {
    }

    public static void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        boolean local = event != null && event.isLocal();
        connectedToRemoteServer = !local;
        remoteConnectionType = event == null ? "UNKNOWN" : event.getConnectionType();
        remoteServerHasGpom = local || remoteModListHasGpom(event == null ? null : event.getManager());
        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[GPOM ClientOnly] Connection type={} local={} remoteHasGpom={} serverFeaturesAllowed={}",
                    remoteConnectionType,
                    local,
                    remoteServerHasGpom,
                    serverFeaturesAllowed()
            );
        }
    }

    public static void onClientDisconnected() {
        connectedToRemoteServer = false;
        remoteServerHasGpom = true;
        remoteConnectionType = "NONE";
    }

    public static boolean serverFeaturesAllowed() {
        return !connectedToRemoteServer || remoteServerHasGpom;
    }

    public static boolean connectedRemoteServerHasGpom() {
        return connectedToRemoteServer && remoteServerHasGpom;
    }

    public static boolean connectedRemoteServerMissingGpom() {
        return connectedToRemoteServer && !remoteServerHasGpom;
    }

    public static String remoteConnectionType() {
        return remoteConnectionType;
    }

    private static boolean remoteModListHasGpom(NetworkManager manager) {
        if (manager == null) {
            return false;
        }
        try {
            NetworkDispatcher dispatcher = NetworkDispatcher.get(manager);
            if (dispatcher == null) {
                return false;
            }
            Map<String, String> modList = dispatcher.getModList();
            return modList != null && modList.containsKey(Reference.MOD_ID);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
