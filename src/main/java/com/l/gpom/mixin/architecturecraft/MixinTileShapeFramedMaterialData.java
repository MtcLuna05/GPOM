package com.l.gpom.mixin.architecturecraft;

import com.l.gpom.compat.framed.FramedMaterialData;
import com.l.gpom.compat.framed.FramedMaterialDataAccess;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.elytradev.architecture.common.tile.TileShape", remap = false)
public abstract class MixinTileShapeFramedMaterialData implements FramedMaterialDataAccess {
    @Shadow
    private IBlockState baseBlockState;

    @Shadow
    private IBlockState secondaryBlockState;

    @Unique
    private NBTTagCompound gpom$framedMaterialData;

    @Inject(method = "func_145839_a(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"), require = 0)
    private void gpom$readFramedMaterialData(NBTTagCompound compound, CallbackInfo ci) {
        FramedMaterialData.read(this, compound);
    }

    @Inject(method = "readFromItemStackNBT", at = @At("RETURN"), require = 0)
    private void gpom$readFramedMaterialDataFromItem(NBTTagCompound compound, CallbackInfo ci) {
        FramedMaterialData.read(this, compound);
    }

    @Inject(method = "func_189515_b(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;", at = @At("RETURN"), require = 0)
    private void gpom$writeFramedMaterialData(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        FramedMaterialData.writeArchitectureCraft(this, cir.getReturnValue(), this.baseBlockState, this.secondaryBlockState);
    }

    @Inject(method = "writeToItemStackNBT", at = @At("TAIL"), require = 0)
    private void gpom$writeFramedMaterialDataToItem(NBTTagCompound compound, CallbackInfo ci) {
        FramedMaterialData.writeArchitectureCraft(this, compound, this.baseBlockState, this.secondaryBlockState);
    }

    @Override
    public NBTTagCompound gpom$getFramedMaterialData() {
        return this.gpom$framedMaterialData;
    }

    @Override
    public void gpom$setFramedMaterialData(NBTTagCompound data) {
        this.gpom$framedMaterialData = data;
    }
}
