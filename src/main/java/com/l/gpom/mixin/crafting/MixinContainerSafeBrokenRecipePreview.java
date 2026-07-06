package com.l.gpom.mixin.crafting;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = Container.class, remap = false)
public abstract class MixinContainerSafeBrokenRecipePreview {
    private static final Set<String> gpom$loggedBrokenPreviews = ConcurrentHashMap.newKeySet();

    @Inject(
            method = {
                    "slotChangedCraftingGrid(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/inventory/InventoryCraftResult;)V",
                    "func_192389_a(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/inventory/InventoryCraftResult;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$guardBrokenRecipePreview(World world, EntityPlayer player, InventoryCrafting crafting, InventoryCraftResult result, CallbackInfo ci) {
        if (!GpomEarlyConfig.safeBrokenRecipePreviewEnabled() || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        if (world == null || MinecraftMappingCompat.worldIsRemote(world) || crafting == null || result == null) {
            return;
        }

        ItemStack empty = MinecraftMappingCompat.emptyStack();
        if (empty == null) {
            return;
        }

        ci.cancel();

        ItemStack output = empty;
        IRecipe recipe = null;
        try {
            Object matchedRecipe = MinecraftMappingCompat.invokeStatic(CraftingManager.class, "crafting.findMatchingRecipe",
                    new Class<?>[]{InventoryCrafting.class, World.class}, new Object[]{crafting, world},
                    "func_192413_b", "findMatchingRecipe");
            recipe = matchedRecipe instanceof IRecipe ? (IRecipe) matchedRecipe : null;
            if (recipe != null && gpom$canUseRecipe(world, player, recipe)) {
                MinecraftMappingCompat.invoke(result, "craftResult.setRecipeUsed", new Class<?>[]{IRecipe.class}, new Object[]{recipe},
                        "func_193056_a", "setRecipeUsed");
                output = MinecraftMappingCompat.recipeCraftingResult(recipe, crafting);
                if (output == null) {
                    output = empty;
                }
            }
        } catch (Throwable throwable) {
            MinecraftMappingCompat.invoke(result, "craftResult.setRecipeUsed", new Class<?>[]{IRecipe.class}, new Object[]{null},
                    "func_193056_a", "setRecipeUsed");
            output = empty;
            gpom$logBrokenRecipe("preview", recipe, throwable);
        }

        MinecraftMappingCompat.inventorySetStack(result, 0, output);
        Object windowIdValue = MinecraftMappingCompat.fieldValue((Container) (Object) this, "container.windowId", "field_75152_c", "windowId");
        Integer windowId = windowIdValue instanceof Number ? ((Number) windowIdValue).intValue() : null;
        if (windowId != null) {
            MinecraftMappingCompat.sendCraftingResult(player, windowId, output);
        }
    }

    private static boolean gpom$canUseRecipe(World world, EntityPlayer player, IRecipe recipe) {
        Object dynamic = MinecraftMappingCompat.invoke(recipe, "recipe.isDynamic", MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_192399_d", "isDynamic");
        Object rules = MinecraftMappingCompat.invoke(world, "world.getGameRules", MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_82736_K", "getGameRules");
        boolean limitedCraftingDisabled = true;
        if (rules instanceof GameRules) {
            Object limited = MinecraftMappingCompat.invoke(rules, "gameRules.getBoolean", new Class<?>[]{String.class}, new Object[]{"doLimitedCrafting"},
                    "func_82766_b", "getBoolean");
            limitedCraftingDisabled = !(limited instanceof Boolean) || !((Boolean) limited);
        }
        if (Boolean.TRUE.equals(dynamic) || limitedCraftingDisabled) {
            return true;
        }
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        Object recipeBook = MinecraftMappingCompat.invoke(player, "player.getRecipeBook", MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_192037_E", "getRecipeBook");
        if (recipeBook == null) {
            return false;
        }
        Object unlocked = MinecraftMappingCompat.invoke(recipeBook, "recipeBook.isUnlocked", new Class<?>[]{IRecipe.class}, new Object[]{recipe},
                "func_194076_e", "isUnlocked");
        return Boolean.TRUE.equals(unlocked);
    }

    private static void gpom$logBrokenRecipe(String phase, IRecipe recipe, Throwable throwable) {
        String key = phase + ":" + gpom$recipeName(recipe) + ":" + throwable.getClass().getName();
        if (gpom$loggedBrokenPreviews.add(key)) {
            GPOM.LOGGER.warn("[GPOM Crafting Safety] Suppressed broken recipe {} during {}; output slot cleared", gpom$recipeName(recipe), phase, throwable);
        }
    }

    private static String gpom$recipeName(IRecipe recipe) {
        if (recipe == null) {
            return "<matching recipe>";
        }
        Object registryName = MinecraftMappingCompat.invoke(recipe, "recipe.getRegistryName", MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "getRegistryName");
        ResourceLocation name = registryName instanceof ResourceLocation ? (ResourceLocation) registryName : null;
        return name == null ? recipe.getClass().getName() : name.toString();
    }
}
