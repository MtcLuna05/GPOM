package com.l.gpom.compat.ae2;

import net.minecraft.inventory.Slot;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MouseTweaksAe2TerminalCompat {
    private static final String SLOT_ME = "appeng.client.me.SlotME";
    private static final String SLOT_PLAYER_INV = "appeng.container.slot.SlotPlayerInv";
    private static final String SLOT_PLAYER_HOTBAR = "appeng.container.slot.SlotPlayerHotBar";
    private static final ConcurrentMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_FIELDS = ConcurrentHashMap.newKeySet();

    private MouseTweaksAe2TerminalCompat() {
    }

    public static boolean hasTerminalSlots(Object gui) {
        for (Object slot : inventorySlots(gui)) {
            if (slot != null && SLOT_ME.equals(slot.getClass().getName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isIgnored(Slot slot) {
        if (slot == null) {
            return true;
        }

        if (Mouse.isButtonDown(1)) {
            return true;
        }

        String className = slot.getClass().getName();
        return !SLOT_ME.equals(className)
                && !SLOT_PLAYER_INV.equals(className)
                && !SLOT_PLAYER_HOTBAR.equals(className);
    }

    @SuppressWarnings("unchecked")
    private static List<?> inventorySlots(Object gui) {
        Object container = readField(gui, "field_147002_h", "inventorySlots");
        Object slots = readField(container, "field_75151_b", "inventorySlots");
        return slots instanceof List ? (List<?>) slots : Collections.emptyList();
    }

    private static Object readField(Object owner, String... names) {
        if (owner == null) {
            return null;
        }

        Field field = field(owner.getClass(), names);
        if (field == null) {
            return null;
        }

        try {
            return field.get(owner);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field field(Class<?> ownerClass, String... names) {
        String key = ownerClass.getName() + '#' + names[0];
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        if (MISSING_FIELDS.contains(key)) {
            return null;
        }

        Class<?> current = ownerClass;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    FIELDS.putIfAbsent(key, field);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }

        MISSING_FIELDS.add(key);
        return null;
    }
}
