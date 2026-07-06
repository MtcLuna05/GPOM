package com.l.gpom.compat.hei;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class DraconicFusionTransferHandler implements IRecipeTransferHandler<Container> {
    private static final String FUSION_CONTAINER = "com.brandon3055.draconicevolution.inventory.ContainerFusionCraftingCore";
    private final IRecipeTransferHandlerHelper helper;
    private final Class<? extends Container> containerClass;

    DraconicFusionTransferHandler(IRecipeTransferHandlerHelper helper, Class<? extends Container> containerClass) {
        this.helper = helper;
        this.containerClass = containerClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Container> getContainerClass() {
        return (Class<Container>) containerClass;
    }

    @Override
    public IRecipeTransferError transferRecipe(Container container,
                                               IRecipeLayout recipeLayout,
                                               EntityPlayer player,
                                               boolean maxTransfer,
                                               boolean doTransfer) {
        if (container == null || !FUSION_CONTAINER.equals(container.getClass().getName())) {
            return helper.createInternalError();
        }

        List<List<ItemStack>> inputs = collectInputs(recipeLayout);
        if (inputs.size() < 2) {
            return helper.createUserErrorWithTooltip("No transferable Draconic Fusion recipe inputs found.");
        }

        if (doTransfer && !HeiQuickCraftNetwork.sendDraconicFusionTransfer(HeiReflection.containerWindowId(container), inputs)) {
            return helper.createInternalError();
        }
        return null;
    }

    private static List<List<ItemStack>> collectInputs(IRecipeLayout recipeLayout) {
        if (recipeLayout == null || recipeLayout.getItemStacks() == null) {
            return Collections.emptyList();
        }

        Map<Integer, ? extends IGuiIngredient<ItemStack>> guiIngredients =
                recipeLayout.getItemStacks().getGuiIngredients();
        if (guiIngredients == null || guiIngredients.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>>> entries =
                new ArrayList<>(guiIngredients.entrySet());
        entries.sort(Comparator.comparingInt(Map.Entry::getKey));

        List<List<ItemStack>> inputs = new ArrayList<>();
        for (Map.Entry<Integer, ? extends IGuiIngredient<ItemStack>> entry : entries) {
            IGuiIngredient<ItemStack> ingredient = entry.getValue();
            if (ingredient == null || !ingredient.isInput()) {
                continue;
            }

            List<ItemStack> options = normaliseOptions(ingredient);
            if (!options.isEmpty()) {
                inputs.add(options);
            }
        }
        return inputs;
    }

    private static List<ItemStack> normaliseOptions(IGuiIngredient<ItemStack> ingredient) {
        List<ItemStack> raw = ingredient.getAllIngredients();
        if ((raw == null || raw.isEmpty()) && ingredient.getDisplayedIngredient() != null) {
            raw = Collections.singletonList(ingredient.getDisplayedIngredient());
        }
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> options = new ArrayList<>(raw.size());
        for (ItemStack stack : raw) {
            if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
                continue;
            }
            ItemStack copy = MinecraftMappingCompat.itemStackCopy(stack);
            MinecraftMappingCompat.itemStackSetCount(copy, Math.max(1, MinecraftMappingCompat.itemStackCount(copy)));
            options.add(copy);
        }
        return options;
    }
}
