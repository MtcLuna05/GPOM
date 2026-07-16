package com.l.gpom.compat.scannable;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;

public final class ScannableConfigSyncOptimization {
    private static final String SETTINGS_CLASS = "li.cil.scannable.common.config.Settings";
    private static final String SERVER_SETTINGS_FIELD = "serverSettings";
    private static final String[] SERVER_SETTING_FIELDS = {
            "useEnergy",
            "energyCapacityScanner",
            "energyCostModuleRange",
            "energyCostModuleAnimal",
            "energyCostModuleMonster",
            "energyCostModuleOreCommon",
            "energyCostModuleOreRare",
            "energyCostModuleBlock",
            "energyCostModuleStructure",
            "energyCostModuleFluid",
            "energyCostModuleEntity",
            "baseScanRadius",
            "blockBlacklist",
            "oresBlacklist",
            "oresCommon",
            "oresRare",
            "statesCommon",
            "statesRare",
            "structures",
            "fluidBlacklist"
    };
    private static final String[] ORE_CACHE_GETTERS = {
            "getOreBlacklist",
            "getCommonOres",
            "getRareOres",
            "getCommonStates",
            "getRareStates",
            "getFluidBlacklist"
    };
    private static final String[] ORE_CACHE_STATIC_FIELDS = {
            "oreColors",
            "fluidColors"
    };

    private static volatile Field serverSettingsField;
    private static volatile Field[] serverSettingFields;
    private static volatile Method[] oreCacheGetterMethods;
    private static volatile Field[] oreCacheStaticFields;
    private static volatile boolean oreCacheBuilt;
    private static volatile int lastOreCacheSignature;

    private ScannableConfigSyncOptimization() {
    }

    public static boolean shouldSkipSetServerSettings(Object incomingSettings) {
        if (!GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled() || incomingSettings == null) {
            return false;
        }

        try {
            Object currentSettings = currentServerSettings(incomingSettings);
            if (currentSettings == null) {
                return false;
            }
            if (currentSettings == incomingSettings || settingsEqual(currentSettings, incomingSettings)) {
                if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.info("[ScannableConfigSync] Skipped redundant server settings ore-cache rebuild");
                }
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    public static boolean shouldSkipRebuildOreCache(Object provider) {
        if (!GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled()) {
            return false;
        }
        try {
            int signature = currentOreCacheSignature(provider);
            if (oreCacheBuilt && signature == lastOreCacheSignature) {
                if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.info("[ScannableConfigSync] Skipped redundant ore-cache rebuild");
                }
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    public static void markRebuildOreCache(Object provider) {
        if (!GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled()) {
            return;
        }
        try {
            lastOreCacheSignature = currentOreCacheSignature(provider);
            oreCacheBuilt = true;
        } catch (Throwable ignored) {
            oreCacheBuilt = false;
        }
    }

    private static Object currentServerSettings(Object incomingSettings) throws ReflectiveOperationException {
        Field field = serverSettingsField;
        if (field == null) {
            ClassLoader loader = incomingSettings.getClass().getClassLoader();
            Class<?> settingsClass = Class.forName(SETTINGS_CLASS, false, loader);
            field = settingsClass.getDeclaredField(SERVER_SETTINGS_FIELD);
            field.setAccessible(true);
            serverSettingsField = field;
        }
        return field.get(null);
    }

    private static boolean settingsEqual(Object currentSettings, Object incomingSettings) throws IllegalAccessException, NoSuchFieldException {
        Class<?> settingsClass = incomingSettings.getClass();
        if (!settingsClass.isInstance(currentSettings)) {
            return false;
        }

        Field[] fields = serverSettingFields;
        if (fields == null || fields.length == 0 || fields[0].getDeclaringClass() != settingsClass) {
            fields = resolveServerSettingFields(settingsClass);
            serverSettingFields = fields;
        }

        for (Field field : fields) {
            if (!valuesEqual(field.get(currentSettings), field.get(incomingSettings))) {
                return false;
            }
        }
        return true;
    }

    private static int currentOreCacheSignature(Object provider) throws ReflectiveOperationException {
        ClassLoader loader = provider == null ? ScannableConfigSyncOptimization.class.getClassLoader() : provider.getClass().getClassLoader();
        Class<?> settingsClass = Class.forName(SETTINGS_CLASS, false, loader);
        Method[] getters = oreCacheGetterMethods;
        if (getters == null || getters.length == 0 || getters[0].getDeclaringClass() != settingsClass) {
            getters = resolveOreCacheGetterMethods(settingsClass);
            oreCacheGetterMethods = getters;
        }
        Field[] staticFields = oreCacheStaticFields;
        if (staticFields == null || staticFields.length == 0 || staticFields[0].getDeclaringClass() != settingsClass) {
            staticFields = resolveOreCacheStaticFields(settingsClass);
            oreCacheStaticFields = staticFields;
        }

        Object[] values = new Object[getters.length + staticFields.length];
        int index = 0;
        for (Method getter : getters) {
            values[index++] = getter.invoke(null);
        }
        for (Field field : staticFields) {
            values[index++] = field.get(null);
        }
        return Arrays.deepHashCode(values);
    }

    private static Method[] resolveOreCacheGetterMethods(Class<?> settingsClass) throws NoSuchMethodException {
        Method[] methods = new Method[ORE_CACHE_GETTERS.length];
        for (int i = 0; i < ORE_CACHE_GETTERS.length; i++) {
            Method method = settingsClass.getDeclaredMethod(ORE_CACHE_GETTERS[i]);
            method.setAccessible(true);
            methods[i] = method;
        }
        return methods;
    }

    private static Field[] resolveOreCacheStaticFields(Class<?> settingsClass) throws NoSuchFieldException {
        Field[] fields = new Field[ORE_CACHE_STATIC_FIELDS.length];
        for (int i = 0; i < ORE_CACHE_STATIC_FIELDS.length; i++) {
            Field field = settingsClass.getDeclaredField(ORE_CACHE_STATIC_FIELDS[i]);
            field.setAccessible(true);
            fields[i] = field;
        }
        return fields;
    }

    private static Field[] resolveServerSettingFields(Class<?> settingsClass) throws NoSuchFieldException {
        Field[] fields = new Field[SERVER_SETTING_FIELDS.length];
        for (int i = 0; i < SERVER_SETTING_FIELDS.length; i++) {
            Field field = settingsClass.getDeclaredField(SERVER_SETTING_FIELDS[i]);
            field.setAccessible(true);
            fields[i] = field;
        }
        return fields;
    }

    private static boolean valuesEqual(Object first, Object second) {
        if (first instanceof Object[] && second instanceof Object[]) {
            return Arrays.equals((Object[]) first, (Object[]) second);
        }
        if (first != null && second != null && first.getClass().isArray() && second.getClass().isArray()) {
            int length = Array.getLength(first);
            if (length != Array.getLength(second)) {
                return false;
            }
            for (int i = 0; i < length; i++) {
                if (!valuesEqual(Array.get(first, i), Array.get(second, i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(first, second);
    }
}
