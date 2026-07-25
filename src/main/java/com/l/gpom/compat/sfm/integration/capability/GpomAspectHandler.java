package com.l.gpom.compat.sfm.integration.capability;

import com.l.gpom.compat.sfm.integration.resource.ThaumcraftAspectResourceType;

public interface GpomAspectHandler {
    int getSlots();

    ThaumcraftAspectResourceType.AspectStack getStackInSlot(int slot);

    ThaumcraftAspectResourceType.AspectStack extract(int slot, long amount, boolean simulate);

    ThaumcraftAspectResourceType.AspectStack insert(int slot, ThaumcraftAspectResourceType.AspectStack stack, boolean simulate);

    long getMaxAmount(int slot);
}
