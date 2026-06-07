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
        writer.println("# General Purpose Optimization Mod early-loading config");
        writer.println("#");
        writer.println("# This file is read before Forge's normal config system is available.");
        writer.println("# Worker values use 0 for automatic sizing based on CPU and total physical memory.");
        writer.println("# Allowlist accepts comma-separated mod ids or * for every loaded mod.");
        writer.println("# Denylist always wins over allowlist.");
        writer.println("# continueOnModError=true is diagnostic-only: GPOM marks the failed mod errored and continues");
        writer.println("# so the next unsafe mod can be found. Use false for normal play once a phase is stable.");
        writer.println();

        writer.println("# Shared worker fallback. Per-phase worker values of 0 inherit this.");
        writeProperty(writer, "fml.parallel.workers");
        writer.println();

        writer.println("# FMLConstructionEvent: most invasive lifecycle phase.");
        writer.println("# Mod instances, proxies, classloader state, and automatic event subscribers are created here.");
        writer.println("# Keep disabled unless testing a narrow allowlist; broad * construction threading is unsafe.");
        writeProperty(writer, "fml.parallel.construct.enabled");
        writeProperty(writer, "fml.parallel.construct.workers");
        writeProperty(writer, "fml.parallel.construct.allowlist");
        writeProperty(writer, "fml.parallel.construct.denylist");
        writeProperty(writer, "fml.parallel.construct.continueOnModError");
        writer.println();

        writer.println("# FMLPreInitializationEvent: highest-risk lifecycle phase.");
        writer.println("# Many mods register blocks, items, entities, model loaders, and client resources here.");
        writer.println("# Shipped default is disabled and empty; pack profiles should add a tested allowlist/denylist.");
        writeProperty(writer, "fml.parallel.preInit.enabled");
        writeProperty(writer, "fml.parallel.preInit.workers");
        writeProperty(writer, "fml.parallel.preInit.allowlist");
        writeProperty(writer, "fml.parallel.preInit.denylist");
        writeProperty(writer, "fml.parallel.preInit.continueOnModError");
        writeProperty(writer, "fml.parallel.preInit.dag.enabled");
        writer.println();

        writer.println("# FMLInitializationEvent: still client/global-state sensitive.");
        writer.println("# Mods that create OpenGL resources, textures, shaders, or global registries must stay denied.");
        writeProperty(writer, "fml.parallel.init.enabled");
        writeProperty(writer, "fml.parallel.init.workers");
        writeProperty(writer, "fml.parallel.init.allowlist");
        writeProperty(writer, "fml.parallel.init.denylist");
        writeProperty(writer, "fml.parallel.init.continueOnModError");
        writer.println();

        writer.println("# FMLPostInitializationEvent: safer than PreInit/Init, but recipe/global registry mutations");
        writer.println("# and client render setup still need per-pack denylist validation.");
        writeProperty(writer, "fml.parallel.postInit.enabled");
        writeProperty(writer, "fml.parallel.postInit.workers");
        writeProperty(writer, "fml.parallel.postInit.allowlist");
        writeProperty(writer, "fml.parallel.postInit.denylist");
        writeProperty(writer, "fml.parallel.postInit.continueOnModError");
        writer.println();

        writer.println("# FMLLoadCompleteEvent: safest lifecycle phase for broad parallel dispatch.");
        writer.println("# HEI/JER and other mods with global client state should remain denied unless explicitly tested.");
        writeProperty(writer, "fml.parallel.loadComplete.enabled");
        writeProperty(writer, "fml.parallel.loadComplete.workers");
        writeProperty(writer, "fml.parallel.loadComplete.allowlist");
        writeProperty(writer, "fml.parallel.loadComplete.denylist");
        writeProperty(writer, "fml.parallel.loadComplete.continueOnModError");
        writer.println();

        writer.println("# Registry serialization: keeps Forge registry mutation single-writer while threaded");
        writer.println("# lifecycle handlers run. This is required for broad PreInit/Init experiments because");
        writer.println("# ForgeRegistry/HashBiMap internals are not concurrent writers.");
        writeProperty(writer, "fml.parallel.registrySerialization.enabled");
        writer.println();

        writer.println("# Parallel GL auto-quarantine: diagnostic-only helper for broad threading experiments.");
        writer.println("# Catchable OpenGL thread violations append the failing mod to the phase denylist and abort");
        writer.println("# so the next launch can retry that mod on the main thread. The retry wrapper can also use");
        writer.println("# GPOM's last-threaded breadcrumb after hard JVM exits.");
        writeProperty(writer, "fml.parallel.autoQuarantineGlErrors.enabled");
        writeProperty(writer, "fml.parallel.autoQuarantineGlErrors.includeRelatedMods");
        writer.println();

        writer.println("# GPOM logging: gpom.logging.enabled=false silences all logs emitted through GPOM's logger.");
        writer.println("# Other switches silence specific high-volume GPOM categories while keeping warnings/errors elsewhere.");
        writeProperty(writer, "gpom.logging.enabled");
        writeProperty(writer, "gpom.logging.fmlScheduler.enabled");
        writeProperty(writer, "gpom.logging.optimizationInfo.enabled");
        writeProperty(writer, "gpom.logging.cacheInfo.enabled");
        writer.println();

        writer.println("# Startup profiler log volume. Timings are still collected when these are disabled,");
        writer.println("# but selected raw/detail/summary lines are not emitted.");
        writeProperty(writer, "gpom.startupProfiler.logs.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.boot.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.phaseLifecycle.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.modDetails.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.phaseSummary.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.phaseDigest.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.memoryDetails.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.probes.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.probeSummary.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.wallDiagnostics.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.stackSamples.enabled");
        writeProperty(writer, "gpom.startupProfiler.logs.resourceLoadOrder.enabled");
        writer.println("# Compatibility alias for older configs; false also disables raw [Probe] lines.");
        writeProperty(writer, "gpom.startupProfiler.probeLogs.enabled");
        writer.println("# Number of top mods/probes logged in each phase summary.");
        writeProperty(writer, "gpom.startupProfiler.topCount");
        writer.println();

        writer.println("# Early splash: experimental isolated AWT/Swing window shown before Minecraft creates its own display.");
        writer.println("# Shipped default is disabled. Enable only in client profiles being tested.");
        writer.println("# packName is only a display label for the splash footer; leave as Minecraft for generic use.");
        writeProperty(writer, "gpom.earlySplash.enabled");
        writeProperty(writer, "gpom.earlySplash.packName");
        writer.println();

        writer.println("# World loading screen: passive/fail-closed overlay for the blank/early-0% world join wait.");
        writer.println("# Shipped default is disabled until pack-specific world-entry validation is clean.");
        writeProperty(writer, "gpom.worldLoadingScreen.enabled");
        writer.println();

        writer.println("# Railcraft startup deferrals: exact-version options for Railcraft 12.1.0-beta-8.");
        writer.println("# These avoid forcing large Railcraft enum/container initialization during module discovery.");
        writeProperty(writer, "gpom.railcraftLazyItemConditions");
        writeProperty(writer, "gpom.railcraft.deferModuleIC2Containers");
        writeProperty(writer, "gpom.railcraft.deferModuleContainers");
        writeProperty(writer, "gpom.railcraft.deferSelectedModuleContainers");
        writeProperty(writer, "gpom.railcraft.deferModuleContainerAllowlist");
        writeProperty(writer, "gpom.railcraft.lazyCartConfig");
        writer.println();

        writer.println("# Deep PreInit profilers: diagnostic-only exact-version probes for current high sinks.");
        writer.println("# These add nested method/call timings to the StartupProfiler summary; leave false for normal play.");
        writer.println("# Astral Sorcery: append AssetLibrary's reload listener during PreInit without running immediate texture preload.");
        writeProperty(writer, "gpom.astralSorcery.deferAssetLibraryReload");
        writer.println("# AgriCraft: exact-version JSON IO fast path; parses current JSON but skips redundant default/writeback rewrites.");
        writeProperty(writer, "gpom.agricraft.fastJsonIo");
        writeProperty(writer, "gpom.agricraft.fastResourceScan");
        writeProperty(writer, "gpom.agricraft.skipJsonWriteback");
        writer.println("# Exact-input cache for OpenComputers settings construction. Invalidates on bundled/user config bytes.");
        writeProperty(writer, "gpom.openComputersSettingsCache");
        writer.println("# OpenComputers: avoid probing disabled Lua native backends before architecture registration.");
        writeProperty(writer, "gpom.openComputers.fastLuaSelection");
        writeProperty(writer, "gpom.openComputersCallProfiler");
        writeProperty(writer, "gpom.openComputersIntegrationProfiler");
        writer.println("# PreInit class prewarm: async sidecar class definition/linking. Static initialization is opt-in and risky.");
        writer.println("# allowlist accepts mod ids or *; extraPrefixes accepts modid:internal/prefix|other/prefix;modid2:prefix.");
        writer.println("# noInitAllowlist accepts mod ids or * and uses the built-in prefix table with Class.forName(..., false).");
        writer.println("# noInitPrefixes accepts extra modid:internal/prefix|other/prefix entries and also always uses Class.forName(..., false).");
        writer.println("# explicitClasses accepts modid:pkg.Class|other.Class;modid2:pkg.Class and is the intended form for initializeClasses=true.");
        writeProperty(writer, "gpom.preInitClassPrewarm.enabled");
        writeProperty(writer, "gpom.preInitClassPrewarm.allowlist");
        writeProperty(writer, "gpom.preInitClassPrewarm.workers");
        writeProperty(writer, "gpom.preInitClassPrewarm.deferMinCompletedHandlers");
        writeProperty(writer, "gpom.preInitClassPrewarm.deferUntilSerialMillis");
        writeProperty(writer, "gpom.preInitClassPrewarm.pauseDuringSerialHandlers");
        writeProperty(writer, "gpom.preInitClassPrewarm.pauseDuringBlockingWaits");
        writeProperty(writer, "gpom.preInitClassPrewarm.maxClassesPerMod");
        writeProperty(writer, "gpom.preInitClassPrewarm.chunkSize");
        writeProperty(writer, "gpom.preInitClassPrewarm.includeAnonClasses");
        writeProperty(writer, "gpom.preInitClassPrewarm.extraPrefixes");
        writeProperty(writer, "gpom.preInitClassPrewarm.noInitAllowlist");
        writeProperty(writer, "gpom.preInitClassPrewarm.noInitPrefixes");
        writeProperty(writer, "gpom.preInitClassPrewarm.initializeClasses");
        writeProperty(writer, "gpom.preInitClassPrewarm.explicitClasses");
        writer.println("# Exact-input cache for Gendustry tuning/config parsing. Invalidates on bundled/user config bytes.");
        writeProperty(writer, "gpom.gendustryConfigCache");
        writeProperty(writer, "gpom.gendustryCallProfiler");
        writer.println("# Erebus: defer composter registry materialization until the first compostability query.");
        writeProperty(writer, "gpom.erebus.deferComposterRegistry");
        writer.println("# Erebus: defer ore config enum materialization until first ore-setting use.");
        writeProperty(writer, "gpom.erebus.deferOreConfigs");
        writer.println("# CraftTweaker: use ASMData to register ZenRegister classes without scanning class annotations reflectively.");
        writeProperty(writer, "gpom.crafttweaker.fastZenRegister");
        writeProperty(writer, "gpom.preInitHighSinkCallProfiler");
        writer.println();

        writer.println("# HEI recipe progress: keeps GPOM's optimized no-per-recipe HEI path, but adds a coarse");
        writer.println("# progress bar while HEI consumes already-parsed recipes into its recipe registry.");
        writeProperty(writer, "gpom.hei.recipeProgressBar.enabled");
        writeProperty(writer, "gpom.hei.recipeProgressBar.stepSize");
        writer.println();

        writer.println("# HEI search workers: GPOM replaces HEI's single async search worker with a bounded pool.");
        writer.println("# 0 picks an automatic value from CPU count. Search semantics are unchanged.");
        writeProperty(writer, "gpom.hei.searchWorkers");
        writer.println();

        writer.println("# HEI plugin threading: experimental. GPOM synchronizes HEI's mutable ModRegistry surface,");
        writer.println("# then optionally runs exact allowlisted IModPlugin.register calls in workers.");
        writer.println("# Empty allowlist means no plugin is threaded; denylist wins. Use full plugin class names or *.");
        writeProperty(writer, "gpom.hei.parallelPluginRegistration.enabled");
        writeProperty(writer, "gpom.hei.parallelPluginRegistration.workers");
        writeProperty(writer, "gpom.hei.parallelPluginRegistration.allowlist");
        writeProperty(writer, "gpom.hei.parallelPluginRegistration.denylist");
        writer.println();

        writer.println("# JER villager trade cache: replaces JER's 100 random trade simulations per trade");
        writer.println("# with a reduced deterministic sample and stores the reduced display trade result.");
        writer.println("# Set samples=100 for closer vanilla JER display ranges, or disable to use stock JER.");
        writeProperty(writer, "gpom.hei.jerVillagerTradeCache.enabled");
        writeProperty(writer, "gpom.hei.jerVillagerTradeCache.samples");
        writer.println();

        writer.println("# Forestry Bottler HEI fast path: exact-version optimization for Forestry 5.8.2.427.");
        writer.println("# Skips the per-fluid fill loop for fluid containers that are already full, while falling");
        writer.println("# back to Forestry's original recipe generator if this path is disabled or fails.");
        writeProperty(writer, "gpom.hei.fastForestryBottler.enabled");
        writer.println("# Stores the generated Bottler HEI wrapper inputs/outputs as compressed NBT tuples after");
        writer.println("# a full build, then reconstructs wrappers directly when the item/fluid signature matches.");
        writeProperty(writer, "gpom.hei.forestryBottlerRecipeCache.enabled");
        writer.println();

        writer.println("# EnderIO Tank HEI fast path: exact-version optimization for EnderIO 5.3.72.");
        writer.println("# Uses the cached fluid-handler subset for fluid IO recipes, while preserving");
        writer.println("# the full HEI item list for EnderIO's mending recipe display.");
        writeProperty(writer, "gpom.hei.fastEnderIOTank.enabled");
        writer.println();

        writer.println("# ExtraTrees Lumbermill HEI cache: exact-version optimization for Binnie Mods 2.5.1.213.");
        writer.println("# Stores Lumbermill input/output wrapper tuples after the full logWood recipe scan, then");
        writer.println("# reconstructs wrappers directly when the item/recipe/log signatures match.");
        writeProperty(writer, "gpom.hei.extraTreesLumbermillRecipeCache.enabled");
        writer.println();

        writer.println("# Thermal Expansion Transposer HEI fast path: exact-version optimization for TE 5.5.7.1.");
        writer.println("# Replaces the expensive fluid-container wrapper constructor with GPOM's equivalent builder,");
        writer.println("# reuses identical animated drawables, and can persist fill/drain simulation results.");
        writeProperty(writer, "gpom.hei.fastThermalTransposerContainers.enabled");
        writer.println("# Stores generated Transposer container wrapper item/fluid lists as compressed NBT tuples.");
        writer.println("# First launch populates the cache; matching later launches reconstruct wrappers directly.");
        writeProperty(writer, "gpom.hei.thermalTransposerContainerCache.enabled");
    }

    private static void writeProperty(PrintWriter writer, String key) {
        writer.println(key + "=" + DEFAULTS.getProperty(key, ""));
    }

    private static void applyEarlySystemProperties() {
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
        copySystemPropertyIfAbsent("gpom.crafttweaker.fastZenRegister");
        copySystemPropertyIfAbsent("gpom.preInitHighSinkCallProfiler");
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
