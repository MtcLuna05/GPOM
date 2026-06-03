package com.l.gpom.config;

import com.l.gpom.GPOM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

public final class GpomEarlyConfig {
    private static final String FILE_NAME = "gpom-early.properties";
    private static final Properties DEFAULTS = new Properties();
    private static final Properties VALUES = new Properties();

    static {
        DEFAULTS.setProperty("fml.parallel.postInit.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.loadComplete.enabled", "false");
        DEFAULTS.setProperty("fml.parallel.workers", Integer.toString(Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        DEFAULTS.setProperty("fml.parallel.postInit.allowlist", "modularmachinery,techreborn");
        DEFAULTS.setProperty("fml.parallel.postInit.denylist", "enderio,forestry,advancedrocketry");
        DEFAULTS.setProperty("fml.parallel.loadComplete.allowlist", "");
        DEFAULTS.setProperty("fml.parallel.loadComplete.denylist", "");
        DEFAULTS.setProperty("fml.parallel.progressBar.enabled", "true");

        VALUES.putAll(DEFAULTS);
        load();
    }

    private GpomEarlyConfig() {
    }

    public static boolean parallelPostInitEnabled() {
        return booleanValue("fml.parallel.postInit.enabled");
    }

    public static boolean parallelLoadCompleteEnabled() {
        return booleanValue("fml.parallel.loadComplete.enabled");
    }

    public static int parallelWorkers() {
        return Math.max(1, intValue("fml.parallel.workers", Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
    }

    public static Set<String> parallelPostInitAllowlist() {
        return setValue("fml.parallel.postInit.allowlist");
    }

    public static Set<String> parallelPostInitDenylist() {
        return setValue("fml.parallel.postInit.denylist");
    }

    public static Set<String> parallelLoadCompleteAllowlist() {
        return setValue("fml.parallel.loadComplete.allowlist");
    }

    public static Set<String> parallelLoadCompleteDenylist() {
        return setValue("fml.parallel.loadComplete.denylist");
    }

    public static boolean parallelProgressBarEnabled() {
        return booleanValue("fml.parallel.progressBar.enabled");
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

        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            Properties defaults = new Properties();
            defaults.putAll(DEFAULTS);
            defaults.store(output, "General Purpose Optimization Mod early-loading config");
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM Config] Failed to write default {}", file, exception);
        }
    }

    private static File configFile() {
        File gameDir = new File(System.getProperty("user.dir", "."));
        return new File(new File(gameDir, "config"), FILE_NAME);
    }

    private static boolean booleanValue(String key) {
        return Boolean.parseBoolean(VALUES.getProperty(key, DEFAULTS.getProperty(key, "false")).trim());
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
