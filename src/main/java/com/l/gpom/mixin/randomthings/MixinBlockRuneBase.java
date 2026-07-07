package com.l.gpom.mixin.randomthings;

import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "lumien.randomthings.block.BlockRuneBase", remap = false)
public abstract class MixinBlockRuneBase {
    @Inject(method = "func_189540_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$keepRunesAttachedToConfiguredFace(IBlockState state, World world, BlockPos pos, Block blockIn, BlockPos fromPos,
                                                        CallbackInfo ci) {
        if (RandomThingsRuneCompat.handleNeighborChange(world, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "func_180639_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$openRuneBlockSettings(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> cir) {
        if (MinecraftMappingCompat.playerIsSneaking(player)) {
            int rune = RandomThingsRuneCompat.runeAt(world, pos, hitX, hitZ, 0);
            if (MinecraftMappingCompat.worldIsRemote(world)) {
                cir.setReturnValue(RandomThingsRuneCompat.openSettingsScreen(player, rune));
            } else {
                cir.setReturnValue(true);
            }
            return;
        }
        EnumActionResult result = RandomThingsRuneCompat.toggleConnectionWithEmptyHand(player, world, pos, hand, facing, hitX, hitY, hitZ);
        if (result != null) {
            cir.setReturnValue(result == EnumActionResult.SUCCESS);
        }
    }

    @Inject(method = "removedByPlayer", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$breakCustomRunePiece(IBlockState state, World world, BlockPos pos, EntityPlayer player, boolean willHarvest,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!MinecraftMappingCompat.playerIsCreative(player)) {
            return;
        }
        Boolean result = RandomThingsRuneCompat.breakSingleRunePiece(world, pos, player);
        if (result != null && result) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "func_180649_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$clickCustomRunePiece(World world, BlockPos pos, EntityPlayer player, CallbackInfo ci) {
        Boolean result = RandomThingsRuneCompat.breakSingleRunePiece(world, pos, player);
        if (result != null && result) {
            ci.cancel();
        }
    }

    @Inject(method = "func_180663_b", at = @At("HEAD"), require = 0)
    private void gpom$dropCustomRuneGrid(World world, BlockPos pos, IBlockState state, CallbackInfo ci) {
        RandomThingsRuneCompat.dropCustomRunesBeforeStockBreak(world, pos);
    }

    @Inject(method = "getExtendedState", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$getCustomExtendedRuneState(IBlockState state, IBlockAccess world, BlockPos pos,
                                                 CallbackInfoReturnable<IBlockState> cir) {
        IBlockState custom = RandomThingsRuneCompat.getExtendedRuneState(state, world, pos);
        if (custom != state) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = "func_185496_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$getFaceAwareRuneBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos,
                                                  CallbackInfoReturnable<AxisAlignedBB> cir) {
        cir.setReturnValue(RandomThingsRuneCompat.boundingBox(world, pos));
    }

    @Inject(method = "func_180640_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$getFaceAwareRuneSelectedBoundingBox(IBlockState state, World world, BlockPos pos,
                                                          CallbackInfoReturnable<AxisAlignedBB> cir) {
        cir.setReturnValue(RandomThingsRuneCompat.selectedBoundingBox(world, pos));
    }
}
