package com.l.gpom.optimization;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import java.util.Map;
import java.util.WeakHashMap;

public final class GeneralCraftingRecipeCache {
    private static final Map<InventoryCrafting, IRecipe> LAST_MATCH = new WeakHashMap<>();

    private GeneralCraftingRecipeCache() {
    }

    public static IRecipe findStillMatching(InventoryCrafting inventory, World world) {
        IRecipe recipe;
        synchronized (LAST_MATCH) {
            recipe = LAST_MATCH.get(inventory);
        }
        return recipe != null && MinecraftMappingCompat.recipeMatches(recipe, inventory, world) ? recipe : null;
    }

    public static void record(InventoryCrafting inventory, IRecipe recipe) {
        synchronized (LAST_MATCH) {
            if (recipe == null) {
                LAST_MATCH.remove(inventory);
            } else {
                LAST_MATCH.put(inventory, recipe);
            }
        }
    }
}
