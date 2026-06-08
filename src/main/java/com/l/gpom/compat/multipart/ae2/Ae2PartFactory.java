package com.l.gpom.compat.multipart.ae2;

import codechicken.multipart.TMultiPart;
import codechicken.multipart.api.IPartFactory;
import net.minecraft.util.ResourceLocation;

public final class Ae2PartFactory implements IPartFactory {
    @Override
    public TMultiPart createPart(ResourceLocation identifier, boolean client) {
        if (!Ae2CableBusMultipart.TYPE.equals(identifier)) {
            return null;
        }
        return new Ae2CableBusMultipart();
    }
}
