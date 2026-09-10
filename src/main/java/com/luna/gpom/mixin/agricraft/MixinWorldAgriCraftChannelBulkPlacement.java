package com.luna.gpom.mixin.agricraft;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.luna.gpom.compat.agricraft.AgriCraftChannelConnectionCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = World.class, remap = false)
public abstract class MixinWorldAgriCraftChannelBulkPlacement {
    @ModifyReturnValue(
            method = {
                    "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
                    "func_180501_a(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z"
            },
            at = @At("RETURN"),
            require = 0
    )
    private boolean gpom$refreshAgricraftChannelConnections(boolean changed, BlockPos pos, IBlockState state, int flags) {
        AgriCraftChannelConnectionCompat.onSetBlockState((World) (Object) this, pos, state, changed);
        return changed;
    }
}
