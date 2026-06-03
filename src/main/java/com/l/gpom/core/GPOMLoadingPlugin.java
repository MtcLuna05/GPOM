package com.l.gpom.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

@IFMLLoadingPlugin.Name("General Purpose Optimization Mod Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class GPOMLoadingPlugin implements IFMLLoadingPlugin {
    static {
        MixinBootstrap.init();
        Mixins.addConfiguration("gpom.mod.mixin.json");
    }

    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[] {
                "com.l.gpom.core.HeiStartupProfilerTransformer",
                "com.l.gpom.core.ModularMachineryStartupProfilerTransformer",
                "com.l.gpom.core.ThaumcraftStartupProfilerTransformer",
                "com.l.gpom.core.BetweenlandsStartupProfilerTransformer",
                "com.l.gpom.core.RailcraftStartupProfilerTransformer",
                "com.l.gpom.core.TechRebornStartupProfilerTransformer",
                "com.l.gpom.core.InitPhaseDeepProfilerTransformer",
                "com.l.gpom.core.ForcedResourceReloadTransformer",
                "com.l.gpom.core.RenderLibCompatibilityTransformer",
                "com.l.gpom.core.LibVulpesCompatibilityTransformer",
                "com.l.gpom.core.LogSpamTransformer"
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
