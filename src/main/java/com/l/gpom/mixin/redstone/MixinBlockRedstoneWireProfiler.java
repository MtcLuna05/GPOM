package com.l.gpom.mixin.redstone;

import com.l.gpom.optimization.RedstoneWireUpdateProfiler;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRedstoneWire.class, remap = false)
public abstract class MixinBlockRedstoneWireProfiler {
    @Inject(method = "func_176338_e", at = @At("HEAD"), remap = false)
    private void gpom$beginWireUpdate(World world, BlockPos position, IBlockState state,
                                      CallbackInfoReturnable<IBlockState> callback) {
        RedstoneWireUpdateProfiler.enter();
    }

    @Inject(method = "func_176338_e", at = @At("RETURN"), remap = false)
    private void gpom$finishWireUpdate(World world, BlockPos position, IBlockState state,
                                       CallbackInfoReturnable<IBlockState> callback) {
        RedstoneWireUpdateProfiler.exit();
    }
}
