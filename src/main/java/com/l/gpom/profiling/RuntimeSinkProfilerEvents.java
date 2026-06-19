package com.l.gpom.profiling;

import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;

public final class RuntimeSinkProfilerEvents {
    private static final RuntimeSinkProfilerEvents INSTANCE = new RuntimeSinkProfilerEvents();
    private static final ThreadLocal<Map<String, Long>> STARTS = ThreadLocal.withInitial(HashMap::new);
    private static boolean registered;

    private RuntimeSinkProfilerEvents() {
    }

    public static void register() {
        if (registered || !RuntimeSinkProfiler.enabled()) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        FMLCommonHandler.instance().bus().register(INSTANCE);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        phase("tick", "client", event.phase);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        phase("tick", "render", event.phase);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        phase("tick", "server", event.phase);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        phase("tick", "world dim=" + dimension(event.world) + " side=" + event.side, event.phase);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        phase("tick", "player side=" + event.side, event.phase);
    }

    private static void phase(String category, String label, TickEvent.Phase phase) {
        if (!RuntimeSinkProfiler.enabled()) {
            return;
        }
        String key = category + ' ' + label;
        Map<String, Long> starts = STARTS.get();
        if (phase == TickEvent.Phase.START) {
            starts.put(key, RuntimeSinkProfiler.begin());
            return;
        }
        Long startedAt = starts.remove(key);
        if (startedAt != null) {
            RuntimeSinkProfiler.end(category, label, startedAt);
        }
    }

    private static int dimension(World world) {
        try {
            return world == null || world.provider == null ? Integer.MIN_VALUE : world.provider.getDimension();
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }
}
