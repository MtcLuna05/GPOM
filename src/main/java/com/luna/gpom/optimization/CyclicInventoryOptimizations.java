package com.luna.gpom.optimization;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Allocation-light equivalent of Cyclic's legacy IInventory transfer loop. */
public final class CyclicInventoryOptimizations {
    private CyclicInventoryOptimizations() {
    }

    public static ArrayList<ItemStack> dumpToIInventory(List<ItemStack> stacks,
                                                         IInventory inventory,
                                                         int startingSlot,
                                                         int maxSlot) {
        ArrayList<ItemStack> remaining = new ArrayList<>(stacks.size());
        int stackCount = stacks.size();
        for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
            ItemStack current = stacks.get(stackIndex);
            if (current.isEmpty()) {
                continue;
            }
            Item currentItem = current.getItem();
            int currentDamage = current.getItemDamage();
            for (int slot = startingSlot; slot < maxSlot; slot++) {
                if (current.isEmpty()) {
                    break;
                }
                ItemStack chestStack = inventory.getStackInSlot(slot);
                if (chestStack.isEmpty()) {
                    inventory.setInventorySlotContents(slot, current);
                    current = ItemStack.EMPTY;
                    break;
                }
                Item chestItem = chestStack.getItem();
                if ((chestItem == currentItem || chestItem.equals(currentItem))
                        && chestStack.getItemDamage() == currentDamage
                        && ItemStack.areItemStackTagsEqual(chestStack, current)) {
                    int space = chestStack.getMaxStackSize() - chestStack.getCount();
                    int toDeposit = Math.min(space, current.getCount());
                    if (toDeposit > 0) {
                        current.shrink(toDeposit);
                        chestStack.grow(toDeposit);
                        if (current.getCount() == 0) {
                            current = ItemStack.EMPTY;
                        }
                    }
                }
            }
            if (!current.isEmpty()) {
                remaining.add(current);
            }
        }
        return remaining;
    }
}
