package com.l.gpom.compat.blockcraftery;

import net.minecraft.block.state.IBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

/** Carries the second material through Blockcraftery's extended baked-model state. */
public final class BlockcrafteryDoubleSlopeStateProperty implements IUnlistedProperty<IBlockState> {
    public static final BlockcrafteryDoubleSlopeStateProperty INSTANCE =
            new BlockcrafteryDoubleSlopeStateProperty();

    private BlockcrafteryDoubleSlopeStateProperty() {
    }

    @Override
    public String getName() {
        return "gpom_double_slope_secondary";
    }

    @Override
    public boolean isValid(IBlockState value) {
        return value != null;
    }

    @Override
    public Class<IBlockState> getType() {
        return IBlockState.class;
    }

    @Override
    public String valueToString(IBlockState value) {
        return String.valueOf(value);
    }
}
