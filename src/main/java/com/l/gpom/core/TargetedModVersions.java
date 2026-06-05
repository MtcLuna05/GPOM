package com.l.gpom.core;

import net.minecraft.launchwrapper.Launch;

import java.io.File;
import java.net.URL;
import java.security.CodeSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TargetedModVersions {
    public static final String DEPENDENCIES =
            "after:aoa3@[3.3.6];" +
            "after:abyssalcraft@[1.11.2];" +
            "after:aquaacrobatics@[1.15.4];" +
            "after:betterquesting@[4.3.1];" +
            "after:binniecore@[2.5.1.213];" +
            "after:buildcraftcore@[8.0.0];" +
            "after:buildcraftlib@[8.0.0];" +
            "after:citnbt@[0.3.2];" +
            "after:enderio@[5.3.72];" +
            "after:environmentaltech@[1.12.2-2.0.20.1];" +
            "after:expequiv@[12.3.17];" +
            "after:extrautils2@[1.0];" +
            "after:extratrees@[2.5.1.213];" +
            "after:forestry@[5.8.2.427];" +
            "after:ftblib@[5.4.7.2];" +
            "after:jei@[4.28.1];" +
            "after:jeresources@[0.9.2.60];" +
            "after:libvulpes@[0.5.0];" +
            "after:modularmachinery@[2.3.2];" +
            "after:opencomputers@[1.8.7];" +
            "after:projecte@[1.12.2-PE1.4.1];" +
            "after:railcraft@[12.1.0-beta-8];" +
            "after:reborncore@[3.19.5];" +
            "after:renderlib@[1.4.5];" +
            "after:techreborn@[2.27.3.1084];" +
            "after:thaumcraft@[6.1.BETA26];" +
            "after:thebetweenlands@[3.8.1];" +
            "after:unlimitedchiselworks@[0.3.5]";

    private static final Map<String, Boolean> JAR_PRESENT_CACHE = new ConcurrentHashMap<>();

    private TargetedModVersions() {
    }

    public static boolean isAdventOfAscensionClass(Class<?> type) {
        return isClassFromSupportedJar(type, "AoA3-3.3.6.jar");
    }

    public static boolean isAdventOfAscensionClass(String className) {
        return isClassFromSupportedJar(className, "AoA3-3.3.6.jar");
    }

    public static boolean isAquaAcrobaticsClass(String className) {
        return isClassFromSupportedJar(className, "AquaAcrobatics-1.15.4.jar");
    }

    public static boolean isAbyssalCraftClass(String className) {
        return isClassFromSupportedJar(className, "AbyssalCraft-1.12.2-1.11.2.jar");
    }

    public static boolean isBetweenlandsClass(String className) {
        return isClassFromSupportedJar(className, "TheBetweenlands-3.8.1-universal.jar");
    }

    public static boolean isBetterQuestingClass(String className) {
        return isClassFromSupportedJar(className, "BetterQuestingUnofficial-4.3.1.jar");
    }

    public static boolean isBinnieModsClass(String className) {
        return isClassFromSupportedJar(className, "binnie-mods-1.12.2-2.5.1.213.jar");
    }

    public static boolean isBuildCraftCoreClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-core-8.0.0.jar");
    }

    public static boolean isCitNbtClass(String className) {
        return isClassFromSupportedJar(className, "citnbt-0.3.2.jar");
    }

    public static boolean isCraftTweakerClass(String className) {
        return isClassFromSupportedJar(className, "CraftTweaker2-1.12-4.1.20.711.jar");
    }

    public static boolean isEnderIOClass(Class<?> type) {
        return isClassFromSupportedJar(type, "EnderIO-1.12.2-5.3.72.jar");
    }

    public static boolean isEnderIOClass(String className) {
        return isClassFromSupportedJar(className, "EnderIO-1.12.2-5.3.72.jar");
    }

    public static boolean isEnvironmentalTechClass(String className) {
        return isClassFromSupportedJar(className, "environmentaltech-1.12.2-2.0.20.1.jar");
    }

    public static boolean isExpandedEquivalenceClass(String className) {
        return isClassFromSupportedJar(className, "ExpandedEquivalence-1.12.2-12.3.17.jar");
    }

    public static boolean isExtraUtilities2Class(String className) {
        return isClassFromSupportedJar(className, "extrautils2-1.12-1.9.9.jar");
    }

    public static boolean isForestryClass(String className) {
        return isClassFromSupportedJar(className, "forestry_1.12.2-5.8.2.427.jar");
    }

    public static boolean isFTBLibClass(String className) {
        return isClassFromSupportedJar(className, "FTBLib-5.4.7.2.jar");
    }

    public static boolean isHadEnoughItemsClass(String className) {
        return isClassFromSupportedJar(className, "HadEnoughItems_1.12.2-4.28.1.jar");
    }

    public static boolean isImmersiveEngineeringClass(String className) {
        return isClassFromSupportedJar(className, "ImmersiveEngineering-0.12-98.jar");
    }

    public static boolean isJustEnoughResourcesClass(String className) {
        return isClassFromSupportedJar(className, "JustEnoughResources-1.12.2-0.9.2.60.jar");
    }

    public static boolean isLibVulpesClass(String className) {
        return isClassFromSupportedJar(className, "libvulpes-0.5.1.jar");
    }

    public static boolean isModularMachineryClass(String className) {
        return isClassFromSupportedJar(className, "ModularMachinery-CE-2.3.2.jar");
    }

    public static boolean isOpenComputersClass(String className) {
        return isClassFromSupportedJar(className, "OpenComputers-MC1.12.2-1.8.7+2502094.jar");
    }

    public static boolean isProjectEClass(String className) {
        return isClassFromSupportedJar(className, "ProjectE-1.12.2-PE1.4.1.jar");
    }

    public static boolean isRailcraftClass(String className) {
        return isClassFromSupportedJar(className, "railcraft-12.1.0-beta-8.jar");
    }

    public static boolean isRebornCoreClass(String className) {
        return isClassFromSupportedJar(className, "RebornCore-FORK-1.12.2-3.19.5-universal.jar");
    }

    public static boolean isRenderLibClass(String className) {
        return isClassFromSupportedJar(className, "RenderLib-1.12.2-1.4.5.jar");
    }

    public static boolean isTechRebornClass(String className) {
        return isClassFromSupportedJar(className, "TechReborn-1.12.2-2.27.3.1084-universal.jar");
    }

    public static boolean isThaumcraftClass(String className) {
        return isClassFromSupportedJar(className, "Thaumcraft-1.12.2-6.1.BETA26.jar");
    }

    public static boolean isThermalExpansionClass(String className) {
        return isClassFromSupportedJar(className, "ThermalExpansion-1.12.2-5.5.7.1-universal.jar");
    }

    public static boolean isThaumcraftTargetAvailable() {
        return isClassFromSupportedJar("thaumcraft.common.Thaumcraft", "Thaumcraft-1.12.2-6.1.BETA26.jar");
    }

    public static boolean isTechRebornSuiteAvailable() {
        return isClassFromSupportedJar("techreborn.Core", "TechReborn-1.12.2-2.27.3.1084-universal.jar")
                && isClassFromSupportedJar("reborncore.api.scriba.TileRegistrationManager", "RebornCore-FORK-1.12.2-3.19.5-universal.jar");
    }

    public static boolean isUnlimitedChiselWorksClass(String className) {
        return isClassFromSupportedJar(className, "UnlimitedChiselWorks-0.3.5.jar");
    }

    public static boolean isGpomClass(String className) {
        return className != null && className.startsWith("com.l.gpom.");
    }

    private static boolean isClassFromSupportedJar(String className, String jarName) {
        if (className == null || jarName == null) {
            return false;
        }
        return isSupportedJarPresent(jarName);
    }

    private static boolean isClassFromSupportedJar(Class<?> type, String jarName) {
        if (type == null || jarName == null) {
            return false;
        }
        try {
            CodeSource codeSource = type.getProtectionDomain() != null ? type.getProtectionDomain().getCodeSource() : null;
            URL location = codeSource != null ? codeSource.getLocation() : null;
            if (location != null && location.toString().contains('/' + jarName)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall back to the source list below.
        }
        return isSupportedJarPresent(jarName);
    }

    private static boolean isSupportedJarPresent(String jarName) {
        Boolean cached = JAR_PRESENT_CACHE.get(jarName);
        if (cached != null) {
            return cached;
        }

        boolean matches = false;
        String classPath = System.getProperty("java.class.path", "");
        if (containsJarName(classPath, jarName)) {
            matches = true;
        }

        if (!matches) {
            matches = launchClassLoaderHasSource(jarName);
        }

        if (!matches) {
            matches = activeModsDirectoryHasJar(jarName);
        }

        JAR_PRESENT_CACHE.put(jarName, matches);
        return matches;
    }

    private static boolean launchClassLoaderHasSource(String jarName) {
        try {
            if (Launch.classLoader == null) {
                return false;
            }

            for (URL source : Launch.classLoader.getSources()) {
                if (source != null && containsJarName(source.toString(), jarName)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static boolean activeModsDirectoryHasJar(String jarName) {
        if (modsDirectoryHasJar(new File(System.getProperty("user.dir", ""), "mods"), jarName)) {
            return true;
        }

        String mcDir = System.getProperty("minecraft.client.jar");
        if (mcDir != null) {
            File clientJar = new File(mcDir);
            File parent = clientJar.getParentFile();
            if (parent != null && modsDirectoryHasJar(new File(parent, "mods"), jarName)) {
                return true;
            }
        }

        return false;
    }

    private static boolean modsDirectoryHasJar(File modsDirectory, String jarName) {
        try {
            return modsDirectory != null
                    && modsDirectory.isDirectory()
                    && new File(modsDirectory, jarName).isFile();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean containsJarName(String value, String jarName) {
        return value != null && value.indexOf(jarName) >= 0;
    }
}
