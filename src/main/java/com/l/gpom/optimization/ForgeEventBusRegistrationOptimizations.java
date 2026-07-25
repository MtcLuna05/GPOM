package com.l.gpom.optimization;

import java.lang.reflect.Method;

/** Exact Forge 1.12 registration hook installed by ForgeEventBusRegistrationTransformer. */
public final class ForgeEventBusRegistrationOptimizations {
    private ForgeEventBusRegistrationOptimizations() {
    }

    @SuppressWarnings("unused")
    public static Method skipLegacyForceClassLoadingProbe(Class<?> owner, String name, Class<?>[] parameterTypes) {
        // Forge ignores this result. The original lookup always targets a synthetic absent method.
        return null;
    }
}
