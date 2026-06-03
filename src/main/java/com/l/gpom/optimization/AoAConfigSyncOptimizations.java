package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.util.ReflectionFields;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.tslat.aoa3.utils.ConfigurationUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AoAConfigSyncOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("gpom.aoa3FastConfigSync", "true")
    );
    private static final Set<String> WARNING_KEYS = ConcurrentHashMap.newKeySet();

    private static final ConfigRoot[] CONFIG_ROOTS = {
            new ConfigRoot(ConfigurationUtil.MainConfig.class, "main_config"),
            new ConfigRoot(ConfigurationUtil.EntityConfig.class, "entities_config"),
            new ConfigRoot(ConfigurationUtil.IntegrationsConfig.class, "integrations_config"),
            new ConfigRoot(ConfigurationUtil.OreConfig.class, "ore_config"),
            new ConfigRoot(ConfigurationUtil.StructureConfig.class, "structure_config")
    };

    private AoAConfigSyncOptimizations() {
    }

    public static boolean tryFastSync(String modId, Config.Type type) {
        if (!ENABLED || !"aoa3".equals(modId) || type != Config.Type.INSTANCE) {
            return false;
        }
        if (!TargetedModVersions.isAdventOfAscensionClass(ConfigurationUtil.class)) {
            return false;
        }

        try {
            for (ConfigRoot root : CONFIG_ROOTS) {
                File file = new File(new File(Loader.instance().getConfigDir(), "aoa3"), root.fileName + ".cfg");
                Configuration configuration = loadConfiguration(file);
                applyClass(configuration, root.configClass, null, "general");
                registerWithConfigManager(modId, root.configClass, configuration);
            }
            return true;
        } catch (Throwable throwable) {
            warnOnce("fast-sync-failed", "AoA fast config sync failed; falling back to Forge ConfigManager.sync", throwable);
            return false;
        }
    }

    private static Configuration loadConfiguration(File file) {
        Configuration configuration = cachedConfiguration(file);
        if (configuration == null) {
            configuration = new Configuration(file);
            configuration.load();
            putCachedConfiguration(file, configuration);
        }
        return configuration;
    }

    private static void applyClass(Configuration configuration, Class<?> configClass, Object owner, String category) throws IllegalAccessException {
        for (Field field : configClass.getDeclaredFields()) {
            if (field.isSynthetic()) {
                continue;
            }

            int modifiers = field.getModifiers();
            if (Modifier.isFinal(modifiers) && !isCategoryField(field)) {
                continue;
            }
            if (owner == null && !Modifier.isStatic(modifiers)) {
                continue;
            }

            field.setAccessible(true);
            Class<?> fieldType = field.getType();
            Object target = Modifier.isStatic(modifiers) ? null : owner;

            if (isCategoryField(field)) {
                Object child = field.get(target);
                if (child != null) {
                    applyClass(configuration, child.getClass(), child, category + '.' + normalizedName(field.getName()));
                }
                continue;
            }

            Object currentValue = field.get(target);
            Object loadedValue = readValue(configuration, category, field.getName(), fieldType, currentValue);
            if (loadedValue != null) {
                field.set(target, loadedValue);
            }
        }
    }

    private static boolean isCategoryField(Field field) {
        Class<?> type = field.getType();
        return type.getName().startsWith("net.tslat.aoa3.utils.ConfigurationUtil$") && !type.isEnum();
    }

    private static Object readValue(Configuration configuration, String category, String name, Class<?> type, Object defaultValue) {
        if (type == Boolean.TYPE || type == Boolean.class) {
            return configuration.get(category, name, defaultValue instanceof Boolean && (Boolean) defaultValue).getBoolean();
        }
        if (type == Integer.TYPE || type == Integer.class) {
            int fallback = defaultValue instanceof Number ? ((Number) defaultValue).intValue() : 0;
            return configuration.get(category, name, fallback).getInt(fallback);
        }
        if (type == Double.TYPE || type == Double.class) {
            double fallback = defaultValue instanceof Number ? ((Number) defaultValue).doubleValue() : 0.0D;
            return configuration.get(category, name, fallback).getDouble(fallback);
        }
        if (type == String.class) {
            return configuration.get(category, name, defaultValue == null ? "" : String.valueOf(defaultValue)).getString();
        }
        if (type == String[].class) {
            String[] fallback = defaultValue instanceof String[] ? (String[]) defaultValue : new String[0];
            return configuration.get(category, name, fallback).getStringList();
        }
        if (type == int[].class) {
            int[] fallback = defaultValue instanceof int[] ? (int[]) defaultValue : new int[0];
            return configuration.get(category, name, fallback).getIntList();
        }
        if (type == boolean[].class) {
            boolean[] fallback = defaultValue instanceof boolean[] ? (boolean[]) defaultValue : new boolean[0];
            return configuration.get(category, name, fallback).getBooleanList();
        }
        if (type == double[].class) {
            double[] fallback = defaultValue instanceof double[] ? (double[]) defaultValue : new double[0];
            return configuration.get(category, name, fallback).getDoubleList();
        }
        if (type.isEnum()) {
            String fallback = defaultValue == null ? "" : ((Enum<?>) defaultValue).name();
            String value = configuration.get(category, name, fallback).getString();
            return enumValue(type, value, defaultValue);
        }

        warnOnce("unsupported-" + type.getName(), "AoA fast config sync does not support field type " + type.getName() + " for " + category + '.' + name, null);
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumType, String value, Object fallback) {
        try {
            return Enum.valueOf((Class<? extends Enum>) enumType, value);
        } catch (IllegalArgumentException e) {
            warnOnce(enumType.getName() + '#' + value, "AoA fast config sync found invalid enum value " + value + " for " + enumType.getName() + "; using default", e);
            return fallback;
        }
    }

    private static String normalizedName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private static Configuration cachedConfiguration(File file) {
        Map<String, Configuration> configs = (Map<String, Configuration>) ReflectionFields.getStatic(ConfigManager.class, "CONFIGS", "CONFIGS");
        return configs == null ? null : configs.get(file.getAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static void putCachedConfiguration(File file, Configuration configuration) {
        Map<String, Configuration> configs = (Map<String, Configuration>) ReflectionFields.getStatic(ConfigManager.class, "CONFIGS", "CONFIGS");
        if (configs != null) {
            configs.put(file.getAbsolutePath(), configuration);
        }
    }

    @SuppressWarnings("unchecked")
    private static void registerWithConfigManager(String modId, Class<?> configClass, Configuration configuration) {
        Map<String, Set<Class<?>>> modConfigClasses = (Map<String, Set<Class<?>>>) ReflectionFields.getStatic(ConfigManager.class, "MOD_CONFIG_CLASSES", "MOD_CONFIG_CLASSES");
        if (modConfigClasses != null) {
            Set<Class<?>> classes = modConfigClasses.get(modId);
            if (classes == null) {
                classes = new LinkedHashSet<>();
                modConfigClasses.put(modId, classes);
            }
            classes.add(configClass);
        }

        Map<Class<?>, Configuration> classToConfig = (Map<Class<?>, Configuration>) ReflectionFields.getStatic(ConfigManager.class, "CLASS_TO_CONFIG", "CLASS_TO_CONFIG");
        if (classToConfig != null) {
            classToConfig.put(configClass, configuration);
        }
    }

    private static void warnOnce(String key, String message, Throwable throwable) {
        if (!WARNING_KEYS.add(key)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn(message);
        } else {
            GPOM.LOGGER.warn(message, throwable);
        }
    }

    private static final class ConfigRoot {
        private final Class<?> configClass;
        private final String fileName;

        private ConfigRoot(Class<?> configClass, String fileName) {
            this.configClass = configClass;
            this.fileName = fileName;
        }
    }
}
