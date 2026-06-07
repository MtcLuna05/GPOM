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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class GpomEarlyConfig {
    private static final String FILE_NAME = "gpom-early.properties";
    private static final String[] GPOM_LOGGER_NAMES = {
            Reference.MOD_NAME,
            "GPOM Early Splash"
    };
    private static final Properties DEFAULTS = new Properties();
    private static final Properties VALUES = new Properties();

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
        DEFAULTS.setProperty("fml.parallel.construct.denylist", "");
        DEFAULTS.setProperty("fml.parallel.construct.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.preInit.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.preInit.denylist", "");
        DEFAULTS.setProperty("fml.parallel.preInit.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.preInit.dag.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.init.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.init.denylist", "");
        DEFAULTS.setProperty("fml.parallel.init.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.postInit.allowlist", "*");
        DEFAULTS.setProperty("fml.parallel.postInit.denylist", "crafttweaker,cofhcore,journeymap,thebetweenlands,iceandfire,scannable,randomthings,nuclearcraft,topaddons,thaumicaugmentation");
        DEFAULTS.setProperty("fml.parallel.postInit.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.loadComplete.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.loadComplete.denylist", "");
        DEFAULTS.setProperty("fml.parallel.loadComplete.continueOnModError", "false");
        DEFAULTS.setProperty("fml.parallel.registrySerialization.enabled", "true");
        DEFAULTS.setProperty("fml.parallel.autoQuarantineGlErrors.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.autoQuarantineGlErrors.includeRelatedMods", "true");
        DEFAULTS.setProperty("gpom.logging.enabled", "true");
        DEFAULTS.setProperty("gpom.logging.fmlScheduler.enabled", "true");
        DEFAULTS.setProperty("gpom.logging.optimizationInfo.enabled", "true");
        DEFAULTS.setProperty("gpom.logging.cacheInfo.enabled", "true");
        DEFAULTS.setProperty("gpom.logging.asyncProbeLogs.enabled", "true");
        DEFAULTS.setProperty("gpom.logging.asyncProbeLogs.queueSize", "8192");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.boot.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.phaseLifecycle.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.modDetails.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.phaseSummary.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.phaseDigest.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.memoryDetails.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.probes.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.probeSummary.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.wallDiagnostics.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.stackSamples.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.logs.resourceLoadOrder.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.probeLogs.enabled", "true");
        DEFAULTS.setProperty("gpom.startupProfiler.topCount", "40");
        DEFAULTS.setProperty("gpom.earlySplash.enabled", "false");
        DEFAULTS.setProperty("gpom.earlySplash.packName", "Minecraft");
        DEFAULTS.setProperty("gpom.worldLoadingScreen.enabled", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.enabled", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.forceGcBeforeSnapshots", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.delayedSnapshotMillis", "2000,10000,25000");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.deepAttribution.enabled", "false");
        DEFAULTS.setProperty("gpom.worldLifecycleProfiler.deepAttribution.maxEntries", "8");
        DEFAULTS.setProperty("gpom.mainMenuStartupTime.enabled", "false");
        DEFAULTS.setProperty("gpom.loliasm.threadSafeStatefulRegistry", "true");
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
        DEFAULTS.setProperty("gpom.preInitClassPrewarm.explicitClasses", "");
        DEFAULTS.setProperty("gpom.gendustryConfigCache", "true");
        DEFAULTS.setProperty("gpom.gendustryCallProfiler", "false");
        DEFAULTS.setProperty("gpom.erebus.deferComposterRegistry", "true");
        DEFAULTS.setProperty("gpom.erebus.deferOreConfigs", "true");
        DEFAULTS.setProperty("gpom.enderio.fastSpawnerEntityValidation", "true");
        DEFAULTS.setProperty("gpom.crafttweaker.fastZenRegister", "false");
        DEFAULTS.setProperty("gpom.preInitHighSinkCallProfiler", "true");
        DEFAULTS.setProperty("gpom.hei.recipeProgressBar.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.recipeProgressBar.stepSize", "256");
        DEFAULTS.setProperty("gpom.hei.searchWorkers", "0");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.enabled", "false");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.workers", "0");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.allowlist", "");
        DEFAULTS.setProperty("gpom.hei.parallelPluginRegistration.denylist", "");
        DEFAULTS.setProperty("gpom.hei.jerVillagerTradeCache.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.jerVillagerTradeCache.samples", "32");
        DEFAULTS.setProperty("gpom.hei.fastForestryBottler.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.forestryBottlerRecipeCache.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.fastEnderIOTank.enabled", "true");
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

    public static Set<String> parallelInitAllowlist() {
        return setValue("fml.parallel.init.allowlist");
    }

    public static Set<String> parallelInitDenylist() {
        return setValue("fml.parallel.init.denylist");
    }

    public static boolean parallelInitContinueOnModError() {
        return booleanValue("fml.parallel.init.continueOnModError");
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

    public static Set<String> parallelLoadCompleteAllowlist() {
        return setValue("fml.parallel.loadComplete.allowlist");
    }

    public static Set<String> parallelLoadCompleteDenylist() {
        return setValue("fml.parallel.loadComplete.denylist");
    }

    public static boolean parallelLoadCompleteContinueOnModError() {
        return booleanValue("fml.parallel.loadComplete.continueOnModError");
    }

    private static void load() {
        File file = configFile();
        ensureDefaultFile(file);
        if (!file.isFile()) {
            return;
        }

        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            VALUES.load(input);
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to load {}; using defaults", file, exception);
        }
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
                "fml.parallel.construct.continueOnModError"
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
                "fml.parallel.init.continueOnModError"
        );
        writeSection(writer, "PostInit Parallelism", "Controls FMLPostInitializationEvent worker dispatch. Usually safer than PreInit/Init, but recipe mutation and render integration still need pack validation.",
                "fml.parallel.postInit.enabled",
                "fml.parallel.postInit.workers",
                "fml.parallel.postInit.allowlist",
                "fml.parallel.postInit.denylist",
                "fml.parallel.postInit.continueOnModError"
        );
        writeSection(writer, "LoadComplete Parallelism", "Controls FMLLoadCompleteEvent worker dispatch. This is the safest lifecycle phase, but HEI/JER and client-global mods can still serialize the wall time.",
                "fml.parallel.loadComplete.enabled",
                "fml.parallel.loadComplete.workers",
                "fml.parallel.loadComplete.allowlist",
                "fml.parallel.loadComplete.denylist",
                "fml.parallel.loadComplete.continueOnModError"
        );
        writeSection(writer, "Thread Safety Guards", "Global safety switches used by the lifecycle scheduler while phases are running in workers.",
                "fml.parallel.registrySerialization.enabled",
                "fml.parallel.autoQuarantineGlErrors.enabled",
                "fml.parallel.autoQuarantineGlErrors.includeRelatedMods"
        );
        writeSection(writer, "GPOM Logging", "Controls GPOM's own logger and high-volume categories. Keep the root logger enabled when diagnostics such as world lifecycle snapshots are needed.",
                "gpom.logging.enabled",
                "gpom.logging.fmlScheduler.enabled",
                "gpom.logging.optimizationInfo.enabled",
                "gpom.logging.cacheInfo.enabled",
                "gpom.logging.asyncProbeLogs.enabled",
                "gpom.logging.asyncProbeLogs.queueSize"
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
                "gpom.startupProfiler.probeLogs.enabled",
                "gpom.startupProfiler.topCount"
        );
        writeSection(writer, "Client Loading UI", "Optional client-only UI additions for early startup, world entry, and the main menu startup time display.",
                "gpom.earlySplash.enabled",
                "gpom.earlySplash.packName",
                "gpom.worldLoadingScreen.enabled",
                "gpom.mainMenuStartupTime.enabled"
        );
        writeSection(writer, "World Lifecycle Profiling", "World load, unload, and dimension-switch diagnostics for tracking retained client state and memory leaks.",
                "gpom.worldLifecycleProfiler.enabled",
                "gpom.worldLifecycleProfiler.forceGcBeforeSnapshots",
                "gpom.worldLifecycleProfiler.delayedSnapshotMillis",
                "gpom.worldLifecycleProfiler.deepAttribution.enabled",
                "gpom.worldLifecycleProfiler.deepAttribution.maxEntries"
        );
        writeSection(writer, "Compatibility Fixes", "Small exact-version safety fixes for known thread-safety or lifecycle issues exposed by modern render/loading stacks.",
                "gpom.loliasm.threadSafeStatefulRegistry"
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
                "gpom.preInitHighSinkCallProfiler"
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
                "gpom.preInitClassPrewarm.explicitClasses"
        );
        writeSection(writer, "HEI Progress And Search", "HEI client startup helpers that keep recipe ingestion visible and replace the single search worker with a bounded pool.",
                "gpom.hei.recipeProgressBar.enabled",
                "gpom.hei.recipeProgressBar.stepSize",
                "gpom.hei.searchWorkers"
        );
        writeSection(writer, "HEI Plugin Parallelism", "Experimental worker dispatch for allowlisted HEI plugin registration calls. Empty allowlist means no plugin registration is threaded.",
                "gpom.hei.parallelPluginRegistration.enabled",
                "gpom.hei.parallelPluginRegistration.workers",
                "gpom.hei.parallelPluginRegistration.allowlist",
                "gpom.hei.parallelPluginRegistration.denylist"
        );
        writeSection(writer, "HEI Recipe Optimizations", "Exact-version HEI recipe/category fast paths and persistent wrapper caches. Caches validate input signatures before reuse.",
                "gpom.hei.jerVillagerTradeCache.enabled",
                "gpom.hei.jerVillagerTradeCache.samples",
                "gpom.hei.fastForestryBottler.enabled",
                "gpom.hei.forestryBottlerRecipeCache.enabled",
                "gpom.hei.fastEnderIOTank.enabled",
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
            case "fml.parallel.postInit.allowlist":
                return "Comma-separated mod ids allowed to run PostInit off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.postInit.denylist":
                return "Comma-separated mod ids forced back to the main thread during PostInit; entries here override the allowlist.";
            case "fml.parallel.postInit.continueOnModError":
                return "Diagnostic-only mode that records a PostInit failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.loadComplete.allowlist":
                return "Comma-separated mod ids allowed to run LoadComplete off-thread; '*' allows every loaded mod before denylist filtering.";
            case "fml.parallel.loadComplete.denylist":
                return "Comma-separated mod ids forced back to the main thread during LoadComplete; entries here override the allowlist.";
            case "fml.parallel.loadComplete.continueOnModError":
                return "Diagnostic-only mode that records a LoadComplete failure and keeps scheduling later mods. Use false for normal play.";
            case "fml.parallel.registrySerialization.enabled":
                return "Serializes Forge registry writes while lifecycle workers are active to avoid HashBiMap and ForgeRegistry concurrent mutation.";
            case "fml.parallel.autoQuarantineGlErrors.enabled":
                return "Diagnostic helper that appends catchable OpenGL thread offenders to the relevant phase denylist for the next launch.";
            case "fml.parallel.autoQuarantineGlErrors.includeRelatedMods":
                return "When auto-quarantining, also deny likely dependent/related mods so the next launch moves the whole cluster back to main thread.";
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
            case "gpom.startupProfiler.probeLogs.enabled":
                return "Compatibility alias for older configs; false also disables raw [Probe] startup lines.";
            case "gpom.startupProfiler.topCount":
                return "Number of top mods/probes included in startup profiler summaries.";
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
            case "gpom.mainMenuStartupTime.enabled":
                return "Draws the last measured startup duration in the top-right corner of the main menu.";
            case "gpom.loliasm.threadSafeStatefulRegistry":
                return "Replaces LoliASM's crash-state registry with a concurrent set and prunes cleared weak references from BufferBuilder churn.";
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
                return "Allows static initializers during explicit class prewarm. Risky; keep false unless testing a precise class list.";
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
            case "gpom.preInitHighSinkCallProfiler":
                return "Enables exact-version deep PreInit profilers for current high-sink mods.";
            case "gpom.hei.recipeProgressBar.enabled":
                return "Shows a coarse GPOM progress bar while HEI consumes parsed recipes into its registry.";
            case "gpom.hei.recipeProgressBar.stepSize":
                return "Number of HEI recipe registrations between progress updates.";
            case "gpom.hei.searchWorkers":
                return "Worker count for HEI search indexing. 0 lets GPOM choose an automatic bounded pool size.";
            case "gpom.hei.parallelPluginRegistration.enabled":
                return "Enables experimental worker dispatch for HEI IModPlugin.register calls that match the allowlist.";
            case "gpom.hei.parallelPluginRegistration.workers":
                return "Worker count for HEI plugin registration. 0 lets GPOM choose an automatic bounded pool size.";
            case "gpom.hei.parallelPluginRegistration.allowlist":
                return "Comma-separated HEI plugin class names allowed to register off-thread; '*' allows all before denylist filtering.";
            case "gpom.hei.parallelPluginRegistration.denylist":
                return "Comma-separated HEI plugin class names forced to register on the main thread; entries override the allowlist.";
            case "gpom.hei.jerVillagerTradeCache.enabled":
                return "Replaces JER's repeated random trade simulations with a reduced deterministic display cache.";
            case "gpom.hei.jerVillagerTradeCache.samples":
                return "Number of simulated villager-trade samples retained for JER display range approximation.";
            case "gpom.hei.fastForestryBottler.enabled":
                return "Uses Forestry's exact-version Bottler HEI fast path and skips redundant full-fluid-container fill loops.";
            case "gpom.hei.forestryBottlerRecipeCache.enabled":
                return "Persists Forestry Bottler HEI wrapper inputs/outputs and reuses them when item/fluid signatures match.";
            case "gpom.hei.fastEnderIOTank.enabled":
                return "Uses EnderIO's exact-version tank HEI fast path while preserving the full item list for mending recipes.";
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
        copySystemPropertyIfAbsent("gpom.logging.asyncProbeLogs.enabled");
        copySystemPropertyIfAbsent("gpom.logging.asyncProbeLogs.queueSize");
        copySystemPropertyIfAbsent("gpom.startupProfiler.topCount");
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
        copySystemPropertyIfAbsent("gpom.preInitClassPrewarm.explicitClasses");
        copySystemPropertyIfAbsent("gpom.gendustryConfigCache");
        copySystemPropertyIfAbsent("gpom.gendustryCallProfiler");
        copySystemPropertyIfAbsent("gpom.erebus.deferComposterRegistry");
        copySystemPropertyIfAbsent("gpom.erebus.deferOreConfigs");
        copySystemPropertyIfAbsent("gpom.enderio.fastSpawnerEntityValidation");
        copySystemPropertyIfAbsent("gpom.crafttweaker.fastZenRegister");
        copySystemPropertyIfAbsent("gpom.preInitHighSinkCallProfiler");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.enabled");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.forceGcBeforeSnapshots");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.delayedSnapshotMillis");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.deepAttribution.enabled");
        copySystemPropertyIfAbsent("gpom.worldLifecycleProfiler.deepAttribution.maxEntries");
        copySystemPropertyIfAbsent("gpom.loliasm.threadSafeStatefulRegistry");
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

    public static boolean mainMenuStartupTimeEnabled() {
        return booleanValue("gpom.mainMenuStartupTime.enabled");
    }

    public static boolean loliAsmThreadSafeStatefulRegistryEnabled() {
        return booleanValue("gpom.loliasm.threadSafeStatefulRegistry");
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

    public static boolean heiFastForestryBottlerEnabled() {
        return booleanValue("gpom.hei.fastForestryBottler.enabled");
    }

    public static boolean heiForestryBottlerRecipeCacheEnabled() {
        return booleanValue("gpom.hei.forestryBottlerRecipeCache.enabled");
    }

    public static boolean heiFastEnderIOTankEnabled() {
        return booleanValue("gpom.hei.fastEnderIOTank.enabled");
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
        return parseSet(VALUES.getProperty(key, DEFAULTS.getProperty(key, "")));
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

        Set<String> values = fileValue == null ? setValue(key) : parseSet(fileValue);
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
            return true;
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to write {} while appending {}", file, key, exception);
            return false;
        }
    }
}
