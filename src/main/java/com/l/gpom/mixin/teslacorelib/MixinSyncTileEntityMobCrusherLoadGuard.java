package com.l.gpom.mixin.teslacorelib;

import com.l.gpom.compat.industrialforegoing.IndustrialForegoingMobCrusherLoadGuard;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.ndrei.teslacorelib.tileentities.SyncTileEntity", remap = false)
public abstract class MixinSyncTileEntityMobCrusherLoadGuard {
    @Inject(method = "func_145839_a(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"), require = 0)
    private void gpom$beginMobCrusherRead(NBTTagCompound compound, CallbackInfo ci) {
        IndustrialForegoingMobCrusherLoadGuard.beginRead(this);
    }

    @Inject(method = "func_145839_a(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"), require = 0)
    private void gpom$endMobCrusherRead(NBTTagCompound compound, CallbackInfo ci) {
        IndustrialForegoingMobCrusherLoadGuard.endRead(this);
    }

    @Inject(method = "partialSync(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$suppressAddonPartialSyncDuringMobCrusherRead(String key, boolean markDirty, CallbackInfo ci) {
        if (IndustrialForegoingMobCrusherLoadGuard.suppressPartialSync(this, key)) {
            ci.cancel();
        }
    }
}
