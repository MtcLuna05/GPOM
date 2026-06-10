package com.l.gpom.mixin.baubles;

import com.l.gpom.compat.baubles.BaublesSideSlotsVanillaBridge;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerPlayer.class)
public abstract class MixinContainerPlayerSideSlots extends Container {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void gpom$appendBaublesSideSlots(InventoryPlayer playerInventory, boolean localWorld, EntityPlayer player, CallbackInfo ci) {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled()) {
            return;
        }

        BaublesSideSlotsVanillaBridge.prepare((Container) (Object) this, player);
    }
}
