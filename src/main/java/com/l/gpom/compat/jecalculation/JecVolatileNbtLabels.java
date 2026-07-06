package com.l.gpom.compat.jecalculation;

import com.l.gpom.util.ReflectionLookup;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

public final class JecVolatileNbtLabels {
    private static final Method NBT_HAS_NO_TAGS = findMethod(NBTTagCompound.class, new String[]{"hasNoTags", "func_82582_d"});
    private static final Method NBT_GET_KEY_SET = findMethod(NBTTagCompound.class, new String[]{"getKeySet", "func_150296_c"});

    private JecVolatileNbtLabels() {
    }

    public static void normalize(Object label) {
        if (label == null) {
            return;
        }
        try {
            boolean fuzzyNbt = booleanField(label, "fNbt");
            boolean fuzzyCap = booleanField(label, "fCap");
            NBTTagCompound nbt = tagField(label, "nbt");
            NBTTagCompound cap = tagField(label, "cap");
            boolean shouldFuzzyNbt = !fuzzyNbt && hasTags(nbt) && (isKnownVolatileItem(label) || hasVolatileKey(nbt));
            boolean shouldFuzzyCap = !fuzzyCap && hasTags(cap);
            if (shouldFuzzyNbt) {
                invokeFuzzySetter(label, "setFNbt");
            }
            if (shouldFuzzyCap) {
                invokeFuzzySetter(label, "setFCap");
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isKnownVolatileItem(Object label) {
        try {
            Object value = field(label, "item").get(label);
            if (!(value instanceof Item)) {
                return false;
            }
            String name = registryName((Item) value);
            return name.startsWith("simplyjetpacks:");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasTags(NBTTagCompound tag) {
        if (tag == null) {
            return false;
        }
        try {
            return NBT_HAS_NO_TAGS == null || !((Boolean) NBT_HAS_NO_TAGS.invoke(tag));
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean hasVolatileKey(NBTTagCompound tag) {
        try {
            if (NBT_GET_KEY_SET == null) {
                return false;
            }
            Object keysObject = NBT_GET_KEY_SET.invoke(tag);
            if (!(keysObject instanceof Set)) {
                return false;
            }
            for (Object keyObject : (Set<?>) keysObject) {
                if (!(keyObject instanceof String)) {
                    continue;
                }
                String lower = ((String) keyObject).toLowerCase(Locale.ROOT);
                if (lower.contains("energy")
                        || lower.contains("charge")
                        || lower.equals("rf")
                        || lower.contains("forgeenergy")
                        || lower.contains("flux")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static boolean booleanField(Object target, String name) throws ReflectiveOperationException {
        return field(target, name).getBoolean(target);
    }

    private static NBTTagCompound tagField(Object target, String name) throws ReflectiveOperationException {
        Object value = field(target, name).get(target);
        return value instanceof NBTTagCompound ? (NBTTagCompound) value : null;
    }

    private static Field field(Object target, String name) throws NoSuchFieldException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void invokeFuzzySetter(Object label, String name) {
        try {
            Method method = label.getClass().getDeclaredMethod(name, boolean.class);
            method.setAccessible(true);
            method.invoke(label, true);
        } catch (Throwable ignored) {
        }
    }

    private static String registryName(Item item) {
        if (item == null) {
            return "";
        }
        try {
            Method method = item.getClass().getMethod("getRegistryName");
            Object value = method.invoke(item);
            return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static Method findMethod(Class<?> owner, String[] names, Class<?>... parameters) {
        try {
            return ReflectionLookup.findMethod(owner, names, parameters);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
