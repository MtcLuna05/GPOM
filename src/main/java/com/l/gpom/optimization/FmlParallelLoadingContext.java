package com.l.gpom.optimization;

import net.minecraftforge.fml.common.ModContainer;

public final class FmlParallelLoadingContext {
    private static final ThreadLocal<ModContainer> ACTIVE_CONTAINER = new ThreadLocal<>();

    private FmlParallelLoadingContext() {
    }

    public static ModContainer getActiveContainer() {
        return ACTIVE_CONTAINER.get();
    }

    static void setActiveContainer(ModContainer container) {
        ACTIVE_CONTAINER.set(container);
    }

    static void clearActiveContainer() {
        ACTIVE_CONTAINER.remove();
    }
}
