package com.l.gpom.compat.agricraft;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AgriCraftChannelConnectionCompat {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.agricraft.refreshChannelsAfterBulkPlacement", "true"));
    private static final boolean SRG_RUNTIME = detectSrgRuntime();

    private static volatile Class<?> connectableClass;
    private static volatile boolean connectableClassUnavailable;
    private static final Map<Class<?>, Method> REFRESH_CONNECTIONS_METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();

    private AgriCraftChannelConnectionCompat() {
    }

    public static void onSetBlockState(World world, BlockPos pos, IBlockState state, boolean changed) {
        if (!ENABLED || !changed || world == null || pos == null || !isAgricraftWaterChannel(state)) {
            return;
        }

        refreshConnectable(world, pos);
        for (EnumFacing facing : EnumFacing.values()) {
            Object offset = invokeNamed(pos, new String[]{"func_177972_a", "offset"}, new Class<?>[]{EnumFacing.class}, facing);
            if (offset instanceof BlockPos) {
                refreshConnectable(world, (BlockPos) offset);
            }
        }
    }

    private static boolean isAgricraftWaterChannel(IBlockState state) {
        if (state == null) {
            return false;
        }
        Object block = invokeNamed(state, new String[]{"func_177230_c", "getBlock"}, new Class<?>[0]);
        Object registryName = registryName(block);
        String namespace = resourceNamespace(registryName);
        String path = resourcePath(registryName);
        return "agricraft".equals(namespace) && path != null && path.startsWith("water_channel_");
    }

    private static Object registryName(Object block) {
        if (block == null) {
            return null;
        }

        Object registryName = invokeNamed(block, new String[]{"getRegistryName"}, new Class<?>[0]);
        if (registryName != null) {
            return registryName;
        }

        try {
            Class<?> blockClass = Class.forName("net.minecraft.block.Block", false,
                    AgriCraftChannelConnectionCompat.class.getClassLoader());
            Field registryField = fieldNamed(blockClass, "field_149771_c", "REGISTRY");
            Object registry = registryField == null ? null : registryField.get(null);
            return registry == null ? null : invokeNamed(registry, new String[]{"func_177774_c", "getNameForObject"}, new Class<?>[]{Object.class}, block);
        } catch (ClassNotFoundException | IllegalAccessException | LinkageError ignored) {
            return null;
        }
    }

    private static String resourceNamespace(Object resourceLocation) {
        Object namespace = invokeNamed(resourceLocation, new String[]{"func_110624_b", "getNamespace", "getResourceDomain"}, new Class<?>[0]);
        if (namespace instanceof String) {
            return (String) namespace;
        }
        String text = resourceLocation == null ? null : resourceLocation.toString();
        int separator = text == null ? -1 : text.indexOf(':');
        return separator >= 0 ? text.substring(0, separator) : null;
    }

    private static String resourcePath(Object resourceLocation) {
        Object path = invokeNamed(resourceLocation, new String[]{"func_110623_a", "getPath", "getResourcePath"}, new Class<?>[0]);
        if (path instanceof String) {
            return (String) path;
        }
        String text = resourceLocation == null ? null : resourceLocation.toString();
        int separator = text == null ? -1 : text.indexOf(':');
        return separator >= 0 ? text.substring(separator + 1) : text;
    }

    private static void refreshConnectable(World world, BlockPos pos) {
        Object tile = invokeNamed(world, new String[]{"func_175625_s", "getTileEntity"}, new Class<?>[]{BlockPos.class}, pos);
        if (!(tile instanceof TileEntity) || !isAgriConnectable((TileEntity) tile)) {
            return;
        }

        Method method = refreshConnectionsMethod(tile.getClass());
        if (method == null) {
            return;
        }

        try {
            method.invoke(tile);
            invokeNamed(tile, new String[]{"func_70296_d", "markDirty"}, new Class<?>[0]);
            Object state = invokeNamed(world, new String[]{"func_180495_p", "getBlockState"}, new Class<?>[]{BlockPos.class}, pos);
            if (isRemote(world)) {
                invokeNamed(world, new String[]{"func_175704_b", "markBlockRangeForRenderUpdate"}, new Class<?>[]{BlockPos.class, BlockPos.class}, pos, pos);
            } else if (state instanceof IBlockState) {
                invokeNamed(world, new String[]{"func_184138_a", "notifyBlockUpdate"},
                        new Class<?>[]{BlockPos.class, IBlockState.class, IBlockState.class, int.class}, pos, state, state, 3);
            }
        } catch (IllegalAccessException ignored) {
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException(cause);
        }
    }

    private static boolean isRemote(World world) {
        Object value = fieldValue(world, "field_72995_K", "isRemote");
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isAgriConnectable(TileEntity tile) {
        Class<?> type = connectableClass();
        return type != null && type.isInstance(tile);
    }

    private static Class<?> connectableClass() {
        if (connectableClassUnavailable) {
            return null;
        }
        Class<?> type = connectableClass;
        if (type != null) {
            return type;
        }
        try {
            type = Class.forName("com.infinityraider.agricraft.api.v1.misc.IAgriConnectable", false,
                    AgriCraftChannelConnectionCompat.class.getClassLoader());
            connectableClass = type;
            return type;
        } catch (ClassNotFoundException | LinkageError ignored) {
            connectableClassUnavailable = true;
            return null;
        }
    }

    private static Method refreshConnectionsMethod(Class<?> tileClass) {
        Method cached = REFRESH_CONNECTIONS_METHODS.get(tileClass);
        if (cached != null) {
            return cached;
        }
        try {
            Method method = tileClass.getMethod("refreshConnections");
            method.setAccessible(true);
            REFRESH_CONNECTIONS_METHODS.put(tileClass, method);
            return method;
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    private static Object invokeNamed(Object target, String[] names, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        Method method = methodNamed(target.getClass(), names, parameterTypes);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method methodNamed(Class<?> owner, String[] names, Class<?>[] parameterTypes) {
        String key = owner.getName() + '#' + String.join("|", names) + '#' + parameterTypes.length;
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        for (String name : orderedNames(names)) {
            Method method = findMethod(owner, name, parameterTypes);
            if (method != null) {
                method.setAccessible(true);
                METHODS.put(key, method);
                return method;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        Class<?> type = owner;
        while (type != null) {
            try {
                return type.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && parameterTypesMatch(method.getParameterTypes(), parameterTypes)) {
                return method;
            }
        }
        return null;
    }

    private static boolean parameterTypesMatch(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!wrap(actual[i]).isAssignableFrom(wrap(expected[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    private static Object fieldValue(Object target, String... names) {
        if (target == null) {
            return null;
        }
        Field field = fieldNamed(target.getClass(), names);
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    private static Field fieldNamed(Class<?> owner, String... names) {
        String key = owner.getName() + '#' + String.join("|", names);
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        for (String name : orderedNames(names)) {
            Class<?> type = owner;
            while (type != null) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    FIELDS.put(key, field);
                    return field;
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
        }
        return null;
    }

    private static String[] orderedNames(String... names) {
        if (names.length < 2) {
            return names;
        }
        String[] ordered = new String[names.length];
        int index = 0;
        for (String name : names) {
            if (isSrgName(name) == SRG_RUNTIME) {
                ordered[index++] = name;
            }
        }
        for (String name : names) {
            if (isSrgName(name) != SRG_RUNTIME) {
                ordered[index++] = name;
            }
        }
        return ordered;
    }

    private static boolean isSrgName(String name) {
        return name.startsWith("func_") || name.startsWith("field_");
    }

    private static boolean detectSrgRuntime() {
        try {
            Class<?> stateClass = Class.forName(
                    "net.minecraft.block.state.IBlockState",
                    false,
                    AgriCraftChannelConnectionCompat.class.getClassLoader()
            );
            stateClass.getMethod("func_177230_c");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
