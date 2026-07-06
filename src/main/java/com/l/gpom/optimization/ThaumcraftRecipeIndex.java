package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

public final class ThaumcraftRecipeIndex {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.thaumcraft.recipeOutputIndex", "true"));

    private static volatile Map<RecipeKey, List<Object>> index;
    private static volatile int indexedRecipes;
    private static volatile int indexedOutputs;
    private static volatile boolean indexFailed;
    private static volatile Field craftingRegistryField;
    private static volatile Method getAspectsFromIngredientsMethod;
    private static volatile Method itemIdMethod;
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Integer> CURRENT_CANDIDATES = ThreadLocal.withInitial(() -> 64);
    private static final LongAdder LOOKUPS = new LongAdder();
    private static final LongAdder CANDIDATES = new LongAdder();

    private ThaumcraftRecipeIndex() {
    }

    public static Object generateTagsFromCraftingRecipes(Object stack, ArrayList<String> history) {
        if (!ENABLED) {
            return fallbackFullScan(stack, history);
        }

        try {
            List<Object> recipes = recipesFor(stack);
            CURRENT_CANDIDATES.set(Math.max(2, recipes.size()));
            LOOKUPS.increment();
            CANDIDATES.add(recipes.size());

            Object ret = null;
            int value = Integer.MAX_VALUE;
            for (Object recipe : recipes) {
                try {
                    Object recipeOut = recipeOutput(recipe);
                    Object ingredients = ingredients(recipe);
                    if (!fitsVanillaCraftingGrid(recipe, ingredients)) {
                        continue;
                    }
                    Object ph = getAspectsFromIngredients(ingredients, recipeOut, recipe, history);
                    if (isArcaneRecipe(recipe)) {
                        int vis = arcaneVis(recipe);
                        if (vis > 0) {
                            addAspect(ph, magicAspect(), (int) (Math.sqrt(1 + vis / 2) / count(recipeOut)));
                        }
                    }

                    removeNonPositiveAspects(ph);
                    int visSize = visSize(ph);
                    if (visSize < value && visSize > 0) {
                        ret = ph;
                        value = visSize;
                    }
                } catch (Exception e) {
                    if (GpomEarlyConfig.gpomLoggingEnabled()) {
                        GPOM.LOGGER.warn("[Thaumcraft Optimizations] Indexed recipe aspect generation failed; continuing", e);
                    }
                }
            }
            return ret;
        } catch (Throwable throwable) {
            if (!indexFailed) {
                indexFailed = true;
                GPOM.LOGGER.warn("[Thaumcraft Optimizations] Recipe output index failed; falling back to full crafting recipe scan", throwable);
            }
            return fallbackFullScan(stack, history);
        } finally {
            CURRENT_CANDIDATES.set(64);
        }
    }

    public static int currentCandidateCount() {
        return CURRENT_CANDIDATES.get();
    }

    public static String progressStats() {
        Map<RecipeKey, List<Object>> current = index;
        if (current == null) {
            return "";
        }
        long lookups = LOOKUPS.sum();
        long candidates = CANDIDATES.sum();
        long averageCandidates = lookups == 0L ? 0L : candidates / lookups;
        return "ThaumRecipeIndex recipes=" + indexedRecipes
                + " outputs=" + indexedOutputs
                + " lookups=" + lookups
                + " avgCandidates=" + averageCandidates;
    }

    private static List<Object> recipesFor(Object stack) throws Exception {
        Map<RecipeKey, List<Object>> current = index;
        if (current == null) {
            synchronized (ThaumcraftRecipeIndex.class) {
                current = index;
                if (current == null) {
                    current = buildIndex();
                    index = current;
                }
            }
        }

        List<Object> recipes = current.get(recipeKey(stack));
        return recipes != null ? recipes : java.util.Collections.emptyList();
    }

    private static Map<RecipeKey, List<Object>> buildIndex() throws Exception {
        long startedAt = System.nanoTime();
        Map<RecipeKey, List<Object>> built = new HashMap<>();
        int recipes = 0;

        Object registry = craftingRegistry();
        for (Object key : registryKeys(registry)) {
            Object recipe = registryGet(registry, key);
            if (!validRecipe(recipe)) {
                continue;
            }
            recipes++;
            RecipeKey recipeKey = recipeKey(recipeOutput(recipe));
            built.computeIfAbsent(recipeKey, ignored -> new ArrayList<>()).add(recipe);
        }

        indexedRecipes = recipes;
        indexedOutputs = built.size();
        GPOM.LOGGER.info(
                "[Thaumcraft Optimizations] Built crafting recipe output index: recipes={}, outputs={}, elapsed={} ms",
                recipes,
                indexedOutputs,
                String.format(java.util.Locale.ROOT, "%.3f", (System.nanoTime() - startedAt) / 1_000_000.0D)
        );
        return built;
    }

    private static boolean validRecipe(Object recipe) throws Exception {
        if (recipe == null) {
            return false;
        }
        Object output = recipeOutput(recipe);
        if (output == null || isEmpty(output)) {
            return false;
        }
        Object item = item(output);
        return item != null && itemId(item) > 0;
    }

    private static Object fallbackFullScan(Object stack, ArrayList<String> history) {
        try {
            Object ret = null;
            int value = Integer.MAX_VALUE;
            Object registry = craftingRegistry();
            for (Object key : registryKeys(registry)) {
                Object recipe = registryGet(registry, key);
                if (validRecipe(recipe) && recipeKey(recipeOutput(recipe)).equals(recipeKey(stack))) {
                    try {
                        Object recipeOut = recipeOutput(recipe);
                        Object ingredients = ingredients(recipe);
                        if (!fitsVanillaCraftingGrid(recipe, ingredients)) {
                            continue;
                        }
                        Object ph = getAspectsFromIngredients(ingredients, recipeOut, recipe, history);
                        if (isArcaneRecipe(recipe)) {
                            int vis = arcaneVis(recipe);
                            if (vis > 0) {
                                addAspect(ph, magicAspect(), (int) (Math.sqrt(1 + vis / 2) / count(recipeOut)));
                            }
                        }
                        removeNonPositiveAspects(ph);
                        int visSize = visSize(ph);
                        if (visSize < value && visSize > 0) {
                            ret = ph;
                            value = visSize;
                        }
                    } catch (Exception e) {
                        if (GpomEarlyConfig.gpomLoggingEnabled()) {
                            GPOM.LOGGER.warn("[Thaumcraft Optimizations] Fallback recipe aspect generation failed; continuing", e);
                        }
                    }
                }
            }
            return ret;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getAspectsFromIngredients(Object ingredients, Object recipeOut, Object recipe, ArrayList<String> history) throws Exception {
        Method method = getAspectsFromIngredientsMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.common.lib.crafting.ThaumcraftCraftingManager")
                    .getDeclaredMethod(
                            "getAspectsFromIngredients",
                            Class.forName("net.minecraft.util.NonNullList"),
                            Class.forName("net.minecraft.item.ItemStack"),
                            Class.forName("net.minecraft.item.crafting.IRecipe"),
                            ArrayList.class
                    );
            method.setAccessible(true);
            getAspectsFromIngredientsMethod = method;
        }
        return method.invoke(null, ingredients, recipeOut, recipe, history);
    }

    private static void removeNonPositiveAspects(Object aspectList) throws Exception {
        if (aspectList == null) {
            return;
        }
        Object copy = call(aspectList, "copy");
        for (Object aspect : (Object[]) call(copy, "getAspects")) {
            if (intCall(aspectList, "getAmount", Class.forName("thaumcraft.api.aspects.Aspect"), aspect) <= 0) {
                call(aspectList, "remove", Class.forName("thaumcraft.api.aspects.Aspect"), aspect);
            }
        }
    }

    private static Object craftingRegistry() throws Exception {
        Field field = craftingRegistryField;
        if (field == null) {
            field = Class.forName("net.minecraft.item.crafting.CraftingManager").getField("field_193380_a");
            field.setAccessible(true);
            craftingRegistryField = field;
        }
        return field.get(null);
    }

    private static Iterable<?> registryKeys(Object registry) throws Exception {
        return (Iterable<?>) call(registry, "func_148742_b");
    }

    private static Object registryGet(Object registry, Object key) throws Exception {
        try {
            return call(registry, "getValue", key.getClass(), key);
        } catch (NoSuchMethodException ignored) {
            return call(registry, "func_82594_a", key.getClass(), key);
        }
    }

    private static Object recipeOutput(Object recipe) throws Exception {
        return call(recipe, "getRecipeOutput", "func_77571_b");
    }

    private static Object ingredients(Object recipe) throws Exception {
        return call(recipe, "getIngredients", "func_192400_c");
    }

    private static boolean fitsVanillaCraftingGrid(Object recipe, Object ingredients) throws Exception {
        if (ingredients == null) {
            return recipeFitsVanillaCraftingGrid(recipe);
        }
        int size;
        if (ingredients instanceof Collection) {
            size = ((Collection<?>) ingredients).size();
        } else {
            size = intCall(ingredients, "size");
        }
        return size <= 9 && recipeFitsVanillaCraftingGrid(recipe);
    }

    private static boolean recipeFitsVanillaCraftingGrid(Object recipe) {
        if (recipe == null) {
            return true;
        }
        try {
            Object value = twoArgMethod(recipe.getClass(), int.class, int.class, "canFit", "func_194133_a")
                    .invoke(recipe, 3, 3);
            return !(value instanceof Boolean) || (Boolean) value;
        } catch (NoSuchMethodException ignored) {
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isArcaneRecipe(Object recipe) throws Exception {
        return Class.forName("thaumcraft.api.crafting.IArcaneRecipe").isInstance(recipe);
    }

    private static int arcaneVis(Object recipe) throws Exception {
        return intCall(recipe, "getVis");
    }

    private static Object magicAspect() throws Exception {
        return Class.forName("thaumcraft.api.aspects.Aspect").getField("MAGIC").get(null);
    }

    private static void addAspect(Object aspectList, Object aspect, int amount) throws Exception {
        call(aspectList, "add", Class.forName("thaumcraft.api.aspects.Aspect"), int.class, aspect, amount);
    }

    private static int visSize(Object aspectList) throws Exception {
        return intCall(aspectList, "visSize");
    }

    private static RecipeKey recipeKey(Object stack) throws Exception {
        int meta = metadata(stack);
        return new RecipeKey(item(stack), meta == 32767 ? 0 : meta);
    }

    private static Object item(Object stack) throws Exception {
        return call(stack, "getItem", "func_77973_b");
    }

    private static int metadata(Object stack) throws Exception {
        return intCall(stack, "getMetadata", "func_77952_i");
    }

    private static int count(Object stack) throws Exception {
        return intCall(stack, "getCount", "func_190916_E");
    }

    private static boolean isEmpty(Object stack) throws Exception {
        Object value = call(stack, "isEmpty", "func_190926_b");
        return value instanceof Boolean && (Boolean) value;
    }

    private static int itemId(Object item) throws Exception {
        Method method = itemIdMethod;
        if (method == null) {
            Class<?> itemClass = Class.forName("net.minecraft.item.Item");
            method = itemClass.getMethod("func_150891_b", itemClass);
            method.setAccessible(true);
            itemIdMethod = method;
        }
        Object value = method.invoke(null, item);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int intCall(Object target, String name) throws Exception {
        Object value = call(target, name);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int intCall(Object target, String... names) throws Exception {
        Object value = call(target, names);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int intCall(Object target, String name, Class<?> argType, Object arg) throws Exception {
        Object value = call(target, name, argType, arg);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object call(Object target, String... names) throws Exception {
        return noArgMethod(target.getClass(), names).invoke(target);
    }

    private static Object call(Object target, String name, Class<?> argType, Object arg) throws Exception {
        return oneArgMethod(target.getClass(), argType, name).invoke(target, arg);
    }

    private static Object call(Object target, String name, Class<?> firstArgType, Class<?> secondArgType, Object firstArg, Object secondArg) throws Exception {
        return twoArgMethod(target.getClass(), firstArgType, secondArgType, name).invoke(target, firstArg, secondArg);
    }

    private static Method noArgMethod(Class<?> type, String... names) throws NoSuchMethodException {
        String key = methodKey(type, "()", names);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(key, method);
                return method;
            } catch (NoSuchMethodException exception) {
                last = exception;
            }
        }
        throw last != null ? last : new NoSuchMethodException(names[0]);
    }

    private static Method oneArgMethod(Class<?> type, Class<?> argType, String... names) throws NoSuchMethodException {
        String key = methodKey(type, "(" + argType.getName() + ")", names);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                Method method = findCompatibleMethod(type, name, argType);
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(key, method);
                return method;
            } catch (NoSuchMethodException exception) {
                last = exception;
            }
        }
        throw last != null ? last : new NoSuchMethodException(names[0]);
    }

    private static Method twoArgMethod(Class<?> type, Class<?> firstArgType, Class<?> secondArgType, String... names) throws NoSuchMethodException {
        String key = methodKey(type, "(" + firstArgType.getName() + "," + secondArgType.getName() + ")", names);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                Method method = type.getMethod(name, firstArgType, secondArgType);
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(key, method);
                return method;
            } catch (NoSuchMethodException exception) {
                last = exception;
            }
        }
        throw last != null ? last : new NoSuchMethodException(names[0]);
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> argType) throws NoSuchMethodException {
        try {
            return type.getMethod(name, argType);
        } catch (NoSuchMethodException exactMiss) {
            for (Method method : type.getMethods()) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!method.getName().equals(name) || parameterTypes.length != 1) {
                    continue;
                }
                if (parameterTypes[0].isAssignableFrom(argType) || argType.isAssignableFrom(parameterTypes[0])) {
                    return method;
                }
            }
            throw exactMiss;
        }
    }

    private static String methodKey(Class<?> type, String descriptor, String... names) {
        StringBuilder key = new StringBuilder(type.getName()).append('#').append(descriptor);
        for (String name : names) {
            key.append('/').append(name);
        }
        return key.toString();
    }

    private static final class RecipeKey {
        private final Object item;
        private final int meta;

        private RecipeKey(Object item, int meta) {
            this.item = item;
            this.meta = meta;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RecipeKey)) {
                return false;
            }
            RecipeKey recipeKey = (RecipeKey) other;
            return meta == recipeKey.meta && item == recipeKey.item;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(item) + meta;
        }
    }
}
