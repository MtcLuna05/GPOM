package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class ThermalExpansionRecipeOptimizations {
    private static final boolean FAST_SAWMILL_CRAFTING_RESULT = Boolean.parseBoolean(System.getProperty("gpom.thermalexpansion.fastSawmillCraftingResult", "true"));
    private static final ConcurrentHashMap<String, ItemStack> SAWMILL_CRAFTING_RESULT_CACHE = new ConcurrentHashMap<>();
    private static final Method CRAFTING_RESULT = findMethod(CraftingManager.class, "findMatchingResult", "func_82787_a", InventoryCrafting.class, World.class);
    private static final Method RECIPE_CAN_FIT = findMethod(IRecipe.class, "canFit", "func_194133_a", int.class, int.class);
    private static final Method RECIPE_MATCHES = findMethod(IRecipe.class, "matches", "func_77569_a", InventoryCrafting.class, World.class);
    private static final Method RECIPE_RESULT = findMethod(IRecipe.class, "getCraftingResult", "func_77572_b", InventoryCrafting.class);
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
            String key = key(inventoryStack(inventory, 0));
            ItemStack cached = SAWMILL_CRAFTING_RESULT_CACHE.get(key);
            if (cached != null) {
                return isEmpty(cached) ? emptyStack() : copy(cached);
            }

            long startedAt = StartupProfiler.beginProbe();
            ItemStack result = findMatchingSingleSlotResult(inventory);
            SAWMILL_CRAFTING_RESULT_CACHE.putIfAbsent(key, isEmpty(result) ? emptyStack() : copy(result));
            StartupProfiler.endProbe("TE SawmillManager.getCraftingResult cached", startedAt);
            return isEmpty(result) ? emptyStack() : copy(result);
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
        return emptyStack();
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
}
