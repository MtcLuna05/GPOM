package com.l.cleanroomoptimizations.profiling;

import com.l.cleanroomoptimizations.CleanroomOptimizations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class ThaumcraftAspectCache {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("cleanroomoptimizations.thaumcraft.generatedAspectCache", "false"));
    private static final ThreadLocal<Integer> ACTIVE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final Map<String, Object> GENERATED_TAGS = new ConcurrentHashMap<>();
    private static final LongAdder EXISTS_HITS = new LongAdder();
    private static final LongAdder EXISTS_MISSES = new LongAdder();

    private ThaumcraftAspectCache() {
    }

    public static void beginPostInit() {
        int depth = ACTIVE_DEPTH.get();
        if (depth == 0) {
            EXISTS_HITS.reset();
            EXISTS_MISSES.reset();
            if (ENABLED) {
                GENERATED_TAGS.clear();
                CleanroomOptimizations.LOGGER.info("[Thaumcraft Optimizations] Enabled generated aspect cache for Thaumcraft PostInit");
            }
        }
        ACTIVE_DEPTH.set(depth + 1);
    }

    public static void endPostInit() {
        int depth = Math.max(0, ACTIVE_DEPTH.get() - 1);
        ACTIVE_DEPTH.set(depth);
        if (depth == 0) {
            CleanroomOptimizations.LOGGER.info("[Thaumcraft Optimizations] ThaumcraftApi.exists replacement during PostInit: hits={}, misses={}", EXISTS_HITS.sum(), EXISTS_MISSES.sum());
            if (ENABLED) {
                CleanroomOptimizations.LOGGER.info("[Thaumcraft Optimizations] Cleared generated aspect cache after Thaumcraft PostInit (entries={})", GENERATED_TAGS.size());
                GENERATED_TAGS.clear();
            }
        }
    }

    public static Object getGenerated(Object itemStack, ArrayList<String> history) {
        if (!active()) {
            return null;
        }

        String key = normalizedStackKey(itemStack);
        if (key == null) {
            return null;
        }

        // Preserve Thaumcraft's recursion guard. A key already in the current path must still return null.
        if (history != null && history.contains(key)) {
            return null;
        }

        Object cached = GENERATED_TAGS.get(key);
        return cached != null ? copyAspectList(cached) : null;
    }

    public static Object storeGenerated(Object result, Object itemStack, ArrayList<String> history) {
        if (!active() || result == null) {
            return result;
        }

        String key = normalizedStackKey(itemStack);
        if (key != null) {
            GENERATED_TAGS.putIfAbsent(key, copyAspectList(result));
        }
        return result;
    }

    public static boolean objectTagExists(Object itemStack) {
        try {
            if (itemStack == null || booleanCall(itemStack, "isEmpty", "func_190926_b")) {
                return false;
            }

            Map<?, ?> objectTags = objectTags();
            Object stack = normalizedCountCopy(itemStack);
            if (objectTags.containsKey(uniqueItemstackId(stack, false))) {
                recordExists(true);
                return true;
            }

            call(stack, "setItemDamage", "func_77964_b", int.class, 32767);
            if (objectTags.containsKey(uniqueItemstackId(stack, false))) {
                recordExists(true);
                return true;
            }

            if (intCall(itemStack, "getMetadata", "func_77952_i") == 32767) {
                for (int index = 0; index < 16; index++) {
                    call(stack, "setItemDamage", "func_77964_b", int.class, index);
                    if (objectTags.containsKey(uniqueItemstackId(stack, false))) {
                        recordExists(true);
                        return true;
                    }
                }
            }

            Object stripped = normalizedCountCopy(itemStack);
            if (objectTags.containsKey(uniqueItemstackId(stripped, true))) {
                recordExists(true);
                return true;
            }

            call(stripped, "setItemDamage", "func_77964_b", int.class, 32767);
            boolean found = objectTags.containsKey(uniqueItemstackId(stripped, true));
            recordExists(found);
            return found;
        } catch (Throwable ignored) {
            recordExists(false);
            return false;
        }
    }

    private static void recordExists(boolean found) {
        if (ACTIVE_DEPTH.get() <= 0) {
            return;
        }
        if (found) {
            EXISTS_HITS.increment();
        } else {
            EXISTS_MISSES.increment();
        }
    }

    private static boolean active() {
        return ENABLED && ACTIVE_DEPTH.get() > 0;
    }

    private static String normalizedStackKey(Object itemStack) {
        try {
            if (itemStack == null || booleanCall(itemStack, "isEmpty", "func_190926_b")) {
                return null;
            }

            Object stack = call(itemStack, "copy", "func_77946_l");
            call(stack, "setCount", "func_190920_e", int.class, 1);

            Object item = call(stack, "getItem", "func_77973_b");
            boolean damageable = booleanCall(stack, "isItemStackDamageable", "func_77984_f");
            boolean hasSubtypes = item != null && booleanCall(item, "getHasSubtypes", "func_77614_k");
            if (damageable || !hasSubtypes) {
                call(stack, "setItemDamage", "func_77964_b", int.class, 32767);
            }

            Object nbt = call(stack, "serializeNBT");
            return nbt != null ? nbt.toString() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object normalizedCountCopy(Object itemStack) throws Exception {
        Object stack = call(itemStack, "copy", "func_77946_l");
        call(stack, "setCount", "func_190920_e", int.class, 1);
        return stack;
    }

    private static int uniqueItemstackId(Object itemStack, boolean stripped) throws Exception {
        Object stack = normalizedCountCopy(itemStack);
        if (stripped) {
            call(stack, "setTagCompound", "func_77982_d", Object.class, null);
        }
        Object nbt = call(stack, "serializeNBT");
        return nbt != null ? nbt.toString().hashCode() : 0;
    }

    private static Map<?, ?> objectTags() throws Exception {
        Class<?> commonInternals = Class.forName("thaumcraft.api.internal.CommonInternals");
        Object value = commonInternals.getField("objectTags").get(null);
        return value instanceof Map ? (Map<?, ?>) value : java.util.Collections.emptyMap();
    }

    private static Object copyAspectList(Object aspectList) {
        if (aspectList == null) {
            return null;
        }

        try {
            return call(aspectList, "copy");
        } catch (Throwable ignored) {
            return aspectList;
        }
    }

    private static boolean booleanCall(Object target, String... names) throws Exception {
        Object value = call(target, names);
        return value instanceof Boolean && (Boolean) value;
    }

    private static int intCall(Object target, String... names) throws Exception {
        Object value = call(target, names);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object call(Object target, String... names) throws Exception {
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(names[0]);
    }

    private static Object call(Object target, String mcpName, String srgName, Class<?> argType, Object arg) throws Exception {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                Method method = findMethod(target.getClass(), name, argType);
                method.setAccessible(true);
                return method.invoke(target, arg);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(mcpName);
    }

    private static Method findMethod(Class<?> type, String name, Class<?> argType) throws NoSuchMethodException {
        try {
            return type.getMethod(name, argType);
        } catch (NoSuchMethodException ignored) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == 1) {
                    Class<?> parameterType = method.getParameterTypes()[0];
                    if (argType == Object.class && !parameterType.isPrimitive()) {
                        return method;
                    }
                    if (parameterType.isAssignableFrom(argType)) {
                        return method;
                    }
                }
            }
            throw ignored;
        }
    }
}
