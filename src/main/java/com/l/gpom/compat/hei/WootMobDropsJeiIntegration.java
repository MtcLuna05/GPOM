package com.l.gpom.compat.hei;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class WootMobDropsJeiIntegration {
    private static final String WOOT_MOD_ID = "woot";
    private static final String WOOT_MAIN_CLASS = "ipsis.Woot";
    private static final String WOOT_MOB_NAME_BUILDER = "ipsis.woot.util.WootMobNameBuilder";
    private static final String WOOT_ENCHANT_ENUM = "ipsis.woot.util.EnumEnchantKey";
    private static final String WOOT_LOOT_LOADER = "ipsis.woot.configuration.loaders.FactoryLootLoader";
    private static final String WOOT_LOOT_LOAD_INTERFACE = "ipsis.woot.loot.repository.ILootRepositoryLoad";
    private static final String WOOT_FILES_CLASS = "ipsis.woot.reference.Files";
    private static final String WOOT_CONFIG_KEY_CLASS = "ipsis.woot.configuration.EnumConfigKey";
    private static final String WOOT_BLOOD_MAGIC_HELPER = "ipsis.woot.plugins.bloodmagic.BloodMagicHelper";
    private static final ResourceLocation ENDER_SHARD = new ResourceLocation("woot", "endershard");
    private static final ResourceLocation FACTORY_SHARD = new ResourceLocation("woot", "shard");
    private static final ResourceLocation UPGRADE_B = new ResourceLocation("woot", "upgradeb");
    private static final ResourceLocation FACTORY_CONTROLLER = new ResourceLocation("woot", "controller");
    private static final ResourceLocation FACTORY_HEART = new ResourceLocation("woot", "factory");
    private static final ResourceLocation FACTORY_STRUCTURE = new ResourceLocation("woot", "structure");
    private static final ResourceLocation EBWIZARDRY_SPELL_BOOK =
            new ResourceLocation("ebwizardry", "spell_book");
    private static final ResourceLocation EBWIZARDRY_RUINED_SPELL_BOOK =
            new ResourceLocation("ebwizardry", "ruined_spell_book");
    private static final ResourceLocation THAUMCRAFT_CRYSTAL_ESSENCE =
            new ResourceLocation("thaumcraft", "crystal_essence");
    private static final String[] PRIMAL_ASPECT_FIELDS = {
            "AIR", "FIRE", "WATER", "EARTH", "ORDER", "ENTROPY"
    };
    private static volatile RecipeCache recipeCache;

    private WootMobDropsJeiIntegration() {
    }

    static boolean available() {
        boolean available = Loader.isModLoaded(WOOT_MOD_ID);
        WootJeiDiagnostics.log("Woot availability check: {}", available);
        return available;
    }

    static void registerCategory(IRecipeCategoryRegistration registration) {
        WootJeiDiagnostics.log("Adding recipe category {}", WootMobDropsRecipeCategory.UID);
        registration.addRecipeCategories(new WootMobDropsRecipeCategory(
                registration.getJeiHelpers().getGuiHelper()));
        WootJeiDiagnostics.log("Recipe category added: {}", WootMobDropsRecipeCategory.UID);
    }

    static void registerRecipes(IModRegistry registry) {
        try {
            WootJeiDiagnostics.log("Beginning Woot recipe/catalyst registration");
            List<WootMobDropsRecipeWrapper> recipes = loadRecipes();
            WootJeiDiagnostics.log("Calling IModRegistry.addRecipes with {} page(s)", recipes.size());
            registry.addRecipes(recipes, WootMobDropsRecipeCategory.UID);
            WootJeiDiagnostics.log("IModRegistry.addRecipes returned successfully");

            registerCatalyst(registry, FACTORY_CONTROLLER);
            registerCatalyst(registry, FACTORY_HEART);
            registerCatalyst(registry, ENDER_SHARD);

            GPOM.LOGGER.info("[GPOM HEI QoL] Registered {} Woot mob-drop recipe page(s)", recipes.size());
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Woot recipe registration failed", throwable);
            GPOM.LOGGER.warn("[GPOM HEI QoL] Could not register Woot mob drops; leaving the optional category empty", throwable);
        }
    }

    private static void registerCatalyst(IModRegistry registry, ResourceLocation itemId) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        WootJeiDiagnostics.log("Catalyst lookup {} -> {}", itemId,
                item == null ? "missing" : MinecraftMappingCompat.itemRegistryName(item));
        if (item != null) {
            registry.addRecipeCatalyst(new ItemStack(item), WootMobDropsRecipeCategory.UID);
            WootJeiDiagnostics.log("Catalyst registered: {}", itemId);
        }
    }

    static int resolveFarmTier(String serializedMobName) {
        try {
            ClassLoader loader = WootMobDropsJeiIntegration.class.getClassLoader();
            Class<?> wootClass = Class.forName(WOOT_MAIN_CLASS, false, loader);
            Object configuration = field(wootClass, "wootConfiguration").get(null);
            Class<?> mobNameBuilder = Class.forName(WOOT_MOB_NAME_BUILDER, false, loader);
            Object mobName = mobNameBuilder.getMethod("create", String.class).invoke(null, serializedMobName);
            return farmTier(wootClass, configuration, mobName, serializedMobName,
                    MinecraftMappingCompat.minecraftWorld(MinecraftMappingCompat.minecraftInstance()));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static List<WootMobDropsRecipeWrapper> loadRecipes() throws ReflectiveOperationException {
        ClassLoader loader = WootMobDropsJeiIntegration.class.getClassLoader();
        String fingerprint = recipeInputFingerprint(loader);
        RecipeCache cached = recipeCache;
        if (cached != null && cached.fingerprint.equals(fingerprint)) {
            WootJeiDiagnostics.log("Woot recipe snapshot cache hit: {} page(s), fingerprint={}",
                    cached.recipes.size(), fingerprint);
            return cached.recipes;
        }
        synchronized (WootMobDropsJeiIntegration.class) {
            cached = recipeCache;
            if (cached != null && cached.fingerprint.equals(fingerprint)) {
                WootJeiDiagnostics.log("Woot recipe snapshot cache hit after lock: {} page(s)",
                        cached.recipes.size());
                return cached.recipes;
            }
            WootJeiDiagnostics.log("Woot recipe snapshot cache miss/invalidation: fingerprint={}", fingerprint);
            List<WootMobDropsRecipeWrapper> recipes = Collections.unmodifiableList(
                    loadRecipesUncached(loader));
            recipeCache = new RecipeCache(fingerprint, recipes);
            return recipes;
        }
    }

    private static List<WootMobDropsRecipeWrapper> loadRecipesUncached(ClassLoader loader)
            throws ReflectiveOperationException {
        Class<?> wootClass = Class.forName(WOOT_MAIN_CLASS, false, loader);
        Object liveRepository = field(wootClass, "lootRepository").get(null);
        Object customRepository = field(wootClass, "customDropsRepository").get(null);
        Object configuration = field(wootClass, "wootConfiguration").get(null);
        Object policyRepository = field(wootClass, "policyRepository").get(null);
        WootJeiDiagnostics.log(
                "Woot static state: liveRepository={}, customRepository={}, configuration={}, policyRepository={}",
                typeName(liveRepository), typeName(customRepository), typeName(configuration),
                typeName(policyRepository));
        if (liveRepository == null || customRepository == null || configuration == null
                || policyRepository == null) {
            WootJeiDiagnostics.log("Returning no recipes because one or more Woot static services are null");
            return Collections.emptyList();
        }

        // Woot only populates its learned-loot repository in FMLServerStartingEvent. HEI builds
        // its immutable recipe index before an integrated server exists, so reading the live
        // repository here makes the category empty. Load Woot's loot.json into a private
        // repository instead; the live server repository remains untouched and can load normally.
        Object learnedRepository = loadLearnedLootSnapshot(liveRepository, loader);
        WootJeiDiagnostics.log("Private learned-loot snapshot created: {}", typeName(learnedRepository));

        Class<?> mobNameBuilder = Class.forName(WOOT_MOB_NAME_BUILDER, false, loader);
        Method createMobName = mobNameBuilder.getMethod("create", String.class);
        Class<?> mobNameClass = createMobName.getReturnType();
        Method canGenerateFrom = policyRepository.getClass().getMethod("canGenerateFrom", mobNameClass);
        Method getAllLearnedMobs = learnedRepository.getClass().getMethod("getAllMobs");
        Method getAllCustomMobs = customRepository.getClass().getMethod("getAllMobs");

        Class<?> enchantClass = Class.forName(WOOT_ENCHANT_ENUM, false, loader);
        Object[] enchantments = enchantClass.getEnumConstants();
        Method getLearnedDrops = learnedRepository.getClass().getMethod("getDrops", mobNameClass, enchantClass);
        Method getCustomDrops = customRepository.getClass().getMethod("getDrops", mobNameClass, enchantClass);

        List<WootMobDropsRecipeWrapper> recipes = new ArrayList<>();
        Set<String> mobNames = new LinkedHashSet<>();
        Collection<?> learnedMobs = collection(getAllLearnedMobs.invoke(learnedRepository));
        Collection<?> customMobs = collection(getAllCustomMobs.invoke(customRepository));
        RegistryMobCounts registryCounts = addEligibleRegistryMobs(
                mobNames, policyRepository, canGenerateFrom, createMobName);
        int learnedAdded = addEligibleMobNames(
                mobNames, learnedMobs, policyRepository, canGenerateFrom, createMobName);
        int customAdded = addEligibleMobNames(
                mobNames, customMobs, policyRepository, canGenerateFrom, createMobName);
        WootJeiDiagnostics.log(
                "Mob enumeration: registeredLiving={}, eligibleRegistry={}, rejectedRegistry={}, "
                        + "learned={}, learnedAdded={}, custom={}, customAdded={}, uniqueEligible={}",
                registryCounts.registeredLiving, registryCounts.eligible, registryCounts.rejected,
                learnedMobs.size(), learnedAdded, customMobs.size(), customAdded, mobNames.size());
        WootLootTableExtractor.Result tableExtraction = WootLootTableExtractor.extract(mobNames);
        Map<String, List<WootMobDropsRecipeWrapper.DropInfo>> tableDrops = tableExtraction.dropsByMob;
        WootUnsupportedLootLearner.configure(tableExtraction.unsupportedFunctionsByMob);
        World farmTierWorld = WootLootTableExtractor.createFakeWorld();
        ItemStack controller = itemStack(FACTORY_CONTROLLER);
        ItemStack factory = itemStack(FACTORY_HEART);
        List<WootMobDropsRecipeWrapper.DropInfo> factoryBonuses = factoryShardDrops(configuration, loader);
        WootJeiDiagnostics.log("Lookup input stacks: controller={}, factory={}",
                stackName(controller), stackName(factory));
        for (String mobName : mobNames) {
            Object wootMobName = createMobName.invoke(null, mobName);
            String displayName = displayName(mobName);
            int farmTier = farmTier(wootClass, configuration, wootMobName, mobName, farmTierWorld);
            List<WootMobDropsRecipeWrapper.DropInfo> drops = drops(
                    learnedRepository, getLearnedDrops, customRepository, getCustomDrops,
                    wootMobName, enchantments);
            boolean hasLearnedMobDrops = !drops.isEmpty();
            int tableDerivedAdded = mergeTableDerivedDrops(drops, tableDrops.get(mobName));
            drops.addAll(factoryBonuses);
            drops.sort(Comparator.comparingInt(WootMobDropsJeiIntegration::dropSortGroup)
                    .thenComparing(WootMobDropsJeiIntegration::itemSortKey));
            BloodMagicSupport bloodMagic = bloodMagicSupport(
                    configuration, wootMobName, mobName, loader);
            ItemStack shard = programmedShard(mobName, displayName);
            ItemStack factoryCap = factoryCap(farmTier);
            WootJeiDiagnostics.log("Mob {}: displayName={}, tier={}, drops={}, learnedDrops={}, tableDerived={}, "
                            + "factoryBonuses={}, bloodMagicUpgrades={}, fluidOutputs={}, shard={}, cap={}",
                    mobName, displayName, farmTier, drops.size(), hasLearnedMobDrops,
                    tableDerivedAdded,
                    factoryBonuses.size(), bloodMagic.upgrades.size(),
                    bloodMagic.lifeEssenceOutputs.size(), stackName(shard), stackName(factoryCap));

            recipes.add(new WootMobDropsRecipeWrapper(
                    mobName, displayName, farmTier, shard, factoryCap, controller, factory, drops,
                    bloodMagic.upgrades, bloodMagic.lifeEssenceOutputs));
        }

        recipes.sort(Comparator.comparing(WootMobDropsRecipeWrapper::getDisplayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WootMobDropsRecipeWrapper::getMobName));
        WootJeiDiagnostics.log("Finished building {} Woot mob recipe(s)", recipes.size());
        return recipes;
    }

    private static String recipeInputFingerprint(ClassLoader loader) {
        StringBuilder fingerprint = new StringBuilder();
        try {
            Class<?> files = Class.forName(WOOT_FILES_CLASS, false, loader);
            appendFileSignature(fingerprint, (File) field(files, "lootFile").get(null));
            appendFileSignature(fingerprint, (File) field(files, "configFile").get(null));
            File configDirectory = (File) files.getMethod("getConfigDirectory").invoke(null);
            appendFileSignature(fingerprint, new File(configDirectory, "custom_drops.json"));
            appendFileSignature(fingerprint, new File(configDirectory, "factory_config.json"));
        } catch (Throwable throwable) {
            fingerprint.append("files-unavailable:").append(throwable.getClass().getName());
        }
        fingerprint.append(";entities=").append(ForgeRegistries.ENTITIES.getValuesCollection().size());
        fingerprint.append(";bloodmagic=").append(Loader.isModLoaded("bloodmagic"));
        return fingerprint.toString();
    }

    private static void appendFileSignature(StringBuilder target, File file) {
        if (file == null) {
            target.append(";null");
            return;
        }
        target.append(';').append(file.getAbsolutePath()).append(':')
                .append(file.exists()).append(':').append(file.length()).append(':')
                .append(file.lastModified());
    }

    private static Object loadLearnedLootSnapshot(Object liveRepository, ClassLoader loader)
            throws ReflectiveOperationException {
        Object snapshot = liveRepository.getClass().newInstance();
        Class<?> loadInterface = Class.forName(WOOT_LOOT_LOAD_INTERFACE, false, loader);
        Object lootLoader = Class.forName(WOOT_LOOT_LOADER, false, loader).newInstance();
        WootJeiDiagnostics.log("Invoking {}.loadConfig({})", lootLoader.getClass().getName(), loadInterface.getName());
        lootLoader.getClass().getMethod("loadConfig", loadInterface).invoke(lootLoader, snapshot);
        WootJeiDiagnostics.log("Woot FactoryLootLoader.loadConfig returned");
        return snapshot;
    }

    private static List<WootMobDropsRecipeWrapper.DropInfo> drops(Object learnedRepository,
                                                                   Method getLearnedDrops,
                                                                   Object customRepository,
                                                                   Method getCustomDrops,
                                                                   Object mobName,
                                                                   Object[] enchantments)
            throws ReflectiveOperationException {
        List<WootMobDropsRecipeWrapper.DropInfo> merged = new ArrayList<>();
        int levels = Math.min(4, enchantments.length);
        for (int looting = 0; looting < levels; looting++) {
            mergeDrops(merged, collection(getLearnedDrops.invoke(
                    learnedRepository, mobName, enchantments[looting])), looting);
            mergeDrops(merged, collection(getCustomDrops.invoke(
                    customRepository, mobName, enchantments[looting])), looting);
        }
        normalizeVariableDrops(merged);
        merged.sort(Comparator.comparing(WootMobDropsJeiIntegration::itemSortKey));
        return merged;
    }

    private static List<WootMobDropsRecipeWrapper.DropInfo> factoryShardDrops(
            Object configuration, ClassLoader loader) {
        List<WootMobDropsRecipeWrapper.DropInfo> drops = new ArrayList<>(3);
        try {
            Class<?> keyClass = Class.forName(WOOT_CONFIG_KEY_CLASS, false, loader);
            Method getBoolean = configuration.getClass().getMethod("getBoolean", keyClass);
            if (!Boolean.TRUE.equals(getBoolean.invoke(
                    configuration, enumValue(keyClass, "ALLOW_SHARD_RECIPES")))) {
                WootJeiDiagnostics.log("Factory shard outputs omitted because Woot disabled shard recipes");
                return drops;
            }
            Method getInteger = configuration.getClass().getMethod("getInteger", keyClass);
            addFactoryShard(drops, 4, 2, configInteger(
                    configuration, getInteger, keyClass, "T2_SHARD_GEN"));
            addFactoryShard(drops, 5, 3, configInteger(
                    configuration, getInteger, keyClass, "T3_SHARD_GEN"));
            addFactoryShard(drops, 6, 4, configInteger(
                    configuration, getInteger, keyClass, "T4_SHARD_GEN"));
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Could not resolve Woot factory shard outputs", throwable);
        }
        return Collections.unmodifiableList(drops);
    }

    private static void addFactoryShard(List<WootMobDropsRecipeWrapper.DropInfo> target,
                                        int metadata,
                                        int shardTier,
                                        int configuredChance) {
        Item item = ForgeRegistries.ITEMS.getValue(FACTORY_SHARD);
        if (item == null || configuredChance < 0) {
            return;
        }
        WootMobDropsRecipeWrapper.DropInfo drop =
                new WootMobDropsRecipeWrapper.DropInfo(new ItemStack(item, 1, metadata));
        int minimumFarmTier = shardTier - 1;
        double actualChance = 100.0D * (Math.min(100, configuredChance) + 1.0D) / 101.0D;
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_GRAY + "Woot factory shard bonus");
        tooltip.add(TextFormatting.GRAY + "Generated by Farm Tier "
                + TextFormatting.WHITE + WootMobDropsRecipeWrapper.roman(minimumFarmTier)
                + TextFormatting.GRAY + " or higher");
        tooltip.add(TextFormatting.GRAY + "Configured chance: " + TextFormatting.WHITE
                + configuredChance + "% per factory cycle");
        tooltip.add(TextFormatting.DARK_GRAY + "Woot's inclusive 0..100 roll: "
                + String.format(Locale.ROOT, "%.2f%% actual", actualChance));
        tooltip.add(TextFormatting.DARK_GRAY + "Independent of Looting and other shard rolls");
        drop.setSpecialTooltip(tooltip);
        target.add(drop);
    }

    private static BloodMagicSupport bloodMagicSupport(Object configuration,
                                                        Object mobName,
                                                        String serializedMobName,
                                                        ClassLoader loader) {
        if (!Loader.isModLoaded("bloodmagic")) {
            return BloodMagicSupport.EMPTY;
        }
        Item upgradeItem = ForgeRegistries.ITEMS.getValue(UPGRADE_B);
        if (upgradeItem == null) {
            return BloodMagicSupport.EMPTY;
        }
        try {
            Class<?> keyClass = Class.forName(WOOT_CONFIG_KEY_CLASS, false, loader);
            Method getBoolean = configuration.getClass().getMethod("getBoolean", keyClass);
            Method getInteger = configuration.getClass().getMethod(
                    "getInteger", mobName.getClass(), keyClass);
            int sacrificeRatio = lifeEssenceRatio(mobName, loader);
            List<WootMobDropsRecipeWrapper.UpgradeInfo> upgrades = new ArrayList<>(3);
            List<FluidStack> lifeEssence = new ArrayList<>(3);

            if (configBoolean(configuration, getBoolean, keyClass, "ALLOW_BM_LE_TANK")) {
                int[] params = mobConfigIntegers(
                        configuration, getInteger, mobName, keyClass, "BM_LE_TANK", "PARAM");
                int[] power = mobConfigIntegers(
                        configuration, getInteger, mobName, keyClass, "BM_LE_TANK", "POWER_TICK");
                upgrades.add(tankUpgrade(upgradeItem, params, power, sacrificeRatio));
                Fluid fluid = FluidRegistry.getFluid("lifeessence");
                if (fluid != null && sacrificeRatio > 0) {
                    for (int param : params) {
                        int amount = (int) ((1.0F + 0.1F * param) * sacrificeRatio);
                        if (amount > 0) {
                            lifeEssence.add(new FluidStack(fluid, amount));
                        }
                    }
                }
            }
            if (configBoolean(configuration, getBoolean, keyClass, "ALLOW_BM_LE_ALTAR")) {
                int[] params = mobConfigIntegers(
                        configuration, getInteger, mobName, keyClass, "BM_LE_ALTAR", "PARAM");
                int[] power = mobConfigIntegers(
                        configuration, getInteger, mobName, keyClass, "BM_LE_ALTAR", "POWER_TICK");
                upgrades.add(altarUpgrade(upgradeItem, params, power, sacrificeRatio));
            }
            if (configBoolean(configuration, getBoolean, keyClass, "ALLOW_BM_CRYSTAL")) {
                int[] params = mobConfigIntegers(
                        configuration, getInteger, mobName, keyClass, "BM_CRYSTAL", "PARAM");
                int[] power = mobConfigIntegers(
                        configuration, getInteger, mobName, keyClass, "BM_CRYSTAL", "POWER_TICK");
                upgrades.add(crystalUpgrade(upgradeItem, params, power));
            }
            return new BloodMagicSupport(upgrades, lifeEssence);
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error(
                    "Could not resolve Woot Blood Magic support for " + serializedMobName, throwable);
            return BloodMagicSupport.EMPTY;
        }
    }

    private static WootMobDropsRecipeWrapper.UpgradeInfo tankUpgrade(Item item,
                                                                      int[] params,
                                                                      int[] power,
                                                                      int sacrificeRatio) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_PURPLE + "Blood Magic: Sanguine Urn");
        tooltip.add(TextFormatting.GRAY + "Produces Life Essence in connected fluid outputs");
        tooltip.add(TextFormatting.DARK_GRAY + "Requires the Woot Sanguine Urn ritual keep-alive");
        if (sacrificeRatio <= 0) {
            tooltip.add(TextFormatting.RED + "This mob is sacrifice-blacklisted by Blood Magic");
        } else {
            for (int level = 0; level < 3; level++) {
                int amount = (int) ((1.0F + 0.1F * params[level]) * sacrificeRatio);
                tooltip.add(upgradeLine(level, amount + " mB/mob/cycle; "
                        + params[level] + " sacrifice runes; " + power[level] + " RF/t"));
            }
            tooltip.add(TextFormatting.DARK_GRAY + "Mass upgrades multiply the simulated mob count");
        }
        return new WootMobDropsRecipeWrapper.UpgradeInfo(upgradeStacks(item, 3), tooltip);
    }

    private static WootMobDropsRecipeWrapper.UpgradeInfo altarUpgrade(Item item,
                                                                       int[] params,
                                                                       int[] power,
                                                                       int sacrificeRatio) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_PURPLE + "Blood Magic: Mechanical Altar");
        tooltip.add(TextFormatting.GRAY + "Produces LP in a nearby Blood Altar via ritual");
        if (sacrificeRatio <= 0) {
            tooltip.add(TextFormatting.RED + "This mob is sacrifice-blacklisted by Blood Magic");
        } else {
            for (int level = 0; level < 3; level++) {
                int amount = (int) (sacrificeRatio / 100.0F * params[level]);
                tooltip.add(upgradeLine(level, amount + " LP/mob; " + params[level]
                        + "% sacrifice value; " + power[level] + " RF/t"));
            }
            tooltip.add(TextFormatting.DARK_GRAY + "Mass upgrades multiply altar sacrifice calls");
        }
        return new WootMobDropsRecipeWrapper.UpgradeInfo(upgradeStacks(item, 6), tooltip);
    }

    private static WootMobDropsRecipeWrapper.UpgradeInfo crystalUpgrade(Item item,
                                                                         int[] params,
                                                                         int[] power) {
        List<String> tooltip = new ArrayList<>();
        tooltip.add(TextFormatting.DARK_PURPLE + "Blood Magic: Cloned Soul");
        tooltip.add(TextFormatting.GRAY + "Produces Demon Will and Demon Crystal growth via ritual");
        for (int level = 0; level < 3; level++) {
            tooltip.add(upgradeLine(level, params[level]
                    + "% effective mob health; " + power[level] + " RF/t"));
        }
        tooltip.add(TextFormatting.DARK_GRAY + "Per scaled health: 1.75 will + 0.05 growth");
        tooltip.add(TextFormatting.DARK_GRAY + "Animals receive Woot's 4x will/growth modifier");
        tooltip.add(TextFormatting.DARK_GRAY + "Values are buffered into a nearby Demon Crystal");
        tooltip.add(TextFormatting.DARK_GRAY + "Mass upgrades multiply the simulated mob count");
        return new WootMobDropsRecipeWrapper.UpgradeInfo(upgradeStacks(item, 12), tooltip);
    }

    private static String upgradeLine(int zeroBasedLevel, String text) {
        return TextFormatting.GRAY + WootMobDropsRecipeWrapper.roman(zeroBasedLevel + 1)
                + ": " + TextFormatting.WHITE + text;
    }

    private static List<ItemStack> upgradeStacks(Item item, int firstMetadata) {
        List<ItemStack> stacks = new ArrayList<>(3);
        for (int offset = 0; offset < 3; offset++) {
            stacks.add(new ItemStack(item, 1, firstMetadata + offset));
        }
        return stacks;
    }

    private static int lifeEssenceRatio(Object mobName, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> helper = Class.forName(WOOT_BLOOD_MAGIC_HELPER, false, loader);
        return ((Number) helper.getMethod("getLifeEssenceRatio", mobName.getClass())
                .invoke(null, mobName)).intValue();
    }

    private static boolean configBoolean(Object configuration,
                                         Method method,
                                         Class<?> keyClass,
                                         String name) throws ReflectiveOperationException {
        return Boolean.TRUE.equals(method.invoke(configuration, enumValue(keyClass, name)));
    }

    private static int[] mobConfigIntegers(Object configuration,
                                           Method method,
                                           Object mobName,
                                           Class<?> keyClass,
                                           String prefix,
                                           String suffix) throws ReflectiveOperationException {
        int[] values = new int[3];
        for (int level = 1; level <= 3; level++) {
            Object key = enumValue(keyClass, prefix + '_' + level + '_' + suffix);
            values[level - 1] = ((Number) method.invoke(configuration, mobName, key)).intValue();
        }
        return values;
    }

    private static void mergeDrops(List<WootMobDropsRecipeWrapper.DropInfo> merged,
                                   Collection<?> values,
                                   int looting) throws ReflectiveOperationException {
        for (Object value : values) {
            ItemStack stack = (ItemStack) field(value.getClass(), "itemStack").get(value);
            if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
                continue;
            }
            WootMobDropsRecipeWrapper.DropInfo info = findDrop(merged, stack);
            if (info == null) {
                info = new WootMobDropsRecipeWrapper.DropInfo(stack);
                merged.add(info);
            }
            int chance = field(value.getClass(), "dropChance").getInt(value);
            Map<Integer, Integer> sizes = integerMap(field(value.getClass(), "sizes").get(value));
            info.setLootingData(looting, chance, sizes);
        }
    }

    private static int mergeTableDerivedDrops(
            List<WootMobDropsRecipeWrapper.DropInfo> merged,
            List<WootMobDropsRecipeWrapper.DropInfo> derived) {
        if (derived == null || derived.isEmpty()) {
            return 0;
        }
        int added = 0;
        for (WootMobDropsRecipeWrapper.DropInfo candidate : derived) {
            boolean known = false;
            for (ItemStack stack : candidate.getStacks()) {
                if (findDrop(merged, stack) != null) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                merged.add(candidate);
                added++;
            }
        }
        return added;
    }

    private static ItemStack itemStack(ResourceLocation itemId) {
        return itemStack(itemId, 0);
    }

    private static ItemStack itemStack(ResourceLocation itemId, int metadata) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        return item == null ? MinecraftMappingCompat.emptyStack() : new ItemStack(item, 1, metadata);
    }

    private static ItemStack factoryCap(int farmTier) {
        return farmTier >= 1 && farmTier <= 4
                ? itemStack(FACTORY_STRUCTURE, farmTier + 5)
                : MinecraftMappingCompat.emptyStack();
    }

    private static RegistryMobCounts addEligibleRegistryMobs(Set<String> target,
                                                              Object policyRepository,
                                                              Method canGenerateFrom,
                                                              Method createMobName)
            throws ReflectiveOperationException {
        RegistryMobCounts counts = new RegistryMobCounts();
        for (EntityEntry entry : ForgeRegistries.ENTITIES.getValuesCollection()) {
            Class<?> entityClass = entry.getEntityClass();
            ResourceLocation registryName = entry.getRegistryName();
            if (registryName == null || entityClass == null
                    || !EntityLiving.class.isAssignableFrom(entityClass)) {
                continue;
            }
            counts.registeredLiving++;
            String serializedName = registryName.toString();
            Object mobName = createMobName.invoke(null, serializedName);
            if (Boolean.TRUE.equals(canGenerateFrom.invoke(policyRepository, mobName))) {
                target.add(serializedName);
                counts.eligible++;
            } else {
                counts.rejected++;
            }
        }
        return counts;
    }

    private static int addEligibleMobNames(Set<String> target,
                                           Collection<?> values,
                                           Object policyRepository,
                                           Method canGenerateFrom,
                                           Method createMobName)
            throws ReflectiveOperationException {
        int added = 0;
        for (Object value : values) {
            if (value != null) {
                String serializedName = String.valueOf(value);
                Object mobName = createMobName.invoke(null, serializedName);
                if (Boolean.TRUE.equals(canGenerateFrom.invoke(policyRepository, mobName))
                        && target.add(serializedName)) {
                    added++;
                }
            }
        }
        return added;
    }

    private static WootMobDropsRecipeWrapper.DropInfo findDrop(
            List<WootMobDropsRecipeWrapper.DropInfo> drops,
            ItemStack candidate) {
        for (WootMobDropsRecipeWrapper.DropInfo drop : drops) {
            for (ItemStack existing : drop.getStacks()) {
                if (MinecraftMappingCompat.itemStacksSameItemAndTags(existing, candidate)) {
                    return drop;
                }
            }
        }
        return null;
    }

    private static void normalizeVariableDrops(List<WootMobDropsRecipeWrapper.DropInfo> drops) {
        for (WootMobDropsRecipeWrapper.DropInfo drop : drops) {
            ItemStack stack = drop.getStack();
            Item item = MinecraftMappingCompat.itemStackItem(stack);
            ResourceLocation itemId = MinecraftMappingCompat.itemRegistryName(item);
            NBTTagCompound tag = MinecraftMappingCompat.itemStackTagCompound(stack);
            if (!THAUMCRAFT_CRYSTAL_ESSENCE.equals(itemId)
                    || !MinecraftMappingCompat.nbtIsEmpty(tag)) {
                continue;
            }
            List<ItemStack> alternatives = thaumcraftPrimalCrystals();
            if (alternatives.size() == PRIMAL_ASPECT_FIELDS.length) {
                drop.setStacks(alternatives, "Any primal vis aspect");
                WootJeiDiagnostics.log(
                        "Expanded unspecified Thaumcraft crystal into {} primal-aspect alternatives",
                        alternatives.size());
            } else {
                WootJeiDiagnostics.log(
                        "Could not expand unspecified Thaumcraft crystal: resolved {} of {} alternatives",
                        alternatives.size(), PRIMAL_ASPECT_FIELDS.length);
            }
        }
    }

    private static List<ItemStack> thaumcraftPrimalCrystals() {
        List<ItemStack> crystals = new ArrayList<>(PRIMAL_ASPECT_FIELDS.length);
        try {
            ClassLoader loader = WootMobDropsJeiIntegration.class.getClassLoader();
            Class<?> aspectClass = Class.forName("thaumcraft.api.aspects.Aspect", false, loader);
            Class<?> helperClass = Class.forName("thaumcraft.api.ThaumcraftApiHelper", false, loader);
            Method makeCrystal = helperClass.getMethod("makeCrystal", aspectClass);
            for (String fieldName : PRIMAL_ASPECT_FIELDS) {
                Object aspect = field(aspectClass, fieldName).get(null);
                Object value = makeCrystal.invoke(null, aspect);
                if (value instanceof ItemStack
                        && !MinecraftMappingCompat.itemStackIsEmpty((ItemStack) value)) {
                    crystals.add((ItemStack) value);
                }
            }
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Failed to create Thaumcraft primal crystal alternatives", throwable);
        }
        return crystals;
    }

    private static String itemSortKey(WootMobDropsRecipeWrapper.DropInfo drop) {
        ItemStack stack = drop.getStack();
        Item item = MinecraftMappingCompat.itemStackItem(stack);
        ResourceLocation id = MinecraftMappingCompat.itemRegistryName(item);
        return (id == null ? "" : id.toString()) + ':' + MinecraftMappingCompat.itemStackMetadata(stack);
    }

    private static int dropSortGroup(WootMobDropsRecipeWrapper.DropInfo drop) {
        ItemStack stack = drop.getStack();
        Item item = MinecraftMappingCompat.itemStackItem(stack);
        ResourceLocation id = MinecraftMappingCompat.itemRegistryName(item);
        return EBWIZARDRY_SPELL_BOOK.equals(id) || EBWIZARDRY_RUINED_SPELL_BOOK.equals(id) ? 1 : 0;
    }

    private static int farmTier(Class<?> wootClass,
                                Object configuration,
                                Object mobName,
                                String serializedMobName,
                                World fallbackWorld) {
        try {
            Class<?> keyClass = Class.forName("ipsis.woot.configuration.EnumConfigKey", false,
                    WootMobDropsJeiIntegration.class.getClassLoader());
            Object factoryTierKey = enumValue(keyClass, "FACTORY_TIER");
            Method getInteger = configuration.getClass().getMethod(
                    "getInteger", mobName.getClass(), keyClass);
            Object configuredTier = getInteger.invoke(configuration, mobName, factoryTierKey);
            if (configuredTier instanceof Number && ((Number) configuredTier).intValue() > 0) {
                return ((Number) configuredTier).intValue();
            }
        } catch (Throwable ignored) {
            // Fall through to Woot's world-sensitive calculation and legacy cached values.
        }

        World world = fallbackWorld != null ? fallbackWorld
                : MinecraftMappingCompat.minecraftWorld(MinecraftMappingCompat.minecraftInstance());
        if (world != null) {
            try {
                Method getFactoryTier = configuration.getClass().getMethod(
                        "getFactoryTier", World.class, mobName.getClass());
                Object tier = getFactoryTier.invoke(configuration, world, mobName);
                if (tier != null) {
                    return ((Number) tier.getClass().getMethod("getLevel").invoke(tier)).intValue();
                }
            } catch (Throwable ignored) {
                // Use Woot's loaded config/cache below if a particular modded entity cannot be constructed.
            }
        }

        try {
            Map<?, ?> mobValues = map(field(configuration.getClass(), "integerMobMap").get(configuration));
            Object configuredTier = mobValues.get(serializedMobName + ":FACTORY_TIER");
            if (configuredTier instanceof Number) {
                return ((Number) configuredTier).intValue();
            }

            Number spawnCost = number(mobValues.get(serializedMobName + ":SPAWN_UNITS"));
            if (spawnCost == null) {
                Object mobCosting = field(wootClass, "mobCosting").get(null);
                Map<?, ?> health = map(field(mobCosting.getClass(), "mobHealthMap").get(mobCosting));
                spawnCost = number(health.get(serializedMobName));
            }
            if (spawnCost != null) {
                return tierFromSpawnCost(configuration, spawnCost.intValue());
            }
        } catch (Throwable ignored) {
            // The tier can be retried lazily once a client world exists.
        }
        return 0;
    }

    private static int tierFromSpawnCost(Object configuration, int spawnCost) throws ReflectiveOperationException {
        Class<?> keyClass = Class.forName("ipsis.woot.configuration.EnumConfigKey", false,
                WootMobDropsJeiIntegration.class.getClassLoader());
        Method getInteger = configuration.getClass().getMethod("getInteger", keyClass);
        int tierTwo = configInteger(configuration, getInteger, keyClass, "T2_UNITS_MAX");
        int tierThree = configInteger(configuration, getInteger, keyClass, "T3_UNITS_MAX");
        int tierFour = configInteger(configuration, getInteger, keyClass, "T4_UNITS_MAX");
        if (spawnCost >= tierFour) {
            return 4;
        }
        if (spawnCost >= tierThree) {
            return 3;
        }
        if (spawnCost >= tierTwo) {
            return 2;
        }
        return 1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int configInteger(Object configuration, Method method, Class<?> enumClass, String name)
            throws ReflectiveOperationException {
        Object key = enumValue(enumClass, name);
        return ((Number) method.invoke(configuration, key)).intValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> enumClass, String name) {
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
    }

    private static ItemStack programmedShard(String mobName, String displayName) {
        Item shardItem = ForgeRegistries.ITEMS.getValue(ENDER_SHARD);
        if (shardItem == null) {
            return MinecraftMappingCompat.emptyStack();
        }
        ItemStack shard = new ItemStack(shardItem);
        int separator = mobName.indexOf(',');
        String entityKey = separator < 0 ? mobName : mobName.substring(0, separator);
        String tag = separator < 0 ? "" : mobName.substring(separator + 1);
        NBTTagCompound nbt = new NBTTagCompound();
        MinecraftMappingCompat.nbtSetString(nbt, "wootMobDisplayName", displayName);
        MinecraftMappingCompat.nbtSetInteger(nbt, "wootMobDeaths", Integer.MAX_VALUE);
        MinecraftMappingCompat.nbtSetString(nbt, "wootMobNameKey", entityKey);
        MinecraftMappingCompat.nbtSetString(nbt, "wootMobNameTag", tag);
        MinecraftMappingCompat.itemStackSetTagCompound(shard, nbt);
        return shard;
    }

    private static String displayName(String mobName) {
        String entityKey = mobName;
        int separator = entityKey.indexOf(',');
        if (separator >= 0) {
            entityKey = entityKey.substring(0, separator);
        }
        try {
            ResourceLocation id = new ResourceLocation(entityKey);
            String translation = MinecraftMappingCompat.entityTranslationName(id);
            if (translation == null) {
                return entityKey;
            }
            String key = "entity." + translation + ".name";
            String localized = com.l.gpom.client.ClientAccess.i18nFormat(key);
            if (!key.equals(localized)) {
                return localized;
            }
        } catch (Throwable ignored) {
            // Registry id is a stable fallback for unusual or removed entities.
        }
        return entityKey;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Class<?> current = owner;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + '.' + name);
    }

    private static Collection<?> collection(Object value) {
        return value instanceof Collection ? (Collection<?>) value : Collections.emptyList();
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : Collections.emptyMap();
    }

    private static Map<Integer, Integer> integerMap(Object value) {
        Map<Integer, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map(value).entrySet()) {
            if (entry.getKey() instanceof Number && entry.getValue() instanceof Number) {
                copy.put(((Number) entry.getKey()).intValue(), ((Number) entry.getValue()).intValue());
            }
        }
        return copy;
    }

    private static Number number(Object value) {
        return value instanceof Number ? (Number) value : null;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String stackName(ItemStack stack) {
        if (stack == null) {
            return "null";
        }
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return "empty";
        }
        Item item = MinecraftMappingCompat.itemStackItem(stack);
        ResourceLocation id = MinecraftMappingCompat.itemRegistryName(item);
        return String.valueOf(id) + ':' + MinecraftMappingCompat.itemStackMetadata(stack)
                + " nbt=" + MinecraftMappingCompat.itemStackTagCompound(stack);
    }

    private static final class RegistryMobCounts {
        private int registeredLiving;
        private int eligible;
        private int rejected;
    }

    private static final class RecipeCache {
        private final String fingerprint;
        private final List<WootMobDropsRecipeWrapper> recipes;

        private RecipeCache(String fingerprint, List<WootMobDropsRecipeWrapper> recipes) {
            this.fingerprint = fingerprint;
            this.recipes = recipes;
        }
    }

    private static final class BloodMagicSupport {
        private static final BloodMagicSupport EMPTY = new BloodMagicSupport(
                Collections.<WootMobDropsRecipeWrapper.UpgradeInfo>emptyList(),
                Collections.<FluidStack>emptyList());

        private final List<WootMobDropsRecipeWrapper.UpgradeInfo> upgrades;
        private final List<FluidStack> lifeEssenceOutputs;

        private BloodMagicSupport(List<WootMobDropsRecipeWrapper.UpgradeInfo> upgrades,
                                  List<FluidStack> lifeEssenceOutputs) {
            this.upgrades = Collections.unmodifiableList(new ArrayList<>(upgrades));
            List<FluidStack> fluidCopies = new ArrayList<>(lifeEssenceOutputs.size());
            for (FluidStack stack : lifeEssenceOutputs) {
                if (stack != null && stack.amount > 0) {
                    fluidCopies.add(stack.copy());
                }
            }
            this.lifeEssenceOutputs = Collections.unmodifiableList(fluidCopies);
        }
    }
}
