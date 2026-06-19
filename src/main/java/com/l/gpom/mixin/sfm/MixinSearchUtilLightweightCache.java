package com.l.gpom.mixin.sfm;

import com.google.common.collect.Multimap;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

@Mixin(targets = "vswe.superfactory.util.SearchUtil", remap = false)
public abstract class MixinSearchUtilLightweightCache {
    @Shadow
    @Final
    private static Multimap<ItemStack, String> cache;

    @Unique
    private static final AtomicBoolean gpom$building = new AtomicBoolean();

    @Inject(method = "buildCache()V", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gpom$buildLightweightCache(CallbackInfo ci) {
        if (!GpomEarlyConfig.sfmLightweightSearchCacheEnabled()) {
            return;
        }
        ci.cancel();
        if (!gpom$building.compareAndSet(false, true)) {
            return;
        }
        Thread worker = new Thread(MixinSearchUtilLightweightCache::gpom$buildCache, "GPOM-SFM-SearchCache");
        worker.setDaemon(true);
        worker.setContextClassLoader(null);
        worker.start();
    }

    @Unique
    private static void gpom$buildCache() {
        long startedAt = System.currentTimeMillis();
        String source = "creative";
        try {
            CandidateStacks candidates = gpom$candidateStacks();
            List<ItemStack> stacks = candidates.stacks;
            source = candidates.source;
            cache.clear();

            int workers = gpom$workerCount(stacks.size());
            AtomicInteger indexedCount = new AtomicInteger();
            if (workers <= 1 || stacks.size() < 512) {
                gpom$indexRange(stacks, 0, stacks.size(), indexedCount);
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
                    executor.execute(() -> gpom$indexRange(stacks, rangeStart, rangeEnd, indexedCount));
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
            gpom$building.set(false);
        }
    }

    @Unique
    private static CandidateStacks gpom$candidateStacks() {
        if (GpomEarlyConfig.sfmLightweightSearchCacheUseHeiIngredientsEnabled()) {
            List<ItemStack> heiStacks = gpom$heiIngredientStacks();
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

    @Unique
    private static List<ItemStack> gpom$heiIngredientStacks() {
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

    @Unique
    private static int gpom$workerCount(int stackCount) {
        if (stackCount < 512) {
            return 1;
        }
        int configured = GpomEarlyConfig.sfmLightweightSearchCacheWorkers();
        if (configured > 0) {
            return Math.max(1, configured);
        }
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    }

    @Unique
    private static void gpom$indexRange(List<ItemStack> stacks, int start, int end, AtomicInteger indexedCount) {
        for (int i = start; i < end; i++) {
            ItemStack stack = stacks.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            try {
                gpom$put(stack, stack.getDisplayName());
                ResourceLocation registryName = stack.getItem().getRegistryName();
                if (registryName != null) {
                    gpom$put(stack, registryName.toString());
                    gpom$put(stack, registryName.getNamespace());
                    gpom$put(stack, registryName.getPath());
                }
                int[] oreIds = OreDictionary.getOreIDs(stack);
                for (int oreId : oreIds) {
                    gpom$put(stack, OreDictionary.getOreName(oreId));
                }
                indexedCount.incrementAndGet();
            } catch (Throwable ignored) {
            }
        }
    }

    @Unique
    private static void gpom$put(ItemStack stack, String value) {
        if (value != null) {
            String clean = value.trim();
            if (!clean.isEmpty()) {
                cache.put(stack, clean);
            }
        }
    }

    @Unique
    private static final class CandidateStacks {
        private final String source;
        private final List<ItemStack> stacks;

        private CandidateStacks(String source, List<ItemStack> stacks) {
            this.source = source;
            this.stacks = Objects.requireNonNull(stacks, "stacks");
        }
    }
}
