package com.l.gpom.optimization;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import java.lang.reflect.Method;

final class RecipeOptimizationHelper {
    private RecipeOptimizationHelper() {
    }

    static Method findMappedMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(type, mcpName, parameterTypes);
        if (method == null) {
            method = findMethod(type, srgName, parameterTypes);
        }
        if (method != null) {
            method.setAccessible(true);
        }
        return method;
    }

    static ItemStack recipeCraftingResult(IRecipe recipe, InventoryCrafting inventory) {
        if (recipe == null || inventory == null) {
            return emptyStack();
        }
        ItemStack result = MinecraftMappingCompat.recipeCraftingResult(recipe, inventory);
        return result == null ? emptyStack() : result;
    }

    static ItemStack findMatchingResult(InventoryCrafting inventory, World world) {
        ItemStack result = MinecraftMappingCompat.findMatchingResult(inventory, world);
        return result == null ? emptyStack() : result;
    }

    static ItemStack inventoryStack(InventoryCrafting inventory, int slot) {
        ItemStack value = MinecraftMappingCompat.inventoryStackInSlot(inventory, slot);
        return value == null ? emptyStack() : value;
    }

    static String stackCacheKey(ItemStack stack) {
        if (isEmpty(stack)) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder(48);
        builder.append(itemId(stack)).append(':').append(meta(stack));
        if (MinecraftMappingCompat.itemStackHasTagCompound(stack)) {
            builder.append(':').append(MinecraftMappingCompat.itemStackTagCompound(stack));
        }
        return builder.toString();
    }

    static long stackKey(ItemStack stack) {
        return stackKey(itemId(stack), meta(stack));
    }

    static long stackKey(int itemId, int meta) {
        return ((long) itemId << 32) ^ (meta & 0xFFFFFFFFL);
    }

    static int itemId(ItemStack stack) {
        int id = MinecraftMappingCompat.itemIdFromItem(MinecraftMappingCompat.itemStackItem(stack));
        return id != 0 ? id : System.identityHashCode(stack);
    }

    static int meta(ItemStack stack) {
        return MinecraftMappingCompat.itemStackMetadata(stack);
    }

    static boolean isEmpty(ItemStack stack) {
        return MinecraftMappingCompat.itemStackIsEmpty(stack);
    }

    static ItemStack copy(ItemStack stack) {
        if (isEmpty(stack)) {
            return emptyStack();
        }
        ItemStack value = MinecraftMappingCompat.itemStackCopy(stack);
        return value == null ? emptyStack() : value;
    }

    static ItemStack emptyStack() {
        return MinecraftMappingCompat.emptyStack();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getDeclaredMethod(name, parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
