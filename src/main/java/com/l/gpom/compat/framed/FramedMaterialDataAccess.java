package com.l.gpom.compat.framed;

import net.minecraft.nbt.NBTTagCompound;

public interface FramedMaterialDataAccess {
    NBTTagCompound gpom$getFramedMaterialData();

    void gpom$setFramedMaterialData(NBTTagCompound data);
}
