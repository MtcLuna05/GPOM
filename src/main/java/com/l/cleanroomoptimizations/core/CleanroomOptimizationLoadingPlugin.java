package com.l.cleanroomoptimizations.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

@IFMLLoadingPlugin.Name("Cleanroom Optimizations Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class CleanroomOptimizationLoadingPlugin implements IFMLLoadingPlugin {
    static {
        MixinBootstrap.init();
        Mixins.addConfiguration("cleanroomoptimizations.mod.mixin.json");
    }

    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[] {
                "com.l.cleanroomoptimizations.core.HeiStartupProfilerTransformer",
                "com.l.cleanroomoptimizations.core.ModularMachineryStartupProfilerTransformer",
                "com.l.cleanroomoptimizations.core.ThaumcraftStartupProfilerTransformer",
                "com.l.cleanroomoptimizations.core.CitNbtResourceReloadTransformer",
                "com.l.cleanroomoptimizations.core.RenderLibCompatibilityTransformer",
                "com.l.cleanroomoptimizations.core.LibVulpesCompatibilityTransformer"
        };
    }

    @Override
    public @Nullable String getModContainerClass() {
        return null;
    }

    @Override
    public @Nullable String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }
}
