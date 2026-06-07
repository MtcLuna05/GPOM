package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class BetweenlandsOptimizations {
    private static final String MOD_ID = "thebetweenlands";
    private static final int FIELD_ACCESS_SUMMARY_LIMIT = 20;
    private static final ConcurrentHashMap<String, LongAdder> BLOCK_FIELD_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> BLOCK_FIELD_CONTEXT_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> LAZY_BLOCK_FIELD_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> DIRECT_BLOCK_REGISTRATION_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> DIRECT_BLOCK_NULL_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> DIRECT_ITEM_REGISTRATION_COUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LongAdder> DIRECT_ITEM_NULL_COUNTS = new ConcurrentHashMap<>();
    private static volatile int expectedDirectBlockRegistrations;
    private static volatile int expectedDirectItemRegistrations;
    private static Object betweenlandsInstance;
    private static volatile Collection<Block> betweenlandsBlocks;
    private static volatile List<ItemBlock> betweenlandsItemBlocks;
    private static volatile Set<Item> betweenlandsItems;
    private static volatile Class<?> customItemBlockInterface;
    private static volatile Method getItemBlockMethod;
    private static volatile Method blockTranslationKeyMethod;
    private static volatile Method itemTranslationKeyMethod;
    private static volatile boolean deferredBlockItemBlocksPopulated;

    private BetweenlandsOptimizations() {
    }

    public static void registerModEntity(Class<? extends Entity> entityClass, String name, int id, int range, int updateFrequency, boolean sendVelocityUpdates) {
        EntityRegistry.registerModEntity(
                new ResourceLocation(MOD_ID, name),
                entityClass,
                MOD_ID + '.' + name,
                id,
                betweenlandsInstance(),
                range,
                updateFrequency,
                sendVelocityUpdates
        );
    }

    public static void registerEgg(String name, int primaryColor, int secondaryColor) {
        EntityRegistry.registerEgg(new ResourceLocation(MOD_ID, name), primaryColor, secondaryColor);
    }

    public static void recordBlockRegistryFieldAccess(String context, String fieldName) {
        if (context == null || fieldName == null) {
            return;
        }
        BLOCK_FIELD_COUNTS.computeIfAbsent(fieldName, ignored -> new LongAdder()).increment();
        BLOCK_FIELD_CONTEXT_COUNTS.computeIfAbsent(context + " -> " + fieldName, ignored -> new LongAdder()).increment();
    }

    public static Block lazyBlockField(String fieldName) {
        LAZY_BLOCK_FIELD_COUNTS.computeIfAbsent(fieldName, ignored -> new LongAdder()).increment();
        try {
            Class<?> registry = Class.forName("thebetweenlands.common.registries.BlockRegistry");
            try {
                Method lazyMethod = registry.getMethod("gpom$lazyBlock$" + fieldName);
                Object value = lazyMethod.invoke(null);
                if (value instanceof Block) {
                    return (Block) value;
                }
            } catch (NoSuchMethodException ignored) {
                // Fields that remain eager do not have generated lazy constructors.
            }

            Field field = registry.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof Block) {
                return (Block) value;
            }
        } catch (Throwable throwable) {
            throw new IllegalStateException("Unable to resolve Betweenlands BlockRegistry." + fieldName, throwable);
        }

        throw new IllegalStateException("Betweenlands BlockRegistry." + fieldName + " was read before it was assigned");
    }

    public static void beginBlockRegistryDirectPreInit(int expectedRegistrations) {
        if (!directBlockProbeCountersEnabled()) {
            return;
        }
        expectedDirectBlockRegistrations = expectedRegistrations;
        DIRECT_BLOCK_REGISTRATION_COUNTS.clear();
        DIRECT_BLOCK_NULL_COUNTS.clear();
    }

    public static Block recordBlockRegistryDirectPreInit(String fieldName, String registryName, Block block) {
        if (!directBlockProbeCountersEnabled()) {
            return block;
        }
        String key = fieldName + " -> thebetweenlands:" + registryName;
        DIRECT_BLOCK_REGISTRATION_COUNTS.computeIfAbsent(key, ignored -> new LongAdder()).increment();
        if (block == null) {
            DIRECT_BLOCK_NULL_COUNTS.computeIfAbsent(key, ignored -> new LongAdder()).increment();
            if (GpomEarlyConfig.startupProfilerProbeLogsEnabled()) {
                GPOM.LOGGER.error("[StartupProfiler] [Probe] BL direct BlockRegistry preInit resolved null for {}", key);
            }
        }
        return block;
    }

    private static boolean directBlockProbeCountersEnabled() {
        return GpomEarlyConfig.startupProfilerProbeLogsEnabled()
                || GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled();
    }

    public static void registerBlockWithoutItemBlock(String registryName, Block block) {
        if (block == null) {
            return;
        }
        betweenlandsBlocks().add(block);
        block.setRegistryName(MOD_ID, registryName);
        setTranslationKey(block, MOD_ID + "." + registryName);
    }

    public static void beginItemRegistryDirectPreInit(int expectedRegistrations) {
        if (!directItemProbeCountersEnabled()) {
            return;
        }
        expectedDirectItemRegistrations = expectedRegistrations;
        DIRECT_ITEM_REGISTRATION_COUNTS.clear();
        DIRECT_ITEM_NULL_COUNTS.clear();
    }

    public static Item recordItemRegistryDirectPreInit(String fieldName, Item item) {
        if (!directItemProbeCountersEnabled()) {
            return item;
        }
        DIRECT_ITEM_REGISTRATION_COUNTS.computeIfAbsent(fieldName, ignored -> new LongAdder()).increment();
        if (item == null) {
            DIRECT_ITEM_NULL_COUNTS.computeIfAbsent(fieldName, ignored -> new LongAdder()).increment();
            if (GpomEarlyConfig.startupProfilerProbeLogsEnabled()) {
                GPOM.LOGGER.error("[StartupProfiler] [Probe] BL direct ItemRegistry preInit resolved null for {}", fieldName);
            }
        }
        return item;
    }

    private static boolean directItemProbeCountersEnabled() {
        return GpomEarlyConfig.startupProfilerProbeLogsEnabled()
                || GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled();
    }

    public static void registerItem(String fieldName, Item item) {
        if (item == null) {
            return;
        }
        String registryName = fieldName.toLowerCase(java.util.Locale.ENGLISH);
        betweenlandsItems().add(item);
        item.setRegistryName(MOD_ID, registryName);
        setTranslationKey(item, MOD_ID + "." + registryName);
    }

    public static void populateDeferredBlockItemBlocks() {
        if (deferredBlockItemBlocksPopulated) {
            return;
        }
        synchronized (BetweenlandsOptimizations.class) {
            if (deferredBlockItemBlocksPopulated) {
                return;
            }

            List<ItemBlock> itemBlocks = betweenlandsItemBlocks();
            if (!itemBlocks.isEmpty()) {
                deferredBlockItemBlocksPopulated = true;
                return;
            }

            Class<?> customItemBlock = customItemBlockInterface();
            Method getItemBlock = getItemBlockMethod(customItemBlock);
            for (Block block : betweenlandsBlocks()) {
                ItemBlock itemBlock = createItemBlock(block, customItemBlock, getItemBlock);
                if (itemBlock == null) {
                    continue;
                }

                ResourceLocation blockName = block.getRegistryName();
                if (blockName == null) {
                    throw new IllegalStateException("Betweenlands block has no registry name: " + block);
                }
                String path = registryPath(blockName);
                itemBlocks.add(itemBlock);
                itemBlock.setRegistryName(MOD_ID, path);
                setTranslationKey(itemBlock, MOD_ID + "." + path);
            }
            deferredBlockItemBlocksPopulated = true;
        }
    }

    public static void logItemRegistryDirectPreInitSummary() {
        if (!GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled()) {
            return;
        }
        int total = sum(DIRECT_ITEM_REGISTRATION_COUNTS);
        int nulls = sum(DIRECT_ITEM_NULL_COUNTS);
        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL direct ItemRegistry preInit generated registrations={} expected={} nulls={}",
                total,
                expectedDirectItemRegistrations,
                nulls
        );
        if (nulls > 0) {
            logTop("item direct-registration nulls", DIRECT_ITEM_NULL_COUNTS);
        }
    }

    public static void logBlockRegistryDirectPreInitSummary() {
        if (!GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled()) {
            return;
        }
        int total = sum(DIRECT_BLOCK_REGISTRATION_COUNTS);
        int nulls = sum(DIRECT_BLOCK_NULL_COUNTS);
        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL direct BlockRegistry preInit generated registrations={} expected={} nulls={}",
                total,
                expectedDirectBlockRegistrations,
                nulls
        );
        if (nulls > 0) {
            logTop("direct-registration nulls", DIRECT_BLOCK_NULL_COUNTS);
        }
    }

    public static void logBlockRegistryFieldAccessSummary() {
        if (!GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled()) {
            return;
        }
        int total = sum(BLOCK_FIELD_COUNTS);
        if (total == 0) {
            GPOM.LOGGER.info("[StartupProfiler] [Probe] BL lazy-field feasibility saw no BlockRegistry field reads during pre-init");
            return;
        }

        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL lazy-field feasibility BlockRegistry field reads total={} uniqueFields={} uniqueContexts={}",
                total,
                BLOCK_FIELD_COUNTS.size(),
                BLOCK_FIELD_CONTEXT_COUNTS.size()
        );
        logTop("fields", BLOCK_FIELD_COUNTS);
        logTop("contexts", BLOCK_FIELD_CONTEXT_COUNTS);
        logLazyBlockAccessorSummary();
    }

    private static Object betweenlandsInstance() {
        Object instance = betweenlandsInstance;
        if (instance != null) {
            return instance;
        }

        try {
            instance = Class.forName("thebetweenlands.common.TheBetweenlands").getField("instance").get(null);
            betweenlandsInstance = instance;
            return instance;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<Block> betweenlandsBlocks() {
        Collection<Block> blocks = betweenlandsBlocks;
        if (blocks != null) {
            return blocks;
        }
        try {
            blocks = (Collection<Block>) Class.forName("thebetweenlands.common.registries.BlockRegistry")
                    .getField("BLOCKS")
                    .get(null);
            betweenlandsBlocks = blocks;
            return blocks;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve Betweenlands BlockRegistry.BLOCKS", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ItemBlock> betweenlandsItemBlocks() {
        List<ItemBlock> itemBlocks = betweenlandsItemBlocks;
        if (itemBlocks != null) {
            return itemBlocks;
        }
        try {
            itemBlocks = (List<ItemBlock>) Class.forName("thebetweenlands.common.registries.BlockRegistry")
                    .getField("ITEM_BLOCKS")
                    .get(null);
            betweenlandsItemBlocks = itemBlocks;
            return itemBlocks;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve Betweenlands BlockRegistry.ITEM_BLOCKS", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Item> betweenlandsItems() {
        Set<Item> items = betweenlandsItems;
        if (items != null) {
            return items;
        }
        try {
            items = (Set<Item>) Class.forName("thebetweenlands.common.registries.ItemRegistry")
                    .getField("ITEMS")
                    .get(null);
            betweenlandsItems = items;
            return items;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to resolve Betweenlands ItemRegistry.ITEMS", exception);
        }
    }

    private static Class<?> customItemBlockInterface() {
        Class<?> customItemBlock = customItemBlockInterface;
        if (customItemBlock != null) {
            return customItemBlock;
        }
        try {
            customItemBlock = Class.forName("thebetweenlands.common.registries.BlockRegistry$ICustomItemBlock");
            customItemBlockInterface = customItemBlock;
            return customItemBlock;
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to resolve Betweenlands custom ItemBlock interface", exception);
        }
    }

    private static Method getItemBlockMethod(Class<?> customItemBlock) {
        Method method = getItemBlockMethod;
        if (method != null) {
            return method;
        }
        try {
            method = customItemBlock.getMethod("getItemBlock");
            getItemBlockMethod = method;
            return method;
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException("Unable to resolve Betweenlands custom ItemBlock factory", exception);
        }
    }

    private static ItemBlock createItemBlock(Block block, Class<?> customItemBlock, Method getItemBlock) {
        if (!customItemBlock.isInstance(block)) {
            return new ItemBlock(block);
        }
        try {
            Object itemBlock = getItemBlock.invoke(block);
            if (itemBlock == null) {
                return null;
            }
            if (!(itemBlock instanceof ItemBlock)) {
                throw new IllegalStateException("Betweenlands custom ItemBlock factory returned " + itemBlock.getClass().getName());
            }
            return (ItemBlock) itemBlock;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create Betweenlands custom ItemBlock for " + block, exception);
        }
    }

    private static String registryPath(ResourceLocation registryName) {
        String value = registryName.toString();
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private static void setTranslationKey(Block block, String key) {
        try {
            translationKeyMethod(Block.class, true).invoke(block, key);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set Betweenlands block translation key", exception);
        }
    }

    private static void setTranslationKey(ItemBlock itemBlock, String key) {
        setTranslationKey((Item) itemBlock, key);
    }

    private static void setTranslationKey(Item item, String key) {
        try {
            translationKeyMethod(Item.class, false).invoke(item, key);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to set Betweenlands item translation key", exception);
        }
    }

    private static Method translationKeyMethod(Class<?> type, boolean block) {
        Method cached = block ? blockTranslationKeyMethod : itemTranslationKeyMethod;
        if (cached != null) {
            return cached;
        }

        Method method = findTranslationKeyMethod(type);
        if (block) {
            blockTranslationKeyMethod = method;
        } else {
            itemTranslationKeyMethod = method;
        }
        return method;
    }

    private static Method findTranslationKeyMethod(Class<?> type) {
        for (String name : new String[] {"func_149663_c", "func_77655_b", "setTranslationKey"}) {
            try {
                Method method = type.getMethod(name, String.class);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Runtime names differ depending on the remapping layer.
            }
        }
        throw new IllegalStateException("Unable to resolve translation key method for " + type.getName());
    }

    private static int sum(ConcurrentHashMap<String, LongAdder> counters) {
        int total = 0;
        for (LongAdder counter : counters.values()) {
            total += counter.intValue();
        }
        return total;
    }

    private static void logTop(String label, ConcurrentHashMap<String, LongAdder> counters) {
        if (!GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled()) {
            return;
        }
        List<Map.Entry<String, LongAdder>> entries = new ArrayList<>(counters.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, LongAdder> entry) -> entry.getValue().intValue()).reversed());
        int limit = Math.min(FIELD_ACCESS_SUMMARY_LIMIT, entries.size());
        for (int index = 0; index < limit; index++) {
            Map.Entry<String, LongAdder> entry = entries.get(index);
            GPOM.LOGGER.info(
                    "[StartupProfiler] [Probe] BL lazy-field feasibility top {} #{} count={} - {}",
                    label,
                    index + 1,
                    entry.getValue().intValue(),
                    entry.getKey()
            );
        }
    }

    private static void logLazyBlockAccessorSummary() {
        if (!GpomEarlyConfig.startupProfilerProbeSummaryLogsEnabled()) {
            return;
        }
        int total = sum(LAZY_BLOCK_FIELD_COUNTS);
        if (total == 0) {
            GPOM.LOGGER.info("[StartupProfiler] [Probe] BL lazy block accessors were not used during pre-init");
            return;
        }

        GPOM.LOGGER.info(
                "[StartupProfiler] [Probe] BL lazy block accessors resolved total={} uniqueFields={}",
                total,
                LAZY_BLOCK_FIELD_COUNTS.size()
        );
        logTop("lazy-accessor fields", LAZY_BLOCK_FIELD_COUNTS);
    }
}
