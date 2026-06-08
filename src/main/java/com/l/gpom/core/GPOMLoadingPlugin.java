package com.l.gpom.core;

import com.l.gpom.client.EarlySplashWindow;
import com.l.gpom.optimization.ForgeEventSubscriptionTransformerOptimizations;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

@IFMLLoadingPlugin.Name("General Purpose Optimization Mod Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class GPOMLoadingPlugin implements IFMLLoadingPlugin {
    static {
        if (System.getProperty("gpom.bootStartNanos") == null) {
            System.setProperty("gpom.bootStartNanos", Long.toString(System.nanoTime()));
        }
        EarlySplashWindow.startIfEnabled();
        EarlySplashWindow.setBootProgress("GPOM core plugin", 1, 4);
        MixinBootstrap.init();
        EarlySplashWindow.setBootProgress("Mixin bootstrap", 2, 4);
        Mixins.addConfiguration("gpom.mod.mixin.json");
        EarlySplashWindow.setBootProgress("GPOM mixins registered", 3, 4);
        preloadRuntimeHelpers();
    }

    @Override
    public @Nullable String[] getASMTransformerClass() {
        return new String[] {
                "com.l.gpom.core.ForgeEventSubscriptionTransformerInstaller",
                "com.l.gpom.core.Ae2MultipartGridHostTransformer",
                "com.l.gpom.core.ChickenAsmConcurrencyTransformer",
                "com.l.gpom.core.ForgeRegistrySerializationTransformer",
                "com.l.gpom.core.ForestryRecipeManagerSerializationTransformer",
                "com.l.gpom.core.HammerCoreConstructionTransformer",
                "com.l.gpom.core.HeiStartupProfilerTransformer",
                "com.l.gpom.core.ModularMachineryStartupProfilerTransformer",
                "com.l.gpom.core.ThaumcraftStartupProfilerTransformer",
                "com.l.gpom.core.BetweenlandsStartupProfilerTransformer",
                "com.l.gpom.core.RailcraftStartupProfilerTransformer",
                "com.l.gpom.core.TechRebornStartupProfilerTransformer",
                "com.l.gpom.core.InitPhaseDeepProfilerTransformer",
                "com.l.gpom.core.ForcedResourceReloadTransformer",
                "com.l.gpom.core.BetweenlandsItemRendererTransformer",
                "com.l.gpom.core.CustomMainMenuStartupOverlayTransformer",
                "com.l.gpom.core.RenderLibCompatibilityTransformer",
                "com.l.gpom.core.LibVulpesCompatibilityTransformer",
                "com.l.gpom.core.LoliAsmCompatibilityTransformer",
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
        ForgeEventSubscriptionTransformerOptimizations.install();
    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    private static void preloadRuntimeHelpers() {
        preload("com.l.gpom.optimization.ForestryRecipeManagerOptimizations");
    }

    private static void preload(String className) {
        try {
            Class.forName(className, true, GPOMLoadingPlugin.class.getClassLoader());
        } catch (Throwable ignored) {
        }
    }
}
