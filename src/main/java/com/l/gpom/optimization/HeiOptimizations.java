package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomCaches;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.entity.IMerchant;
import net.minecraft.village.MerchantRecipe;
import net.minecraft.village.MerchantRecipeList;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.DataOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public final class HeiOptimizations {
    private static final int HEI_ITEM_STACK_CACHE_VERSION = 2;
    private static final int HEI_ITEM_STACK_CACHE_MAGIC = 0x47504849;
    private static final int JER_VILLAGER_TRADE_CACHE_VERSION = 1;
    private static final int JER_VILLAGER_TRADE_CACHE_MAGIC = 0x47504A56;
    private static final int FORESTRY_BOTTLER_RECIPE_CACHE_VERSION = 1;
    private static final int FORESTRY_BOTTLER_RECIPE_CACHE_MAGIC = 0x47504642;
    private static final int EXTRATREES_LUMBERMILL_RECIPE_CACHE_VERSION = 1;
    private static final int EXTRATREES_LUMBERMILL_RECIPE_CACHE_MAGIC = 0x4750454C;
    private static final int THERMAL_TRANSPOSER_CONTAINER_CACHE_VERSION = 1;
    private static final int THERMAL_TRANSPOSER_CONTAINER_CACHE_MAGIC = 0x47505454;
    private static final boolean HEI_ITEM_STACK_CACHE = Boolean.parseBoolean(System.getProperty("gpom.hei.itemStackListCache", "true"));
    private static final int SEARCH_WORKERS = computeSearchWorkerCount();
    private static final ThreadLocal<RecipeProgress> RECIPE_PROGRESS = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> RECIPE_REGISTRY_BULK = new ThreadLocal<>();
    private static Method jerEnchantmentWrapperCreate;
    private static Method itemStackIsEmpty;
    private static Method itemStackGetItem;
    private static Method itemStackWriteToNbt;
    private static Method itemStackGetMetadata;
    private static Method itemStackGetCount;
    private static Method itemStackSetCount;
    private static Method itemStackCopy;
    private static Method itemStackIsDamageable;
    private static Method itemStackGetMaxDamage;
    private static Method itemStackGetItemDamage;
    private static Method itemStackSetItemDamage;
    private static Method merchantRecipeGetItemToBuy;
    private static Method merchantRecipeGetSecondItemToBuy;
    private static Method merchantRecipeGetItemToSell;
    private static Method nbtDataWrite;
    private static Method nbtDataRead;
    private static Method itemGetEnchantability;
    private static Object minecraftBookItem;
    private static Field jerVillagerProfessionCareers;
    private static Field jerVillagerCareerTrades;
    private static Field jerVillagerCareerId;
    private static Method jerGetPrivateValue;
    private static Constructor<?> jerTradeConstructor;
    private static Object jerFakeMerchant;
    private static final ConcurrentMap<String, Field> JER_PRIVATE_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, CachedJerTrade> JER_VILLAGER_TRADE_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> JER_FAILED_GENERATORS = ConcurrentHashMap.newKeySet();
    private static final Map<Object, Byte> HEI_FALLBACK_SUBTYPE_STATE = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final byte HEI_SUBTYPE_NOT_CHECKED = 0;
    private static final byte HEI_SUBTYPE_NONE_KNOWN = 1;
    private static final byte HEI_SUBTYPE_PRESENT = 2;
    private static Method heiHasSubtypeInterpreter;
    private static Method heiRegisterSubtypeInterpreter;
    private static Method heiFluidSubtypeApply;
    private static Object heiFluidSubtypeInterpreter;
    private static final ConcurrentMap<Class<?>, Method> HEI_PLUGIN_REGISTER_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, ThermalTransposerContainerData> THERMAL_TRANSPOSER_CONTAINER_CACHE = new ConcurrentHashMap<>();
    private static final Map<Object, Map<String, Object[]>> THERMAL_TRANSPOSER_DRAWABLES = Collections.synchronizedMap(new WeakHashMap<>());
    private static Constructor<?> forestryBottlerRecipeWrapperConstructor;
    private static Constructor<?> extraTreesLumbermillRecipeWrapperConstructor;
    private static Constructor<?> enderIOTankSimpleWrapperConstructor;
    private static Constructor<?> enderIOTankRecipeWrapperConstructor;
    private static volatile List<Fluid> forestryBottlerFluidSnapshot;
    private static volatile boolean thermalTransposerContainerCacheLoaded;
    private static volatile boolean thermalTransposerContainerCacheDirty;
    private static final Map<Object, List<?>> HEI_FLUID_HANDLER_ITEMS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, List<?>> HEI_FILLABLE_FLUID_HANDLER_ITEMS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, List<?>> HEI_DRAINABLE_FLUID_HANDLER_ITEMS = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Map<Object, CompressedEnderIOTankIngredients> ENDERIO_TANK_COMPRESSED_INGREDIENTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean jerVillagerTradeCacheLoaded;
    private static volatile boolean jerVillagerTradeCacheDirty;
    private static volatile int jerVillagerTradeCacheHits;
    private static volatile int jerVillagerTradeCacheMisses;
    private static volatile int jerVillagerTradeCacheFailed;

    private HeiOptimizations() {
    }

    public static int searchWorkerCount() {
        return SEARCH_WORKERS;
    }

    public static Iterator<?> emptyIterator() {
        return Collections.emptyIterator();
    }

    @SuppressWarnings("rawtypes")
    public static List getFluidHandlerItemIngredients(Object ingredientRegistry, Class ignoredIngredientClass) {
        return getFluidHandlerItemIngredients(ingredientRegistry, ignoredIngredientClass, true);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List getFillableFluidHandlerItemIngredients(Object ingredientRegistry, Class ignoredIngredientClass) {
        if (ingredientRegistry == null) {
            return Collections.emptyList();
        }

        synchronized (HEI_FILLABLE_FLUID_HANDLER_ITEMS) {
            List cached = HEI_FILLABLE_FLUID_HANDLER_ITEMS.get(ingredientRegistry);
            if (cached != null) {
                return cached;
            }

            long started = System.nanoTime();
            List allFluidHandlers = getFluidHandlerItemIngredients(ingredientRegistry, ignoredIngredientClass, true);
            if (allFluidHandlers.isEmpty()) {
                HEI_FILLABLE_FLUID_HANDLER_ITEMS.put(ingredientRegistry, allFluidHandlers);
                return allFluidHandlers;
            }

            List fillable = new ArrayList();
            for (Object ingredient : allFluidHandlers) {
                if (ingredient instanceof ItemStack && canFillFluidHandlerItem((ItemStack) ingredient)) {
                    fillable.add(ingredient);
                }
            }

            List result = Collections.unmodifiableList(fillable);
            HEI_FILLABLE_FLUID_HANDLER_ITEMS.put(ingredientRegistry, result);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Filtered {} fillable fluid-handler item ingredient(s) from {} fluid-handler item ingredient(s) in {} ms",
                    result.size(),
                    allFluidHandlers.size(),
                    elapsedMs
            );
            return result;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List getDrainableFluidHandlerItemIngredients(Object ingredientRegistry, Class ignoredIngredientClass) {
        if (ingredientRegistry == null) {
            return Collections.emptyList();
        }

        synchronized (HEI_DRAINABLE_FLUID_HANDLER_ITEMS) {
            List cached = HEI_DRAINABLE_FLUID_HANDLER_ITEMS.get(ingredientRegistry);
            if (cached != null) {
                return cached;
            }

            long started = System.nanoTime();
            List allFluidHandlers = getFluidHandlerItemIngredients(ingredientRegistry, ignoredIngredientClass, true);
            if (allFluidHandlers.isEmpty()) {
                HEI_DRAINABLE_FLUID_HANDLER_ITEMS.put(ingredientRegistry, allFluidHandlers);
                return allFluidHandlers;
            }

            List drainable = new ArrayList();
            for (Object ingredient : allFluidHandlers) {
                if (ingredient instanceof ItemStack && canDrainFluidHandlerItem((ItemStack) ingredient)) {
                    drainable.add(ingredient);
                }
            }

            List result = Collections.unmodifiableList(drainable);
            HEI_DRAINABLE_FLUID_HANDLER_ITEMS.put(ingredientRegistry, result);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Filtered {} drainable fluid-handler item ingredient(s) from {} fluid-handler item ingredient(s) in {} ms",
                    result.size(),
                    allFluidHandlers.size(),
                    elapsedMs
            );
            return result;
        }
    }

    @SuppressWarnings("rawtypes")
    public static Collection getFluidHandlerItemIngredientsForType(Object ingredientRegistry, Object ignoredIngredientType) {
        return getFluidHandlerItemIngredients(ingredientRegistry, ignoredIngredientType, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List getFluidHandlerItemIngredients(Object ingredientRegistry, Object ingredientToken, boolean classLookup) {
        if (ingredientRegistry == null) {
            return Collections.emptyList();
        }

        synchronized (HEI_FLUID_HANDLER_ITEMS) {
            List cached = HEI_FLUID_HANDLER_ITEMS.get(ingredientRegistry);
            if (cached != null) {
                return cached;
            }

            long started = System.nanoTime();
            Collection allIngredients = allItemIngredients(ingredientRegistry, ingredientToken, classLookup);
            if (allIngredients == null || allIngredients.isEmpty()) {
                List empty = Collections.emptyList();
                HEI_FLUID_HANDLER_ITEMS.put(ingredientRegistry, empty);
                return empty;
            }

            List filtered = new ArrayList();
            int scanned = 0;
            for (Object ingredient : allIngredients) {
                if (!(ingredient instanceof ItemStack)) {
                    continue;
                }
                scanned++;
                ItemStack stack = (ItemStack) ingredient;
                try {
                    if (!isStackEmptyReflective(stack)
                            && stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                        filtered.add(stack);
                    }
                } catch (Throwable ignored) {
                    // A broken capability provider should not break HEI startup.
                }
            }

            List result = Collections.unmodifiableList(filtered);
            HEI_FLUID_HANDLER_ITEMS.put(ingredientRegistry, result);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Filtered {} fluid-handler item ingredient(s) from {} HEI item ingredient(s) in {} ms",
                    result.size(),
                    scanned,
                    elapsedMs
            );
            return result;
        }
    }

    @SuppressWarnings("rawtypes")
    private static Collection allItemIngredients(Object ingredientRegistry, Object ingredientToken, boolean classLookup) {
        try {
            if (classLookup && ingredientToken instanceof Class) {
                Method method = ingredientRegistry.getClass().getMethod("getIngredients", Class.class);
                Object value = method.invoke(ingredientRegistry, ingredientToken);
                return value instanceof Collection ? (Collection) value : Collections.emptyList();
            }

            Class<?> ingredientTypeClass = Class.forName("mezz.jei.api.recipe.IIngredientType");
            Method method = ingredientRegistry.getClass().getMethod("getAllIngredients", ingredientTypeClass);
            Object value = method.invoke(ingredientRegistry, ingredientToken);
            return value instanceof Collection ? (Collection) value : Collections.emptyList();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to enumerate HEI item ingredients for fluid-handler cache", throwable);
            return Collections.emptyList();
        }
    }

    private static boolean canFillFluidHandlerItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        try {
            ItemStack copy = copyStack(stack);
            IFluidHandlerItem handler = copy.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler == null) {
                return false;
            }
            if (FluidRegistry.WATER != null && handler.fill(new FluidStack(FluidRegistry.WATER, 1000), false) > 0) {
                return true;
            }
            IFluidTankProperties[] properties = handler.getTankProperties();
            if (properties == null) {
                return false;
            }
            for (IFluidTankProperties property : properties) {
                if (property != null && property.canFill()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Keep broken capability providers out of expensive TE simulation paths.
        }
        return false;
    }

    private static boolean canDrainFluidHandlerItem(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        try {
            ItemStack copy = copyStack(stack);
            IFluidHandlerItem handler = copy.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
            if (handler == null) {
                return false;
            }
            FluidStack drained = handler.drain(Integer.MAX_VALUE, false);
            if (drained != null && drained.amount > 0) {
                return true;
            }
            IFluidTankProperties[] properties = handler.getTankProperties();
            if (properties == null) {
                return false;
            }
            for (IFluidTankProperties property : properties) {
                if (property != null
                        && property.canDrain()
                        && property.getContents() != null
                        && property.getContents().amount > 0) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // Keep broken capability providers out of expensive TE simulation paths.
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List fastForestryBottlerRecipes(Object ingredientRegistry) {
        if (!GpomEarlyConfig.heiFastForestryBottlerEnabled()) {
            return null;
        }

        long started = System.nanoTime();
        try {
            List fluidHandlers = getFluidHandlerItemIngredients(ingredientRegistry, ItemStack.class, true);
            if (fluidHandlers.isEmpty()) {
                return Collections.emptyList();
            }

            List<Fluid> fluids = forestryBottlerFluids();
            String cacheSignature = forestryBottlerRecipeCacheSignature(fluidHandlers, fluids);
            List cachedRecipes = loadForestryBottlerRecipeCache(cacheSignature);
            if (cachedRecipes != null) {
                return cachedRecipes;
            }

            List recipes = new ArrayList();
            List<ForestryBottlerRecipeRecord> records = cacheSignature != null && GpomEarlyConfig.heiForestryBottlerRecipeCacheEnabled()
                    ? new ArrayList<ForestryBottlerRecipeRecord>()
                    : null;
            int scanned = 0;
            int drainRecipes = 0;
            int fillCandidates = 0;
            int fullFillSkipped = 0;
            int fillRecipes = 0;
            for (Object ingredient : fluidHandlers) {
                if (!(ingredient instanceof ItemStack)) {
                    continue;
                }
                scanned++;
                ItemStack stack = (ItemStack) ingredient;
                ItemStack handlerStack = copyStack(stack);
                IFluidHandlerItem handler = fluidHandlerItem(handlerStack);
                if (handler == null) {
                    continue;
                }

                if (handlerHasDrainProperty(handler)) {
                    FluidStack drained = handler.drain(Integer.MAX_VALUE, true);
                    if (drained != null && drained.amount > 0) {
                        ItemStack output = handler.getContainer();
                        recipes.add(newForestryBottlerRecipe(stack, drained, output, false));
                        addForestryBottlerRecord(records, stack, drained, output, false);
                        drainRecipes++;
                    }
                }

                if (!handlerMayAcceptAdditionalFluid(handler)) {
                    fullFillSkipped++;
                    continue;
                }

                fillCandidates++;
                for (Fluid fluid : fluids) {
                    if (fluid == null) {
                        continue;
                    }
                    ItemStack fillStack = copyStack(stack);
                    IFluidHandlerItem fillHandler = fluidHandlerItem(fillStack);
                    if (fillHandler == null) {
                        continue;
                    }

                    int filled = fillHandler.fill(new FluidStack(fluid, Integer.MAX_VALUE), true);
                    if (filled <= 0) {
                        continue;
                    }
                    FluidStack filledFluid = new FluidStack(fluid, filled);
                    ItemStack output = fillHandler.getContainer();
                    recipes.add(newForestryBottlerRecipe(stack, filledFluid, output, true));
                    addForestryBottlerRecord(records, stack, filledFluid, output, true);
                    fillRecipes++;
                }
            }

            saveForestryBottlerRecipeCache(cacheSignature, records);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Built Forestry Bottler recipes with GPOM fast path in {} ms (scanned={}, fluids={}, drainRecipes={}, fillCandidates={}, fullFillSkipped={}, fillRecipes={}, total={})",
                    elapsedMs,
                    scanned,
                    fluids.size(),
                    drainRecipes,
                    fillCandidates,
                    fullFillSkipped,
                    fillRecipes,
                    recipes.size()
            );
            return recipes;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Forestry Bottler fast path failed; falling back to stock Forestry generator", throwable);
            return null;
        }
    }

    private static void addForestryBottlerRecord(
            List<ForestryBottlerRecipeRecord> records,
            ItemStack input,
            FluidStack fluid,
            ItemStack output,
            boolean filling
    ) {
        if (records == null || input == null || fluid == null || fluid.getFluid() == null || fluid.amount <= 0) {
            return;
        }
        String fluidName = fluid.getFluid().getName();
        if (fluidName == null || fluidName.isEmpty()) {
            return;
        }
        records.add(new ForestryBottlerRecipeRecord(input, fluidName, fluid.amount, output, filling));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List loadForestryBottlerRecipeCache(String signature) {
        if (!GpomEarlyConfig.heiForestryBottlerRecipeCacheEnabled() || signature == null) {
            return null;
        }

        File file = forestryBottlerRecipeCacheFile();
        if (!file.isFile()) {
            return null;
        }

        long started = System.nanoTime();
        try (DataInputStream input = compressedCacheInput(file)) {
            int magic = input.readInt();
            int version = input.readInt();
            String cacheSignature = input.readUTF();
            if (magic != FORESTRY_BOTTLER_RECIPE_CACHE_MAGIC
                    || version != FORESTRY_BOTTLER_RECIPE_CACHE_VERSION
                    || !signature.equals(cacheSignature)) {
                return null;
            }

            int count = input.readInt();
            if (count < 0 || count > 250_000) {
                GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt Forestry Bottler recipe cache {}; invalid count {}", file, count);
                return null;
            }

            List recipes = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                ItemStack inputStack = readNullableItemStack(input);
                String fluidName = input.readUTF();
                int amount = input.readInt();
                ItemStack outputStack = readNullableItemStack(input);
                boolean filling = input.readBoolean();
                if (inputStack == null || fluidName == null || fluidName.isEmpty() || amount <= 0) {
                    GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt Forestry Bottler recipe cache {}; invalid entry {}", file, i);
                    return null;
                }

                Fluid fluid = FluidRegistry.getFluid(fluidName);
                if (fluid == null) {
                    GPOM.LOGGER.info("[HEI Optimizations] Ignoring stale Forestry Bottler recipe cache {}; missing fluid {}", file, fluidName);
                    return null;
                }
                recipes.add(newForestryBottlerRecipe(inputStack, new FluidStack(fluid, amount), outputStack, filling));
            }

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info("[HEI Optimizations] Loaded {} Forestry Bottler recipe(s) from cache in {} ms", recipes.size(), elapsedMs);
            return recipes;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to load Forestry Bottler recipe cache {}; rebuilding", file, throwable);
            return null;
        }
    }

    private static void saveForestryBottlerRecipeCache(String signature, List<ForestryBottlerRecipeRecord> records) {
        if (!GpomEarlyConfig.heiForestryBottlerRecipeCacheEnabled()
                || signature == null
                || records == null
                || records.isEmpty()) {
            return;
        }

        long started = System.nanoTime();
        File file = forestryBottlerRecipeCacheFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to create HEI cache directory {}", parent);
            return;
        }

        File tmp = parent == null ? new File(file.getPath() + ".tmp") : new File(parent, file.getName() + ".tmp");
        try (DataOutputStream output = compressedCacheOutput(tmp)) {
            output.writeInt(FORESTRY_BOTTLER_RECIPE_CACHE_MAGIC);
            output.writeInt(FORESTRY_BOTTLER_RECIPE_CACHE_VERSION);
            output.writeUTF(signature);
            output.writeInt(records.size());
            for (ForestryBottlerRecipeRecord record : records) {
                writeNullableItemStack(output, record.input);
                output.writeUTF(record.fluidName);
                output.writeInt(record.amount);
                writeNullableItemStack(output, record.output);
                output.writeBoolean(record.filling);
            }
        } catch (Throwable throwable) {
            if (tmp.isFile() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to save Forestry Bottler recipe cache {}", file, throwable);
            return;
        }

        if (file.isFile() && !file.delete()) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to replace old Forestry Bottler recipe cache {}", file);
            return;
        }
        if (!tmp.renameTo(file)) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to move Forestry Bottler recipe cache into place {}", file);
            return;
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        GPOM.LOGGER.info("[HEI Optimizations] Saved {} Forestry Bottler recipe(s) to cache in {} ms", records.size(), elapsedMs);
    }

    private static String forestryBottlerRecipeCacheSignature(List<?> fluidHandlers, List<Fluid> fluids) {
        if (!GpomEarlyConfig.heiForestryBottlerRecipeCacheEnabled()) {
            return null;
        }
        try {
            StringBuilder builder = new StringBuilder(64 * 1024);
            builder.append("v=").append(FORESTRY_BOTTLER_RECIPE_CACHE_VERSION).append('\n');
            builder.append("items=").append(itemStackCacheSignature()).append('\n');
            builder.append("fluids=");
            for (Fluid fluid : fluids) {
                if (fluid == null) {
                    builder.append("null;");
                } else {
                    builder.append(fluid.getName()).append('@').append(fluid.getClass().getName()).append(';');
                }
            }
            builder.append('\n').append("handlers=");
            for (Object ingredient : fluidHandlers) {
                if (!(ingredient instanceof ItemStack)) {
                    builder.append("nonstack:").append(ingredient == null ? "null" : ingredient.getClass().getName()).append(';');
                    continue;
                }
                byte[] data = writeItemStack((ItemStack) ingredient);
                builder.append(data.length).append(':').append(Arrays.hashCode(data)).append(';');
            }
            return Integer.toHexString(builder.toString().hashCode());
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to build Forestry Bottler recipe cache signature; cache disabled for this run", throwable);
            return null;
        }
    }

    private static File forestryBottlerRecipeCacheFile() {
        return GpomCaches.file("hei", "forestry-bottler-recipes-v1.dat");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List fastExtraTreesLumbermillRecipes(Object jeiHelpers) {
        if (!GpomEarlyConfig.heiExtraTreesLumbermillRecipeCacheEnabled()) {
            return null;
        }

        long started = System.nanoTime();
        try {
            List<ExtraTreesLumbermillRecipeRecord> managerRecords = extraTreesLumbermillManagerRecords();
            List logSubtypes = extraTreesLogSubtypes(jeiHelpers);
            String signature = extraTreesLumbermillRecipeCacheSignature(managerRecords, logSubtypes);
            List cachedRecipes = loadExtraTreesLumbermillRecipeCache(signature);
            if (cachedRecipes != null) {
                return cachedRecipes;
            }

            List recipes = new ArrayList();
            List<ExtraTreesLumbermillRecipeRecord> records = new ArrayList<>();
            for (ExtraTreesLumbermillRecipeRecord record : managerRecords) {
                addExtraTreesLumbermillRecipe(recipes, records, record.input, record.output);
            }

            Method getPlanksOutput = Class.forName("binnie.extratrees.machines.lumbermill.recipes.LumbermillRecipeManager")
                    .getMethod("getRecipeWithPlanksOutput", ItemStack.class, Class.forName("net.minecraft.world.World"));
            int scannedLogs = 0;
            int generatedLogs = 0;
            for (Object object : logSubtypes) {
                if (!(object instanceof ItemStack)) {
                    continue;
                }
                scannedLogs++;
                ItemStack input = (ItemStack) object;
                ItemStack output = (ItemStack) getPlanksOutput.invoke(null, copyStack(input), null);
                if (output == null || isStackEmptyReflective(output)) {
                    continue;
                }

                int adjustedCount = (int) Math.ceil(stackCount(output) * 1.5D);
                ItemStack adjustedOutput = stackWithCount(output, adjustedCount);
                addExtraTreesLumbermillRecipe(recipes, records, copyStack(input), adjustedOutput);
                generatedLogs++;
            }

            saveExtraTreesLumbermillRecipeCache(signature, records);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Built ExtraTrees Lumbermill recipes with GPOM cache path in {} ms (managerRecipes={}, scannedLogs={}, generatedLogRecipes={}, total={})",
                    elapsedMs,
                    managerRecords.size(),
                    scannedLogs,
                    generatedLogs,
                    recipes.size()
            );
            return recipes;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] ExtraTrees Lumbermill cache path failed; falling back to stock ExtraTrees generator", throwable);
            return null;
        }
    }

    @SuppressWarnings("rawtypes")
    private static List extraTreesLogSubtypes(Object jeiHelpers) throws ReflectiveOperationException {
        if (jeiHelpers == null) {
            return Collections.emptyList();
        }

        Object stackHelper = jeiHelpers.getClass().getMethod("getStackHelper").invoke(jeiHelpers);
        if (stackHelper == null) {
            return Collections.emptyList();
        }

        Object value = stackHelper.getClass()
                .getMethod("getAllSubtypes", Iterable.class)
                .invoke(stackHelper, OreDictionary.getOres("logWood"));
        return value instanceof List ? (List) value : Collections.emptyList();
    }

    @SuppressWarnings("rawtypes")
    private static List<ExtraTreesLumbermillRecipeRecord> extraTreesLumbermillManagerRecords() throws ReflectiveOperationException {
        List<ExtraTreesLumbermillRecipeRecord> records = new ArrayList<>();
        Object manager = Class.forName("binnie.extratrees.api.recipes.ExtraTreesRecipeManager")
                .getField("lumbermillManager")
                .get(null);
        if (manager == null) {
            return records;
        }

        Object value = manager.getClass().getMethod("recipes").invoke(manager);
        if (!(value instanceof Collection)) {
            return records;
        }

        for (Object recipe : (Collection) value) {
            if (recipe == null) {
                continue;
            }
            Object input = recipe.getClass().getMethod("getInput").invoke(recipe);
            Object output = recipe.getClass().getMethod("getOutput").invoke(recipe);
            if (input instanceof ItemStack && output instanceof ItemStack) {
                addExtraTreesLumbermillRecord(records, (ItemStack) input, (ItemStack) output);
            }
        }
        return records;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addExtraTreesLumbermillRecipe(
            List recipes,
            List<ExtraTreesLumbermillRecipeRecord> records,
            ItemStack input,
            ItemStack output
    ) throws ReflectiveOperationException {
        if (input == null || output == null || isStackEmptyReflective(input) || isStackEmptyReflective(output)) {
            return;
        }
        ItemStack inputCopy = copyStack(input);
        ItemStack outputCopy = copyStack(output);
        recipes.add(newExtraTreesLumbermillRecipe(inputCopy, outputCopy));
        addExtraTreesLumbermillRecord(records, inputCopy, outputCopy);
    }

    private static void addExtraTreesLumbermillRecord(
            List<ExtraTreesLumbermillRecipeRecord> records,
            ItemStack input,
            ItemStack output
    ) throws ReflectiveOperationException {
        if (records == null || input == null || output == null || isStackEmptyReflective(input) || isStackEmptyReflective(output)) {
            return;
        }
        records.add(new ExtraTreesLumbermillRecipeRecord(copyStack(input), copyStack(output)));
    }

    private static Object newExtraTreesLumbermillRecipe(ItemStack input, ItemStack output) throws ReflectiveOperationException {
        Constructor<?> constructor = extraTreesLumbermillRecipeWrapperConstructor;
        if (constructor == null) {
            constructor = Class.forName("binnie.extratrees.integration.jei.lumbermill.LumbermillRecipeWrapper")
                    .getConstructor(ItemStack.class, ItemStack.class);
            extraTreesLumbermillRecipeWrapperConstructor = constructor;
        }
        return constructor.newInstance(input, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List loadExtraTreesLumbermillRecipeCache(String signature) {
        if (!GpomEarlyConfig.heiExtraTreesLumbermillRecipeCacheEnabled() || signature == null) {
            return null;
        }

        File file = extraTreesLumbermillRecipeCacheFile();
        if (!file.isFile()) {
            return null;
        }

        long started = System.nanoTime();
        try (DataInputStream input = compressedCacheInput(file)) {
            int magic = input.readInt();
            int version = input.readInt();
            String cacheSignature = input.readUTF();
            if (magic != EXTRATREES_LUMBERMILL_RECIPE_CACHE_MAGIC
                    || version != EXTRATREES_LUMBERMILL_RECIPE_CACHE_VERSION
                    || !signature.equals(cacheSignature)) {
                return null;
            }

            int count = input.readInt();
            if (count < 0 || count > 100_000) {
                GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt ExtraTrees Lumbermill recipe cache {}; invalid count {}", file, count);
                return null;
            }

            List recipes = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                ItemStack inputStack = readNullableItemStack(input);
                ItemStack outputStack = readNullableItemStack(input);
                if (inputStack == null || outputStack == null) {
                    GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt ExtraTrees Lumbermill recipe cache {}; invalid entry {}", file, i);
                    return null;
                }
                recipes.add(newExtraTreesLumbermillRecipe(inputStack, outputStack));
            }

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info("[HEI Optimizations] Loaded {} ExtraTrees Lumbermill recipe(s) from cache in {} ms", recipes.size(), elapsedMs);
            return recipes;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to load ExtraTrees Lumbermill recipe cache {}; rebuilding", file, throwable);
            return null;
        }
    }

    private static void saveExtraTreesLumbermillRecipeCache(String signature, List<ExtraTreesLumbermillRecipeRecord> records) {
        if (!GpomEarlyConfig.heiExtraTreesLumbermillRecipeCacheEnabled()
                || signature == null
                || records == null
                || records.isEmpty()) {
            return;
        }

        long started = System.nanoTime();
        File file = extraTreesLumbermillRecipeCacheFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to create HEI cache directory {}", parent);
            return;
        }

        File tmp = parent == null ? new File(file.getPath() + ".tmp") : new File(parent, file.getName() + ".tmp");
        try (DataOutputStream output = compressedCacheOutput(tmp)) {
            output.writeInt(EXTRATREES_LUMBERMILL_RECIPE_CACHE_MAGIC);
            output.writeInt(EXTRATREES_LUMBERMILL_RECIPE_CACHE_VERSION);
            output.writeUTF(signature);
            output.writeInt(records.size());
            for (ExtraTreesLumbermillRecipeRecord record : records) {
                writeNullableItemStack(output, record.input);
                writeNullableItemStack(output, record.output);
            }
        } catch (Throwable throwable) {
            if (tmp.isFile() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to save ExtraTrees Lumbermill recipe cache {}", file, throwable);
            return;
        }

        if (file.isFile() && !file.delete()) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to replace old ExtraTrees Lumbermill recipe cache {}", file);
            return;
        }
        if (!tmp.renameTo(file)) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to move ExtraTrees Lumbermill recipe cache into place {}", file);
            return;
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        GPOM.LOGGER.info("[HEI Optimizations] Saved {} ExtraTrees Lumbermill recipe(s) to cache in {} ms", records.size(), elapsedMs);
    }

    private static String extraTreesLumbermillRecipeCacheSignature(
            List<ExtraTreesLumbermillRecipeRecord> managerRecords,
            List<?> logSubtypes
    ) {
        if (!GpomEarlyConfig.heiExtraTreesLumbermillRecipeCacheEnabled()) {
            return null;
        }
        try {
            StringBuilder builder = new StringBuilder(64 * 1024);
            builder.append("v=").append(EXTRATREES_LUMBERMILL_RECIPE_CACHE_VERSION).append('\n');
            builder.append("items=").append(itemStackCacheSignature()).append('\n');
            builder.append("manager=");
            for (ExtraTreesLumbermillRecipeRecord record : managerRecords) {
                appendStackKey(builder, record.input);
                builder.append("=>");
                appendStackKey(builder, record.output);
                builder.append(';');
            }
            builder.append('\n').append("logs=");
            for (Object object : logSubtypes) {
                if (object instanceof ItemStack) {
                    appendStackKey(builder, (ItemStack) object);
                    builder.append(';');
                }
            }
            builder.append('\n').append("recipes=");
            List<String> recipeEntries = new ArrayList<>();
            for (IRecipe recipe : ForgeRegistries.RECIPES) {
                if (recipe == null) {
                    continue;
                }
                ResourceLocation registryName = recipe.getRegistryName();
                recipeEntries.add((registryName == null ? "unknown" : registryName.toString()) + '@' + recipe.getClass().getName());
            }
            Collections.sort(recipeEntries);
            for (String entry : recipeEntries) {
                builder.append(entry).append(';');
            }
            return Integer.toHexString(builder.toString().hashCode());
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to build ExtraTrees Lumbermill recipe cache signature; cache disabled for this run", throwable);
            return null;
        }
    }

    private static File extraTreesLumbermillRecipeCacheFile() {
        return GpomCaches.file("hei", "extratrees-lumbermill-recipes-v1.dat");
    }

    private static IFluidHandlerItem fluidHandlerItem(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        try {
            return stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean handlerHasDrainProperty(IFluidHandlerItem handler) {
        try {
            IFluidTankProperties[] properties = handler.getTankProperties();
            if (properties == null) {
                return false;
            }
            for (IFluidTankProperties property : properties) {
                if (property != null && property.canDrain()) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean handlerMayAcceptAdditionalFluid(IFluidHandlerItem handler) {
        try {
            IFluidTankProperties[] properties = handler.getTankProperties();
            if (properties == null) {
                return false;
            }
            for (IFluidTankProperties property : properties) {
                if (property == null || !property.canFill()) {
                    continue;
                }
                int capacity = property.getCapacity();
                FluidStack contents = property.getContents();
                if (capacity <= 0 || contents == null || contents.amount < capacity) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static List<Fluid> forestryBottlerFluids() {
        List<Fluid> snapshot = forestryBottlerFluidSnapshot;
        if (snapshot != null) {
            return snapshot;
        }
        snapshot = Collections.unmodifiableList(new ArrayList<>(FluidRegistry.getRegisteredFluids().values()));
        forestryBottlerFluidSnapshot = snapshot;
        return snapshot;
    }

    private static Object newForestryBottlerRecipe(ItemStack input, FluidStack fluid, ItemStack output, boolean filling) throws ReflectiveOperationException {
        Constructor<?> constructor = forestryBottlerRecipeWrapperConstructor;
        if (constructor == null) {
            constructor = Class.forName("forestry.factory.recipes.jei.bottler.BottlerRecipeWrapper")
                    .getConstructor(ItemStack.class, FluidStack.class, ItemStack.class, boolean.class);
            forestryBottlerRecipeWrapperConstructor = constructor;
        }
        return constructor.newInstance(input, fluid, output, filling);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean fastEnderIOTankRegister() {
        if (!GpomEarlyConfig.heiFastEnderIOTankEnabled()) {
            return false;
        }

        long started = System.nanoTime();
        boolean mutatedHeiRegistry = false;
        try {
            boolean fluidIoEnabled = booleanConfigValue(
                    "crazypants.enderio.machines.config.config.PersonalConfig",
                    "enableTankFluidInOutJEIRecipes"
            );
            boolean mendingEnabled = booleanConfigValue(
                    "crazypants.enderio.machines.config.config.PersonalConfig",
                    "enableTankMendingJEIRecipes"
            ) && booleanConfigValue(
                    "crazypants.enderio.machines.config.config.TankConfig",
                    "allowMending"
            );
            if (!fluidIoEnabled && !mendingEnabled) {
                return false;
            }

            Class<?> machinesPluginClass = Class.forName("crazypants.enderio.machines.integration.jei.MachinesPlugin");
            Object modRegistry = findField(machinesPluginClass, "iModRegistry").get(null);
            Object guiHelper = findField(machinesPluginClass, "iGuiHelper").get(null);
            if (modRegistry == null || guiHelper == null) {
                return false;
            }

            Object ingredientRegistry = modRegistry.getClass().getMethod("getIngredientRegistry").invoke(modRegistry);
            Collection allItemIngredients = allItemIngredients(ingredientRegistry, ItemStack.class, true);
            List fluidHandlers = fluidIoEnabled
                    ? getFluidHandlerItemIngredients(ingredientRegistry, ItemStack.class, true)
                    : Collections.emptyList();

            List recipes = new ArrayList();
            int machineRecipes = addEnderIOTankMachineRecipes(recipes);
            int fluidRecipes = fluidIoEnabled ? addEnderIOTankFluidRecipes(recipes, fluidHandlers) : 0;
            int mendingCandidates = mendingEnabled ? addCompressedEnderIOTankMendingRecipe(recipes, allItemIngredients) : 0;

            mutatedHeiRegistry = true;
            registerEnderIOTankCategory(modRegistry, guiHelper);
            modRegistry.getClass()
                    .getMethod("addRecipes", Collection.class, String.class)
                    .invoke(modRegistry, recipes, "EIOTank");
            Object transferRegistry = modRegistry.getClass().getMethod("getRecipeTransferRegistry").invoke(modRegistry);
            transferRegistry.getClass().getMethod(
                    "addRecipeTransferHandler",
                    Class.class,
                    String.class,
                    int.class,
                    int.class,
                    int.class,
                    int.class
            ).invoke(
                    transferRegistry,
                    Class.forName("crazypants.enderio.machines.machine.tank.ContainerTank"),
                    "EIOTank",
                    0,
                    2,
                    3,
                    36
            );

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Registered EnderIO Tank HEI recipes with GPOM fast path in {} ms (machineRecipes={}, fluidHandlers={}, fluidRecipes={}, mendingCandidates={}, totalRecipes={})",
                    elapsedMs,
                    machineRecipes,
                    fluidHandlers.size(),
                    fluidRecipes,
                    mendingCandidates,
                    recipes.size()
            );
            return true;
        } catch (Throwable throwable) {
            if (mutatedHeiRegistry) {
                GPOM.LOGGER.warn("[HEI Optimizations] EnderIO Tank fast path failed after mutating HEI; suppressing stock fallback to avoid duplicate EIOTank registration", throwable);
                return true;
            }
            GPOM.LOGGER.warn("[HEI Optimizations] EnderIO Tank fast path failed before mutating HEI; falling back to stock EnderIO registration", throwable);
            return false;
        }
    }

    public static boolean applyCompressedEnderIOTankIngredients(Object wrapper, Object ingredients) {
        if (wrapper == null || ingredients == null) {
            return false;
        }

        CompressedEnderIOTankIngredients data;
        synchronized (ENDERIO_TANK_COMPRESSED_INGREDIENTS) {
            data = ENDERIO_TANK_COMPRESSED_INGREDIENTS.get(wrapper);
        }
        if (data == null) {
            return false;
        }

        try {
            ingredients.getClass()
                    .getMethod("setInputs", Class.class, List.class)
                    .invoke(ingredients, ItemStack.class, data.itemInputs);
            ingredients.getClass()
                    .getMethod("setInputs", Class.class, List.class)
                    .invoke(ingredients, FluidStack.class, data.fluidInputs);
            ingredients.getClass()
                    .getMethod("setOutputs", Class.class, List.class)
                    .invoke(ingredients, ItemStack.class, data.itemOutputs);
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to apply compressed EnderIO Tank mending ingredients; using representative recipe", throwable);
            return false;
        }
    }

    private static void registerEnderIOTankCategory(Object modRegistry, Object guiHelper) throws ReflectiveOperationException, ClassNotFoundException {
        Class<?> simpleWrapperClass = Class.forName("crazypants.enderio.machines.integration.jei.TankRecipeCategory$TankRecipeWrapperSimple");
        Class<?> recipeWrapperClass = Class.forName("crazypants.enderio.machines.integration.jei.TankRecipeCategory$TankRecipeWrapperRecipe");
        Class<?> wrapperBaseClass = Class.forName("crazypants.enderio.base.integration.jei.RecipeWrapperBase");
        Class<?> guiHelperClass = Class.forName("mezz.jei.api.IGuiHelper");
        Method setLevelData = wrapperBaseClass.getMethod(
                "setLevelData",
                Class.class,
                guiHelperClass,
                int.class,
                int.class,
                String.class,
                String.class
        );
        setLevelData.invoke(null, simpleWrapperClass, guiHelper, 125, 15, "textures/blocks/block_tank.png", "textures/blocks/block_tank.png");
        setLevelData.invoke(null, recipeWrapperClass, guiHelper, 125, 15, "textures/blocks/block_tank.png", "textures/blocks/block_tank.png");

        Object category = Class.forName("crazypants.enderio.machines.integration.jei.TankRecipeCategory")
                .getConstructor(guiHelperClass)
                .newInstance(guiHelper);
        Class<?> categoryClass = Class.forName("mezz.jei.api.recipe.IRecipeCategory");
        Object categories = Array.newInstance(categoryClass, 1);
        Array.set(categories, 0, category);
        modRegistry.getClass().getMethod("addRecipeCategories", categories.getClass()).invoke(modRegistry, categories);

        Block tankBlock = enderIOTankBlock();
        Method addCraftingItem = modRegistry.getClass().getMethod("addRecipeCategoryCraftingItem", ItemStack.class, String[].class);
        addCraftingItem.invoke(modRegistry, new ItemStack(tankBlock, 1, 0), (Object) new String[]{"EIOTank"});
        addCraftingItem.invoke(modRegistry, new ItemStack(tankBlock, 1, 1), (Object) new String[]{"EIOTank"});

        modRegistry.getClass().getMethod(
                "addRecipeClickArea",
                Class.class,
                int.class,
                int.class,
                int.class,
                int.class,
                String[].class
        ).invoke(
                modRegistry,
                Class.forName("crazypants.enderio.machines.machine.tank.GuiTank"),
                155,
                42,
                16,
                16,
                (Object) new String[]{"EIOTank"}
        );
    }

    private static Block enderIOTankBlock() throws ReflectiveOperationException, ClassNotFoundException {
        Object machineObject = Class.forName("crazypants.enderio.machines.init.MachineObject")
                .getField("block_tank")
                .get(null);
        Object value = machineObject.getClass().getMethod("getBlockNN").invoke(machineObject);
        return (Block) value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int addEnderIOTankMachineRecipes(List recipes) throws ReflectiveOperationException, ClassNotFoundException {
        int added = 0;
        Object registry = Class.forName("crazypants.enderio.base.recipe.MachineRecipeRegistry")
                .getField("instance")
                .get(null);
        Method getRecipesForMachine = registry.getClass().getMethod("getRecipesForMachine", String.class);
        Class<?> tankRecipeClass = Class.forName("crazypants.enderio.base.recipe.tank.TankMachineRecipe");
        for (String machine : new String[]{"tankempty", "tankfill"}) {
            Object recipesByName = getRecipesForMachine.invoke(registry, machine);
            if (!(recipesByName instanceof Map)) {
                continue;
            }
            for (Object recipe : ((Map) recipesByName).values()) {
                if (tankRecipeClass.isInstance(recipe)) {
                    recipes.add(newEnderIOTankRecipeWrapper(recipe));
                    added++;
                }
            }
        }
        return added;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int addEnderIOTankFluidRecipes(List recipes, List fluidHandlers) throws ReflectiveOperationException {
        int added = 0;
        List<Fluid> fluids = forestryBottlerFluids();
        for (Object ingredient : fluidHandlers) {
            if (!(ingredient instanceof ItemStack)) {
                continue;
            }
            ItemStack stack = (ItemStack) ingredient;
            ItemStack drainStack = copyStack(stack);
            IFluidHandlerItem handler = fluidHandlerItem(drainStack);
            if (handler == null) {
                continue;
            }

            FluidStack drained = handler.drain(16000, true);
            ItemStack drainOutput = handler.getContainer();
            if (drained != null && drained.amount > 0) {
                recipes.add(newEnderIOTankSimpleWrapper(null, drained, copyStack(stack), drainOutput));
                added++;
                continue;
            }

            for (Fluid fluid : fluids) {
                if (fluid == null) {
                    continue;
                }
                ItemStack fillStack = copyStack(stack);
                IFluidHandlerItem fillHandler = fluidHandlerItem(fillStack);
                if (fillHandler == null) {
                    continue;
                }
                int filled = fillHandler.fill(new FluidStack(fluid, 16000), true);
                if (filled <= 0) {
                    continue;
                }
                recipes.add(newEnderIOTankSimpleWrapper(new FluidStack(fluid, filled), null, copyStack(stack), fillHandler.getContainer()));
                added++;
            }
        }
        return added;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int addCompressedEnderIOTankMendingRecipe(List recipes, Collection allItemIngredients) throws ReflectiveOperationException, IOException, ClassNotFoundException {
        if (allItemIngredients == null || allItemIngredients.isEmpty()) {
            return 0;
        }

        Enchantment mending = minecraftMendingEnchantment();
        if (mending == null) {
            return 0;
        }

        List<ItemStack> damagedInputs = new ArrayList<>();
        List<ItemStack> repairedOutputs = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int repairDamage = enderIOTankXpToDurability(enderIOLiquidToExperience(16000));
        for (Object ingredient : allItemIngredients) {
            if (!(ingredient instanceof ItemStack)) {
                continue;
            }
            ItemStack stack = (ItemStack) ingredient;
            int maxDamage = stackMaxDamage(stack);
            if (isStackEmptyReflective(stack) || !stackIsDamageable(stack) || maxDamage <= 0) {
                continue;
            }

            ItemStack enchanted = copyStack(stack);
            if (EnchantmentHelper.getEnchantmentLevel(mending, enchanted) <= 0) {
                if (!mending.canApply(enchanted)) {
                    continue;
                }
                EnchantmentHelper.setEnchantments(Collections.singletonMap(mending, 1), enchanted);
            }

            ItemStack damaged = copyStack(enchanted);
            int startingDamage = Math.max(1, maxDamage * 3 / 4);
            setStackItemDamage(damaged, startingDamage);
            int damagedValue = stackItemDamage(damaged);
            int repairedDamage = Math.max(0, startingDamage - Math.min(repairDamage, damagedValue));
            ItemStack repaired = copyStack(enchanted);
            setStackItemDamage(repaired, repairedDamage);
            if (stackItemDamage(damaged) == stackItemDamage(repaired)
                    || isStackEmptyReflective(damaged)
                    || isStackEmptyReflective(repaired)) {
                continue;
            }

            StringBuilder keyBuilder = new StringBuilder(128);
            appendStackKey(keyBuilder, damaged);
            String key = keyBuilder.toString();
            if (!seen.add(key)) {
                continue;
            }
            damagedInputs.add(damaged);
            repairedOutputs.add(repaired);
        }

        if (damagedInputs.isEmpty() || repairedOutputs.isEmpty()) {
            return 0;
        }

        Fluid xpJuice = enderIOXpJuice();
        if (xpJuice == null) {
            return 0;
        }
        Object wrapper = newEnderIOTankSimpleWrapper(
                new FluidStack(xpJuice, 16000),
                null,
                damagedInputs.get(0),
                repairedOutputs.get(0)
        );
        synchronized (ENDERIO_TANK_COMPRESSED_INGREDIENTS) {
            ENDERIO_TANK_COMPRESSED_INGREDIENTS.put(
                    wrapper,
                    new CompressedEnderIOTankIngredients(
                            Collections.singletonList(new FluidStack(xpJuice, 16000)),
                            Collections.unmodifiableList(damagedInputs),
                            Collections.unmodifiableList(repairedOutputs)
                    )
            );
        }
        recipes.add(wrapper);
        return damagedInputs.size();
    }

    private static Enchantment minecraftMendingEnchantment() {
        try {
            return ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation("minecraft", "mending"));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean booleanConfigValue(String className, String fieldName) throws ReflectiveOperationException, ClassNotFoundException {
        Object holder = Class.forName(className).getField(fieldName).get(null);
        Object value = holder == null ? null : holder.getClass().getMethod("get").invoke(holder);
        return Boolean.TRUE.equals(value);
    }

    private static Object newEnderIOTankSimpleWrapper(FluidStack fluidInput, FluidStack fluidOutput, ItemStack itemInput, ItemStack itemOutput) throws ReflectiveOperationException, ClassNotFoundException {
        Constructor<?> constructor = enderIOTankSimpleWrapperConstructor;
        if (constructor == null) {
            constructor = Class.forName("crazypants.enderio.machines.integration.jei.TankRecipeCategory$TankRecipeWrapperSimple")
                    .getConstructor(FluidStack.class, FluidStack.class, ItemStack.class, ItemStack.class);
            enderIOTankSimpleWrapperConstructor = constructor;
        }
        return constructor.newInstance(fluidInput, fluidOutput, itemInput, itemOutput);
    }

    private static Object newEnderIOTankRecipeWrapper(Object recipe) throws ReflectiveOperationException, ClassNotFoundException {
        Constructor<?> constructor = enderIOTankRecipeWrapperConstructor;
        if (constructor == null) {
            constructor = Class.forName("crazypants.enderio.machines.integration.jei.TankRecipeCategory$TankRecipeWrapperRecipe")
                    .getConstructor(Class.forName("crazypants.enderio.base.recipe.tank.TankMachineRecipe"));
            enderIOTankRecipeWrapperConstructor = constructor;
        }
        return constructor.newInstance(recipe);
    }

    private static Fluid enderIOXpJuice() throws ReflectiveOperationException, ClassNotFoundException {
        Object xpJuice = Class.forName("crazypants.enderio.base.fluid.Fluids")
                .getField("XP_JUICE")
                .get(null);
        Object value = xpJuice == null ? null : xpJuice.getClass().getMethod("getFluid").invoke(xpJuice);
        return value instanceof Fluid ? (Fluid) value : null;
    }

    private static int enderIOLiquidToExperience(int liquid) throws ReflectiveOperationException, ClassNotFoundException {
        Object value = Class.forName("crazypants.enderio.base.xp.XpUtil")
                .getMethod("liquidToExperience", int.class)
                .invoke(null, liquid);
        return value instanceof Number ? ((Number) value).intValue() : liquid / 20;
    }

    private static int enderIOTankXpToDurability(int xp) throws ReflectiveOperationException, ClassNotFoundException {
        Object value = Class.forName("crazypants.enderio.machines.machine.tank.TileTank")
                .getMethod("xpToDurability", int.class)
                .invoke(null, xp);
        return value instanceof Number ? ((Number) value).intValue() : xp * 2;
    }

    public static void initThermalTransposerContainerWrapper(Object wrapper, Object guiHelper, ItemStack stack, String uId) {
        long started = System.nanoTime();
        try {
            boolean filling = isThermalTransposerFill(uId);
            ThermalTransposerContainerData data = thermalTransposerContainerData(stack, uId);

            setFieldValue(wrapper, "uId", uId);
            setFieldValue(wrapper, "inputs", Collections.singletonList(data.inputs));
            setFieldValue(wrapper, "outputs", Collections.singletonList(data.outputs));
            if (filling) {
                setFieldValue(wrapper, "inputFluids", Collections.singletonList(data.fluids));
                setFieldValue(wrapper, "outputFluids", Collections.emptyList());
            } else {
                setFieldValue(wrapper, "inputFluids", Collections.emptyList());
                setFieldValue(wrapper, "outputFluids", Collections.singletonList(data.fluids));
            }
            setIntFieldValue(wrapper, "energy", 400);
            applyThermalTransposerDrawables(wrapper, guiHelper, filling, 400);
        } catch (Throwable throwable) {
            try {
                setFieldValue(wrapper, "uId", uId);
                setFieldValue(wrapper, "inputs", Collections.singletonList(Collections.emptyList()));
                setFieldValue(wrapper, "outputs", Collections.singletonList(Collections.emptyList()));
                setFieldValue(wrapper, "inputFluids", isThermalTransposerFill(uId)
                        ? Collections.singletonList(Collections.emptyList())
                        : Collections.emptyList());
                setFieldValue(wrapper, "outputFluids", isThermalTransposerFill(uId)
                        ? Collections.emptyList()
                        : Collections.singletonList(Collections.emptyList()));
                setIntFieldValue(wrapper, "energy", 400);
                applyThermalTransposerDrawables(wrapper, guiHelper, isThermalTransposerFill(uId), 400);
            } catch (Throwable ignored) {
            }
            GPOM.LOGGER.warn("[HEI Optimizations] Thermal Expansion Transposer container fast path failed for {}; emitted empty wrapper", uId, throwable);
        } finally {
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            if (elapsedMs >= 250L) {
                GPOM.LOGGER.info("[HEI Optimizations] Built Thermal Expansion Transposer container wrapper for {} in {} ms", uId, elapsedMs);
            }
        }
    }

    private static ThermalTransposerContainerData thermalTransposerContainerData(ItemStack stack, String uId) throws ReflectiveOperationException, IOException {
        if (!GpomEarlyConfig.heiFastThermalTransposerContainersEnabled()) {
            return buildThermalTransposerContainerData(stack, uId);
        }

        loadThermalTransposerContainerCache();
        String key = thermalTransposerContainerKey(stack, uId);
        ThermalTransposerContainerData cached = THERMAL_TRANSPOSER_CONTAINER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }

        ThermalTransposerContainerData built = buildThermalTransposerContainerData(stack, uId);
        if (GpomEarlyConfig.heiThermalTransposerContainerCacheEnabled()) {
            ThermalTransposerContainerData previous = THERMAL_TRANSPOSER_CONTAINER_CACHE.putIfAbsent(key, built);
            thermalTransposerContainerCacheDirty = true;
            return previous != null ? previous : built;
        }
        return built;
    }

    private static ThermalTransposerContainerData buildThermalTransposerContainerData(ItemStack stack, String uId) throws ReflectiveOperationException {
        if (stack == null || isStackEmptyReflective(stack)) {
            return ThermalTransposerContainerData.empty();
        }

        ItemStack handlerStack = copyStack(stack);
        IFluidHandlerItem handler = fluidHandlerItem(handlerStack);
        if (handler == null) {
            return ThermalTransposerContainerData.empty();
        }

        boolean filling = isThermalTransposerFill(uId);
        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        List<FluidStack> fluids = new ArrayList<>();

        for (Fluid fluid : forestryBottlerFluids()) {
            if (fluid == null) {
                continue;
            }
            int filled;
            try {
                filled = handler.fill(new FluidStack(fluid, 1000), true);
            } catch (Throwable ignored) {
                continue;
            }
            if (filled <= 0) {
                continue;
            }

            if (filling) {
                ItemStack output = safeCopyStack(handler.getContainer());
                inputs.add(stack);
                outputs.add(output);
                fluids.add(new FluidStack(fluid, filled));
                safeDrain(handler, 1000);
            } else {
                ItemStack input = safeCopyStack(handler.getContainer());
                FluidStack drained = safeDrain(handler, 1000);
                if (drained == null || drained.amount <= 0) {
                    continue;
                }
                inputs.add(input);
                outputs.add(safeCopyStack(handler.getContainer()));
                fluids.add(drained);
            }
        }

        return new ThermalTransposerContainerData(inputs, outputs, fluids);
    }

    private static ItemStack safeCopyStack(ItemStack stack) throws ReflectiveOperationException {
        return stack == null ? null : copyStack(stack);
    }

    private static FluidStack safeDrain(IFluidHandlerItem handler, int amount) {
        try {
            return handler == null ? null : handler.drain(amount, true);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isThermalTransposerFill(String uId) {
        return "thermalexpansion.transposer_fill".equals(uId);
    }

    private static String thermalTransposerContainerKey(ItemStack stack, String uId) throws ReflectiveOperationException, IOException {
        StringBuilder builder = new StringBuilder(256);
        builder.append(uId == null ? "null" : uId).append('|');
        appendStackKey(builder, stack);
        return Integer.toHexString(builder.toString().hashCode());
    }

    private static void applyThermalTransposerDrawables(Object wrapper, Object guiHelper, boolean filling, int energy) throws ReflectiveOperationException, ClassNotFoundException {
        Object[] drawables = thermalTransposerDrawables(guiHelper, filling, energy);
        setFieldValue(wrapper, "progressBack", drawables[0]);
        setFieldValue(wrapper, "fluid", drawables[1]);
        setFieldValue(wrapper, "progress", drawables[2]);
        setFieldValue(wrapper, "speed", drawables[3]);
        setFieldValue(wrapper, "energyMeter", drawables[4]);
    }

    private static Object[] thermalTransposerDrawables(Object guiHelper, boolean filling, int energy) throws ReflectiveOperationException, ClassNotFoundException {
        if (guiHelper == null) {
            return new Object[5];
        }

        String key = (filling ? "fill:" : "extract:") + energy + ':' + thermalTransposerBasePower();
        synchronized (THERMAL_TRANSPOSER_DRAWABLES) {
            Map<String, Object[]> perHelper = THERMAL_TRANSPOSER_DRAWABLES.get(guiHelper);
            if (perHelper == null) {
                perHelper = new ConcurrentHashMap<>();
                THERMAL_TRANSPOSER_DRAWABLES.put(guiHelper, perHelper);
            }
            Object[] cached = perHelper.get(key);
            if (cached != null) {
                return cached;
            }

            Object drawables = Class.forName("cofh.thermalexpansion.plugins.jei.Drawables")
                    .getMethod("getDrawables", Class.forName("mezz.jei.api.IGuiHelper"))
                    .invoke(null, guiHelper);
            Object progressBack;
            Object fluidStatic;
            Object progressStatic;
            String direction;
            if (filling) {
                progressBack = invoke(drawables, "getProgressLeft", int.class, 2);
                fluidStatic = invoke(drawables, "getProgressLeft", int.class, 2);
                progressStatic = invoke(drawables, "getProgressLeftFill", int.class, 2);
                direction = "RIGHT";
            } else {
                progressBack = invoke(drawables, "getProgress", int.class, 2);
                fluidStatic = invoke(drawables, "getProgress", int.class, 2);
                progressStatic = invoke(drawables, "getProgressFill", int.class, 2);
                direction = "LEFT";
            }

            int duration = Math.max(1, energy / Math.max(1, thermalTransposerBasePower()));
            Object[] built = new Object[]{
                    progressBack,
                    createHeiAnimatedDrawable(guiHelper, fluidStatic, duration, direction, true),
                    createHeiAnimatedDrawable(guiHelper, progressStatic, duration, direction, false),
                    createHeiAnimatedDrawable(guiHelper, invoke(drawables, "getScaleFill", int.class, 1), 1000, "TOP", true),
                    createHeiAnimatedDrawable(guiHelper, invoke(drawables, "getEnergyFill"), 1000, "TOP", true)
            };
            perHelper.put(key, built);
            return built;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object createHeiAnimatedDrawable(Object guiHelper, Object drawable, int duration, String direction, boolean inverted) throws ReflectiveOperationException, ClassNotFoundException {
        Class<?> drawableStaticClass = Class.forName("mezz.jei.api.gui.IDrawableStatic");
        Class<? extends Enum> directionClass = (Class<? extends Enum>) Class.forName("mezz.jei.api.gui.IDrawableAnimated$StartDirection");
        Object directionValue = Enum.valueOf(directionClass, direction);
        Method method = guiHelper.getClass().getMethod("createAnimatedDrawable", drawableStaticClass, int.class, directionClass, boolean.class);
        return method.invoke(guiHelper, drawable, duration, directionValue, inverted);
    }

    private static Object invoke(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private static Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, parameterType);
        return method.invoke(target, argument);
    }

    private static int thermalTransposerBasePower() {
        try {
            Object value = Class.forName("cofh.thermalexpansion.block.machine.TileTransposer")
                    .getField("basePower")
                    .get(null);
            return value instanceof Number ? Math.max(1, ((Number) value).intValue()) : 1;
        } catch (Throwable ignored) {
            return 1;
        }
    }

    public static void beginRecipeProgress(Object categorizedRecipes, List<?> uncategorizedRecipes) {
        if (!GpomEarlyConfig.heiRecipeProgressBarEnabled()) {
            return;
        }
        try {
            finishRecipeProgress();

            int total = Math.max(0, totalRecipeCount(categorizedRecipes));
            if (uncategorizedRecipes != null) {
                total += uncategorizedRecipes.size();
            }
            int stepSize = Math.max(1, GpomEarlyConfig.heiRecipeProgressBarStepSize());
            int maxSteps = Math.max(1, ((Math.max(1, total) + stepSize - 1) / stepSize) + 1);
            ProgressManager.ProgressBar bar = ProgressManager.push("GPOM HEI Parsed Recipes", maxSteps);
            RecipeProgress progress = new RecipeProgress(bar, total, stepSize, maxSteps);
            RECIPE_PROGRESS.set(progress);
            progress.step("starting " + total + " parsed recipes");
            GPOM.LOGGER.info("[HEI Optimizations] Started parsed recipe progress for {} recipe object(s)", total);
        } catch (Throwable throwable) {
            RECIPE_PROGRESS.remove();
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to start parsed recipe progress bar", throwable);
        }
    }

    public static void stepRecipeProgress(Object categoryUid) {
        try {
            RecipeProgress progress = RECIPE_PROGRESS.get();
            if (progress == null) {
                return;
            }
            progress.processed++;
            long now = System.nanoTime();
            if (progress.processed < progress.nextStepAt && now - progress.lastStepNanos < 500_000_000L) {
                return;
            }
            String category = categoryUid == null ? "uncategorized" : String.valueOf(categoryUid);
            if (category.length() > 80) {
                category = category.substring(0, 80);
            }
            progress.step(progress.processed + "/" + progress.total + " " + category);
            progress.nextStepAt = Math.min(progress.total, progress.nextStepAt + progress.stepSize);
            progress.lastStepNanos = now;
        } catch (Throwable throwable) {
            RECIPE_PROGRESS.remove();
            GPOM.LOGGER.warn("[HEI Optimizations] Disabled parsed recipe progress after step failure", throwable);
        }
    }

    public static void finishRecipeProgress() {
        RecipeProgress progress = RECIPE_PROGRESS.get();
        if (progress == null) {
            return;
        }
        try {
            while (progress.stepsDone < progress.maxSteps) {
                progress.step("complete " + progress.processed + "/" + progress.total);
            }
            ProgressManager.pop(progress.bar);
            GPOM.LOGGER.info("[HEI Optimizations] Finished parsed recipe progress for {} recipe object(s)", progress.processed);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to finish parsed recipe progress bar", throwable);
        } finally {
            RECIPE_PROGRESS.remove();
        }
    }

    public static void beginRecipeRegistryBulk() {
        RECIPE_REGISTRY_BULK.set(Boolean.TRUE);
    }

    public static void finishRecipeRegistryBulk() {
        RECIPE_REGISTRY_BULK.remove();
    }

    @SuppressWarnings("rawtypes")
    public static void clearRecipeCategoriesVisibleCache(List cache) {
        if (Boolean.TRUE.equals(RECIPE_REGISTRY_BULK.get())) {
            return;
        }
        if (cache != null) {
            cache.clear();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean registerPluginsMaybeParallel(List plugins, Object modRegistry) {
        if (!GpomEarlyConfig.heiParallelPluginRegistrationEnabled() || plugins == null || modRegistry == null || plugins.size() < 2) {
            return false;
        }

        Set<String> allowlist = GpomEarlyConfig.heiParallelPluginRegistrationAllowlist();
        if (allowlist.isEmpty()) {
            return false;
        }

        int workers = configuredHeiPluginWorkers();
        if (workers <= 1) {
            return false;
        }

        long started = System.nanoTime();
        List<Object> failedPlugins = new ArrayList<>();
        ProgressManager.ProgressBar bar = null;
        ExecutorService executor = null;
        try {
            bar = ProgressManager.push("GPOM HEI Plugin Register", plugins.size());
            executor = Executors.newFixedThreadPool(workers, new ThreadFactory() {
                private int nextId = 1;

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "GPOM HEI Plugin Register " + nextId++);
                    thread.setDaemon(true);
                    return thread;
                }
            });
            CompletionService<PluginResult> completion = new ExecutorCompletionService<>(executor);
            List<Object> threadedPlugins = new ArrayList<>();
            List<Object> serialPlugins = new ArrayList<>();

            for (Object plugin : plugins) {
                if (!isParallelPluginAllowed(plugin, allowlist, GpomEarlyConfig.heiParallelPluginRegistrationDenylist())) {
                    serialPlugins.add(plugin);
                    continue;
                }
                threadedPlugins.add(plugin);
            }
            GPOM.LOGGER.info(
                    "[HEI Optimizations] HEI plugin registration split: threaded={}, serial={}, threadedPlugins={}",
                    threadedPlugins.size(),
                    serialPlugins.size(),
                    pluginNames(threadedPlugins)
            );

            for (Object plugin : serialPlugins) {
                if (!registerPluginSerial(plugin, modRegistry)) {
                    failedPlugins.add(plugin);
                }
                bar.step(pluginName(plugin));
            }

            for (Object plugin : threadedPlugins) {
                completion.submit(() -> registerPluginWorker(plugin, modRegistry, true));
            }

            for (int i = 0; i < threadedPlugins.size(); i++) {
                PluginResult result = completion.take().get();
                if (!result.success) {
                    failedPlugins.add(result.plugin);
                }
                bar.step(pluginName(result.plugin));
            }

            plugins.removeAll(failedPlugins);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Registered HEI plugins with experimental threading in {} ms (threaded={}, serial={}, failed={}, failedPlugins={})",
                    elapsedMs,
                    threadedPlugins.size(),
                    serialPlugins.size(),
                    failedPlugins.size(),
                    pluginNames(failedPlugins)
            );
            return true;
        } catch (Throwable throwable) {
            plugins.removeAll(failedPlugins);
            GPOM.LOGGER.warn(
                    "[HEI Optimizations] Experimental HEI plugin threading aborted after partial handling; not falling back to avoid double registration",
                    throwable
            );
            return true;
        } finally {
            saveThermalTransposerContainerCache();
            if (executor != null) {
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(30L, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    executor.shutdownNow();
                }
            }
            if (bar != null) {
                try {
                    ProgressManager.pop(bar);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static int totalRecipeCount(Object categorizedRecipes) {
        if (categorizedRecipes == null) {
            return 0;
        }
        try {
            Method method = categorizedRecipes.getClass().getMethod("getTotalSize");
            Object value = method.invoke(categorizedRecipes);
            if (value instanceof Number) {
                return Math.max(0, ((Number) value).intValue());
            }
        } catch (Throwable ignored) {
        }
        try {
            Method method = categorizedRecipes.getClass().getMethod("entrySet");
            Object entries = method.invoke(categorizedRecipes);
            if (!(entries instanceof Collection)) {
                return 0;
            }
            int total = 0;
            for (Object entryObject : (Collection<?>) entries) {
                if (!(entryObject instanceof Map.Entry)) {
                    continue;
                }
                Object value = ((Map.Entry<?, ?>) entryObject).getValue();
                if (value instanceof Collection) {
                    total += ((Collection<?>) value).size();
                }
            }
            return total;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static int configuredHeiPluginWorkers() {
        int configured = GpomEarlyConfig.heiParallelPluginRegistrationWorkers();
        if (configured > 0) {
            return Math.max(1, Math.min(configured, Math.max(1, Runtime.getRuntime().availableProcessors() - 1)));
        }
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static boolean isParallelPluginAllowed(Object plugin, Set<String> allowlist, Set<String> denylist) {
        String name = pluginName(plugin).toLowerCase(Locale.ROOT);
        if (denylist.contains("*") || denylist.contains(name)) {
            return false;
        }
        return allowlist.contains("*") || allowlist.contains(name);
    }

    private static boolean registerPluginSerial(Object plugin, Object modRegistry) {
        PluginResult result = registerPluginWorker(plugin, modRegistry, false);
        return result.success;
    }

    private static PluginResult registerPluginWorker(Object plugin, Object modRegistry, boolean threaded) {
        long started = System.nanoTime();
        try {
            Method method = pluginRegisterMethod(plugin.getClass());
            method.invoke(plugin, modRegistry);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info(
                    "[HEI Optimizations] Registered {} HEI plugin {} in {} ms",
                    threaded ? "threaded" : "serial",
                    pluginName(plugin),
                    elapsedMs
            );
            return new PluginResult(plugin, true);
        } catch (Throwable throwable) {
            GPOM.LOGGER.error(
                    "[HEI Optimizations] Failed to register {} HEI plugin {}; add this class to gpom.hei.parallelPluginRegistration.denylist if it was threaded",
                    threaded ? "threaded" : "serial",
                    pluginName(plugin),
                    throwable
            );
            return new PluginResult(plugin, false);
        }
    }

    private static Method pluginRegisterMethod(Class<?> pluginClass) throws ClassNotFoundException, NoSuchMethodException {
        Method cached = HEI_PLUGIN_REGISTER_METHODS.get(pluginClass);
        if (cached != null) {
            return cached;
        }
        Method method = pluginClass.getMethod("register", Class.forName("mezz.jei.api.IModRegistry"));
        Method previous = HEI_PLUGIN_REGISTER_METHODS.putIfAbsent(pluginClass, method);
        return previous != null ? previous : method;
    }

    private static String pluginName(Object plugin) {
        return plugin == null ? "null" : plugin.getClass().getName();
    }

    private static List<String> pluginNames(List<?> plugins) {
        List<String> names = new ArrayList<>(plugins.size());
        for (Object plugin : plugins) {
            names.add(pluginName(plugin));
        }
        return names;
    }

    private static final class RecipeProgress {
        private final ProgressManager.ProgressBar bar;
        private final int total;
        private final int stepSize;
        private final int maxSteps;
        private int processed;
        private int stepsDone;
        private int nextStepAt;
        private long lastStepNanos;

        private RecipeProgress(ProgressManager.ProgressBar bar, int total, int stepSize, int maxSteps) {
            this.bar = bar;
            this.total = Math.max(1, total);
            this.stepSize = Math.max(1, stepSize);
            this.maxSteps = Math.max(1, maxSteps);
            this.nextStepAt = this.stepSize;
            this.lastStepNanos = System.nanoTime();
        }

        private void step(String label) {
            if (stepsDone >= maxSteps) {
                return;
            }
            bar.step(label);
            stepsDone++;
        }
    }

    private static final class PluginResult {
        private final Object plugin;
        private final boolean success;

        private PluginResult(Object plugin, boolean success) {
            this.plugin = plugin;
            this.success = success;
        }
    }

    private static final class CachedJerTrade {
        private final ItemStack buy1;
        private final int minBuy1;
        private final int maxBuy1;
        private final ItemStack buy2;
        private final int minBuy2;
        private final int maxBuy2;
        private final ItemStack sell;
        private final int minSell;
        private final int maxSell;
        private final boolean empty;

        private CachedJerTrade(
                ItemStack buy1,
                int minBuy1,
                int maxBuy1,
                ItemStack buy2,
                int minBuy2,
                int maxBuy2,
                ItemStack sell,
                int minSell,
                int maxSell,
                boolean empty
        ) {
            this.buy1 = buy1;
            this.minBuy1 = minBuy1;
            this.maxBuy1 = maxBuy1;
            this.buy2 = buy2;
            this.minBuy2 = minBuy2;
            this.maxBuy2 = maxBuy2;
            this.sell = sell;
            this.minSell = minSell;
            this.maxSell = maxSell;
            this.empty = empty;
        }

        private static CachedJerTrade empty() {
            return new CachedJerTrade(null, 0, 0, null, 0, 0, null, 0, 0, true);
        }
    }

    private static final class ForestryBottlerRecipeRecord {
        private final ItemStack input;
        private final String fluidName;
        private final int amount;
        private final ItemStack output;
        private final boolean filling;

        private ForestryBottlerRecipeRecord(ItemStack input, String fluidName, int amount, ItemStack output, boolean filling) {
            this.input = input;
            this.fluidName = fluidName;
            this.amount = amount;
            this.output = output;
            this.filling = filling;
        }
    }

    private static final class ExtraTreesLumbermillRecipeRecord {
        private final ItemStack input;
        private final ItemStack output;

        private ExtraTreesLumbermillRecipeRecord(ItemStack input, ItemStack output) {
            this.input = input;
            this.output = output;
        }
    }

    private static final class ThermalTransposerContainerData {
        private final List<ItemStack> inputs;
        private final List<ItemStack> outputs;
        private final List<FluidStack> fluids;

        private ThermalTransposerContainerData(List<ItemStack> inputs, List<ItemStack> outputs, List<FluidStack> fluids) {
            this.inputs = inputs == null ? Collections.emptyList() : inputs;
            this.outputs = outputs == null ? Collections.emptyList() : outputs;
            this.fluids = fluids == null ? Collections.emptyList() : fluids;
        }

        private static ThermalTransposerContainerData empty() {
            return new ThermalTransposerContainerData(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }

    private static final class CompressedEnderIOTankIngredients {
        private final List<FluidStack> fluidInputs;
        private final List<ItemStack> itemInputs;
        private final List<ItemStack> itemOutputs;

        private CompressedEnderIOTankIngredients(List<FluidStack> fluidInputs, List<ItemStack> itemInputs, List<ItemStack> itemOutputs) {
            this.fluidInputs = fluidInputs == null ? Collections.emptyList() : fluidInputs;
            this.itemInputs = itemInputs == null ? Collections.emptyList() : itemInputs;
            this.itemOutputs = itemOutputs == null ? Collections.emptyList() : itemOutputs;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List loadCachedItemStacks(Object factory, Object stackHelper, Set seenUids) {
        if (!HEI_ITEM_STACK_CACHE || ForgeRegistries.ITEMS == null) {
            return null;
        }

        long started = System.nanoTime();
        File file = itemStackCacheFile();
        if (!file.isFile()) {
            return null;
        }

        try (DataInputStream input = compressedCacheInput(file)) {
            int magic = input.readInt();
            int version = input.readInt();
            String signature = input.readUTF();
            if (magic != HEI_ITEM_STACK_CACHE_MAGIC || version != HEI_ITEM_STACK_CACHE_VERSION) {
                return null;
            }
            String currentSignature = itemStackCacheSignature();
            if (!currentSignature.equals(signature)) {
                String legacySignature = legacyOrderedItemStackCacheSignature();
                if (!legacySignature.equals(signature)) {
                    GPOM.LOGGER.info(
                            "[HEI Optimizations] Ignoring stale HEI item stack cache {} (cache={}, current={})",
                            file,
                            signature,
                            currentSignature
                    );
                    return null;
                }
                GPOM.LOGGER.info("[HEI Optimizations] Loading HEI item stack cache {} with legacy ordered signature", file);
            }

            int count = input.readInt();
            if (count < 0 || count > 500_000) {
                GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt HEI item stack cache {}; invalid count {}", file, count);
                return null;
            }

            List stacks = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                int length = input.readInt();
                if (length <= 0 || length > 1_048_576) {
                    GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt HEI item stack cache {}; invalid entry length {}", file, length);
                    return null;
                }
                byte[] data = new byte[length];
                input.readFully(data);
                ItemStack stack = readItemStack(data);
                if (stack != null && !isStackEmptyReflective(stack)) {
                    stacks.add(stack);
                }
            }

            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info("[HEI Optimizations] Loaded {} HEI item stack(s) from cache in {} ms", stacks.size(), elapsedMs);
            return stacks;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to load HEI item stack cache {}; rebuilding", file, throwable);
            return null;
        }
    }

    public static void saveCachedItemStacks(List<?> stacks) {
        if (!HEI_ITEM_STACK_CACHE || stacks == null || ForgeRegistries.ITEMS == null) {
            return;
        }

        long started = System.nanoTime();
        File file = itemStackCacheFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to create HEI cache directory {}", parent);
            return;
        }

        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        int written = 0;
        try (DataOutputStream output = compressedCacheOutput(tmp)) {
            List<byte[]> serializedStacks = new ArrayList<>(stacks.size());
            for (Object object : stacks) {
                if (object instanceof ItemStack) {
                    byte[] data = writeItemStack((ItemStack) object);
                    if (data.length > 0) {
                        serializedStacks.add(data);
                    }
                }
            }

            output.writeInt(HEI_ITEM_STACK_CACHE_MAGIC);
            output.writeInt(HEI_ITEM_STACK_CACHE_VERSION);
            output.writeUTF(itemStackCacheSignature());
            output.writeInt(serializedStacks.size());
            for (byte[] data : serializedStacks) {
                output.writeInt(data.length);
                output.write(data);
                written++;
            }
        } catch (Throwable throwable) {
            if (tmp.isFile() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to save HEI item stack cache {}", file, throwable);
            return;
        }

        if (file.isFile() && !file.delete()) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to replace old HEI item stack cache {}", file);
            return;
        }
        if (!tmp.renameTo(file)) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to move HEI item stack cache into place {}", file);
            return;
        }

        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        GPOM.LOGGER.info("[HEI Optimizations] Saved {} HEI item stack(s) to cache in {} ms", written, elapsedMs);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List addMissingRegistryItemStacks(List stacks, Object factory, Object stackHelper, Set seenUids) {
        try {
            if (stacks == null || factory == null || stackHelper == null || seenUids == null || ForgeRegistries.ITEMS == null) {
                return stacks;
            }

            Set<ResourceLocation> seenItems = collectSeenItemIds(stacks);
            int added = 0;
            int considered = 0;
            for (Item item : ForgeRegistries.ITEMS) {
                if (item == null) {
                    continue;
                }
                ResourceLocation registryName = item.getRegistryName();
                if (registryName == null || "minecraft:air".equals(registryName.toString()) || seenItems.contains(registryName)) {
                    continue;
                }
                considered++;
                stacks.add(new ItemStack(item));
                added++;
            }
            if (considered > 0) {
                GPOM.LOGGER.info(
                        "[HEI Optimizations] added {} stack(s) from {} registry-only item id(s) after creative-tab enumeration",
                        added,
                        considered
                );
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to add registry-only HEI item stacks; continuing with creative-tab list", throwable);
        }
        return stacks;
    }

    public static void logRegistryOnlyItems(List<?> stacks) {
        try {
            if (stacks == null || ForgeRegistries.ITEMS == null) {
                return;
            }

            Set<ResourceLocation> seenItems = collectSeenItemIds(stacks);
            List<String> missing = new ArrayList<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (item == null) {
                    continue;
                }
                ResourceLocation registryName = item.getRegistryName();
                if (registryName != null && !"minecraft:air".equals(registryName.toString()) && !seenItems.contains(registryName)) {
                    missing.add(registryName.toString());
                }
            }

            if (missing.isEmpty()) {
                GPOM.LOGGER.info("[HEI Optimizations] creative-tab item enumeration covered all {} registered item ids", seenItems.size());
                return;
            }

            Collections.sort(missing);
            int maxLogged = intProperty("gpom.hei.logRegistryOnlyItems.max", 250);
            GPOM.LOGGER.warn(
                    "[HEI Optimizations] creative-tab item enumeration missed {} registered item id(s); showing up to {}. Missing ids: {}",
                    missing.size(),
                    Math.max(0, maxLogged),
                    missing.subList(0, Math.min(Math.max(0, maxLogged), missing.size()))
            );
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to log registry-only HEI items", throwable);
        }
    }

    private static Set<ResourceLocation> collectSeenItemIds(List<?> stacks) throws ReflectiveOperationException {
        Set<ResourceLocation> seenItems = new HashSet<>();
        for (Object object : stacks) {
            if (!(object instanceof ItemStack)) {
                continue;
            }
            ItemStack stack = (ItemStack) object;
            Object itemObject = getItemReflective(stack);
            if (isStackEmptyReflective(stack) || !(itemObject instanceof Item)) {
                continue;
            }
            ResourceLocation registryName = ((Item) itemObject).getRegistryName();
            if (registryName != null) {
                seenItems.add(registryName);
            }
        }
        return seenItems;
    }

    public static Object createJerEnchantmentWrapper(ItemStack stack) {
        try {
            if (!mayHaveJerEnchantments(stack)) {
                return null;
            }
            Method method = jerEnchantmentWrapperCreate;
            if (method == null) {
                method = Class.forName("jeresources.jei.enchantment.EnchantmentWrapper")
                        .getMethod("create", ItemStack.class);
                jerEnchantmentWrapperCreate = method;
            }
            return method.invoke(null, stack);
        } catch (Throwable ignored) {
            return createJerEnchantmentWrapperFallback(stack);
        }
    }

    public static void fastHeiFallbackSubtypeInterpreter(Object subtypeRegistry, ItemStack stack) {
        try {
            if (subtypeRegistry == null || stack == null) {
                return;
            }
            Object item = getItemReflective(stack);
            if (item == null) {
                return;
            }

            byte state = heiFallbackSubtypeState(item);
            if (state == HEI_SUBTYPE_PRESENT) {
                return;
            }
            if (state == HEI_SUBTYPE_NOT_CHECKED && hasHeiSubtypeInterpreter(subtypeRegistry, stack)) {
                setHeiFallbackSubtypeState(item, HEI_SUBTYPE_PRESENT);
                return;
            }

            Object interpreter = heiFluidSubtypeInterpreter();
            String subtype = applyHeiFluidSubtypeInterpreter(interpreter, stack);
            if (subtype != null && !subtype.isEmpty()) {
                registerHeiSubtypeInterpreter(subtypeRegistry, item, interpreter);
                setHeiFallbackSubtypeState(item, HEI_SUBTYPE_PRESENT);
            } else {
                setHeiFallbackSubtypeState(item, HEI_SUBTYPE_NONE_KNOWN);
            }
        } catch (Throwable ignored) {
            // Equivalent to HEI failing its optional fallback subtype path: leave the stack registered normally.
        }
    }

    @SuppressWarnings("unchecked")
    public static List<?> fastJerVillagerCareers(Object profession) {
        try {
            Field field = jerVillagerProfessionCareers;
            if (field == null) {
                field = findField(profession.getClass(), "careers");
                jerVillagerProfessionCareers = field;
            }
            return (List<?>) field.get(profession);
        } catch (Throwable ignored) {
            Object value = jerGetPrivateValue(profession, "careers");
            return value instanceof List ? (List<?>) value : Collections.emptyList();
        }
    }

    public static List<?> fastJerVillagerTrades(Object career) {
        try {
            Field field = jerVillagerCareerTrades;
            if (field == null) {
                field = findField(career.getClass(), "trades");
                jerVillagerCareerTrades = field;
            }
            return (List<?>) field.get(career);
        } catch (Throwable ignored) {
            Object value = jerGetPrivateValue(career, "trades");
            return value instanceof List ? (List<?>) value : Collections.emptyList();
        }
    }

    public static int fastJerVillagerCareerId(Object career) {
        try {
            Field field = jerVillagerCareerId;
            if (field == null) {
                field = findField(career.getClass(), "id");
                jerVillagerCareerId = field;
            }
            return field.getInt(career);
        } catch (Throwable ignored) {
            Object value = jerGetPrivateValue(career, "id");
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }
    }

    public static void beginJerVillagerTradeCache() {
        if (!GpomEarlyConfig.heiJerVillagerTradeCacheEnabled()) {
            return;
        }
        loadJerVillagerTradeCache();
        jerVillagerTradeCacheHits = 0;
        jerVillagerTradeCacheMisses = 0;
        jerVillagerTradeCacheFailed = 0;
    }

    public static void finishJerVillagerTradeCache() {
        if (!GpomEarlyConfig.heiJerVillagerTradeCacheEnabled()) {
            return;
        }
        saveJerVillagerTradeCacheIfDirty();
        GPOM.LOGGER.info(
                "[HEI Optimizations] JER villager trade cache stats: hits={}, misses={}, failed={}, entries={}",
                jerVillagerTradeCacheHits,
                jerVillagerTradeCacheMisses,
                jerVillagerTradeCacheFailed,
                JER_VILLAGER_TRADE_CACHE.size()
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean addJerTradeListCached(List tradeList, Object tradeGenerator) {
        if (!GpomEarlyConfig.heiJerVillagerTradeCacheEnabled() || tradeList == null || tradeGenerator == null) {
            return false;
        }

        try {
            loadJerVillagerTradeCache();
            String key = jerTradeGeneratorKey(tradeGenerator);
            CachedJerTrade cached = JER_VILLAGER_TRADE_CACHE.get(key);
            if (cached != null) {
                jerVillagerTradeCacheHits++;
                addCachedJerTrade(tradeList, cached);
                return true;
            }

            cached = sampleJerTradeGenerator(tradeGenerator, key);
            if (cached == null) {
                jerVillagerTradeCacheFailed++;
                return false;
            }

            JER_VILLAGER_TRADE_CACHE.put(key, cached);
            jerVillagerTradeCacheDirty = true;
            jerVillagerTradeCacheMisses++;
            addCachedJerTrade(tradeList, cached);
            return true;
        } catch (Throwable throwable) {
            jerVillagerTradeCacheFailed++;
            String generatorName = tradeGenerator.getClass().getName();
            String failureKey = generatorName + '|' + throwable.getClass().getName() + '|' + String.valueOf(throwable.getMessage());
            if (JER_FAILED_GENERATORS.add(failureKey)) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed JER villager trade cache path for {}; falling back to stock JER", generatorName, throwable);
            } else {
                GPOM.LOGGER.debug("[HEI Optimizations] Repeated JER villager trade cache failure for {}; falling back to stock JER", generatorName);
            }
            return false;
        }
    }

    private static CachedJerTrade sampleJerTradeGenerator(Object tradeGenerator, String key) throws ReflectiveOperationException, IOException {
        MerchantRecipeList recipes = new MerchantRecipeList();
        Method method = findMethodWithParameters(
                tradeGenerator.getClass(),
                "func_190888_a",
                "addMerchantRecipe",
                IMerchant.class,
                MerchantRecipeList.class,
                Random.class
        );
        if (method == null) {
            throw new NoSuchMethodException(tradeGenerator.getClass().getName() + "#addMerchantRecipe");
        }

        Random random = new Random(0x47504F4D5A5A5A5AL ^ key.hashCode());
        Object merchant = jerFakeMerchant();
        int samples = GpomEarlyConfig.heiJerVillagerTradeCacheSamples();
        for (int i = 0; i < samples; i++) {
            method.invoke(tradeGenerator, merchant, recipes, random);
        }

        if (recipes.isEmpty()) {
            return CachedJerTrade.empty();
        }

        MerchantRecipe first = (MerchantRecipe) recipes.get(0);
        ItemStack buy1 = merchantRecipeItemToBuy(first);
        ItemStack buy2 = merchantRecipeSecondItemToBuy(first);
        ItemStack sell = merchantRecipeItemToSell(first);
        if (buy1 == null || sell == null) {
            return CachedJerTrade.empty();
        }

        int minBuy1 = stackCount(buy1);
        int maxBuy1 = minBuy1;
        int minBuy2 = buy2 == null ? 0 : stackCount(buy2);
        int maxBuy2 = minBuy2;
        int minSell = stackCount(sell);
        int maxSell = minSell;

        for (Object object : recipes) {
            if (!(object instanceof MerchantRecipe)) {
                continue;
            }
            MerchantRecipe recipe = (MerchantRecipe) object;
            ItemStack currentBuy1 = merchantRecipeItemToBuy(recipe);
            ItemStack currentBuy2 = merchantRecipeSecondItemToBuy(recipe);
            ItemStack currentSell = merchantRecipeItemToSell(recipe);
            if (currentBuy1 != null) {
                int count = stackCount(currentBuy1);
                minBuy1 = Math.min(minBuy1, count);
                maxBuy1 = Math.max(maxBuy1, count);
            }
            if (buy2 != null && currentBuy2 != null) {
                int count = stackCount(currentBuy2);
                minBuy2 = Math.min(minBuy2, count);
                maxBuy2 = Math.max(maxBuy2, count);
            }
            if (currentSell != null) {
                int count = stackCount(currentSell);
                minSell = Math.min(minSell, count);
                maxSell = Math.max(maxSell, count);
            }
        }

        return new CachedJerTrade(
                stackWithCount(buy1, 1),
                minBuy1,
                maxBuy1,
                buy2 == null ? null : stackWithCount(buy2, 1),
                minBuy2,
                maxBuy2,
                stackWithCount(sell, 1),
                minSell,
                maxSell,
                false
        );
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addCachedJerTrade(List tradeList, CachedJerTrade cached) throws ReflectiveOperationException {
        if (cached == null || cached.empty) {
            return;
        }
        Constructor<?> constructor = jerTradeConstructor;
        if (constructor == null) {
            constructor = Class.forName("jeresources.collection.TradeList$Trade").getDeclaredConstructor(
                    ItemStack.class,
                    int.class,
                    int.class,
                    ItemStack.class,
                    int.class,
                    int.class,
                    ItemStack.class,
                    int.class,
                    int.class
            );
            constructor.setAccessible(true);
            jerTradeConstructor = constructor;
        }
        Object trade = constructor.newInstance(
                copyStack(cached.buy1),
                cached.minBuy1,
                cached.maxBuy1,
                copyStack(cached.buy2),
                cached.minBuy2,
                cached.maxBuy2,
                copyStack(cached.sell),
                cached.minSell,
                cached.maxSell
        );
        tradeList.add(trade);
    }

    private static Object jerFakeMerchant() throws ReflectiveOperationException {
        Object merchant = jerFakeMerchant;
        if (merchant == null) {
            merchant = Class.forName("jeresources.util.FakeMerchant").newInstance();
            jerFakeMerchant = merchant;
        }
        return merchant;
    }

    private static ItemStack merchantRecipeItemToBuy(MerchantRecipe recipe) throws ReflectiveOperationException {
        Method method = merchantRecipeGetItemToBuy;
        if (method == null) {
            method = findMethod(recipe.getClass(), "func_77394_a", "getItemToBuy");
            merchantRecipeGetItemToBuy = method;
        }
        return invokeMerchantRecipeStack(recipe, method, "MerchantRecipe.getItemToBuy");
    }

    private static ItemStack merchantRecipeSecondItemToBuy(MerchantRecipe recipe) throws ReflectiveOperationException {
        Method method = merchantRecipeGetSecondItemToBuy;
        if (method == null) {
            method = findMethod(recipe.getClass(), "func_77396_b", "getSecondItemToBuy");
            merchantRecipeGetSecondItemToBuy = method;
        }
        return invokeMerchantRecipeStack(recipe, method, "MerchantRecipe.getSecondItemToBuy");
    }

    private static ItemStack merchantRecipeItemToSell(MerchantRecipe recipe) throws ReflectiveOperationException {
        Method method = merchantRecipeGetItemToSell;
        if (method == null) {
            method = findMethod(recipe.getClass(), "func_77397_d", "getItemToSell");
            merchantRecipeGetItemToSell = method;
        }
        return invokeMerchantRecipeStack(recipe, method, "MerchantRecipe.getItemToSell");
    }

    private static ItemStack invokeMerchantRecipeStack(MerchantRecipe recipe, Method method, String label) throws ReflectiveOperationException {
        if (method == null) {
            throw new NoSuchMethodException(label);
        }
        Object value = method.invoke(recipe);
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    private static int stackCount(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null) {
            return 0;
        }
        Method method = itemStackGetCount;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_190916_E", "getCount");
            itemStackGetCount = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.getCount");
        }
        Object value = method.invoke(stack);
        return value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
    }

    private static int stackMetadata(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null) {
            return 0;
        }
        Method method = itemStackGetMetadata;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77960_j", "getMetadata", "getItemDamage");
            itemStackGetMetadata = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.getMetadata");
        }
        Object value = method.invoke(stack);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean stackIsDamageable(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null) {
            return false;
        }
        Method method = itemStackIsDamageable;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77984_f", "isItemStackDamageable", "isDamageable");
            itemStackIsDamageable = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.isItemStackDamageable");
        }
        Object value = method.invoke(stack);
        return Boolean.TRUE.equals(value);
    }

    private static int stackMaxDamage(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null) {
            return 0;
        }
        Method method = itemStackGetMaxDamage;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77958_k", "getMaxDamage");
            itemStackGetMaxDamage = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.getMaxDamage");
        }
        Object value = method.invoke(stack);
        return value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
    }

    private static int stackItemDamage(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null) {
            return 0;
        }
        Method method = itemStackGetItemDamage;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77952_i", "getItemDamage", "getMetadata");
            itemStackGetItemDamage = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.getItemDamage");
        }
        Object value = method.invoke(stack);
        return value instanceof Number ? Math.max(0, ((Number) value).intValue()) : 0;
    }

    private static void setStackItemDamage(ItemStack stack, int damage) throws ReflectiveOperationException {
        if (stack == null) {
            return;
        }
        Method method = itemStackSetItemDamage;
        if (method == null) {
            method = findMethodWithParameters(stack.getClass(), "func_77964_b", "setItemDamage", int.class);
            itemStackSetItemDamage = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.setItemDamage");
        }
        method.invoke(stack, Math.max(0, damage));
    }

    private static ItemStack stackWithCount(ItemStack stack, int count) throws ReflectiveOperationException {
        if (stack == null) {
            return null;
        }
        ItemStack copy = copyStack(stack);
        Method method = itemStackSetCount;
        if (method == null) {
            method = findMethodWithParameters(copy.getClass(), "func_190920_e", "setCount", int.class);
            itemStackSetCount = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.setCount");
        }
        method.invoke(copy, count);
        return copy;
    }

    private static ItemStack copyStack(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null) {
            return null;
        }
        Method method = itemStackCopy;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77946_l", "copy");
            itemStackCopy = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.copy");
        }
        Object value = method.invoke(stack);
        if (value instanceof ItemStack) {
            return (ItemStack) value;
        }
        throw new NoSuchMethodException("ItemStack.copy returned " + (value == null ? "null" : value.getClass().getName()));
    }

    private static void loadJerVillagerTradeCache() {
        if (jerVillagerTradeCacheLoaded) {
            return;
        }
        synchronized (JER_VILLAGER_TRADE_CACHE) {
            if (jerVillagerTradeCacheLoaded) {
                return;
            }
            jerVillagerTradeCacheLoaded = true;

            File file = jerVillagerTradeCacheFile();
            if (!file.isFile()) {
                return;
            }

            long started = System.nanoTime();
            try (DataInputStream input = compressedCacheInput(file)) {
                int magic = input.readInt();
                int version = input.readInt();
                String signature = input.readUTF();
                if (magic != JER_VILLAGER_TRADE_CACHE_MAGIC
                        || version != JER_VILLAGER_TRADE_CACHE_VERSION
                        || !jerVillagerTradeCacheSignature().equals(signature)) {
                    return;
                }

                int count = input.readInt();
                if (count < 0 || count > 100_000) {
                    GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt JER villager trade cache {}; invalid count {}", file, count);
                    JER_VILLAGER_TRADE_CACHE.clear();
                    return;
                }

                for (int i = 0; i < count; i++) {
                    String key = input.readUTF();
                    CachedJerTrade trade = readCachedJerTrade(input);
                    if (key != null && trade != null) {
                        JER_VILLAGER_TRADE_CACHE.put(key, trade);
                    }
                }

                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                GPOM.LOGGER.info("[HEI Optimizations] Loaded {} JER villager trade cache entrie(s) in {} ms", JER_VILLAGER_TRADE_CACHE.size(), elapsedMs);
            } catch (Throwable throwable) {
                JER_VILLAGER_TRADE_CACHE.clear();
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to load JER villager trade cache {}; rebuilding", file, throwable);
            }
        }
    }

    private static void saveJerVillagerTradeCacheIfDirty() {
        if (!jerVillagerTradeCacheDirty || JER_VILLAGER_TRADE_CACHE.isEmpty()) {
            return;
        }
        synchronized (JER_VILLAGER_TRADE_CACHE) {
            if (!jerVillagerTradeCacheDirty || JER_VILLAGER_TRADE_CACHE.isEmpty()) {
                return;
            }

            long started = System.nanoTime();
            File file = jerVillagerTradeCacheFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to create JER cache directory {}", parent);
                return;
            }

            File tmp = new File(parent, file.getName() + ".tmp");
            try (DataOutputStream output = compressedCacheOutput(tmp)) {
                output.writeInt(JER_VILLAGER_TRADE_CACHE_MAGIC);
                output.writeInt(JER_VILLAGER_TRADE_CACHE_VERSION);
                output.writeUTF(jerVillagerTradeCacheSignature());
                output.writeInt(JER_VILLAGER_TRADE_CACHE.size());
                for (Map.Entry<String, CachedJerTrade> entry : JER_VILLAGER_TRADE_CACHE.entrySet()) {
                    output.writeUTF(entry.getKey());
                    writeCachedJerTrade(output, entry.getValue());
                }
            } catch (Throwable throwable) {
                if (tmp.isFile() && !tmp.delete()) {
                    tmp.deleteOnExit();
                }
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to save JER villager trade cache {}", file, throwable);
                return;
            }

            if (file.isFile() && !file.delete()) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to replace old JER villager trade cache {}", file);
                return;
            }
            if (!tmp.renameTo(file)) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to move JER villager trade cache into place {}", file);
                return;
            }

            jerVillagerTradeCacheDirty = false;
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info("[HEI Optimizations] Saved {} JER villager trade cache entrie(s) in {} ms", JER_VILLAGER_TRADE_CACHE.size(), elapsedMs);
        }
    }

    private static CachedJerTrade readCachedJerTrade(DataInputStream input) throws ReflectiveOperationException, IOException {
        boolean empty = input.readBoolean();
        ItemStack buy1 = readNullableItemStack(input);
        int minBuy1 = input.readInt();
        int maxBuy1 = input.readInt();
        ItemStack buy2 = readNullableItemStack(input);
        int minBuy2 = input.readInt();
        int maxBuy2 = input.readInt();
        ItemStack sell = readNullableItemStack(input);
        int minSell = input.readInt();
        int maxSell = input.readInt();
        return new CachedJerTrade(buy1, minBuy1, maxBuy1, buy2, minBuy2, maxBuy2, sell, minSell, maxSell, empty);
    }

    private static void writeCachedJerTrade(DataOutputStream output, CachedJerTrade trade) throws ReflectiveOperationException, IOException {
        output.writeBoolean(trade.empty);
        writeNullableItemStack(output, trade.buy1);
        output.writeInt(trade.minBuy1);
        output.writeInt(trade.maxBuy1);
        writeNullableItemStack(output, trade.buy2);
        output.writeInt(trade.minBuy2);
        output.writeInt(trade.maxBuy2);
        writeNullableItemStack(output, trade.sell);
        output.writeInt(trade.minSell);
        output.writeInt(trade.maxSell);
    }

    private static void writeNullableItemStack(DataOutputStream output, ItemStack stack) throws ReflectiveOperationException, IOException {
        if (stack == null) {
            output.writeInt(0);
            return;
        }
        byte[] data = writeItemStack(stack);
        output.writeInt(data.length);
        output.write(data);
    }

    private static ItemStack readNullableItemStack(DataInputStream input) throws ReflectiveOperationException, IOException {
        int length = input.readInt();
        if (length <= 0) {
            return null;
        }
        if (length > 1_048_576) {
            throw new IOException("invalid ItemStack length " + length);
        }
        byte[] data = new byte[length];
        input.readFully(data);
        return readItemStack(data);
    }

    private static void loadThermalTransposerContainerCache() {
        if (!GpomEarlyConfig.heiThermalTransposerContainerCacheEnabled() || thermalTransposerContainerCacheLoaded) {
            return;
        }
        synchronized (THERMAL_TRANSPOSER_CONTAINER_CACHE) {
            if (thermalTransposerContainerCacheLoaded) {
                return;
            }
            thermalTransposerContainerCacheLoaded = true;

            File file = thermalTransposerContainerCacheFile();
            if (!file.isFile()) {
                return;
            }

            long started = System.nanoTime();
            try (DataInputStream input = compressedCacheInput(file)) {
                int magic = input.readInt();
                int version = input.readInt();
                String signature = input.readUTF();
                String currentSignature = thermalTransposerContainerCacheSignature();
                if (magic != THERMAL_TRANSPOSER_CONTAINER_CACHE_MAGIC
                        || version != THERMAL_TRANSPOSER_CONTAINER_CACHE_VERSION
                        || !currentSignature.equals(signature)) {
                    GPOM.LOGGER.info(
                            "[HEI Optimizations] Ignoring stale Thermal Expansion Transposer container cache {} (cache={}, current={})",
                            file,
                            signature,
                            currentSignature
                    );
                    return;
                }

                int count = input.readInt();
                if (count < 0 || count > 100_000) {
                    GPOM.LOGGER.warn("[HEI Optimizations] Ignoring corrupt Thermal Expansion Transposer container cache {}; invalid count {}", file, count);
                    return;
                }

                for (int i = 0; i < count; i++) {
                    String key = input.readUTF();
                    THERMAL_TRANSPOSER_CONTAINER_CACHE.put(key, readThermalTransposerContainerData(input));
                }
                long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
                GPOM.LOGGER.info("[HEI Optimizations] Loaded {} Thermal Expansion Transposer container cache entrie(s) in {} ms", count, elapsedMs);
            } catch (Throwable throwable) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to load Thermal Expansion Transposer container cache {}; rebuilding", file, throwable);
                THERMAL_TRANSPOSER_CONTAINER_CACHE.clear();
            }
        }
    }

    private static void saveThermalTransposerContainerCache() {
        if (!GpomEarlyConfig.heiThermalTransposerContainerCacheEnabled() || !thermalTransposerContainerCacheDirty) {
            return;
        }
        synchronized (THERMAL_TRANSPOSER_CONTAINER_CACHE) {
            if (!thermalTransposerContainerCacheDirty) {
                return;
            }

            long started = System.nanoTime();
            File file = thermalTransposerContainerCacheFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to create HEI cache directory {}", parent);
                return;
            }

            File tmp = parent == null ? new File(file.getPath() + ".tmp") : new File(parent, file.getName() + ".tmp");
            int written = 0;
            try (DataOutputStream output = compressedCacheOutput(tmp)) {
                output.writeInt(THERMAL_TRANSPOSER_CONTAINER_CACHE_MAGIC);
                output.writeInt(THERMAL_TRANSPOSER_CONTAINER_CACHE_VERSION);
                output.writeUTF(thermalTransposerContainerCacheSignature());
                output.writeInt(THERMAL_TRANSPOSER_CONTAINER_CACHE.size());
                for (Map.Entry<String, ThermalTransposerContainerData> entry : THERMAL_TRANSPOSER_CONTAINER_CACHE.entrySet()) {
                    output.writeUTF(entry.getKey());
                    writeThermalTransposerContainerData(output, entry.getValue());
                    written++;
                }
            } catch (Throwable throwable) {
                if (tmp.isFile() && !tmp.delete()) {
                    tmp.deleteOnExit();
                }
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to save Thermal Expansion Transposer container cache {}", file, throwable);
                return;
            }

            if (file.isFile() && !file.delete()) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to replace old Thermal Expansion Transposer container cache {}", file);
                return;
            }
            if (!tmp.renameTo(file)) {
                GPOM.LOGGER.warn("[HEI Optimizations] Failed to move Thermal Expansion Transposer container cache into place {}", file);
                return;
            }

            thermalTransposerContainerCacheDirty = false;
            long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
            GPOM.LOGGER.info("[HEI Optimizations] Saved {} Thermal Expansion Transposer container cache entrie(s) in {} ms", written, elapsedMs);
        }
    }

    private static ThermalTransposerContainerData readThermalTransposerContainerData(DataInputStream input) throws ReflectiveOperationException, IOException {
        return new ThermalTransposerContainerData(
                readItemStackList(input),
                readItemStackList(input),
                readFluidStackList(input)
        );
    }

    private static void writeThermalTransposerContainerData(DataOutputStream output, ThermalTransposerContainerData data) throws ReflectiveOperationException, IOException {
        writeItemStackList(output, data.inputs);
        writeItemStackList(output, data.outputs);
        writeFluidStackList(output, data.fluids);
    }

    private static void writeItemStackList(DataOutputStream output, List<ItemStack> stacks) throws ReflectiveOperationException, IOException {
        output.writeInt(stacks.size());
        for (ItemStack stack : stacks) {
            writeNullableItemStack(output, stack);
        }
    }

    private static List<ItemStack> readItemStackList(DataInputStream input) throws ReflectiveOperationException, IOException {
        int count = input.readInt();
        if (count < 0 || count > 250_000) {
            throw new IOException("invalid ItemStack list count " + count);
        }
        List<ItemStack> stacks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ItemStack stack = readNullableItemStack(input);
            if (stack != null) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static void writeFluidStackList(DataOutputStream output, List<FluidStack> fluids) throws IOException {
        output.writeInt(fluids.size());
        for (FluidStack fluidStack : fluids) {
            if (fluidStack == null || fluidStack.getFluid() == null || fluidStack.amount <= 0) {
                output.writeUTF("");
                output.writeInt(0);
            } else {
                output.writeUTF(fluidStack.getFluid().getName());
                output.writeInt(fluidStack.amount);
            }
        }
    }

    private static List<FluidStack> readFluidStackList(DataInputStream input) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > 250_000) {
            throw new IOException("invalid FluidStack list count " + count);
        }
        List<FluidStack> fluids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String fluidName = input.readUTF();
            int amount = input.readInt();
            Fluid fluid = fluidName.isEmpty() ? null : FluidRegistry.getFluid(fluidName);
            if (fluid != null && amount > 0) {
                fluids.add(new FluidStack(fluid, amount));
            }
        }
        return fluids;
    }

    private static File thermalTransposerContainerCacheFile() {
        return GpomCaches.file("hei", "thermal-transposer-containers-v1.dat");
    }

    private static String thermalTransposerContainerCacheSignature() {
        StringBuilder builder = new StringBuilder(64 * 1024);
        builder.append("v=").append(THERMAL_TRANSPOSER_CONTAINER_CACHE_VERSION).append('\n');
        builder.append("items=").append(itemStackCacheSignature()).append('\n');
        builder.append("fluids=");
        for (Fluid fluid : forestryBottlerFluids()) {
            if (fluid == null) {
                builder.append("null;");
            } else {
                builder.append(fluid.getName()).append('@').append(fluid.getClass().getName()).append(';');
            }
        }
        return Integer.toHexString(builder.toString().hashCode());
    }

    private static File jerVillagerTradeCacheFile() {
        return GpomCaches.file("hei", "jer-villager-trades-v1.dat");
    }

    private static String jerVillagerTradeCacheSignature() {
        return itemStackCacheSignature() + ":samples=" + GpomEarlyConfig.heiJerVillagerTradeCacheSamples();
    }

    private static String jerTradeGeneratorKey(Object tradeGenerator) throws ReflectiveOperationException, IOException {
        StringBuilder builder = new StringBuilder(512);
        appendStableObject(builder, tradeGenerator, 0);
        return tradeGenerator.getClass().getName() + ':' + Integer.toHexString(builder.toString().hashCode());
    }

    private static void appendStableObject(StringBuilder builder, Object value, int depth) throws ReflectiveOperationException, IOException {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (depth > 4) {
            builder.append(value.getClass().getName());
            return;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum) {
            builder.append(value.getClass().getName()).append('(').append(value).append(')');
            return;
        }
        if (value instanceof ResourceLocation) {
            builder.append("rl(").append(value).append(')');
            return;
        }
        if (value instanceof ItemStack) {
            appendStackKey(builder, (ItemStack) value);
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            builder.append(type.getName()).append('[');
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                appendStableObject(builder, Array.get(value, i), depth + 1);
                builder.append(',');
            }
            builder.append(']');
            return;
        }
        if (value instanceof Iterable) {
            builder.append(type.getName()).append('[');
            int count = 0;
            for (Object element : (Iterable<?>) value) {
                appendStableObject(builder, element, depth + 1);
                builder.append(',');
                if (++count > 512) {
                    builder.append("...");
                    break;
                }
            }
            builder.append(']');
            return;
        }

        builder.append(type.getName()).append('{');
        List<Field> fields = new ArrayList<>();
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            fields.addAll(Arrays.asList(cursor.getDeclaredFields()));
        }
        Collections.sort(fields, (left, right) -> left.getName().compareTo(right.getName()));
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                continue;
            }
            field.setAccessible(true);
            builder.append(field.getName()).append('=');
            appendStableObject(builder, field.get(value), depth + 1);
            builder.append(';');
        }
        builder.append('}');
    }

    private static void appendStackKey(StringBuilder builder, ItemStack stack) throws ReflectiveOperationException, IOException {
        if (stack == null) {
            builder.append("stack(null)");
            return;
        }
        Object itemObject = getItemReflective(stack);
        ResourceLocation registryName = itemObject instanceof Item ? ((Item) itemObject).getRegistryName() : null;
        builder.append("stack(")
                .append(registryName == null ? "unknown" : registryName.toString())
                .append('@')
                .append(stackMetadata(stack))
                .append('#')
                .append(stackCount(stack))
                .append(':')
                .append(Integer.toHexString(Arrays.hashCode(writeItemStack(stack))))
                .append(')');
    }

    private static boolean mayHaveJerEnchantments(ItemStack stack) {
        return mayHaveJerEnchantmentsReflective(stack);
    }

    private static boolean mayHaveJerEnchantmentsReflective(ItemStack stack) {
        try {
            if (stack == null) {
                return false;
            }
            Method isEmpty = itemStackIsEmpty;
            if (isEmpty == null) {
                isEmpty = findMethod(stack.getClass(), "func_190926_b", "isEmpty");
                itemStackIsEmpty = isEmpty;
            }
            if (isEmpty != null && Boolean.TRUE.equals(isEmpty.invoke(stack))) {
                return false;
            }

            Method getItem = itemStackGetItem;
            if (getItem == null) {
                getItem = findMethod(stack.getClass(), "func_77973_b", "getItem");
                itemStackGetItem = getItem;
            }
            if (getItem == null) {
                return true;
            }
            Object item = getItem.invoke(stack);
            if (item == null) {
                return false;
            }
            Object book = minecraftBookItem;
            if (book == null) {
                book = findStaticField(Class.forName("net.minecraft.init.Items"), "field_151122_aG", "BOOK");
                minecraftBookItem = book;
            }
            if (item == book) {
                return true;
            }

            Method enchantability = itemGetEnchantability;
            if (enchantability == null) {
                enchantability = findMethod(item.getClass(), "func_77619_b", "getItemEnchantability");
                itemGetEnchantability = enchantability;
            }
            if (enchantability == null) {
                return true;
            }
            Object value = enchantability.invoke(item);
            return value instanceof Number && ((Number) value).intValue() > 0;
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static Object getItemReflective(ItemStack stack) throws ReflectiveOperationException {
        Method getItem = itemStackGetItem;
        if (getItem == null) {
            getItem = findMethod(stack.getClass(), "func_77973_b", "getItem");
            itemStackGetItem = getItem;
        }
        return getItem == null ? null : getItem.invoke(stack);
    }

    private static byte[] writeItemStack(ItemStack stack) throws ReflectiveOperationException, IOException {
        NBTTagCompound tag = new NBTTagCompound();
        Method method = itemStackWriteToNbt;
        if (method == null) {
            method = findMethodWithParameters(stack.getClass(), "func_77955_b", "writeToNBT", NBTTagCompound.class);
            itemStackWriteToNbt = method;
        }
        if (method == null) {
            throw new NoSuchMethodException("ItemStack.writeToNBT");
        }
        Object written = method.invoke(stack, tag);
        if (written instanceof NBTTagCompound) {
            tag = (NBTTagCompound) written;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        Method write = nbtDataWrite;
        if (write == null) {
            write = findStaticMethod(Class.forName("net.minecraft.nbt.CompressedStreamTools"), "func_74800_a", "write", NBTTagCompound.class, DataOutput.class);
            nbtDataWrite = write;
        }
        if (write == null) {
            throw new NoSuchMethodException("CompressedStreamTools.write");
        }
        DataOutputStream output = new DataOutputStream(bytes);
        write.invoke(null, tag, output);
        output.flush();
        return bytes.toByteArray();
    }

    private static ItemStack readItemStack(byte[] data) throws ReflectiveOperationException {
        Method read = nbtDataRead;
        if (read == null) {
            read = findStaticMethod(Class.forName("net.minecraft.nbt.CompressedStreamTools"), "func_74794_a", "read", DataInputStream.class);
            nbtDataRead = read;
        }
        if (read == null) {
            throw new NoSuchMethodException("CompressedStreamTools.read");
        }
        Object tag = read.invoke(null, new DataInputStream(new ByteArrayInputStream(data)));
        if (!(tag instanceof NBTTagCompound)) {
            return null;
        }
        return new ItemStack((NBTTagCompound) tag);
    }

    private static boolean isStackEmptyReflective(ItemStack stack) throws ReflectiveOperationException {
        Method isEmpty = itemStackIsEmpty;
        if (isEmpty == null) {
            isEmpty = findMethod(stack.getClass(), "func_190926_b", "isEmpty");
            itemStackIsEmpty = isEmpty;
        }
        return isEmpty != null && Boolean.TRUE.equals(isEmpty.invoke(stack));
    }

    private static byte heiFallbackSubtypeState(Object item) {
        Byte state = HEI_FALLBACK_SUBTYPE_STATE.get(item);
        return state == null ? HEI_SUBTYPE_NOT_CHECKED : state;
    }

    private static void setHeiFallbackSubtypeState(Object item, byte state) {
        HEI_FALLBACK_SUBTYPE_STATE.put(item, state);
    }

    private static boolean hasHeiSubtypeInterpreter(Object subtypeRegistry, ItemStack stack) throws ReflectiveOperationException {
        Method method = heiHasSubtypeInterpreter;
        if (method == null) {
            method = subtypeRegistry.getClass().getMethod("hasSubtypeInterpreter", ItemStack.class);
            method.setAccessible(true);
            heiHasSubtypeInterpreter = method;
        }
        return Boolean.TRUE.equals(method.invoke(subtypeRegistry, stack));
    }

    private static Object heiFluidSubtypeInterpreter() throws ReflectiveOperationException {
        Object interpreter = heiFluidSubtypeInterpreter;
        if (interpreter == null) {
            interpreter = Class.forName("mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory$FluidSubtypeInterpreter")
                    .getField("INSTANCE")
                    .get(null);
            heiFluidSubtypeInterpreter = interpreter;
        }
        return interpreter;
    }

    private static String applyHeiFluidSubtypeInterpreter(Object interpreter, ItemStack stack) throws ReflectiveOperationException {
        Method method = heiFluidSubtypeApply;
        if (method == null) {
            method = interpreter.getClass().getMethod("apply", ItemStack.class);
            method.setAccessible(true);
            heiFluidSubtypeApply = method;
        }
        Object value = method.invoke(interpreter, stack);
        return value instanceof String ? (String) value : "";
    }

    private static void registerHeiSubtypeInterpreter(Object subtypeRegistry, Object item, Object interpreter) throws ReflectiveOperationException, ClassNotFoundException {
        Method method = heiRegisterSubtypeInterpreter;
        if (method == null) {
            method = subtypeRegistry.getClass().getMethod(
                    "registerSubtypeInterpreter",
                    Class.forName("net.minecraft.item.Item"),
                    Class.forName("mezz.jei.api.ISubtypeRegistry$ISubtypeInterpreter")
            );
            method.setAccessible(true);
            heiRegisterSubtypeInterpreter = method;
        }
        method.invoke(subtypeRegistry, item, interpreter);
    }

    public static Object fastJerPrivateValue(Class<?> ownerClass, Object target, String fieldName) {
        try {
            if (ownerClass == null || target == null || fieldName == null) {
                return jerGetPrivateValue(target, fieldName);
            }
            String key = ownerClass.getName() + '#' + fieldName;
            Field field = JER_PRIVATE_FIELDS.get(key);
            if (field == null) {
                field = findField(ownerClass, fieldName);
                Field existing = JER_PRIVATE_FIELDS.putIfAbsent(key, field);
                if (existing != null) {
                    field = existing;
                }
            }
            return field.get(target);
        } catch (Throwable ignored) {
            return jerGetPrivateValue(target, fieldName);
        }
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findMethodWithParameters(Class<?> type, String srgName, String mcpName, Class<?>... parameterTypes) {
        for (String name : new String[]{srgName, mcpName}) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findStaticMethod(Class<?> type, String srgName, String mcpName, Class<?>... parameterTypes) {
        return findMethodWithParameters(type, srgName, mcpName, parameterTypes);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void setFieldValue(Object target, String fieldName, Object value) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchFieldException(fieldName);
        }
        findField(target.getClass(), fieldName).set(target, value);
    }

    private static void setIntFieldValue(Object target, String fieldName, int value) throws ReflectiveOperationException {
        if (target == null) {
            throw new NoSuchFieldException(fieldName);
        }
        findField(target.getClass(), fieldName).setInt(target, value);
    }

    private static Object jerGetPrivateValue(Object target, String fieldName) {
        try {
            if (target == null) {
                return null;
            }
            Method method = jerGetPrivateValue;
            if (method == null) {
                method = Class.forName("jeresources.util.ReflectionHelper")
                        .getMethod("getPrivateValue", Class.class, Object.class, String.class);
                method.setAccessible(true);
                jerGetPrivateValue = method;
            }
            return method.invoke(null, target.getClass(), target, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object findStaticField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getField(name).get(null);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object createJerEnchantmentWrapperFallback(ItemStack stack) {
        try {
            Method method = jerEnchantmentWrapperCreate;
            if (method == null) {
                method = Class.forName("jeresources.jei.enchantment.EnchantmentWrapper")
                        .getMethod("create", ItemStack.class);
                jerEnchantmentWrapperCreate = method;
            }
            return method.invoke(null, stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int computeSearchWorkerCount() {
        int fallback = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        int configured = intProperty("gpom.hei.searchWorkers", fallback);
        int max = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        int workers = Math.max(1, Math.min(configured, max));
        GPOM.LOGGER.info("[HEI Optimizations] Using {} HEI async search worker(s)", workers);
        return workers;
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Integer.parseInt(System.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static File itemStackCacheFile() {
        return GpomCaches.file("hei", "hei-item-stacks-v2.dat");
    }

    private static DataInputStream compressedCacheInput(File file) throws IOException {
        return new DataInputStream(new BufferedInputStream(new InflaterInputStream(
                new BufferedInputStream(new FileInputStream(file))
        )));
    }

    private static DataOutputStream compressedCacheOutput(File file) throws IOException {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        return new DataOutputStream(new BufferedOutputStream(new OwnedDeflaterOutputStream(
                new BufferedOutputStream(new FileOutputStream(file)),
                deflater
        )));
    }

    private static final class OwnedDeflaterOutputStream extends DeflaterOutputStream {
        private final Deflater ownedDeflater;

        private OwnedDeflaterOutputStream(OutputStream output, Deflater deflater) {
            super(output, deflater, 8192);
            this.ownedDeflater = deflater;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                ownedDeflater.end();
            }
        }
    }

    private static String itemStackCacheSignature() {
        return itemStackCacheSignature(true);
    }

    private static String legacyOrderedItemStackCacheSignature() {
        return itemStackCacheSignature(false);
    }

    private static String itemStackCacheSignature(boolean sortEntries) {
        List<String> entries = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null) {
                continue;
            }
            ResourceLocation registryName = item.getRegistryName();
            if (registryName != null) {
                entries.add(registryName + "@" + item.getClass().getName());
            }
        }
        if (sortEntries) {
            Collections.sort(entries);
        }

        StringBuilder builder = new StringBuilder(64 * 1024);
        builder.append("v=").append(HEI_ITEM_STACK_CACHE_VERSION).append('\n');
        builder.append("items=");
        for (String entry : entries) {
            builder.append(entry).append(';');
        }
        return Integer.toHexString(builder.toString().hashCode());
    }
}
