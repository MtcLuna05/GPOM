package com.l.gpom.compat.scannable;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.lang.reflect.Field;
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

    private static volatile Field serverSettingsField;
    private static volatile Field[] serverSettingFields;

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
        return Objects.equals(first, second);
    }
}
