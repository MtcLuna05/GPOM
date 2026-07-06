package com.l.gpom.compat.randomthings;

import com.l.gpom.GPOM;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RandomThingsRuneSettings {
    public static final int RUNE_COUNT = 16;
    public static final int MIN_RESOLUTION = 2;
    public static final int MAX_RESOLUTION = 256;
    private static final int[] ALLOWED_RESOLUTIONS = {2, 3, 4, 7, 8, 15, 16, 31, 32, 63, 64, 127, 128, 255, 256};
    private static final RuneSettings[] CLIENT = defaultSettingsArray();
    private static final Map<UUID, RuneSettings[]> SERVER_BY_PLAYER = new ConcurrentHashMap<>();
    private static File settingsFile;
    private static volatile boolean loaded;

    private RandomThingsRuneSettings() {
    }

    public static RuneSettings client(int rune) {
        ensureClientLoaded();
        return CLIENT[clampRune(rune)].copy();
    }

    public static RuneSettings clientRaw(int rune) {
        ensureClientLoaded();
        return CLIENT[clampRune(rune)];
    }

    public static RuneSettings forPlayer(UUID playerId, int rune) {
        RuneSettings[] settings = playerId == null ? null : SERVER_BY_PLAYER.get(playerId);
        if (settings == null) {
            return defaultSetting();
        }
        return settings[clampRune(rune)].copy();
    }

    public static void updateClient(int rune, RuneSettings settings) {
        ensureClientLoaded();
        CLIENT[clampRune(rune)] = normalize(settings);
        saveClient();
    }

    public static void updateServer(UUID playerId, int rune, RuneSettings settings) {
        if (playerId == null) {
            return;
        }
        RuneSettings[] values = SERVER_BY_PLAYER.computeIfAbsent(playerId, ignored -> defaultSettingsArray());
        values[clampRune(rune)] = normalize(settings);
    }

    public static RuneSettings[] clientSnapshot() {
        ensureClientLoaded();
        RuneSettings[] copy = new RuneSettings[RUNE_COUNT];
        for (int i = 0; i < RUNE_COUNT; i++) {
            copy[i] = CLIENT[i].copy();
        }
        return copy;
    }

    public static void setSettingsFile(File configDirectory) {
        if (configDirectory == null) {
            return;
        }
        settingsFile = new File(configDirectory, "gpom-rune-dust.properties");
    }

    public static void ensureClientLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = settingsFile != null ? settingsFile : new File("config", "gpom-rune-dust.properties");
        if (!file.isFile()) {
            saveClient();
            return;
        }
        Properties properties = new Properties();
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
            properties.load(input);
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM RandomThings Runes] Failed to read {}; using defaults", file, exception);
            return;
        }
        for (int rune = 0; rune < RUNE_COUNT; rune++) {
            CLIENT[rune] = new RuneSettings(
                    bool(properties, rune, "autoConnect", true),
                    resolution(properties, rune, "resolution", 4),
                    integer(properties, rune, "brush", 1, 1, 9),
                    integer(properties, rune, "visualScale", 50, 10, 100),
                    integer(properties, rune, "visualPadding", 25, 0, 45),
                    bool(properties, rune, "replaceOccupied", false)
            );
        }
    }

    public static void saveClient() {
        File file = settingsFile != null ? settingsFile : new File("config", "gpom-rune-dust.properties");
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            GPOM.LOGGER.warn("[GPOM RandomThings Runes] Failed to create config directory {}", parent);
            return;
        }
        Properties properties = new Properties();
        for (int rune = 0; rune < RUNE_COUNT; rune++) {
            RuneSettings setting = normalize(CLIENT[rune]);
            String prefix = "rune." + rune + ".";
            properties.setProperty(prefix + "autoConnect", Boolean.toString(setting.autoConnect));
            properties.setProperty(prefix + "resolution", Integer.toString(setting.resolution));
            properties.setProperty(prefix + "brush", Integer.toString(setting.brush));
            properties.setProperty(prefix + "visualScale", Integer.toString(setting.visualScale));
            properties.setProperty(prefix + "visualPadding", Integer.toString(setting.visualPadding));
            properties.setProperty(prefix + "replaceOccupied", Boolean.toString(setting.replaceOccupied));
        }
        try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
            properties.store(output, "GPOM improved runic dust per-rune settings. Edited in game with shift right-click on rune dust or a rune block.");
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[GPOM RandomThings Runes] Failed to write {}", file, exception);
        }
    }

    public static RuneSettings defaultSetting() {
        return new RuneSettings(true, 4, 1, 50, 25, false);
    }

    public static boolean isDefault(RuneSettings settings) {
        RuneSettings normalized = normalize(settings);
        RuneSettings defaults = defaultSetting();
        return normalized.autoConnect == defaults.autoConnect
                && normalized.resolution == defaults.resolution
                && normalized.brush == defaults.brush
                && normalized.visualScale == defaults.visualScale
                && normalized.visualPadding == defaults.visualPadding
                && normalized.replaceOccupied == defaults.replaceOccupied;
    }

    public static RuneSettings normalize(RuneSettings settings) {
        RuneSettings fallback = settings == null ? defaultSetting() : settings;
        return new RuneSettings(
                fallback.autoConnect,
                normalizeResolution(fallback.resolution),
                clamp(fallback.brush, 1, 9),
                clamp(fallback.visualScale, 10, 100),
                clamp(fallback.visualPadding, 0, 45),
                fallback.replaceOccupied
        );
    }

    public static int clampRune(int rune) {
        return clamp(rune, 0, RUNE_COUNT - 1);
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static int normalizeResolution(int value) {
        int clamped = clamp(value, MIN_RESOLUTION, MAX_RESOLUTION);
        if (isAllowedResolution(clamped)) {
            return clamped;
        }
        int best = ALLOWED_RESOLUTIONS[0];
        int bestDistance = Math.abs(clamped - best);
        for (int allowed : ALLOWED_RESOLUTIONS) {
            int distance = Math.abs(clamped - allowed);
            if (distance < bestDistance) {
                best = allowed;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static int previousResolution(int value) {
        int normalized = normalizeResolution(value);
        for (int i = ALLOWED_RESOLUTIONS.length - 1; i >= 0; i--) {
            if (ALLOWED_RESOLUTIONS[i] < normalized) {
                return ALLOWED_RESOLUTIONS[i];
            }
        }
        return ALLOWED_RESOLUTIONS[0];
    }

    public static int nextResolution(int value) {
        int normalized = normalizeResolution(value);
        for (int allowed : ALLOWED_RESOLUTIONS) {
            if (allowed > normalized) {
                return allowed;
            }
        }
        return ALLOWED_RESOLUTIONS[ALLOWED_RESOLUTIONS.length - 1];
    }

    public static boolean isAllowedResolution(int value) {
        return value >= MIN_RESOLUTION && value <= MAX_RESOLUTION
                && (isPowerOfTwo(value) || isPowerOfTwo(value + 1));
    }

    private static RuneSettings[] defaultSettingsArray() {
        RuneSettings[] settings = new RuneSettings[RUNE_COUNT];
        for (int i = 0; i < settings.length; i++) {
            settings[i] = defaultSetting();
        }
        return settings;
    }

    private static boolean bool(Properties properties, int rune, String key, boolean fallback) {
        return Boolean.parseBoolean(properties.getProperty("rune." + rune + "." + key, Boolean.toString(fallback)).trim());
    }

    private static int integer(Properties properties, int rune, String key, int fallback, int min, int max) {
        String raw = properties.getProperty("rune." + rune + "." + key, Integer.toString(fallback)).trim();
        try {
            return clamp(Integer.parseInt(raw), min, max);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int resolution(Properties properties, int rune, String key, int fallback) {
        String raw = properties.getProperty("rune." + rune + "." + key, Integer.toString(fallback)).trim();
        try {
            return normalizeResolution(Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return normalizeResolution(fallback);
        }
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    public static final class RuneSettings {
        public final boolean autoConnect;
        public final int resolution;
        public final int brush;
        public final int visualScale;
        public final int visualPadding;
        public final boolean replaceOccupied;

        public RuneSettings(boolean autoConnect, int resolution, int brush, int visualScale, int visualPadding, boolean replaceOccupied) {
            this.autoConnect = autoConnect;
            this.resolution = resolution;
            this.brush = brush;
            this.visualScale = visualScale;
            this.visualPadding = visualPadding;
            this.replaceOccupied = replaceOccupied;
        }

        public RuneSettings copy() {
            return new RuneSettings(autoConnect, resolution, brush, visualScale, visualPadding, replaceOccupied);
        }
    }
}
