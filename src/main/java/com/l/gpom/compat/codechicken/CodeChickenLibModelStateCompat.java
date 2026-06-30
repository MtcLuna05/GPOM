package com.l.gpom.compat.codechicken;

import codechicken.lib.model.bakery.ModelErrorStateProperty;
import codechicken.lib.model.bakery.ModelErrorStateProperty.ErrorState;
import net.minecraftforge.common.property.IExtendedBlockState;

public final class CodeChickenLibModelStateCompat {
    private CodeChickenLibModelStateCompat() {
    }

    public static IExtendedBlockState repairNullModelErrorState(IExtendedBlockState state) {
        if (state == null) {
            return null;
        }
        try {
            if (!state.getUnlistedProperties().containsKey(ModelErrorStateProperty.ERROR_STATE)) {
                return state;
            }
            if (state.getValue(ModelErrorStateProperty.ERROR_STATE) != null) {
                return state;
            }
            return state.withProperty(ModelErrorStateProperty.ERROR_STATE, ErrorState.OK);
        } catch (Throwable ignored) {
            return state;
        }
    }
}
