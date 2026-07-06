package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import com.l.gpom.util.GpomSide;
import net.minecraftforge.fml.common.ModContainer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class PreInitClassPrewarmer {
    private static final Map<String, List<String>> DEFAULT_PREFIXES = defaultPrefixes();
    private static final Set<String> FORCE_NO_INIT_MODS = forceNoInitMods();
    private static final Object SERIAL_PAUSE_LOCK = new Object();
    private static int serialPauseDepth;

    private PreInitClassPrewarmer() {
    }

    public static SerialPause pauseDuringSerialHandler() {
        if (!GpomEarlyConfig.preInitClassPrewarmPauseDuringSerialHandlers()) {
            return SerialPause.noop();
        }
        return pause();
    }

    public static SerialPause pauseDuringBlockingWait() {
        if (!GpomEarlyConfig.preInitClassPrewarmPauseDuringBlockingWaits()) {
            return SerialPause.noop();
        }
        return pause();
    }

    private static SerialPause pause() {
        synchronized (SERIAL_PAUSE_LOCK) {
            serialPauseDepth++;
        }
        return new SerialPause(true);
    }

    private static boolean awaitSerialQuiet() {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (!GpomEarlyConfig.preInitClassPrewarmPauseDuringSerialHandlers()
                && !GpomEarlyConfig.preInitClassPrewarmPauseDuringBlockingWaits()) {
            return true;
        }
        synchronized (SERIAL_PAUSE_LOCK) {
            while (serialPauseDepth > 0) {
                try {
                    SERIAL_PAUSE_LOCK.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    private static void resumeAfterSerialHandler() {
        synchronized (SERIAL_PAUSE_LOCK) {
            if (serialPauseDepth > 0) {
                serialPauseDepth--;
            }
            if (serialPauseDepth == 0) {
                SERIAL_PAUSE_LOCK.notifyAll();
            }
        }
    }

    public static void prewarm(List<ModContainer> mods, int fallbackWorkers) {
        WarmHandle handle = startAsync(mods, fallbackWorkers);
        handle.await();
        handle.close();
    }

    public static WarmHandle startAsync(List<ModContainer> mods, int fallbackWorkers) {
        if (!GpomEarlyConfig.preInitClassPrewarmEnabled() || mods == null || mods.isEmpty()) {
            return WarmHandle.noop();
        }

        long startedAt = StartupProfiler.beginProbe();
        PreparedWarmup prepared = prepare(mods, fallbackWorkers);

        if (prepared.units.isEmpty()) {
            StartupProfiler.endProbe("PreInit class prewarm scan empty", startedAt);
            return WarmHandle.noop();
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = PreInitClassPrewarmer.class.getClassLoader();
        }

        AtomicBoolean cancelled = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(prepared.workers, runnable -> {
            Thread thread = new Thread(runnable, "GPOM PreInit class prewarmer");
            thread.setDaemon(true);
            return thread;
        });
        List<Future<WarmResult>> futures = new ArrayList<Future<WarmResult>>();
        for (WarmUnit unit : prepared.units) {
            futures.add(executor.submit(new WarmTask(loader, unit.label, unit.classNames, unit.initialize, cancelled)));
        }

        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[PreInitClassPrewarmer] Started async prewarm of {} candidate class(es) for {} mod group(s) using {} worker(s); chunkSize={}, initialize={}, noInit={}",
                    prepared.candidates,
                    prepared.modGroups,
                    prepared.workers,
                    prepared.chunkSize,
                    prepared.initializeClasses,
                    prepared.noInitCandidates
            );
        }
        StartupProfiler.endProbeAlways("PreInit class prewarm async start", startedAt);
        return new WarmHandle(
                startedAt,
                prepared.candidates,
                prepared.modGroups,
                prepared.workers,
                prepared.totalsByLabel,
                cancelled,
                executor,
                futures
        );
    }

    private static PreparedWarmup prepare(List<ModContainer> mods, int fallbackWorkers) {
        Set<String> allowlist = GpomEarlyConfig.preInitClassPrewarmAllowlist();
        int maxClassesPerMod = GpomEarlyConfig.preInitClassPrewarmMaxClassesPerMod();
        int chunkSize = GpomEarlyConfig.preInitClassPrewarmChunkSize();
        int workers = Math.max(1, GpomEarlyConfig.preInitClassPrewarmWorkers());
        if (fallbackWorkers > 0) {
            workers = Math.min(workers, fallbackWorkers);
        }
        boolean includeAnonClasses = GpomEarlyConfig.preInitClassPrewarmIncludeAnonClasses();
        boolean initializeClasses = GpomEarlyConfig.preInitClassPrewarmInitializeClasses();
        Set<String> initializeAllowlist = GpomEarlyConfig.preInitClassPrewarmInitializeAllowlist();
        Map<String, List<String>> extraPrefixes = parseExtraPrefixes(GpomEarlyConfig.preInitClassPrewarmExtraPrefixes());
        Set<String> noInitAllowlist = GpomEarlyConfig.preInitClassPrewarmNoInitAllowlist();
        Map<String, List<String>> noInitPrefixes = parseExtraPrefixes(GpomEarlyConfig.preInitClassPrewarmNoInitPrefixes());
        Map<String, List<String>> explicitClasses = parseExplicitClasses(GpomEarlyConfig.preInitClassPrewarmExplicitClasses());
        Map<String, LinkedHashSet<String>> classesByMod = new LinkedHashMap<String, LinkedHashSet<String>>();
        Map<String, LinkedHashSet<String>> noInitClassesByMod = new LinkedHashMap<String, LinkedHashSet<String>>();

        for (Map.Entry<String, List<String>> entry : explicitClasses.entrySet()) {
            LinkedHashSet<String> classes = classesByMod.computeIfAbsent(entry.getKey(), ignored -> new LinkedHashSet<String>());
            classes.addAll(entry.getValue());
        }

        if (!allowlist.isEmpty()) {
            for (ModContainer mod : mods) {
                if (mod == null) {
                    continue;
                }
                String modId = normalize(mod.getModId());
                if (!allowlist.contains("*") && !allowlist.contains(modId)) {
                    continue;
                }

                List<String> prefixes = new ArrayList<String>();
                List<String> defaults = DEFAULT_PREFIXES.get(modId);
                if (defaults != null) {
                    prefixes.addAll(defaults);
                }
                List<String> extra = extraPrefixes.get(modId);
                if (extra != null) {
                    prefixes.addAll(extra);
                }
                if (prefixes.isEmpty()) {
                    continue;
                }

                File source = sourceFile(mod);
                if (source == null || !source.exists()) {
                    continue;
                }

                List<String> classNames = scanSource(source, prefixes, includeAnonClasses, maxClassesPerMod);
                addScannedClasses(classesByMod,
                        noInitClassesByMod,
                        modId,
                        classNames,
                        shouldInitializeScannedMod(modId, initializeClasses, initializeAllowlist));
            }
        }

        if (!noInitAllowlist.isEmpty() || !noInitPrefixes.isEmpty()) {
            boolean allNoInit = noInitAllowlist.contains("*");
            for (ModContainer mod : mods) {
                if (mod == null) {
                    continue;
                }
                String modId = normalize(mod.getModId());
                if (modId.isEmpty()) {
                    continue;
                }
                List<String> prefixes = new ArrayList<String>();
                boolean initializeScanned = shouldInitializeScannedMod(modId, initializeClasses, initializeAllowlist);
                if ((allNoInit || noInitAllowlist.contains(modId)) && !initializeScanned) {
                    List<String> defaults = DEFAULT_PREFIXES.get(modId);
                    if (defaults != null) {
                        prefixes.addAll(defaults);
                    }
                    List<String> extra = extraPrefixes.get(modId);
                    if (extra != null) {
                        prefixes.addAll(extra);
                    }
                }
                List<String> specific = noInitPrefixes.get(modId);
                if (specific != null) {
                    prefixes.addAll(specific);
                }
                addScannedNoInitClasses(noInitClassesByMod, mod, modId, prefixes, includeAnonClasses, maxClassesPerMod);
            }
        }

        Map<String, List<String>> immutable = new LinkedHashMap<String, List<String>>();
        int candidates = 0;
        for (Map.Entry<String, LinkedHashSet<String>> entry : classesByMod.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> classes = new ArrayList<String>(entry.getValue());
            immutable.put(entry.getKey(), classes);
            candidates += classes.size();
        }
        Map<String, List<String>> noInitImmutable = new LinkedHashMap<String, List<String>>();
        int noInitCandidates = 0;
        for (Map.Entry<String, LinkedHashSet<String>> entry : noInitClassesByMod.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            List<String> classes = new ArrayList<String>(entry.getValue());
            noInitImmutable.put(entry.getKey(), classes);
            noInitCandidates += classes.size();
        }
        List<WarmGroup> groups = new ArrayList<WarmGroup>();
        Map<String, Integer> totalsByLabel = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, List<String>> entry : immutable.entrySet()) {
            groups.add(new WarmGroup(entry.getKey(), entry.getValue(), true));
            totalsByLabel.put(entry.getKey(), entry.getValue().size());
        }
        for (Map.Entry<String, List<String>> entry : noInitImmutable.entrySet()) {
            String label = entry.getKey() + " no-init";
            groups.add(new WarmGroup(label, entry.getValue(), false));
            totalsByLabel.put(label, entry.getValue().size());
        }
        List<WarmUnit> units = interleavedUnits(groups, chunkSize);
        return new PreparedWarmup(
                units,
                totalsByLabel,
                candidates + noInitCandidates,
                noInitCandidates,
                immutable.size() + noInitImmutable.size(),
                workers,
                chunkSize,
                initializeClasses
        );
    }

    private static List<WarmUnit> interleavedUnits(List<WarmGroup> groups, int chunkSize) {
        if (groups.isEmpty()) {
            return Collections.emptyList();
        }
        List<WarmUnit> units = new ArrayList<WarmUnit>();
        int offset = 0;
        boolean added;
        do {
            added = false;
            for (WarmGroup group : groups) {
                if (offset >= group.classNames.size()) {
                    continue;
                }
                int end = Math.min(group.classNames.size(), offset + chunkSize);
                units.add(new WarmUnit(
                        group.label,
                        new ArrayList<String>(group.classNames.subList(offset, end)),
                        group.initialize
                ));
                added = true;
            }
            offset += chunkSize;
        } while (added);
        return units;
    }

    private static List<String> scanSource(File source, List<String> prefixes, boolean includeAnonClasses, int maxClasses) {
        try {
            List<String> candidates = source.isDirectory()
                    ? scanDirectory(source, prefixes, includeAnonClasses)
                    : scanJar(source, prefixes, includeAnonClasses);
            Collections.sort(candidates, CLASS_PRIORITY);
            if (candidates.size() > maxClasses) {
                return new ArrayList<String>(candidates.subList(0, maxClasses));
            }
            return candidates;
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[PreInitClassPrewarmer] Failed to scan {}", source, throwable);
            }
            return Collections.emptyList();
        }
    }

    private static void addScannedNoInitClasses(Map<String, LinkedHashSet<String>> classesByMod,
                                                ModContainer mod,
                                                String modId,
                                                List<String> prefixes,
                                                boolean includeAnonClasses,
                                                int maxClassesPerMod) {
        if (mod == null || modId == null || modId.isEmpty() || prefixes == null || prefixes.isEmpty()) {
            return;
        }
        File source = sourceFile(mod);
        if (source == null || !source.exists()) {
            return;
        }
        List<String> classNames = scanSource(source, prefixes, includeAnonClasses, maxClassesPerMod);
        if (!classNames.isEmpty()) {
            LinkedHashSet<String> classes = classesByMod.computeIfAbsent(modId, ignored -> new LinkedHashSet<String>());
            classes.addAll(classNames);
        }
    }

    private static void addScannedClasses(Map<String, LinkedHashSet<String>> initClassesByMod,
                                          Map<String, LinkedHashSet<String>> noInitClassesByMod,
                                          String modId,
                                          List<String> classNames,
                                          boolean initialize) {
        if (modId == null || modId.isEmpty() || classNames == null || classNames.isEmpty()) {
            return;
        }
        if (!initialize) {
            LinkedHashSet<String> classes = noInitClassesByMod.computeIfAbsent(modId, ignored -> new LinkedHashSet<String>());
            classes.addAll(classNames);
            return;
        }
        if (FORCE_NO_INIT_MODS.contains(normalize(modId))) {
            LinkedHashSet<String> classes = noInitClassesByMod.computeIfAbsent(modId, ignored -> new LinkedHashSet<String>());
            classes.addAll(classNames);
            return;
        }

        LinkedHashSet<String> initClasses = initClassesByMod.computeIfAbsent(modId, ignored -> new LinkedHashSet<String>());
        LinkedHashSet<String> noInitClasses = null;
        for (String className : classNames) {
            if (isUnsafeToInitializeDuringPrewarm(modId, className)) {
                if (noInitClasses == null) {
                    noInitClasses = noInitClassesByMod.computeIfAbsent(modId, ignored -> new LinkedHashSet<String>());
                }
                noInitClasses.add(className);
            } else {
                initClasses.add(className);
            }
        }
    }

    private static Map<String, ModContainer> modsById(List<ModContainer> mods) {
        Map<String, ModContainer> result = new HashMap<String, ModContainer>();
        for (ModContainer mod : mods) {
            if (mod != null) {
                result.put(normalize(mod.getModId()), mod);
            }
        }
        return result;
    }

    private static List<String> scanJar(File source, List<String> prefixes, boolean includeAnonClasses) throws IOException {
        List<String> result = new ArrayList<String>();
        JarFile jarFile = new JarFile(source);
        try {
            java.util.Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (isCandidateClass(name, prefixes, includeAnonClasses)) {
                    result.add(toClassName(name));
                }
            }
        } finally {
            jarFile.close();
        }
        return result;
    }

    private static List<String> scanDirectory(File source, List<String> prefixes, boolean includeAnonClasses) throws IOException {
        final Path root = source.toPath();
        final List<String> result = new ArrayList<String>();
        Files.walk(root).forEach(path -> {
            if (!Files.isRegularFile(path)) {
                return;
            }
            String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
            if (isCandidateClass(relative, prefixes, includeAnonClasses)) {
                result.add(toClassName(relative));
            }
        });
        return result;
    }

    private static boolean isCandidateClass(String name, List<String> prefixes, boolean includeAnonClasses) {
        if (name == null || !name.endsWith(".class") || name.endsWith("package-info.class") || name.endsWith("module-info.class")) {
            return false;
        }
        if (GpomSide.isDedicatedServerLaunch() && isClientOnlyClassEntry(name)) {
            return false;
        }
        if (!includeAnonClasses && (name.contains("$anonfun") || name.contains("$$anon") || name.contains("$lambda"))) {
            return false;
        }
        for (String prefix : prefixes) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String toClassName(String entryName) {
        return entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
    }

    private static boolean isClientOnlyClassEntry(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("net/minecraft/client/")
                || lower.contains("/client/")
                || lower.contains("/clientgui/")
                || lower.contains("/gui/client/")
                || lower.contains("/render/")
                || lower.contains("/renderer/")
                || lower.contains("/rendering/")
                || lower.endsWith("/clientproxy.class")
                || lower.endsWith("clientproxy.class")
                || lower.endsWith("/clienteventhandler.class")
                || lower.endsWith("clienteventhandler.class");
    }

    private static File sourceFile(ModContainer mod) {
        try {
            Method method = mod.getClass().getMethod("getSource");
            Object value = method.invoke(mod);
            if (value instanceof File) {
                return (File) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Map<String, List<String>> parseExtraPrefixes(String raw) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (String modEntry : raw.split(";")) {
            int separator = modEntry.indexOf(':');
            if (separator <= 0 || separator >= modEntry.length() - 1) {
                continue;
            }
            String modId = normalize(modEntry.substring(0, separator));
            if (modId.isEmpty()) {
                continue;
            }
            List<String> prefixes = new ArrayList<String>();
            for (String prefix : modEntry.substring(separator + 1).split("\\|")) {
                String normalized = normalizePrefix(prefix);
                if (!normalized.isEmpty()) {
                    prefixes.add(normalized);
                }
            }
            if (!prefixes.isEmpty()) {
                result.put(modId, prefixes);
            }
        }
        return result;
    }

    private static Map<String, List<String>> parseExplicitClasses(String raw) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        for (String modEntry : raw.split(";")) {
            int separator = modEntry.indexOf(':');
            if (separator <= 0 || separator >= modEntry.length() - 1) {
                continue;
            }
            String modId = normalize(modEntry.substring(0, separator));
            if (modId.isEmpty()) {
                continue;
            }
            List<String> classes = new ArrayList<String>();
            for (String className : modEntry.substring(separator + 1).split("\\|")) {
                String normalized = normalizeClassName(className);
                if (!normalized.isEmpty()) {
                    classes.add(normalized);
                }
            }
            if (!classes.isEmpty()) {
                result.put(modId, classes);
            }
        }
        return result;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        String value = prefix.trim().replace('.', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String normalizeClassName(String className) {
        if (className == null) {
            return "";
        }
        String value = className.trim().replace('/', '.');
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<String>> defaultPrefixes() {
        Map<String, List<String>> prefixes = new HashMap<String, List<String>>();
        prefixes.put("opencomputers", Arrays.asList(
                "li/cil/oc/OpenComputers",
                "li/cil/oc/Settings",
                "li/cil/oc/common/Proxy",
                "li/cil/oc/client/Proxy",
                "li/cil/oc/common/init/",
                "li/cil/oc/common/block/",
                "li/cil/oc/common/item/",
                "li/cil/oc/common/tileentity/",
                "li/cil/oc/common/recipe/",
                "li/cil/oc/client/renderer/block/"
        ));
        prefixes.put("brandonscore", Collections.singletonList("com/brandon3055/brandonscore/"));
        prefixes.put("gendustry", Collections.singletonList("net/bdew/gendustry/"));
        prefixes.put("aoa3", Arrays.asList("net/tslat/aoa3/advent/", "net/tslat/aoa3/common/", "net/tslat/aoa3/utils/", "net/tslat/aoa3/hooks/"));
        prefixes.put("appliedenergistics2", Collections.singletonList("appeng/"));
        prefixes.put("ebwizardry", Collections.singletonList("electroblob/wizardry/"));
        prefixes.put("deepmoblearning", Collections.singletonList("mustapelto/deepmoblearning/"));
        prefixes.put("topaddons", Collections.singletonList("io/github/drmanganese/"));
        prefixes.put("railcraft", Arrays.asList("mods/railcraft/common/core/", "mods/railcraft/common/modules/", "mods/railcraft/common/items/", "mods/railcraft/common/blocks/"));
        prefixes.put("contenttweaker", Collections.singletonList("com/teamacronymcoders/contenttweaker/"));
        prefixes.put("crafttweaker", Arrays.asList("crafttweaker/", "stanhebben/zenscript/"));
        prefixes.put("erebus", Arrays.asList("erebus/Erebus", "erebus/Mod", "erebus/core/", "erebus/recipes/", "erebus/proxy/"));
        prefixes.put("extrautils2", Collections.singletonList("com/rwtema/extrautils2/"));
        prefixes.put("environmentaltech", Arrays.asList("com/valkyrieofnight/et/", "com/valkyrieofnight/vliblegacy/"));
        prefixes.put("teslacorelib", Collections.singletonList("net/modcrafters/mclib/"));
        prefixes.put("rftools", Arrays.asList("mcjty/rftools/", "mcjty/lib/setup/"));
        prefixes.put("thebetweenlands", Collections.singletonList("thebetweenlands/"));
        prefixes.put("enderio", Collections.singletonList("crazypants/enderio/"));
        prefixes.put("randomthings", Collections.singletonList("lumien/randomthings/"));
        prefixes.put("nuclearcraft", Collections.singletonList("nc/"));
        prefixes.put("twilightforest", Arrays.asList(
                "twilightforest/TwilightForestMod",
                "twilightforest/TFFeature",
                "twilightforest/TFCommonProxy",
                "twilightforest/client/TFClientProxy",
                "twilightforest/block/",
                "twilightforest/item/",
                "twilightforest/entity/",
                "twilightforest/world/"
        ));
        prefixes.put("modularmachinery", Arrays.asList("github/kasuminova/mmce/", "hellfirepvp/modularmachinery/"));
        prefixes.put("integrateddynamics", Arrays.asList("org/cyclops/integrateddynamics/", "org/cyclops/cyclopscore/"));
        prefixes.put("expequiv", Collections.singletonList("tk/zeitheron/expequiv/"));
        prefixes.put("forestry", Collections.singletonList("forestry/"));
        prefixes.put("jei", Collections.singletonList("mezz/jei/"));
        return prefixes;
    }

    private static Set<String> forceNoInitMods() {
        return new HashSet<String>(Arrays.asList(
                "deepmoblearning",
                // XU2 client/font classes create FontRenderer instances in static initializers.
                "extrautils2"
        ));
    }

    private static final Comparator<String> CLASS_PRIORITY = new Comparator<String>() {
        @Override
        public int compare(String left, String right) {
            int leftPriority = priority(left);
            int rightPriority = priority(right);
            if (leftPriority != rightPriority) {
                return leftPriority - rightPriority;
            }
            return left.compareTo(right);
        }

        private int priority(String name) {
            int priority = 0;
            if (name.indexOf('$') >= 0) {
                priority += 10;
            }
            if (name.endsWith("$")) {
                priority -= 5;
            }
            if (IMPORTANT_CLASSES.contains(name)) {
                priority -= 20;
            }
            return priority;
        }
    };

    private static final Set<String> IMPORTANT_CLASSES = new HashSet<String>(Arrays.asList(
            "li.cil.oc.OpenComputers",
            "li.cil.oc.OpenComputers$",
            "li.cil.oc.Settings",
            "li.cil.oc.Settings$",
            "li.cil.oc.common.Proxy",
            "li.cil.oc.client.Proxy",
            "li.cil.oc.common.init.Blocks$",
            "li.cil.oc.common.init.Items$",
            "com.brandon3055.brandonscore.BrandonsCore",
            "com.brandon3055.brandonscore.CommonProxy",
            "com.brandon3055.brandonscore.BCConfig",
            "com.rwtema.extrautils2.ExtraUtils2",
            "com.rwtema.extrautils2.backend.entries.XU2Entries",
            "com.rwtema.extrautils2.backend.entries.EntryHandler",
            "erebus.Erebus",
            "erebus.proxy.CommonProxy",
            "org.cyclops.integrateddynamics.IntegratedDynamics",
            "org.cyclops.cyclopscore.init.ModBase",
            "com.valkyrieofnight.et.ETMod",
            "com.valkyrieofnight.vliblegacy.lib.sys.proxy.VLCommonProxy",
            "twilightforest.TwilightForestMod",
            "twilightforest.TFFeature",
            "twilightforest.TFCommonProxy",
            "twilightforest.client.TFClientProxy",
            "crafttweaker.runtime.CrTTweaker",
            "crafttweaker.mc1120.CraftTweaker",
            "com.teamacronymcoders.contenttweaker.ContentTweaker"
    ));

    private static boolean isUnsafeToInitializeDuringPrewarm(String modId, String className) {
        if (className == null || className.isEmpty()) {
            return true;
        }
        String normalizedModId = normalize(modId);
        if (PREWARM_NO_INIT_EXACT_CLASSES.contains(className)
                || PREWARM_NO_INIT_EXACT_CLASSES.contains(normalizedModId + ":" + className)) {
            return true;
        }

        String lower = className.toLowerCase(Locale.ROOT);
        if (lower.endsWith("registry")
                || lower.endsWith("registries")
                || lower.endsWith("modblocks")
                || lower.endsWith("moditems")
                || lower.endsWith("blockregistry")
                || lower.endsWith("itemregistry")
                || lower.endsWith("recipehandler")
                || lower.endsWith("recipehandlers")
                || lower.contains(".registry.")
                || lower.contains(".registries.")
                || lower.contains(".init.")
                || lower.contains(".blocks.")
                || lower.contains(".block.")
                || lower.contains(".items.")
                || lower.contains(".item.")
                || lower.contains(".recipes.")
                || lower.contains(".recipe.")) {
            return true;
        }
        return lower.endsWith("$blocks")
                || lower.endsWith("$items")
                || lower.endsWith("$recipes")
                || lower.endsWith("$registry")
                || lower.endsWith("$registries");
    }

    private static final Set<String> PREWARM_NO_INIT_EXACT_CLASSES = new HashSet<String>(Arrays.asList(
            "mustapelto.deepmoblearning.common.DMLRegistry",
            "deepmoblearning:mustapelto.deepmoblearning.common.DMLRegistry"
    ));

    private static boolean shouldInitializeScannedMod(String modId, boolean initializeClasses, Set<String> initializeAllowlist) {
        if (initializeClasses) {
            return true;
        }
        return initializeAllowlist != null && (initializeAllowlist.contains("*") || initializeAllowlist.contains(normalize(modId)));
    }

    private static final class PreparedWarmup {
        private final List<WarmUnit> units;
        private final Map<String, Integer> totalsByLabel;
        private final int candidates;
        private final int noInitCandidates;
        private final int modGroups;
        private final int workers;
        private final int chunkSize;
        private final boolean initializeClasses;

        private PreparedWarmup(List<WarmUnit> units,
                               Map<String, Integer> totalsByLabel,
                               int candidates,
                               int noInitCandidates,
                               int modGroups,
                               int workers,
                               int chunkSize,
                               boolean initializeClasses) {
            this.units = units;
            this.totalsByLabel = totalsByLabel;
            this.candidates = candidates;
            this.noInitCandidates = noInitCandidates;
            this.modGroups = modGroups;
            this.workers = workers;
            this.chunkSize = chunkSize;
            this.initializeClasses = initializeClasses;
        }
    }

    private static final class WarmGroup {
        private final String label;
        private final List<String> classNames;
        private final boolean initialize;

        private WarmGroup(String label, List<String> classNames, boolean initialize) {
            this.label = label;
            this.classNames = classNames;
            this.initialize = initialize;
        }
    }

    private static final class WarmUnit {
        private final String label;
        private final List<String> classNames;
        private final boolean initialize;

        private WarmUnit(String label, List<String> classNames, boolean initialize) {
            this.label = label;
            this.classNames = classNames;
            this.initialize = initialize;
        }
    }

    public static final class WarmHandle {
        private static final WarmHandle NOOP = new WarmHandle(
                0L,
                0,
                0,
                0,
                Collections.<String, Integer>emptyMap(),
                new AtomicBoolean(false),
                null,
                Collections.<Future<WarmResult>>emptyList()
        );

        private final long startedAt;
        private final int candidates;
        private final int modGroups;
        private final int workers;
        private final Map<String, Integer> totalsByLabel;
        private final AtomicBoolean cancelled;
        private final ExecutorService executor;
        private final List<Future<WarmResult>> futures;
        private boolean closed;

        private WarmHandle(long startedAt,
                           int candidates,
                           int modGroups,
                           int workers,
                           Map<String, Integer> totalsByLabel,
                           AtomicBoolean cancelled,
                           ExecutorService executor,
                           List<Future<WarmResult>> futures) {
            this.startedAt = startedAt;
            this.candidates = candidates;
            this.modGroups = modGroups;
            this.workers = workers;
            this.totalsByLabel = totalsByLabel;
            this.cancelled = cancelled;
            this.executor = executor;
            this.futures = futures;
        }

        public static WarmHandle noop() {
            return NOOP;
        }

        private void await() {
            if (executor == null) {
                return;
            }
            for (Future<WarmResult> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException ignored) {
                }
            }
        }

        public void close() {
            if (executor == null || closed) {
                return;
            }
            closed = true;
            int loaded = 0;
            int failed = 0;
            int pending = 0;
            Map<String, MutableWarmResult> aggregate = new LinkedHashMap<String, MutableWarmResult>();
            for (Map.Entry<String, Integer> entry : totalsByLabel.entrySet()) {
                aggregate.put(entry.getKey(), new MutableWarmResult(entry.getValue()));
            }
            for (Future<WarmResult> future : futures) {
                if (!future.isDone()) {
                    pending++;
                    continue;
                }
                try {
                    WarmResult result = future.get();
                    loaded += result.loaded;
                    failed += result.failed;
                    MutableWarmResult mutable = aggregate.get(result.label);
                    if (mutable != null) {
                        mutable.loaded += result.loaded;
                        mutable.failed += result.failed;
                    }
                } catch (Throwable ignored) {
                    failed++;
                }
            }
            if (pending > 0) {
                cancelled.set(true);
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
            long elapsedMillis = startedAt == 0L ? 0L : (System.nanoTime() - startedAt) / 1_000_000L;
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[PreInitClassPrewarmer] Async prewarm status: loaded={} candidates={} modGroups={} failed={} pending={} elapsed={} ms workers={}",
                        loaded,
                        candidates,
                        modGroups,
                        failed,
                        pending,
                        elapsedMillis,
                        workers
                );
                for (Map.Entry<String, MutableWarmResult> entry : aggregate.entrySet()) {
                    MutableWarmResult result = entry.getValue();
                    GPOM.LOGGER.info(
                            "[PreInitClassPrewarmer] {} loaded {}/{} class(es), failed={}",
                            entry.getKey(),
                            result.loaded,
                            result.total,
                            result.failed
                    );
                }
            }
        }
    }

    private static final class MutableWarmResult {
        private final int total;
        private int loaded;
        private int failed;

        private MutableWarmResult(int total) {
            this.total = total;
        }
    }

    public static final class SerialPause implements AutoCloseable {
        private static final SerialPause NOOP = new SerialPause(false);

        private final boolean active;
        private boolean closed;

        private SerialPause(boolean active) {
            this.active = active;
        }

        private static SerialPause noop() {
            return NOOP;
        }

        @Override
        public void close() {
            if (!active || closed) {
                return;
            }
            closed = true;
            resumeAfterSerialHandler();
        }
    }

    private static final class WarmTask implements Callable<WarmResult> {
        private final ClassLoader loader;
        private final String label;
        private final List<String> classNames;
        private final boolean initialize;
        private final AtomicBoolean cancelled;

        private WarmTask(ClassLoader loader, String label, List<String> classNames, boolean initialize, AtomicBoolean cancelled) {
            this.loader = loader;
            this.label = label;
            this.classNames = classNames;
            this.initialize = initialize;
            this.cancelled = cancelled;
        }

        @Override
        public WarmResult call() {
            int loaded = 0;
            int failed = 0;
            for (String className : classNames) {
                if (cancelled.get() || Thread.currentThread().isInterrupted() || !awaitSerialQuiet() || cancelled.get()) {
                    break;
                }
                try {
                    Class.forName(className, initialize, loader);
                    loaded++;
                } catch (ClassNotFoundException | LinkageError | RuntimeException exception) {
                    failed++;
                }
            }
            return new WarmResult(label, loaded, failed);
        }
    }

    private static final class WarmResult {
        private final String label;
        private final int loaded;
        private final int failed;

        private WarmResult(String label, int loaded, int failed) {
            this.label = label;
            this.loaded = loaded;
            this.failed = failed;
        }
    }
}
