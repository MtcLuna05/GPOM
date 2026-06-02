package com.l.cleanroomoptimizations.profiling;

import com.l.cleanroomoptimizations.CleanroomOptimizations;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class HeiOptimizations {
    private static final int SEARCH_WORKERS = computeSearchWorkerCount();
    private static Method jerEnchantmentWrapperCreate;
    private static Method itemStackIsEmpty;
    private static Method itemStackGetItem;
    private static Method itemGetEnchantability;
    private static Object minecraftBookItem;
    private static Field jerVillagerProfessionCareers;
    private static Field jerVillagerCareerTrades;
    private static Field jerVillagerCareerId;
    private static Method jerGetPrivateValue;
    private static final ConcurrentMap<String, Field> JER_PRIVATE_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Object, Byte> HEI_FALLBACK_SUBTYPE_STATE = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final byte HEI_SUBTYPE_NOT_CHECKED = 0;
    private static final byte HEI_SUBTYPE_NONE_KNOWN = 1;
    private static final byte HEI_SUBTYPE_PRESENT = 2;
    private static Method heiHasSubtypeInterpreter;
    private static Method heiRegisterSubtypeInterpreter;
    private static Method heiFluidSubtypeApply;
    private static Object heiFluidSubtypeInterpreter;

    private HeiOptimizations() {
    }

    public static int searchWorkerCount() {
        return SEARCH_WORKERS;
    }

    public static Object createJerEnchantmentWrapper(ItemStack stack) {
        try {
            if (!mayHaveJerEnchantments(stack)) {
                return null;
            }
            Method method = jerEnchantmentWrapperCreate;
            if (method == null) {
                method = Class.forName("jeresources.jei.enchantment.EnchantmentWrapper")
                        .getMethod("create", ItemStack.class);
                jerEnchantmentWrapperCreate = method;
            }
            return method.invoke(null, stack);
        } catch (Throwable ignored) {
            return createJerEnchantmentWrapperFallback(stack);
        }
    }

    public static void fastHeiFallbackSubtypeInterpreter(Object subtypeRegistry, ItemStack stack) {
        try {
            if (subtypeRegistry == null || stack == null) {
                return;
            }
            Object item = getItemReflective(stack);
            if (item == null) {
                return;
            }

            byte state = heiFallbackSubtypeState(item);
            if (state == HEI_SUBTYPE_PRESENT) {
                return;
            }
            if (state == HEI_SUBTYPE_NOT_CHECKED && hasHeiSubtypeInterpreter(subtypeRegistry, stack)) {
                setHeiFallbackSubtypeState(item, HEI_SUBTYPE_PRESENT);
                return;
            }

            Object interpreter = heiFluidSubtypeInterpreter();
            String subtype = applyHeiFluidSubtypeInterpreter(interpreter, stack);
            if (subtype != null && !subtype.isEmpty()) {
                registerHeiSubtypeInterpreter(subtypeRegistry, item, interpreter);
                setHeiFallbackSubtypeState(item, HEI_SUBTYPE_PRESENT);
            } else {
                setHeiFallbackSubtypeState(item, HEI_SUBTYPE_NONE_KNOWN);
            }
        } catch (Throwable ignored) {
            // Equivalent to HEI failing its optional fallback subtype path: leave the stack registered normally.
        }
    }

    @SuppressWarnings("unchecked")
    public static List<?> fastJerVillagerCareers(Object profession) {
        try {
            Field field = jerVillagerProfessionCareers;
            if (field == null) {
                field = findField(profession.getClass(), "careers");
                jerVillagerProfessionCareers = field;
            }
            return (List<?>) field.get(profession);
        } catch (Throwable ignored) {
            Object value = jerGetPrivateValue(profession, "careers");
            return value instanceof List ? (List<?>) value : Collections.emptyList();
        }
    }

    public static List<?> fastJerVillagerTrades(Object career) {
        try {
            Field field = jerVillagerCareerTrades;
            if (field == null) {
                field = findField(career.getClass(), "trades");
                jerVillagerCareerTrades = field;
            }
            return (List<?>) field.get(career);
        } catch (Throwable ignored) {
            Object value = jerGetPrivateValue(career, "trades");
            return value instanceof List ? (List<?>) value : Collections.emptyList();
        }
    }

    public static int fastJerVillagerCareerId(Object career) {
        try {
            Field field = jerVillagerCareerId;
            if (field == null) {
                field = findField(career.getClass(), "id");
                jerVillagerCareerId = field;
            }
            return field.getInt(career);
        } catch (Throwable ignored) {
            Object value = jerGetPrivateValue(career, "id");
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }
    }

    private static boolean mayHaveJerEnchantments(ItemStack stack) {
        return mayHaveJerEnchantmentsReflective(stack);
    }

    private static boolean mayHaveJerEnchantmentsReflective(ItemStack stack) {
        try {
            if (stack == null) {
                return false;
            }
            Method isEmpty = itemStackIsEmpty;
            if (isEmpty == null) {
                isEmpty = findMethod(stack.getClass(), "func_190926_b", "isEmpty");
                itemStackIsEmpty = isEmpty;
            }
            if (isEmpty != null && Boolean.TRUE.equals(isEmpty.invoke(stack))) {
                return false;
            }

            Method getItem = itemStackGetItem;
            if (getItem == null) {
                getItem = findMethod(stack.getClass(), "func_77973_b", "getItem");
                itemStackGetItem = getItem;
            }
            if (getItem == null) {
                return true;
            }
            Object item = getItem.invoke(stack);
            if (item == null) {
                return false;
            }
            Object book = minecraftBookItem;
            if (book == null) {
                book = findStaticField(Class.forName("net.minecraft.init.Items"), "field_151122_aG", "BOOK");
                minecraftBookItem = book;
            }
            if (item == book) {
                return true;
            }

            Method enchantability = itemGetEnchantability;
            if (enchantability == null) {
                enchantability = findMethod(item.getClass(), "func_77619_b", "getItemEnchantability");
                itemGetEnchantability = enchantability;
            }
            if (enchantability == null) {
                return true;
            }
            Object value = enchantability.invoke(item);
            return value instanceof Number && ((Number) value).intValue() > 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static Object getItemReflective(ItemStack stack) throws ReflectiveOperationException {
        Method getItem = itemStackGetItem;
        if (getItem == null) {
            getItem = findMethod(stack.getClass(), "func_77973_b", "getItem");
            itemStackGetItem = getItem;
        }
        return getItem == null ? null : getItem.invoke(stack);
    }

    private static byte heiFallbackSubtypeState(Object item) {
        Byte state = HEI_FALLBACK_SUBTYPE_STATE.get(item);
        return state == null ? HEI_SUBTYPE_NOT_CHECKED : state;
    }

    private static void setHeiFallbackSubtypeState(Object item, byte state) {
        HEI_FALLBACK_SUBTYPE_STATE.put(item, state);
    }

    private static boolean hasHeiSubtypeInterpreter(Object subtypeRegistry, ItemStack stack) throws ReflectiveOperationException {
        Method method = heiHasSubtypeInterpreter;
        if (method == null) {
            method = subtypeRegistry.getClass().getMethod("hasSubtypeInterpreter", ItemStack.class);
            method.setAccessible(true);
            heiHasSubtypeInterpreter = method;
        }
        return Boolean.TRUE.equals(method.invoke(subtypeRegistry, stack));
    }

    private static Object heiFluidSubtypeInterpreter() throws ReflectiveOperationException {
        Object interpreter = heiFluidSubtypeInterpreter;
        if (interpreter == null) {
            interpreter = Class.forName("mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory$FluidSubtypeInterpreter")
                    .getField("INSTANCE")
                    .get(null);
            heiFluidSubtypeInterpreter = interpreter;
        }
        return interpreter;
    }

    private static String applyHeiFluidSubtypeInterpreter(Object interpreter, ItemStack stack) throws ReflectiveOperationException {
        Method method = heiFluidSubtypeApply;
        if (method == null) {
            method = interpreter.getClass().getMethod("apply", ItemStack.class);
            method.setAccessible(true);
            heiFluidSubtypeApply = method;
        }
        Object value = method.invoke(interpreter, stack);
        return value instanceof String ? (String) value : "";
    }

    private static void registerHeiSubtypeInterpreter(Object subtypeRegistry, Object item, Object interpreter) throws ReflectiveOperationException, ClassNotFoundException {
        Method method = heiRegisterSubtypeInterpreter;
        if (method == null) {
            method = subtypeRegistry.getClass().getMethod(
                    "registerSubtypeInterpreter",
                    Class.forName("net.minecraft.item.Item"),
                    Class.forName("mezz.jei.api.ISubtypeRegistry$ISubtypeInterpreter")
            );
            method.setAccessible(true);
            heiRegisterSubtypeInterpreter = method;
        }
        method.invoke(subtypeRegistry, item, interpreter);
    }

    public static Object fastJerPrivateValue(Class<?> ownerClass, Object target, String fieldName) {
        try {
            if (ownerClass == null || target == null || fieldName == null) {
                return jerGetPrivateValue(target, fieldName);
            }
            String key = ownerClass.getName() + '#' + fieldName;
            Field field = JER_PRIVATE_FIELDS.get(key);
            if (field == null) {
                field = findField(ownerClass, fieldName);
                Field existing = JER_PRIVATE_FIELDS.putIfAbsent(key, field);
                if (existing != null) {
                    field = existing;
                }
            }
            return field.get(target);
        } catch (Throwable ignored) {
            return jerGetPrivateValue(target, fieldName);
        }
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object jerGetPrivateValue(Object target, String fieldName) {
        try {
            if (target == null) {
                return null;
            }
            Method method = jerGetPrivateValue;
            if (method == null) {
                method = Class.forName("jeresources.util.ReflectionHelper")
                        .getMethod("getPrivateValue", Class.class, Object.class, String.class);
                method.setAccessible(true);
                jerGetPrivateValue = method;
            }
            return method.invoke(null, target.getClass(), target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findStaticField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getField(name).get(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object createJerEnchantmentWrapperFallback(ItemStack stack) {
        try {
            Method method = jerEnchantmentWrapperCreate;
            if (method == null) {
                method = Class.forName("jeresources.jei.enchantment.EnchantmentWrapper")
                        .getMethod("create", ItemStack.class);
                jerEnchantmentWrapperCreate = method;
            }
            return method.invoke(null, stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int computeSearchWorkerCount() {
        int fallback = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        int configured = intProperty("cleanroomoptimizations.hei.searchWorkers", fallback);
        int max = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int workers = Math.max(1, Math.min(configured, max));
        CleanroomOptimizations.LOGGER.info("[HEI Optimizations] Using {} HEI async search worker(s)", workers);
        return workers;
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
