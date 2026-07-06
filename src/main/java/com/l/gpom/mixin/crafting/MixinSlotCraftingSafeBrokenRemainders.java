package com.l.gpom.mixin.crafting;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.util.NonNullList;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(value = SlotCrafting.class, remap = false)
public abstract class MixinSlotCraftingSafeBrokenRemainders {
    private static final Set<String> gpom$loggedBrokenRemainders = ConcurrentHashMap.newKeySet();

    @Inject(
            method = {
                    "onTake(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;",
                    "func_190901_a(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$guardBrokenRecipeRemainders(EntityPlayer playerIn, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (!GpomEarlyConfig.safeBrokenRecipePreviewEnabled() || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        Object craftMatrixValue = MinecraftMappingCompat.fieldValue((SlotCrafting) (Object) this, "slotCrafting.craftMatrix", "field_75239_a", "craftMatrix");
        InventoryCrafting craftMatrix = craftMatrixValue instanceof InventoryCrafting ? (InventoryCrafting) craftMatrixValue : null;
        Object slotPlayerValue = MinecraftMappingCompat.fieldValue((SlotCrafting) (Object) this, "slotCrafting.player", "field_75238_b", "player");
        EntityPlayer slotPlayer = slotPlayerValue instanceof EntityPlayer ? (EntityPlayer) slotPlayerValue : null;
        if (craftMatrix == null || slotPlayer == null) {
            return;
        }
        cir.setReturnValue(gpom$safeOnTake(playerIn != null ? playerIn : slotPlayer, slotPlayer, stack, craftMatrix));
    }

    private ItemStack gpom$safeOnTake(EntityPlayer playerIn, EntityPlayer slotPlayer, ItemStack stack, InventoryCrafting craftMatrix) {
        MinecraftMappingCompat.invoke((SlotCrafting) (Object) this, "slotCrafting.onCrafting",
                new Class<?>[]{ItemStack.class}, new Object[]{stack},
                "func_75208_c", "onCrafting");
        NonNullList<ItemStack> remaining = null;
        try {
            ForgeHooks.setCraftingPlayer(playerIn);
            World world = MinecraftMappingCompat.playerWorld(playerIn);
            if (world == null) {
                return stack;
            }
            Object remainingValue = MinecraftMappingCompat.invokeStatic(CraftingManager.class, "crafting.getRemainingItems",
                    new Class<?>[]{InventoryCrafting.class, World.class}, new Object[]{craftMatrix, world},
                    "func_180303_b", "getRemainingItems");
            remaining = remainingValue instanceof NonNullList ? (NonNullList<ItemStack>) remainingValue : null;
        } catch (Throwable throwable) {
            gpom$logBrokenRemainder(throwable);
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }
        if (remaining == null) {
            ItemStack empty = MinecraftMappingCompat.emptyStack();
            if (empty == null) {
                return stack;
            }
            Object value = MinecraftMappingCompat.invokeStatic(NonNullList.class, "nonNullList.withSize",
                    new Class<?>[]{int.class, Object.class}, new Object[]{MinecraftMappingCompat.inventorySize(craftMatrix), empty},
                    "func_191197_a", "withSize");
            remaining = value instanceof NonNullList ? (NonNullList<ItemStack>) value : null;
            if (remaining == null) {
                return stack;
            }
        }

        for (int slot = 0; slot < remaining.size(); ++slot) {
            ItemStack input = MinecraftMappingCompat.inventoryStackInSlot(craftMatrix, slot);
            ItemStack remainder = remaining.get(slot);

            if (!MinecraftMappingCompat.itemStackIsEmpty(input)) {
                MinecraftMappingCompat.invoke(craftMatrix, "inventory.decrStackSize",
                        new Class<?>[]{int.class, int.class}, new Object[]{slot, 1},
                        "func_70298_a", "decrStackSize");
                input = MinecraftMappingCompat.inventoryStackInSlot(craftMatrix, slot);
            }

            if (!MinecraftMappingCompat.itemStackIsEmpty(remainder)) {
                if (MinecraftMappingCompat.itemStackIsEmpty(input)) {
                    MinecraftMappingCompat.inventorySetStack(craftMatrix, slot, remainder);
                } else if (MinecraftMappingCompat.itemStacksSameItemAndTags(input, remainder)) {
                    MinecraftMappingCompat.invoke(remainder, "itemStack.grow", new Class<?>[]{int.class},
                            new Object[]{MinecraftMappingCompat.itemStackCount(input)}, "func_190917_f", "grow");
                    MinecraftMappingCompat.inventorySetStack(craftMatrix, slot, remainder);
                } else if (!MinecraftMappingCompat.addToPlayerInventory(slotPlayer, remainder)) {
                    MinecraftMappingCompat.invoke(slotPlayer, "player.dropItem",
                            new Class<?>[]{ItemStack.class, boolean.class}, new Object[]{remainder, false},
                            "func_71019_a", "dropItem");
                }
            }
        }
        return stack;
    }

    private static void gpom$logBrokenRemainder(Throwable throwable) {
        String key = throwable.getClass().getName() + ":" + String.valueOf(throwable.getMessage());
        if (gpom$loggedBrokenRemainders.add(key)) {
            GPOM.LOGGER.warn("[GPOM Crafting Safety] Suppressed broken recipe remainder calculation; consumed one item from each occupied input slot", throwable);
        }
    }
}
