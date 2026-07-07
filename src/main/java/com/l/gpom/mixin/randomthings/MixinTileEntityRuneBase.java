package com.l.gpom.mixin.randomthings;

import com.l.gpom.compat.randomthings.GpomRuneDataAccess;
import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "lumien.randomthings.tileentity.TileEntityRuneBase", remap = false)
public abstract class MixinTileEntityRuneBase implements GpomRuneDataAccess {
    @Shadow
    int[][] runeData;

    @Unique
    private int[] gpom$runeDisconnectedEdges = new int[16];

    @Unique
    private boolean gpom$runeConnectionMetadata;

    @Unique
    private EnumFacing gpom$runeFace = EnumFacing.UP;

    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void gpom$initializeCustomRuneGrid(CallbackInfo ci) {
        RandomThingsRuneCompat.onConstruct(this);
    }

    @Inject(method = "readDataFromNBT", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$readCustomRuneData(NBTTagCompound compound, boolean sync, CallbackInfo ci) {
        if (RandomThingsRuneCompat.readRuneData(this, compound, sync)) {
            ci.cancel();
        }
    }

    @Inject(method = "writeDataToNBT", at = @At("TAIL"), require = 0)
    private void gpom$writeCustomRuneData(NBTTagCompound compound, boolean sync, CallbackInfo ci) {
        RandomThingsRuneCompat.writeRuneData(this, compound, sync);
    }

    @Override
    public int[][] gpom$getRuneDataRaw() {
        return this.runeData;
    }

    @Override
    public void gpom$setRuneDataRaw(int[][] data) {
        this.runeData = data;
    }

    @Override
    public int[] gpom$getRuneDisconnectedEdges() {
        return this.gpom$runeDisconnectedEdges;
    }

    @Override
    public void gpom$setRuneDisconnectedEdges(int[] disconnectedEdges) {
        this.gpom$runeDisconnectedEdges = disconnectedEdges == null ? new int[16] : disconnectedEdges;
    }

    @Override
    public boolean gpom$hasRuneConnectionMetadata() {
        return this.gpom$runeConnectionMetadata;
    }

    @Override
    public void gpom$setRuneConnectionMetadata(boolean hasMetadata) {
        this.gpom$runeConnectionMetadata = hasMetadata;
    }

    @Override
    public EnumFacing gpom$getRuneFace() {
        return this.gpom$runeFace == null ? EnumFacing.UP : this.gpom$runeFace;
    }

    @Override
    public void gpom$setRuneFace(EnumFacing face) {
        this.gpom$runeFace = face == null ? EnumFacing.UP : face;
    }
}
