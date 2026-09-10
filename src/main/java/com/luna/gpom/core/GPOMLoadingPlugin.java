package com.luna.gpom.core;

import com.luna.gpom.config.GpomEarlyConfig;
import com.luna.gpom.optimization.ForgeEventSubscriptionTransformerOptimizations;
import com.luna.gpom.util.EarlySplashBridge;
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
                "com.luna.gpom.core.GpomRuntimeNameTransformer",
                "com.luna.gpom.core.ForgeVertexPackTransformer",
                "com.luna.gpom.core.ForgeUnpackedQuadPipeTransformer",
                "com.luna.gpom.core.ForgeEventSubscriptionTransformerInstaller",
                "com.luna.gpom.core.ForgeEventBusRegistrationTransformer",
                "com.luna.gpom.core.FoundationClassLoaderCompatibilityTransformer",
                "com.luna.gpom.core.ChickenAsmConcurrencyTransformer",
                "com.luna.gpom.core.CodeChickenLibModelStateTransformer",
                "com.luna.gpom.core.WirelessRedstoneServerAddonsTransformer",
                "com.luna.gpom.core.ForgeRegistrySerializationTransformer",
                "com.luna.gpom.core.ForestryRecipeManagerSerializationTransformer",
                "com.luna.gpom.core.HammerCoreConstructionTransformer",
                "com.luna.gpom.core.HeiRegistrationThreadSafetyTransformer",
                "com.luna.gpom.core.HeiStartupProfilerTransformer",
                "com.luna.gpom.core.ModularMachineryStartupProfilerTransformer",
                "com.luna.gpom.core.ThaumcraftStartupProfilerTransformer",
                "com.luna.gpom.core.BetweenlandsStartupProfilerTransformer",
                "com.luna.gpom.core.RailcraftStartupProfilerTransformer",
                "com.luna.gpom.core.TechRebornStartupProfilerTransformer",
                "com.luna.gpom.core.InitPhaseDeepProfilerTransformer",
                "com.luna.gpom.core.ForcedResourceReloadTransformer",
                "com.luna.gpom.core.BetweenlandsItemRendererTransformer",
                "com.luna.gpom.core.RenderLibCompatibilityTransformer",
                "com.luna.gpom.core.LibVulpesCompatibilityTransformer",
                "com.luna.gpom.core.LoliAsmCompatibilityTransformer",
                "com.luna.gpom.core.BetterPortalsCompatibilityTransformer",
                "com.luna.gpom.core.JourneyMapBetterPortalsTeleportTransformer",
                "com.luna.gpom.core.AbyssalCraftTieredAltarDimensionTransformer",
                "com.luna.gpom.core.TwilightForestPortalDimensionTransformer",
                "com.luna.gpom.core.ArchitectureCraftCompatibilityTransformer",
                "com.luna.gpom.core.BlockcrafteryCompatibilityTransformer",
                "com.luna.gpom.core.OpenBlocksTankRenderTransformer",
                "com.luna.gpom.core.EnderIOFarmerCompatibilityTransformer",
                "com.luna.gpom.core.BuildingGadgetsFramedCopyPasteTransformer",
                "com.luna.gpom.core.AgriCraftClientFluidSimulationTransformer",
                "com.luna.gpom.core.AgriCraftIrrigationRenderTransformer",
                "com.luna.gpom.core.Ae2ExtendedTerminalGuiCastGuardTransformer",
                "com.luna.gpom.core.JecVolatileNbtTransformer",
                "com.luna.gpom.core.ScannableConfigSyncTransformer",
                "com.luna.gpom.core.SfmLightweightSearchCacheTransformer",
                "com.luna.gpom.core.CustomMainMenuStartupOverlayTransformer",
                "com.luna.gpom.core.RandomThingsRenderUtilsTransformer",
                "com.luna.gpom.core.OptionalModExceptionLoopTransformer",
                "com.luna.gpom.core.LogSpamTransformer",
                "com.luna.gpom.core.WorldgenSafetyTransformer"
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
        startedAt = System.nanoTime();
        ChickenAsmConcurrencyTransformer.hardenRuntimeCaches();
        ChickenAsmConcurrencyTransformer.preloadObfMapping();
        markBootDuration("GPOM injectData/harden ChickenASM", startedAt);
    }

    @Override
    public @Nullable String getAccessTransformerClass() {
        return null;
    }

    private static void preloadRuntimeHelpers() {
        preload("com.luna.gpom.optimization.ForestryRecipeManagerOptimizations");
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
