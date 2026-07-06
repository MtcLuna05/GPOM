package com.l.gpom.mixin.baubles;

import com.l.gpom.compat.baubles.AetherSideSlotsBridge;
import com.l.gpom.compat.baubles.BaublesSideSlotsVanillaBridge;
import com.l.gpom.compat.baubles.CosmeticArmorSideSlotsBridge;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerPlayer.class)
public abstract class MixinContainerPlayerSideSlots extends Container {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void gpom$appendBaublesSideSlots(InventoryPlayer playerInventory, boolean localWorld, EntityPlayer player, CallbackInfo ci) {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled() || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        // Keep the base player container slot count symmetric on client and server.
        // GuiInventory only moves these hidden slots into the rail while the panel is open.
        if (((Object) this).getClass() != ContainerPlayer.class) {
            return;
        }
        if (player instanceof FakePlayer) {
            return;
        }

        BaublesSideSlotsVanillaBridge.prepare((Container) (Object) this, player);
        AetherSideSlotsBridge.prepare((Container) (Object) this, player);
        CosmeticArmorSideSlotsBridge.prepare((Container) (Object) this, player);
    }
}
