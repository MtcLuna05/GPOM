package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ThermalExpansionRecipeOptimizations {
    private static final boolean FAST_SAWMILL_CRAFTING_RESULT = Boolean.parseBoolean(System.getProperty("gpom.thermalexpansion.fastSawmillCraftingResult", "true"));
    private static final ConcurrentHashMap<String, ItemStack> SAWMILL_CRAFTING_RESULT_CACHE = new ConcurrentHashMap<>();
    private static final Method RECIPE_CAN_FIT = RecipeOptimizationHelper.findMappedMethod(IRecipe.class, "canFit", "func_194133_a", int.class, int.class);
    private static final Method RECIPE_MATCHES = RecipeOptimizationHelper.findMappedMethod(IRecipe.class, "matches", "func_77569_a", InventoryCrafting.class, World.class);
    private static final Method RECIPE_RESULT = RecipeOptimizationHelper.findMappedMethod(IRecipe.class, "getCraftingResult", "func_77572_b", InventoryCrafting.class);
    private static volatile List<IRecipe> singleSlotRecipes;
    private static volatile boolean fallbackLogged;

    private ThermalExpansionRecipeOptimizations() {
    }

    public static ItemStack getSawmillCraftingResult(InventoryCrafting inventory, World world) {
        if (!FAST_SAWMILL_CRAFTING_RESULT
                || inventory == null
                || world != null
                || !TargetedModVersions.isThermalExpansionClass("cofh.thermalexpansion.util.managers.machine.SawmillManager")) {
            return findMatchingResult(inventory, world);
        }
        try {
            String key = RecipeOptimizationHelper.stackCacheKey(RecipeOptimizationHelper.inventoryStack(inventory, 0));
            ItemStack cached = SAWMILL_CRAFTING_RESULT_CACHE.get(key);
            if (cached != null) {
                return RecipeOptimizationHelper.isEmpty(cached) ? RecipeOptimizationHelper.emptyStack() : RecipeOptimizationHelper.copy(cached);
            }

            long startedAt = StartupProfiler.beginProbe();
            ItemStack result = findMatchingSingleSlotResult(inventory);
            SAWMILL_CRAFTING_RESULT_CACHE.putIfAbsent(key, RecipeOptimizationHelper.isEmpty(result) ? RecipeOptimizationHelper.emptyStack() : RecipeOptimizationHelper.copy(result));
            StartupProfiler.endProbe("TE SawmillManager.getCraftingResult cached", startedAt);
            return RecipeOptimizationHelper.isEmpty(result) ? RecipeOptimizationHelper.emptyStack() : RecipeOptimizationHelper.copy(result);
        } catch (Throwable throwable) {
            logFallback("Thermal Expansion sawmill crafting-result cache failed; falling back to CraftingManager", throwable);
            return findMatchingResult(inventory, world);
        }
    }

    private static ItemStack findMatchingSingleSlotResult(InventoryCrafting inventory) {
        if (RECIPE_MATCHES == null || RECIPE_RESULT == null) {
            return findMatchingResult(inventory, null);
        }
        for (IRecipe recipe : singleSlotRecipes()) {
            if (recipeMatches(recipe, inventory)) {
                return recipeCraftingResult(recipe, inventory);
            }
        }
        return RecipeOptimizationHelper.emptyStack();
    }

    private static List<IRecipe> singleSlotRecipes() {
        List<IRecipe> recipes = singleSlotRecipes;
        if (recipes != null) {
            return recipes;
        }
        synchronized (ThermalExpansionRecipeOptimizations.class) {
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
            StartupProfiler.endProbeAlways("TE SawmillManager.singleSlotRecipeList", startedAt);
            return recipes;
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
        if (recipe == null || inventory == null) {
            return RecipeOptimizationHelper.emptyStack();
        }
        return RecipeOptimizationHelper.recipeCraftingResult(recipe, inventory);
    }

    private static ItemStack findMatchingResult(InventoryCrafting inventory, World world) {
        return RecipeOptimizationHelper.findMatchingResult(inventory, world);
    }

    private static void logFallback(String message, Throwable throwable) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            GPOM.LOGGER.warn(message, throwable);
        }
    }
}
