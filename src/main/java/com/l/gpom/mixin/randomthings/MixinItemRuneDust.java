package com.l.gpom.mixin.randomthings;

import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "lumien.randomthings.item.ItemRuneDust", remap = false)
public abstract class MixinItemRuneDust {
    @Inject(method = "func_180614_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$placeCustomRuneDust(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                          float hitX, float hitY, float hitZ, CallbackInfoReturnable<EnumActionResult> cir) {
        EnumActionResult result = RandomThingsRuneCompat.onRuneDustUse(player, world, pos, hand, facing, hitX, hitY, hitZ);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
