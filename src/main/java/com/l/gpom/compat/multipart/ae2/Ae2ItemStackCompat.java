package com.l.gpom.compat.multipart.ae2;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Ae2ItemStackCompat {
    private static final ItemStack EMPTY_STACK = findEmptyStack();
    private static final Method IS_EMPTY = findMethod(ItemStack.class, "isEmpty", "func_190926_b");
    private static final Method GET_ITEM = findMethod(ItemStack.class, "getItem", "func_77973_b");
    private static final Method COPY = findMethod(ItemStack.class, "copy", "func_77946_l");
    private static final Method SET_COUNT = findMethod(ItemStack.class, "setCount", "func_190920_e", int.class);
    private static final Method SHRINK = findMethod(ItemStack.class, "shrink", "func_190918_g", int.class);
    private static final Method GET_COUNT = findMethod(ItemStack.class, "getCount", "func_190916_E");
    private static final Method WRITE_TO_NBT = findMethod(ItemStack.class, "writeToNBT", "func_77955_b", NBTTagCompound.class);
    private static final Field COUNT_FIELD = findField(ItemStack.class, "stackSize", "field_77994_a");
    private static final Constructor<ItemStack> NBT_CONSTRUCTOR = findConstructor(ItemStack.class, NBTTagCompound.class);

    private Ae2ItemStackCompat() {
    }

    static ItemStack emptyStack() {
        return EMPTY_STACK;
    }

    static boolean isEmpty(ItemStack stack) {
        if (stack == null || stack == EMPTY_STACK) {
            return true;
        }
        if (IS_EMPTY != null) {
            try {
                return Boolean.TRUE.equals(IS_EMPTY.invoke(stack));
            } catch (Throwable throwable) {
                logBridgeFailure("ItemStack.isEmpty", throwable);
            }
        }
        return count(stack) <= 0 || item(stack) == null;
    }

    static Item item(ItemStack stack) {
        if (stack == null || GET_ITEM == null) {
            return null;
        }
        try {
            Object value = GET_ITEM.invoke(stack);
            return value instanceof Item ? (Item) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("ItemStack.getItem", throwable);
            return null;
        }
    }

    static ItemStack copy(ItemStack stack) {
        if (isEmpty(stack) || COPY == null) {
            return emptyStack();
        }
        try {
            Object value = COPY.invoke(stack);
            return value instanceof ItemStack ? (ItemStack) value : emptyStack();
        } catch (Throwable throwable) {
            logBridgeFailure("ItemStack.copy", throwable);
            return emptyStack();
        }
    }

    static void setCount(ItemStack stack, int count) {
        if (stack == null) {
            return;
        }
        if (SET_COUNT != null) {
            try {
                SET_COUNT.invoke(stack, count);
                return;
            } catch (Throwable throwable) {
                logBridgeFailure("ItemStack.setCount", throwable);
            }
        }
        setCountField(stack, count);
    }

    static void shrink(ItemStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return;
        }
        if (SHRINK != null) {
            try {
                SHRINK.invoke(stack, amount);
                return;
            } catch (Throwable throwable) {
                logBridgeFailure("ItemStack.shrink", throwable);
            }
        }
        setCount(stack, Math.max(0, count(stack) - amount));
    }

    static NBTTagCompound writeToNbt(ItemStack stack) {
        if (isEmpty(stack) || WRITE_TO_NBT == null) {
            return null;
        }
        NBTTagCompound tag = new NBTTagCompound();
        try {
            Object value = WRITE_TO_NBT.invoke(stack, tag);
            return value instanceof NBTTagCompound ? (NBTTagCompound) value : tag;
        } catch (Throwable throwable) {
            logBridgeFailure("ItemStack.writeToNBT", throwable);
            return null;
        }
    }

    static ItemStack readFromNbt(NBTTagCompound tag) {
        if (tag == null || NBT_CONSTRUCTOR == null) {
            return emptyStack();
        }
        try {
            ItemStack stack = NBT_CONSTRUCTOR.newInstance(tag);
            return isEmpty(stack) ? emptyStack() : stack;
        } catch (Throwable throwable) {
            logBridgeFailure("ItemStack(NBTTagCompound)", throwable);
            return emptyStack();
        }
    }

    private static int count(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        if (GET_COUNT != null) {
            try {
                Object value = GET_COUNT.invoke(stack);
                return value instanceof Integer ? (Integer) value : 0;
            } catch (Throwable throwable) {
                logBridgeFailure("ItemStack.getCount", throwable);
            }
        }
        if (COUNT_FIELD != null) {
            try {
                return COUNT_FIELD.getInt(stack);
            } catch (Throwable throwable) {
                logBridgeFailure("ItemStack.stackSize", throwable);
            }
        }
        return 1;
    }

    private static void setCountField(ItemStack stack, int count) {
        if (COUNT_FIELD == null) {
            return;
        }
        try {
            COUNT_FIELD.setInt(stack, count);
        } catch (Throwable throwable) {
            logBridgeFailure("ItemStack.stackSize write", throwable);
        }
    }

    private static ItemStack findEmptyStack() {
        Field field = findField(ItemStack.class, "EMPTY", "field_190927_a");
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(null);
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("ItemStack.EMPTY", throwable);
            return null;
        }
    }

    private static Constructor<ItemStack> findConstructor(Class<ItemStack> type, Class<?>... parameterTypes) {
        try {
            Constructor<ItemStack> constructor = type.getConstructor(parameterTypes);
            constructor.setAccessible(true);
            return constructor;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(type, mcpName, parameterTypes);
        return method != null ? method : findMethod(type, srgName, parameterTypes);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String mcpName, String srgName) {
        Field field = findField(type, mcpName);
        return field != null ? field : findField(type, srgName);
    }

    private static Field findField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logBridgeFailure(String operation, Throwable throwable) {
        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.warn("[GPOM Multipart] {} bridge failed", operation, throwable);
        }
    }
}
