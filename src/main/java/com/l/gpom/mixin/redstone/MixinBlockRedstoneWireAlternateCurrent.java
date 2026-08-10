package com.l.gpom.mixin.redstone;

import com.l.gpom.optimization.redstone.AlternateCurrentWireEngine;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BlockRedstoneWire.class, remap = false)
public abstract class MixinBlockRedstoneWireAlternateCurrent {
    @Inject(method = "func_176338_e", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void gpom$replaceWirePowerUpdate(World world, BlockPos position, IBlockState state,
                                             CallbackInfoReturnable<IBlockState> callback) {
        if (AlternateCurrentWireEngine.shouldReplaceVanilla(world)) {
            callback.setReturnValue(state);
        }
    }

    @Inject(method = "func_176213_c",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockRedstoneWire;func_176338_e"
                            + "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/state/IBlockState;"),
            remap = false,
            require = 1)
    private void gpom$onWireAdded(World world, BlockPos position, IBlockState state, CallbackInfo callback) {
        AlternateCurrentWireEngine.onWireAdded(world, position);
    }

    @Inject(method = "func_180663_b",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockRedstoneWire;func_176338_e"
                            + "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;"
                            + "Lnet/minecraft/block/state/IBlockState;)Lnet/minecraft/block/state/IBlockState;"),
            remap = false,
            require = 1)
    private void gpom$onWireRemoved(World world, BlockPos position, IBlockState state, CallbackInfo callback) {
        AlternateCurrentWireEngine.onWireRemoved(world, position);
    }

    @Inject(method = "func_189540_a", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void gpom$onWireNeighborChanged(IBlockState state, World world, BlockPos position,
                                            Block neighborBlock, BlockPos neighborPosition,
                                            CallbackInfo callback) {
        if (AlternateCurrentWireEngine.onWireNeighborChanged(world, position)) {
            callback.cancel();
        }
    }
}
