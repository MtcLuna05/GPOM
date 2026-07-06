package com.l.gpom.mixin.randomthings;

import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "lumien.randomthings.handler.RTEventHandler", remap = false)
public abstract class MixinRTEventHandlerRunePattern {
    @Inject(method = "playerInteract", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$copyCustomRunePattern(PlayerInteractEvent event, CallbackInfo ci) {
        if (!(event instanceof PlayerInteractEvent.RightClickBlock)) {
            return;
        }
        PlayerInteractEvent.RightClickBlock rightClick = (PlayerInteractEvent.RightClickBlock) event;
        boolean handled = RandomThingsRuneCompat.copyRunePattern(new RandomThingsRuneCompat.PlayerInteractEventAccess() {
            @Override
            public World gpom$getWorld() {
                return rightClick.getWorld();
            }

            @Override
            public BlockPos gpom$getPos() {
                return rightClick.getPos();
            }

            @Override
            public ItemStack gpom$getHeldItem() {
                return rightClick.getItemStack();
            }

            @Override
            public EntityPlayer gpom$getEntityPlayer() {
                return rightClick.getEntityPlayer();
            }

            @Override
            public EnumHand gpom$getHand() {
                return rightClick.getHand();
            }

            @Override
            public void gpom$allowUseItem() {
                rightClick.setUseItem(Event.Result.ALLOW);
            }
        });
        if (handled) {
            ci.cancel();
        }
    }
}
