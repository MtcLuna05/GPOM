package com.l.gpom.config;

import com.l.gpom.GPOM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class GpomEarlyConfig {
    private static final String FILE_NAME = "gpom-early.properties";
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
        DEFAULTS.setProperty("gpom.earlySplash.enabled", "false");
        DEFAULTS.setProperty("gpom.earlySplash.packName", "Minecraft");
        DEFAULTS.setProperty("gpom.worldLoadingScreen.enabled", "false");
        DEFAULTS.setProperty("gpom.hei.recipeProgressBar.enabled", "true");
        DEFAULTS.setProperty("gpom.hei.recipeProgressBar.stepSize", "256");
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

        writer.println("# HEI recipe progress: keeps GPOM's optimized no-per-recipe HEI path, but adds a coarse");
        writer.println("# progress bar while HEI consumes already-parsed recipes into its recipe registry.");
        writeProperty(writer, "gpom.hei.recipeProgressBar.enabled");
        writeProperty(writer, "gpom.hei.recipeProgressBar.stepSize");
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

    public static boolean earlySplashEnabled() {
        return booleanValue("gpom.earlySplash.enabled");
    }

    public static String earlySplashPackName() {
        return stringValue("gpom.earlySplash.packName", "Minecraft");
    }

    public static boolean worldLoadingScreenEnabled() {
        return booleanValue("gpom.worldLoadingScreen.enabled");
    }

    public static boolean heiRecipeProgressBarEnabled() {
        return booleanValue("gpom.hei.recipeProgressBar.enabled");
    }

    public static int heiRecipeProgressBarStepSize() {
        return Math.max(1, intValue("gpom.hei.recipeProgressBar.stepSize", 256));
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
        Set<String> values = new LinkedHashSet<>();
        String raw = VALUES.getProperty(key, DEFAULTS.getProperty(key, ""));
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
}
