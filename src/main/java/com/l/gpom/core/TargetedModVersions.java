package com.l.gpom.core;

public final class TargetedModVersions {
    public static final String DEPENDENCIES =
            "after:aoa3;" +
            "after:advancedrocketry;" +
            "after:abyssalcraft;" +
            "after:acintegration;" +
            "after:actuallyadditions;" +
            "after:actuallybaubles;" +
            "after:aquaacrobatics;" +
            "after:astralsorcery;" +
            "after:baubles;" +
            "after:betterquesting;" +
            "after:binniecore;" +
            "after:bookshelf;" +
            "after:brandonscore;" +
            "after:buildcraftbuilders;" +
            "after:buildcraftcompat;" +
            "after:buildcraftcore;" +
            "after:buildcraftenergy;" +
            "after:buildcraftfactory;" +
            "after:buildcraftlib;" +
            "after:buildcraftsilicon;" +
            "after:buildcrafttransport;" +
            "after:chisel;" +
            "after:citnbt;" +
            "after:codechickenlib;" +
            "after:cofhcore;" +
            "after:cofhworld;" +
            "after:crafttweaker;" +
            "after:ctm;" +
            "after:draconicevolution;" +
            "after:dimstages;" +
            "after:endercore;" +
            "after:enderio;" +
            "after:enderiointegrationtic;" +
            "after:endertweaker;" +
            "after:immersiveengineering;" +
            "after:environmentaltech;" +
            "after:expequiv;" +
            "after:extrautils2;" +
            "after:fermiumbooter;" +
            "after:extratrees;" +
            "after:forestry;" +
            "after:ftblib;" +
            "after:fugue;" +
            "after:gamestages;" +
            "after:gendustry;" +
            "after:hammercore;" +
            "after:itemstages;" +
            "after:itemblacklist;" +
            "after:jei;" +
            "after:jeresources;" +
            "after:key_binding_patch;" +
            "after:libvulpes;" +
            "after:mantle;" +
            "after:concheckrmd;" +
            "after:modularmachinery;" +
            "after:modtweaker;" +
            "after:mobstages;" +
            "after:mtlib;" +
            "after:natura;" +
            "after:opencomputers;" +
            "after:projecte;" +
            "after:railcraft;" +
            "after:redcore;" +
            "after:redstoneflux;" +
            "after:reborncore;" +
            "after:recipestages;" +
            "after:renderlib;" +
            "after:smoothfont;" +
            "after:techreborn;" +
            "after:thaumcraft;" +
            "after:thermalexpansion;" +
            "after:thermalfoundation;" +
            "after:theoneprobe;" +
            "after:thebetweenlands;" +
            "after:tconstruct;" +
            "after:tombstone;" +
            "after:twilightforest;" +
            "after:unlimitedchiselworks";

    private TargetedModVersions() {
    }

    public static boolean isAdventOfAscensionClass(Class<?> type) {
        return isClassFromSupportedJar(type, "AoA3-3.3.6.jar");
    }

    public static boolean isAdventOfAscensionClass(String className) {
        return isClassFromSupportedJar(className, "AoA3-3.3.6.jar");
    }

    public static boolean isAdvancedRocketryClass(Class<?> type) {
        return isClassFromSupportedJar(type, "advancedrocketry-2.2.5hotfix.jar");
    }

    public static boolean isAdvancedRocketryClass(String className) {
        return isClassFromSupportedJar(className, "advancedrocketry-2.2.5hotfix.jar");
    }

    public static boolean isAquaAcrobaticsClass(String className) {
        return isClassFromSupportedJar(className, "AquaAcrobatics-1.15.4.jar");
    }

    public static boolean isAstralSorceryClass(String className) {
        return isClassFromSupportedJar(className, "astralsorcery-1.12.2-1.10.27.jar");
    }

    public static boolean isBaublesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Baubles-1.12-1.5.2.jar");
    }

    public static boolean isAbyssalCraftClass(String className) {
        return isClassFromSupportedJar(className, "AbyssalCraft-1.12.2-1.11.2.jar");
    }

    public static boolean isAbyssalCraftClass(Class<?> type) {
        return isClassFromSupportedJar(type, "AbyssalCraft-1.12.2-1.11.2.jar");
    }

    public static boolean isAbyssalCraftIntegrationClass(String className) {
        return isClassFromSupportedJar(className, "AbyssalCraft Integration-1.12.2-1.11.3.jar");
    }

    public static boolean isAbyssalCraftIntegrationClass(Class<?> type) {
        return isClassFromSupportedJar(type, "AbyssalCraft Integration-1.12.2-1.11.3.jar");
    }

    public static boolean isActuallyAdditionsClass(String className) {
        return isClassFromSupportedJar(className, "ActuallyAdditions-1.12.2-r152.jar");
    }

    public static boolean isActuallyAdditionsClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ActuallyAdditions-1.12.2-r152.jar");
    }

    public static boolean isActuallyBaublesClass(String className) {
        return isClassFromSupportedJar(className, "ActuallyBaubles-1.12-1.1.jar");
    }

    public static boolean isActuallyBaublesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ActuallyBaubles-1.12-1.1.jar");
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

    public static boolean isBookshelfClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Bookshelf-1.12.2-2.3.590.jar");
    }

    public static boolean isBookshelfClass(String className) {
        return isClassFromSupportedJar(className, "Bookshelf-1.12.2-2.3.590.jar");
    }

    public static boolean isBuildCraftCoreClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-core-8.0.0.jar");
    }

    public static boolean isBuildCraftCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-core-8.0.0.jar");
    }

    public static boolean isBuildCraftBuildersClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-builders-8.0.0.jar");
    }

    public static boolean isBuildCraftBuildersClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-builders-8.0.0.jar");
    }

    public static boolean isBuildCraftCompatClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-compat-8.0.0.jar");
    }

    public static boolean isBuildCraftCompatClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-compat-8.0.0.jar");
    }

    public static boolean isBuildCraftEnergyClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-energy-8.0.0.jar");
    }

    public static boolean isBuildCraftEnergyClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-energy-8.0.0.jar");
    }

    public static boolean isBuildCraftFactoryClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-factory-8.0.0.jar");
    }

    public static boolean isBuildCraftFactoryClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-factory-8.0.0.jar");
    }

    public static boolean isBuildCraftLibClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-core-8.0.0.jar");
    }

    public static boolean isBuildCraftLibClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-core-8.0.0.jar");
    }

    public static boolean isBuildCraftSiliconClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-silicon-8.0.0.jar");
    }

    public static boolean isBuildCraftSiliconClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-silicon-8.0.0.jar");
    }

    public static boolean isBuildCraftTransportClass(String className) {
        return isClassFromSupportedJar(className, "buildcraft-transport-8.0.0.jar");
    }

    public static boolean isBuildCraftTransportClass(Class<?> type) {
        return isClassFromSupportedJar(type, "buildcraft-transport-8.0.0.jar");
    }

    public static boolean isBrandonsCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "BrandonsCore-1.12.2-2.4.20.162-universal.jar");
    }

    public static boolean isBrandonsCoreClass(String className) {
        return isClassFromSupportedJar(className, "BrandonsCore-1.12.2-2.4.20.162-universal.jar");
    }

    public static boolean isCitNbtClass(String className) {
        return isClassFromSupportedJar(className, "citnbt-0.3.2.jar");
    }

    public static boolean isChiselClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Chisel-MC1.12.2-1.0.2.45.jar");
    }

    public static boolean isCodeChickenLibClass(Class<?> type) {
        return isClassFromSupportedJar(type, "CodeChickenLib-1.12.2-3.2.3.358-universal.jar");
    }

    public static boolean isCodeChickenLibClass(String className) {
        return isClassFromSupportedJar(className, "CodeChickenLib-1.12.2-3.2.3.358-universal.jar");
    }

    public static boolean isCoFHCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "CoFHCore-1.12.2-4.6.6.1-universal.jar");
    }

    public static boolean isCoFHCoreClass(String className) {
        return isClassFromSupportedJar(className, "CoFHCore-1.12.2-4.6.6.1-universal.jar");
    }

    public static boolean isCoFHWorldClass(Class<?> type) {
        return isClassFromSupportedJar(type, "CoFHWorld-1.12.2-1.4.0.1-universal.jar");
    }

    public static boolean isCoFHWorldClass(String className) {
        return isClassFromSupportedJar(className, "CoFHWorld-1.12.2-1.4.0.1-universal.jar");
    }

    public static boolean isCraftTweakerClass(String className) {
        return isClassFromSupportedJar(className, "CraftTweaker2-1.12-4.1.20.711.jar");
    }

    public static boolean isCraftTweakerClass(Class<?> type) {
        return isClassFromSupportedJar(type, "CraftTweaker2-1.12-4.1.20.711.jar");
    }

    public static boolean isDraconicEvolutionClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Draconic-Evolution-1.12.2-2.3.28.354-universal.jar");
    }

    public static boolean isDraconicEvolutionClass(String className) {
        return isClassFromSupportedJar(className, "Draconic-Evolution-1.12.2-2.3.28.354-universal.jar");
    }

    public static boolean isDimensionStagesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "DimensionStages-1.12.2-2.0.23.jar");
    }

    public static boolean isDimensionStagesClass(String className) {
        return isClassFromSupportedJar(className, "DimensionStages-1.12.2-2.0.23.jar");
    }

    public static boolean isConnectedTexturesModClass(String className) {
        return isClassFromSupportedJar(className, "CTM-MC1.12.2-1.0.2.31.jar");
    }

    public static boolean isConnectedTexturesModClass(Class<?> type) {
        return isClassFromSupportedJar(type, "CTM-MC1.12.2-1.0.2.31.jar");
    }

    public static boolean isEnderCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "EnderCore-1.12.2-0.5.78.jar");
    }

    public static boolean isEnderIOClass(Class<?> type) {
        return isClassFromSupportedJar(type, "EnderIO-1.12.2-5.3.72.jar");
    }

    public static boolean isEnderIOClass(String className) {
        return isClassFromSupportedJar(className, "EnderIO-1.12.2-5.3.72.jar");
    }

    public static boolean isEnderTweakerClass(Class<?> type) {
        return isClassFromSupportedJar(type, "EnderTweaker-1.12.2-1.2.3.jar");
    }

    public static boolean isEnderTweakerClass(String className) {
        return isClassFromSupportedJar(className, "EnderTweaker-1.12.2-1.2.3.jar");
    }

    public static boolean isEnvironmentalTechClass(String className) {
        return isClassFromSupportedJar(className, "environmentaltech-1.12.2-2.0.20.1.jar");
    }

    public static boolean isIntegratedDynamicsClass(String className) {
        return isClassFromSupportedJar(className, "IntegratedDynamics-1.12.2-1.1.11.jar");
    }

    public static boolean isErebusClass(String className) {
        return isClassFromSupportedJar(className, "Erebus-1.0.32.jar");
    }

    public static boolean isAgriCraftClass(String className) {
        return isClassFromSupportedJar(className, "agricraft-2.12.0-1.12.2-b2.jar");
    }

    public static boolean isExpandedEquivalenceClass(String className) {
        return isClassFromSupportedJar(className, "ExpandedEquivalence-1.12.2-12.3.17.jar");
    }

    public static boolean isExtraUtilities2Class(String className) {
        return isClassFromSupportedJar(className, "extrautils2-1.12-1.9.9.jar");
    }

    public static boolean isFermiumBooterClass(Class<?> type) {
        return isClassFromSupportedJar(type, "`FermiumBooter-1.3.1.jar");
    }

    public static boolean isFermiumBooterClass(String className) {
        return isClassFromSupportedJar(className, "`FermiumBooter-1.3.1.jar");
    }

    public static boolean isForestryClass(String className) {
        return isClassFromSupportedJar(className, "forestry_1.12.2-5.8.2.427.jar");
    }

    public static boolean isForestryClass(Class<?> type) {
        return isClassFromSupportedJar(type, "forestry_1.12.2-5.8.2.427.jar");
    }

    public static boolean isForgeRuntimeClass(Class<?> type) {
        return isClassFromSupportedJar(type, "forge-1.12.2-14.23.5.2860-universal.jar")
                || isClassFromSupportedJar(type, "cleanroom-0.3.24-alpha.jar")
                || isClassFromSupportedJar(type, "cleanroom-0.5.12-alpha-universal.jar");
    }

    public static boolean isFTBLibClass(String className) {
        return isClassFromSupportedJar(className, "FTBLib-5.4.7.2.jar");
    }

    public static boolean isFugueClass(Class<?> type) {
        return isClassFromSupportedJar(type, "+Fugue-0.21.0.jar");
    }

    public static boolean isFugueClass(String className) {
        return isClassFromSupportedJar(className, "+Fugue-0.21.0.jar");
    }

    public static boolean isGameStagesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "GameStages-1.12.2-2.0.123.jar");
    }

    public static boolean isGameStagesClass(String className) {
        return isClassFromSupportedJar(className, "GameStages-1.12.2-2.0.123.jar");
    }

    public static boolean isGendustryClass(String className) {
        return isClassFromSupportedJar(className, "gendustry-1.6.5.8-mc1.12.2.jar");
    }

    public static boolean isHadEnoughItemsClass(String className) {
        return isClassFromSupportedJar(className, "HadEnoughItems_1.12.2-4.28.1.jar");
    }

    public static boolean isHadEnoughItemsClass(Class<?> type) {
        return isClassFromSupportedJar(type, "HadEnoughItems_1.12.2-4.28.1.jar");
    }

    public static boolean isHammerCoreClass(String className) {
        return isClassFromSupportedJar(className, "HammerLib-1.12.2-12.2.50.jar");
    }

    public static boolean isHammerCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "HammerLib-1.12.2-12.2.50.jar");
    }

    public static boolean isItemBlacklistClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ItemBlacklist-1.4.3.jar");
    }

    public static boolean isItemBlacklistClass(String className) {
        return isClassFromSupportedJar(className, "ItemBlacklist-1.4.3.jar");
    }

    public static boolean isItemStagesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ItemStages-1.12.2-2.0.49.jar");
    }

    public static boolean isItemStagesClass(String className) {
        return isClassFromSupportedJar(className, "ItemStages-1.12.2-2.0.49.jar");
    }

    public static boolean isImmersiveEngineeringClass(String className) {
        return isClassFromSupportedJar(className, "ImmersiveEngineering-0.12-98.jar");
    }

    public static boolean isImmersiveEngineeringClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ImmersiveEngineering-0.12-98.jar");
    }

    public static boolean isJustEnoughResourcesClass(String className) {
        return isClassFromSupportedJar(className, "JustEnoughResources-1.12.2-0.9.2.60.jar");
    }

    public static boolean isKeyBindingPatchClass(Class<?> type) {
        return isClassFromSupportedJar(type, "[MC-1.12.2] Key Binding Patch v1.3.3.3 - 2024-12-1.jar");
    }

    public static boolean isKeyBindingPatchClass(String className) {
        return isClassFromSupportedJar(className, "[MC-1.12.2] Key Binding Patch v1.3.3.3 - 2024-12-1.jar");
    }

    public static boolean isLibVulpesClass(String className) {
        return isClassFromSupportedJar(className, "libvulpes-0.5.1.jar");
    }

    public static boolean isLibVulpesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "libvulpes-0.5.1.jar");
    }

    public static boolean isLoliAsmClass(String className) {
        return isClassFromSupportedJar(className, "loliasm-5.31.jar");
    }

    public static boolean isMantleClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Mantle-1.12-1.3.3.55.jar");
    }

    public static boolean isMantleClass(String className) {
        return isClassFromSupportedJar(className, "Mantle-1.12-1.3.3.55.jar");
    }

    public static boolean isModularMachineryClass(String className) {
        return isClassFromSupportedJar(className, "ModularMachinery-CE-2.3.2.jar");
    }

    public static boolean isModTweakerClass(Class<?> type) {
        return isClassFromSupportedJar(type, "modtweaker-4.0.20.11.jar");
    }

    public static boolean isModTweakerClass(String className) {
        return isClassFromSupportedJar(className, "modtweaker-4.0.20.11.jar");
    }

    public static boolean isMobStagesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "MobStages-1.12.2-2.0.8.jar");
    }

    public static boolean isMobStagesClass(String className) {
        return isClassFromSupportedJar(className, "MobStages-1.12.2-2.0.8.jar");
    }

    public static boolean isMtLibClass(Class<?> type) {
        return isClassFromSupportedJar(type, "MTLib-3.0.7.jar");
    }

    public static boolean isMtLibClass(String className) {
        return isClassFromSupportedJar(className, "MTLib-3.0.7.jar");
    }

    public static boolean isOpenComputersClass(String className) {
        return isClassFromSupportedJar(className, "OpenComputers-MC1.12.2-1.8.7+2502094.jar");
    }

    public static boolean isNuclearCraftClass(String className) {
        return isClassFromSupportedJar(className, "nuclearcraft-1.12.2-2.19a.jar");
    }

    public static boolean isQuantumThingsClass(String className) {
        return isClassFromSupportedJar(className, "QuantumThings-MC-1.12.2-1.1.0.jar");
    }

    public static boolean isNaturaClass(String className) {
        return isClassFromSupportedJar(className, "natura-1.12.2-4.3.2.69.jar");
    }

    public static boolean isNaturaClass(Class<?> type) {
        return isClassFromSupportedJar(type, "natura-1.12.2-4.3.2.69.jar");
    }

    public static boolean isProjectEClass(String className) {
        return isClassFromSupportedJar(className, "ProjectE-1.12.2-PE1.4.1.jar");
    }

    public static boolean isModpackConfigCheckerClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Modpack Configuration Checker-1.12.2-v1.9.5.jar");
    }

    public static boolean isModpackConfigCheckerClass(String className) {
        return isClassFromSupportedJar(className, "Modpack Configuration Checker-1.12.2-v1.9.5.jar");
    }

    public static boolean isRailcraftClass(String className) {
        return isClassFromSupportedJar(className, "railcraft-12.1.0-beta-8.jar");
    }

    public static boolean isRailcraftClass(Class<?> type) {
        return isClassFromSupportedJar(type, "railcraft-12.1.0-beta-8.jar");
    }

    public static boolean isRFToolsClass(String className) {
        return isClassFromSupportedJar(className, "rftools-1.12-7.73.jar");
    }

    public static boolean isRedCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "!Red-Core-MC-1.8-1.12-0.7.jar");
    }

    public static boolean isRedCoreClass(String className) {
        return isClassFromSupportedJar(className, "!Red-Core-MC-1.8-1.12-0.7.jar");
    }

    public static boolean isRedstoneFluxClass(Class<?> type) {
        return isClassFromSupportedJar(type, "RedstoneFlux-1.12-2.1.1.1-universal.jar");
    }

    public static boolean isRedstoneFluxClass(String className) {
        return isClassFromSupportedJar(className, "RedstoneFlux-1.12-2.1.1.1-universal.jar");
    }

    public static boolean isRebornCoreClass(String className) {
        return isClassFromSupportedJar(className, "RebornCore-FORK-1.12.2-3.19.5-universal.jar");
    }

    public static boolean isRebornCoreClass(Class<?> type) {
        return isClassFromSupportedJar(type, "RebornCore-FORK-1.12.2-3.19.5-universal.jar");
    }

    public static boolean isRecipeStagesClass(Class<?> type) {
        return isClassFromSupportedJar(type, "RecipeStages-1.1.3.8.jar");
    }

    public static boolean isRecipeStagesClass(String className) {
        return isClassFromSupportedJar(className, "RecipeStages-1.1.3.8.jar");
    }

    public static boolean isRenderLibClass(String className) {
        return isClassFromSupportedJar(className, "RenderLib-1.12.2-1.4.5.jar");
    }

    public static boolean isSmoothFontClass(Class<?> type) {
        return isClassFromSupportedJar(type, "SmoothFont-mc1.12.2-2.1.4.jar");
    }

    public static boolean isSmoothFontClass(String className) {
        return isClassFromSupportedJar(className, "SmoothFont-mc1.12.2-2.1.4.jar");
    }

    public static boolean isTechRebornClass(String className) {
        return isClassFromSupportedJar(className, "TechReborn-1.12.2-2.27.3.1084-universal.jar");
    }

    public static boolean isTechRebornClass(Class<?> type) {
        return isClassFromSupportedJar(type, "TechReborn-1.12.2-2.27.3.1084-universal.jar");
    }

    public static boolean isTConstructClass(Class<?> type) {
        return isClassFromSupportedJar(type, "TinkersAntique-1.12.2-2.13.0.205.jar");
    }

    public static boolean isTConstructClass(String className) {
        return isClassFromSupportedJar(className, "TinkersAntique-1.12.2-2.13.0.205.jar");
    }

    public static boolean isThaumcraftClass(String className) {
        return isClassFromSupportedJar(className, "Thaumcraft-1.12.2-6.1.BETA26.jar");
    }

    public static boolean isThaumcraftClass(Class<?> type) {
        return isClassFromSupportedJar(type, "Thaumcraft-1.12.2-6.1.BETA26.jar");
    }

    public static boolean isThermalExpansionClass(String className) {
        return isClassFromSupportedJar(className, "ThermalExpansion-1.12.2-5.5.7.1-universal.jar");
    }

    public static boolean isThermalExpansionClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ThermalExpansion-1.12.2-5.5.7.1-universal.jar");
    }

    public static boolean isThermalFoundationClass(Class<?> type) {
        return isClassFromSupportedJar(type, "ThermalFoundation-1.12.2-2.6.7.1-universal.jar");
    }

    public static boolean isThermalFoundationClass(String className) {
        return isClassFromSupportedJar(className, "ThermalFoundation-1.12.2-2.6.7.1-universal.jar");
    }

    public static boolean isTheOneProbeClass(String className) {
        return isClassFromSupportedJar(className, "theoneprobe-1.12-1.4.28.jar");
    }

    public static boolean isTheOneProbeClass(Class<?> type) {
        return isClassFromSupportedJar(type, "theoneprobe-1.12-1.4.28.jar");
    }

    public static boolean isTombstoneClass(Class<?> type) {
        return isClassFromSupportedJar(type, "tombstone-1.12.2-4.7.5.jar");
    }

    public static boolean isTombstoneClass(String className) {
        return isClassFromSupportedJar(className, "tombstone-1.12.2-4.7.5.jar");
    }

    public static boolean isTwilightForestClass(Class<?> type) {
        return isClassFromSupportedJar(type, "twilightforest-1.12.2-3.11.1021-universal.jar");
    }

    public static boolean isTwilightForestClass(String className) {
        return isClassFromSupportedJar(className, "twilightforest-1.12.2-3.11.1021-universal.jar");
    }

    public static boolean isThaumcraftTargetAvailable() {
        return isClassFromSupportedJar("thaumcraft.Thaumcraft", "Thaumcraft-1.12.2-6.1.BETA26.jar");
    }

    public static boolean isTechRebornSuiteAvailable() {
        return isClassFromSupportedJar("techreborn.Core", "TechReborn-1.12.2-2.27.3.1084-universal.jar")
                && isClassFromSupportedJar("reborncore.api.scriba.TileRegistrationManager", "RebornCore-FORK-1.12.2-3.19.5-universal.jar");
    }

    public static boolean isUnlimitedChiselWorksClass(String className) {
        return isClassFromSupportedJar(className, "UnlimitedChiselWorks-0.3.5.jar");
    }

    public static boolean isVintageFixClass(String className) {
        return isClassFromSupportedJar(className, "vintagefix-0.6.2.jar");
    }

    public static boolean isGpomClass(String className) {
        return className != null && className.startsWith("com.l.gpom.");
    }

    private static boolean isClassFromSupportedJar(String className, String jarName) {
        return className != null && !className.trim().isEmpty();
    }

    private static boolean isClassFromSupportedJar(Class<?> type, String jarName) {
        return type != null;
    }
}
