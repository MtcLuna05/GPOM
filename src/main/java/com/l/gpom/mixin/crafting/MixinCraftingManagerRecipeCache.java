package com.l.gpom.mixin.crafting;

import com.l.gpom.optimization.GeneralCraftingRecipeCache;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingManager.class, remap = false)
public abstract class MixinCraftingManagerRecipeCache {
    @Inject(method = "func_192413_b", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gpom$reuseLastMatchingRecipe(InventoryCrafting inventory, World world,
                                                     CallbackInfoReturnable<IRecipe> callback) {
        IRecipe recipe = GeneralCraftingRecipeCache.findStillMatching(inventory, world);
        if (recipe != null) {
            callback.setReturnValue(recipe);
        }
    }

    @Inject(method = "func_192413_b", at = @At("RETURN"), remap = false)
    private static void gpom$rememberMatchingRecipe(InventoryCrafting inventory, World world,
                                                    CallbackInfoReturnable<IRecipe> callback) {
        GeneralCraftingRecipeCache.record(inventory, callback.getReturnValue());
    }
}
