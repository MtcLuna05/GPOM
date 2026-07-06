package com.l.gpom.profiling;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class WorldLifecycleProfilerEvents {
    private static final WorldLifecycleProfilerEvents INSTANCE = new WorldLifecycleProfilerEvents();
    private static boolean registered;

    private WorldLifecycleProfilerEvents() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.worldLifecycleProfilerEnabled()) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (isClientWorld(event.getWorld())) {
            WorldLifecycleProfiler.worldEvent("load", event.getWorld());
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (isClientWorld(event.getWorld())) {
            WorldLifecycleProfiler.worldEvent("unload", event.getWorld());
        }
    }

    private static boolean isClientWorld(World world) {
        return MinecraftMappingCompat.worldIsRemote(world);
    }
}
