package com.l.gpom.compat.hei;

import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ExtendedCraftingTableTransferInfo implements IRecipeTransferInfo<Container> {
    private final Class<? extends Container> containerClass;
    private final String category;
    private final int tableWidth;
    private final int recipeWidth;

    ExtendedCraftingTableTransferInfo(Class<? extends Container> containerClass,
                                      String category,
                                      int tableWidth,
                                      int recipeWidth) {
        this.containerClass = containerClass;
        this.category = category;
        this.tableWidth = tableWidth;
        this.recipeWidth = recipeWidth;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Class<Container> getContainerClass() {
        return (Class<Container>) containerClass;
    }

    @Override
    public String getRecipeCategoryUid() {
        return category;
    }

    @Override
    public boolean canHandle(Container container) {
        return container != null && containerClass.isInstance(container);
    }

    @Override
    public List<Slot> getRecipeSlots(Container container) {
        if (!canHandle(container) || recipeWidth <= 0 || tableWidth < recipeWidth) {
            return Collections.emptyList();
        }

        List<Slot> slots = HeiReflection.containerSlots(container);
        int offset = (tableWidth - recipeWidth) / 2;
        List<Slot> recipeSlots = new ArrayList<>(recipeWidth * recipeWidth);
        for (int row = 0; row < recipeWidth; row++) {
            for (int column = 0; column < recipeWidth; column++) {
                int slotIndex = 1 + ((row + offset) * tableWidth) + column + offset;
                if (slotIndex < 0 || slotIndex >= slots.size()) {
                    return Collections.emptyList();
                }
                recipeSlots.add(slots.get(slotIndex));
            }
        }
        return recipeSlots;
    }

    @Override
    public List<Slot> getInventorySlots(Container container) {
        if (!canHandle(container)) {
            return Collections.emptyList();
        }

        List<Slot> slots = HeiReflection.containerSlots(container);
        int inventoryStart = 1 + (tableWidth * tableWidth);
        int inventoryEnd = Math.min(inventoryStart + 36, slots.size());
        if (inventoryStart < 0 || inventoryStart >= inventoryEnd) {
            return Collections.emptyList();
        }
        return new ArrayList<>(slots.subList(inventoryStart, inventoryEnd));
    }
}
