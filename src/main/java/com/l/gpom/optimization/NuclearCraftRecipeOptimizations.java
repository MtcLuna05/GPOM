package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NuclearCraftRecipeOptimizations {
    private static final ConcurrentHashMap<String, ItemStack> MANUFACTORY_LOG_CRAFTING_RESULT_CACHE = new ConcurrentHashMap<>();
    private static final Method CRAFTING_RESULT = findMethod(CraftingManager.class, "findMatchingResult", "func_82787_a", InventoryCrafting.class, World.class);
    private static final Method RECIPE_CAN_FIT = findMethod(IRecipe.class, "canFit", "func_194133_a", int.class, int.class);
    private static final Method RECIPE_MATCHES = findMethod(IRecipe.class, "matches", "func_77569_a", InventoryCrafting.class, World.class);
    private static final Method RECIPE_RESULT = findMethod(IRecipe.class, "getCraftingResult", "func_77572_b", InventoryCrafting.class);
    private static final Method RECIPE_INGREDIENTS = findMethod(IRecipe.class, "getIngredients", "func_192400_c");
    private static final Method INGREDIENT_APPLY = findMethod(Ingredient.class, "apply", "apply", ItemStack.class);
    private static final Method INVENTORY_GET_STACK = findMethod(InventoryCrafting.class, "getStackInSlot", "func_70301_a", int.class);
    private static final Method STACK_IS_EMPTY = findMethod(ItemStack.class, "isEmpty", "func_190926_b");
    private static final Method STACK_COPY = findMethod(ItemStack.class, "copy", "func_77946_l");
    private static final Method STACK_GET_ITEM = findMethod(ItemStack.class, "getItem", "func_77973_b");
    private static final Method STACK_GET_META = findMethod(ItemStack.class, "getMetadata", "func_77960_j");
    private static final Method STACK_HAS_TAG = findMethod(ItemStack.class, "hasTagCompound", "func_77942_o");
    private static final Method STACK_GET_TAG = findMethod(ItemStack.class, "getTagCompound", "func_77978_p");
    private static final Method ITEM_ID_FROM_ITEM = findMethod(Item.class, "getIdFromItem", "func_150891_b", Item.class);
    private static final ItemStack EMPTY_STACK = findEmptyStack();
    private static final Ingredient EMPTY_INGREDIENT = findEmptyIngredient();
    private static volatile Constructor<?> oreIngredientConstructor;
    private static volatile Method addRecipeMethod;
    private static volatile Field oreProcessingField;
    private static volatile List<SingleInputRecipe> singleInputRecipes;
    private static volatile boolean fallbackLogged;

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
            String key = key(inventoryStack(inventory, 0));
            ItemStack cached = MANUFACTORY_LOG_CRAFTING_RESULT_CACHE.get(key);
            if (cached != null) {
                return isEmpty(cached) ? emptyStack() : copy(cached);
            }

            ItemStack result = findMatchingSingleSlotResult(inventory);
            if (isEmpty(result)) {
                result = findMatchingResult(inventory, world);
            }
            MANUFACTORY_LOG_CRAFTING_RESULT_CACHE.putIfAbsent(key, isEmpty(result) ? emptyStack() : copy(result));
            return isEmpty(result) ? emptyStack() : copy(result);
        } catch (Throwable throwable) {
            logFallback("NuclearCraft Manufactory log crafting-result cache failed; falling back to CraftingManager", throwable);
            return findMatchingResult(inventory, world);
        } finally {
            StartupProfiler.endProbe("POSTPRE NC Manufactory.addLogRecipes crafting-result cache", startedAt);
        }
    }

    private static ItemStack findMatchingSingleSlotResult(InventoryCrafting inventory) {
        if (RECIPE_MATCHES == null || RECIPE_RESULT == null) {
            return findMatchingResult(inventory, null);
        }
        ItemStack stack = inventoryStack(inventory, 0);
        for (SingleInputRecipe candidate : singleInputRecipes()) {
            if (ingredientApplies(candidate.ingredient, stack) && recipeMatches(candidate.recipe, inventory)) {
                return recipeCraftingResult(candidate.recipe, inventory);
            }
        }
        return emptyStack();
    }

    private static List<SingleInputRecipe> singleInputRecipes() {
        List<SingleInputRecipe> recipes = singleInputRecipes;
        if (recipes != null) {
            return recipes;
        }
        synchronized (NuclearCraftRecipeOptimizations.class) {
            recipes = singleInputRecipes;
            if (recipes != null) {
                return recipes;
            }
            long startedAt = StartupProfiler.beginProbe();
            List<SingleInputRecipe> filtered = new ArrayList<>();
            for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
                Ingredient ingredient = singleNonEmptyIngredient(recipe);
                if (ingredient != null && recipeCanFit(recipe, 1, 1)) {
                    filtered.add(new SingleInputRecipe(recipe, ingredient));
                }
            }
            recipes = Collections.unmodifiableList(filtered);
            singleInputRecipes = recipes;
            StartupProfiler.endProbeAlways("POSTPRE NC Manufactory.addLogRecipes single-input recipe list", startedAt);
            return recipes;
        }
    }

    @SuppressWarnings("unchecked")
    private static Ingredient singleNonEmptyIngredient(IRecipe recipe) {
        if (recipe == null || RECIPE_INGREDIENTS == null) {
            return null;
        }
        try {
            Object value = RECIPE_INGREDIENTS.invoke(recipe);
            if (!(value instanceof NonNullList)) {
                return null;
            }
            Ingredient found = null;
            for (Ingredient ingredient : (NonNullList<Ingredient>) value) {
                if (isEmptyIngredient(ingredient)) {
                    continue;
                }
                if (found != null) {
                    return null;
                }
                found = ingredient;
            }
            return found;
        } catch (Throwable throwable) {
            logFallback("IRecipe.getIngredients bridge failed", throwable);
            return null;
        }
    }

    private static boolean ingredientApplies(Ingredient ingredient, ItemStack stack) {
        if (ingredient == null || isEmpty(stack) || INGREDIENT_APPLY == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(INGREDIENT_APPLY.invoke(ingredient, stack));
        } catch (Throwable throwable) {
            logFallback("Ingredient.apply bridge failed", throwable);
            return false;
        }
    }

    private static boolean isEmptyIngredient(Ingredient ingredient) {
        if (ingredient == null) {
            return true;
        }
        if (EMPTY_INGREDIENT != null && ingredient == EMPTY_INGREDIENT) {
            return true;
        }
        ItemStack empty = emptyStack();
        if (empty == null || INGREDIENT_APPLY == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(INGREDIENT_APPLY.invoke(ingredient, empty));
        } catch (Throwable throwable) {
            logFallback("Ingredient.empty bridge failed", throwable);
            return false;
        }
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
        if (recipe == null || inventory == null || RECIPE_RESULT == null) {
            return emptyStack();
        }
        try {
            Object result = RECIPE_RESULT.invoke(recipe, inventory);
            return result instanceof ItemStack ? (ItemStack) result : emptyStack();
        } catch (Throwable throwable) {
            logFallback("IRecipe.getCraftingResult bridge failed", throwable);
            return emptyStack();
        }
    }

    private static ItemStack findMatchingResult(InventoryCrafting inventory, World world) {
        if (CRAFTING_RESULT == null) {
            return emptyStack();
        }
        try {
            Object result = CRAFTING_RESULT.invoke(null, inventory, world);
            return result instanceof ItemStack ? (ItemStack) result : emptyStack();
        } catch (Throwable throwable) {
            logFallback("CraftingManager.findMatchingResult bridge failed", throwable);
            return emptyStack();
        }
    }

    private static ItemStack inventoryStack(InventoryCrafting inventory, int slot) {
        if (inventory == null || INVENTORY_GET_STACK == null) {
            return emptyStack();
        }
        try {
            Object value = INVENTORY_GET_STACK.invoke(inventory, slot);
            return value instanceof ItemStack ? (ItemStack) value : emptyStack();
        } catch (Throwable throwable) {
            logFallback("InventoryCrafting.getStackInSlot bridge failed", throwable);
            return emptyStack();
        }
    }

    private static String key(ItemStack stack) {
        if (isEmpty(stack)) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder(48);
        builder.append(itemId(stack)).append(':').append(meta(stack));
        if (hasTag(stack)) {
            builder.append(':').append(tag(stack));
        }
        return builder.toString();
    }

    private static int itemId(ItemStack stack) {
        if (ITEM_ID_FROM_ITEM == null || STACK_GET_ITEM == null) {
            return System.identityHashCode(stack);
        }
        try {
            Object item = STACK_GET_ITEM.invoke(stack);
            Object id = ITEM_ID_FROM_ITEM.invoke(null, item);
            return id instanceof Integer ? (Integer) id : System.identityHashCode(item);
        } catch (Throwable throwable) {
            logFallback("ItemStack item id bridge failed", throwable);
            return System.identityHashCode(stack);
        }
    }

    private static int meta(ItemStack stack) {
        if (STACK_GET_META == null) {
            return 0;
        }
        try {
            Object value = STACK_GET_META.invoke(stack);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable throwable) {
            logFallback("ItemStack metadata bridge failed", throwable);
            return 0;
        }
    }

    private static boolean hasTag(ItemStack stack) {
        if (STACK_HAS_TAG == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(STACK_HAS_TAG.invoke(stack));
        } catch (Throwable throwable) {
            logFallback("ItemStack hasTag bridge failed", throwable);
            return false;
        }
    }

    private static Object tag(ItemStack stack) {
        if (STACK_GET_TAG == null) {
            return null;
        }
        try {
            return STACK_GET_TAG.invoke(stack);
        } catch (Throwable throwable) {
            logFallback("ItemStack tag bridge failed", throwable);
            return null;
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        if (stack == null || stack == EMPTY_STACK) {
            return true;
        }
        if (STACK_IS_EMPTY == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(STACK_IS_EMPTY.invoke(stack));
        } catch (Throwable throwable) {
            logFallback("ItemStack.isEmpty bridge failed", throwable);
            return false;
        }
    }

    private static ItemStack copy(ItemStack stack) {
        if (isEmpty(stack) || STACK_COPY == null) {
            return emptyStack();
        }
        try {
            Object value = STACK_COPY.invoke(stack);
            return value instanceof ItemStack ? (ItemStack) value : emptyStack();
        } catch (Throwable throwable) {
            logFallback("ItemStack.copy bridge failed", throwable);
            return emptyStack();
        }
    }

    private static ItemStack emptyStack() {
        return EMPTY_STACK;
    }

    private static ItemStack findEmptyStack() {
        Field field = findField(ItemStack.class, "EMPTY", "field_190927_a");
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(null);
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable ignored) {
            return null;
        }
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

    private static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(type, mcpName, parameterTypes);
        if (method == null) {
            method = findMethod(type, srgName, parameterTypes);
        }
        if (method != null) {
            method.setAccessible(true);
        }
        return method;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
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
