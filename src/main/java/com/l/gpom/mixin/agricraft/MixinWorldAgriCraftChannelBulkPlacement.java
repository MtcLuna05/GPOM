package com.l.gpom.mixin.agricraft;

import com.l.gpom.compat.agricraft.AgriCraftChannelConnectionCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = World.class, remap = false)
public abstract class MixinWorldAgriCraftChannelBulkPlacement {
    @Inject(
            method = {
                    "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
                    "func_180501_a(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$refreshAgricraftChannelConnections(BlockPos pos, IBlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        AgriCraftChannelConnectionCompat.onSetBlockState((World) (Object) this, pos, state, cir.getReturnValueZ());
    }
}
