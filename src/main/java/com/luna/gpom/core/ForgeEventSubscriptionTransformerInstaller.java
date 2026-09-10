package com.luna.gpom.core;

import com.luna.gpom.optimization.ForgeEventSubscriptionTransformerOptimizations;
import net.minecraft.launchwrapper.IClassTransformer;

public final class ForgeEventSubscriptionTransformerInstaller implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String className = transformedName != null ? transformedName : name;
        if (className != null && (className.startsWith("com.luna.gpom.") || className.startsWith("$wrapper.com.luna.gpom."))) {
            return basicClass;
        }
        ForgeEventSubscriptionTransformerOptimizations.install();
        return basicClass;
    }
}
