package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class CraftTweakerPreInitOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.crafttweaker.fastZenRegister", "false"));
    private static final boolean PARALLEL_CLASS_LOAD = Boolean.parseBoolean(System.getProperty("gpom.crafttweaker.fastZenRegister.parallelClassLoad", "false"));
    private static final int CLASS_LOAD_WORKERS = intProperty("gpom.crafttweaker.fastZenRegister.classLoadWorkers", 0);
    private static final boolean DEEP_PROBES = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.fastZenRegister.deepProbes",
            System.getProperty("gpom.crafttweaker.parallelScriptParsing.deepProbes", "false")
    ));
    private static final String ZEN_REGISTER = "crafttweaker.annotations.ZenRegister";
    private static final String ZEN_EXPANSION = "stanhebben.zenscript.annotations.ZenExpansion";
    private static final String ZEN_CLASS = "stanhebben.zenscript.annotations.ZenClass";
    private static final String BRACKET_HANDLER = "crafttweaker.annotations.BracketHandler";
    private static final String MOD_ONLY = "crafttweaker.annotations.ModOnly";
    private static final String MODS_ONLY = "crafttweaker.annotations.ModsOnly";
    private static final String ON_REGISTER = "crafttweaker.annotations.OnRegister";

    private static volatile Reflection reflection;
    private static volatile boolean fallbackLogged;

    private CraftTweakerPreInitOptimizations() {
    }

    public static boolean registerZenClasses(ASMDataTable asmData, ClassLoader loader) {
        if (!ENABLED || asmData == null || loader == null) {
            return false;
        }

        Metrics metrics = DEEP_PROBES ? new Metrics() : null;
        long totalStarted = start(metrics);
        Reflection methods;
        try {
            long started = start(metrics);
            methods = reflection(loader);
            addReflection(metrics, started);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker fast ZenRegister helper unavailable; using stock registration", throwable);
            return false;
        }

        long asmStarted = start(metrics);
        Set<ASMDataTable.ASMData> registrations = asmData.getAll(ZEN_REGISTER);
        if (registrations == null || registrations.isEmpty()) {
            return true;
        }

        Set<String> expansions = classSet(asmData.getAll(ZEN_EXPANSION));
        Set<String> nativeClasses = classSet(asmData.getAll(ZEN_CLASS));
        Set<String> bracketHandlers = classSet(asmData.getAll(BRACKET_HANDLER));
        Map<String, ASMDataTable.ASMData> modOnly = firstByClass(asmData.getAll(MOD_ONLY));
        Map<String, List<ASMDataTable.ASMData>> modsOnly = allByClass(asmData.getAll(MODS_ONLY));
        Map<String, Set<String>> onRegisterMethods = methodsByClass(asmData.getAll(ON_REGISTER));
        addAsmScan(metrics, asmStarted);

        List<ASMDataTable.ASMData> orderedRegistrations = new ArrayList<>();
        for (ASMDataTable.ASMData data : registrations) {
            if (data != null && data.getClassName() != null) {
                orderedRegistrations.add(data);
            }
        }
        List<ClassLoadResult> classLoadResults = loadClasses(orderedRegistrations, loader, metrics);

        int loaded = 0;
        int skipped = 0;
        int slowFallbacks = 0;
        for (ClassLoadResult loadResult : classLoadResults) {
            String className = loadResult.className;
            if (className == null) {
                continue;
            }
            try {
                if (loadResult.failure != null) {
                    if (loadResult.failure instanceof ClassNotFoundException) {
                        GPOM.LOGGER.warn("[CraftTweaker Optimizations] Could not load ZenRegister class {}", className, loadResult.failure);
                        continue;
                    }
                    throw loadResult.failure;
                }
                Class<?> type = loadResult.type;
                long filterStarted = start(metrics);
                if (!passesModFilters(type, modOnly.get(className), modsOnly.get(className), methods)) {
                    addModFilters(metrics, filterStarted);
                    skipped++;
                    continue;
                }
                addModFilters(metrics, filterStarted);
                long registerStarted = start(metrics);
                fastRegisterClass(
                        type,
                        expansions.contains(className),
                        nativeClasses.contains(className),
                        bracketHandlers.contains(className),
                        onRegisterMethods.get(className),
                        methods,
                        metrics
                );
                addRegisterClass(metrics, registerStarted);
                loaded++;
            } catch (ClassNotFoundException throwable) {
                GPOM.LOGGER.warn("[CraftTweaker Optimizations] Could not load ZenRegister class {}", className, throwable);
            } catch (Throwable throwable) {
                slowFallbacks++;
                long fallbackStarted = start(metrics);
                if (!trySlowRegister(className, loader, methods, throwable)) {
                    GPOM.LOGGER.warn("[CraftTweaker Optimizations] Fast ZenRegister failed for {}", className, throwable);
                }
                addSlowFallback(metrics, fallbackStarted);
            }
        }
        addTotal(metrics, totalStarted);

        if (loaded > 0 || skipped > 0 || slowFallbacks > 0) {
            GPOM.LOGGER.info(
                    "[CraftTweaker Optimizations] Fast-registered {} ZenRegister class(es), skipped {}, slowFallbacks={}",
                    loaded,
                    skipped,
                    slowFallbacks
            );
        }
        if (metrics != null) {
            metrics.log(registrations.size(), loaded, skipped, slowFallbacks);
        }
        return true;
    }

    private static List<ClassLoadResult> loadClasses(List<ASMDataTable.ASMData> registrations,
                                                     ClassLoader loader,
                                                     Metrics metrics) {
        if (registrations == null || registrations.isEmpty()) {
            return Collections.emptyList();
        }
        int workers = classLoadWorkers(registrations.size());
        if (!PARALLEL_CLASS_LOAD || workers <= 1) {
            long wallStarted = start(metrics);
            List<ClassLoadResult> results = new ArrayList<>(registrations.size());
            for (ASMDataTable.ASMData data : registrations) {
                ClassLoadResult result = loadClass(data, loader);
                addClassLoadDuration(metrics, result.elapsedNanos);
                results.add(result);
            }
            addClassLoadWall(metrics, wallStarted, 1);
            return results;
        }

        long wallStarted = start(metrics);
        ExecutorService executor = Executors.newFixedThreadPool(workers, new CtClassLoadThreadFactory());
        try {
            List<Future<ClassLoadResult>> futures = new ArrayList<>(registrations.size());
            for (ASMDataTable.ASMData data : registrations) {
                futures.add(executor.submit(new ClassLoadTask(data, loader)));
            }
            List<ClassLoadResult> results = new ArrayList<>(futures.size());
            for (Future<ClassLoadResult> future : futures) {
                ClassLoadResult result;
                try {
                    result = future.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    result = ClassLoadResult.failed(null, null, exception, 0L);
                } catch (ExecutionException exception) {
                    result = ClassLoadResult.failed(null, null, exception.getCause() == null ? exception : exception.getCause(), 0L);
                }
                addClassLoadDuration(metrics, result.elapsedNanos);
                results.add(result);
            }
            return results;
        } finally {
            executor.shutdownNow();
            addClassLoadWall(metrics, wallStarted, workers);
        }
    }

    private static ClassLoadResult loadClass(ASMDataTable.ASMData data, ClassLoader loader) {
        if (data == null || data.getClassName() == null) {
            return ClassLoadResult.failed(data, null, new ClassNotFoundException("<missing class name>"), 0L);
        }
        String className = data.getClassName();
        long startedAt = System.nanoTime();
        try {
            return new ClassLoadResult(data, className, Class.forName(className, false, loader), null, System.nanoTime() - startedAt);
        } catch (Throwable throwable) {
            return ClassLoadResult.failed(data, className, throwable, System.nanoTime() - startedAt);
        }
    }

    private static int classLoadWorkers(int tasks) {
        if (!PARALLEL_CLASS_LOAD || tasks <= 1) {
            return 1;
        }
        int configured = CLASS_LOAD_WORKERS;
        if (configured <= 0) {
            int processors = Runtime.getRuntime().availableProcessors();
            configured = Math.max(2, Math.min(4, processors / 2));
        }
        return Math.max(1, Math.min(tasks, configured));
    }

    private static boolean passesModFilters(Class<?> type,
                                            ASMDataTable.ASMData modOnlyData,
                                            List<ASMDataTable.ASMData> modsOnlyData,
                                            Reflection methods) throws Exception {
        List<String> singleMod = annotationValues(modOnlyData);
        if (!singleMod.isEmpty()) {
            return Loader.isModLoaded(singleMod.get(0));
        }
        if (modOnlyData != null) {
            Annotation annotation = type.getAnnotation(methods.modOnlyAnnotation);
            if (annotation != null) {
                String modId = (String) methods.modOnlyValue.invoke(annotation);
                return Loader.isModLoaded(modId);
            }
        }

        List<String> requiredMods = annotationValues(modsOnlyData);
        if (!requiredMods.isEmpty()) {
            for (String modId : requiredMods) {
                if (!Loader.isModLoaded(modId)) {
                    return false;
                }
            }
            return true;
        }
        if (modsOnlyData != null && !modsOnlyData.isEmpty()) {
            Annotation annotation = type.getAnnotation(methods.modsOnlyAnnotation);
            if (annotation != null) {
                String[] modIds = (String[]) methods.modsOnlyValue.invoke(annotation);
                for (String modId : modIds) {
                    if (!Loader.isModLoaded(modId)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void fastRegisterClass(Class<?> type,
                                          boolean isExpansion,
                                          boolean isNativeClass,
                                          boolean isBracketHandler,
                                          Set<String> onRegisterMethodNames,
                                          Reflection methods,
                                          Metrics metrics) throws Exception {
        boolean registered = false;
        if (isExpansion) {
            long started = start(metrics);
            methods.registerExpansion.invoke(null, type);
            addExpansion(metrics, started);
            registered = true;
        }
        if (isNativeClass) {
            long started = start(metrics);
            methods.registerNativeClass.invoke(null, type);
            addNativeClass(metrics, started);
            registered = true;
        }
        if (isBracketHandler && methods.bracketHandlerType.isAssignableFrom(type)) {
            long started = start(metrics);
            Object handler = type.newInstance();
            methods.registerBracketHandler.invoke(null, handler);
            addBracketHandler(metrics, started);
            registered = true;
        }
        if (!registered || onRegisterMethodNames == null || onRegisterMethodNames.isEmpty()) {
            return;
        }
        long started = start(metrics);
        for (Method method : type.getDeclaredMethods()) {
            if (!onRegisterMethodNames.contains(method.getName())) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers)
                    && Modifier.isPublic(modifiers)
                    && method.isAnnotationPresent(methods.onRegisterAnnotation)) {
                method.invoke(null);
            }
        }
        addOnRegister(metrics, started);
    }

    private static boolean trySlowRegister(String className, ClassLoader loader, Reflection methods, Throwable originalFailure) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            methods.registerClass.invoke(null, type);
            return true;
        } catch (Throwable fallbackFailure) {
            fallbackFailure.addSuppressed(originalFailure);
            GPOM.LOGGER.warn("[CraftTweaker Optimizations] Slow ZenRegister fallback failed for {}", className, fallbackFailure);
            return false;
        }
    }

    private static Reflection reflection(ClassLoader loader) throws Exception {
        Reflection current = reflection;
        if (current != null) {
            return current;
        }
        synchronized (CraftTweakerPreInitOptimizations.class) {
            current = reflection;
            if (current != null) {
                return current;
            }
            Class<?> globalRegistry = Class.forName("crafttweaker.zenscript.GlobalRegistry", false, loader);
            Class<?> api = Class.forName("crafttweaker.CraftTweakerAPI", false, loader);
            Class<?> bracketHandlerType = Class.forName("crafttweaker.zenscript.IBracketHandler", false, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> modOnly = (Class<? extends Annotation>) Class.forName(MOD_ONLY, false, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> modsOnly = (Class<? extends Annotation>) Class.forName(MODS_ONLY, false, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> onRegister = (Class<? extends Annotation>) Class.forName(ON_REGISTER, false, loader);
            current = new Reflection(
                    globalRegistry.getMethod("registerExpansion", Class.class),
                    globalRegistry.getMethod("registerNativeClass", Class.class),
                    api.getMethod("registerBracketHandler", bracketHandlerType),
                    api.getMethod("registerClass", Class.class),
                    bracketHandlerType,
                    modOnly,
                    modOnly.getMethod("value"),
                    modsOnly,
                    modsOnly.getMethod("value"),
                    onRegister
            );
            reflection = current;
            return current;
        }
    }

    private static Set<String> classSet(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null) {
                result.add(entry.getClassName());
            }
        }
        return result;
    }

    private static Map<String, ASMDataTable.ASMData> firstByClass(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ASMDataTable.ASMData> result = new HashMap<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null && !result.containsKey(entry.getClassName())) {
                result.put(entry.getClassName(), entry);
            }
        }
        return result;
    }

    private static Map<String, List<ASMDataTable.ASMData>> allByClass(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<ASMDataTable.ASMData>> result = new HashMap<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null) {
                result.computeIfAbsent(entry.getClassName(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        return result;
    }

    private static Map<String, Set<String>> methodsByClass(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null && entry.getObjectName() != null) {
                result.computeIfAbsent(entry.getClassName(), ignored -> new HashSet<>()).add(entry.getObjectName());
            }
        }
        return result;
    }

    private static List<String> annotationValues(List<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (ASMDataTable.ASMData entry : data) {
            result.addAll(annotationValues(entry));
        }
        return result;
    }

    private static List<String> annotationValues(ASMDataTable.ASMData data) {
        if (data == null || data.getAnnotationInfo() == null) {
            return Collections.emptyList();
        }
        return stringValues(data.getAnnotationInfo().get("value"));
    }

    private static List<String> stringValues(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof String) {
            result.add((String) value);
        } else if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                addStringValue(result, item);
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addStringValue(result, Array.get(value, i));
            }
        } else {
            addStringValue(result, value);
        }
        return result;
    }

    private static void addStringValue(List<String> values, Object value) {
        if (value instanceof String && !((String) value).isEmpty()) {
            values.add((String) value);
        }
    }

    private static void logFallback(String message, Throwable throwable) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            GPOM.LOGGER.warn("[CraftTweaker Optimizations] " + message, throwable);
        }
    }

    private static long start(Metrics metrics) {
        return metrics == null ? 0L : System.nanoTime();
    }

    private static long elapsed(long startedAt) {
        return startedAt == 0L ? 0L : System.nanoTime() - startedAt;
    }

    private static void addReflection(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.reflectionNanos += elapsed(startedAt);
        }
    }

    private static void addAsmScan(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.asmScanNanos += elapsed(startedAt);
        }
    }

    private static void addClassLoadDuration(Metrics metrics, long elapsedNanos) {
        if (metrics != null) {
            metrics.classLoadNanos += elapsedNanos;
            metrics.classLoadCount++;
        }
    }

    private static void addClassLoadWall(Metrics metrics, long startedAt, int workers) {
        if (metrics != null) {
            metrics.classLoadWallNanos += elapsed(startedAt);
            metrics.classLoadWorkers = Math.max(metrics.classLoadWorkers, workers);
        }
    }

    private static void addModFilters(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.modFilterNanos += elapsed(startedAt);
            metrics.modFilterCount++;
        }
    }

    private static void addRegisterClass(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.registerClassNanos += elapsed(startedAt);
            metrics.registerClassCount++;
        }
    }

    private static void addExpansion(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.expansionNanos += elapsed(startedAt);
            metrics.expansionCount++;
        }
    }

    private static void addNativeClass(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.nativeClassNanos += elapsed(startedAt);
            metrics.nativeClassCount++;
        }
    }

    private static void addBracketHandler(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.bracketHandlerNanos += elapsed(startedAt);
            metrics.bracketHandlerCount++;
        }
    }

    private static void addOnRegister(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.onRegisterNanos += elapsed(startedAt);
            metrics.onRegisterCount++;
        }
    }

    private static void addSlowFallback(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.slowFallbackNanos += elapsed(startedAt);
            metrics.slowFallbackCount++;
        }
    }

    private static void addTotal(Metrics metrics, long startedAt) {
        if (metrics != null) {
            metrics.totalNanos += elapsed(startedAt);
        }
    }

    private static String millis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static int intProperty(String key, int defaultValue) {
        String value = System.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static final class ClassLoadTask implements Callable<ClassLoadResult> {
        private final ASMDataTable.ASMData data;
        private final ClassLoader loader;

        private ClassLoadTask(ASMDataTable.ASMData data, ClassLoader loader) {
            this.data = data;
            this.loader = loader;
        }

        @Override
        public ClassLoadResult call() {
            return loadClass(data, loader);
        }
    }

    private static final class CtClassLoadThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GPOM CT ZenRegister class load " + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class ClassLoadResult {
        private final ASMDataTable.ASMData data;
        private final String className;
        private final Class<?> type;
        private final Throwable failure;
        private final long elapsedNanos;

        private ClassLoadResult(ASMDataTable.ASMData data,
                                String className,
                                Class<?> type,
                                Throwable failure,
                                long elapsedNanos) {
            this.data = data;
            this.className = className;
            this.type = type;
            this.failure = failure;
            this.elapsedNanos = elapsedNanos;
        }

        private static ClassLoadResult failed(ASMDataTable.ASMData data,
                                              String className,
                                              Throwable failure,
                                              long elapsedNanos) {
            return new ClassLoadResult(data, className, null, failure, elapsedNanos);
        }
    }

    private static final class Metrics {
        private long totalNanos;
        private long reflectionNanos;
        private long asmScanNanos;
        private long classLoadNanos;
        private long classLoadWallNanos;
        private long modFilterNanos;
        private long registerClassNanos;
        private long expansionNanos;
        private long nativeClassNanos;
        private long bracketHandlerNanos;
        private long onRegisterNanos;
        private long slowFallbackNanos;
        private int classLoadCount;
        private int classLoadWorkers;
        private int modFilterCount;
        private int registerClassCount;
        private int expansionCount;
        private int nativeClassCount;
        private int bracketHandlerCount;
        private int onRegisterCount;
        private int slowFallbackCount;

        private void log(int registrations, int loaded, int skipped, int slowFallbacks) {
            GPOM.LOGGER.info(
                    "[CraftTweaker Optimizations] ZenRegister metrics total={} ms registrations={} loaded={} skipped={} slowFallbacks={} reflection={} ms asmScan={} ms classLoad={} ms/{} modFilters={} ms/{} registerClass={} ms/{} nativeClass={} ms/{} expansion={} ms/{} bracketHandler={} ms/{} onRegister={} ms/{} slowFallback={} ms/{}",
                    millis(totalNanos),
                    registrations,
                    loaded,
                    skipped,
                    slowFallbacks,
                    millis(reflectionNanos),
                    millis(asmScanNanos),
                    millis(classLoadNanos),
                    classLoadCount,
                    millis(modFilterNanos),
                    modFilterCount,
                    millis(registerClassNanos),
                    registerClassCount,
                    millis(nativeClassNanos),
                    nativeClassCount,
                    millis(expansionNanos),
                    expansionCount,
                    millis(bracketHandlerNanos),
                    bracketHandlerCount,
                    millis(onRegisterNanos),
                    onRegisterCount,
                    millis(slowFallbackNanos),
                    slowFallbackCount
            );
            if (classLoadWorkers > 1) {
                GPOM.LOGGER.info(
                        "[CraftTweaker Optimizations] ZenRegister parallel class load wall={} ms workers={} summedClassLoad={} ms",
                        millis(classLoadWallNanos),
                        classLoadWorkers,
                        millis(classLoadNanos)
                );
            }
        }
    }

    private static final class Reflection {
        private final Method registerExpansion;
        private final Method registerNativeClass;
        private final Method registerBracketHandler;
        private final Method registerClass;
        private final Class<?> bracketHandlerType;
        private final Class<? extends Annotation> modOnlyAnnotation;
        private final Method modOnlyValue;
        private final Class<? extends Annotation> modsOnlyAnnotation;
        private final Method modsOnlyValue;
        private final Class<? extends Annotation> onRegisterAnnotation;

        private Reflection(Method registerExpansion,
                           Method registerNativeClass,
                           Method registerBracketHandler,
                           Method registerClass,
                           Class<?> bracketHandlerType,
                           Class<? extends Annotation> modOnlyAnnotation,
                           Method modOnlyValue,
                           Class<? extends Annotation> modsOnlyAnnotation,
                           Method modsOnlyValue,
                           Class<? extends Annotation> onRegisterAnnotation) {
            this.registerExpansion = registerExpansion;
            this.registerNativeClass = registerNativeClass;
            this.registerBracketHandler = registerBracketHandler;
            this.registerClass = registerClass;
            this.bracketHandlerType = bracketHandlerType;
            this.modOnlyAnnotation = modOnlyAnnotation;
            this.modOnlyValue = modOnlyValue;
            this.modsOnlyAnnotation = modsOnlyAnnotation;
            this.modsOnlyValue = modsOnlyValue;
            this.onRegisterAnnotation = onRegisterAnnotation;
        }
    }
}
