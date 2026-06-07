package com.l.gpom.core;

import com.l.gpom.optimization.ForgeEventSubscriptionTransformerOptimizations;
import net.minecraft.launchwrapper.IClassTransformer;

public final class ForgeEventSubscriptionTransformerInstaller implements IClassTransformer {
    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        String className = transformedName != null ? transformedName : name;
        if (className != null && (className.startsWith("com.l.gpom.") || className.startsWith("$wrapper.com.l.gpom."))) {
            return basicClass;
        }
        ForgeEventSubscriptionTransformerOptimizations.install();
        return basicClass;
    }
}
