package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

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
        if (recipe == null || inventory == null) {
            return emptyStack();
        }
        ItemStack result = MinecraftMappingCompat.recipeCraftingResult(recipe, inventory);
        return result == null ? emptyStack() : result;
    }

    private static ItemStack inventoryStack(InventoryCrafting inventory, int slot) {
        ItemStack value = MinecraftMappingCompat.inventoryStackInSlot(inventory, slot);
        return value == null ? emptyStack() : value;
    }

    private static boolean isEmpty(ItemStack stack) {
        return MinecraftMappingCompat.itemStackIsEmpty(stack);
    }

    private static ItemStack copy(ItemStack stack) {
        if (isEmpty(stack)) {
            return emptyStack();
        }
        ItemStack copy = MinecraftMappingCompat.itemStackCopy(stack);
        return copy == null ? emptyStack() : copy;
    }

    private static ItemStack emptyStack() {
        return MinecraftMappingCompat.emptyStack();
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
        Item item = MinecraftMappingCompat.itemStackItem(stack);
        ResourceLocation name = itemName(item);
        String itemName = name != null ? name.toString() : String.valueOf(MinecraftMappingCompat.itemIdFromItem(item));
        Object tag = MinecraftMappingCompat.itemStackHasTagCompound(stack) ? MinecraftMappingCompat.itemStackTagCompound(stack) : null;
        return itemName + '#' + stackMetadata(stack) + '#' + (tag != null ? tag.toString() : "");
    }

    private static ResourceLocation itemName(Item item) {
        if (item == null) {
            return null;
        }
        try {
            return ForgeRegistries.ITEMS.getKey(item);
        } catch (Throwable throwable) {
            logRecipeBridgeFailure("ForgeRegistries.ITEMS.getKey", throwable);
            return null;
        }
    }

    private static int stackMetadata(ItemStack stack) {
        return MinecraftMappingCompat.itemStackMetadata(stack);
    }

}
