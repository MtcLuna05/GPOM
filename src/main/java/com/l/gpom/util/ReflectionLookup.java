package com.l.gpom.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ReflectionLookup {
    private ReflectionLookup() {
    }

    public static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return findMethod(type, new String[] {mcpName, srgName}, parameterTypes);
    }

    public static Method findMethod(Class<?> type, String[] names, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        NoSuchMethodException failure = null;
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException exception) {
                failure = exception;
            }

            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException exception) {
                    failure = exception;
                }
            }
        }
        throw failure == null ? new NoSuchMethodException(type.getName()) : failure;
    }

    public static Field findField(Class<?> type, String mcpName, String srgName) throws NoSuchFieldException {
        return findField(type, new String[] {mcpName, srgName});
    }

    public static Field findField(Class<?> type, String[] names) throws NoSuchFieldException {
        NoSuchFieldException failure = null;
        for (String name : names) {
            try {
                Field field = type.getField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException exception) {
                failure = exception;
            }

            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException exception) {
                    failure = exception;
                }
            }
        }
        throw failure == null ? new NoSuchFieldException(type.getName()) : failure;
    }
}
