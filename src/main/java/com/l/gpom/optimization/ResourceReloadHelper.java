package com.l.gpom.optimization;

import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

final class ResourceReloadHelper {
    private ResourceReloadHelper() {
    }

    @SuppressWarnings("unchecked")
    static boolean appendReloadListener(IReloadableResourceManager manager, IResourceManagerReloadListener listener) throws ReflectiveOperationException {
        if (manager == null || listener == null) {
            return false;
        }
        Field field = findField(manager.getClass(), "reloadListeners", "field_110546_b");
        if (field == null) {
            return false;
        }
        Object value = field.get(manager);
        if (!(value instanceof List)) {
            return false;
        }
        List<IResourceManagerReloadListener> listeners = (List<IResourceManagerReloadListener>) value;
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
        return true;
    }

    static void registerReloadListener(IReloadableResourceManager manager, IResourceManagerReloadListener listener) throws ReflectiveOperationException {
        if (manager == null || listener == null) {
            return;
        }
        Method register = findMethod(manager.getClass(), IResourceManagerReloadListener.class, "registerReloadListener", "func_110542_a");
        if (register == null) {
            throw new NoSuchMethodException(manager.getClass().getName() + ".registerReloadListener");
        }
        register.invoke(manager, listener);
    }

    static void invokeReload(IResourceManager manager, IResourceManagerReloadListener listener) throws ReflectiveOperationException {
        if (manager == null || listener == null) {
            return;
        }
        Method reload = findMethod(listener.getClass(), IResourceManager.class, "onResourceManagerReload", "func_110549_a");
        if (reload == null) {
            throw new NoSuchMethodException(listener.getClass().getName() + ".onResourceManagerReload/func_110549_a");
        }
        reload.invoke(listener, manager);
    }

    private static Field findField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> type, Class<?> parameterType, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterType);
                    method.setAccessible(true);
                    return method;
                } catch (ReflectiveOperationException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
