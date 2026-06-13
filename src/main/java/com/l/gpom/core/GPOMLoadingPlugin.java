package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.optimization.ForgeEventSubscriptionTransformerOptimizations;
import com.l.gpom.util.EarlySplashBridge;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.Mixins;

import java.util.Map;

@IFMLLoadingPlugin.Name("General Purpose Optimization Mod Core")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class GPOMLoadingPlugin implements IFMLLoadingPlugin {
    private static final Logger LOGGER = LogManager.getLogger("General Purpose Optimization Mod");

    static {
        if (System.getProperty("gpom.bootStartNanos") == null) {
            System.setProperty("gpom.bootStartNanos", Long.toString(System.nanoTime()));
        }
        markBoot("GPOM core plugin static init entered");
        long startedAt = System.nanoTime();
        EarlySplashBridge.startIfEnabled();
        markBootDuration("EarlySplashWindow.startIfEnabled", startedAt);
        EarlySplashBridge.setBootProgress("GPOM core plugin", 1, 4);
        startedAt = System.nanoTime();
        MixinBootstrap.init();
        markBootDuration("MixinBootstrap.init", startedAt);
        EarlySplashBridge.setBootProgress("Mixin bootstrap", 2, 4);
        startedAt = System.nanoTime();
        Mixins.addConfiguration("gpom.mod.mixin.json");
        markBootDuration("Mixins.addConfiguration(gpom.mod.mixin.json)", startedAt);
        EarlySplashBridge.setBootProgress("GPOM mixins registered", 3, 4);
        startedAt = System.nanoTime();
        preloadRuntimeHelpers();
        markBootDuration("GPOM preloadRuntimeHelpers", startedAt);
    }

    @Override
    public @Nullable String[] getASMTransformerClass() {
        markBoot("GPOM getASMTransformerClass entered");
        return new String[] {
                "com.l.gpom.core.ForgeEventSubscriptionTransformerInstaller",
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
                "com.l.gpom.core.BetterPortalsCompatibilityTransformer",
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
        long startedAt = System.nanoTime();
        ForgeEventSubscriptionTransformerOptimizations.install();
        markBootDuration("GPOM injectData/install subscription optimizer", startedAt);
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

    private static void markBootDuration(String label, long startedAt) {
        markBoot(label + " completed in " + formatMillis(System.nanoTime() - startedAt) + " ms");
    }

    private static void markBoot(String label) {
        if (!GpomEarlyConfig.startupProfilerBootLogsEnabled()) {
            return;
        }
        LOGGER.info(
                "[StartupProfiler] [Boot] {} at {} ms since GPOM core init",
                label,
                formatMillis(System.nanoTime() - bootStartNanos())
        );
    }

    private static long bootStartNanos() {
        try {
            return Long.parseLong(System.getProperty("gpom.bootStartNanos", Long.toString(System.nanoTime())));
        } catch (NumberFormatException ignored) {
            return System.nanoTime();
        }
    }

    private static String formatMillis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }
}
