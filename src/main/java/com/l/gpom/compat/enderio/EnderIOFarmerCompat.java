package com.l.gpom.compat.enderio;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionLookup;
import crazypants.enderio.api.farm.AbstractFarmerJoe;
import crazypants.enderio.api.farm.FarmingAction;
import crazypants.enderio.api.farm.IFarmer;
import crazypants.enderio.api.farm.IFarmerJoe;
import crazypants.enderio.api.farm.IFarmingTool;
import crazypants.enderio.api.farm.IHarvestResult;
import crazypants.enderio.base.farming.farmers.HarvestResult;
import crazypants.enderio.base.farming.registry.Commune;
import crazypants.enderio.base.farming.registry.Registry;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.registries.IForgeRegistry;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EnderIOFarmerCompat {
    private static final String AGRICRAFT_CROP_CLASS =
            "com.infinityraider.agricraft.api.v1.crop.IAgriCrop";
    private static final String TINKERS_TOOL_HELPER_CLASS =
            "slimeknights.tconstruct.library.utils.ToolHelper";
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final AgriCraftCropFarmer AGRICRAFT_FARMER = new AgriCraftCropFarmer();

    private static volatile boolean agriCraftLookupComplete;
    private static volatile Class<?> agriCraftCropClass;
    private static volatile Method agriCraftIsCrossCrop;
    private static volatile Method agriCraftCanHarvest;
    private static volatile Method agriCraftHarvest;

    private static volatile boolean tinkersLookupComplete;
    private static volatile Method tinkersFortuneLevel;

    private static volatile Field farmerRegistryField;
    private static volatile boolean farmerRegistryLookupComplete;
    private static volatile Method ignoreTreeHarvestMethod;
    private static volatile boolean ignoreTreeHarvestLookupComplete;
    private static volatile IFarmerJoe[] orderedFarmers;

    private EnderIOFarmerCompat() {
    }

    public static int applyTinkersLuck(int originalLevel, IFarmer farmer, IFarmingTool tool) {
        if (farmer == null || tool == null || !resolveTinkers()) {
            return originalLevel;
        }
        ItemStack stack = farmer.getTool(tool);
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return originalLevel;
        }
        try {
            Object value = tinkersFortuneLevel.invoke(null, stack);
            return value instanceof Number
                    ? Math.max(originalLevel, ((Number) value).intValue())
                    : originalLevel;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            logOnce("tinkers-luck", "Could not read the Tinkers luck modifier", exception);
            return originalLevel;
        }
    }

    public static IHarvestResult harvestWithCachedHandlers(
            Commune commune,
            IFarmer farm,
            BlockPos pos,
            IBlockState state
    ) {
        Object agriCraftCrop = GpomEarlyConfig.enderIOFarmerAgriCraftCropSticksEnabled()
                ? cropAt(farm, pos)
                : null;
        if (agriCraftCrop != null) {
            // Never let EnderIO's generic crop handlers break an AgriCraft crop tile. Mature crops
            // are harvested through AgriCraft's API; immature crops and cross-crops are left untouched.
            if (isAgriCraftCrossCrop(agriCraftCrop)) {
                return null;
            }
            return harvestWithFarmer(commune, AGRICRAFT_FARMER, farm, pos, state);
        }
        if (!GpomEarlyConfig.enderIOFarmerOptimizedHarvestEnabled()) {
            return Registry.foreach(farmer -> harvestWithFarmerOriginal(commune, farmer, farm, pos, state));
        }
        IFarmerJoe[] farmers = getOrderedFarmers();
        if (farmers == null) {
            return Registry.foreach(farmer -> harvestWithFarmer(commune, farmer, farm, pos, state));
        }
        for (IFarmerJoe farmer : farmers) {
            IHarvestResult result = harvestWithFarmer(commune, farmer, farm, pos, state);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private static IHarvestResult harvestWithFarmerOriginal(
            Commune commune,
            IFarmerJoe farmer,
            IFarmer farm,
            BlockPos pos,
            IBlockState state
    ) {
        if (farmer == null || ignoreTreeHarvest(commune, farm, pos, farmer)
                || !farmer.canHarvest(farm, pos, state)) {
            return null;
        }
        return farmer.harvestBlock(farm, pos, state);
    }

    private static IHarvestResult harvestWithFarmer(
            Commune commune,
            IFarmerJoe farmer,
            IFarmer farm,
            BlockPos pos,
            IBlockState state
    ) {
        if (farmer == null) {
            return null;
        }
        try {
            if (ignoreTreeHarvest(commune, farm, pos, farmer) || !farmer.canHarvest(farm, pos, state)) {
                return null;
            }
        } catch (RuntimeException | LinkageError exception) {
            logOnce("harvest-check:" + farmerName(farmer),
                    "EnderIO farmer harvest check failed: " + farmerName(farmer), exception);
            return null;
        }
        try {
            return sanitizeHarvestResult(farmer, pos, farmer.harvestBlock(farm, pos, state));
        } catch (RuntimeException | LinkageError exception) {
            logOnce("harvest-action:" + farmerName(farmer),
                    "EnderIO farmer harvest action failed: " + farmerName(farmer), exception);
            // A handler may have already changed the crop before failing. Claim the position so a
            // later handler cannot harvest the same block a second time during this station action.
            return new HarvestResult(pos);
        }
    }

    private static IHarvestResult sanitizeHarvestResult(IFarmerJoe farmer, BlockPos fallbackPos, IHarvestResult result) {
        if (result == null) {
            return null;
        }
        NonNullList<?> drops;
        NonNullList<?> harvestedBlocks;
        try {
            drops = result.getDrops();
            harvestedBlocks = result.getHarvestedBlocks();
        } catch (RuntimeException | LinkageError exception) {
            logOnce("invalid-result:" + farmerName(farmer),
                    "EnderIO farmer returned an unreadable harvest result: " + farmerName(farmer), exception);
            return new HarvestResult(fallbackPos);
        }

        boolean repair = drops == null || harvestedBlocks == null;
        if (!repair) {
            for (Object harvestedBlock : harvestedBlocks) {
                if (!(harvestedBlock instanceof BlockPos)) {
                    repair = true;
                    break;
                }
            }
        }
        if (!repair) {
            for (Object drop : drops) {
                if (!isValidDropPair(drop)) {
                    repair = true;
                    break;
                }
            }
        }
        if (!repair) {
            return result;
        }

        HarvestResult repaired = new HarvestResult();
        if (harvestedBlocks != null) {
            for (Object harvestedBlock : harvestedBlocks) {
                if (harvestedBlock instanceof BlockPos) {
                    repaired.getHarvestedBlocks().add((BlockPos) harvestedBlock);
                }
            }
        }
        if (drops != null) {
            for (Object drop : drops) {
                if (drop instanceof Pair) {
                    Pair<?, ?> pair = (Pair<?, ?>) drop;
                    if (pair.getLeft() instanceof BlockPos && pair.getRight() instanceof ItemStack) {
                        addDropIfPresent(repaired, (BlockPos) pair.getLeft(), (ItemStack) pair.getRight());
                    }
                } else if (drop instanceof EntityItem) {
                    addDropIfPresent(repaired, fallbackPos,
                            MinecraftMappingCompat.entityItemStack((EntityItem) drop));
                }
            }
        }
        if (repaired.getHarvestedBlocks().isEmpty() && repaired.getDrops().isEmpty()) {
            repaired.getHarvestedBlocks().add(fallbackPos);
        }
        logOnce("repaired-result:" + farmerName(farmer),
                "Repaired a malformed EnderIO harvest result from " + farmerName(farmer), null);
        return repaired;
    }

    private static boolean isValidDropPair(Object drop) {
        if (!(drop instanceof Pair)) {
            return false;
        }
        Pair<?, ?> pair = (Pair<?, ?>) drop;
        return pair.getLeft() instanceof BlockPos && pair.getRight() instanceof ItemStack;
    }

    private static void addDropIfPresent(HarvestResult result, BlockPos pos, ItemStack stack) {
        if (pos != null && !MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            result.addDrop(pos, stack);
        }
    }

    private static boolean ignoreTreeHarvest(Commune commune, IFarmer farm, BlockPos pos, IFarmerJoe farmer) {
        Method method = resolveIgnoreTreeHarvest();
        if (method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(commune, farm, pos, farmer));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            logOnce("ignore-tree-harvest", "Could not apply EnderIO's disabled-tree harvest filter", exception);
            return false;
        }
    }

    private static IFarmerJoe[] getOrderedFarmers() {
        IFarmerJoe[] cached = orderedFarmers;
        if (cached != null) {
            return cached;
        }
        IForgeRegistry<IFarmerJoe> registry = resolveFarmerRegistry();
        if (registry == null) {
            return null;
        }
        Collection<IFarmerJoe> values = registry.getValuesCollection();
        if (values == null || values.isEmpty()) {
            return null;
        }
        List<IFarmerJoe> sorted = new ArrayList<>(values);
        sorted.sort(Comparator
                .comparing(IFarmerJoe::getPriority)
                .thenComparing(EnderIOFarmerCompat::farmerName));
        cached = sorted.toArray(new IFarmerJoe[0]);
        orderedFarmers = cached;
        return cached;
    }

    @SuppressWarnings("unchecked")
    private static IForgeRegistry<IFarmerJoe> resolveFarmerRegistry() {
        if (!farmerRegistryLookupComplete) {
            synchronized (EnderIOFarmerCompat.class) {
                if (!farmerRegistryLookupComplete) {
                    try {
                        farmerRegistryField = ReflectionLookup.findField(
                                Registry.class, new String[]{"REGISTRY"});
                    } catch (NoSuchFieldException | RuntimeException exception) {
                        logOnce("farmer-registry", "Could not cache EnderIO's farmer registry", exception);
                    }
                    farmerRegistryLookupComplete = true;
                }
            }
        }
        if (farmerRegistryField == null) {
            return null;
        }
        try {
            Object value = farmerRegistryField.get(null);
            return value instanceof IForgeRegistry ? (IForgeRegistry<IFarmerJoe>) value : null;
        } catch (IllegalAccessException | RuntimeException exception) {
            logOnce("farmer-registry-value", "Could not read EnderIO's farmer registry", exception);
            return null;
        }
    }

    private static Method resolveIgnoreTreeHarvest() {
        if (!ignoreTreeHarvestLookupComplete) {
            synchronized (EnderIOFarmerCompat.class) {
                if (!ignoreTreeHarvestLookupComplete) {
                    try {
                        ignoreTreeHarvestMethod = ReflectionLookup.findMethod(
                                Commune.class,
                                new String[]{"ignoreTreeHarvest"},
                                IFarmer.class,
                                BlockPos.class,
                                IFarmerJoe.class
                        );
                    } catch (NoSuchMethodException | RuntimeException exception) {
                        logOnce("ignore-tree-method", "Could not resolve EnderIO's disabled-tree harvest filter", exception);
                    }
                    ignoreTreeHarvestLookupComplete = true;
                }
            }
        }
        return ignoreTreeHarvestMethod;
    }

    private static boolean resolveAgriCraft() {
        if (!agriCraftLookupComplete) {
            synchronized (EnderIOFarmerCompat.class) {
                if (!agriCraftLookupComplete) {
                    try {
                        ClassLoader loader = EnderIOFarmerCompat.class.getClassLoader();
                        agriCraftCropClass = Class.forName(AGRICRAFT_CROP_CLASS, false, loader);
                        agriCraftIsCrossCrop = ReflectionLookup.findMethod(
                                agriCraftCropClass, new String[]{"isCrossCrop"});
                        agriCraftCanHarvest = ReflectionLookup.findMethod(
                                agriCraftCropClass, new String[]{"canBeHarvested"});
                        agriCraftHarvest = ReflectionLookup.findMethod(
                                agriCraftCropClass,
                                new String[]{"onHarvest"},
                                Consumer.class,
                                EntityPlayer.class
                        );
                    } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError exception) {
                        agriCraftCropClass = null;
                        agriCraftIsCrossCrop = null;
                        agriCraftCanHarvest = null;
                        agriCraftHarvest = null;
                        logOnce("agricraft-api", "Could not resolve the AgriCraft crop API", exception);
                    }
                    agriCraftLookupComplete = true;
                }
            }
        }
        return agriCraftCropClass != null
                && agriCraftIsCrossCrop != null
                && agriCraftCanHarvest != null
                && agriCraftHarvest != null;
    }

    private static boolean resolveTinkers() {
        if (!tinkersLookupComplete) {
            synchronized (EnderIOFarmerCompat.class) {
                if (!tinkersLookupComplete) {
                    try {
                        ClassLoader loader = EnderIOFarmerCompat.class.getClassLoader();
                        Class<?> helper = Class.forName(TINKERS_TOOL_HELPER_CLASS, false, loader);
                        tinkersFortuneLevel = ReflectionLookup.findMethod(
                                helper, new String[]{"getFortuneLevel"}, ItemStack.class);
                    } catch (ClassNotFoundException | NoSuchMethodException | RuntimeException | LinkageError exception) {
                        tinkersFortuneLevel = null;
                        logOnce("tinkers-api", "Could not resolve the Tinkers fortune helper", exception);
                    }
                    tinkersLookupComplete = true;
                }
            }
        }
        return tinkersFortuneLevel != null;
    }

    private static String farmerName(IFarmerJoe farmer) {
        ResourceLocation name = farmer == null ? null : farmer.getRegistryName();
        return name == null ? farmer == null ? "unknown" : farmer.getClass().getName() : name.toString();
    }

    private static void logOnce(String key, String message, Throwable throwable) {
        if (!LOGGED_FAILURES.add(key)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[GPOM EnderIO Farmer] {}", message);
        } else {
            GPOM.LOGGER.warn("[GPOM EnderIO Farmer] {}", message, throwable);
        }
    }

    private static final class AgriCraftCropFarmer extends AbstractFarmerJoe {
        @Override
        public boolean canPlant(ItemStack stack) {
            return false;
        }

        @Override
        public Result tryPrepareBlock(IFarmer farm, BlockPos pos, IBlockState state) {
            return Result.NEXT;
        }

        @Override
        public boolean canHarvest(IFarmer farm, BlockPos pos, IBlockState state) {
            Object crop = EnderIOFarmerCompat.cropAt(farm, pos);
            if (crop == null || EnderIOFarmerCompat.isAgriCraftCrossCrop(crop)) {
                return false;
            }
            try {
                return Boolean.TRUE.equals(agriCraftCanHarvest.invoke(crop));
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
                logOnce("agricraft-can-harvest", "AgriCraft rejected a crop harvest check", exception);
                return false;
            }
        }

        @Override
        public IHarvestResult harvestBlock(IFarmer farm, BlockPos pos, IBlockState state) {
            Object crop = EnderIOFarmerCompat.cropAt(farm, pos);
            IFarmingTool hoe = IFarmingTool.Tools.HOE;
            if (crop == null || !canHarvest(farm, pos, state)
                    || !farm.checkAction(FarmingAction.HARVEST, hoe)) {
                return null;
            }
            List<ItemStack> drops = new ArrayList<>();
            boolean changed = false;
            try {
                Object methodResult = agriCraftHarvest.invoke(
                        crop,
                        (Consumer<ItemStack>) drops::add,
                        farm.getFakePlayer()
                );
                changed = methodResult instanceof Enum
                        && "SUCCESS".equals(((Enum<?>) methodResult).name());
            } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
                logOnce("agricraft-harvest", "AgriCraft failed while harvesting a crop-stick crop", exception);
                changed = true;
            }
            if (!changed && drops.isEmpty()) {
                return null;
            }
            int fortune = Math.max(0, farm.getLootingValue(hoe));
            Random random = MinecraftMappingCompat.worldRandom(farm.getWorld());
            HarvestResult result = new HarvestResult(pos);
            int baseItems = 0;
            int finalItems = 0;
            for (ItemStack drop : drops) {
                if (MinecraftMappingCompat.itemStackIsEmpty(drop)) {
                    continue;
                }
                baseItems += MinecraftMappingCompat.itemStackCount(drop);
                ItemStack adjusted = applyFortune(drop, fortune, random);
                finalItems += MinecraftMappingCompat.itemStackCount(adjusted);
                addDropIfPresent(result, pos, adjusted);
            }
            farm.registerAction(FarmingAction.HARVEST, hoe);
            if (LOGGED_FAILURES.add("agricraft-fortune-report")) {
                GPOM.LOGGER.info(
                        "[GPOM EnderIO Farmer] AgriCraft harvest used fortune={} baseItems={} finalItems={}",
                        fortune,
                        baseItems,
                        finalItems
                );
            }
            return result;
        }
    }

    private static Object cropAt(IFarmer farm, BlockPos pos) {
        if (farm == null || pos == null || !resolveAgriCraft()) {
            return null;
        }
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(farm.getWorld(), pos);
        return tile != null && agriCraftCropClass.isInstance(tile) ? tile : null;
    }

    private static boolean isAgriCraftCrossCrop(Object crop) {
        if (crop == null || !resolveAgriCraft()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(agriCraftIsCrossCrop.invoke(crop));
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError exception) {
            logOnce("agricraft-cross-crop", "Could not read AgriCraft crop-stick breed-mode state", exception);
            return true;
        }
    }

    private static ItemStack applyFortune(ItemStack drop, int fortune, Random random) {
        ItemStack adjusted = MinecraftMappingCompat.itemStackCopy(drop);
        if (fortune <= 0 || random == null || MinecraftMappingCompat.itemStackIsEmpty(adjusted)) {
            return adjusted;
        }
        int bonus = random.nextInt(fortune + 2) - 1;
        if (bonus > 0) {
            long count = (long) MinecraftMappingCompat.itemStackCount(adjusted) * (bonus + 1L);
            MinecraftMappingCompat.itemStackSetCount(adjusted, (int) Math.min(Integer.MAX_VALUE, count));
        }
        return adjusted;
    }
}
