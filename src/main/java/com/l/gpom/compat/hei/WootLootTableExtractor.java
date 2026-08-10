package com.l.gpom.compat.hei;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootEntryItem;
import net.minecraft.world.storage.loot.LootEntryTable;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Builds potential Woot outputs from final Forge loot tables without spawning or killing mobs.
 * Entity construction and LootTableManager access stay on HEI's registration thread. Only frozen
 * table traversal is dispatched to workers.
 */
final class WootLootTableExtractor {
    private static final int CACHE_SCHEMA = 2;
    private static final String JER_FAKE_WORLD = "jeresources.util.FakeClientWorld";
    private static final String JER_LOOT_HELPER = "jeresources.util.LootTableHelper";
    private static final String CACHE_DIRECTORY = "gpom-cache";
    private static final String CACHE_FILE = "woot-loot-tables.properties";
    private static final Base64.Encoder KEY_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder KEY_DECODER = Base64.getUrlDecoder();
    private static final Object CACHE_IO_LOCK = new Object();

    private WootLootTableExtractor() {
    }

    static World createFakeWorld() {
        if (!Loader.isModLoaded("jeresources")) {
            return null;
        }
        try {
            return (World) Class.forName(JER_FAKE_WORLD, true,
                    WootLootTableExtractor.class.getClassLoader()).newInstance();
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Could not create JER fake world", throwable);
            return null;
        }
    }

    static Result extract(Collection<String> mobNames) {
        if (!GpomEarlyConfig.heiWootLootTableExtractionEnabled()) {
            WootJeiDiagnostics.log("Loot-table extraction disabled by config");
            return Result.empty();
        }
        if (!Loader.isModLoaded("jeresources")) {
            WootJeiDiagnostics.log("Loot-table extraction unavailable because JER is absent");
            return Result.empty();
        }

        long started = System.nanoTime();
        try {
            Context context = Context.create();
            Map<String, EntityEntry> registryEntries = registryEntries();
            Map<String, EntityRecord> entities = resolveEntities(mobNames, registryEntries, context);
            Map<String, TableRecord> tables = resolveTables(entities, context);
            Result result = materialize(entities, tables);
            saveCache(entities, tables);

            int outputCount = 0;
            for (List<WootMobDropsRecipeWrapper.DropInfo> drops : result.dropsByMob.values()) {
                outputCount += drops.size();
            }
            WootJeiDiagnostics.log(
                    "Loot-table extraction completed: entities={}, tables={}, cacheHits={}, rebuilt={}, "
                            + "outputs={}, unsupportedEntries={}, unsupportedFunctions={}, unsupportedMobs={}, "
                            + "workers={}, millis={}",
                    entities.size(), tables.size(), context.cacheHits, context.rebuiltTables,
                    outputCount, context.unsupportedEntries, context.unsupportedFunctions,
                    result.unsupportedFunctionsByMob.size(), context.workers, elapsedMillis(started));
            if (!result.unsupportedFunctionsByMob.isEmpty()) {
                WootJeiDiagnostics.log("Unsupported-function mobs selected for optional runtime learning: {}",
                        result.unsupportedFunctionsByMob);
            }
            return result;
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Loot-table extraction failed; learned/custom Woot data remains available",
                    throwable);
            return Result.empty();
        }
    }

    private static Map<String, EntityEntry> registryEntries() {
        Map<String, EntityEntry> entries = new LinkedHashMap<>();
        for (EntityEntry entry : ForgeRegistries.ENTITIES.getValuesCollection()) {
            ResourceLocation id = entry.getRegistryName();
            if (id != null) {
                entries.put(id.toString(), entry);
            }
        }
        return entries;
    }

    private static Map<String, EntityRecord> resolveEntities(Collection<String> mobNames,
                                                              Map<String, EntityEntry> registryEntries,
                                                              Context context) {
        Map<String, EntityRecord> entities = new LinkedHashMap<>();
        List<String> sorted = new ArrayList<>(mobNames);
        Collections.sort(sorted);
        for (String mobName : sorted) {
            EntityEntry entry = registryEntries.get(mobName);
            if (entry == null || entry.getEntityClass() == null) {
                continue;
            }
            Class<?> entityClass = entry.getEntityClass();
            String sourceSignature = classSourceSignature(entityClass);
            EntityRecord cached = readEntityRecord(context.cache, mobName);
            if (cached != null
                    && entityClass.getName().equals(cached.className)
                    && sourceSignature.equals(cached.sourceSignature)
                    && cached.tableId != null) {
                entities.put(mobName, cached);
                continue;
            }

            ResourceLocation entityId = entry.getRegistryName();
            ResourceLocation tableId = resolveLootTableId(entityId, context.fakeWorld);
            EntityRecord resolved = new EntityRecord(
                    mobName, entityClass.getName(), sourceSignature,
                    tableId == null ? null : tableId.toString());
            entities.put(mobName, resolved);
        }
        return entities;
    }

    private static ResourceLocation resolveLootTableId(ResourceLocation entityId, World fakeWorld) {
        if (entityId == null || fakeWorld == null) {
            return null;
        }
        try {
            Entity entity = MinecraftMappingCompat.createEntityById(entityId, fakeWorld);
            if (entity instanceof EntityLiving) {
                return MinecraftMappingCompat.entityLivingLootTable((EntityLiving) entity);
            }
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Could not resolve loot table for " + entityId, throwable);
        }
        return null;
    }

    private static Map<String, TableRecord> resolveTables(Map<String, EntityRecord> entities,
                                                           Context context)
            throws InterruptedException, ExecutionException {
        Set<String> roots = new TreeSet<>();
        for (EntityRecord entity : entities.values()) {
            if (entity.tableId != null && !entity.tableId.isEmpty()) {
                roots.add(entity.tableId);
            }
        }

        Map<String, TableRecord> records = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        for (String root : roots) {
            TableRecord cached = readTableRecord(context.cache, root);
            if (cached != null) {
                String signature = dependencySignature(cached.dependencies, context.scripts);
                if (signature.equals(cached.signature)) {
                    records.put(root, cached);
                    context.cacheHits++;
                    context.unsupportedFunctions += cached.unsupportedFunctions.size();
                    continue;
                }
            }
            misses.add(root);
        }

        if (misses.isEmpty()) {
            return records;
        }

        Map<String, LootTable> loadedTables = new LinkedHashMap<>();
        Map<String, Set<String>> rootDependencies = new LinkedHashMap<>();
        for (String root : misses) {
            Set<String> dependencies = new LinkedHashSet<>();
            preloadGraph(root, context.manager, loadedTables, dependencies, new HashSet<String>());
            if (dependencies.isEmpty()) {
                dependencies.add(root);
            }
            rootDependencies.put(root, dependencies);
        }

        ExecutorService executor = Executors.newFixedThreadPool(context.workers, new ExtractionThreadFactory());
        try {
            Map<String, Future<Analysis>> futures = new LinkedHashMap<>();
            for (final String root : misses) {
                futures.put(root, executor.submit(new Callable<Analysis>() {
                    @Override
                    public Analysis call() {
                        return analyze(root, loadedTables);
                    }
                }));
            }
            for (Map.Entry<String, Future<Analysis>> entry : futures.entrySet()) {
                Analysis analysis = entry.getValue().get();
                Set<String> dependencies = rootDependencies.get(entry.getKey());
                String signature = dependencySignature(dependencies, context.scripts);
                TableRecord record = new TableRecord(entry.getKey(), signature,
                        dependencies, analysis.drops, analysis.unsupportedFunctionNames);
                records.put(entry.getKey(), record);
                context.rebuiltTables++;
                context.unsupportedEntries += analysis.unsupportedEntries;
                context.unsupportedFunctions += analysis.unsupportedFunctions;
            }
        } finally {
            executor.shutdownNow();
        }
        return records;
    }

    private static void preloadGraph(String tableId,
                                     LootTableManager manager,
                                     Map<String, LootTable> loaded,
                                     Set<String> dependencies,
                                     Set<String> active) {
        if (tableId == null || !active.add(tableId)) {
            return;
        }
        dependencies.add(tableId);
        LootTable table = loaded.get(tableId);
        if (table == null) {
            table = MinecraftMappingCompat.lootTableManagerGetTable(manager, new ResourceLocation(tableId));
            if (table != null) {
                loaded.put(tableId, table);
            }
        }
        if (table != null) {
            for (Object pool : listField(table, "lootTable.pools", "field_186466_c", "c", "pools")) {
                for (Object entry : listField(pool, "lootPool.entries",
                        "field_186453_a", "a", "lootEntries")) {
                    if (entry instanceof LootEntryTable) {
                        Object nested = MinecraftMappingCompat.fieldValue(entry, "lootEntryTable.table",
                                "field_186371_a", "lootTable", "table");
                        if (nested instanceof ResourceLocation) {
                            preloadGraph(nested.toString(), manager, loaded, dependencies, active);
                        }
                    }
                }
            }
        }
        active.remove(tableId);
    }

    private static Analysis analyze(String root, Map<String, LootTable> loadedTables) {
        Analysis analysis = new Analysis();
        analyzeTable(root, loadedTables, analysis, new HashSet<String>());
        analysis.mergeEquivalentDrops();
        return analysis;
    }

    private static void analyzeTable(String tableId,
                                     Map<String, LootTable> loadedTables,
                                     Analysis analysis,
                                     Set<String> active) {
        if (!active.add(tableId)) {
            return;
        }
        LootTable table = loadedTables.get(tableId);
        if (table == null) {
            active.remove(tableId);
            return;
        }
        for (Object poolObject : listField(table, "lootTable.pools", "field_186466_c", "c", "pools")) {
            if (!(poolObject instanceof LootPool)) {
                continue;
            }
            LootPool pool = (LootPool) poolObject;
            List<String> poolNotes = conditionNotes(listField(pool, "lootPool.conditions",
                    "field_186454_b", "b", "conditions"));
            for (Object entryObject : listField(pool, "lootPool.entries",
                    "field_186453_a", "a", "lootEntries")) {
                if (entryObject instanceof LootEntryItem) {
                    DerivedDrop drop = itemDrop((LootEntryItem) entryObject, poolNotes, analysis);
                    if (drop != null) {
                        analysis.drops.add(drop);
                    }
                } else if (entryObject instanceof LootEntryTable) {
                    Object nested = MinecraftMappingCompat.fieldValue(entryObject, "lootEntryTable.table",
                            "field_186371_a", "lootTable", "table");
                    if (nested instanceof ResourceLocation) {
                        analyzeTable(nested.toString(), loadedTables, analysis, active);
                    }
                } else if (entryObject instanceof LootEntry) {
                    String name = entryObject.getClass().getName();
                    if (!"net.minecraft.world.storage.loot.LootEntryEmpty".equals(name)) {
                        analysis.unsupportedEntries++;
                    }
                }
            }
        }
        active.remove(tableId);
    }

    private static DerivedDrop itemDrop(LootEntryItem entry,
                                        List<String> poolNotes,
                                        Analysis analysis) {
        Object itemValue = MinecraftMappingCompat.fieldValue(entry, "lootEntryItem.item",
                "field_186368_a", "item");
        if (!(itemValue instanceof Item)) {
            return null;
        }

        List<ItemStack> stacks = new ArrayList<>();
        stacks.add(new ItemStack((Item) itemValue));
        int[] minimum = {1, 1, 1, 1};
        int[] maximum = {1, 1, 1, 1};
        List<String> notes = new ArrayList<>(poolNotes);
        notes.addAll(conditionNotes(arrayField(entry, "lootEntry.conditions",
                "field_186366_e", "conditions")));

        for (Object function : arrayField(entry, "lootEntryItem.functions",
                "field_186369_b", "functions")) {
            if (function == null) {
                continue;
            }
            String className = function.getClass().getName();
            if ("net.minecraft.world.storage.loot.functions.SetCount".equals(className)) {
                Object range = MinecraftMappingCompat.fieldValue(function, "setCount.range",
                        "field_186568_a", "countRange");
                int min = floor(rangeMinimum(range));
                int max = floor(rangeMaximum(range));
                for (int level = 0; level < 4; level++) {
                    minimum[level] = Math.max(0, min);
                    maximum[level] = Math.max(minimum[level], max);
                }
            } else if ("net.minecraft.world.storage.loot.functions.SetMetadata".equals(className)) {
                Object range = MinecraftMappingCompat.fieldValue(function, "setMetadata.range",
                        "field_186573_b", "metaRange");
                stacks = metadataVariants(stacks, floor(rangeMinimum(range)), floor(rangeMaximum(range)));
            } else if ("net.minecraft.world.storage.loot.functions.SetNBT".equals(className)) {
                Object tagValue = MinecraftMappingCompat.fieldValue(function, "setNbt.tag",
                        "field_186570_a", "tag");
                if (tagValue instanceof NBTTagCompound) {
                    for (ItemStack stack : stacks) {
                        NBTTagCompound tag = MinecraftMappingCompat.itemStackTagCompound(stack);
                        if (tag == null) {
                            tag = new NBTTagCompound();
                            MinecraftMappingCompat.itemStackSetTagCompound(stack, tag);
                        }
                        MinecraftMappingCompat.nbtMerge(tag, (NBTTagCompound) tagValue);
                    }
                }
            } else if ("net.minecraft.world.storage.loot.functions.LootingEnchantBonus".equals(className)) {
                Object range = MinecraftMappingCompat.fieldValue(function, "lootingBonus.range",
                        "field_186563_a", "count");
                Object limitValue = MinecraftMappingCompat.fieldValue(function, "lootingBonus.limit",
                        "field_189971_b", "limit");
                int limit = limitValue instanceof Number ? ((Number) limitValue).intValue() : 0;
                for (int level = 1; level < 4; level++) {
                    minimum[level] += Math.round(level * rangeMinimum(range));
                    maximum[level] += Math.round(level * rangeMaximum(range));
                    if (limit > 0) {
                        minimum[level] = Math.min(minimum[level], limit);
                        maximum[level] = Math.min(maximum[level], limit);
                    }
                }
                notes.add("Quantity is affected by Looting");
            } else if ("net.minecraft.world.storage.loot.functions.Smelt".equals(className)) {
                notes.add("Smelting applies only when the mob is burning; Woot's normal fake-player kill is unburned");
            } else if ("net.minecraft.world.storage.loot.functions.EnchantRandomly".equals(className)
                    || "net.minecraft.world.storage.loot.functions.EnchantWithLevels".equals(className)) {
                notes.add("The generated item may carry a random enchantment");
            } else if ("net.minecraft.world.storage.loot.functions.SetDamage".equals(className)) {
                notes.add("The generated item may have variable durability");
            } else {
                notes.add("Unresolved loot function: " + simpleName(className));
                analysis.unsupportedFunctions++;
                analysis.unsupportedFunctionNames.add(className);
            }
        }

        List<ItemStack> normalized = new ArrayList<>();
        for (ItemStack stack : stacks) {
            if (!MinecraftMappingCompat.itemStackIsEmpty(stack)) {
                MinecraftMappingCompat.itemStackSetCount(stack, 1);
                normalized.add(stack);
            }
        }
        return normalized.isEmpty() ? null : new DerivedDrop(normalized, minimum, maximum, unique(notes));
    }

    private static List<ItemStack> metadataVariants(List<ItemStack> original, int minimum, int maximum) {
        int min = Math.max(0, Math.min(minimum, maximum));
        int max = Math.max(min, Math.max(minimum, maximum));
        List<Integer> values = new ArrayList<>();
        if (max - min <= 31) {
            for (int value = min; value <= max; value++) {
                values.add(value);
            }
        } else {
            values.add(min);
            values.add(max);
        }
        List<ItemStack> variants = new ArrayList<>();
        for (ItemStack stack : original) {
            Item item = MinecraftMappingCompat.itemStackItem(stack);
            if (item == null) {
                continue;
            }
            for (Integer value : values) {
                ItemStack variant = new ItemStack(item, 1, value);
                NBTTagCompound tag = MinecraftMappingCompat.itemStackTagCompound(stack);
                if (tag != null) {
                    MinecraftMappingCompat.itemStackSetTagCompound(variant,
                            MinecraftMappingCompat.nbtCopy(tag));
                }
                variants.add(variant);
            }
        }
        return variants;
    }

    private static List<String> conditionNotes(Collection<?> conditions) {
        List<String> notes = new ArrayList<>();
        for (Object condition : conditions) {
            if (condition == null) {
                continue;
            }
            String className = condition.getClass().getName();
            if ("net.minecraft.world.storage.loot.conditions.KilledByPlayer".equals(className)) {
                continue;
            }
            if ("net.minecraft.world.storage.loot.conditions.RandomChance".equals(className)) {
                Object value = MinecraftMappingCompat.fieldValue(condition, "randomChance.chance",
                        "field_186630_a", "chance");
                if (value instanceof Number) {
                    notes.add(String.format(Locale.ROOT, "Table condition: %.2f%% base chance",
                            ((Number) value).doubleValue() * 100.0D));
                    continue;
                }
            }
            if ("net.minecraft.world.storage.loot.conditions.RandomChanceWithLooting".equals(className)) {
                Object base = MinecraftMappingCompat.fieldValue(condition, "randomLootingChance.base",
                        "field_186627_a", "chance");
                Object bonus = MinecraftMappingCompat.fieldValue(condition, "randomLootingChance.bonus",
                        "field_186628_b", "lootingMultiplier");
                if (base instanceof Number && bonus instanceof Number) {
                    notes.add(String.format(Locale.ROOT,
                            "Table condition: %.2f%% + %.2f%% per Looting level",
                            ((Number) base).doubleValue() * 100.0D,
                            ((Number) bonus).doubleValue() * 100.0D));
                    continue;
                }
            }
            if ("net.minecraft.world.storage.loot.conditions.EntityHasProperty".equals(className)) {
                notes.add("Conditional on mob or killer state");
            } else {
                notes.add("Conditional: " + simpleName(className));
            }
        }
        return unique(notes);
    }

    private static List<String> unique(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static float rangeMinimum(Object range) {
        Object value = MinecraftMappingCompat.fieldValue(range, "randomValueRange.minimum",
                "field_186514_a", "min");
        return value instanceof Number ? ((Number) value).floatValue() : 1.0F;
    }

    private static float rangeMaximum(Object range) {
        Object value = MinecraftMappingCompat.fieldValue(range, "randomValueRange.maximum",
                "field_186515_b", "max");
        return value instanceof Number ? ((Number) value).floatValue() : rangeMinimum(range);
    }

    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    private static List<?> listField(Object owner, String purpose, String... names) {
        Object value = MinecraftMappingCompat.fieldValue(owner, purpose, names);
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    private static List<?> arrayField(Object owner, String purpose, String... names) {
        Object value = MinecraftMappingCompat.fieldValue(owner, purpose, names);
        if (!(value instanceof Object[])) {
            return Collections.emptyList();
        }
        Object[] array = (Object[]) value;
        List<Object> result = new ArrayList<>(array.length);
        Collections.addAll(result, array);
        return result;
    }

    private static Result materialize(
            Map<String, EntityRecord> entities, Map<String, TableRecord> tables) {
        Map<String, List<WootMobDropsRecipeWrapper.DropInfo>> result = new HashMap<>();
        Map<String, Set<String>> unsupportedFunctionsByMob = new LinkedHashMap<>();
        for (EntityRecord entity : entities.values()) {
            TableRecord table = tables.get(entity.tableId);
            if (table == null) {
                continue;
            }
            if (!table.unsupportedFunctions.isEmpty()) {
                unsupportedFunctionsByMob.put(entity.mobName, table.unsupportedFunctions);
            }
            if (table.drops.isEmpty()) {
                continue;
            }
            List<WootMobDropsRecipeWrapper.DropInfo> drops = new ArrayList<>(table.drops.size());
            for (DerivedDrop derived : table.drops) {
                WootMobDropsRecipeWrapper.DropInfo drop =
                        new WootMobDropsRecipeWrapper.DropInfo(derived.stacks.get(0));
                drop.setStacks(derived.stacks, derived.stacks.size() > 1
                        ? "Loot-table metadata/NBT alternatives" : "");
                drop.setTableDerivedData(derived.minimum, derived.maximum, derived.notes);
                drops.add(drop);
            }
            result.put(entity.mobName, Collections.unmodifiableList(drops));
        }
        return new Result(result, unsupportedFunctionsByMob);
    }

    private static String dependencySignature(Collection<String> dependencies, ScriptIndex scripts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, "schema=" + CACHE_SCHEMA);
            List<String> sorted = new ArrayList<>(dependencies);
            Collections.sort(sorted);
            for (String tableId : sorted) {
                update(digest, "table=" + tableId);
                appendResources(digest, tableId);
                scripts.appendMatches(digest, tableId);
            }
            appendOptionalClassSource(digest, "leviathan143.loottweaker.common.LootTweaker");
            appendOptionalClassSource(digest, "net.darkhax.lttweaker.LTTMod");
            appendOptionalClassSource(digest, JER_LOOT_HELPER);
            return hex(digest.digest());
        } catch (Throwable throwable) {
            return "unavailable:" + throwable.getClass().getName();
        }
    }

    private static void appendResources(MessageDigest digest, String tableId) throws IOException {
        ResourceLocation id = new ResourceLocation(tableId);
        String path = "assets/" + MinecraftMappingCompat.resourceLocationNamespace(id)
                + "/loot_tables/" + MinecraftMappingCompat.resourceLocationPath(id) + ".json";
        Enumeration<URL> resources = WootLootTableExtractor.class.getClassLoader().getResources(path);
        List<URL> urls = new ArrayList<>();
        while (resources.hasMoreElements()) {
            urls.add(resources.nextElement());
        }
        urls.sort(Comparator.comparing(URL::toString));
        if (urls.isEmpty()) {
            update(digest, "missing=" + path);
            return;
        }
        byte[] buffer = new byte[8192];
        for (URL url : urls) {
            update(digest, url.toString());
            try (InputStream input = new BufferedInputStream(url.openStream())) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
    }

    private static void appendOptionalClassSource(MessageDigest digest, String className) {
        try {
            Class<?> type = Class.forName(className, false, WootLootTableExtractor.class.getClassLoader());
            update(digest, className + '=' + classSourceSignature(type));
        } catch (Throwable ignored) {
            update(digest, className + "=absent");
        }
    }

    private static String classSourceSignature(Class<?> type) {
        try {
            ProtectionDomain domain = type.getProtectionDomain();
            URL location = domain == null || domain.getCodeSource() == null
                    ? null : domain.getCodeSource().getLocation();
            if (location == null) {
                return type.getName();
            }
            File file = new File(location.toURI());
            return location + ":" + file.length() + ':' + file.lastModified();
        } catch (Throwable throwable) {
            return type.getName() + ":unavailable";
        }
    }

    private static Properties loadCache() {
        Properties properties = new Properties();
        File file = cacheFile();
        if (!file.isFile()) {
            return properties;
        }
        synchronized (CACHE_IO_LOCK) {
            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
                properties.load(input);
                if (!Integer.toString(CACHE_SCHEMA).equals(properties.getProperty("schema"))) {
                    properties.clear();
                }
            } catch (IOException exception) {
                WootJeiDiagnostics.error("Could not read loot-table cache", exception);
                properties.clear();
            }
        }
        return properties;
    }

    private static void saveCache(Map<String, EntityRecord> entities, Map<String, TableRecord> tables) {
        Properties properties = new Properties();
        properties.setProperty("schema", Integer.toString(CACHE_SCHEMA));
        for (EntityRecord entity : entities.values()) {
            String prefix = "e." + key(entity.mobName) + '.';
            properties.setProperty(prefix + "class", entity.className);
            properties.setProperty(prefix + "source", entity.sourceSignature);
            if (entity.tableId != null) {
                properties.setProperty(prefix + "table", entity.tableId);
            }
        }
        for (TableRecord table : tables.values()) {
            String prefix = "t." + key(table.tableId) + '.';
            properties.setProperty(prefix + "signature", table.signature);
            properties.setProperty(prefix + "dependencies", join(table.dependencies));
            properties.setProperty(prefix + "drops", encodeDrops(table.drops));
            properties.setProperty(prefix + "unsupportedFunctions", join(table.unsupportedFunctions));
        }

        File file = cacheFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            return;
        }
        File temporary = new File(parent, file.getName() + ".tmp");
        synchronized (CACHE_IO_LOCK) {
            try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(temporary))) {
                properties.store(output, "GPOM Woot loot-table extraction cache; generated automatically");
            } catch (IOException exception) {
                WootJeiDiagnostics.error("Could not write loot-table cache", exception);
                return;
            }
            try {
                try {
                    Files.move(temporary.toPath(), file.toPath(),
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException exception) {
                WootJeiDiagnostics.error("Could not install loot-table cache", exception);
            }
        }
    }

    private static EntityRecord readEntityRecord(Properties properties, String mobName) {
        String prefix = "e." + key(mobName) + '.';
        String className = properties.getProperty(prefix + "class");
        String source = properties.getProperty(prefix + "source");
        String table = properties.getProperty(prefix + "table");
        if (className == null || source == null || table == null) {
            return null;
        }
        return new EntityRecord(mobName, className, source, table);
    }

    private static TableRecord readTableRecord(Properties properties, String tableId) {
        String prefix = "t." + key(tableId) + '.';
        String signature = properties.getProperty(prefix + "signature");
        String dependencies = properties.getProperty(prefix + "dependencies");
        String drops = properties.getProperty(prefix + "drops");
        String unsupportedFunctions = properties.getProperty(prefix + "unsupportedFunctions");
        if (signature == null || dependencies == null || drops == null || unsupportedFunctions == null) {
            return null;
        }
        try {
            return new TableRecord(tableId, signature, split(dependencies), decodeDrops(drops),
                    split(unsupportedFunctions));
        } catch (Throwable throwable) {
            WootJeiDiagnostics.error("Ignoring corrupt cached loot table " + tableId, throwable);
            return null;
        }
    }

    private static String encodeDrops(List<DerivedDrop> drops) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(drops.size());
                for (DerivedDrop drop : drops) {
                    output.writeInt(drop.stacks.size());
                    for (ItemStack stack : drop.stacks) {
                        Item item = MinecraftMappingCompat.itemStackItem(stack);
                        ResourceLocation itemId = MinecraftMappingCompat.itemRegistryName(item);
                        output.writeUTF(itemId == null ? "" : itemId.toString());
                        output.writeInt(MinecraftMappingCompat.itemStackMetadata(stack));
                        NBTTagCompound tag = MinecraftMappingCompat.itemStackTagCompound(stack);
                        output.writeUTF(tag == null ? "" : tag.toString());
                    }
                    for (int level = 0; level < 4; level++) {
                        output.writeInt(drop.minimum[level]);
                        output.writeInt(drop.maximum[level]);
                    }
                    output.writeInt(drop.notes.size());
                    for (String note : drop.notes) {
                        output.writeUTF(note);
                    }
                }
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<DerivedDrop> decodeDrops(String encoded) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(encoded);
        List<DerivedDrop> drops = new ArrayList<>();
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int count = bounded(input.readInt(), 0, 10000);
            for (int index = 0; index < count; index++) {
                int stackCount = bounded(input.readInt(), 1, 64);
                List<ItemStack> stacks = new ArrayList<>();
                for (int stackIndex = 0; stackIndex < stackCount; stackIndex++) {
                    ResourceLocation itemId = new ResourceLocation(input.readUTF());
                    int metadata = input.readInt();
                    String nbt = input.readUTF();
                    Item item = ForgeRegistries.ITEMS.getValue(itemId);
                    if (item != null) {
                        ItemStack stack = new ItemStack(item, 1, metadata);
                        if (!nbt.isEmpty()) {
                            NBTTagCompound tag = MinecraftMappingCompat.jsonToNbt(nbt);
                            if (tag != null) {
                                MinecraftMappingCompat.itemStackSetTagCompound(stack, tag);
                            }
                        }
                        stacks.add(stack);
                    }
                }
                int[] minimum = new int[4];
                int[] maximum = new int[4];
                for (int level = 0; level < 4; level++) {
                    minimum[level] = input.readInt();
                    maximum[level] = input.readInt();
                }
                int noteCount = bounded(input.readInt(), 0, 128);
                List<String> notes = new ArrayList<>();
                for (int note = 0; note < noteCount; note++) {
                    notes.add(input.readUTF());
                }
                if (!stacks.isEmpty()) {
                    drops.add(new DerivedDrop(stacks, minimum, maximum, notes));
                }
            }
        }
        return drops;
    }

    private static int bounded(int value, int minimum, int maximum) throws IOException {
        if (value < minimum || value > maximum) {
            throw new IOException("Cached value outside bounds: " + value);
        }
        return value;
    }

    private static File cacheFile() {
        File config = Loader.instance().getConfigDir();
        return new File(new File(config, CACHE_DIRECTORY), CACHE_FILE);
    }

    private static String key(String value) {
        return KEY_ENCODER.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unused")
    private static String valueFromKey(String value) {
        return new String(KEY_DECODER.decode(value), StandardCharsets.UTF_8);
    }

    private static String join(Collection<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(key(value));
        }
        return result.toString();
    }

    private static Set<String> split(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value.isEmpty()) {
            return result;
        }
        for (String part : value.split(",")) {
            result.add(new String(KEY_DECODER.decode(part), StandardCharsets.UTF_8));
        }
        return result;
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private static String simpleName(String className) {
        int separator = className.lastIndexOf('.');
        return separator < 0 ? className : className.substring(separator + 1);
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static final class Context {
        final World fakeWorld;
        final LootTableManager manager;
        final Properties cache;
        final ScriptIndex scripts;
        final int workers;
        int cacheHits;
        int rebuiltTables;
        int unsupportedEntries;
        int unsupportedFunctions;

        private Context(World fakeWorld,
                        LootTableManager manager,
                        Properties cache,
                        ScriptIndex scripts,
                        int workers) {
            this.fakeWorld = fakeWorld;
            this.manager = manager;
            this.cache = cache;
            this.scripts = scripts;
            this.workers = workers;
        }

        static Context create() throws ReflectiveOperationException {
            ClassLoader loader = WootLootTableExtractor.class.getClassLoader();
            World fakeWorld = createFakeWorld();
            if (fakeWorld == null) {
                throw new IllegalStateException("JER did not provide a fake world");
            }
            Class<?> helper = Class.forName(JER_LOOT_HELPER, true, loader);
            Object manager = helper.getMethod("getManager", World.class).invoke(null, fakeWorld);
            if (!(manager instanceof LootTableManager)) {
                throw new IllegalStateException("JER did not provide a LootTableManager");
            }
            int configured = GpomEarlyConfig.heiWootLootTableExtractionWorkers();
            int automatic = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
            int workers = configured <= 0 ? automatic : Math.max(1, Math.min(16, configured));
            File minecraft = Loader.instance().getConfigDir().getParentFile();
            return new Context(fakeWorld, (LootTableManager) manager, loadCache(),
                    new ScriptIndex(new File(minecraft, "scripts")), workers);
        }
    }

    private static final class EntityRecord {
        final String mobName;
        final String className;
        final String sourceSignature;
        final String tableId;

        private EntityRecord(String mobName, String className, String sourceSignature, String tableId) {
            this.mobName = mobName;
            this.className = className;
            this.sourceSignature = sourceSignature;
            this.tableId = tableId;
        }
    }

    private static final class TableRecord {
        final String tableId;
        final String signature;
        final Set<String> dependencies;
        final List<DerivedDrop> drops;
        final Set<String> unsupportedFunctions;

        private TableRecord(String tableId,
                            String signature,
                            Collection<String> dependencies,
                            List<DerivedDrop> drops,
                            Collection<String> unsupportedFunctions) {
            this.tableId = tableId;
            this.signature = signature;
            this.dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
            this.drops = Collections.unmodifiableList(new ArrayList<>(drops));
            this.unsupportedFunctions = Collections.unmodifiableSet(
                    new LinkedHashSet<>(unsupportedFunctions));
        }
    }

    private static final class DerivedDrop {
        final List<ItemStack> stacks;
        final int[] minimum;
        final int[] maximum;
        final List<String> notes;

        private DerivedDrop(List<ItemStack> stacks, int[] minimum, int[] maximum, List<String> notes) {
            List<ItemStack> copies = new ArrayList<>();
            for (ItemStack stack : stacks) {
                ItemStack copy = MinecraftMappingCompat.itemStackCopy(stack);
                if (!MinecraftMappingCompat.itemStackIsEmpty(copy)) {
                    copies.add(copy);
                }
            }
            this.stacks = Collections.unmodifiableList(copies);
            this.minimum = minimum.clone();
            this.maximum = maximum.clone();
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
        }
    }

    private static final class Analysis {
        final List<DerivedDrop> drops = new ArrayList<>();
        final Set<String> unsupportedFunctionNames = new LinkedHashSet<>();
        int unsupportedEntries;
        int unsupportedFunctions;

        void mergeEquivalentDrops() {
            Map<String, DerivedDrop> merged = new LinkedHashMap<>();
            for (DerivedDrop drop : drops) {
                if (drop.stacks.isEmpty()) {
                    continue;
                }
                String key = stackKey(drop.stacks.get(0));
                DerivedDrop existing = merged.get(key);
                if (existing == null) {
                    merged.put(key, drop);
                    continue;
                }
                List<ItemStack> alternatives = new ArrayList<>(existing.stacks);
                for (ItemStack candidate : drop.stacks) {
                    boolean found = false;
                    for (ItemStack current : alternatives) {
                        if (MinecraftMappingCompat.itemStacksSameItemAndTags(current, candidate)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        alternatives.add(candidate);
                    }
                }
                int[] minimum = existing.minimum.clone();
                int[] maximum = existing.maximum.clone();
                for (int level = 0; level < 4; level++) {
                    minimum[level] = Math.min(minimum[level], drop.minimum[level]);
                    maximum[level] = Math.max(maximum[level], drop.maximum[level]);
                }
                List<String> notes = new ArrayList<>(existing.notes);
                notes.addAll(drop.notes);
                merged.put(key, new DerivedDrop(alternatives, minimum, maximum, unique(notes)));
            }
            drops.clear();
            drops.addAll(merged.values());
        }

        private static String stackKey(ItemStack stack) {
            Item item = MinecraftMappingCompat.itemStackItem(stack);
            ResourceLocation id = MinecraftMappingCompat.itemRegistryName(item);
            NBTTagCompound tag = MinecraftMappingCompat.itemStackTagCompound(stack);
            return String.valueOf(id) + ':' + MinecraftMappingCompat.itemStackMetadata(stack)
                    + ':' + String.valueOf(tag);
        }
    }

    static final class Result {
        final Map<String, List<WootMobDropsRecipeWrapper.DropInfo>> dropsByMob;
        final Map<String, Set<String>> unsupportedFunctionsByMob;

        private Result(Map<String, List<WootMobDropsRecipeWrapper.DropInfo>> dropsByMob,
                       Map<String, Set<String>> unsupportedFunctionsByMob) {
            this.dropsByMob = Collections.unmodifiableMap(new LinkedHashMap<>(dropsByMob));
            Map<String, Set<String>> copied = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : unsupportedFunctionsByMob.entrySet()) {
                copied.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
            }
            this.unsupportedFunctionsByMob = Collections.unmodifiableMap(copied);
        }

        static Result empty() {
            return new Result(Collections.<String, List<WootMobDropsRecipeWrapper.DropInfo>>emptyMap(),
                    Collections.<String, Set<String>>emptyMap());
        }
    }

    private static final class ScriptIndex {
        final List<ScriptFile> scripts;

        private ScriptIndex(File root) {
            List<ScriptFile> found = new ArrayList<>();
            if (root.isDirectory()) {
                try (Stream<Path> paths = Files.walk(root.toPath())) {
                    paths
                            .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".zs"))
                            .forEach(path -> {
                                try {
                                    byte[] bytes = Files.readAllBytes(path);
                                    found.add(new ScriptFile(path.toFile(), bytes,
                                            new String(bytes, StandardCharsets.UTF_8)));
                                } catch (IOException ignored) {
                                }
                            });
                } catch (IOException ignored) {
                }
            }
            found.sort(Comparator.comparing(script -> script.file.getAbsolutePath()));
            scripts = Collections.unmodifiableList(found);
        }

        void appendMatches(MessageDigest digest, String tableId) {
            for (ScriptFile script : scripts) {
                if (script.text.contains(tableId)) {
                    update(digest, script.file.getAbsolutePath());
                    digest.update(script.bytes);
                }
            }
        }
    }

    private static final class ScriptFile {
        final File file;
        final byte[] bytes;
        final String text;

        private ScriptFile(File file, byte[] bytes, String text) {
            this.file = file;
            this.bytes = bytes;
            this.text = text;
        }
    }

    private static final class ExtractionThreadFactory implements ThreadFactory {
        private final AtomicInteger ids = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable,
                    "GPOM Woot Loot Analyzer " + ids.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
