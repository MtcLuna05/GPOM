package com.l.gpom.util;

import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.relauncher.FMLLaunchHandler;

public final class GpomSide {
    private GpomSide() {
    }

    public static boolean isClientLaunch() {
        try {
            return FMLLaunchHandler.side().isClient();
        } catch (Throwable ignored) {
        }

        try {
            Object side = Launch.blackboard.get("fml.side");
            return side != null && "CLIENT".equalsIgnoreCase(side.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isDedicatedServerLaunch() {
        try {
            return FMLLaunchHandler.side().isServer();
        } catch (Throwable ignored) {
        }

        try {
            Object side = Launch.blackboard.get("fml.side");
            return side != null && "SERVER".equalsIgnoreCase(side.toString());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
