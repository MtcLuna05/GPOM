package com.l.gpom.mixin.blockcraftery;

import com.l.gpom.compat.framed.FramedMaterialData;
import com.l.gpom.compat.framed.FramedMaterialDataAccess;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "epicsquid.blockcraftery.tile.TileEditableBlock", remap = false)
public abstract class MixinTileEditableBlockFramedMaterialData implements FramedMaterialDataAccess {
    @Unique
    private NBTTagCompound gpom$framedMaterialData;

    @Override
    @Unique
    public NBTTagCompound gpom$getFramedMaterialData() {
        return gpom$framedMaterialData;
    }

    @Override
    @Unique
    public void gpom$setFramedMaterialData(NBTTagCompound data) {
        gpom$framedMaterialData = data;
    }
    @Inject(method = "func_145839_a(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("RETURN"), remap = false, require = 0)
    private void gpom$readFramedMaterialData(NBTTagCompound compound, CallbackInfo ci) {
        Object self = this;
        if (self instanceof FramedMaterialDataAccess) {
            FramedMaterialData.read((FramedMaterialDataAccess) self, compound);
        }
    }
}
