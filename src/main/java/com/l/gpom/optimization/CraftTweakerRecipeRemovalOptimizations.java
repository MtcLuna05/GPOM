package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftTweakerRecipeRemovalOptimizations {
    private static final boolean FAST_RECIPE_REMOVAL = Boolean.parseBoolean(System.getProperty("gpom.crafttweaker.fastRecipeRemoval", "true"));
    private static final String MANAGER_CLASS = "crafttweaker.mc1120.recipes.MCRecipeManager";
    private static final int WILDCARD_META = 32767;
    private static volatile Field recipesField;
    private static volatile Method removeRecipesMethod;
    private static volatile Method ingredientMatchesMethod;
    private static volatile Method ingredientMatchesExactMethod;
    private static volatile Method ingredientContainsMethod;
    private static volatile Method ingredientItemsMethod;
    private static volatile Method ingredientInternalMethod;
    private static volatile Method itemStackInternalMethod;
    private static volatile Method craftTweakerStackForMatchingMethod;
    private static volatile Method craftTweakerOreDictMethod;
    private static volatile RecipeIndex recipeIndex;
    private static volatile boolean fallbackLogged;

    private CraftTweakerRecipeRemovalOptimizations() {
    }

    public static void applyRemoveShaped(Object action) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            RemoveContext context = context(action);
            Object output = field(action, "output");
            Object[][] ingredients = (Object[][]) field(action, "ingredients");
            int width = 0;
            int height = 0;
            if (ingredients != null) {
                height = ingredients.length;
                for (Object[] row : ingredients) {
                    width = Math.max(width, row == null ? 0 : row.length);
                }
            }

            List<Entry> removed = new ArrayList<>();
            for (Entry entry : candidatesForOutput(context, output)) {
                IRecipe recipe = entry.recipe;
                if (recipe == null || isEmpty(recipe.getRecipeOutput()) || !matchesItem(recipe.getRecipeOutput(), output)) {
                    continue;
                }
                if (!(recipe instanceof IShapedRecipe)) {
                    continue;
                }
                if (ingredients != null && !matchesShaped((IShapedRecipe) recipe, ingredients, width, height)) {
                    continue;
                }
                removed.add(entry);
            }
            logInfo(removed.size() + " removed");
            remove(context, action, removed);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker fast shaped recipe removal failed", throwable);
            applyRemoveShapedSlow(action);
        } finally {
            StartupProfiler.endProbe("CT fast ActionRemoveShapedRecipes.apply", startedAt);
        }
    }

    public static void applyRemoveShapeless(Object action) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            RemoveContext context = context(action);
            Object output = field(action, "output");
            if (output == null) {
                return;
            }
            Object[] ingredients = (Object[]) field(action, "ingredients");
            boolean wildcard = Boolean.TRUE.equals(field(action, "wildcard"));

            List<Entry> removed = new ArrayList<>();
            for (Entry entry : candidatesForOutput(context, output)) {
                IRecipe recipe = entry.recipe;
                if (recipe == null || isEmpty(recipe.getRecipeOutput()) || !matchesItem(recipe.getRecipeOutput(), output)) {
                    continue;
                }
                if (recipe instanceof IShapedRecipe) {
                    continue;
                }
                if (!(recipe instanceof ShapelessRecipes) && !(recipe instanceof ShapelessOreRecipe)) {
                    continue;
                }
                if (ingredients != null && !matchesShapeless(recipe, ingredients, wildcard)) {
                    continue;
                }
                removed.add(entry);
            }
            logInfo("Removing" + removed.size() + " Shapeless recipes.");
            remove(context, action, removed);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker fast shapeless recipe removal failed", throwable);
            applyRemoveShapelessSlow(action);
        } finally {
            StartupProfiler.endProbe("CT fast ActionRemoveShapelessRecipes.apply", startedAt);
        }
    }

    public static void applyRemoveNoIngredients(Object action) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            RemoveContext context = context(action);
            List<?> outputs = listField(action, "outputs");
            List<Entry> removed = new ArrayList<>();
            Iterable<Entry> candidates = candidatesForOutputs(context, outputs);
            for (Entry entry : candidates) {
                IRecipe recipe = entry.recipe;
                if (recipe == null) {
                    continue;
                }
                Object itemStack = craftTweakerStackForMatching(recipe.getRecipeOutput());
                if (itemStack != null && matchesAnyOutput(outputs, itemStack)) {
                    removed.add(entry);
                }
            }
            remove(context, action, removed);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker fast no-ingredient recipe removal failed", throwable);
            applyRemoveNoIngredientsSlow(action);
        } finally {
            StartupProfiler.endProbe("CT fast ActionRemoveRecipesNoIngredients.apply", startedAt);
        }
    }

    private static void applyRemoveShapedSlow(Object action) {
        try {
            RemoveContext context = context(action);
            Object output = field(action, "output");
            Object[][] ingredients = (Object[][]) field(action, "ingredients");
            int width = 0;
            int height = 0;
            if (ingredients != null) {
                height = ingredients.length;
                for (Object[] row : ingredients) {
                    width = Math.max(width, row == null ? 0 : row.length);
                }
            }
            List<Entry> removed = new ArrayList<>();
            for (Entry entry : context.index.allEntries()) {
                IRecipe recipe = entry.recipe;
                if (recipe == null || isEmpty(recipe.getRecipeOutput()) || !matchesItem(recipe.getRecipeOutput(), output)) {
                    continue;
                }
                if (recipe instanceof IShapedRecipe && (ingredients == null || matchesShaped((IShapedRecipe) recipe, ingredients, width, height))) {
                    removed.add(entry);
                }
            }
            logInfo(removed.size() + " removed");
            remove(context, action, removed);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker slow shaped recipe removal failed", throwable);
        }
    }

    private static void applyRemoveShapelessSlow(Object action) {
        try {
            RemoveContext context = context(action);
            Object output = field(action, "output");
            if (output == null) {
                return;
            }
            Object[] ingredients = (Object[]) field(action, "ingredients");
            boolean wildcard = Boolean.TRUE.equals(field(action, "wildcard"));
            List<Entry> removed = new ArrayList<>();
            for (Entry entry : context.index.allEntries()) {
                IRecipe recipe = entry.recipe;
                if (recipe == null || isEmpty(recipe.getRecipeOutput()) || !matchesItem(recipe.getRecipeOutput(), output)) {
                    continue;
                }
                if (recipe instanceof IShapedRecipe) {
                    continue;
                }
                if ((recipe instanceof ShapelessRecipes || recipe instanceof ShapelessOreRecipe)
                        && (ingredients == null || matchesShapeless(recipe, ingredients, wildcard))) {
                    removed.add(entry);
                }
            }
            logInfo("Removing" + removed.size() + " Shapeless recipes.");
            remove(context, action, removed);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker slow shapeless recipe removal failed", throwable);
        }
    }

    private static void applyRemoveNoIngredientsSlow(Object action) {
        try {
            RemoveContext context = context(action);
            List<?> outputs = listField(action, "outputs");
            List<Entry> removed = new ArrayList<>();
            for (Entry entry : context.index.allEntries()) {
                IRecipe recipe = entry.recipe;
                if (recipe == null) {
                    continue;
                }
                Object itemStack = craftTweakerStackForMatching(recipe.getRecipeOutput());
                if (itemStack != null && matchesAnyOutput(outputs, itemStack)) {
                    removed.add(entry);
                }
            }
            remove(context, action, removed);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker slow no-ingredient recipe removal failed", throwable);
        }
    }

    private static RemoveContext context(Object action) {
        if (!FAST_RECIPE_REMOVAL || action == null || !TargetedModVersions.isCraftTweakerClass(MANAGER_CLASS)) {
            return new RemoveContext(index(), false);
        }
        return new RemoveContext(index(), true);
    }

    private static Iterable<Entry> candidatesForOutput(RemoveContext context, Object output) {
        if (!context.fast) {
            return context.index.allEntries();
        }
        List<ItemStack> stacks = concreteStacks(output);
        if (stacks.isEmpty()) {
            return context.index.allEntries();
        }
        return context.index.entriesFor(stacks);
    }

    private static Iterable<Entry> candidatesForOutputs(RemoveContext context, List<?> outputs) {
        if (!context.fast || outputs == null || outputs.isEmpty()) {
            return context.index.allEntries();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (Object pair : outputs) {
            Object ingredient = pairKey(pair);
            List<ItemStack> concrete = concreteStacks(ingredient);
            if (concrete.isEmpty()) {
                return context.index.allEntries();
            }
            stacks.addAll(concrete);
        }
        return context.index.entriesFor(stacks);
    }

    private static boolean matchesShaped(IShapedRecipe recipe, Object[][] ingredients, int width, int height) {
        if (width != recipe.getRecipeWidth() || height != recipe.getRecipeHeight()) {
            return false;
        }
        NonNullList<Ingredient> recipeIngredients = recipe.getIngredients();
        for (int rowIndex = 0; rowIndex < height; rowIndex++) {
            Object[] row = ingredients[rowIndex];
            for (int column = 0; column < width; column++) {
                Object expected = row != null && column < row.length ? row[column] : null;
                Ingredient ingredient = recipeIngredients.get(rowIndex * width + column);
                ItemStack stack = firstIngredientStack(ingredient);
                if (!matchesItem(stack, expected)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean matchesShapeless(IRecipe recipe, Object[] expectedIngredients, boolean wildcard) {
        NonNullList<Ingredient> recipeIngredients = recipe.getIngredients();
        if (recipeIngredients.size() < expectedIngredients.length) {
            return false;
        }
        if (!wildcard && recipeIngredients.size() > expectedIngredients.length) {
            return false;
        }
        for (Object expected : expectedIngredients) {
            boolean found = false;
            for (Object ingredient : recipeIngredients) {
                if (matchesIngredientObject(ingredient, expected)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesIngredientObject(Object candidate, Object expected) {
        if (candidate instanceof String) {
            Object oreDict = oreDict((String) candidate);
            return invokeBoolean(ingredientContainsMethod(expected), expected, oreDict);
        }
        if (candidate instanceof ItemStack) {
            return matchesItem((ItemStack) candidate, expected);
        }
        if (candidate instanceof Ingredient) {
            ItemStack[] stacks = ((Ingredient) candidate).getMatchingStacks();
            return stacks.length > 0 && matchesItem(stacks[0], expected);
        }
        return false;
    }

    private static boolean matchesItem(ItemStack stack, Object expected) {
        if (expected == null) {
            return isEmpty(stack);
        }
        if (isEmpty(stack)) {
            return false;
        }
        Object craftTweakerStack = craftTweakerStackForMatching(stack);
        return craftTweakerStack != null && invokeBoolean(ingredientMatchesMethod(expected), expected, craftTweakerStack);
    }

    private static boolean matchesAnyOutput(List<?> outputs, Object itemStack) {
        if (outputs == null || itemStack == null) {
            return false;
        }
        for (Object pair : outputs) {
            Object ingredient = pairKey(pair);
            boolean exact = Boolean.TRUE.equals(pairValue(pair));
            Method method = exact ? ingredientMatchesExactMethod(ingredient) : ingredientMatchesMethod(ingredient);
            if (invokeBoolean(method, ingredient, itemStack)) {
                return true;
            }
        }
        return false;
    }

    private static void remove(RemoveContext context, Object action, List<Entry> entries) throws Exception {
        if (entries.isEmpty()) {
            invokeRemoveRecipes(action, Collections.emptyList());
            return;
        }
        List<ResourceLocation> keys = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            keys.add(entry.key);
        }
        invokeRemoveRecipes(action, keys);
        context.index.remove(entries);
    }

    private static RecipeIndex index() {
        RecipeIndex current = recipeIndex;
        Set<Map.Entry<ResourceLocation, IRecipe>> recipes = recipes();
        if (current != null && current.source == recipes) {
            return current;
        }
        synchronized (CraftTweakerRecipeRemovalOptimizations.class) {
            current = recipeIndex;
            if (current != null && current.source == recipes) {
                return current;
            }
            current = new RecipeIndex(recipes);
            recipeIndex = current;
            return current;
        }
    }

    private static Set<Map.Entry<ResourceLocation, IRecipe>> recipes() {
        try {
            Field field = recipesField();
            Object value = field == null ? null : field.get(null);
            if (value instanceof Set) {
                return (Set<Map.Entry<ResourceLocation, IRecipe>>) value;
            }
        } catch (Throwable ignored) {
        }
        return ForgeRegistries.RECIPES.getEntries();
    }

    private static List<ItemStack> concreteStacks(Object ingredient) {
        if (ingredient == null) {
            return Collections.emptyList();
        }
        List<ItemStack> result = new ArrayList<>();
        Object direct = internalStack(ingredient);
        if (direct instanceof ItemStack && !isEmpty((ItemStack) direct)) {
            result.add((ItemStack) direct);
            return result;
        }
        try {
            Method method = ingredientItemsMethod(ingredient);
            Object value = method == null ? null : method.invoke(ingredient);
            if (value instanceof Iterable) {
                for (Object item : (Iterable<?>) value) {
                    Object stack = internalStack(item);
                    if (stack instanceof ItemStack && !isEmpty((ItemStack) stack)) {
                        result.add((ItemStack) stack);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private static Object internalStack(Object ingredient) {
        try {
            Method method = itemStackInternalMethod(ingredient);
            if (method != null) {
                return method.invoke(ingredient);
            }
            method = ingredientInternalMethod(ingredient);
            return method == null ? null : method.invoke(ingredient);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ItemStack firstIngredientStack(Ingredient ingredient) {
        if (ingredient == null || ingredient == Ingredient.EMPTY || ingredient.apply(ItemStack.EMPTY)) {
            return ItemStack.EMPTY;
        }
        ItemStack[] stacks = ingredient.getMatchingStacks();
        return stacks.length == 0 ? ItemStack.EMPTY : stacks[0];
    }

    private static Object craftTweakerStackForMatching(ItemStack stack) {
        if (isEmpty(stack)) {
            return null;
        }
        try {
            Method method = craftTweakerStackForMatchingMethod();
            return method == null ? null : method.invoke(null, stack);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker stack bridge failed", throwable);
            return null;
        }
    }

    private static Object oreDict(String name) {
        try {
            Method method = craftTweakerOreDictMethod();
            return method == null ? null : method.invoke(null, name);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker ore dictionary bridge failed", throwable);
            return null;
        }
    }

    private static boolean invokeBoolean(Method method, Object target, Object argument) {
        if (method == null || target == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(target, argument));
        } catch (Throwable throwable) {
            logFallback("CraftTweaker ingredient bridge failed", throwable);
            return false;
        }
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        return field == null ? null : field.get(target);
    }

    private static List<?> listField(Object target, String name) throws Exception {
        Object value = field(target, name);
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    private static Object pairKey(Object pair) {
        return pairValue(pair, "getKey");
    }

    private static Object pairValue(Object pair) {
        return pairValue(pair, "getValue");
    }

    private static Object pairValue(Object pair, String methodName) {
        if (pair == null) {
            return null;
        }
        try {
            Method method = pair.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(pair);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field recipesField() {
        Field field = recipesField;
        if (field != null) {
            return field;
        }
        try {
            Class<?> manager = Class.forName(MANAGER_CLASS, false, CraftTweakerRecipeRemovalOptimizations.class.getClassLoader());
            field = findField(manager, "recipes");
            recipesField = field;
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method ingredientMatchesMethod(Object target) {
        return findInterfaceMethod(target, ingredientMatchesMethod, "crafttweaker.api.item.IIngredient", "matches", "crafttweaker.api.item.IItemStack");
    }

    private static Method ingredientMatchesExactMethod(Object target) {
        return findInterfaceMethod(target, ingredientMatchesExactMethod, "crafttweaker.api.item.IIngredient", "matchesExact", "crafttweaker.api.item.IItemStack");
    }

    private static Method ingredientContainsMethod(Object target) {
        return findInterfaceMethod(target, ingredientContainsMethod, "crafttweaker.api.item.IIngredient", "contains", "crafttweaker.api.item.IIngredient");
    }

    private static Method ingredientItemsMethod(Object target) {
        return findInterfaceMethod(target, ingredientItemsMethod, "crafttweaker.api.item.IIngredient", "getItems");
    }

    private static Method ingredientInternalMethod(Object target) {
        return findInterfaceMethod(target, ingredientInternalMethod, "crafttweaker.api.item.IIngredient", "getInternal");
    }

    private static Method itemStackInternalMethod(Object target) {
        return findInterfaceMethod(target, itemStackInternalMethod, "crafttweaker.api.item.IItemStack", "getInternal");
    }

    private static Method findInterfaceMethod(Object target, Method cached, String className, String name, String... parameterClassNames) {
        if (cached != null) {
            return cached;
        }
        if (target == null) {
            return null;
        }
        try {
            ClassLoader loader = target.getClass().getClassLoader();
            Class<?> type = Class.forName(className, false, loader);
            Class<?>[] parameters = new Class<?>[parameterClassNames.length];
            for (int i = 0; i < parameterClassNames.length; i++) {
                parameters[i] = Class.forName(parameterClassNames[i], false, loader);
            }
            Method method = type.getMethod(name, parameters);
            method.setAccessible(true);
            cacheInterfaceMethod(className, name, method);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void cacheInterfaceMethod(String className, String name, Method method) {
        if ("matches".equals(name)) {
            ingredientMatchesMethod = method;
        } else if ("matchesExact".equals(name)) {
            ingredientMatchesExactMethod = method;
        } else if ("contains".equals(name)) {
            ingredientContainsMethod = method;
        } else if ("getItems".equals(name)) {
            ingredientItemsMethod = method;
        } else if ("getInternal".equals(name) && className.endsWith("IItemStack")) {
            itemStackInternalMethod = method;
        } else if ("getInternal".equals(name)) {
            ingredientInternalMethod = method;
        }
    }

    private static Method craftTweakerStackForMatchingMethod() {
        Method method = craftTweakerStackForMatchingMethod;
        if (method != null) {
            return method;
        }
        try {
            ClassLoader loader = CraftTweakerRecipeRemovalOptimizations.class.getClassLoader();
            Class<?> bridge = Class.forName("crafttweaker.api.minecraft.CraftTweakerMC", false, loader);
            method = bridge.getMethod("getIItemStackForMatching", ItemStack.class);
            method.setAccessible(true);
            craftTweakerStackForMatchingMethod = method;
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method craftTweakerOreDictMethod() {
        Method method = craftTweakerOreDictMethod;
        if (method != null) {
            return method;
        }
        try {
            ClassLoader loader = CraftTweakerRecipeRemovalOptimizations.class.getClassLoader();
            Class<?> bridge = Class.forName("crafttweaker.api.minecraft.CraftTweakerMC", false, loader);
            method = bridge.getMethod("getOreDict", String.class);
            method.setAccessible(true);
            craftTweakerOreDictMethod = method;
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void invokeRemoveRecipes(Object action, List<ResourceLocation> keys) throws Exception {
        Method method = removeRecipesMethod;
        if (method == null) {
            method = action.getClass().getSuperclass().getDeclaredMethod("removeRecipes", List.class);
            method.setAccessible(true);
            removeRecipesMethod = method;
        }
        method.invoke(action, keys);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.isEmpty();
    }

    private static Item item(ItemStack stack) {
        return isEmpty(stack) ? null : stack.getItem();
    }

    private static void logInfo(String message) {
        try {
            Class<?> api = Class.forName("crafttweaker.CraftTweakerAPI", false, CraftTweakerRecipeRemovalOptimizations.class.getClassLoader());
            Method method = api.getMethod("logInfo", String.class);
            method.invoke(null, message);
        } catch (Throwable ignored) {
        }
    }

    private static void logFallback(String message, Throwable throwable) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            GPOM.LOGGER.warn(message, throwable);
        }
    }

    private static final class RemoveContext {
        private final RecipeIndex index;
        private final boolean fast;

        private RemoveContext(RecipeIndex index, boolean fast) {
            this.index = index;
            this.fast = fast;
        }
    }

    private static final class RecipeIndex {
        private final Set<Map.Entry<ResourceLocation, IRecipe>> source;
        private final Map<Item, List<Entry>> byItem = new IdentityHashMap<>();
        private final Map<ResourceLocation, Entry> byKey = new LinkedHashMap<>();

        private RecipeIndex(Set<Map.Entry<ResourceLocation, IRecipe>> source) {
            this.source = source;
            for (Map.Entry<ResourceLocation, IRecipe> sourceEntry : source) {
                if (sourceEntry == null || sourceEntry.getKey() == null || sourceEntry.getValue() == null) {
                    continue;
                }
                Entry entry = new Entry(sourceEntry.getKey(), sourceEntry.getValue());
                byKey.put(entry.key, entry);
                Item item = item(entry.recipe.getRecipeOutput());
                if (item != null) {
                    byItem.computeIfAbsent(item, ignored -> new ArrayList<>()).add(entry);
                }
            }
        }

        private Iterable<Entry> allEntries() {
            return new ArrayList<>(byKey.values());
        }

        private Iterable<Entry> entriesFor(List<ItemStack> stacks) {
            Set<Entry> result = new LinkedHashSet<>();
            for (ItemStack stack : stacks) {
                Item item = item(stack);
                if (item == null) {
                    continue;
                }
                List<Entry> entries = byItem.get(item);
                if (entries != null) {
                    result.addAll(entries);
                }
            }
            return result;
        }

        private void remove(List<Entry> entries) {
            for (Entry entry : entries) {
                byKey.remove(entry.key);
                Item item = item(entry.recipe.getRecipeOutput());
                List<Entry> itemEntries = item == null ? null : byItem.get(item);
                if (itemEntries != null) {
                    itemEntries.remove(entry);
                    if (itemEntries.isEmpty()) {
                        byItem.remove(item);
                    }
                }
            }
        }
    }

    private static final class Entry {
        private final ResourceLocation key;
        private final IRecipe recipe;

        private Entry(ResourceLocation key, IRecipe recipe) {
            this.key = key;
            this.recipe = recipe;
        }
    }
}
