package com.l.gpom.config;

import com.l.gpom.GPOM;
import com.l.gpom.Reference;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class GpomEarlyConfig {
    private static final String FILE_NAME = "gpom-early.properties";
    private static final String[] GPOM_LOGGER_NAMES = {
            Reference.MOD_NAME,
            "GPOM Early Splash"
    };
    private static final Properties DEFAULTS = new Properties();
    private static final Properties VALUES = new Properties();
    private static final ConcurrentMap<String, Set<String>> SET_VALUES = new ConcurrentHashMap<>();
    private static final String ENDERIO_TILE_ENTITY_LIFECYCLE_DENYLIST =
            "enderio,enderiobase,enderioconduits,enderioconduitsappliedenergistics,"
                    + "enderioconduitsopencomputers,enderioconduitsrefinedstorage,"
                    + "enderiointegrationforestry,enderiointegrationtic,enderiointegrationticlate,"
                    + "enderioinvpanel,enderiomachines,enderiopowertools,enderioendergy";
    private static final String CYCLOPS_CAPABILITY_INIT_DENYLIST =
            "careerbees,commoncapabilities,cyclopscore,integratedderivative,"
                    + "integrateddynamics,integrateddynamicscompat,integratednbt,"
                    + "integratedtunnels,integratedtunnelscompat";
    private static final String BINNIE_FLUID_PREINIT_DENYLIST =
            "binniecore,binniedesign,genetics,botany,extrabees,extratrees,gendustry";
    private static final String PROJECTRED_CLIENT_PREINIT_DENYLIST =
            "projectred-core,projectred-illumination,projectred-compat,projectred-integration,"
                    + "projectred-transmission,projectred-fabrication,projectred-expansion,"
                    + "projectred-relocation,projectred-transportation,projectred-exploration";
    private static final String CLIENT_PREINIT_THREAD_AFFINITY_DENYLIST =
            "simplyjetpacks," + PROJECTRED_CLIENT_PREINIT_DENYLIST;
    private static final String DEFAULT_PREINIT_DENYLIST =
            ENDERIO_TILE_ENTITY_LIFECYCLE_DENYLIST
                    + ",erebus,extracells,draconicevolution,aether_legacy,"
                    + BINNIE_FLUID_PREINIT_DENYLIST + ","
                    + CLIENT_PREINIT_THREAD_AFFINITY_DENYLIST;
    private static final String DEFAULT_CONSTRUCTION_GENERIC_PROXY_DENYLIST =
            "thaumcraft,aether_legacy,architecturecraft";
    private static final String DEFAULT_CONSTRUCTION_GENERIC_SUBSCRIBER_DENYLIST =
            "thaumcraft,thaumcraftfix,chisel,ctm,unlimitedchiselworks,thebetweenlands,twilightforest,erebus,plustic,aether_legacy,superfactorymanager";

    static {
        DEFAULTS.setProperty("fml.parallel.preInit.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.construct.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.postInit.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.init.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.loadComplete.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.workers", "0");
        DEFAULTS.setProperty("fml.parallel.preInit.workers", "0");
        DEFAULTS.setProperty("fml.parallel.construct.workers", "0");
        DEFAULTS.setProperty("fml.parallel.postInit.workers", "0");
        DEFAULTS.setProperty("fml.parallel.init.workers", "0");
        DEFAULTS.setProperty("fml.parallel.loadComplete.workers", "0");
        DEFAULTS.setProperty("fml.parallel.construct.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.construct.denylist", "aether_legacy");
        DEFAULTS.setProperty("fml.parallel.construct.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.construct.dag.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.preInit.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.preInit.denylist", DEFAULT_PREINIT_DENYLIST);
        DEFAULTS.setProperty("fml.parallel.preInit.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.preInit.dag.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.init.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.init.denylist", ENDERIO_TILE_ENTITY_LIFECYCLE_DENYLIST + "," + CYCLOPS_CAPABILITY_INIT_DENYLIST);
        DEFAULTS.setProperty("fml.parallel.init.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.init.dag.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.postInit.allowlist", "*");
        DEFAULTS.setProperty("fml.parallel.postInit.denylist", "crafttweaker,cofhcore,cyclopscore,integrateddynamics,integrateddynamicscompat,integratednbt,integratedtunnels,integratedtunnelscompat,journeymap,thebetweenlands,iceandfire,scannable,randomthings,nuclearcraft,topaddons,thaumicaugmentation,smoothfont");
        DEFAULTS.setProperty("fml.parallel.postInit.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.postInit.dag.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.loadComplete.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.loadComplete.denylist", "");
        DEFAULTS.setProperty("fml.parallel.loadComplete.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.loadComplete.dag.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.registrySerialization.enabled", "true");
        DEFAULTS.setProperty("fml.parallel.clientLifecycleOpenGlScan.enabled", "true");
        DEFAULTS.setProperty("fml.parallel.autoQuarantineGlErrors.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.autoQuarantineGlErrors.includeRelatedMods", "true");
        DEFAULTS.setProperty("gpom.construction.genericSidedProxies.denylist", DEFAULT_CONSTRUCTION_GENERIC_PROXY_DENYLIST);
        DEFAULTS.setProperty("gpom.construction.genericAutomaticSubscribers.denylist", DEFAULT_CONSTRUCTION_GENERIC_SUBSCRIBER_DENYLIST);
        DEFAULTS.setProperty("gpom.logging.enabled", "true");
        DEFAULTS.setProperty("gpom.logging.fmlScheduler.enabled", "false");
        DEFAULTS.setProperty("gpom.logging.optimizationInfo.enabled", "false");
        DEFAULTS.setProperty("gpom.logging.cacheInfo.enabled", "false");
        DEFAULTS.setProperty("gpom.logging.asyncProbeLogs.enabled", "false");
        DEFAULTS.setProperty("gpom.logging.asyncProbeLogs.queueSize", "8192");
        DEFAULTS.setProperty("gpom.vintageFix.suppressUcwModelErrorSpam", "true");
        DEFAULTS.setProperty("gpom.vintageFix.skipUcwDefinitionEarlyModelLoad", "true");
        DEFAULTS.setProperty("gpom.ctm.tolerateUnknownRenderLayer", "true");
        DEFAULTS.setProperty("gpom.ctm.suppressTextureMetadataErrorSpam", "true");
        DEFAULTS.setProperty("gpom.cacheInvalidation.denylist", "ausm,gpom");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.boot.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.phaseLifecycle.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.modDetails.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.phaseSummary.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.phaseDigest.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.memoryDetails.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.probes.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.probeSummary.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.wallDiagnostics.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.stackSamples.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.resourceLoadOrder.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.nonFmlGaps.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.constructCriticalPath.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.preInitCriticalPath.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.loadCompleteCriticalPath.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.postPreInitProbeSummary.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.probeLogs.enabled", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.probeHighVolumeEventBusPosts", "false");
        DEFAULTS.setProperty("gpom.startupProfiler.probePrefixAllowlist", "");
        DEFAULTS.setProperty("gpom.startupProfiler.topCount", "40");
        DEFAULTS.setProperty("gpom.startupProfiler.postPreInitProgressBars", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.postPreInitProgressSteps", "96");
        DEFAULTS.setProperty("gpom.earlySplash.enabled", "false");
        DEFAULTS.setProperty("gpom.earlySplash.packName", "Minecraft");
        DEFAULTS.setProperty("gpom.worldLoadingScreen.enabled", "true");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.enabled", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.forceGcBeforeSnapshots", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.delayedSnapshotMillis", "2000,10000,25000");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.deepAttribution.enabled", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.deepAttribution.maxEntries", "8");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.enabled", "false");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.summaryIntervalSeconds", "10");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.topCount", "12");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.slowThresholdMillis", "50");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.immediateSlowLogs.enabled", "true");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.forgeEvents.enabled", "true");
        DEFAULTS.setProperty("gpom.runtimeSinkProfiler.forgeEvents.profileAll", "false");
        DEFAULTS.setProperty("gpom.ae2.patternDiagnostics.enabled", "false");
        DEFAULTS.setProperty("gpom.ae2.patternDiagnostics.maxFailures", "200");
        DEFAULTS.setProperty("gpom.ae2.patternDiagnostics.logMismatchedOutputs", "true");
        DEFAULTS.setProperty("gpom.ae2.patternDiagnostics.skipRecipeFunctions", "true");
        DEFAULTS.setProperty("gpom.jecalculation.pinnedCraftOverlay.enabled", "true");
        DEFAULTS.setProperty("gpom.jecalculation.fuzzyVolatileItemNbt.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.extendedCraftingLowerTierTransfer.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.draconicFusionTransfer.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.craftableRecipesFirst.enabled", "true");
        DEFAULTS.setProperty("gpom.journeymap.waypointDimensionDropup.enabled", "true");
        DEFAULTS.setProperty("gpom.mainMenuStartupTime.enabled", "false");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.enabled", "false");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.visibleRows", "7");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.columns", "2");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.preferRight", "false");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.shiftRightClickEquip", "true");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.aether.enabled", "false");
        DEFAULTS.setProperty("gpom.baubles.sideSlots.cosmeticArmor.enabled", "false");
        DEFAULTS.setProperty("gpom.loliasm.threadSafeStatefulRegistry", "true");
        DEFAULTS.setProperty("gpom.betterPortals.fixMissingNewTarget", "true");
        DEFAULTS.setProperty("gpom.betterPortals.remapLegacyAetherBridge", "true");
        DEFAULTS.setProperty("gpom.betterPortals.skipLegacyAetherBridgeIfMissing", "true");
        DEFAULTS.setProperty("gpom.betterPortals.fixGuavaAddCallback", "true");
        DEFAULTS.setProperty("gpom.betterPortals.skipUnsafeThirdPartyTransition", "true");
        DEFAULTS.setProperty("gpom.betterPortals.cleanupClientWorlds", "false");
        DEFAULTS.setProperty("gpom.betterPortals.journeymapWaypointTeleportTransition", "true");
        DEFAULTS.setProperty("gpom.betterPortals.journeymapWaypointTeleportRequireActiveView", "true");
        DEFAULTS.setProperty("gpom.architecturecraft.fastShapeLighting", "true");
        DEFAULTS.setProperty("gpom.architecturecraft.accurateHitboxes", "true");
        DEFAULTS.setProperty("gpom.architecturecraft.parentMaterialOcclusion.enabled", "false");
        DEFAULTS.setProperty("gpom.blockcraftery.accurateHitboxes", "true");
        DEFAULTS.setProperty("gpom.blockcraftery.parentMaterialOcclusion.enabled", "true");
        DEFAULTS.setProperty("gpom.blockcraftery.modelRenderLayerCompat", "true");
        DEFAULTS.setProperty("gpom.journeymap.cleanupLeaks", "true");
        DEFAULTS.setProperty("gpom.journeymap.cleanupLeaksOnDimensionHandoff", "false");
        DEFAULTS.setProperty("gpom.scannable.skipRedundantConfigOreCacheRebuilds", "true");
        DEFAULTS.setProperty("gpom.enderio.repairMissingTileEntityMappings", "true");
        DEFAULTS.setProperty("gpom.registry.repairThaumicWondersMissingMappings", "false");
        DEFAULTS.setProperty("gpom.registry.ignoreMissingSoundEventNamespaces", "erebus");
        DEFAULTS.setProperty("gpom.registry.failMissingBlockItemNamespaces", "");
        DEFAULTS.setProperty("gpom.sfm.lightweightSearchCache.enabled", "true");
        DEFAULTS.setProperty("gpom.sfm.lightweightSearchCache.useHeiIngredients", "true");
        DEFAULTS.setProperty("gpom.sfm.lightweightSearchCache.workers", "0");
        DEFAULTS.setProperty("gpom.railcraftLazyItemConditions", "false");
        DEFAULTS.setProperty("gpom.railcraft.deferModuleIC2Containers", "false");
        DEFAULTS.setProperty("gpom.railcraft.deferModuleContainers", "false");
        DEFAULTS.setProperty("gpom.railcraft.deferSelectedModuleContainers", "false");
        DEFAULTS.setProperty("gpom.railcraft.deferModuleContainerAllowlist", "mods.railcraft.common.modules.ModuleBuilding,mods.railcraft.common.modules.ModuleCharge,mods.railcraft.common.modules.ModuleCore,mods.railcraft.common.modules.ModuleLocomotives,mods.railcraft.common.modules.ModuleTracksStrapIron");
        DEFAULTS.setProperty("gpom.railcraft.lazyCartConfig", "false");
        DEFAULTS.setProperty("gpom.astralSorcery.deferAssetLibraryReload", "true");
        DEFAULTS.setProperty("gpom.agricraft.fastJsonIo", "true");
        DEFAULTS.setProperty("gpom.agricraft.fastResourceScan", "true");
        DEFAULTS.setProperty("gpom.agricraft.skipJsonWriteback", "true");
        DEFAULTS.setProperty("gpom.agricraft.refreshChannelsAfterBulkPlacement", "false");
        DEFAULTS.setProperty("gpom.openComputersSettingsCache", "true");
        DEFAULTS.setProperty("gpom.openComputers.fastLuaSelection", "true");
        DEFAULTS.setProperty("gpom.openComputersCallProfiler", "false");
        DEFAULTS.setProperty("gpom.openComputersIntegrationProfiler", "false");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.enabled", "false");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.allowlist", "");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.workers", "1");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.deferMinCompletedHandlers", "32");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.deferUntilSerialMillis", "1000");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.pauseDuringSerialHandlers", "true");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.pauseDuringBlockingWaits", "true");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.maxClassesPerMod", "384");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.chunkSize", "32");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.includeAnonClasses", "false");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.extraPrefixes", "");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.noInitAllowlist", "");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.noInitPrefixes", "");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.initializeClasses", "false");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.initializeAllowlist", "");
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.explicitClasses", "");
        DEFAULTS.setProperty("gpom.gendustryConfigCache", "true");
        DEFAULTS.setProperty("gpom.gendustryCallProfiler", "false");
        DEFAULTS.setProperty("gpom.erebus.deferComposterRegistry", "true");
        DEFAULTS.setProperty("gpom.erebus.deferOreConfigs", "true");
        DEFAULTS.setProperty("gpom.enderio.fastSpawnerEntityValidation", "true");
        DEFAULTS.setProperty("gpom.crafttweaker.fastZenRegister", "false");
        DEFAULTS.setProperty("gpom.crafttweaker.fastZenRegister.parallelClassLoad", "false");
        DEFAULTS.setProperty("gpom.crafttweaker.fastZenRegister.classLoadWorkers", "0");
        DEFAULTS.setProperty("gpom.crafttweaker.fastZenRegister.deepProbes", "false");
        DEFAULTS.setProperty("gpom.crafttweaker.lazyItemList", "true");
        DEFAULTS.setProperty("gpom.crafttweaker.suppressFunctionTypeStdout", "true");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.enabled", "false");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.workers", "0");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.allowlist", "*");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.denylist", "");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.offThreadZenParse", "false");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.suppressGlobalDebugCompileLogs", "true");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts", "false");
        DEFAULTS.setProperty("gpom.crafttweaker.parallelScriptParsing.deepProbes", "false");
        DEFAULTS.setProperty("gpom.nuclearcraft.fastManufactoryMetalRecipes", "false");
        DEFAULTS.setProperty("gpom.nuclearcraft.cacheManufactoryLogCraftingResults", "true");
        DEFAULTS.setProperty("gpom.nuclearcraft.skipEmptyManufactoryLogCraftingFallback", "false");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.enabled", "false");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.registries", "minecraft:recipes,minecraft:blocks,minecraft:items,minecraft:entities,ebwizardry:spells");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.recipes.enabled", "false");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.workers", "0");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.queuedCommit", "true");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.proxyEventRegistry", "true");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist", "moarsigns@minecraft:recipes,cyclopscore@minecraft:recipes,integrateddynamics@minecraft:recipes");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.immediateCommitRegistries", "minecraft:items,minecraft:entities");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.proxyImmediateRegistries", "false");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.orderedWaveRegistries", "minecraft:items");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.immediateCommitWaitDiagnosticsMillis", "5000");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.dependencyGating", "true");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.allowlist", "*");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.denylist", ENDERIO_TILE_ENTITY_LIFECYCLE_DENYLIST + ",contenttweaker,modtweaker,betterportals,actuallyadditions@minecraft:items,abyssalcraft@minecraft:items,actuallybaubles@minecraft:items,bewitchment@minecraft:blocks,bewitchment@minecraft:items,bhc@minecraft:items,bigreactors@minecraft:items,botania@minecraft:items,chickens@minecraft:items,chisel@minecraft:blocks,chisel@minecraft:items,chisel@minecraft:recipes,deepmoblearningbm@minecraft:items,enderio@minecraft:blocks,enderiobase@minecraft:blocks,enderioconduits@minecraft:blocks,enderioinvpanel@minecraft:blocks,enderiomachines@minecraft:blocks,enderiopowertools@minecraft:blocks,enderioendergy@minecraft:blocks,extendedcrafting@minecraft:items,extrabotany@minecraft:items,glassential@minecraft:items,iceandfire@minecraft:items,immersiveengineering@minecraft:items,industrialforegoing@minecraft:items,mysticalagradditions@minecraft:items,mysticalagriculture@minecraft:items,natura@minecraft:blocks,natura@minecraft:items,plustic@minecraft:items,storagedrawers@minecraft:items,tconstruct@minecraft:items,thebetweenlands@minecraft:blocks,thebetweenlands@minecraft:recipes,unlimitedchiselworks@minecraft:blocks,unlimitedchiselworks@minecraft:items,unlimitedchiselworks@minecraft:recipes,zerocore@minecraft:items,integrateddynamics@minecraft:blocks,rftools@minecraft:items,appliedenergistics2@minecraft:items,integrateddynamics@minecraft:items");
        DEFAULTS.setProperty("gpom.registry.parallelRegisterEvents.deepDiagnostics", "false");
        DEFAULTS.setProperty("gpom.preInitHighSinkCallProfiler", "false");
        DEFAULTS.setProperty("gpom.postPreInitTopCallProfiler", "false");
        DEFAULTS.setProperty("gpom.hei.recipeProgressBar.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.recipeProgressBar.stepSize", "256");
        DEFAULTS.setProperty("gpom.hei.searchWorkers", "0");
        DEFAULTS.setProperty("gpom.hei.fastPreInitPluginDiscovery.enabled", "false");
        DEFAULTS.setProperty("gpom.hei.fastPreInitPluginDiscovery.workers", "0");
        DEFAULTS.setProperty("gpom.hei.fastPreInitPluginDiscovery.deepProbes", "false");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.workers", "6");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.overlapSerial", "true");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.allowlist", "*");
        DEFAULTS.setProperty(
                "gpom.hei.parallelPluginRegistration.denylist",
                "mezz.jei.plugins.jei.JEIInternalPlugin,mezz.jei.plugins.modsupport.ModSupportPlugin,"
                        + "com.l.gpom.compat.hei.GpomHeiQoLPlugin,"
                        + "lumien.randomthings.handler.compability.jei.RandomThingsPlugin,"
                        + "com.blakebr0.mysticalagradditions.compat.jei.CompatJEI"
        );
        DEFAULTS.setProperty("gpom.hei.jerVillagerTradeCache.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.jerVillagerTradeCache.samples", "32");
        DEFAULTS.setProperty("gpom.hei.jerLootDropCache.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.fastForestryBottler.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.forestryBottlerRecipeCache.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.compressForestryBottlerRecipes.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.skipUnsupportedRuntimeRecipes.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.skipUnsupportedRuntimeRecipes.classes", "");
        DEFAULTS.setProperty("gpom.hei.fastEnderIOTank.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.compressEnderIOTankFluidRecipes.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.extraTreesLumbermillRecipeCache.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.fastThermalTransposerContainers.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.thermalTransposerContainerCache.enabled", "true");

        VALUES.putAll(DEFAULTS);
        load();
        applyEarlySystemProperties();
        silenceGpomLoggersIfDisabled();
    }

    private GpomEarlyConfig() {
    }

    public static boolean parallelPreInitEnabled() {
        return booleanValue("fml.parallel.preInit.enabled");
    }

    public static boolean parallelConstructEnabled() {
        return booleanValue("fml.parallel.construct.enabled");
    }

    public static boolean parallelPostInitEnabled() {
        return booleanValue("fml.parallel.postInit.enabled");
    }

    public static boolean parallelInitEnabled() {
        return booleanValue("fml.parallel.init.enabled");
    }

    public static boolean parallelLoadCompleteEnabled() {
        return booleanValue("fml.parallel.loadComplete.enabled");
    }

    public static int parallelWorkers() {
        return intValue("fml.parallel.workers", 0);
    }

    public static int parallelPreInitWorkers() {
        int configured = intValue("fml.parallel.preInit.workers", 0);
        return configured > 0 ? configured : parallelWorkers();
    }

    public static int parallelConstructWorkers() {
        int configured = intValue("fml.parallel.construct.workers", 0);
        return configured > 0 ? configured : parallelWorkers();
    }

    public static int parallelPostInitWorkers() {
        int configured = intValue("fml.parallel.postInit.workers", 0);
        return configured > 0 ? configured : parallelWorkers();
    }

    public static int parallelInitWorkers() {
        int configured = intValue("fml.parallel.init.workers", 0);
        return configured > 0 ? configured : parallelWorkers();
    }

    public static int parallelLoadCompleteWorkers() {
        int configured = intValue("fml.parallel.loadComplete.workers", 0);
        return configured > 0 ? configured : parallelWorkers();
    }

    public static Set<String> parallelPreInitAllowlist() {
        return setValue("fml.parallel.preInit.allowlist");
    }

    public static Set<String> parallelConstructAllowlist() {
        return setValue("fml.parallel.construct.allowlist");
    }

    public static Set<String> parallelConstructDenylist() {
        return setValue("fml.parallel.construct.denylist");
    }

    public static boolean parallelConstructContinueOnModError() {
        return booleanValue("fml.parallel.construct.continueOnModError");
    }

    public static boolean parallelConstructDagEnabled() {
        return booleanValue("fml.parallel.construct.dag.enabled");
    }

    public static Set<String> parallelPreInitDenylist() {
        return setValue("fml.parallel.preInit.denylist");
    }

    public static boolean parallelPreInitContinueOnModError() {
        return booleanValue("fml.parallel.preInit.continueOnModError");
    }

    public static boolean parallelPreInitDagEnabled() {
        return booleanValue("fml.parallel.preInit.dag.enabled");
    }

    public static boolean parallelAutoQuarantineGlErrorsEnabled() {
        return booleanValue("fml.parallel.autoQuarantineGlErrors.enabled");
    }

    public static boolean parallelAutoQuarantineGlErrorsIncludeRelatedMods() {
        return booleanValue("fml.parallel.autoQuarantineGlErrors.includeRelatedMods");
    }

    public static Set<String> constructionGenericSidedProxiesDenylist() {
        return setValue("gpom.construction.genericSidedProxies.denylist");
    }

    public static Set<String> constructionGenericAutomaticSubscribersDenylist() {
        return setValue("gpom.construction.genericAutomaticSubscribers.denylist");
    }

    public static Set<String> parallelInitAllowlist() {
        return setValue("fml.parallel.init.allowlist");
    }

    public static Set<String> parallelInitDenylist() {
        return setValue("fml.parallel.init.denylist");
    }

    public static boolean parallelInitContinueOnModError() {
        return booleanValue("fml.parallel.init.continueOnModError");
    }

    public static boolean parallelInitDagEnabled() {
        return booleanValue("fml.parallel.init.dag.enabled");
    }

    public static Set<String> parallelPostInitAllowlist() {
        return setValue("fml.parallel.postInit.allowlist");
    }

    public static Set<String> parallelPostInitDenylist() {
        return setValue("fml.parallel.postInit.denylist");
    }

    public static boolean parallelPostInitContinueOnModError() {
        return booleanValue("fml.parallel.postInit.continueOnModError");
    }

    public static boolean parallelPostInitDagEnabled() {
        return booleanValue("fml.parallel.postInit.dag.enabled");
    }

    public static Set<String> parallelLoadCompleteAllowlist() {
        return setValue("fml.parallel.loadComplete.allowlist");
    }

    public static Set<String> parallelLoadCompleteDenylist() {
        return setValue("fml.parallel.loadComplete.denylist");
    }

    public static boolean parallelLoadCompleteContinueOnModError() {
        return booleanValue("fml.parallel.loadComplete.continueOnModError");
    }

    public static boolean parallelLoadCompleteDagEnabled() {
        return booleanValue("fml.parallel.loadComplete.dag.enabled");
    }

    public static boolean heiExtendedCraftingLowerTierTransferEnabled() {
        return booleanValue("gpom.hei.extendedCraftingLowerTierTransfer.enabled");
    }

    public static boolean heiDraconicFusionTransferEnabled() {
        return booleanValue("gpom.hei.draconicFusionTransfer.enabled");
    }

    public static boolean heiCraftableRecipesFirstEnabled() {
        return booleanValue("gpom.hei.craftableRecipesFirst.enabled");
    }

    private static void load() {
        File file = configFile();
        ensureDefaultFile(file);
        if (!file.isFile()) {
            return;
        }

        Properties loaded = new Properties();
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            loaded.load(input);
            VALUES.putAll(loaded);
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to load {}; using defaults", file, exception);
            return;
        }
        appendMissingDefaults(file, loaded);
    }

    private static void ensureDefaultFile(File file) {
        if (file.isFile()) {
            return;
        }

        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to create config directory {}", parent);
            return;
        }

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new BufferedOutputStream(new FileOutputStream(file)),
                StandardCharsets.UTF_8
        ))) {
            writeDefaultFile(writer);
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to write default {}", file, exception);
        }
    }

    private static void appendMissingDefaults(File file, Properties loaded) {
        List<String> missing = new ArrayList<>();
        for (String key : DEFAULTS.stringPropertyNames()) {
            if (!loaded.containsKey(key)) {
                missing.add(key);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        Collections.sort(missing);

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new BufferedOutputStream(new FileOutputStream(file, true)),
                StandardCharsets.UTF_8
        ))) {
            writer.println();
            writeComment(writer, "== Added Defaults ==");
            writeComment(writer, "GPOM appended these keys because this build defines options missing from the existing file.");
            for (String key : missing) {
                writeProperty(writer, key);
            }
            writer.println();
            GPOM.LOGGER.info("[GPOM Config] Added {} missing default option(s) to {}", missing.size(), file);
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to append missing defaults to {}", file, exception);
        }
    }

    private static void writeDefaultFile(PrintWriter writer) {
        writeComment(writer, "General Purpose Optimization Mod early-loading config.");
        writeComment(writer, "Generated comments describe each section and each value; delete and relaunch to regenerate this file with current defaults.");
        writer.println();
        writeSection(writer, "File Format", "This file is loaded before Forge's normal config system exists. Comma lists accept mod ids unless noted; '*' means every candidate; denylist entries always win.",
                "fml.parallel.workers"
        );
        writeSection(writer, "Construction Parallelism", "Controls FMLConstructionEvent worker dispatch. This is the most fragile phase because mod instances, proxies, and automatic subscribers are created here.",
                "fml.parallel.construct.enabled",
                "fml.parallel.construct.workers",
                "fml.parallel.construct.allowlist",
                "fml.parallel.construct.denylist",
                "fml.parallel.construct.continueOnModError",
                "fml.parallel.construct.dag.enabled"
        );
        writeSection(writer, "PreInit Parallelism", "Controls FMLPreInitializationEvent worker dispatch. This phase touches registries, model loaders, configs, and client resource setup, so use tested allowlists.",
                "fml.parallel.preInit.enabled",
                "fml.parallel.preInit.workers",
                "fml.parallel.preInit.allowlist",
                "fml.parallel.preInit.denylist",
                "fml.parallel.preInit.continueOnModError",
                "fml.parallel.preInit.dag.enabled"
        );
        writeSection(writer, "Init Parallelism", "Controls FMLInitializationEvent worker dispatch. Mods that create OpenGL objects, textures, shaders, or shared global state should stay denied.",
                "fml.parallel.init.enabled",
                "fml.parallel.init.workers",
                "fml.parallel.init.allowlist",
                "fml.parallel.init.denylist",
                "fml.parallel.init.continueOnModError",
                "fml.parallel.init.dag.enabled"
        );
        writeSection(writer, "PostInit Parallelism", "Controls FMLPostInitializationEvent worker dispatch. Usually safer than PreInit/Init, but recipe mutation and render integration still need pack validation.",
                "fml.parallel.postInit.enabled",
                "fml.parallel.postInit.workers",
                "fml.parallel.postInit.allowlist",
                "fml.parallel.postInit.denylist",
                "fml.parallel.postInit.continueOnModError",
                "fml.parallel.postInit.dag.enabled"
        );
        writeSection(writer, "LoadComplete Parallelism", "Controls FMLLoadCompleteEvent worker dispatch. This is the safest lifecycle phase, but HEI/JER and client-global mods can still serialize the wall time.",
                "fml.parallel.loadComplete.enabled",
                "fml.parallel.loadComplete.workers",
                "fml.parallel.loadComplete.allowlist",
                "fml.parallel.loadComplete.denylist",
                "fml.parallel.loadComplete.continueOnModError",
                "fml.parallel.loadComplete.dag.enabled"
        );
        writeSection(writer, "Thread Safety Guards", "Global safety switches used by the lifecycle scheduler while phases are running in workers.",
                "fml.parallel.registrySerialization.enabled",
                "fml.parallel.clientLifecycleOpenGlScan.enabled",
                "fml.parallel.autoQuarantineGlErrors.enabled",
                "fml.parallel.autoQuarantineGlErrors.includeRelatedMods"
        );
        writeSection(writer, "Construction Shortcuts", "Fine-grained controls for GPOM's Forge construction shortcuts. Denied mods fall back to Forge's original behavior for the named shortcut only.",
                "gpom.construction.genericSidedProxies.denylist",
                "gpom.construction.genericAutomaticSubscribers.denylist"
        );
        writeSection(writer, "HEI QoL", "Client-side Had Enough Items integration tweaks. These only register when the relevant target mod is loaded.",
                "gpom.hei.extendedCraftingLowerTierTransfer.enabled",
                "gpom.hei.draconicFusionTransfer.enabled",
                "gpom.hei.craftableRecipesFirst.enabled"
        );
        writeSection(writer, "GPOM Logging", "Controls GPOM's own logger and high-volume categories. Keep the root logger enabled when diagnostics such as world lifecycle snapshots are needed.",
                "gpom.logging.enabled",
                "gpom.logging.fmlScheduler.enabled",
                "gpom.logging.optimizationInfo.enabled",
                "gpom.logging.cacheInfo.enabled",
                "gpom.logging.asyncProbeLogs.enabled",
                "gpom.logging.asyncProbeLogs.queueSize"
        );
        writeSection(writer, "Cache Invalidation", "Mod ids, jar names, or file stems ignored by GPOM cache input signatures. Use this for local no-registry dev mods that change often but do not affect cached data.",
                "gpom.cacheInvalidation.denylist"
        );
        writeSection(writer, "Startup Profiler Logs", "Controls how much startup timing data GPOM emits. Disabling logs does not disable the underlying optimized code paths.",
                "gpom.startupProfiler.logs.enabled",
                "gpom.startupProfiler.logs.boot.enabled",
                "gpom.startupProfiler.logs.phaseLifecycle.enabled",
                "gpom.startupProfiler.logs.modDetails.enabled",
                "gpom.startupProfiler.logs.phaseSummary.enabled",
                "gpom.startupProfiler.logs.phaseDigest.enabled",
                "gpom.startupProfiler.logs.memoryDetails.enabled",
                "gpom.startupProfiler.logs.probes.enabled",
                "gpom.startupProfiler.logs.probeSummary.enabled",
                "gpom.startupProfiler.logs.wallDiagnostics.enabled",
                "gpom.startupProfiler.logs.stackSamples.enabled",
                "gpom.startupProfiler.logs.resourceLoadOrder.enabled",
                "gpom.startupProfiler.logs.nonFmlGaps.enabled",
                "gpom.startupProfiler.logs.constructCriticalPath.enabled",
                "gpom.startupProfiler.logs.preInitCriticalPath.enabled",
                "gpom.startupProfiler.logs.loadCompleteCriticalPath.enabled",
                "gpom.startupProfiler.logs.postPreInitProbeSummary.enabled",
                "gpom.startupProfiler.probeLogs.enabled",
                "gpom.startupProfiler.probeHighVolumeEventBusPosts",
                "gpom.startupProfiler.probePrefixAllowlist",
                "gpom.startupProfiler.topCount",
                "gpom.startupProfiler.postPreInitProgressBars",
                "gpom.startupProfiler.postPreInitProgressSteps"
        );
        writeSection(writer, "Client Loading UI", "Optional client-only UI additions for early startup, world entry, and the main menu startup time display.",
                "gpom.earlySplash.enabled",
                "gpom.earlySplash.packName",
                "gpom.worldLoadingScreen.enabled",
                "gpom.mainMenuStartupTime.enabled"
        );
        writeSection(writer, "Baubles UI", "Optional client-side Baubles inventory layout that moves Bauble slots to a Curios-like side rail and keeps quick-equip validated on the server.",
                "gpom.baubles.sideSlots.enabled",
                "gpom.baubles.sideSlots.visibleRows",
                "gpom.baubles.sideSlots.columns",
                "gpom.baubles.sideSlots.preferRight",
                "gpom.baubles.sideSlots.shiftRightClickEquip",
                "gpom.baubles.sideSlots.aether.enabled",
                "gpom.baubles.sideSlots.cosmeticArmor.enabled"
        );
        writeSection(writer, "World Lifecycle Profiling", "World load, unload, and dimension-switch diagnostics for tracking retained client state and memory leaks.",
                "gpom.worldLifecycleProfiler.enabled",
                "gpom.worldLifecycleProfiler.forceGcBeforeSnapshots",
                "gpom.worldLifecycleProfiler.delayedSnapshotMillis",
                "gpom.worldLifecycleProfiler.deepAttribution.enabled",
                "gpom.worldLifecycleProfiler.deepAttribution.maxEntries"
        );
        writeSection(writer, "Runtime Sink Profiling", "Low-overhead aggregate timing probes for world load, frame/tick, render, and selected Forge event-handler sinks.",
                "gpom.runtimeSinkProfiler.enabled",
                "gpom.runtimeSinkProfiler.summaryIntervalSeconds",
                "gpom.runtimeSinkProfiler.topCount",
                "gpom.runtimeSinkProfiler.slowThresholdMillis",
                "gpom.runtimeSinkProfiler.immediateSlowLogs.enabled",
                "gpom.runtimeSinkProfiler.forgeEvents.enabled",
                "gpom.runtimeSinkProfiler.forgeEvents.profileAll"
        );
        writeSection(writer, "AE2 Pattern Diagnostics", "Temporary one-shot world-load scanner that builds canonical AE2 crafting patterns for registered recipes and logs recipes that AE2 cannot validate.",
                "gpom.ae2.patternDiagnostics.enabled",
                "gpom.ae2.patternDiagnostics.maxFailures",
                "gpom.ae2.patternDiagnostics.logMismatchedOutputs",
                "gpom.ae2.patternDiagnostics.skipRecipeFunctions"
        );
        writeSection(writer, "JEC QoL", "Small client-side Just Enough Calculation interaction fixes. These remain no-op unless JEC is installed.",
                "gpom.jecalculation.pinnedCraftOverlay.enabled",
                "gpom.jecalculation.fuzzyVolatileItemNbt.enabled"
        );
        writeSection(writer, "JourneyMap QoL", "Small client-side JourneyMap interaction fixes. These remain no-op unless JourneyMap is installed.",
                "gpom.journeymap.waypointDimensionDropup.enabled"
        );
        writeSection(writer, "Compatibility Fixes", "Small exact-version safety fixes for known thread-safety or lifecycle issues exposed by modern render/loading stacks.",
                "gpom.loliasm.threadSafeStatefulRegistry",
                "gpom.betterPortals.fixMissingNewTarget",
                "gpom.betterPortals.remapLegacyAetherBridge",
                "gpom.betterPortals.skipLegacyAetherBridgeIfMissing",
                "gpom.betterPortals.fixGuavaAddCallback",
                "gpom.betterPortals.skipUnsafeThirdPartyTransition",
                "gpom.betterPortals.cleanupClientWorlds",
                "gpom.betterPortals.journeymapWaypointTeleportTransition",
                "gpom.betterPortals.journeymapWaypointTeleportRequireActiveView",
                "gpom.agricraft.refreshChannelsAfterBulkPlacement",
                "gpom.architecturecraft.fastShapeLighting",
                "gpom.architecturecraft.accurateHitboxes",
                "gpom.architecturecraft.parentMaterialOcclusion.enabled",
                "gpom.blockcraftery.accurateHitboxes",
                "gpom.blockcraftery.parentMaterialOcclusion.enabled",
                "gpom.blockcraftery.modelRenderLayerCompat",
                "gpom.journeymap.cleanupLeaks",
                "gpom.journeymap.cleanupLeaksOnDimensionHandoff",
                "gpom.scannable.skipRedundantConfigOreCacheRebuilds",
                "gpom.enderio.repairMissingTileEntityMappings",
                "gpom.registry.repairThaumicWondersMissingMappings",
                "gpom.registry.ignoreMissingSoundEventNamespaces",
                "gpom.registry.failMissingBlockItemNamespaces",
                "gpom.sfm.lightweightSearchCache.enabled",
                "gpom.sfm.lightweightSearchCache.useHeiIngredients",
                "gpom.sfm.lightweightSearchCache.workers"
        );
        writeSection(writer, "Railcraft Deferrals", "Exact-version startup deferrals for Railcraft 12.1.0-beta-8 module/container initialization.",
                "gpom.railcraftLazyItemConditions",
                "gpom.railcraft.deferModuleIC2Containers",
                "gpom.railcraft.deferModuleContainers",
                "gpom.railcraft.deferSelectedModuleContainers",
                "gpom.railcraft.deferModuleContainerAllowlist",
                "gpom.railcraft.lazyCartConfig"
        );
        writeSection(writer, "General Startup Optimizations", "Exact-version or input-validated startup shortcuts outside HEI. Disable a key to fall back to the original mod behavior.",
                "gpom.astralSorcery.deferAssetLibraryReload",
                "gpom.agricraft.fastJsonIo",
                "gpom.agricraft.fastResourceScan",
                "gpom.agricraft.skipJsonWriteback",
                "gpom.openComputersSettingsCache",
                "gpom.openComputers.fastLuaSelection",
                "gpom.openComputersCallProfiler",
                "gpom.openComputersIntegrationProfiler",
                "gpom.gendustryConfigCache",
                "gpom.gendustryCallProfiler",
                "gpom.erebus.deferComposterRegistry",
                "gpom.erebus.deferOreConfigs",
                "gpom.enderio.fastSpawnerEntityValidation",
                "gpom.crafttweaker.fastZenRegister",
                "gpom.crafttweaker.fastZenRegister.parallelClassLoad",
                "gpom.crafttweaker.fastZenRegister.classLoadWorkers",
                "gpom.crafttweaker.fastZenRegister.deepProbes",
                "gpom.crafttweaker.lazyItemList",
                "gpom.crafttweaker.suppressFunctionTypeStdout",
                "gpom.crafttweaker.parallelScriptParsing.enabled",
                "gpom.crafttweaker.parallelScriptParsing.workers",
                "gpom.crafttweaker.parallelScriptParsing.allowlist",
                "gpom.crafttweaker.parallelScriptParsing.denylist",
                "gpom.crafttweaker.parallelScriptParsing.offThreadZenParse",
                "gpom.crafttweaker.parallelScriptParsing.suppressGlobalDebugCompileLogs",
                "gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts",
                "gpom.crafttweaker.parallelScriptParsing.deepProbes",
                "gpom.nuclearcraft.fastManufactoryMetalRecipes",
                "gpom.nuclearcraft.cacheManufactoryLogCraftingResults",
                "gpom.nuclearcraft.skipEmptyManufactoryLogCraftingFallback",
                "gpom.registry.parallelRegisterEvents.enabled",
                "gpom.registry.parallelRegisterEvents.registries",
                "gpom.registry.parallelRegisterEvents.recipes.enabled",
                "gpom.registry.parallelRegisterEvents.workers",
                "gpom.registry.parallelRegisterEvents.queuedCommit",
                "gpom.registry.parallelRegisterEvents.proxyEventRegistry",
                "gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist",
                "gpom.registry.parallelRegisterEvents.immediateCommitRegistries",
                "gpom.registry.parallelRegisterEvents.proxyImmediateRegistries",
                "gpom.registry.parallelRegisterEvents.orderedWaveRegistries",
                "gpom.registry.parallelRegisterEvents.immediateCommitWaitDiagnosticsMillis",
                "gpom.registry.parallelRegisterEvents.dependencyGating",
                "gpom.registry.parallelRegisterEvents.allowlist",
                "gpom.registry.parallelRegisterEvents.denylist",
                "gpom.registry.parallelRegisterEvents.deepDiagnostics",
                "gpom.preInitHighSinkCallProfiler",
                "gpom.postPreInitTopCallProfiler"
        );
        writeSection(writer, "PreInit Class Prewarm", "Optional sidecar class definition/linking during PreInit. Static initialization is disabled by default because it can execute mod code early.",
                "gpom.preInitClassPrewarm.enabled",
                "gpom.preInitClassPrewarm.allowlist",
                "gpom.preInitClassPrewarm.workers",
                "gpom.preInitClassPrewarm.deferMinCompletedHandlers",
                "gpom.preInitClassPrewarm.deferUntilSerialMillis",
                "gpom.preInitClassPrewarm.pauseDuringSerialHandlers",
                "gpom.preInitClassPrewarm.pauseDuringBlockingWaits",
                "gpom.preInitClassPrewarm.maxClassesPerMod",
                "gpom.preInitClassPrewarm.chunkSize",
                "gpom.preInitClassPrewarm.includeAnonClasses",
                "gpom.preInitClassPrewarm.extraPrefixes",
                "gpom.preInitClassPrewarm.noInitAllowlist",
                "gpom.preInitClassPrewarm.noInitPrefixes",
                "gpom.preInitClassPrewarm.initializeClasses",
                "gpom.preInitClassPrewarm.initializeAllowlist",
                "gpom.preInitClassPrewarm.explicitClasses"
        );
        writeSection(writer, "HEI Progress And Search", "HEI client startup helpers that keep recipe ingestion visible and replace the single search worker with a bounded pool.",
                "gpom.hei.recipeProgressBar.enabled",
                "gpom.hei.recipeProgressBar.stepSize",
                "gpom.hei.searchWorkers"
        );
        writeSection(writer, "HEI Plugin Parallelism", "Experimental worker dispatch for allowlisted HEI plugin registration calls. Empty allowlist means no plugin registration is threaded.",
                "gpom.hei.fastPreInitPluginDiscovery.enabled",
                "gpom.hei.fastPreInitPluginDiscovery.workers",
                "gpom.hei.fastPreInitPluginDiscovery.deepProbes",
                "gpom.hei.parallelPluginRegistration.enabled",
                "gpom.hei.parallelPluginRegistration.workers",
                "gpom.hei.parallelPluginRegistration.overlapSerial",
                "gpom.hei.parallelPluginRegistration.allowlist",
                "gpom.hei.parallelPluginRegistration.denylist"
        );
        writeSection(writer, "HEI Recipe Optimizations", "Exact-version HEI recipe/category fast paths and persistent wrapper caches. Caches validate input signatures before reuse.",
                "gpom.hei.jerVillagerTradeCache.enabled",
                "gpom.hei.jerVillagerTradeCache.samples",
                "gpom.hei.jerLootDropCache.enabled",
                "gpom.hei.fastForestryBottler.enabled",
                "gpom.hei.forestryBottlerRecipeCache.enabled",
                "gpom.hei.compressForestryBottlerRecipes.enabled",
                "gpom.hei.skipUnsupportedRuntimeRecipes.enabled",
                "gpom.hei.skipUnsupportedRuntimeRecipes.classes",
                "gpom.hei.fastEnderIOTank.enabled",
                "gpom.hei.compressEnderIOTankFluidRecipes.enabled",
                "gpom.hei.extraTreesLumbermillRecipeCache.enabled",
                "gpom.hei.fastThermalTransposerContainers.enabled",
                "gpom.hei.thermalTransposerContainerCache.enabled"
        );
    }

    private static void writeSection(PrintWriter writer, String title, String description, String... keys) {
        writeComment(writer, "== " + title + " ==");
        writeComment(writer, description);
        for (String key : keys) {
            writeProperty(writer, key);
        }
        writer.println();
    }

    private static void writeProperty(PrintWriter writer, String key) {
        writeComment(writer, key + " - " + propertyDescription(key));
        writer.println(key + "=" + DEFAULTS.getProperty(key, ""));
    }

    private static void writeComment(PrintWriter writer, String comment) {
        writer.println("# " + comment);
    }

    private static String propertyDescription(String key) {
        switch (key) {
            case "fml.parallel.preInit.enabled":
                return "Enables worker dispatch for FMLPreInitializationEvent handlers that pass the allowlist and denylist filters.";
            case "fml.parallel.construct.enabled":
                return "Enables worker dispatch for FMLConstructionEvent handlers that pass the allowlist and denylist filters.";
            case "fml.parallel.postInit.enabled":
                return "Enables worker dispatch for FMLPostInitializationEvent handlers that pass the allowlist and denylist filters.";
            case "fml.parallel.init.enabled":
                return "Enables worker dispatch for FMLInitializationEvent handlers that pass the allowlist and denylist filters.";
            case "fml.parallel.loadComplete.enabled":
                return "Enables worker dispatch for FMLLoadCompleteEvent handlers that pass the allowlist and denylist filters.";
            case "fml.parallel.workers":
                return "Shared worker-count fallback for all threaded FML phases. 0 lets GPOM choose a bounded value from CPU and memory.";
            case "fml.parallel.preInit.workers":
                return "Worker count for PreInit. 0 inherits fml.parallel.workers, then automatic sizing if the shared value is also 0.";
            case "fml.parallel.construct.workers":
                return "Worker count for construction. 0 inherits fml.parallel.workers, then automatic sizing if the shared value is also 0.";
            case "fml.parallel.postInit.workers":
                return "Worker count for PostInit. 0 inherits fml.parallel.workers, then automatic sizing if the shared value is also 0.";
            case "fml.parallel.init.workers":
                return "Worker count for Init. 0 inherits fml.parallel.workers, then automatic sizing if the shared value is also 0.";
            case "fml.parallel.loadComplete.workers":
                return "Worker count for LoadComplete. 0 inherits fml.parallel.workers, then automatic sizing if the shared value is also 0.";
            case "fml.parallel.construct.allowlist":
                return "Comma-separated mod ids allowed to run construction off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.construct.denylist":
                return "Comma-separated mod ids forced back to the main thread during construction; entries here override the allowlist.";
            case "fml.parallel.construct.continueOnModError":
                return "Diagnostic-only mode that records a construction failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.construct.dag.enabled":
                return "Uses dependency-aware scheduling for Construction. Serial construction handlers remain list-order barriers for safety.";
            case "fml.parallel.preInit.allowlist":
                return "Comma-separated mod ids allowed to run PreInit off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.preInit.denylist":
                return "Comma-separated mod ids forced back to the main thread during PreInit; entries here override the allowlist.";
            case "fml.parallel.preInit.continueOnModError":
                return "Diagnostic-only mode that records a PreInit failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.preInit.dag.enabled":
                return "Uses dependency-aware scheduling for PreInit instead of strict list order. Experimental until missing-registry behavior is retested.";
            case "fml.parallel.init.allowlist":
                return "Comma-separated mod ids allowed to run Init off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.init.denylist":
                return "Comma-separated mod ids forced back to the main thread during Init; entries here override the allowlist.";
            case "fml.parallel.init.continueOnModError":
                return "Diagnostic-only mode that records an Init failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.init.dag.enabled":
                return "Uses dependency-aware scheduling for Init instead of strict list order, while keeping denied handlers on the main thread.";
            case "fml.parallel.postInit.allowlist":
                return "Comma-separated mod ids allowed to run PostInit off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.postInit.denylist":
                return "Comma-separated mod ids forced back to the main thread during PostInit; entries here override the allowlist.";
            case "fml.parallel.postInit.continueOnModError":
                return "Diagnostic-only mode that records a PostInit failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.postInit.dag.enabled":
                return "Uses dependency-aware scheduling for PostInit so independent later handlers can run while main-thread-only handlers execute.";
            case "fml.parallel.loadComplete.allowlist":
                return "Comma-separated mod ids allowed to run LoadComplete off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.loadComplete.denylist":
                return "Comma-separated mod ids forced back to the main thread during LoadComplete; entries here override the allowlist.";
            case "fml.parallel.loadComplete.continueOnModError":
                return "Diagnostic-only mode that records a LoadComplete failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.loadComplete.dag.enabled":
                return "Uses dependency-aware scheduling for LoadComplete, including independent lookahead around long main-thread handlers such as HEI.";
            case "fml.parallel.registrySerialization.enabled":
                return "Serializes Forge registry writes while lifecycle workers are active to avoid HashBiMap and ForgeRegistry concurrent mutation.";
            case "fml.parallel.clientLifecycleOpenGlScan.enabled":
                return "Keeps PreInit/Init/PostInit/LoadComplete handlers on the main thread when their mod jar references LWJGL/OpenGL/Input bytecode. This preserves client thread affinity without editing denylists.";
            case "fml.parallel.autoQuarantineGlErrors.enabled":
                return "Diagnostic helper that appends catchable OpenGL thread offenders to the relevant phase denylist for the next launch.";
            case "fml.parallel.autoQuarantineGlErrors.includeRelatedMods":
                return "When auto-quarantining, also deny likely dependent/related mods so the next launch moves the whole cluster back to main thread.";
            case "gpom.construction.genericSidedProxies.denylist":
                return "Comma-separated mod ids that must use Forge's original sided-proxy injection instead of GPOM's generic proxy shortcut.";
            case "gpom.construction.genericAutomaticSubscribers.denylist":
                return "Comma-separated mod ids that must use Forge's original AutomaticEventSubscriber and EventBus.register(Class) paths instead of GPOM's generic/lazy subscriber shortcuts.";
            case "gpom.logging.enabled":
                return "Master switch for GPOM's own logger. If false, GPOM diagnostics including WorldLifecycle lines are suppressed.";
            case "gpom.logging.fmlScheduler.enabled":
                return "Enables lifecycle scheduler status lines such as worker waits, phase transitions, and serialized fallback notices.";
            case "gpom.logging.optimizationInfo.enabled":
                return "Enables one-time informational lines from optimization installers and exact-version fast paths.";
            case "gpom.logging.cacheInfo.enabled":
                return "Enables cache hit, miss, invalidation, and persistence lines for GPOM input-validated caches.";
            case "gpom.logging.asyncProbeLogs.enabled":
                return "Moves high-volume GPOM probe/profiler log writes onto a bounded daemon thread so console/file IO does not stall gameplay.";
            case "gpom.logging.asyncProbeLogs.queueSize":
                return "Maximum queued GPOM probe log lines before excess probe logs are dropped instead of blocking the caller.";
            case "gpom.vintageFix.suppressUcwModelErrorSpam":
                return "Suppresses repeated VintageFix model retrieval stack traces after replacing them with one short GPOM notice per failing model namespace.";
            case "gpom.vintageFix.skipUcwDefinitionEarlyModelLoad":
                return "Prevents VintageFix from treating Unlimited Chisel Works ucwdefs JSON files as real model JSON during early dynamic model discovery.";
            case "gpom.ctm.tolerateUnknownRenderLayer":
                return "Lets CTM ignore unknown texture metadata render-layer names such as BLOOM instead of dropping the whole metadata section.";
            case "gpom.ctm.suppressTextureMetadataErrorSpam":
                return "Replaces repeated CTM texture metadata IOException stack traces with one concise GPOM line per unique metadata failure.";
            case "gpom.cacheInvalidation.denylist":
                return "Comma-separated mod ids, jar names, or file stems excluded from GPOM cache input signatures; default ignores local AUSM dev jar churn.";
            case "gpom.startupProfiler.logs.enabled":
                return "Master switch for startup profiler log output. Timings may still be collected for UI or summaries.";
            case "gpom.startupProfiler.logs.boot.enabled":
                return "Logs early boot/profiler setup markers.";
            case "gpom.startupProfiler.logs.phaseLifecycle.enabled":
                return "Logs lifecycle phase start, end, and wall-time markers.";
            case "gpom.startupProfiler.logs.modDetails.enabled":
                return "Logs per-mod lifecycle timings and wait attribution details.";
            case "gpom.startupProfiler.logs.phaseSummary.enabled":
                return "Logs per-phase top sink summaries.";
            case "gpom.startupProfiler.logs.phaseDigest.enabled":
                return "Logs compact phase digest lines intended for quick comparison between launches.";
            case "gpom.startupProfiler.logs.memoryDetails.enabled":
                return "Logs memory snapshots attached to startup phases and selected probes.";
            case "gpom.startupProfiler.logs.probes.enabled":
                return "Logs raw high-volume probe events from targeted deep profilers.";
            case "gpom.startupProfiler.logs.probeSummary.enabled":
                return "Logs aggregated probe summaries without the full raw probe stream.";
            case "gpom.startupProfiler.logs.wallDiagnostics.enabled":
                return "Logs wall-clock diagnostics that explain dead time, waits, and scheduler under-utilization.";
            case "gpom.startupProfiler.logs.stackSamples.enabled":
                return "Logs stack-sample diagnostics for long-running startup sinks.";
            case "gpom.startupProfiler.logs.resourceLoadOrder.enabled":
                return "Logs resource/model load-order diagnostics used to locate client-side loading stalls.";
            case "gpom.startupProfiler.logs.nonFmlGaps.enabled":
                return "Logs material wall-clock gaps between Forge lifecycle events, even when raw startup profiler probes are disabled.";
            case "gpom.startupProfiler.logs.constructCriticalPath.enabled":
                return "Logs a compact Construction DAG critical-path breakdown, even when scheduler chatter and raw probes are disabled.";
            case "gpom.startupProfiler.logs.preInitCriticalPath.enabled":
                return "Logs a compact PreInitialization DAG critical-path breakdown, even when scheduler chatter and raw probes are disabled.";
            case "gpom.startupProfiler.logs.loadCompleteCriticalPath.enabled":
                return "Logs a compact LoadComplete DAG critical-path breakdown, separating true handler time from progress-bar wait labels.";
            case "gpom.startupProfiler.logs.postPreInitProbeSummary.enabled":
                return "Records and logs compact POST_PREINIT_TRANSITION probe summaries without enabling raw probe lines or broad startup profiler output.";
            case "gpom.startupProfiler.probeLogs.enabled":
                return "Compatibility alias for older configs; false also disables raw [Probe] startup lines.";
            case "gpom.startupProfiler.probeHighVolumeEventBusPosts":
                return "When true, records per-post probe totals for very high-volume EventBus events such as VintageFix model bake and CTM texture collection; keep false unless investigating those events directly.";
            case "gpom.startupProfiler.probePrefixAllowlist":
                return "Comma-separated probe name prefixes to record and log. Empty records every probe; use HEI to keep only Had Enough Items probes.";
            case "gpom.startupProfiler.topCount":
                return "Number of top mods/probes included in startup profiler summaries.";
            case "gpom.startupProfiler.postPreInitProgressBars":
                return "Shows a coarse Forge loading bar during the long post-PreInit registry transition.";
            case "gpom.startupProfiler.postPreInitProgressSteps":
                return "Step budget for the GPOM post-PreInit loading bar. Raise this if the UI reaches the maximum before the transition ends.";
            case "gpom.earlySplash.enabled":
                return "Shows GPOM's isolated early splash window before Minecraft creates its own display.";
            case "gpom.earlySplash.packName":
                return "Display label shown on the early splash window footer.";
            case "gpom.worldLoadingScreen.enabled":
                return "Shows GPOM's passive world-entry overlay during blank or early-0% world join periods.";
            case "gpom.worldLifecycleProfiler.enabled":
                return "Logs client world load, unload, and dimension-switch memory/state snapshots.";
            case "gpom.worldLifecycleProfiler.forceGcBeforeSnapshots":
                return "Runs System.gc() before delayed world lifecycle snapshots to make retained-memory trends easier to see.";
            case "gpom.worldLifecycleProfiler.delayedSnapshotMillis":
                return "Comma-separated delayed snapshot times after each world transition; include values below 30000 for fast switch testing.";
            case "gpom.worldLifecycleProfiler.deepAttribution.enabled":
                return "Logs detailed texture, resource-manager, and renderer ownership summaries for world memory leak attribution.";
            case "gpom.worldLifecycleProfiler.deepAttribution.maxEntries":
                return "Maximum entries shown in each WorldLifecycleDeep top-list; higher values increase log volume.";
            case "gpom.runtimeSinkProfiler.enabled":
                return "Enables aggregate runtime probes for world loading, gameplay ticks, frame rendering, and selected Forge event handlers.";
            case "gpom.runtimeSinkProfiler.summaryIntervalSeconds":
                return "Seconds between [RuntimeSinkSummary] top-total timing summaries.";
            case "gpom.runtimeSinkProfiler.topCount":
                return "Maximum sink entries included in each [RuntimeSinkSummary] line.";
            case "gpom.runtimeSinkProfiler.slowThresholdMillis":
                return "Elapsed-time threshold for immediate [RuntimeSink] slow lines.";
            case "gpom.runtimeSinkProfiler.immediateSlowLogs.enabled":
                return "Logs individual slow runtime sinks immediately, in addition to periodic aggregate summaries.";
            case "gpom.runtimeSinkProfiler.forgeEvents.enabled":
                return "Profiles selected high-value Forge event posts and handlers while runtime sink profiling is enabled.";
            case "gpom.runtimeSinkProfiler.forgeEvents.profileAll":
                return "Profiles every Forge event post and handler instead of only tick, world, chunk, render, and GUI events. High volume.";
            case "gpom.ae2.patternDiagnostics.enabled":
                return "Runs a temporary one-shot AE2 crafting-pattern validation scan on server overworld load. Keep false except while debugging pattern terminal failures.";
            case "gpom.ae2.patternDiagnostics.maxFailures":
                return "Maximum individual AE2 pattern diagnostic failure lines to log before suppressing additional details. Summary counts are always logged.";
            case "gpom.ae2.patternDiagnostics.logMismatchedOutputs":
                return "Logs recipes whose canonical ingredient choice matches a different recipe or output before AE2 pattern validation.";
            case "gpom.ae2.patternDiagnostics.skipRecipeFunctions":
                return "Skips CraftTweaker/RecipeStages recipes with recipe functions before AE2 PatternHelper validation, avoiding CT FATAL spam from synthetic diagnostic inventories.";
            case "gpom.jecalculation.pinnedCraftOverlay.enabled":
                return "Adds a client-only pinned Just Enough Calculation craft overlay to normal container screens, with a GPOM pin toggle on JEC's craft screen. No-ops when JEC is absent.";
            case "gpom.jecalculation.fuzzyVolatileItemNbt.enabled":
                return "Lets JEC match saved recipe outputs with volatile item NBT or Forge capability payloads, fixing calculator misses for charged/capability-backed outputs such as jetpacks.";
            case "gpom.hei.extendedCraftingLowerTierTransfer.enabled":
                return "Adds HEI transfer buttons for lower-tier ExtendedCrafting table recipes in higher-tier table GUIs, mapping the lower recipe into the centered sub-grid.";
            case "gpom.hei.draconicFusionTransfer.enabled":
                return "Adds a HEI transfer button for Draconic Evolution Fusion Crafting that stages catalyst and injector ingredients through a server-validated packet.";
            case "gpom.hei.craftableRecipesFirst.enabled":
                return "Sorts HEI recipe views so recipes whose item inputs are present in the player inventory appear first. Non-item requirements are ignored.";
            case "gpom.mainMenuStartupTime.enabled":
                return "Draws the last measured startup duration in the top-right corner of the main menu.";
            case "gpom.baubles.sideSlots.enabled":
                return "Repositions Baubles' expanded-inventory slots into a side rail and enables support hooks for extra Bauble slots. Requires Baubles.";
            case "gpom.baubles.sideSlots.visibleRows":
                return "Number of Bauble slot rows visible per side-rail page.";
            case "gpom.baubles.sideSlots.columns":
                return "Number of Bauble slot columns shown per side-rail page; page size is visibleRows * columns.";
            case "gpom.baubles.sideSlots.preferRight":
                return "When true, places the Baubles side rail to the right if there is screen space; false keeps the default left-side rail.";
            case "gpom.baubles.sideSlots.shiftRightClickEquip":
                return "Allows Shift+clicking a supported accessory stack while the side rail is open to quick-equip it through server-validated GPOM handling.";
            case "gpom.baubles.sideSlots.aether.enabled":
                return "Adds The Aether accessory slots to the same Baubles side rail when Aether Legacy is loaded. Requires gpom.baubles.sideSlots.enabled.";
            case "gpom.baubles.sideSlots.cosmeticArmor.enabled":
                return "Adds Cosmetic Armor Reworked armor slots to the same Baubles side rail when Cosmetic Armor Reworked is loaded. Requires gpom.baubles.sideSlots.enabled.";
            case "gpom.loliasm.threadSafeStatefulRegistry":
                return "Replaces LoliASM's crash-state registry with a concurrent set and prunes cleared weak references from BufferBuilder churn.";
            case "gpom.betterPortals.fixMissingNewTarget":
                return "Patches BetterPortals' legacy EntityRenderer @At(\"NEW\") mixin metadata with an explicit Frustum target on newer Mixin runtimes.";
            case "gpom.betterPortals.remapLegacyAetherBridge":
                return "Remaps BetterPortals' optional Aether bridge from the old com.legacy.aether package to the installed com.gildedgames.the_aether package when needed, including Aether's Skyroot-water portal activation path.";
            case "gpom.betterPortals.skipLegacyAetherBridgeIfMissing":
                return "Skips BetterPortals' optional Aether bridge when neither its legacy Aether target nor GPOM's remap target is available.";
            case "gpom.betterPortals.fixGuavaAddCallback":
                return "Patches BetterPortals' old two-argument Guava Futures.addCallback helper calls to the executor-taking overload required by modern Guava.";
            case "gpom.betterPortals.skipUnsafeThirdPartyTransition":
                return "Makes BetterPortals decline enhanced third-party transfers when its server view-world manager state is already unsafe, allowing the original mod transfer to continue instead of crashing.";
            case "gpom.betterPortals.cleanupClientWorlds":
                return "Reflectively resets BetterPortals' retained client view-world registry on client world handoffs and disconnects. Experimental; keep false unless debugging retained BetterPortals client worlds.";
            case "gpom.betterPortals.journeymapWaypointTeleportTransition":
                return "Routes JourneyMap cross-dimensional waypoint teleports through BetterPortals' dimension-transition handler so the configured transition animation plays. Falls back to JourneyMap's original teleport when BetterPortals is absent or declines the transfer.";
            case "gpom.betterPortals.journeymapWaypointTeleportRequireActiveView":
                return "Requires BetterPortals to already have an active non-main view for the source world before GPOM routes a JourneyMap waypoint teleport through BetterPortals. Keep true to avoid BetterPortals main-view teardown crashes on normal waypoint dimension jumps.";
            case "gpom.architecturecraft.fastShapeLighting":
                return "Uses brightness lighting instead of ArchitectureCraft's per-vertex ambient occlusion for shape chunk rebuilds. This reduces rebuild cost for dense shape builds at the cost of flatter lighting. Automatically skipped when AUSM is installed.";
            case "gpom.architecturecraft.accurateHitboxes":
                return "Replaces ArchitectureCraft shape ray tracing with a GPOM-safe collision-box ray trace and preserves the selected sub-box for outlines. No-op when ArchitectureCraft is absent.";
            case "gpom.architecturecraft.parentMaterialOcclusion.enabled":
                return "Lets ArchitectureCraft shapes answer side-render checks as their base material. Experimental; disabled by default because it can over-darken ambient occlusion on partial framed geometry.";
            case "gpom.blockcraftery.accurateHitboxes":
                return "Adds optional Blockcraftery ray-hitbox fixes for editable slants, corners, and copied-block cubes. No-op when Blockcraftery is absent.";
            case "gpom.blockcraftery.parentMaterialOcclusion.enabled":
                return "Lets Blockcraftery copied-block cubes answer side-render and side-occlusion checks as their copied material, matching adjacent copied translucent/solid block culling. Disable only if a copied block has broken side-render logic.";
            case "gpom.blockcraftery.modelRenderLayerCompat":
                return "Rewrites Blockcraftery copied-block model layer checks to use Forge canRenderInLayer. Automatically skipped when AUSM is installed, because AUSM owns shader render-layer routing.";
            case "gpom.journeymap.waypointDimensionDropup.enabled":
                return "Replaces JourneyMap's waypoint-manager dimension cycling button with a scrollable dropup selector. No-op when JourneyMap is absent.";
            case "gpom.journeymap.cleanupLeaks":
                return "Reflectively clears JourneyMap client world/task/chunk/render caches on client world unload, disconnect, and Minecraft loadWorld boundaries. No-op when JourneyMap is absent.";
            case "gpom.journeymap.cleanupLeaksOnDimensionHandoff":
                return "Also runs JourneyMap's full reflective leak cleanup during ordinary client dimension handoffs. Disabled by default because the purge is synchronous and can stall death/respawn dimension switches.";
            case "gpom.scannable.skipRedundantConfigOreCacheRebuilds":
                return "Skips Scannable's expensive ore lookup rebuild when the server sends settings identical to the client settings already applied.";
            case "gpom.enderio.repairMissingTileEntityMappings":
                return "After EnderIO load, re-registers missing vanilla TileEntity class-to-id mappings from EnderIO's own tile enum metadata. No-op when EnderIO is absent or mappings are already present.";
            case "gpom.registry.repairThaumicWondersMissingMappings":
                return "Remaps selected legacy Thaumic Wonders missing block/item mappings to currently registered replacements. Disabled by default because saved Thaumcraft/Thaumic Wonders research and progression can depend on the old ids remaining unresolved for manual review.";
            case "gpom.registry.ignoreMissingSoundEventNamespaces":
                return "Comma-separated namespaces whose stale missing SoundEvent world mappings are ignored instead of opening Forge's missing-mapping confirmation gate.";
            case "gpom.registry.failMissingBlockItemNamespaces":
                return "Comma-separated namespaces whose missing Block or Item mappings must hard-fail world load instead of allowing Forge to continue and risk saving stripped chunks.";
            case "gpom.sfm.lightweightSearchCache.enabled":
                return "Replaces SFM's login-time advanced-tooltip search index with a faster name/id/ore dictionary index for item filter search.";
            case "gpom.sfm.lightweightSearchCache.useHeiIngredients":
                return "Uses HEI's full ItemStack ingredient list as the SFM search source so item variants are preserved; false falls back to creative-tab entries.";
            case "gpom.sfm.lightweightSearchCache.workers":
                return "Worker count for GPOM's SFM search index builder. 0 chooses a conservative bounded value.";
            case "gpom.railcraftLazyItemConditions":
                return "Defers Railcraft item condition initialization until first use instead of during module setup.";
            case "gpom.railcraft.deferModuleIC2Containers":
                return "Defers Railcraft IC2 container setup that otherwise forces expensive cross-mod class initialization.";
            case "gpom.railcraft.deferModuleContainers":
                return "Defers broad Railcraft module container creation until the containers are actually requested.";
            case "gpom.railcraft.deferSelectedModuleContainers":
                return "Defers only module containers listed in gpom.railcraft.deferModuleContainerAllowlist.";
            case "gpom.railcraft.deferModuleContainerAllowlist":
                return "Comma-separated Railcraft module class names eligible for selected container deferral.";
            case "gpom.railcraft.lazyCartConfig":
                return "Defers Railcraft cart config materialization until cart settings are first queried.";
            case "gpom.astralSorcery.deferAssetLibraryReload":
                return "Registers Astral Sorcery's asset reload listener without forcing immediate texture preload during PreInit.";
            case "gpom.agricraft.fastJsonIo":
                return "Uses the AgriCraft exact-version fast JSON IO path while preserving parsed content.";
            case "gpom.agricraft.fastResourceScan":
                return "Avoids redundant AgriCraft resource scans when the same candidate set has already been resolved.";
            case "gpom.agricraft.skipJsonWriteback":
                return "Skips AgriCraft's startup rewrite of unchanged/default JSON files.";
            case "gpom.agricraft.refreshChannelsAfterBulkPlacement":
                return "Refreshes AgriCraft irrigation channel connections after bulk block placement by builder wands.";
            case "gpom.openComputersSettingsCache":
                return "Caches OpenComputers settings construction and invalidates on bundled/user config byte changes.";
            case "gpom.openComputers.fastLuaSelection":
                return "Avoids probing disabled Lua native backends before OpenComputers architecture registration.";
            case "gpom.openComputersCallProfiler":
                return "Enables targeted OpenComputers call timing probes for startup investigation.";
            case "gpom.openComputersIntegrationProfiler":
                return "Enables targeted OpenComputers integration timing probes for startup investigation.";
            case "gpom.preInitClassPrewarm.enabled":
                return "Starts the optional PreInit class prewarm sidecar for allowlisted mods.";
            case "gpom.preInitClassPrewarm.allowlist":
                return "Comma-separated mod ids allowed to prewarm classes; '*' allows every mod before other limits apply.";
            case "gpom.preInitClassPrewarm.workers":
                return "Number of sidecar class-prewarm workers. Values below 1 are clamped to 1.";
            case "gpom.preInitClassPrewarm.deferMinCompletedHandlers":
                return "Minimum completed PreInit handlers before the sidecar may begin deferred work.";
            case "gpom.preInitClassPrewarm.deferUntilSerialMillis":
                return "Minimum serial PreInit elapsed time before deferred class prewarming can start.";
            case "gpom.preInitClassPrewarm.pauseDuringSerialHandlers":
                return "Pauses sidecar prewarming while main-thread serial PreInit handlers are running.";
            case "gpom.preInitClassPrewarm.pauseDuringBlockingWaits":
                return "Pauses sidecar prewarming while the scheduler is blocked waiting for unsafe or serialized work.";
            case "gpom.preInitClassPrewarm.maxClassesPerMod":
                return "Maximum class names GPOM will consider per mod for prewarming.";
            case "gpom.preInitClassPrewarm.chunkSize":
                return "Number of class names processed per prewarm work chunk.";
            case "gpom.preInitClassPrewarm.includeAnonClasses":
                return "Includes anonymous and synthetic-looking classes in the prewarm candidate list.";
            case "gpom.preInitClassPrewarm.extraPrefixes":
                return "Extra prewarm package prefixes, formatted modid:internal/prefix|other/prefix;modid2:prefix.";
            case "gpom.preInitClassPrewarm.noInitAllowlist":
                return "Mod ids that use Class.forName(..., false) with the built-in no-static-init prefix table.";
            case "gpom.preInitClassPrewarm.noInitPrefixes":
                return "Extra no-static-init prefixes, formatted modid:internal/prefix|other/prefix;modid2:prefix.";
            case "gpom.preInitClassPrewarm.initializeClasses":
                return "Allows static initializers during scanned class prewarm for every allowlisted mod. Risky; prefer gpom.preInitClassPrewarm.initializeAllowlist for targeted testing.";
            case "gpom.preInitClassPrewarm.initializeAllowlist":
                return "Comma-separated mod ids whose scanned prewarm classes may run static initializers. Overrides the safe no-init bucket only for listed mods.";
            case "gpom.preInitClassPrewarm.explicitClasses":
                return "Explicit class names to prewarm, formatted modid:pkg.Class|other.Class;modid2:pkg.Class.";
            case "gpom.gendustryConfigCache":
                return "Caches Gendustry tuning/config parsing and invalidates when bundled or user config bytes change.";
            case "gpom.gendustryCallProfiler":
                return "Enables targeted Gendustry startup timing probes.";
            case "gpom.erebus.deferComposterRegistry":
                return "Defers Erebus composter registry materialization until first compostability query.";
            case "gpom.erebus.deferOreConfigs":
                return "Defers Erebus ore config enum materialization until an ore setting is first used.";
            case "gpom.enderio.fastSpawnerEntityValidation":
                return "Caches EnderIO powered-spawner XML entity validation during core recipe loading.";
            case "gpom.crafttweaker.fastZenRegister":
                return "Uses ASMData to register CraftTweaker ZenRegister classes without reflective annotation scans.";
            case "gpom.crafttweaker.fastZenRegister.parallelClassLoad":
                return "Defines CraftTweaker ZenRegister classes on bounded worker threads, then performs all CraftTweaker global registry writes serially in original order.";
            case "gpom.crafttweaker.fastZenRegister.classLoadWorkers":
                return "Worker count for CraftTweaker fast ZenRegister parallel class loading. 0 lets GPOM choose a conservative bounded value.";
            case "gpom.crafttweaker.fastZenRegister.deepProbes":
                return "Adds aggregate timing around CraftTweaker ZenRegister class load, mod filters, and global registry writes.";
            case "gpom.crafttweaker.lazyItemList":
                return "Defers CraftTweaker's regex item-list build until the regex item APIs are actually used.";
            case "gpom.crafttweaker.suppressFunctionTypeStdout":
                return "Removes an unconditional ZenScript function-type System.out.println that can spam ContentTweaker-heavy packs during script compile.";
            case "gpom.crafttweaker.parallelScriptParsing.enabled":
                return "Enables CraftTweaker script-loading acceleration inside each priority bucket while preserving sorted compile and execution order.";
            case "gpom.crafttweaker.parallelScriptParsing.workers":
                return "Worker count for CraftTweaker script source preloading or aggressive off-thread Zen parsing. 0 lets GPOM choose a bounded automatic value.";
            case "gpom.crafttweaker.parallelScriptParsing.allowlist":
                return "Comma-separated CraftTweaker script effective names, group names, or file names allowed to use the accelerated path; '*' allows all before denylist filtering.";
            case "gpom.crafttweaker.parallelScriptParsing.denylist":
                return "Comma-separated CraftTweaker script effective names, group names, or file names forced back to stock serial source-open and parse behavior.";
            case "gpom.crafttweaker.parallelScriptParsing.offThreadZenParse":
                return "If true, runs ZenParsedFile parsing on workers. If false, only script source reads are off-thread and Zen parsing remains on the main thread.";
            case "gpom.crafttweaker.parallelScriptParsing.suppressGlobalDebugCompileLogs":
                return "Suppresses CraftTweaker global debug-mode ZenScript compile chatter while preserving per-script debug and normal error logging.";
            case "gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts":
                return "Compiles and executes accelerated scripts once per allowed same-priority chunk while preserving script order; denylisted or execution-blocked scripts remain serial.";
            case "gpom.crafttweaker.parallelScriptParsing.deepProbes":
                return "Adds aggregated timing probes around CraftTweaker script source read, parse, compile, module creation, execution, and script load events.";
            case "gpom.nuclearcraft.fastManufactoryMetalRecipes":
                return "Uses a cached ore-name set for NuclearCraft Manufactory metal recipe registration with stock fallback.";
            case "gpom.nuclearcraft.cacheManufactoryLogCraftingResults":
                return "Caches NuclearCraft Manufactory log-to-plank crafting lookups and indexes one-input 3x3 recipe candidates before stock fallback.";
            case "gpom.nuclearcraft.skipEmptyManufactoryLogCraftingFallback":
                return "Skips the expensive stock CraftingManager fallback when GPOM's one-input NuclearCraft Manufactory log index finds no result. Experimental; disable if log-derived recipes are missing.";
            case "gpom.registry.parallelRegisterEvents.enabled":
                return "Runs selected Forge RegistryEvent.Register listeners in worker threads during the post-PreInit registry transition.";
            case "gpom.registry.parallelRegisterEvents.registries":
                return "Comma-separated registry names eligible for parallel listener dispatch; '*' allows every non-recipe register event unless recipe parallelism is explicitly enabled.";
            case "gpom.registry.parallelRegisterEvents.recipes.enabled":
                return "Allows minecraft:recipes RegistryEvent.Register listeners to run off-thread. Disabled by default because recipe registration is highly order- and visibility-sensitive.";
            case "gpom.registry.parallelRegisterEvents.workers":
                return "Worker count for parallel registry listener dispatch. 0 lets GPOM choose a bounded automatic value.";
            case "gpom.registry.parallelRegisterEvents.queuedCommit":
                return "Uses per-listener registry proxies and commits queued mutations back to Forge in original listener order.";
            case "gpom.registry.parallelRegisterEvents.proxyEventRegistry":
                return "Passes GPOM's queueing registry proxy to non-immediate registry worker listeners so normal event.getRegistry().register calls avoid ForgeRegistry's synchronized backing lock.";
            case "gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist":
                return "Comma-separated mod ids or modid@registry entries that must receive the real Forge registry object even when proxyEventRegistry is enabled; registrations still queue through the coremod hook.";
            case "gpom.registry.parallelRegisterEvents.immediateCommitRegistries":
                return "Comma-separated registry names whose worker-thread registrations are serialized into Forge immediately instead of queued until the listener batch commits; keep narrow because this reduces parallelism.";
            case "gpom.registry.parallelRegisterEvents.proxyImmediateRegistries":
                return "Passes GPOM's ordered immediate-commit registry proxy to immediate registries too, preserving visibility while avoiding direct concurrent Forge registry mutation.";
            case "gpom.registry.parallelRegisterEvents.orderedWaveRegistries":
                return "Comma-separated registry names that must keep conservative adjacent dependency waves; remove a registry here only when testing more aggressive topological scheduling.";
            case "gpom.registry.parallelRegisterEvents.immediateCommitWaitDiagnosticsMillis":
                return "Logs ordered immediate-commit coordinator state when a worker waits longer than this many milliseconds; 0 disables this diagnostic.";
            case "gpom.registry.parallelRegisterEvents.dependencyGating":
                return "Splits registry worker batches at declared mod dependency edges so dependent listeners wait for required earlier listeners.";
            case "gpom.registry.parallelRegisterEvents.allowlist":
                return "Comma-separated mod ids whose registry listeners may run off-thread; '*' allows every identified mod before denylist filtering.";
            case "gpom.registry.parallelRegisterEvents.denylist":
                return "Comma-separated mod ids forced to stay on the main thread during registry events; use modid@registry:name to deny only one registry.";
            case "gpom.registry.parallelRegisterEvents.deepDiagnostics":
                return "Adds aggregated RegistryParallel probes by registry, mod, and scheduling reason so hidden serial or worker wait costs can be identified.";
            case "gpom.preInitHighSinkCallProfiler":
                return "Enables exact-version deep PreInit profilers for current high-sink mods.";
            case "gpom.postPreInitTopCallProfiler":
                return "Enables exact-version deep post-PreInit registry-transition profilers for current high-sink handlers.";
            case "gpom.hei.recipeProgressBar.enabled":
                return "Shows a coarse GPOM progress bar while HEI consumes parsed recipes into its registry.";
            case "gpom.hei.recipeProgressBar.stepSize":
                return "Number of HEI recipe registrations between progress updates.";
            case "gpom.hei.searchWorkers":
                return "Worker count for HEI search indexing. 0 lets GPOM choose an automatic bounded pool size.";
            case "gpom.hei.fastPreInitPluginDiscovery.enabled":
                return "Predefines JEI plugin classes on bounded worker threads during HEI PreInit, then constructs plugin instances serially in JEI's original order.";
            case "gpom.hei.fastPreInitPluginDiscovery.workers":
                return "Worker count for HEI PreInit plugin class loading. 0 lets GPOM choose a conservative bounded value.";
            case "gpom.hei.fastPreInitPluginDiscovery.deepProbes":
                return "Logs aggregate timing for HEI PreInit plugin annotation scan, class loading, and plugin construction.";
            case "gpom.hei.parallelPluginRegistration.enabled":
                return "Enables experimental worker dispatch for HEI IModPlugin.register calls that match the allowlist.";
            case "gpom.hei.parallelPluginRegistration.workers":
                return "Worker count for HEI plugin registration. 0 lets GPOM choose an automatic bounded pool size.";
            case "gpom.hei.parallelPluginRegistration.overlapSerial":
                return "Starts allowlisted threaded HEI plugins before running non-allowlisted plugins on the client thread, letting tested worker work overlap conservative serial work.";
            case "gpom.hei.parallelPluginRegistration.allowlist":
                return "Comma-separated HEI plugin class names allowed to register off-thread; '*' allows all before denylist filtering.";
            case "gpom.hei.parallelPluginRegistration.denylist":
                return "Comma-separated HEI plugin class names forced to register on the main thread; entries override the allowlist.";
            case "gpom.hei.jerVillagerTradeCache.enabled":
                return "Replaces JER's repeated random trade simulations with a reduced deterministic display cache.";
            case "gpom.hei.jerVillagerTradeCache.samples":
                return "Number of simulated villager-trade samples retained for JER display range approximation.";
            case "gpom.hei.jerLootDropCache.enabled":
                return "Caches JER loot-table drop conversion results during HEI startup and returns fresh list copies to callers.";
            case "gpom.hei.fastForestryBottler.enabled":
                return "Uses Forestry's exact-version Bottler HEI fast path and skips redundant full-fluid-container fill loops.";
            case "gpom.hei.forestryBottlerRecipeCache.enabled":
                return "Persists Forestry Bottler HEI wrapper inputs/outputs and reuses them when item/fluid signatures match.";
            case "gpom.hei.compressForestryBottlerRecipes.enabled":
                return "Groups Forestry Bottler fluid-container recipes by fluid and amount to avoid tens of thousands of individual HEI recipe rows.";
            case "gpom.hei.skipUnsupportedRuntimeRecipes.enabled":
                return "Skips exact recipe classes that HEI already rejects with no handler, avoiding repeated failed lookups and error spam during recipe registry construction.";
            case "gpom.hei.skipUnsupportedRuntimeRecipes.classes":
                return "Comma-separated extra fully-qualified recipe class names to skip when HEI is asked to register them without an explicit category.";
            case "gpom.hei.fastEnderIOTank.enabled":
                return "Uses EnderIO's exact-version tank HEI fast path while preserving the full item list for mending recipes.";
            case "gpom.hei.compressEnderIOTankFluidRecipes.enabled":
                return "Groups EnderIO Tank fill/drain display recipes by fluid and amount to reduce HEI recipe registry construction cost.";
            case "gpom.hei.extraTreesLumbermillRecipeCache.enabled":
                return "Persists ExtraTrees Lumbermill wrapper tuples and reuses them when item/recipe/log signatures match.";
            case "gpom.hei.fastThermalTransposerContainers.enabled":
                return "Uses Thermal Expansion's exact-version Transposer container fast builder and drawable reuse path.";
            case "gpom.hei.thermalTransposerContainerCache.enabled":
                return "Persists Thermal Transposer fill/drain wrapper lists and reuses them when container/fluid signatures match.";
            default:
                return "No description is available for this key; keep the default unless you are testing this option.";
        }
    }

    private static void applyEarlySystemProperties() {
        copySystemPropertyIfAbsent("gpom.cacheInvalidation.denylist");
        copySystemPropertyIfAbsent("gpom.logging.asyncProbeLogs.enabled");
        copySystemPropertyIfAbsent("gpom.logging.asyncProbeLogs.queueSize");
        copySystemPropertyIfAbsent("gpom.construction.genericSidedProxies.denylist");
        copySystemPropertyIfAbsent("gpom.construction.genericAutomaticSubscribers.denylist");
        copySystemPropertyIfAbsent("gpom.vintageFix.suppressUcwModelErrorSpam");
        copySystemPropertyIfAbsent("gpom.vintageFix.skipUcwDefinitionEarlyModelLoad");
        copySystemPropertyIfAbsent("gpom.ctm.tolerateUnknownRenderLayer");
        copySystemPropertyIfAbsent("gpom.ctm.suppressTextureMetadataErrorSpam");
        copySystemPropertyIfAbsent("gpom.hei.extendedCraftingLowerTierTransfer.enabled");
        copySystemPropertyIfAbsent("gpom.hei.draconicFusionTransfer.enabled");
        copySystemPropertyIfAbsent("gpom.hei.craftableRecipesFirst.enabled");
        copySystemPropertyIfAbsent("gpom.startupProfiler.logs.constructCriticalPath.enabled");
        copySystemPropertyIfAbsent("gpom.startupProfiler.topCount");
        copySystemPropertyIfAbsent("gpom.startupProfiler.probeHighVolumeEventBusPosts");
        copySystemPropertyIfAbsent("gpom.startupProfiler.probePrefixAllowlist");
        copySystemPropertyIfAbsent("gpom.startupProfiler.postPreInitProgressBars");
        copySystemPropertyIfAbsent("gpom.startupProfiler.postPreInitProgressSteps");
        copySystemPropertyIfAbsent("gpom.railcraftLazyItemConditions");
        copySystemPropertyIfAbsent("gpom.railcraft.deferModuleIC2Containers");
        copySystemPropertyIfAbsent("gpom.railcraft.deferModuleContainers");
        copySystemPropertyIfAbsent("gpom.railcraft.deferSelectedModuleContainers");
        copySystemPropertyIfAbsent("gpom.railcraft.deferModuleContainerAllowlist");
        copySystemPropertyIfAbsent("gpom.railcraft.lazyCartConfig");
        copySystemPropertyIfAbsent("gpom.astralSorcery.deferAssetLibraryReload");
        copySystemPropertyIfAbsent("gpom.agricraft.fastJsonIo");
        copySystemPropertyIfAbsent("gpom.agricraft.fastResourceScan");
        copySystemPropertyIfAbsent("gpom.agricraft.skipJsonWriteback");
        copySystemPropertyIfAbsent("gpom.agricraft.refreshChannelsAfterBulkPlacement");
        copySystemPropertyIfAbsent("gpom.openComputersSettingsCache");
        copySystemPropertyIfAbsent("gpom.openComputers.fastLuaSelection");
        copySystemPropertyIfAbsent("gpom.openComputersCallProfiler");
        copySystemPropertyIfAbsent("gpom.openComputersIntegrationProfiler");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.enabled");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.allowlist");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.workers");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.deferMinCompletedHandlers");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.deferUntilSerialMillis");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.pauseDuringSerialHandlers");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.pauseDuringBlockingWaits");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.maxClassesPerMod");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.chunkSize");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.includeAnonClasses");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.extraPrefixes");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.noInitAllowlist");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.noInitPrefixes");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.initializeClasses");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.initializeAllowlist");
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.explicitClasses");
        copySystemPropertyIfAbsent("gpom.gendustryConfigCache");
        copySystemPropertyIfAbsent("gpom.gendustryCallProfiler");
        copySystemPropertyIfAbsent("gpom.erebus.deferComposterRegistry");
        copySystemPropertyIfAbsent("gpom.erebus.deferOreConfigs");
        copySystemPropertyIfAbsent("gpom.enderio.fastSpawnerEntityValidation");
        copySystemPropertyIfAbsent("gpom.crafttweaker.fastZenRegister");
        copySystemPropertyIfAbsent("gpom.crafttweaker.fastZenRegister.parallelClassLoad");
        copySystemPropertyIfAbsent("gpom.crafttweaker.fastZenRegister.classLoadWorkers");
        copySystemPropertyIfAbsent("gpom.crafttweaker.fastZenRegister.deepProbes");
        copySystemPropertyIfAbsent("gpom.crafttweaker.lazyItemList");
        copySystemPropertyIfAbsent("gpom.crafttweaker.suppressFunctionTypeStdout");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.enabled");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.workers");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.allowlist");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.denylist");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.offThreadZenParse");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.suppressGlobalDebugCompileLogs");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts");
        copySystemPropertyIfAbsent("gpom.crafttweaker.parallelScriptParsing.deepProbes");
        copySystemPropertyIfAbsent("gpom.nuclearcraft.fastManufactoryMetalRecipes");
        copySystemPropertyIfAbsent("gpom.nuclearcraft.cacheManufactoryLogCraftingResults");
        copySystemPropertyIfAbsent("gpom.nuclearcraft.skipEmptyManufactoryLogCraftingFallback");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.enabled");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.registries");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.recipes.enabled");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.workers");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.queuedCommit");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.proxyEventRegistry");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.immediateCommitRegistries");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.proxyImmediateRegistries");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.orderedWaveRegistries");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.immediateCommitWaitDiagnosticsMillis");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.dependencyGating");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.allowlist");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.denylist");
        copySystemPropertyIfAbsent("gpom.registry.parallelRegisterEvents.deepDiagnostics");
        copySystemPropertyIfAbsent("gpom.preInitHighSinkCallProfiler");
        copySystemPropertyIfAbsent("gpom.postPreInitTopCallProfiler");
        copySystemPropertyIfAbsent("gpom.hei.fastPreInitPluginDiscovery.enabled");
        copySystemPropertyIfAbsent("gpom.hei.fastPreInitPluginDiscovery.workers");
        copySystemPropertyIfAbsent("gpom.hei.fastPreInitPluginDiscovery.deepProbes");
        copySystemPropertyIfAbsent("gpom.hei.fastForestryBottler.enabled");
        copySystemPropertyIfAbsent("gpom.hei.forestryBottlerRecipeCache.enabled");
        copySystemPropertyIfAbsent("gpom.hei.compressForestryBottlerRecipes.enabled");
        copySystemPropertyIfAbsent("gpom.hei.skipUnsupportedRuntimeRecipes.enabled");
        copySystemPropertyIfAbsent("gpom.hei.skipUnsupportedRuntimeRecipes.classes");
        copySystemPropertyIfAbsent("gpom.hei.fastEnderIOTank.enabled");
        copySystemPropertyIfAbsent("gpom.hei.compressEnderIOTankFluidRecipes.enabled");
        copySystemPropertyIfAbsent("gpom.hei.extraTreesLumbermillRecipeCache.enabled");
        copySystemPropertyIfAbsent("gpom.hei.fastThermalTransposerContainers.enabled");
        copySystemPropertyIfAbsent("gpom.hei.thermalTransposerContainerCache.enabled");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.enabled");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.forceGcBeforeSnapshots");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.delayedSnapshotMillis");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.deepAttribution.enabled");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.deepAttribution.maxEntries");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.enabled");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.summaryIntervalSeconds");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.topCount");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.slowThresholdMillis");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.immediateSlowLogs.enabled");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.forgeEvents.enabled");
        copySystemPropertyIfAbsent("gpom.runtimeSinkProfiler.forgeEvents.profileAll");
        copySystemPropertyIfAbsent("gpom.ae2.patternDiagnostics.enabled");
        copySystemPropertyIfAbsent("gpom.ae2.patternDiagnostics.maxFailures");
        copySystemPropertyIfAbsent("gpom.ae2.patternDiagnostics.logMismatchedOutputs");
        copySystemPropertyIfAbsent("gpom.ae2.patternDiagnostics.skipRecipeFunctions");
        copySystemPropertyIfAbsent("gpom.jecalculation.pinnedCraftOverlay.enabled");
        copySystemPropertyIfAbsent("gpom.jecalculation.fuzzyVolatileItemNbt.enabled");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.enabled");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.visibleRows");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.columns");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.preferRight");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.shiftRightClickEquip");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.aether.enabled");
        copySystemPropertyIfAbsent("gpom.baubles.sideSlots.cosmeticArmor.enabled");
        copySystemPropertyIfAbsent("gpom.loliasm.threadSafeStatefulRegistry");
        copySystemPropertyIfAbsent("gpom.betterPortals.fixMissingNewTarget");
        copySystemPropertyIfAbsent("gpom.betterPortals.remapLegacyAetherBridge");
        copySystemPropertyIfAbsent("gpom.betterPortals.skipLegacyAetherBridgeIfMissing");
        copySystemPropertyIfAbsent("gpom.betterPortals.fixGuavaAddCallback");
        copySystemPropertyIfAbsent("gpom.betterPortals.skipUnsafeThirdPartyTransition");
        copySystemPropertyIfAbsent("gpom.betterPortals.cleanupClientWorlds");
        copySystemPropertyIfAbsent("gpom.betterPortals.journeymapWaypointTeleportTransition");
        copySystemPropertyIfAbsent("gpom.betterPortals.journeymapWaypointTeleportRequireActiveView");
        copySystemPropertyIfAbsent("gpom.architecturecraft.fastShapeLighting");
        copySystemPropertyIfAbsent("gpom.architecturecraft.accurateHitboxes");
        copySystemPropertyIfAbsent("gpom.architecturecraft.parentMaterialOcclusion.enabled");
        copySystemPropertyIfAbsent("gpom.blockcraftery.accurateHitboxes");
        copySystemPropertyIfAbsent("gpom.blockcraftery.parentMaterialOcclusion.enabled");
        copySystemPropertyIfAbsent("gpom.blockcraftery.modelRenderLayerCompat");
        copySystemPropertyIfAbsent("gpom.journeymap.waypointDimensionDropup.enabled");
        copySystemPropertyIfAbsent("gpom.journeymap.cleanupLeaks");
        copySystemPropertyIfAbsent("gpom.journeymap.cleanupLeaksOnDimensionHandoff");
        copySystemPropertyIfAbsent("gpom.scannable.skipRedundantConfigOreCacheRebuilds");
        copySystemPropertyIfAbsent("gpom.enderio.repairMissingTileEntityMappings");
        copySystemPropertyIfAbsent("gpom.registry.repairThaumicWondersMissingMappings");
        copySystemPropertyIfAbsent("gpom.registry.ignoreMissingSoundEventNamespaces");
        copySystemPropertyIfAbsent("gpom.registry.failMissingBlockItemNamespaces");
        copySystemPropertyIfAbsent("gpom.sfm.lightweightSearchCache.enabled");
        copySystemPropertyIfAbsent("gpom.sfm.lightweightSearchCache.useHeiIngredients");
        copySystemPropertyIfAbsent("gpom.sfm.lightweightSearchCache.workers");
    }

    private static void copySystemPropertyIfAbsent(String key) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, VALUES.getProperty(key, DEFAULTS.getProperty(key, "")));
        }
    }

    private static File configFile() {
        File gameDir = new File(System.getProperty("user.dir", "."));
        return new File(new File(gameDir, "config"), FILE_NAME);
    }

    private static boolean booleanValue(String key) {
        return Boolean.parseBoolean(VALUES.getProperty(key, DEFAULTS.getProperty(key, "false")).trim());
    }

    public static boolean parallelRegistrySerializationEnabled() {
        return booleanValue("fml.parallel.registrySerialization.enabled");
    }

    public static boolean parallelClientLifecycleOpenGlScanEnabled() {
        return booleanValue("fml.parallel.clientLifecycleOpenGlScan.enabled");
    }

    public static boolean gpomLoggingEnabled() {
        return booleanValue("gpom.logging.enabled");
    }

    public static void silenceGpomLoggersIfDisabled() {
        if (gpomLoggingEnabled()) {
            return;
        }
        for (String loggerName : GPOM_LOGGER_NAMES) {
            setLoggerLevelOff(loggerName);
        }
    }

    public static boolean fmlSchedulerLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.logging.fmlScheduler.enabled");
    }

    public static boolean optimizationInfoLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.logging.optimizationInfo.enabled");
    }

    public static boolean cacheInfoLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.logging.cacheInfo.enabled");
    }

    public static Set<String> cacheInvalidationDenylist() {
        return setValue("gpom.cacheInvalidation.denylist");
    }

    public static boolean asyncProbeLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.logging.asyncProbeLogs.enabled");
    }

    public static int asyncProbeLogQueueSize() {
        return intValue("gpom.logging.asyncProbeLogs.queueSize", 8192);
    }

    public static boolean startupProfilerLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.startupProfiler.logs.enabled");
    }

    public static boolean startupProfilerBootLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.boot.enabled");
    }

    public static boolean startupProfilerPhaseLifecycleLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.phaseLifecycle.enabled");
    }

    public static boolean startupProfilerModDetailsLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.modDetails.enabled");
    }

    public static boolean startupProfilerPhaseSummaryLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.phaseSummary.enabled");
    }

    public static boolean startupProfilerPhaseDigestLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.phaseDigest.enabled");
    }

    public static boolean startupProfilerMemoryDetailsLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.memoryDetails.enabled");
    }

    public static boolean startupProfilerProbeLogsEnabled() {
        return startupProfilerLogsEnabled()
                && booleanValue("gpom.startupProfiler.logs.probes.enabled")
                && booleanValue("gpom.startupProfiler.probeLogs.enabled");
    }

    public static boolean startupProfilerProbeSummaryLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.probeSummary.enabled");
    }

    public static boolean startupProfilerWallDiagnosticsLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.wallDiagnostics.enabled");
    }

    public static boolean startupProfilerStackSampleLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.stackSamples.enabled");
    }

    public static boolean startupProfilerResourceLoadOrderLogsEnabled() {
        return startupProfilerLogsEnabled() && booleanValue("gpom.startupProfiler.logs.resourceLoadOrder.enabled");
    }

    public static boolean startupProfilerNonFmlGapLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.startupProfiler.logs.nonFmlGaps.enabled");
    }

    public static boolean startupProfilerConstructCriticalPathLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.startupProfiler.logs.constructCriticalPath.enabled");
    }

    public static boolean startupProfilerPreInitCriticalPathLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.startupProfiler.logs.preInitCriticalPath.enabled");
    }

    public static boolean startupProfilerLoadCompleteCriticalPathLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.startupProfiler.logs.loadCompleteCriticalPath.enabled");
    }

    public static boolean startupProfilerPostPreInitProbeSummaryLogsEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.startupProfiler.logs.postPreInitProbeSummary.enabled");
    }

    public static boolean startupProfilerHighVolumeEventBusPostProbesEnabled() {
        return booleanValue("gpom.startupProfiler.probeHighVolumeEventBusPosts");
    }

    public static boolean startupProfilerPostPreInitProgressBarsEnabled() {
        return booleanValue("gpom.startupProfiler.postPreInitProgressBars");
    }

    public static int startupProfilerPostPreInitProgressSteps() {
        return Math.max(18, intValue("gpom.startupProfiler.postPreInitProgressSteps", 96));
    }

    public static boolean earlySplashEnabled() {
        return booleanValue("gpom.earlySplash.enabled");
    }

    public static String earlySplashPackName() {
        return stringValue("gpom.earlySplash.packName", "Minecraft");
    }

    public static boolean worldLoadingScreenEnabled() {
        return booleanValue("gpom.worldLoadingScreen.enabled");
    }

    public static boolean worldLifecycleProfilerEnabled() {
        return booleanValue("gpom.worldLifecycleProfiler.enabled");
    }

    public static boolean worldLifecycleProfilerForceGcBeforeSnapshots() {
        return booleanValue("gpom.worldLifecycleProfiler.forceGcBeforeSnapshots");
    }

    public static String worldLifecycleProfilerDelayedSnapshotMillis() {
        return stringValue("gpom.worldLifecycleProfiler.delayedSnapshotMillis", "2000,10000,25000");
    }

    public static boolean worldLifecycleProfilerDeepAttributionEnabled() {
        return booleanValue("gpom.worldLifecycleProfiler.deepAttribution.enabled");
    }

    public static int worldLifecycleProfilerDeepAttributionMaxEntries() {
        return intValue("gpom.worldLifecycleProfiler.deepAttribution.maxEntries", 8);
    }

    public static boolean runtimeSinkProfilerEnabled() {
        return gpomLoggingEnabled() && booleanValue("gpom.runtimeSinkProfiler.enabled");
    }

    public static int runtimeSinkProfilerSummaryIntervalSeconds() {
        return Math.max(1, intValue("gpom.runtimeSinkProfiler.summaryIntervalSeconds", 10));
    }

    public static int runtimeSinkProfilerTopCount() {
        return Math.max(1, intValue("gpom.runtimeSinkProfiler.topCount", 12));
    }

    public static int runtimeSinkProfilerSlowThresholdMillis() {
        return Math.max(1, intValue("gpom.runtimeSinkProfiler.slowThresholdMillis", 50));
    }

    public static boolean runtimeSinkProfilerImmediateSlowLogsEnabled() {
        return booleanValue("gpom.runtimeSinkProfiler.immediateSlowLogs.enabled");
    }

    public static boolean runtimeSinkProfilerForgeEventsEnabled() {
        return booleanValue("gpom.runtimeSinkProfiler.forgeEvents.enabled");
    }

    public static boolean runtimeSinkProfilerAllForgeEventsEnabled() {
        return booleanValue("gpom.runtimeSinkProfiler.forgeEvents.profileAll");
    }

    public static boolean sfmLightweightSearchCacheEnabled() {
        return booleanValue("gpom.sfm.lightweightSearchCache.enabled");
    }

    public static boolean sfmLightweightSearchCacheUseHeiIngredientsEnabled() {
        return booleanValue("gpom.sfm.lightweightSearchCache.useHeiIngredients");
    }

    public static int sfmLightweightSearchCacheWorkers() {
        return Math.max(0, intValue("gpom.sfm.lightweightSearchCache.workers", 0));
    }

    public static boolean ae2PatternDiagnosticsEnabled() {
        return booleanValue("gpom.ae2.patternDiagnostics.enabled");
    }

    public static int ae2PatternDiagnosticsMaxFailures() {
        return Math.max(0, intValue("gpom.ae2.patternDiagnostics.maxFailures", 200));
    }

    public static boolean ae2PatternDiagnosticsLogMismatchedOutputs() {
        return booleanValue("gpom.ae2.patternDiagnostics.logMismatchedOutputs");
    }

    public static boolean ae2PatternDiagnosticsSkipRecipeFunctions() {
        return booleanValue("gpom.ae2.patternDiagnostics.skipRecipeFunctions");
    }

    public static boolean jecalculationPinnedCraftOverlayEnabled() {
        return booleanValue("gpom.jecalculation.pinnedCraftOverlay.enabled");
    }

    public static boolean jecalculationFuzzyVolatileItemNbtEnabled() {
        return booleanValue("gpom.jecalculation.fuzzyVolatileItemNbt.enabled");
    }

    public static boolean mainMenuStartupTimeEnabled() {
        return booleanValue("gpom.mainMenuStartupTime.enabled");
    }

    public static boolean baublesSideSlotsEnabled() {
        return booleanValue("gpom.baubles.sideSlots.enabled");
    }

    public static int baublesSideSlotsVisibleRows() {
        return Math.max(3, Math.min(12, intValue("gpom.baubles.sideSlots.visibleRows", 7)));
    }

    public static int baublesSideSlotsColumns() {
        return Math.max(1, Math.min(6, intValue("gpom.baubles.sideSlots.columns", 2)));
    }

    public static boolean baublesSideSlotsPreferRight() {
        return booleanValue("gpom.baubles.sideSlots.preferRight");
    }

    public static boolean baublesSideSlotsShiftRightClickEquipEnabled() {
        return baublesSideSlotsEnabled() && booleanValue("gpom.baubles.sideSlots.shiftRightClickEquip");
    }

    public static boolean baublesSideSlotsAetherEnabled() {
        return baublesSideSlotsEnabled() && booleanValue("gpom.baubles.sideSlots.aether.enabled");
    }

    public static boolean baublesSideSlotsCosmeticArmorEnabled() {
        return baublesSideSlotsEnabled() && booleanValue("gpom.baubles.sideSlots.cosmeticArmor.enabled");
    }

    public static boolean loliAsmThreadSafeStatefulRegistryEnabled() {
        return booleanValue("gpom.loliasm.threadSafeStatefulRegistry");
    }

    public static boolean betterPortalsMissingNewTargetFixEnabled() {
        return booleanValue("gpom.betterPortals.fixMissingNewTarget");
    }

    public static boolean betterPortalsRemapLegacyAetherBridgeEnabled() {
        return booleanValue("gpom.betterPortals.remapLegacyAetherBridge");
    }

    public static boolean betterPortalsSkipLegacyAetherBridgeIfMissingEnabled() {
        return booleanValue("gpom.betterPortals.skipLegacyAetherBridgeIfMissing");
    }

    public static boolean betterPortalsGuavaAddCallbackFixEnabled() {
        return booleanValue("gpom.betterPortals.fixGuavaAddCallback");
    }

    public static boolean betterPortalsSkipUnsafeThirdPartyTransitionEnabled() {
        return booleanValue("gpom.betterPortals.skipUnsafeThirdPartyTransition");
    }

    public static boolean betterPortalsCleanupClientWorldsEnabled() {
        return booleanValue("gpom.betterPortals.cleanupClientWorlds");
    }

    public static boolean betterPortalsJourneyMapWaypointTeleportTransitionEnabled() {
        return booleanValue("gpom.betterPortals.journeymapWaypointTeleportTransition");
    }

    public static boolean betterPortalsJourneyMapWaypointTeleportRequireActiveViewEnabled() {
        return booleanValue("gpom.betterPortals.journeymapWaypointTeleportRequireActiveView");
    }

    public static boolean agriCraftRefreshChannelsAfterBulkPlacementEnabled() {
        return booleanValue("gpom.agricraft.refreshChannelsAfterBulkPlacement");
    }

    public static boolean architectureCraftFastShapeLightingEnabled() {
        return booleanValue("gpom.architecturecraft.fastShapeLighting");
    }

    public static boolean architectureCraftAccurateHitboxesEnabled() {
        return booleanValue("gpom.architecturecraft.accurateHitboxes");
    }

    public static boolean architectureCraftParentMaterialOcclusionEnabled() {
        return booleanValue("gpom.architecturecraft.parentMaterialOcclusion.enabled");
    }

    public static boolean blockcrafteryAccurateHitboxesEnabled() {
        return booleanValue("gpom.blockcraftery.accurateHitboxes");
    }

    public static boolean blockcrafteryParentMaterialOcclusionEnabled() {
        return booleanValue("gpom.blockcraftery.parentMaterialOcclusion.enabled");
    }

    public static boolean blockcrafteryModelRenderLayerCompatEnabled() {
        return booleanValue("gpom.blockcraftery.modelRenderLayerCompat");
    }

    public static boolean journeyMapCleanupLeaksEnabled() {
        return booleanValue("gpom.journeymap.cleanupLeaks");
    }

    public static boolean journeyMapCleanupLeaksOnDimensionHandoffEnabled() {
        return booleanValue("gpom.journeymap.cleanupLeaksOnDimensionHandoff");
    }

    public static boolean scannableSkipRedundantConfigOreCacheRebuildsEnabled() {
        return booleanValue("gpom.scannable.skipRedundantConfigOreCacheRebuilds");
    }

    public static boolean journeyMapWaypointDimensionDropupEnabled() {
        return booleanValue("gpom.journeymap.waypointDimensionDropup.enabled");
    }

    public static boolean enderIOMissingTileEntityMappingRepairEnabled() {
        return booleanValue("gpom.enderio.repairMissingTileEntityMappings");
    }

    public static boolean registryRepairThaumicWondersMissingMappingsEnabled() {
        return booleanValue("gpom.registry.repairThaumicWondersMissingMappings");
    }

    public static Set<String> registryIgnoredMissingSoundEventNamespaces() {
        return setValue("gpom.registry.ignoreMissingSoundEventNamespaces");
    }

    public static Set<String> registryFailMissingBlockItemNamespaces() {
        return setValue("gpom.registry.failMissingBlockItemNamespaces");
    }

    public static boolean preInitClassPrewarmEnabled() {
        return booleanValue("gpom.preInitClassPrewarm.enabled");
    }

    public static Set<String> preInitClassPrewarmAllowlist() {
        return setValue("gpom.preInitClassPrewarm.allowlist");
    }

    public static int preInitClassPrewarmWorkers() {
        return Math.max(1, intValue("gpom.preInitClassPrewarm.workers", 1));
    }

    public static int preInitClassPrewarmDeferMinCompletedHandlers() {
        return Math.max(0, intValue("gpom.preInitClassPrewarm.deferMinCompletedHandlers", 32));
    }

    public static long preInitClassPrewarmDeferUntilSerialMillis() {
        return Math.max(0, intValue("gpom.preInitClassPrewarm.deferUntilSerialMillis", 1000));
    }

    public static boolean preInitClassPrewarmPauseDuringSerialHandlers() {
        return booleanValue("gpom.preInitClassPrewarm.pauseDuringSerialHandlers");
    }

    public static boolean preInitClassPrewarmPauseDuringBlockingWaits() {
        return booleanValue("gpom.preInitClassPrewarm.pauseDuringBlockingWaits");
    }

    public static int preInitClassPrewarmMaxClassesPerMod() {
        return Math.max(1, intValue("gpom.preInitClassPrewarm.maxClassesPerMod", 384));
    }

    public static int preInitClassPrewarmChunkSize() {
        return Math.max(1, intValue("gpom.preInitClassPrewarm.chunkSize", 32));
    }

    public static boolean preInitClassPrewarmIncludeAnonClasses() {
        return booleanValue("gpom.preInitClassPrewarm.includeAnonClasses");
    }

    public static String preInitClassPrewarmExtraPrefixes() {
        return stringValue("gpom.preInitClassPrewarm.extraPrefixes", "");
    }

    public static Set<String> preInitClassPrewarmNoInitAllowlist() {
        return setValue("gpom.preInitClassPrewarm.noInitAllowlist");
    }

    public static String preInitClassPrewarmNoInitPrefixes() {
        return stringValue("gpom.preInitClassPrewarm.noInitPrefixes", "");
    }

    public static boolean preInitClassPrewarmInitializeClasses() {
        return booleanValue("gpom.preInitClassPrewarm.initializeClasses");
    }

    public static Set<String> preInitClassPrewarmInitializeAllowlist() {
        return setValue("gpom.preInitClassPrewarm.initializeAllowlist");
    }

    public static String preInitClassPrewarmExplicitClasses() {
        return stringValue("gpom.preInitClassPrewarm.explicitClasses", "");
    }

    public static boolean heiRecipeProgressBarEnabled() {
        return booleanValue("gpom.hei.recipeProgressBar.enabled");
    }

    public static int heiRecipeProgressBarStepSize() {
        return Math.max(1, intValue("gpom.hei.recipeProgressBar.stepSize", 256));
    }

    public static int heiSearchWorkers() {
        return intValue("gpom.hei.searchWorkers", 0);
    }

    public static boolean heiParallelPluginRegistrationEnabled() {
        return booleanValue("gpom.hei.parallelPluginRegistration.enabled");
    }

    public static int heiParallelPluginRegistrationWorkers() {
        return intValue("gpom.hei.parallelPluginRegistration.workers", 0);
    }

    public static boolean heiParallelPluginRegistrationOverlapSerialEnabled() {
        return booleanValue("gpom.hei.parallelPluginRegistration.overlapSerial");
    }

    public static Set<String> heiParallelPluginRegistrationAllowlist() {
        return setValue("gpom.hei.parallelPluginRegistration.allowlist");
    }

    public static Set<String> heiParallelPluginRegistrationDenylist() {
        return setValue("gpom.hei.parallelPluginRegistration.denylist");
    }

    public static boolean heiJerVillagerTradeCacheEnabled() {
        return booleanValue("gpom.hei.jerVillagerTradeCache.enabled");
    }

    public static int heiJerVillagerTradeCacheSamples() {
        return Math.max(1, Math.min(100, intValue("gpom.hei.jerVillagerTradeCache.samples", 32)));
    }

    public static boolean heiJerLootDropCacheEnabled() {
        return booleanValue("gpom.hei.jerLootDropCache.enabled");
    }

    public static boolean nuclearCraftFastManufactoryMetalRecipesEnabled() {
        return booleanValue("gpom.nuclearcraft.fastManufactoryMetalRecipes");
    }

    public static boolean nuclearCraftCacheManufactoryLogCraftingResultsEnabled() {
        return booleanValue("gpom.nuclearcraft.cacheManufactoryLogCraftingResults");
    }

    public static boolean nuclearCraftSkipEmptyManufactoryLogCraftingFallbackEnabled() {
        return booleanValue("gpom.nuclearcraft.skipEmptyManufactoryLogCraftingFallback");
    }

    public static boolean registryParallelRegisterEventsEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.enabled");
    }

    public static Set<String> registryParallelRegisterEventsRegistries() {
        return setValue("gpom.registry.parallelRegisterEvents.registries");
    }

    public static boolean registryParallelRegisterEventsRecipesEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.recipes.enabled");
    }

    public static int registryParallelRegisterEventsWorkers() {
        return intValue("gpom.registry.parallelRegisterEvents.workers", 0);
    }

    public static boolean registryParallelRegisterEventsQueuedCommitEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.queuedCommit");
    }

    public static boolean registryParallelRegisterEventsProxyEventRegistryEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.proxyEventRegistry");
    }

    public static Set<String> registryParallelRegisterEventsProxyEventRegistryDenylist() {
        return setValue("gpom.registry.parallelRegisterEvents.proxyEventRegistryDenylist");
    }

    public static Set<String> registryParallelRegisterEventsImmediateCommitRegistries() {
        return setValue("gpom.registry.parallelRegisterEvents.immediateCommitRegistries");
    }

    public static boolean registryParallelRegisterEventsProxyImmediateRegistriesEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.proxyImmediateRegistries");
    }

    public static Set<String> registryParallelRegisterEventsOrderedWaveRegistries() {
        return setValue("gpom.registry.parallelRegisterEvents.orderedWaveRegistries");
    }

    public static int registryParallelRegisterEventsImmediateCommitWaitDiagnosticsMillis() {
        return intValue("gpom.registry.parallelRegisterEvents.immediateCommitWaitDiagnosticsMillis", 5000);
    }

    public static boolean registryParallelRegisterEventsDependencyGatingEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.dependencyGating");
    }

    public static Set<String> registryParallelRegisterEventsAllowlist() {
        return setValue("gpom.registry.parallelRegisterEvents.allowlist");
    }

    public static Set<String> registryParallelRegisterEventsDenylist() {
        return setValue("gpom.registry.parallelRegisterEvents.denylist");
    }

    public static boolean registryParallelRegisterEventsDeepDiagnosticsEnabled() {
        return booleanValue("gpom.registry.parallelRegisterEvents.deepDiagnostics");
    }

    public static boolean craftTweakerLazyItemListEnabled() {
        return booleanValue("gpom.crafttweaker.lazyItemList");
    }

    public static boolean craftTweakerParallelScriptParsingEnabled() {
        return booleanValue("gpom.crafttweaker.parallelScriptParsing.enabled");
    }

    public static int craftTweakerParallelScriptParsingWorkers() {
        return intValue("gpom.crafttweaker.parallelScriptParsing.workers", 0);
    }

    public static Set<String> craftTweakerParallelScriptParsingAllowlist() {
        return setValue("gpom.crafttweaker.parallelScriptParsing.allowlist");
    }

    public static Set<String> craftTweakerParallelScriptParsingDenylist() {
        return setValue("gpom.crafttweaker.parallelScriptParsing.denylist");
    }

    public static boolean craftTweakerParallelScriptParsingOffThreadZenParseEnabled() {
        return booleanValue("gpom.crafttweaker.parallelScriptParsing.offThreadZenParse");
    }

    public static boolean heiFastForestryBottlerEnabled() {
        return booleanValue("gpom.hei.fastForestryBottler.enabled");
    }

    public static boolean heiForestryBottlerRecipeCacheEnabled() {
        return booleanValue("gpom.hei.forestryBottlerRecipeCache.enabled");
    }

    public static boolean heiCompressForestryBottlerRecipesEnabled() {
        return booleanValue("gpom.hei.compressForestryBottlerRecipes.enabled");
    }

    public static boolean heiFastEnderIOTankEnabled() {
        return booleanValue("gpom.hei.fastEnderIOTank.enabled");
    }

    public static boolean heiCompressEnderIOTankFluidRecipesEnabled() {
        return booleanValue("gpom.hei.compressEnderIOTankFluidRecipes.enabled");
    }

    public static boolean heiExtraTreesLumbermillRecipeCacheEnabled() {
        return booleanValue("gpom.hei.extraTreesLumbermillRecipeCache.enabled");
    }

    public static boolean heiFastThermalTransposerContainersEnabled() {
        return booleanValue("gpom.hei.fastThermalTransposerContainers.enabled");
    }

    public static boolean heiThermalTransposerContainerCacheEnabled() {
        return booleanValue("gpom.hei.thermalTransposerContainerCache.enabled");
    }

    private static int intValue(String key, int fallback) {
        String raw = VALUES.getProperty(key, Integer.toString(fallback)).trim();
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Invalid integer {}={} ; using {}", key, raw, fallback);
            return fallback;
        }
    }

    private static String stringValue(String key, String fallback) {
        String raw = VALUES.getProperty(key, DEFAULTS.getProperty(key, fallback));
        if (raw == null) {
            return fallback;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static Set<String> setValue(String key) {
        return SET_VALUES.computeIfAbsent(key, ignored ->
                Collections.unmodifiableSet(parseSet(VALUES.getProperty(key, DEFAULTS.getProperty(key, ""))))
        );
    }

    private static Set<String> parseSet(String raw) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null) {
            return values;
        }
        for (String part : raw.split(",")) {
            String value = part.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static void setLoggerLevelOff(String loggerName) {
        try {
            Class<?> levelClass = Class.forName("org.apache.logging.log4j.Level");
            Object offLevel = levelClass.getField("OFF").get(null);
            Class<?> configuratorClass = Class.forName("org.apache.logging.log4j.core.config.Configurator");
            configuratorClass.getMethod("setLevel", String.class, levelClass).invoke(null, loggerName, offLevel);
        } catch (Throwable ignored) {
            // Logging controls must never affect startup.
        }
    }

    public static synchronized boolean appendCsvValues(String key, Collection<String> additions) {
        if (key == null || key.trim().isEmpty() || additions == null || additions.isEmpty()) {
            return false;
        }

        File file = configFile();
        ensureDefaultFile(file);

        List<String> lines = new ArrayList<>();
        try {
            if (file.isFile()) {
                lines.addAll(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
            }
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to read {} while appending {}; using loaded values", file, key, exception);
        }

        String prefix = key + "=";
        String fileValue = null;
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                fileValue = line.substring(prefix.length());
                break;
            }
        }

        Set<String> values = new LinkedHashSet<>(fileValue == null ? setValue(key) : parseSet(fileValue));
        int before = values.size();
        for (String addition : additions) {
            String value = addition == null ? "" : addition.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty() && !"*".equals(value)) {
                values.add(value);
            }
        }
        if (values.size() == before) {
            return false;
        }

        String updated = String.join(",", values);
        boolean replaced = false;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).startsWith(prefix)) {
                lines.set(index, prefix + updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isEmpty()) {
                lines.add("");
            }
            lines.add(prefix + updated);
        }

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
            VALUES.setProperty(key, updated);
            SET_VALUES.put(key, Collections.unmodifiableSet(parseSet(updated)));
            return true;
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to write {} while appending {}", file, key, exception);
            return false;
        }
    }
}
