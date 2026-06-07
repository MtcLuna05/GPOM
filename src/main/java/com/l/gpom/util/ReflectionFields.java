package com.l.gpom.util;

import com.l.gpom.GPOM;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ReflectionFields {
    private static final ConcurrentMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Set<String> MISSING_FIELDS = ConcurrentHashMap.newKeySet();

    private ReflectionFields() {
    }

    public static Object get(Object owner, String purpose, String... names) {
        Field field = find(owner.getClass(), purpose, names);
        if (field == null) {
            return null;
        }

        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            logOnce(owner.getClass(), purpose, "could not read", e);
            return null;
        }
    }

    public static Object getStatic(Class<?> ownerClass, String purpose, String... names) {
        Field field = find(ownerClass, purpose, names);
        if (field == null) {
            return null;
        }

        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            logOnce(ownerClass, purpose, "could not read", e);
            return null;
        }
    }

    public static void set(Object owner, Object value, String purpose, String... names) {
        Field field = find(owner.getClass(), purpose, names);
        if (field == null) {
            return;
        }

        try {
            field.set(owner, value);
        } catch (IllegalAccessException e) {
            logOnce(owner.getClass(), purpose, "could not write", e);
        }
    }

    private static Field find(Class<?> ownerClass, String purpose, String... names) {
        String key = ownerClass.getName() + '#' + purpose;
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
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
                    // Try all known runtime names before logging a miss.
                }
            }
            current = current.getSuperclass();
        }

        logOnce(ownerClass, purpose, "could not find", null);
        return null;
    }

    private static void logOnce(Class<?> ownerClass, String purpose, String action, Exception e) {
        String key = ownerClass.getName() + '#' + purpose + '#' + action;
        if (!MISSING_FIELDS.add(key)) {
            return;
        }

        if (e == null) {
            GPOM.LOGGER.warn("Skipping cleanup for {}.{}: {} field", ownerClass.getName(), purpose, action);
        } else {
            GPOM.LOGGER.warn("Skipping cleanup for {}.{}: {} field", ownerClass.getName(), purpose, action, e);
        }
    }
}
