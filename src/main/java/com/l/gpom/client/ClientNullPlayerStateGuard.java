package com.l.gpom.client;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public final class ClientNullPlayerStateGuard {
    private static final long CACHE_NANOS = 1_000_000L;
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);
    private static volatile Field worldField;
    private static volatile Field playerField;
    private static volatile Field currentScreenField;
    private static volatile Field inGameHasFocusField;

    private ClientNullPlayerStateGuard() {
    }

    public static boolean hasNoWorldOrPlayer(Minecraft minecraft) {
        return state(minecraft, false).noWorldOrPlayer;
    }

    public static boolean isUnsafeGameplayFrame(Minecraft minecraft) {
        return state(minecraft, false).unsafeGameplayFrame;
    }

    public static boolean isUnsafeCurrentClientGameplayFrame() {
        try {
            return isUnsafeGameplayFrame(Minecraft.getMinecraft());
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean hasNoCurrentClientWorldOrPlayer() {
        try {
            return hasNoWorldOrPlayer(Minecraft.getMinecraft());
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static boolean refreshAndIsUnsafeCurrentClientGameplayFrame() {
        try {
            return state(Minecraft.getMinecraft(), true).unsafeGameplayFrame;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void invalidate() {
        STATE.get().expiresAt = 0L;
    }

    public static void clearInGameFocusIfNoPlayer(Minecraft minecraft) {
        if (!state(minecraft, true).noWorldOrPlayer) {
            return;
        }
        try {
            Field field = inGameHasFocusField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71415_G", "inGameHasFocus");
                inGameHasFocusField = field;
            }
            if (field != null) {
                field.setBoolean(minecraft, false);
            }
        } catch (Throwable ignored) {
        }
    }

    private static State state(Minecraft minecraft, boolean force) {
        State state = STATE.get();
        if (minecraft == null) {
            state.minecraft = null;
            state.noWorldOrPlayer = true;
            state.unsafeGameplayFrame = true;
            state.expiresAt = Long.MAX_VALUE;
            return state;
        }
        long now = System.nanoTime();
        if (!force && state.minecraft == minecraft && now < state.expiresAt) {
            return state;
        }

        Object world = fieldValue(minecraft, worldField, "field_71441_e", "world");
        Object player = fieldValue(minecraft, playerField, "field_71439_g", "player");
        boolean noWorldOrPlayer = world == null || player == null;
        boolean unsafe = noWorldOrPlayer && fieldValue(minecraft, currentScreenField, "field_71462_r", "currentScreen") == null;
        state.minecraft = minecraft;
        state.noWorldOrPlayer = noWorldOrPlayer;
        state.unsafeGameplayFrame = unsafe;
        state.expiresAt = now + CACHE_NANOS;
        return state;
    }

    private static Object fieldValue(Minecraft minecraft, Field cached, String srgName, String mcpName) {
        try {
            Field field = cached;
            if (field == null) {
                field = findField(Minecraft.class, srgName, mcpName);
                if ("field_71441_e".equals(srgName)) {
                    worldField = field;
                } else if ("field_71439_g".equals(srgName)) {
                    playerField = field;
                } else if ("field_71462_r".equals(srgName)) {
                    currentScreenField = field;
                }
            }
            return field == null ? null : field.get(minecraft);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String... names) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = cursor.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        return null;
    }

    private static final class State {
        Minecraft minecraft;
        long expiresAt;
        boolean noWorldOrPlayer = true;
        boolean unsafeGameplayFrame = true;
    }
}
