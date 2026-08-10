package com.l.gpom.compat.blockcraftery;

import net.minecraft.nbt.NBTTagCompound;

/** Runtime access added to Blockcraftery's TileEditableBlock by GPOM's compatibility transformer. */
public interface BlockcrafteryDoubleSlopeAccess {
    NBTTagCompound gpom$getDoubleSlopeData();

    void gpom$setDoubleSlopeData(NBTTagCompound data);
}
