package com.l.gpom.compat.sfm;

import com.google.common.collect.Multimap;
import com.l.gpom.GPOM;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SfmLightweightSearchCache {
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private static final AtomicBoolean BUILDING = new AtomicBoolean();
    private static volatile Method stackIsEmptyMethod;
    private static volatile Method stackGetDisplayNameMethod;
    private static volatile Method stackGetItemMethod;
    private static volatile Method resourceDomainMethod;
    private static volatile Method resourcePathMethod;

    private SfmLightweightSearchCache() {
    }

    public static void deferBuild() {
        if (!GpomEarlyConfig.sfmLightweightSearchCacheEnabled()) {
            return;
        }
        if (!SCHEDULED.compareAndSet(false, true)) {
            return;
        }

        Runnable task = () -> {
            try {
                ClassLoader loader = Launch.classLoader != null
                        ? Launch.classLoader
                        : SfmLightweightSearchCache.class.getClassLoader();
                Class<?> searchUtil = Class.forName("vswe.superfactory.util.SearchUtil", true, loader);
                Method buildCache = searchUtil.getDeclaredMethod("buildCache");
                buildCache.setAccessible(true);
                buildCache.invoke(null);
                GPOM.LOGGER.info("[GPOM SFM] Deferred SFM item search cache build onto client thread");
            } catch (Throwable throwable) {
                SCHEDULED.set(false);
                GPOM.LOGGER.warn("[GPOM SFM] Failed to defer SFM item search cache build; leaving SFM cache empty", throwable);
            }
        };

        Minecraft minecraft = ClientAccess.minecraft();
        if (!ClientAccess.schedule(minecraft, task)) {
            task.run();
        }
    }

    public static void buildCache(Multimap<ItemStack, String> cache) {
        if (!GpomEarlyConfig.sfmLightweightSearchCacheEnabled()) {
            return;
        }
        if (cache == null || !BUILDING.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(() -> buildCacheWorker(cache), "GPOM-SFM-SearchCache");
        worker.setDaemon(true);
        worker.setContextClassLoader(null);
        worker.start();
    }

    private static void buildCacheWorker(Multimap<ItemStack, String> cache) {
        long startedAt = System.currentTimeMillis();
        String source = "creative";
        try {
            CandidateStacks candidates = candidateStacks();
            List<ItemStack> stacks = candidates.stacks;
            source = candidates.source;
            cache.clear();

            int workers = workerCount(stacks.size());
            AtomicInteger indexedCount = new AtomicInteger();
            if (workers <= 1 || stacks.size() < 512) {
                indexRange(cache, stacks, 0, stacks.size(), indexedCount);
            } else {
                ExecutorService executor = Executors.newFixedThreadPool(workers, runnable -> {
                    Thread thread = new Thread(runnable, "GPOM-SFM-SearchCache-Worker");
                    thread.setDaemon(true);
                    thread.setContextClassLoader(null);
                    return thread;
                });
                int chunkSize = Math.max(256, (stacks.size() + workers - 1) / workers);
                for (int start = 0; start < stacks.size(); start += chunkSize) {
                    int rangeStart = start;
                    int rangeEnd = Math.min(stacks.size(), start + chunkSize);
                    executor.execute(() -> indexRange(cache, stacks, rangeStart, rangeEnd, indexedCount));
                }
                executor.shutdown();
                while (!executor.awaitTermination(1L, TimeUnit.MINUTES)) {
                    GPOM.LOGGER.info("[GPOM SFM] Waiting for lightweight item search cache workers indexed={}/{}", indexedCount.get(), stacks.size());
                }
            }

            GPOM.LOGGER.info(
                    "[GPOM SFM] Built lightweight item search cache source={} indexed={} candidates={} workers={} elapsed={}ms",
                    source,
                    indexedCount.get(),
                    stacks.size(),
                    workers,
                    System.currentTimeMillis() - startedAt
            );
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM SFM] Failed to build lightweight item search cache source={}; leaving SFM cache empty", source, throwable);
        } finally {
            BUILDING.set(false);
        }
    }

    private static CandidateStacks candidateStacks() {
        if (GpomEarlyConfig.sfmLightweightSearchCacheUseHeiIngredientsEnabled()) {
            List<ItemStack> heiStacks = heiIngredientStacks();
            if (!heiStacks.isEmpty()) {
                return new CandidateStacks("hei", heiStacks);
            }
        }
        NonNullList<ItemStack> stacks = NonNullList.create();
        for (Item item : Item.REGISTRY) {
            if (item == null || item.getCreativeTab() == null) {
                continue;
            }
            try {
                item.getSubItems(CreativeTabs.SEARCH, stacks);
            } catch (Throwable ignored) {
            }
        }
        return new CandidateStacks("creative", new ArrayList<>(stacks));
    }

    private static List<ItemStack> heiIngredientStacks() {
        List<ItemStack> stacks = new ArrayList<>();
        try {
            Class<?> pluginClass = Class.forName("mezz.jei.plugins.jei.JEIInternalPlugin");
            Field registryField = pluginClass.getField("ingredientRegistry");
            Object registry = registryField.get(null);
            if (registry == null) {
                return stacks;
            }
            Method getAllIngredients = registry.getClass().getMethod("getAllIngredients", Class.class);
            Object values = getAllIngredients.invoke(registry, ItemStack.class);
            if (!(values instanceof Iterable)) {
                return stacks;
            }
            for (Object value : (Iterable<?>) values) {
                if (value instanceof ItemStack) {
                    stacks.add((ItemStack) value);
                }
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.info("[GPOM SFM] HEI ingredient list unavailable for SFM search cache; using creative fallback ({})", throwable.toString());
        }
        return stacks;
    }

    private static int workerCount(int stackCount) {
        if (stackCount < 512) {
            return 1;
        }
        int configured = GpomEarlyConfig.sfmLightweightSearchCacheWorkers();
        if (configured > 0) {
            return Math.max(1, configured);
        }
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static void indexRange(Multimap<ItemStack, String> cache, List<ItemStack> stacks, int start, int end, AtomicInteger indexedCount) {
        List<CacheEntry> entries = new ArrayList<>((end - start) * 3);
        int indexed = 0;
        for (int i = start; i < end; i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stackIsEmpty(stack)) {
                continue;
            }
            try {
                put(entries, stack, stackDisplayName(stack));
                Item item = stackItem(stack);
                ResourceLocation registryName = item == null ? null : item.getRegistryName();
                if (registryName != null) {
                    put(entries, stack, registryName.toString());
                    put(entries, stack, resourceDomain(registryName));
                    put(entries, stack, resourcePath(registryName));
                }
                int[] oreIds = OreDictionary.getOreIDs(stack);
                for (int oreId : oreIds) {
                    put(entries, stack, OreDictionary.getOreName(oreId));
                }
                indexed++;
            } catch (Throwable ignored) {
            }
        }
        if (!entries.isEmpty()) {
            synchronized (cache) {
                for (CacheEntry entry : entries) {
                    cache.put(entry.stack, entry.value);
                }
            }
        }
        if (indexed > 0) {
            indexedCount.addAndGet(indexed);
        }
    }

    private static void put(List<CacheEntry> entries, ItemStack stack, String value) {
        if (value != null) {
            String clean = value.trim();
            if (!clean.isEmpty()) {
                entries.add(new CacheEntry(stack, clean));
            }
        }
    }

    private static boolean stackIsEmpty(ItemStack stack) {
        try {
            Method method = stackIsEmptyMethod;
            if (method == null) {
                method = findMethod(ItemStack.class, "isEmpty", "func_190926_b");
                stackIsEmptyMethod = method;
            }
            Object value = method == null ? null : method.invoke(stack);
            return value instanceof Boolean ? (Boolean) value : false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String stackDisplayName(ItemStack stack) {
        try {
            Method method = stackGetDisplayNameMethod;
            if (method == null) {
                method = findMethod(ItemStack.class, "getDisplayName", "func_82833_r");
                stackGetDisplayNameMethod = method;
            }
            Object value = method == null ? null : method.invoke(stack);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Item stackItem(ItemStack stack) {
        try {
            Method method = stackGetItemMethod;
            if (method == null) {
                method = findMethod(ItemStack.class, "getItem", "func_77973_b");
                stackGetItemMethod = method;
            }
            Object value = method == null ? null : method.invoke(stack);
            return value instanceof Item ? (Item) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resourceDomain(ResourceLocation location) {
        return resourcePart(location, true);
    }

    private static String resourcePath(ResourceLocation location) {
        return resourcePart(location, false);
    }

    private static String resourcePart(ResourceLocation location, boolean domain) {
        try {
            Method method = domain ? resourceDomainMethod : resourcePathMethod;
            if (method == null) {
                method = domain
                        ? findMethod(ResourceLocation.class, "getNamespace", "getResourceDomain", "func_110624_b")
                        : findMethod(ResourceLocation.class, "getPath", "getResourcePath", "func_110623_a");
                if (domain) {
                    resourceDomainMethod = method;
                } else {
                    resourcePathMethod = method;
                }
            }
            Object value = method == null ? null : method.invoke(location);
            return value == null ? null : value.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
            try {
                Method method = owner.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static final class CacheEntry {
        private final ItemStack stack;
        private final String value;

        private CacheEntry(ItemStack stack, String value) {
            this.stack = stack;
            this.value = value;
        }
    }

    private static final class CandidateStacks {
        private final String source;
        private final List<ItemStack> stacks;

        private CandidateStacks(String source, List<ItemStack> stacks) {
            this.source = source;
            this.stacks = Objects.requireNonNull(stacks, "stacks");
        }
    }
}
