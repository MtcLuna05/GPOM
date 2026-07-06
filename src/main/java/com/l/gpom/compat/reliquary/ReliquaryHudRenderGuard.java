package com.l.gpom.compat.reliquary;

import com.l.gpom.GPOM;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReliquaryHudRenderGuard {
    private static final String RELIQUARY_CLIENT_HANDLER = "xreliquary.handler.ClientEventHandler";
    private static final String RENDER_TICK_EVENT = "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent";

    private static volatile Object minecraft;
    private static volatile Field worldField;
    private static volatile Field playerField;
    private static volatile boolean unavailableLogged;

    private ReliquaryHudRenderGuard() {
    }

    public static boolean shouldSkip(Class<?> subscriberClass, String methodName, Event event) {
        if (subscriberClass == null
                || event == null
                || !"onRenderTick".equals(methodName)
                || !RELIQUARY_CLIENT_HANDLER.equals(subscriberClass.getName())
                || !RENDER_TICK_EVENT.equals(event.getClass().getName())) {
            return false;
        }

        ClientState state = clientState();
        return state == ClientState.UNAVAILABLE || state == ClientState.NO_WORLD_OR_PLAYER;
    }

    private static ClientState clientState() {
        try {
            Object mc = minecraft;
            if (mc == null) {
                Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
                Method getMinecraft = findMethod(minecraftClass, "func_71410_x", "getMinecraft");
                mc = getMinecraft.invoke(null);
                minecraft = mc;
            }
            if (mc == null) {
                return ClientState.UNAVAILABLE;
            }

            Field world = field(mc.getClass(), "field_71441_e", "world");
            Field player = field(mc.getClass(), "field_71439_g", "player");
            return world.get(mc) == null || player.get(mc) == null
                    ? ClientState.NO_WORLD_OR_PLAYER
                    : ClientState.READY;
        } catch (Throwable throwable) {
            if (!unavailableLogged) {
                unavailableLogged = true;
                GPOM.LOGGER.warn("[GPOM Reliquary] Unable to inspect Minecraft client state; skipping Reliquary HUD render until available", throwable);
            }
            return ClientState.UNAVAILABLE;
        }
    }

    private static Method findMethod(Class<?> owner, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + " " + java.util.Arrays.toString(names));
    }

    private static Field field(Class<?> owner, String srgName, String mcpName) throws NoSuchFieldException {
        Field cached = "field_71441_e".equals(srgName) ? worldField : playerField;
        if (cached != null) {
            return cached;
        }

        Field resolved = findField(owner, srgName, mcpName);
        if ("field_71441_e".equals(srgName)) {
            worldField = resolved;
        } else {
            playerField = resolved;
        }
        return resolved;
    }

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getName() + " " + java.util.Arrays.toString(names));
    }

    private enum ClientState {
        READY,
        NO_WORLD_OR_PLAYER,
        UNAVAILABLE
    }
}
