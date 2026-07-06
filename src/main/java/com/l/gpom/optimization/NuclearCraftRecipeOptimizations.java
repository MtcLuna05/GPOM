package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NuclearCraftRecipeOptimizations {
    private static final ConcurrentHashMap<String, ItemStack> MANUFACTORY_LOG_CRAFTING_RESULT_CACHE = new ConcurrentHashMap<>();
    private static final Method RECIPE_CAN_FIT = RecipeOptimizationHelper.findMappedMethod(IRecipe.class, "canFit", "func_194133_a", int.class, int.class);
    private static final Method RECIPE_MATCHES = RecipeOptimizationHelper.findMappedMethod(IRecipe.class, "matches", "func_77569_a", InventoryCrafting.class, World.class);
    private static final Method RECIPE_RESULT = RecipeOptimizationHelper.findMappedMethod(IRecipe.class, "getCraftingResult", "func_77572_b", InventoryCrafting.class);
    private static final Ingredient EMPTY_INGREDIENT = findEmptyIngredient();
    private static volatile Constructor<?> oreIngredientConstructor;
    private static volatile Method addRecipeMethod;
    private static volatile Field oreProcessingField;
    private static volatile SingleInputRecipeIndex singleInputRecipeIndex;
    private static volatile boolean fallbackLogged;
    private static volatile boolean indexLogged;

    private NuclearCraftRecipeOptimizations() {
    }

    public static boolean addManufactoryMetalProcessingRecipes(Object handler) {
        if (!GpomEarlyConfig.nuclearCraftFastManufactoryMetalRecipesEnabled()
                || handler == null
                || !TargetedModVersions.isNuclearCraftClass(handler.getClass().getName())) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            String[] oreNames = OreDictionary.getOreNames();
            Set<String> oreNameSet = new HashSet<>(Arrays.asList(oreNames));
            Method addRecipe = addRecipeMethod(handler.getClass());
            Constructor<?> oreIngredient = oreIngredientConstructor();
            boolean oreProcessing = oreProcessingField().getBoolean(null);
            int added = 0;

            for (String ingot : oreNames) {
                if (ingot == null || !ingot.startsWith("ingot")) {
                    continue;
                }
                String type = ingot.substring(5);
                if ("silicon".equals(type)) {
                    continue;
                }
                String dust = "dust" + type;
                if (!oreNameSet.contains(dust)) {
                    continue;
                }
                if (oreProcessing) {
                    String ore = "ore" + type;
                    if (oreNameSet.contains(ore)) {
                        invokeAddRecipe(
                                addRecipe,
                                handler,
                                oreIngredient(oreIngredient, ore, 1),
                                oreIngredient(oreIngredient, dust, 2),
                                Double.valueOf(1.25D),
                                Double.valueOf(1.0D)
                        );
                        added++;
                    }
                }
                invokeAddRecipe(
                        addRecipe,
                        handler,
                        oreIngredient(oreIngredient, ingot, 1),
                        oreIngredient(oreIngredient, dust, 1),
                        Double.valueOf(1.0D),
                        Double.valueOf(1.0D)
                );
                added++;
            }

            GPOM.LOGGER.info("[NC Optimizations] Fast-added {} Manufactory metal recipe(s)", added);
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[NC Optimizations] Fast Manufactory metal recipes failed; using stock path", throwable);
            return false;
        } finally {
            StartupProfiler.endProbe("POSTPRE NC fast Manufactory.addMetalProcessingRecipes", startedAt);
        }
    }

    public static ItemStack getManufactoryLogCraftingResult(InventoryCrafting inventory, World world) {
        if (!GpomEarlyConfig.nuclearCraftCacheManufactoryLogCraftingResultsEnabled()
                || inventory == null
                || world != null
                || !TargetedModVersions.isNuclearCraftClass("nc.recipe.processor.ManufactoryRecipes")) {
            return findMatchingResult(inventory, world);
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            ItemStack stack = RecipeOptimizationHelper.inventoryStack(inventory, 0);
            String key = RecipeOptimizationHelper.stackCacheKey(stack);
            ItemStack cached = MANUFACTORY_LOG_CRAFTING_RESULT_CACHE.get(key);
            if (cached != null) {
                return RecipeOptimizationHelper.isEmpty(cached) ? RecipeOptimizationHelper.emptyStack() : RecipeOptimizationHelper.copy(cached);
            }

            SingleInputRecipeIndex index = singleInputRecipeIndex();
            ItemStack result = findMatchingSingleSlotResult(index, stack, inventory);
            if (RecipeOptimizationHelper.isEmpty(result)) {
                if (canSkipEmptyManufactoryLogFallback(index, inventory)) {
                    long skipStartedAt = StartupProfiler.beginProbe();
                    StartupProfiler.endProbeAlways("POSTPRE NC Manufactory.addLogRecipes skipped empty stock fallback", skipStartedAt);
                    MANUFACTORY_LOG_CRAFTING_RESULT_CACHE.putIfAbsent(key, RecipeOptimizationHelper.emptyStack());
                    return RecipeOptimizationHelper.emptyStack();
                } else {
                    long fallbackStartedAt = StartupProfiler.beginProbe();
                    result = findMatchingResult(inventory, world);
                    StartupProfiler.endProbe(
                            RecipeOptimizationHelper.isEmpty(result)
                                    ? "POSTPRE NC Manufactory.addLogRecipes empty stock fallback CraftingManager lookup"
                                    : "POSTPRE NC Manufactory.addLogRecipes non-empty stock fallback CraftingManager lookup",
                            fallbackStartedAt
                    );
                }
            }
            MANUFACTORY_LOG_CRAFTING_RESULT_CACHE.putIfAbsent(key, RecipeOptimizationHelper.isEmpty(result) ? RecipeOptimizationHelper.emptyStack() : RecipeOptimizationHelper.copy(result));
            return RecipeOptimizationHelper.isEmpty(result) ? RecipeOptimizationHelper.emptyStack() : RecipeOptimizationHelper.copy(result);
        } catch (Throwable throwable) {
            logFallback("NuclearCraft Manufactory log crafting-result cache failed; falling back to CraftingManager", throwable);
            return findMatchingResult(inventory, world);
        } finally {
            StartupProfiler.endProbe("POSTPRE NC Manufactory.addLogRecipes crafting-result cache", startedAt);
        }
    }

    private static ItemStack findMatchingSingleSlotResult(SingleInputRecipeIndex index, ItemStack stack, InventoryCrafting inventory) {
        if (RECIPE_MATCHES == null || RECIPE_RESULT == null) {
            return findMatchingResult(inventory, null);
        }
        if (RecipeOptimizationHelper.isEmpty(stack)) {
            return RecipeOptimizationHelper.emptyStack();
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            return findMatchingIndexedSingleSlotResult(index, stack, inventory);
        } finally {
            StartupProfiler.endProbe("POSTPRE NC Manufactory.addLogRecipes indexed single-input lookup", startedAt);
        }
    }

    private static boolean canSkipEmptyManufactoryLogFallback(SingleInputRecipeIndex index, InventoryCrafting inventory) {
        return GpomEarlyConfig.nuclearCraftSkipEmptyManufactoryLogCraftingFallbackEnabled()
                && index != null
                && RECIPE_MATCHES != null
                && RECIPE_RESULT != null
                && onlyFirstSlotUsed(inventory);
    }

    private static boolean onlyFirstSlotUsed(InventoryCrafting inventory) {
        if (inventory == null) {
            return false;
        }
        for (int slot = 1; slot < 9; slot++) {
            if (!RecipeOptimizationHelper.isEmpty(RecipeOptimizationHelper.inventoryStack(inventory, slot))) {
                return false;
            }
        }
        return true;
    }

    private static ItemStack findMatchingIndexedSingleSlotResult(SingleInputRecipeIndex index, ItemStack stack, InventoryCrafting inventory) {
        if (index == null) {
            return RecipeOptimizationHelper.emptyStack();
        }

        Set<SingleInputRecipe> seen = Collections.newSetFromMap(new IdentityHashMap<SingleInputRecipe, Boolean>());
        ItemStack result = matchCandidates(index.exactCandidates.get(RecipeOptimizationHelper.stackKey(stack)), stack, inventory, seen);
        if (!RecipeOptimizationHelper.isEmpty(result)) {
            return result;
        }
        result = matchCandidates(index.wildcardCandidates.get(Integer.valueOf(RecipeOptimizationHelper.itemId(stack))), stack, inventory, seen);
        if (!RecipeOptimizationHelper.isEmpty(result)) {
            return result;
        }
        return matchCandidates(index.fallbackCandidates, stack, inventory, seen);
    }

    private static ItemStack matchCandidates(List<SingleInputRecipe> candidates, ItemStack stack, InventoryCrafting inventory, Set<SingleInputRecipe> seen) {
        if (candidates == null || candidates.isEmpty()) {
            return RecipeOptimizationHelper.emptyStack();
        }
        for (SingleInputRecipe candidate : candidates) {
            if (!seen.add(candidate)) {
                continue;
            }
            if (ingredientApplies(candidate.ingredient, stack) && recipeMatches(candidate.recipe, inventory)) {
                return recipeCraftingResult(candidate.recipe, inventory);
            }
        }
        return RecipeOptimizationHelper.emptyStack();
    }

    private static SingleInputRecipeIndex singleInputRecipeIndex() {
        SingleInputRecipeIndex index = singleInputRecipeIndex;
        if (index != null) {
            return index;
        }

        synchronized (NuclearCraftRecipeOptimizations.class) {
            index = singleInputRecipeIndex;
            if (index != null) {
                return index;
            }
            long startedAt = StartupProfiler.beginProbe();
            Map<Long, List<SingleInputRecipe>> exactCandidates = new HashMap<>();
            Map<Integer, List<SingleInputRecipe>> wildcardCandidates = new HashMap<>();
            List<SingleInputRecipe> fallbackCandidates = new ArrayList<>();
            int recipeCount = 0;
            for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
                Ingredient ingredient = singleNonEmptyIngredient(recipe);
                if (ingredient != null && recipeCanFit(recipe, 3, 3)) {
                    recipeCount++;
                    SingleInputRecipe candidate = new SingleInputRecipe(recipe, ingredient);
                    if (!indexCandidate(candidate, exactCandidates, wildcardCandidates)) {
                        fallbackCandidates.add(candidate);
                    }
                }
            }
            index = new SingleInputRecipeIndex(
                    immutableCopy(exactCandidates),
                    immutableCopy(wildcardCandidates),
                    Collections.unmodifiableList(fallbackCandidates)
            );
            singleInputRecipeIndex = index;
            if (!indexLogged && GpomEarlyConfig.cacheInfoLogsEnabled()) {
                indexLogged = true;
                GPOM.LOGGER.info(
                        "[NC Optimizations] Indexed {} one-slot recipe candidate(s): exactKeys={}, wildcardItems={}, fallbackCandidates={}",
                        recipeCount,
                        index.exactCandidates.size(),
                        index.wildcardCandidates.size(),
                        index.fallbackCandidates.size()
                );
            }
            StartupProfiler.endProbeAlways("POSTPRE NC Manufactory.addLogRecipes single-input recipe index", startedAt);
            return index;
        }
    }

    private static boolean indexCandidate(
            SingleInputRecipe candidate,
            Map<Long, List<SingleInputRecipe>> exactCandidates,
            Map<Integer, List<SingleInputRecipe>> wildcardCandidates
    ) {
        ItemStack[] stacks = matchingStacks(candidate.ingredient);
        if (stacks == null || stacks.length == 0) {
            return false;
        }

        boolean indexed = false;
        Set<Long> seenExactKeys = new HashSet<>();
        Set<Integer> seenWildcardItems = new HashSet<>();
        for (ItemStack stack : stacks) {
            if (RecipeOptimizationHelper.isEmpty(stack)) {
                continue;
            }
            int itemId = RecipeOptimizationHelper.itemId(stack);
            int meta = RecipeOptimizationHelper.meta(stack);
            if (meta == OreDictionary.WILDCARD_VALUE) {
                Integer key = Integer.valueOf(itemId);
                if (seenWildcardItems.add(key)) {
                    addCandidate(wildcardCandidates, key, candidate);
                    indexed = true;
                }
            } else {
                Long key = Long.valueOf(RecipeOptimizationHelper.stackKey(itemId, meta));
                if (seenExactKeys.add(key)) {
                    addCandidate(exactCandidates, key, candidate);
                    indexed = true;
                }
            }
        }
        return indexed;
    }

    private static <K> void addCandidate(Map<K, List<SingleInputRecipe>> candidates, K key, SingleInputRecipe candidate) {
        List<SingleInputRecipe> list = candidates.get(key);
        if (list == null) {
            list = new ArrayList<>();
            candidates.put(key, list);
        }
        list.add(candidate);
    }

    private static <K> Map<K, List<SingleInputRecipe>> immutableCopy(Map<K, List<SingleInputRecipe>> input) {
        Map<K, List<SingleInputRecipe>> copy = new HashMap<>();
        for (Map.Entry<K, List<SingleInputRecipe>> entry : input.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static ItemStack[] matchingStacks(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        ItemStack[] stacks = MinecraftMappingCompat.ingredientMatchingStacks(ingredient);
        return stacks.length == 0 ? null : stacks;
    }

    private static Ingredient singleNonEmptyIngredient(IRecipe recipe) {
        if (recipe == null) {
            return null;
        }
        NonNullList<Ingredient> ingredients = MinecraftMappingCompat.recipeIngredients(recipe);
        if (ingredients == null) {
            return null;
        }
        Ingredient found = null;
        for (Ingredient ingredient : ingredients) {
            if (isEmptyIngredient(ingredient)) {
                continue;
            }
            if (found != null) {
                return null;
            }
            found = ingredient;
        }
        return found;
    }

    private static boolean ingredientApplies(Ingredient ingredient, ItemStack stack) {
        if (ingredient == null || RecipeOptimizationHelper.isEmpty(stack)) {
            return false;
        }
        return MinecraftMappingCompat.ingredientApply(ingredient, stack);
    }

    private static boolean isEmptyIngredient(Ingredient ingredient) {
        if (ingredient == null) {
            return true;
        }
        if (EMPTY_INGREDIENT != null && ingredient == EMPTY_INGREDIENT) {
            return true;
        }
        ItemStack empty = RecipeOptimizationHelper.emptyStack();
        if (empty == null) {
            return false;
        }
        return MinecraftMappingCompat.ingredientApply(ingredient, empty);
    }

    private static boolean recipeCanFit(IRecipe recipe, int width, int height) {
        if (recipe == null) {
            return false;
        }
        if (RECIPE_CAN_FIT == null) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(RECIPE_CAN_FIT.invoke(recipe, width, height));
        } catch (Throwable throwable) {
            logFallback("IRecipe.canFit bridge failed", throwable);
            return true;
        }
    }

    private static boolean recipeMatches(IRecipe recipe, InventoryCrafting inventory) {
        if (recipe == null || inventory == null || RECIPE_MATCHES == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(RECIPE_MATCHES.invoke(recipe, inventory, null));
        } catch (Throwable throwable) {
            logFallback("IRecipe.matches bridge failed", throwable);
            return false;
        }
    }

    private static ItemStack recipeCraftingResult(IRecipe recipe, InventoryCrafting inventory) {
        if (recipe == null || inventory == null) {
            return RecipeOptimizationHelper.emptyStack();
        }
        return RecipeOptimizationHelper.recipeCraftingResult(recipe, inventory);
    }

    private static ItemStack findMatchingResult(InventoryCrafting inventory, World world) {
        return RecipeOptimizationHelper.findMatchingResult(inventory, world);
    }

    private static Ingredient findEmptyIngredient() {
        Field field = findField(Ingredient.class, "EMPTY", "field_193370_a");
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(null);
            return value instanceof Ingredient ? (Ingredient) value : null;
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

    private static void logFallback(String message, Throwable throwable) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            GPOM.LOGGER.warn(message, throwable);
        }
    }

    private static final class SingleInputRecipe {
        private final IRecipe recipe;
        private final Ingredient ingredient;

        private SingleInputRecipe(IRecipe recipe, Ingredient ingredient) {
            this.recipe = recipe;
            this.ingredient = ingredient;
        }
    }

    private static final class SingleInputRecipeIndex {
        private final Map<Long, List<SingleInputRecipe>> exactCandidates;
        private final Map<Integer, List<SingleInputRecipe>> wildcardCandidates;
        private final List<SingleInputRecipe> fallbackCandidates;

        private SingleInputRecipeIndex(
                Map<Long, List<SingleInputRecipe>> exactCandidates,
                Map<Integer, List<SingleInputRecipe>> wildcardCandidates,
                List<SingleInputRecipe> fallbackCandidates
        ) {
            this.exactCandidates = exactCandidates;
            this.wildcardCandidates = wildcardCandidates;
            this.fallbackCandidates = fallbackCandidates;
        }
    }

    private static void invokeAddRecipe(Method addRecipe, Object handler, Object... recipe) throws ReflectiveOperationException {
        addRecipe.invoke(handler, new Object[] { recipe });
    }

    private static Object oreIngredient(Constructor<?> constructor, String oreName, int stackSize) throws ReflectiveOperationException {
        return constructor.newInstance(oreName, stackSize);
    }

    private static Method addRecipeMethod(Class<?> handlerClass) throws NoSuchMethodException {
        Method method = addRecipeMethod;
        if (method == null) {
            method = handlerClass.getMethod("addRecipe", Object[].class);
            method.setAccessible(true);
            addRecipeMethod = method;
        }
        return method;
    }

    private static Constructor<?> oreIngredientConstructor() throws ReflectiveOperationException {
        Constructor<?> constructor = oreIngredientConstructor;
        if (constructor == null) {
            Class<?> type = Class.forName(
                    "nc.recipe.ingredient.OreIngredient",
                    false,
                    NuclearCraftRecipeOptimizations.class.getClassLoader()
            );
            constructor = type.getConstructor(String.class, int.class);
            constructor.setAccessible(true);
            oreIngredientConstructor = constructor;
        }
        return constructor;
    }

    private static Field oreProcessingField() throws ReflectiveOperationException {
        Field field = oreProcessingField;
        if (field == null) {
            Class<?> type = Class.forName(
                    "nc.config.NCConfig",
                    false,
                    NuclearCraftRecipeOptimizations.class.getClassLoader()
            );
            field = type.getField("ore_processing");
            field.setAccessible(true);
            oreProcessingField = field;
        }
        return field;
    }
}
