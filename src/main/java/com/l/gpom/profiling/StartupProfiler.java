package com.l.gpom.profiling;

import com.l.gpom.GPOM;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StartupProfiler {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.startupProfiler", "true"));
    private static final long DETAIL_THRESHOLD_NANOS = millisProperty("gpom.startupProfiler.detailThresholdMs", 100L) * 1_000_000L;
    private static final long PROBE_THRESHOLD_NANOS = millisProperty("gpom.startupProfiler.probeThresholdMs", 25L) * 1_000_000L;
    private static final boolean STACK_SAMPLER_ENABLED = Boolean.parseBoolean(System.getProperty("gpom.startupProfiler.stackSampler", "true"));
    private static final long STACK_SAMPLER_THRESHOLD_MILLIS = millisProperty("gpom.startupProfiler.stackSamplerThresholdMs", 500L);
    private static final long STACK_SAMPLER_INTERVAL_MILLIS = millisProperty("gpom.startupProfiler.stackSamplerIntervalMs", 1_000L);
    private static final int STACK_SAMPLER_MAX_FRAMES = intProperty("gpom.startupProfiler.stackSamplerFrames", 32);
    private static final Set<String> STACK_SAMPLER_MODS = setProperty("gpom.startupProfiler.stackSamplerMods", "aoa3,abyssalcraft,agricraft,appliedenergistics2,astralsorcery,botania,brandonscore,citnbt,codechickenlib,concheckrmd,contenttweaker,crafttweaker,cyclicmagic,draconicevolution,ebwizardry,enderio,extrautils2,forestry,forgelin,ftbutilities,hammercore,integrateddynamics,itemblacklist,opencomputers,railcraft,redcore,smoothfont,techreborn,tconstruct,thaumadditions,thaumcraft,thaumicaugmentation,thaumictinkerer,thaumicwonders,thebetweenlands,thermalexpansion,thermalfoundation,xreliquary");
    private static final boolean PROGRESS_BARS_ENABLED = Boolean.parseBoolean(System.getProperty("gpom.startupProfiler.progressBars", "false"));
    private static final boolean RESOURCE_LOAD_ORDER_ENABLED = Boolean.parseBoolean(System.getProperty("gpom.resourceLoadOrder", "true"));
    private static final int TOP_COUNT = intProperty("gpom.startupProfiler.topCount", 40);
    private static final int PHASE_DIGEST_COUNT = intProperty("gpom.startupProfiler.phaseDigestCount", 3);
    private static final int RESOURCE_LOAD_ORDER_TOP_COUNT = intProperty("gpom.resourceLoadOrder.topCount", 12);
    private static final long BOOT_STARTED_AT = longProperty("gpom.bootStartNanos", System.nanoTime());

    private static final Object LOCK = new Object();
    private static final Map<String, PhaseData> PHASES = new LinkedHashMap<>();
    private static final ThreadLocal<Map<String, Deque<Long>>> NAMED_PROBES = ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Map<String, Deque<ProgressManager.ProgressBar>>> NAMED_PROGRESS_BARS = ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<ResourceReloadData> ACTIVE_RESOURCE_RELOAD = new ThreadLocal<>();
    private static final ThreadLocal<Deque<Long>> MOD_HEAP_STARTS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> ACTIVE_PROGRESS_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static String activePhase;
    private static long activePhaseStartedAt;
    private static int resourceReloadSequence;

    private StartupProfiler() {
    }

    public static void beginPhase(String phaseName) {
        if (!ENABLED || phaseName == null) {
            return;
        }

        synchronized (LOCK) {
            if (phaseName.equals(activePhase)) {
                return;
            }
            if (activePhase != null) {
                finishPhaseLocked(activePhase, System.nanoTime());
            }
            activePhase = phaseName;
            activePhaseStartedAt = System.nanoTime();
            PHASES.computeIfAbsent(phaseName, PhaseData::new);
            GPOM.LOGGER.info("[StartupProfiler] Starting FML phase {}", phaseName);
        }
    }

    public static void endPhase(String phaseName) {
        if (!ENABLED || phaseName == null) {
            return;
        }

        synchronized (LOCK) {
            finishPhaseLocked(phaseName, System.nanoTime());
            if (phaseName.equals(activePhase)) {
                activePhase = null;
                activePhaseStartedAt = 0L;
            }
        }
    }

    public static long beginMod(ModContainer container, FMLEvent event) {
        if (!ENABLED) {
            return 0L;
        }
        MOD_HEAP_STARTS.get().push(usedHeapBytes());
        return System.nanoTime();
    }

    public static void endMod(ModContainer container, FMLEvent event, long startedAt) {
        if (!ENABLED || startedAt == 0L || container == null || event == null) {
            return;
        }

        long elapsed = System.nanoTime() - startedAt;
        long heapAfter = usedHeapBytes();
        long heapStartedAt = popHeapStart(heapAfter);
        long heapDelta = heapAfter - heapStartedAt;
        String modId = safeModId(container);
        String modName = safeModName(container);

        synchronized (LOCK) {
            String phaseName = activePhase != null ? activePhase : eventName(event);
            PHASES.computeIfAbsent(phaseName, PhaseData::new).add(new ModTiming(modId, modName, elapsed, heapDelta, heapAfter));

            if (elapsed >= DETAIL_THRESHOLD_NANOS) {
                GPOM.LOGGER.info(
                        "[StartupProfiler] {} took {} ms for {} ({}) heapDelta={} MiB heapAfter={} MiB",
                        phaseName,
                        formatMillis(elapsed),
                        modId,
                        modName,
                        formatMib(heapDelta),
                        formatMib(heapAfter)
                );
            }
        }
    }

    public static void recordModError(ModContainer container, FMLEvent event, long startedAt, Throwable throwable) {
        endMod(container, event, startedAt);
        if (ENABLED && container != null && event != null) {
            GPOM.LOGGER.warn(
                    "[StartupProfiler] {} errored while handling {} after timing sample was recorded",
                    safeModId(container),
                    eventName(event),
                    throwable
            );
        }
    }

    public static StackSampler beginModStackSampler(ModContainer container, FMLEvent event, long startedAt) {
        if (!ENABLED || !STACK_SAMPLER_ENABLED || container == null || event == null || startedAt == 0L) {
            return null;
        }

        String modId = safeModId(container);
        if (!STACK_SAMPLER_MODS.contains(modId)) {
            return null;
        }

        StackSampler sampler = new StackSampler(Thread.currentThread(), modId, safeModName(container), eventName(event), startedAt);
        sampler.start();
        return sampler;
    }

    public static void endModStackSampler(StackSampler sampler) {
        if (sampler != null) {
            sampler.stop();
        }
    }


    public static void beginNamedProbe(String probeName) {
        if (!ENABLED || probeName == null) {
            return;
        }

        NAMED_PROBES.get()
                .computeIfAbsent(probeName, ignored -> new ArrayDeque<>())
                .push(System.nanoTime());
        pushProgressBar(probeName);
    }

    public static void endNamedProbe(String probeName) {
        if (!ENABLED || probeName == null) {
            return;
        }

        Map<String, Deque<Long>> probes = NAMED_PROBES.get();
        Deque<Long> starts = probes.get(probeName);
        if (starts == null || starts.isEmpty()) {
            return;
        }

        long startedAt = starts.pop();
        if (starts.isEmpty()) {
            probes.remove(probeName);
        }
        popProgressBar(probeName);
        endProbe(probeName, startedAt, namedProbeThreshold(probeName));
    }

    public static long beginProbe() {
        if (!ENABLED) {
            return 0L;
        }
        return System.nanoTime();
    }

    public static void markBoot(String label) {
        if (!ENABLED || label == null) {
            return;
        }
        GPOM.LOGGER.info("[StartupProfiler] [Boot] {} at {} ms since GPOM core init",
                label,
                formatMillis(System.nanoTime() - BOOT_STARTED_AT));
    }

    public static void endProbe(String probeName, long startedAt) {
        endProbe(probeName, startedAt, PROBE_THRESHOLD_NANOS);
    }

    public static void endProbeAlways(String probeName, long startedAt) {
        endProbe(probeName, startedAt, 0L);
    }

    public static long beginAutomaticSubscriberProbe() {
        return beginProbe();
    }

    public static void endAutomaticSubscriberClassLoad(String modId, String className, long startedAt) {
        endProbe("FML subscriber class load " + safeLabel(modId) + " " + safeLabel(className), startedAt, PROBE_THRESHOLD_NANOS);
    }

    public static void endAutomaticSubscriberRegister(String modId, String className, long startedAt) {
        endProbe("FML subscriber register " + safeLabel(modId) + " " + safeLabel(className), startedAt, PROBE_THRESHOLD_NANOS);
    }

    public static void endEventBusGetMethods(String targetName, long startedAt, int methodCount) {
        endProbe("FML EventBus getMethods " + safeLabel(targetName) + " methods=" + methodCount, startedAt, PROBE_THRESHOLD_NANOS);
    }

    public static void endEventBusHandlerRegister(String targetName, String eventName, String methodName, long startedAt) {
        endProbe(
                "FML EventBus handler " + safeLabel(targetName) + " " + safeLabel(eventName) + "#" + safeLabel(methodName),
                startedAt,
                PROBE_THRESHOLD_NANOS
        );
    }

    public static void beginResourceReload(int packCount) {
        if (!ENABLED || !RESOURCE_LOAD_ORDER_ENABLED) {
            return;
        }
        synchronized (LOCK) {
            ACTIVE_RESOURCE_RELOAD.set(new ResourceReloadData(++resourceReloadSequence, packCount, System.nanoTime()));
        }
    }

    public static void recordResourcePackReload(String packName, long elapsedNanos) {
        if (!ENABLED || !RESOURCE_LOAD_ORDER_ENABLED || packName == null || elapsedNanos < 0L) {
            return;
        }
        ResourceReloadData data = ACTIVE_RESOURCE_RELOAD.get();
        if (data != null) {
            data.resourcePacks.add(new OrderedTiming(data.resourcePacks.size() + 1, packName, elapsedNanos));
        }
    }

    public static void recordResourceReloadListener(String listenerName, long elapsedNanos, boolean immediate) {
        if (!ENABLED || !RESOURCE_LOAD_ORDER_ENABLED || listenerName == null || elapsedNanos < 0L) {
            return;
        }
        ResourceReloadData data = ACTIVE_RESOURCE_RELOAD.get();
        if (data != null) {
            data.listeners.add(new OrderedTiming(data.listeners.size() + 1, listenerName + (immediate ? " [register]" : ""), elapsedNanos));
        }
    }

    public static void endResourceReload() {
        if (!ENABLED || !RESOURCE_LOAD_ORDER_ENABLED) {
            return;
        }
        ResourceReloadData data = ACTIVE_RESOURCE_RELOAD.get();
        ACTIVE_RESOURCE_RELOAD.remove();
        if (data == null) {
            return;
        }
        data.elapsedNanos = System.nanoTime() - data.startedAt;
        logResourceReloadSummary(data);
    }

    private static void endProbe(String probeName, long startedAt, long thresholdNanos) {
        if (!ENABLED || startedAt == 0L || probeName == null) {
            return;
        }

        long elapsed = System.nanoTime() - startedAt;
        synchronized (LOCK) {
            String phaseName = activePhase != null ? activePhase : "<async>";
            PHASES.computeIfAbsent(phaseName, PhaseData::new).addProbe(probeName, elapsed);
        }
        if (elapsed >= thresholdNanos) {
            GPOM.LOGGER.info(
                    "[StartupProfiler] [Probe] {} took {} ms",
                    probeName,
                    formatMillis(elapsed)
            );
        }
    }

    private static long namedProbeThreshold(String probeName) {
        if (probeName.equals("MM hellfirepvp.modularmachinery.common.util.BlockArrayCache.buildBlockArrayCache")) {
            return 1_000_000_000L;
        }
        if (probeName.startsWith("BL block ")) {
            return PROBE_THRESHOLD_NANOS;
        }
        if (probeName.startsWith("ABYSS ")
                || probeName.startsWith("AOA ")
                || probeName.startsWith("BQ ")
                || probeName.startsWith("HEI ")
                || probeName.startsWith("BL ")
                || probeName.startsWith("MM ")
                || probeName.startsWith("OC ")
                || probeName.startsWith("PE ")
                || probeName.startsWith("FTB ")
                || probeName.startsWith("RC ")
                || probeName.startsWith("EIO ")
                || probeName.startsWith("CT ")
                || probeName.startsWith("IE ")
                || probeName.startsWith("BC ")
                || probeName.startsWith("TC ")
                || probeName.startsWith("TE ")
                || probeName.startsWith("TR ")
                || probeName.startsWith("THAUM ")
                || probeName.endsWith(".stop")
                || probeName.endsWith(".<init>")) {
            return PROBE_THRESHOLD_NANOS;
        }
        return 0L;
    }

    private static void pushProgressBar(String probeName) {
        if (!PROGRESS_BARS_ENABLED || !shouldShowProgress(probeName)) {
            return;
        }

        try {
            ACTIVE_PROGRESS_DEPTH.set(ACTIVE_PROGRESS_DEPTH.get() + 1);
            ProgressManager.ProgressBar bar = ProgressManager.push(progressTitle(probeName), 64);
            bar.step(shortProgressName(probeName));
            NAMED_PROGRESS_BARS.get()
                    .computeIfAbsent(probeName, ignored -> new ArrayDeque<>())
                    .push(bar);
        } catch (Throwable ignored) {
            // Progress bars are only visibility aids. Never let them affect startup.
        }
    }

    private static void popProgressBar(String probeName) {
        if (!PROGRESS_BARS_ENABLED) {
            return;
        }

        try {
            Map<String, Deque<ProgressManager.ProgressBar>> bars = NAMED_PROGRESS_BARS.get();
            Deque<ProgressManager.ProgressBar> stack = bars.get(probeName);
            if (stack == null || stack.isEmpty()) {
                return;
            }

            ProgressManager.pop(stack.pop());
            ACTIVE_PROGRESS_DEPTH.set(Math.max(0, ACTIVE_PROGRESS_DEPTH.get() - 1));
            if (stack.isEmpty()) {
                bars.remove(probeName);
            }
        } catch (Throwable ignored) {
            // Keep instrumentation best-effort.
        }
    }

    private static boolean shouldShowProgress(String probeName) {
        if (probeName.startsWith("HEI plugin ")) {
            return false;
        }
        if (probeName.equals("HEI mezz.jei.startup.ProxyCommonClient.loadComplete")
                || probeName.equals("HEI mezz.jei.startup.JeiStarter.load")
                || probeName.equals("HEI mezz.jei.startup.JeiStarter.registerIngredients")
                || probeName.equals("HEI mezz.jei.startup.JeiStarter.registerPlugins")
                || probeName.equals("HEI mezz.jei.startup.ModRegistry.createRecipeRegistry")
                || probeName.equals("HEI mezz.jei.ingredients.IngredientFilter.<init>")
                || probeName.equals("HEI mezz.jei.search.ElementSearch.addAll")) {
            return true;
        }
        if (probeName.equals("MM hellfirepvp.modularmachinery.common.machine.MachineRegistry.preloadMachines")
                || probeName.equals("MM hellfirepvp.modularmachinery.common.crafting.RecipeRegistry.loadRecipeRegistry")
                || probeName.equals("MM hellfirepvp.modularmachinery.common.util.BlockArrayCache.buildCache")) {
            return true;
        }
        return probeName.equals("THAUM thaumcraft.proxies.CommonProxy.postInit")
                || probeName.equals("THAUM thaumcraft.common.config.ConfigAspects.postInit")
                || probeName.equals("THAUM thaumcraft.common.config.ConfigAspects.registerItemAspects")
                || probeName.equals("THAUM thaumcraft.common.config.ConfigAspects.registerEntityAspects")
                || probeName.equals("THAUM thaumcraft.api.aspects.AspectEventProxy.registerComplexObjectTag")
                || probeName.equals("THAUM thaumcraft.common.lib.crafting.ThaumcraftCraftingManager.generateTagsFromCraftingRecipes")
                || probeName.equals("THAUM thaumcraft.common.lib.crafting.ThaumcraftCraftingManager.generateTagsFromRecipes");
    }

    private static String progressTitle(String probeName) {
        if (probeName.startsWith("THAUM ")) {
            return "GPOM Thaumcraft";
        }
        if (probeName.startsWith("HEI ")) {
            return "GPOM HEI";
        }
        if (probeName.startsWith("BQ ")) {
            return "GPOM BQ";
        }
        if (probeName.startsWith("PE ")) {
            return "GPOM ProjectE";
        }
        if (probeName.startsWith("FTB ")) {
            return "GPOM FTBLib";
        }
        if (probeName.startsWith("MM ")) {
            return "GPOM Modular Machinery";
        }
        if (probeName.startsWith("RC ")) {
            return "GPOM Railcraft";
        }
        if (probeName.startsWith("TR ")) {
            return "GPOM TechReborn";
        }
        return "GPOM";
    }

    private static String shortProgressName(String probeName) {
        String named = namedProgressStage(probeName);
        if (named != null) {
            return named;
        }
        if (probeName.startsWith("HEI ")) {
            probeName = probeName.substring(4);
        } else if (probeName.startsWith("BQ ")) {
            probeName = probeName.substring(3);
        } else if (probeName.startsWith("PE ")) {
            probeName = probeName.substring(3);
        } else if (probeName.startsWith("FTB ")) {
            probeName = probeName.substring(4);
        } else if (probeName.startsWith("MM ")) {
            probeName = probeName.substring(3);
        } else if (probeName.startsWith("RC ")) {
            probeName = probeName.substring(3);
        } else if (probeName.startsWith("TR ")) {
            probeName = probeName.substring(3);
        } else if (probeName.startsWith("THAUM ")) {
            probeName = probeName.substring(6);
        }
        int lastDot = probeName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == probeName.length() - 1) {
            return probeName;
        }

        int classDot = probeName.lastIndexOf('.', lastDot - 1);
        String className = classDot >= 0 ? probeName.substring(classDot + 1, lastDot) : probeName.substring(0, lastDot);
        String prefix = probeName.startsWith("thaumcraft.")
                ? "GPOM Thaum: "
                : probeName.startsWith("betterquesting.")
                ? "GPOM BQ: "
                : probeName.startsWith("moze_intel.projecte.")
                ? "GPOM PE: "
                : probeName.startsWith("com.feed_the_beast.ftblib.")
                ? "GPOM FTB: "
                : probeName.startsWith("hellfirepvp.modularmachinery.")
                ? "GPOM MM: "
                : probeName.startsWith("mods.railcraft.")
                ? "GPOM RC: "
                : probeName.startsWith("techreborn.")
                ? "GPOM TR: "
                : "GPOM HEI: ";
        return prefix + className + '.' + probeName.substring(lastDot + 1);
    }

    private static String namedProgressStage(String probeName) {
        if (probeName.equals("THAUM thaumcraft.proxies.CommonProxy.postInit")) {
            return "Post-init";
        }
        if (probeName.equals("THAUM thaumcraft.common.config.ConfigAspects.postInit")) {
            return "Aspect registry";
        }
        if (probeName.equals("THAUM thaumcraft.common.config.ConfigAspects.registerItemAspects")) {
            return "Item aspects";
        }
        if (probeName.equals("THAUM thaumcraft.common.config.ConfigAspects.registerEntityAspects")) {
            return "Entity aspects";
        }
        if (probeName.equals("THAUM thaumcraft.api.aspects.AspectEventProxy.registerComplexObjectTag")) {
            return "Complex aspects";
        }
        if (probeName.equals("THAUM thaumcraft.common.lib.crafting.ThaumcraftCraftingManager.generateTagsFromCraftingRecipes")) {
            return "Crafting aspect inference";
        }
        if (probeName.equals("THAUM thaumcraft.common.lib.crafting.ThaumcraftCraftingManager.generateTagsFromRecipes")) {
            return "Recipe aspect inference";
        }
        if (probeName.equals("HEI mezz.jei.startup.ProxyCommonClient.loadComplete")) {
            return "Load complete";
        }
        if (probeName.equals("HEI mezz.jei.startup.JeiStarter.load")) {
            return "Startup";
        }
        if (probeName.equals("HEI mezz.jei.startup.JeiStarter.registerIngredients")) {
            return "Ingredients";
        }
        if (probeName.equals("HEI mezz.jei.startup.JeiStarter.registerPlugins")) {
            return "Plugins";
        }
        if (probeName.equals("HEI mezz.jei.startup.ModRegistry.createRecipeRegistry")) {
            return "Recipe registry";
        }
        if (probeName.equals("HEI mezz.jei.ingredients.IngredientFilter.<init>")) {
            return "Search index";
        }
        if (probeName.equals("HEI mezz.jei.search.ElementSearch.addAll")) {
            return "Search population";
        }
        if (probeName.equals("MM hellfirepvp.modularmachinery.common.machine.MachineRegistry.preloadMachines")) {
            return "Machine preload";
        }
        if (probeName.equals("MM hellfirepvp.modularmachinery.common.crafting.RecipeRegistry.loadRecipeRegistry")) {
            return "Recipe registry";
        }
        if (probeName.equals("MM hellfirepvp.modularmachinery.common.util.BlockArrayCache.buildCache")) {
            return "Structure cache";
        }
        return null;
    }

    private static void finishPhaseLocked(String phaseName, long now) {
        PhaseData phase = PHASES.computeIfAbsent(phaseName, PhaseData::new);
        if (phaseName.equals(activePhase) && activePhaseStartedAt != 0L) {
            phase.wallTimeNanos += now - activePhaseStartedAt;
            activePhaseStartedAt = now;
        }
        logPhaseSummary(phase);
    }

    private static void logPhaseSummary(PhaseData phase) {
        if (phase.summaryLogged) {
            return;
        }
        phase.summaryLogged = true;

        List<ModTiming> timings = new ArrayList<>(phase.timings);
        timings.sort(Comparator.comparingLong((ModTiming timing) -> timing.elapsedNanos).reversed());

        GPOM.LOGGER.info(
                "[StartupProfiler] Finished FML phase {}: wall={} ms, modHandlers={} ms across {} mods, heap={} MiB",
                phase.name,
                formatMillis(phase.wallTimeNanos),
                formatMillis(phase.totalModNanos()),
                timings.size(),
                formatMib(usedHeapBytes())
        );

        logPhaseDigest(phase, timings);

        int limit = Math.min(TOP_COUNT, timings.size());
        for (int i = 0; i < limit; i++) {
            ModTiming timing = timings.get(i);
            GPOM.LOGGER.info(
                    "[StartupProfiler]   #{} {} ms, heapDelta={} MiB, heapAfter={} MiB - {} ({})",
                    i + 1,
                    formatMillis(timing.elapsedNanos),
                    formatMib(timing.heapDeltaBytes),
                    formatMib(timing.heapAfterBytes),
                    timing.modId,
                    timing.modName
            );
        }

        List<ModTiming> memoryTimings = new ArrayList<>(phase.timings);
        memoryTimings.sort(Comparator.comparingLong((ModTiming timing) -> timing.heapDeltaBytes).reversed());
        int memoryLimit = Math.min(TOP_COUNT, memoryTimings.size());
        for (int i = 0; i < memoryLimit; i++) {
            ModTiming timing = memoryTimings.get(i);
            if (timing.heapDeltaBytes <= 0L) {
                break;
            }
            GPOM.LOGGER.info(
                    "[StartupProfiler]   memory #{} heapDelta={} MiB, heapAfter={} MiB, {} ms - {} ({})",
                    i + 1,
                    formatMib(timing.heapDeltaBytes),
                    formatMib(timing.heapAfterBytes),
                    formatMillis(timing.elapsedNanos),
                    timing.modId,
                    timing.modName
            );
        }

        List<ProbeTiming> probes = new ArrayList<>(phase.probes.values());
        probes.sort(Comparator.comparingLong((ProbeTiming probe) -> probe.totalNanos).reversed());
        int probeLimit = Math.min(TOP_COUNT, probes.size());
        for (int i = 0; i < probeLimit; i++) {
            ProbeTiming probe = probes.get(i);
            GPOM.LOGGER.info(
                    "[StartupProfiler]   probe #{} {} ms total, {} ms max, count={} - {}",
                    i + 1,
                    formatMillis(probe.totalNanos),
                    formatMillis(probe.maxNanos),
                    probe.count,
                    probe.name
            );
        }
    }

    private static void logPhaseDigest(PhaseData phase, List<ModTiming> sortedTimings) {
        int modLimit = Math.min(PHASE_DIGEST_COUNT, sortedTimings.size());
        for (int i = 0; i < modLimit; i++) {
            ModTiming timing = sortedTimings.get(i);
            GPOM.LOGGER.info(
                    "[StartupProfiler]   digest mod #{} phase={} {} ms, heapDelta={} MiB - {} ({})",
                    i + 1,
                    phase.name,
                    formatMillis(timing.elapsedNanos),
                    formatMib(timing.heapDeltaBytes),
                    timing.modId,
                    timing.modName
            );
        }

        List<ModTiming> memoryTimings = new ArrayList<>(phase.timings);
        memoryTimings.sort(Comparator.comparingLong((ModTiming timing) -> timing.heapDeltaBytes).reversed());
        int memoryLimit = Math.min(PHASE_DIGEST_COUNT, memoryTimings.size());
        for (int i = 0; i < memoryLimit; i++) {
            ModTiming timing = memoryTimings.get(i);
            if (timing.heapDeltaBytes <= 0L) {
                break;
            }
            GPOM.LOGGER.info(
                    "[StartupProfiler]   digest memory #{} phase={} heapDelta={} MiB, {} ms - {} ({})",
                    i + 1,
                    phase.name,
                    formatMib(timing.heapDeltaBytes),
                    formatMillis(timing.elapsedNanos),
                    timing.modId,
                    timing.modName
            );
        }

        List<ProbeTiming> probes = new ArrayList<>(phase.probes.values());
        probes.sort(Comparator.comparingLong((ProbeTiming probe) -> probe.totalNanos).reversed());
        int probeLimit = Math.min(PHASE_DIGEST_COUNT, probes.size());
        for (int i = 0; i < probeLimit; i++) {
            ProbeTiming probe = probes.get(i);
            GPOM.LOGGER.info(
                    "[StartupProfiler]   digest probe #{} phase={} {} ms total, {} ms max, count={} - {}",
                    i + 1,
                    phase.name,
                    formatMillis(probe.totalNanos),
                    formatMillis(probe.maxNanos),
                    probe.count,
                    probe.name
            );
        }
    }

    private static void logResourceReloadSummary(ResourceReloadData data) {
        GPOM.LOGGER.info(
                "[ResourceLoadOrder] reload #{} finished: {} ms, packs={}, packLoads={}, listeners={}",
                data.sequence,
                formatMillis(data.elapsedNanos),
                data.packCount,
                data.resourcePacks.size(),
                data.listeners.size()
        );

        List<OrderedTiming> listenerOrder = new ArrayList<>(data.listeners);
        int orderLimit = Math.min(RESOURCE_LOAD_ORDER_TOP_COUNT, listenerOrder.size());
        for (int i = 0; i < orderLimit; i++) {
            OrderedTiming timing = listenerOrder.get(i);
            GPOM.LOGGER.info(
                    "[ResourceLoadOrder]   listener order #{} {} ms - {}",
                    timing.order,
                    formatMillis(timing.elapsedNanos),
                    timing.name
            );
        }

        List<OrderedTiming> slowListeners = new ArrayList<>(data.listeners);
        slowListeners.sort(Comparator.comparingLong((OrderedTiming timing) -> timing.elapsedNanos).reversed());
        int listenerLimit = Math.min(RESOURCE_LOAD_ORDER_TOP_COUNT, slowListeners.size());
        for (int i = 0; i < listenerLimit; i++) {
            OrderedTiming timing = slowListeners.get(i);
            GPOM.LOGGER.info(
                    "[ResourceLoadOrder]   slow listener #{} {} ms, order={} - {}",
                    i + 1,
                    formatMillis(timing.elapsedNanos),
                    timing.order,
                    timing.name
            );
        }

        List<OrderedTiming> slowPacks = new ArrayList<>(data.resourcePacks);
        slowPacks.sort(Comparator.comparingLong((OrderedTiming timing) -> timing.elapsedNanos).reversed());
        int packLimit = Math.min(RESOURCE_LOAD_ORDER_TOP_COUNT, slowPacks.size());
        for (int i = 0; i < packLimit; i++) {
            OrderedTiming timing = slowPacks.get(i);
            GPOM.LOGGER.info(
                    "[ResourceLoadOrder]   slow pack #{} {} ms, order={} - {}",
                    i + 1,
                    formatMillis(timing.elapsedNanos),
                    timing.order,
                    timing.name
            );
        }
    }

    private static String eventName(FMLEvent event) {
        String type = event.getEventType();
        if (type != null && !type.isEmpty()) {
            return type;
        }
        return event.getClass().getSimpleName();
    }

    private static String safeModId(ModContainer container) {
        try {
            return container.getModId();
        } catch (RuntimeException ignored) {
            return "<unknown>";
        }
    }

    private static String safeModName(ModContainer container) {
        try {
            return container.getName();
        } catch (RuntimeException ignored) {
            return "<unknown>";
        }
    }

    private static String safeLabel(String value) {
        return value == null || value.isEmpty() ? "<unknown>" : value;
    }

    private static String formatMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static String formatMib(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0D);
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static long popHeapStart(long fallback) {
        Deque<Long> starts = MOD_HEAP_STARTS.get();
        return starts.isEmpty() ? fallback : starts.pop();
    }

    private static long millisProperty(String key, long fallback) {
        try {
            return Long.parseLong(System.getProperty(key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static int intProperty(String key, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(System.getProperty(key, Integer.toString(fallback))));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longProperty(String key, long fallback) {
        try {
            return Long.parseLong(System.getProperty(key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Set<String> setProperty(String key, String fallback) {
        Set<String> values = new HashSet<>();
        String raw = System.getProperty(key, fallback);
        for (String value : Arrays.asList(raw.split(","))) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    public static final class StackSampler {
        private final Thread targetThread;
        private final String modId;
        private final String modName;
        private final String eventName;
        private final long startedAt;
        private volatile boolean running = true;

        private StackSampler(Thread targetThread, String modId, String modName, String eventName, long startedAt) {
            this.targetThread = targetThread;
            this.modId = modId;
            this.modName = modName;
            this.eventName = eventName;
            this.startedAt = startedAt;
        }

        private void start() {
            Thread samplerThread = new Thread(this::run, "GPOM Startup Stack Sampler - " + modId);
            samplerThread.setDaemon(true);
            samplerThread.start();
        }

        private void stop() {
            running = false;
        }

        private void run() {
            sleep(STACK_SAMPLER_THRESHOLD_MILLIS);
            while (running) {
                logStackSample();
                sleep(STACK_SAMPLER_INTERVAL_MILLIS);
            }
        }

        private void logStackSample() {
            StackTraceElement[] stackTrace = targetThread.getStackTrace();
            long elapsed = System.nanoTime() - startedAt;

            GPOM.LOGGER.info(
                    "[StartupProfiler] [StackSample] {} still handling {} for {} ({}) after {} ms",
                    modId,
                    eventName,
                    modId,
                    modName,
                    formatMillis(elapsed)
            );

            int limit = Math.min(STACK_SAMPLER_MAX_FRAMES, stackTrace.length);
            for (int i = 0; i < limit; i++) {
                GPOM.LOGGER.info("[StartupProfiler] [StackSample]   at {}", stackTrace[i]);
            }
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    private static final class PhaseData {
        private final String name;
        private final List<ModTiming> timings = new ArrayList<>();
        private final Map<String, ProbeTiming> probes = new LinkedHashMap<>();
        private long wallTimeNanos;
        private boolean summaryLogged;

        private PhaseData(String name) {
            this.name = name;
        }

        private void add(ModTiming timing) {
            timings.add(timing);
        }

        private void addProbe(String name, long elapsedNanos) {
            probes.computeIfAbsent(name, ProbeTiming::new).add(elapsedNanos);
        }

        private long totalModNanos() {
            long total = 0L;
            for (ModTiming timing : timings) {
                total += timing.elapsedNanos;
            }
            return total;
        }
    }

    private static final class ResourceReloadData {
        private final int sequence;
        private final int packCount;
        private final long startedAt;
        private final List<OrderedTiming> resourcePacks = new ArrayList<>();
        private final List<OrderedTiming> listeners = new ArrayList<>();
        private long elapsedNanos;

        private ResourceReloadData(int sequence, int packCount, long startedAt) {
            this.sequence = sequence;
            this.packCount = packCount;
            this.startedAt = startedAt;
        }
    }

    private static final class OrderedTiming {
        private final int order;
        private final String name;
        private final long elapsedNanos;

        private OrderedTiming(int order, String name, long elapsedNanos) {
            this.order = order;
            this.name = name;
            this.elapsedNanos = elapsedNanos;
        }
    }

    private static final class ModTiming {
        private final String modId;
        private final String modName;
        private final long elapsedNanos;
        private final long heapDeltaBytes;
        private final long heapAfterBytes;

        private ModTiming(String modId, String modName, long elapsedNanos, long heapDeltaBytes, long heapAfterBytes) {
            this.modId = modId;
            this.modName = modName;
            this.elapsedNanos = elapsedNanos;
            this.heapDeltaBytes = heapDeltaBytes;
            this.heapAfterBytes = heapAfterBytes;
        }
    }

    private static final class ProbeTiming {
        private final String name;
        private long totalNanos;
        private long maxNanos;
        private int count;

        private ProbeTiming(String name) {
            this.name = name;
        }

        private void add(long elapsedNanos) {
            totalNanos += elapsedNanos;
            maxNanos = Math.max(maxNanos, elapsedNanos);
            count++;
        }
    }
}
