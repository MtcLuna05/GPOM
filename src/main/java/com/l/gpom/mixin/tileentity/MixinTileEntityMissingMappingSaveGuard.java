package com.l.gpom.mixin.tileentity;

import com.l.gpom.compat.tileentity.TileEntityMissingMappingSaveGuard;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TileEntity.class, remap = false)
public abstract class MixinTileEntityMissingMappingSaveGuard {
    @Inject(
            method = {
                    "writeInternal(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;",
                    "func_189516_d(Lnet/minecraft/nbt/NBTTagCompound;)Lnet/minecraft/nbt/NBTTagCompound;"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$writeKnownFallbackIdForMissingMapping(NBTTagCompound compound, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (TileEntityMissingMappingSaveGuard.writeFallbackIdIfNeeded((TileEntity) (Object) this, compound)) {
            cir.setReturnValue(compound);
        }
    }
}
