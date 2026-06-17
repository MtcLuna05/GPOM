package com.l.gpom.compat.hei;

import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

final class DisabledRecipeTransferHandler implements IRecipeTransferHandler<Container> {
    private final Class<? extends Container> containerClass;
    private final IRecipeTransferHandlerHelper helper;
    private final String message;

    DisabledRecipeTransferHandler(Class<? extends Container> containerClass,
                                  IRecipeTransferHandlerHelper helper,
                                  String message) {
        this.containerClass = containerClass;
        this.helper = helper;
        this.message = message;
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
        return helper.createUserErrorWithTooltip(message);
    }
}
