package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class TechRebornRecipeOptimizations {
    private static final boolean FAST_SAWMILL_MATCHING = Boolean.parseBoolean(System.getProperty("gpom.techreborn.fastIndustrialSawmillMatching", "true"));
    private static final ConcurrentHashMap<String, ItemStack> SAWMILL_MATCH_CACHE = new ConcurrentHashMap<>();
    private static final Method RECIPE_CAN_FIT = findRecipeMethod(boolean.class, "canFit", "func_194133_a", int.class, int.class);
    private static final Method RECIPE_MATCHES = findRecipeMethod(boolean.class, "matches", "func_77569_a", InventoryCrafting.class, net.minecraft.world.World.class);
    private static final Method RECIPE_RESULT = findRecipeMethod(ItemStack.class, "getCraftingResult", "func_77572_b", InventoryCrafting.class);
    private static final Method INVENTORY_GET_STACK = findMethod(InventoryCrafting.class, "getStackInSlot", "func_70301_a", int.class);
    private static final Method STACK_IS_EMPTY = findMethod(ItemStack.class, "isEmpty", "func_190926_b");
    private static final Method STACK_COPY = findMethod(ItemStack.class, "copy", "func_77946_l");
    private static final Method STACK_GET_ITEM = findMethod(ItemStack.class, "getItem", "func_77973_b");
    private static final Method STACK_GET_META = findMethod(ItemStack.class, "getMetadata", "func_77960_j");
    private static final Method STACK_HAS_TAG = findMethod(ItemStack.class, "hasTagCompound", "func_77942_o");
    private static final Method STACK_GET_TAG = findMethod(ItemStack.class, "getTagCompound", "func_77978_p");
    private static final Method ITEM_ID_FROM_ITEM = findMethod(Item.class, "getIdFromItem", "func_150891_b", Item.class);
    private static final ItemStack EMPTY_STACK = findEmptyStack();
    private static volatile List<IRecipe> singleSlotRecipes;
    private static volatile boolean sawmillFallbackLogged;
    private static volatile boolean recipeBridgeFallbackLogged;

    private TechRebornRecipeOptimizations() {
    }

    public static ItemStack findMatchingIndustrialSawmillRecipe(InventoryCrafting inventory) {
        if (!FAST_SAWMILL_MATCHING || inventory == null || !TargetedModVersions.isTechRebornClass("techreborn.init.recipes.IndustrialSawmillRecipes")) {
            return findMatchingByScan(inventory);
        }
        try {
            ItemStack input = inventoryStack(inventory, 0);
            String key = key(input);
            ItemStack cached = SAWMILL_MATCH_CACHE.get(key);
            if (cached != null) {
                return isEmpty(cached) ? emptyStack() : copy(cached);
            }

            long startedAt = StartupProfiler.beginProbe();
            ItemStack result = findMatchingFromCachedRecipeList(inventory);
            SAWMILL_MATCH_CACHE.putIfAbsent(key, isEmpty(result) ? emptyStack() : copy(result));
            StartupProfiler.endProbe("TR IndustrialSawmillRecipes.findMatchingRecipe cached", startedAt);
            return isEmpty(result) ? emptyStack() : copy(result);
        } catch (Throwable throwable) {
            if (!sawmillFallbackLogged) {
                sawmillFallbackLogged = true;
                GPOM.LOGGER.warn("Tech Reborn fast industrial sawmill matching failed; falling back to registry scan", throwable);
            }
            return findMatchingByScan(inventory);
        }
    }

    private static ItemStack findMatchingFromCachedRecipeList(InventoryCrafting inventory) {
        for (IRecipe recipe : singleSlotRecipes()) {
            if (recipeMatches(recipe, inventory)) {
                return recipeCraftingResult(recipe, inventory);
            }
        }
        return emptyStack();
    }

    private static List<IRecipe> singleSlotRecipes() {
        List<IRecipe> recipes = singleSlotRecipes;
        if (recipes != null) {
            return recipes;
        }
        synchronized (TechRebornRecipeOptimizations.class) {
            recipes = singleSlotRecipes;
            if (recipes != null) {
                return recipes;
            }
            long startedAt = StartupProfiler.beginProbe();
            List<IRecipe> filtered = new ArrayList<>();
            for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
                if (recipeCanFit(recipe, 1, 1)) {
                    filtered.add(recipe);
                }
            }
            recipes = Collections.unmodifiableList(filtered);
            singleSlotRecipes = recipes;
            StartupProfiler.endProbeAlways("TR IndustrialSawmillRecipes.singleSlotRecipeList", startedAt);
            return recipes;
        }
    }

    private static ItemStack findMatchingByScan(InventoryCrafting inventory) {
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
            if (recipeCanFit(recipe, 1, 1) && recipeMatches(recipe, inventory)) {
                return recipeCraftingResult(recipe, inventory);
            }
        }
        return emptyStack();
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
            logRecipeBridgeFailure("canFit", throwable);
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
            logRecipeBridgeFailure("matches", throwable);
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
            logRecipeBridgeFailure("getCraftingResult", throwable);
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
            logRecipeBridgeFailure("InventoryCrafting.getStackInSlot", throwable);
            return emptyStack();
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
            logRecipeBridgeFailure("ItemStack.isEmpty", throwable);
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
            logRecipeBridgeFailure("ItemStack.copy", throwable);
            return emptyStack();
        }
    }

    private static ItemStack emptyStack() {
        return EMPTY_STACK != null ? EMPTY_STACK : null;
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

    private static Method findRecipeMethod(Class<?> returnType, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findRecipeMethod(mcpName, parameterTypes);
        if (method == null) {
            method = findRecipeMethod(srgName, parameterTypes);
        }
        if (method != null && (returnType == null || method.getReturnType() == returnType)) {
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static Method findRecipeMethod(String name, Class<?>... parameterTypes) {
        try {
            return IRecipe.class.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(owner, mcpName, parameterTypes);
        if (method == null) {
            method = findMethod(owner, srgName, parameterTypes);
        }
        if (method != null) {
            method.setAccessible(true);
        }
        return method;
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String mcpName, String srgName) {
        Field field = findField(owner, mcpName);
        if (field == null) {
            field = findField(owner, srgName);
        }
        if (field != null) {
            field.setAccessible(true);
        }
        return field;
    }

    private static Field findField(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private static void logRecipeBridgeFailure(String method, Throwable throwable) {
        if (!recipeBridgeFallbackLogged) {
            recipeBridgeFallbackLogged = true;
            GPOM.LOGGER.warn("Tech Reborn industrial sawmill recipe bridge failed at {}; continuing with conservative fallback", method, throwable);
        }
    }

    private static String key(ItemStack stack) {
        if (isEmpty(stack)) {
            return "empty";
        }
        Object item = invoke(STACK_GET_ITEM, stack);
        ResourceLocation name = itemName(item);
        String itemName = name != null ? name.toString() : String.valueOf(itemId(item));
        Object tag = Boolean.TRUE.equals(invoke(STACK_HAS_TAG, stack)) ? invoke(STACK_GET_TAG, stack) : null;
        return itemName + '#' + stackMetadata(stack) + '#' + (tag != null ? tag.toString() : "");
    }

    private static ResourceLocation itemName(Object item) {
        if (!(item instanceof Item)) {
            return null;
        }
        try {
            return ForgeRegistries.ITEMS.getKey((Item) item);
        } catch (Throwable throwable) {
            logRecipeBridgeFailure("ForgeRegistries.ITEMS.getKey", throwable);
            return null;
        }
    }

    private static int itemId(Object item) {
        if (!(item instanceof Item) || ITEM_ID_FROM_ITEM == null) {
            return 0;
        }
        Object value = invoke(ITEM_ID_FROM_ITEM, null, item);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int stackMetadata(ItemStack stack) {
        Object value = invoke(STACK_GET_META, stack);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (Throwable throwable) {
            logRecipeBridgeFailure(method.getName(), throwable);
            return null;
        }
    }
}
