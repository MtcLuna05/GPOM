package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.launchwrapper.Launch;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@SuppressWarnings({"rawtypes", "unchecked"})
public final class CraftTweakerScriptLoadOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.parallelScriptParsing.enabled", "false"));
    private static final Set<String> ALLOWLIST = setProperty("gpom.crafttweaker.parallelScriptParsing.allowlist", "*");
    private static final Set<String> DENYLIST = setProperty("gpom.crafttweaker.parallelScriptParsing.denylist", "");
    private static final int CONFIGURED_WORKERS = intProperty("gpom.crafttweaker.parallelScriptParsing.workers", 0);
    private static final boolean OFF_THREAD_ZEN_PARSE = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.parallelScriptParsing.offThreadZenParse", "false"));
    private static final boolean SUPPRESS_GLOBAL_DEBUG_COMPILE_LOGS = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.parallelScriptParsing.suppressGlobalDebugCompileLogs", "true"));
    private static final boolean DEEP_PROBES = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.parallelScriptParsing.deepProbes", "false"));
    private static final boolean BATCH_ALLOWED_SCRIPTS = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.parallelScriptParsing.batchAllowedScripts", "false"));
    private static final AtomicBoolean BRIDGE_FAILURE_LOGGED = new AtomicBoolean();
    private static final AtomicBoolean ENABLED_LOGGED = new AtomicBoolean();
    private static volatile Bridge bridge;

    private CraftTweakerScriptLoadOptimizations() {
    }

    public static Boolean tryLoadScript(Object tweaker, boolean forced, Object loader, List errors, boolean syntaxCommand) {
        if (!ENABLED || syntaxCommand) {
            return null;
        }
        Bridge resolved = bridge();
        if (resolved == null) {
            return null;
        }
        long probeStartedAt = StartupProfiler.beginProbe();
        ExecutorService executor = null;
        boolean ownsLoad = false;
        try {
            if (forced) {
                resolved.craftTweakerApiSetSuppressErrorFlag.invoke(null, resolved.suppressErrorForced);
            }
            if (loader == null) {
                logError(resolved, "Error when trying to load with a null loader");
                return Boolean.FALSE;
            }

            logInfo(resolved, "Loading scripts for loader with names" + loader);
            if (Boolean.TRUE.equals(resolved.scriptLoaderIsLoaded.invoke(loader)) && !forced) {
                logDefault(resolved, "Skipping loading for loader" + loader + " since it's already been loaded");
                return Boolean.FALSE;
            }
            if (Boolean.TRUE.equals(resolved.scriptLoaderIsDelayed.invoke(loader)) && !forced) {
                logDefault(resolved, "Skipping loading for loader" + loader + " since its execution is being delayed by another mod.");
                return Boolean.FALSE;
            }
            Object loaderStage = resolved.scriptLoaderGetLoaderStage.invoke(loader);
            if (resolved.loaderStageInvalidated.equals(loaderStage)) {
                logWarning(resolved, "Skipping loading for loader" + loader + " since it's become invalidated");
                return Boolean.FALSE;
            }

            ownsLoad = true;
            resolved.scriptLoaderSetLoaderStage.invoke(loader, resolved.loaderStageLoading);
            Object networkSide = resolved.crtTweakerNetworkSide.get(tweaker);
            Object preprocessorManager = resolved.crtTweakerPreprocessorManager.get(tweaker);
            if (!syntaxCommand) {
                publish(resolved, resolved.crtTweakerStartedEventList.get(tweaker),
                        resolved.loadingStartedEvent.newInstance(loader, forced, networkSide));
            }
            resolved.preprocessorManagerClean.invoke(preprocessorManager);

            List scriptFiles = (List) resolved.crtTweakerLoadPreprocessor.invoke(tweaker, forced);
            Object globalErrors = resolved.globalRegistryGetErrors.invoke(null);
            resolved.crtStoringErrorLoggerClear.invoke(globalErrors);

            Map classes = new HashMap();
            String mainName = (String) resolved.scriptLoaderGetMainName.invoke(loader);
            Object environment = resolved.globalRegistryMakeGlobalEnvironment.invoke(null, classes, mainName);
            Object compileEnvironment = resolved.environmentGetEnvironment.invoke(environment);
            Object classNameGenerator = resolved.environmentGetClassNameGenerator.invoke(environment);
            String descriptor = descriptor(resolved, tweaker, mainName, networkSide);
            long startedAt = System.currentTimeMillis();

            List<ScriptPlan> plans = preparePlans(resolved, loader, scriptFiles, forced, networkSide, descriptor);
            int workers = workerCount(plans);
            if (workers > 1) {
                executor = Executors.newFixedThreadPool(workers, new CtScriptThreadFactory());
            }
            if (ENABLED_LOGGED.compareAndSet(false, true)) {
                GPOM.LOGGER.info(
                        "CraftTweaker parallel script loading enabled with {} worker(s), offThreadZenParse={}, batchAllowedScripts={}, deepProbes={}, allowlist={}, denylist={}",
                        Math.max(1, workers), OFF_THREAD_ZEN_PARSE, BATCH_ALLOWED_SCRIPTS, DEEP_PROBES, ALLOWLIST, DENYLIST
                );
            }

            LoadContext context = new LoadContext(
                    resolved,
                    tweaker,
                    loader,
                    errors,
                    forced,
                    syntaxCommand,
                    networkSide,
                    preprocessorManager,
                    environment,
                    compileEnvironment,
                    classNameGenerator,
                    classes,
                    descriptor,
                    executor
            );
            context.planCount = plans.size();
            processPlans(context, plans);
            finishLoad(context, System.currentTimeMillis() - startedAt);
            return Boolean.valueOf(context.success);
        } catch (Throwable throwable) {
            if (!ownsLoad) {
                logBridgeFailure("CraftTweaker parallel script parser bridge failed before taking over; using stock loadScript", throwable);
                return null;
            }
            GPOM.LOGGER.warn("CraftTweaker parallel script parser failed after taking over loadScript; marking loader errored", throwable);
            try {
                logError(resolved, "[GPOM]: CraftTweaker parallel script parser failed after taking over loadScript", throwable);
            } catch (Throwable ignored) {
                // Keep the transformer-owned method from crashing after it has already changed loader state.
            }
            try {
                if (loader != null) {
                    resolved.scriptLoaderSetLoaderStage.invoke(loader, resolved.loaderStageError);
                }
            } catch (Throwable ignored) {
                // Nothing safer to do after owning the load path.
            }
            return Boolean.FALSE;
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            StartupProfiler.endProbeAlways("CT parallel CrTTweaker.loadScript", probeStartedAt);
        }
    }

    private static List<ScriptPlan> preparePlans(Bridge bridge,
                                                 Object loader,
                                                 List scriptFiles,
                                                 boolean forced,
                                                 Object networkSide,
                                                 String descriptor) throws Exception {
        List<ScriptPlan> plans = new ArrayList<>();
        Set<String> loadedEffectiveNames = new HashSet<>();
        for (Object script : scriptFiles) {
            String mainName = (String) bridge.scriptLoaderGetMainName.invoke(loader);
            String[] loaderNames = (String[]) bridge.scriptFileGetLoaderNames.invoke(script);
            if (!Boolean.TRUE.equals(bridge.scriptLoaderCanExecute.invoke(loader, (Object) loaderNames))) {
                if (!forced) {
                    logDefault(bridge, descriptor + ": Skipping file" + script + " as we are currently loading with a different loader");
                }
                continue;
            }
            if (!Boolean.TRUE.equals(bridge.scriptFileShouldBeLoadedOn.invoke(script, networkSide))) {
                logDefault(bridge, descriptor + ": Skipping file" + script + " as we are on the wrong side of the Network");
                continue;
            }
            String effectiveName = (String) bridge.scriptFileGetEffectiveName.invoke(script);
            if (loadedEffectiveNames.contains(effectiveName)) {
                continue;
            }
            loadedEffectiveNames.add(effectiveName);
            logDefault(bridge, descriptor + ": Loading Script:" + script);
            String extractedClassName = (String) bridge.zenModuleExtractClassName.invoke(null, effectiveName);
            int priority = ((Integer) bridge.scriptFileGetPriority.invoke(script)).intValue();
            String groupName = (String) bridge.scriptFileGetGroupName.invoke(script);
            String name = (String) bridge.scriptFileGetName.invoke(script);
            boolean parallelAllowed = parallelAllowed(effectiveName, groupName, name);
            plans.add(new ScriptPlan(script, effectiveName, extractedClassName, priority, groupName, name, mainName, parallelAllowed));
        }
        return plans;
    }

    private static void processPlans(LoadContext context, List<ScriptPlan> plans) throws Exception {
        List<ScriptPlan> bucket = new ArrayList<>();
        Integer currentPriority = null;
        for (ScriptPlan plan : plans) {
            if (currentPriority != null && plan.priority != currentPriority.intValue()) {
                processPriorityBucket(context, bucket);
                bucket.clear();
            }
            currentPriority = Integer.valueOf(plan.priority);
            bucket.add(plan);
        }
        processPriorityBucket(context, bucket);
    }

    private static void processPriorityBucket(LoadContext context, List<ScriptPlan> bucket) throws Exception {
        if (bucket.isEmpty()) {
            return;
        }
        List<ScriptPlan> parallelChunk = new ArrayList<>();
        for (ScriptPlan plan : bucket) {
            if (plan.parallelAllowed) {
                parallelChunk.add(plan);
            } else {
                processParallelChunk(context, parallelChunk);
                parallelChunk.clear();
                processPlanSerial(context, plan);
            }
        }
        processParallelChunk(context, parallelChunk);
    }

    private static void processParallelChunk(LoadContext context, List<ScriptPlan> chunk) throws Exception {
        if (chunk.isEmpty()) {
            return;
        }
        if (context.executor == null || chunk.size() < 2) {
            for (ScriptPlan plan : chunk) {
                processPlanSerial(context, plan);
            }
            return;
        }
        if (!OFF_THREAD_ZEN_PARSE) {
            processSourcePreloadChunk(context, chunk);
            return;
        }

        long parseStartedAt = StartupProfiler.beginProbe();
        List<Future<ParseResult>> futures = new ArrayList<>(chunk.size());
        for (ScriptPlan plan : chunk) {
            publishScriptPre(context, plan);
            ParseResult earlyResult = prepareScriptForParse(context, plan);
            if (earlyResult != null) {
                plan.parseResult = earlyResult;
                futures.add(null);
            } else {
                futures.add(context.executor.submit(new ParseTask(context.bridge, context.environment, context.compileEnvironment, plan)));
            }
        }
        for (int i = 0; i < chunk.size(); i++) {
            Future<ParseResult> future = futures.get(i);
            if (future != null) {
                chunk.get(i).parseResult = future.get();
            }
        }
        StartupProfiler.endProbeAlways("CT parallel script parse priority " + chunk.get(0).priority + " count " + chunk.size(), parseStartedAt);

        if (canBatchChunk(context, chunk)) {
            completeParsedPlansBatch(context, chunk);
        } else {
            for (ScriptPlan plan : chunk) {
                completeParsedPlan(context, plan, plan.parseResult);
                plan.parseResult = null;
            }
        }
    }

    private static void processSourcePreloadChunk(LoadContext context, List<ScriptPlan> chunk) throws Exception {
        long readStartedAt = StartupProfiler.beginProbe();
        List<Future<ParseResult>> futures = new ArrayList<>(chunk.size());
        for (ScriptPlan plan : chunk) {
            futures.add(context.executor.submit(new ReadTask(context.bridge, plan)));
        }
        for (int i = 0; i < chunk.size(); i++) {
            chunk.get(i).parseResult = futures.get(i).get();
        }
        StartupProfiler.endProbeAlways("CT parallel script source preload priority " + chunk.get(0).priority + " count " + chunk.size(), readStartedAt);

        if (canBatchChunk(context, chunk)) {
            completeParsedPlansBatch(context, chunk);
        } else {
            for (ScriptPlan plan : chunk) {
                publishScriptPre(context, plan);
                ParseResult earlyResult = prepareScriptForParse(context, plan);
                completeParsedPlan(context, plan, earlyResult != null ? earlyResult : plan.parseResult);
                plan.parseResult = null;
            }
        }
    }

    private static void processPlanSerial(LoadContext context, ScriptPlan plan) throws Exception {
        publishScriptPre(context, plan);
        ParseResult result = prepareScriptForParse(context, plan);
        if (result == null) {
            result = parseScript(context.bridge, context.environment, context.compileEnvironment, plan);
        }
        completeParsedPlan(context, plan, result);
    }

    private static ParseResult prepareScriptForParse(LoadContext context, ScriptPlan plan) throws Exception {
        long startedAt = beginDeepProbe();
        try {
            context.bridge.classNameGeneratorSetPrefix.invoke(
                    context.classNameGenerator,
                    context.bridge.scriptFileLoaderNamesConcatCapitalized.invoke(plan.script)
            );
            context.bridge.preprocessorManagerPostLoadEvent.invoke(
                    context.preprocessorManager,
                    context.bridge.crtScriptLoadEvent.newInstance(plan.script)
            );
        } finally {
            endDeepProbe("CT parallel prepare script", startedAt);
        }
        if (Boolean.TRUE.equals(context.bridge.scriptFileIsParsingBlocked.invoke(plan.script))) {
            return ParseResult.parsingBlocked();
        }
        return null;
    }

    private static ParseResult parseScript(Bridge bridge, Object environment, Object compileEnvironment, ScriptPlan plan) {
        Object lock = parseLock();
        synchronized (lock) {
            return parseScriptLocked(bridge, environment, compileEnvironment, plan);
        }
    }

    private static Object parseLock() {
        if (Launch.classLoader != null) {
            return Launch.classLoader;
        }
        ClassLoader loader = CraftTweakerScriptLoadOptimizations.class.getClassLoader();
        return loader == null ? CraftTweakerScriptLoadOptimizations.class : loader;
    }

    private static ParseResult parseScriptLocked(Bridge bridge, Object environment, Object compileEnvironment, ScriptPlan plan) {
        long startedAt = beginDeepProbe();
        Object tokener = null;
        try (Reader reader = new InputStreamReader(
                new BufferedInputStream((java.io.InputStream) bridge.scriptFileOpen.invoke(plan.script)),
                StandardCharsets.UTF_8)) {
            tokener = bridge.zenTokener.newInstance(
                    reader,
                    compileEnvironment,
                    plan.effectiveName,
                    bridge.scriptFileAreBracketErrorsIgnored.invoke(plan.script)
            );
            Object parsedFile = bridge.zenParsedFile.newInstance(
                    plan.effectiveName,
                    plan.extractedClassName,
                    tokener,
                    environment
            );
            return ParseResult.parsed(parsedFile);
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            return ParseResult.failure(cause, tokenPosition(bridge, tokener));
        } finally {
            endDeepProbe("CT parallel parse", startedAt);
        }
    }

    private static ParseResult readScriptSource(Bridge bridge, ScriptPlan plan) {
        long startedAt = beginDeepProbe();
        try (Reader reader = new InputStreamReader(
                new BufferedInputStream((java.io.InputStream) bridge.scriptFileOpen.invoke(plan.script)),
                StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            StringBuilder builder = new StringBuilder();
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return ParseResult.source(builder.toString());
        } catch (Throwable throwable) {
            return ParseResult.failure(unwrap(throwable), null);
        } finally {
            endDeepProbe("CT parallel source read", startedAt);
        }
    }

    private static ParseResult parseScriptSource(Bridge bridge, Object environment, Object compileEnvironment, ScriptPlan plan, String source) {
        Object tokener = null;
        try {
            tokener = bridge.zenTokenerString.newInstance(
                    source,
                    compileEnvironment,
                    plan.effectiveName,
                    bridge.scriptFileAreBracketErrorsIgnored.invoke(plan.script)
            );
            Object parsedFile = bridge.zenParsedFile.newInstance(
                    plan.effectiveName,
                    plan.extractedClassName,
                    tokener,
                    environment
            );
            return ParseResult.parsed(parsedFile);
        } catch (Throwable throwable) {
            return ParseResult.failure(unwrap(throwable), tokenPosition(bridge, tokener));
        }
    }

    private static void completeParsedPlan(LoadContext context, ScriptPlan plan, ParseResult result) throws Exception {
        if (result == null) {
            result = ParseResult.failure(new IllegalStateException("Missing CraftTweaker parse result"), null);
        }
        if (result.parsingBlocked) {
            publishScriptPost(context, plan);
            return;
        }
        if (result.throwable != null) {
            logParseFailure(context, plan, result);
            context.success = false;
            publishScriptPost(context, plan);
            return;
        }
        if (result.source != null) {
            long parseSourceStartedAt = beginDeepProbe();
            try {
                result = parseScriptSource(context.bridge, context.environment, context.compileEnvironment, plan, result.source);
                if (result.throwable != null) {
                    logParseFailure(context, plan, result);
                    context.success = false;
                    publishScriptPost(context, plan);
                    return;
                }
            } finally {
                endDeepProbe(context, "CT parallel parse source", parseSourceStartedAt);
            }
        }
        if (result.parsedFile == null
                || Boolean.TRUE.equals(context.bridge.scriptFileIsCompileBlocked.invoke(plan.script))
                || !context.success) {
            publishScriptPost(context, plan);
            return;
        }

        try {
            context.bridge.classNameGeneratorSetPrefix.invoke(
                    context.classNameGenerator,
                    context.bridge.scriptFileLoaderNamesConcatCapitalized.invoke(plan.script)
            );
            boolean scriptDebug = Boolean.TRUE.equals(context.bridge.scriptFileIsDebugEnabled.invoke(plan.script));
            boolean debug = scriptDebug
                    || (!SUPPRESS_GLOBAL_DEBUG_COMPILE_LOGS && Boolean.TRUE.equals(context.bridge.crtTweakerDebug.get(null)));
            long compileStartedAt = beginDeepProbe();
            try {
                context.bridge.zenModuleCompileScripts.invoke(
                        null,
                        plan.extractedClassName,
                        java.util.Collections.singletonList(result.parsedFile),
                        context.environment,
                        debug
                );
            } finally {
                endDeepProbe(context, "CT parallel compile", compileStartedAt);
            }
            if (Boolean.TRUE.equals(context.bridge.scriptFileIsExecutionBlocked.invoke(plan.script))
                    || context.forced
                    || context.syntaxCommand) {
                publishScriptPost(context, plan);
                return;
            }
            long moduleStartedAt = beginDeepProbe();
            Object module;
            try {
                module = context.bridge.zenModule.newInstance(
                        context.classes,
                        context.bridge.craftTweakerApiClass.getClassLoader()
                );
            } finally {
                endDeepProbe(context, "CT parallel module ctor", moduleStartedAt);
            }
            long mainStartedAt = beginDeepProbe();
            Runnable main;
            try {
                main = (Runnable) context.bridge.zenModuleGetMain.invoke(module);
            } finally {
                endDeepProbe(context, "CT parallel module getMain", mainStartedAt);
            }
            if (main != null) {
                long executeStartedAt = beginDeepProbe();
                try {
                    main.run();
                } finally {
                    endDeepProbe(context, "CT parallel execute main", executeStartedAt);
                }
            }
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            logError(context.bridge, "[" + plan.mainName + "]: Error executing" + plan.script + ":" + cause.getMessage(), cause);
        }
        publishScriptPost(context, plan);
    }

    private static void completeParsedPlansBatch(LoadContext context, List<ScriptPlan> chunk) throws Exception {
        List<ParsedPlan> parsedPlans = new ArrayList<>(chunk.size());
        boolean success = true;
        for (ScriptPlan plan : chunk) {
            publishScriptPre(context, plan);
            ParseResult result = prepareScriptForParse(context, plan);
            if (result == null) {
                result = plan.parseResult;
            }
            plan.parseResult = null;
            if (result == null) {
                result = ParseResult.failure(new IllegalStateException("Missing CraftTweaker parse result"), null);
            }
            if (result.parsingBlocked) {
                publishScriptPost(context, plan);
                continue;
            }
            if (result.throwable != null) {
                logParseFailure(context, plan, result);
                context.success = false;
                success = false;
                publishScriptPost(context, plan);
                continue;
            }
            if (result.source != null) {
                long parseSourceStartedAt = beginDeepProbe();
                try {
                    result = parseScriptSource(context.bridge, context.environment, context.compileEnvironment, plan, result.source);
                    if (result.throwable != null) {
                        logParseFailure(context, plan, result);
                        context.success = false;
                        success = false;
                        publishScriptPost(context, plan);
                        continue;
                    }
                } finally {
                    endDeepProbe(context, "CT parallel parse source", parseSourceStartedAt);
                }
            }
            if (result.parsedFile == null || Boolean.TRUE.equals(context.bridge.scriptFileIsCompileBlocked.invoke(plan.script))) {
                publishScriptPost(context, plan);
                continue;
            }
            parsedPlans.add(new ParsedPlan(plan, result.parsedFile));
        }

        if (!success || parsedPlans.isEmpty() || !context.success) {
            publishRemainingScriptPost(context, parsedPlans);
            return;
        }

        try {
            context.bridge.classNameGeneratorSetPrefix.invoke(
                    context.classNameGenerator,
                    context.bridge.scriptFileLoaderNamesConcatCapitalized.invoke(parsedPlans.get(0).plan.script)
            );
            boolean debug = shouldDebugBatch(context, parsedPlans);
            List parsedFiles = new ArrayList(parsedPlans.size());
            for (ParsedPlan parsedPlan : parsedPlans) {
                parsedFiles.add(parsedPlan.parsedFile);
            }
            long compileStartedAt = beginDeepProbe();
            try {
                context.bridge.zenModuleCompileScripts.invoke(
                        null,
                        parsedPlans.get(0).plan.extractedClassName,
                        parsedFiles,
                        context.environment,
                        debug
                );
            } finally {
                endDeepProbe(context, "CT parallel batch compile", compileStartedAt);
            }
            long moduleStartedAt = beginDeepProbe();
            Object module;
            try {
                module = context.bridge.zenModule.newInstance(
                        context.classes,
                        context.bridge.craftTweakerApiClass.getClassLoader()
                );
            } finally {
                endDeepProbe(context, "CT parallel batch module ctor", moduleStartedAt);
            }
            long mainStartedAt = beginDeepProbe();
            Runnable main;
            try {
                main = (Runnable) context.bridge.zenModuleGetMain.invoke(module);
            } finally {
                endDeepProbe(context, "CT parallel batch module getMain", mainStartedAt);
            }
            if (main != null) {
                long executeStartedAt = beginDeepProbe();
                try {
                    main.run();
                } finally {
                    endDeepProbe(context, "CT parallel batch execute main", executeStartedAt);
                }
            }
        } catch (Throwable throwable) {
            Throwable cause = unwrap(throwable);
            logError(context.bridge, "[" + parsedPlans.get(0).plan.mainName + "]: Error executing batched CraftTweaker scripts:" + cause.getMessage(), cause);
            context.success = false;
        }
        publishRemainingScriptPost(context, parsedPlans);
    }

    private static void publishRemainingScriptPost(LoadContext context, List<ParsedPlan> parsedPlans) throws Exception {
        for (ParsedPlan parsedPlan : parsedPlans) {
            publishScriptPost(context, parsedPlan.plan);
        }
    }

    private static boolean shouldDebugBatch(LoadContext context, List<ParsedPlan> parsedPlans) throws Exception {
        if (!SUPPRESS_GLOBAL_DEBUG_COMPILE_LOGS && Boolean.TRUE.equals(context.bridge.crtTweakerDebug.get(null))) {
            return true;
        }
        for (ParsedPlan parsedPlan : parsedPlans) {
            if (Boolean.TRUE.equals(context.bridge.scriptFileIsDebugEnabled.invoke(parsedPlan.plan.script))) {
                return true;
            }
        }
        return false;
    }

    private static boolean canBatchChunk(LoadContext context, List<ScriptPlan> chunk) throws Exception {
        if (!BATCH_ALLOWED_SCRIPTS || context.forced || context.syntaxCommand || chunk.size() < 2) {
            return false;
        }
        for (ScriptPlan plan : chunk) {
            if (Boolean.TRUE.equals(context.bridge.scriptFileIsExecutionBlocked.invoke(plan.script))) {
                return false;
            }
        }
        return true;
    }

    private static void finishLoad(LoadContext context, long elapsedMillis) throws Exception {
        if (context.errors != null) {
            Object globalErrors = context.bridge.globalRegistryGetErrors.invoke(null);
            context.errors.addAll((List) context.bridge.crtStoringErrorLoggerGetErrors.invoke(globalErrors));
            Method envGetErrors = context.environment.getClass().getMethod("getErrors");
            context.errors.addAll((List) envGetErrors.invoke(context.environment));
        }
        context.bridge.scriptLoaderSetLoaderStage.invoke(
                context.loader,
                context.success ? context.bridge.loaderStageLoadedSuccessful : context.bridge.loaderStageError
        );
        if (!context.syntaxCommand) {
            publish(context.bridge, context.bridge.crtTweakerFinishedEventList.get(context.tweaker),
                    context.bridge.loadingFinishedEvent.newInstance(context.loader, context.networkSide, context.forced));
        }
        logDefault(context.bridge, "Completed script loading in:" + elapsedMillis + "ms");
        logDeepMetrics(context, elapsedMillis);
    }

    private static void publishScriptPre(LoadContext context, ScriptPlan plan) throws Exception {
        if (!context.syntaxCommand) {
            long startedAt = beginDeepProbe();
            try {
                publish(context.bridge, context.bridge.crtTweakerScriptPreEventList.get(context.tweaker),
                        context.bridge.loadingScriptPreEvent.newInstance(plan.effectiveName));
            } finally {
                endDeepProbe(context, "CT parallel publish script pre", startedAt);
            }
        }
    }

    private static void publishScriptPost(LoadContext context, ScriptPlan plan) throws Exception {
        if (!context.syntaxCommand) {
            long startedAt = beginDeepProbe();
            try {
                publish(context.bridge, context.bridge.crtTweakerScriptPostEventList.get(context.tweaker),
                        context.bridge.loadingScriptPostEvent.newInstance(plan.effectiveName));
            } finally {
                endDeepProbe(context, "CT parallel publish script post", startedAt);
            }
        }
    }

    private static void logParseFailure(LoadContext context, ScriptPlan plan, ParseResult result) throws Exception {
        Throwable throwable = result.throwable;
        Bridge bridge = context.bridge;
        if (throwable instanceof IOException) {
            logError(bridge, context.descriptor + ": Could not load script" + plan.script + ":" + throwable.getMessage());
            return;
        }
        if (bridge.parseExceptionClass.isInstance(throwable)) {
            Object parsedFile = bridge.parseExceptionGetFile.invoke(throwable);
            String fileName = parsedFile != null
                    ? (String) bridge.zenParsedFileGetFileName.invoke(parsedFile)
                    : plan.effectiveName;
            int line = ((Integer) bridge.parseExceptionGetLine.invoke(throwable)).intValue();
            int offset = ((Integer) bridge.parseExceptionGetLineOffset.invoke(throwable)).intValue();
            String explanation = String.valueOf(bridge.parseExceptionGetExplanation.invoke(throwable));
            logError(bridge, context.descriptor + ": Error parsing" + fileName + ":" + line + " --" + explanation);
            addSingleError(context.errors, bridge, fileName, line, offset, explanation);
            return;
        }
        logError(bridge, context.descriptor + ": Error loading" + plan.script + ":" + throwable.toString(), throwable);
        if (result.position != null) {
            addSingleError(context.errors, bridge, result.position.fileName, result.position.line, result.position.offset, "Generic ERROR");
        }
    }

    private static void addSingleError(List errors, Bridge bridge, String fileName, int line, int offset, String explanation) throws Exception {
        if (errors == null) {
            return;
        }
        errors.add(bridge.singleError.newInstance(fileName, line, offset, explanation, bridge.singleErrorLevelError));
    }

    private static void publish(Bridge bridge, Object eventList, Object event) throws Exception {
        bridge.eventListPublish.invoke(eventList, event);
    }

    private static String descriptor(Bridge bridge, Object tweaker, String mainName, Object networkSide) {
        try {
            return (String) bridge.crtTweakerGetTweakerDescriptor.invoke(tweaker, mainName);
        } catch (Throwable ignored) {
            return "[" + mainName + " | " + networkSide + "]";
        }
    }

    private static boolean parallelAllowed(String effectiveName, String groupName, String name) {
        if (!matches(ALLOWLIST, effectiveName, groupName, name)) {
            return false;
        }
        return !matches(DENYLIST, effectiveName, groupName, name);
    }

    private static boolean matches(Set<String> filter, String effectiveName, String groupName, String name) {
        if (filter.isEmpty()) {
            return false;
        }
        if (filter.contains("*")) {
            return true;
        }
        String effective = normalize(effectiveName);
        String group = normalize(groupName);
        String scriptName = normalize(name);
        String effectiveNoExt = stripZenExtension(effective);
        String groupNoExt = stripZenExtension(group);
        String nameNoExt = stripZenExtension(scriptName);
        return filter.contains(effective)
                || filter.contains(group)
                || filter.contains(scriptName)
                || filter.contains(effectiveNoExt)
                || filter.contains(groupNoExt)
                || filter.contains(nameNoExt);
    }

    private static String stripZenExtension(String value) {
        if (value.endsWith(".zs")) {
            return value.substring(0, value.length() - 3);
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static int workerCount(List<ScriptPlan> plans) {
        int allowed = 0;
        for (ScriptPlan plan : plans) {
            if (plan.parallelAllowed) {
                allowed++;
            }
        }
        if (allowed < 2) {
            return 1;
        }
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        if (CONFIGURED_WORKERS > 0) {
            return Math.max(1, Math.min(CONFIGURED_WORKERS, Math.max(1, processors - 1)));
        }
        return Math.max(1, Math.min(4, Math.max(1, processors / 2)));
    }

    private static Bridge bridge() {
        Bridge current = bridge;
        if (current != null) {
            return current;
        }
        try {
            current = Bridge.create();
            bridge = current;
            return current;
        } catch (Throwable throwable) {
            logBridgeFailure("CraftTweaker parallel script parser bridge initialization failed; using stock loadScript", throwable);
            return null;
        }
    }

    private static void logInfo(Bridge bridge, String message) throws Exception {
        bridge.craftTweakerApiLogInfo.invoke(null, message);
    }

    private static void logDefault(Bridge bridge, String message) throws Exception {
        bridge.craftTweakerApiLogDefault.invoke(null, message);
    }

    private static void logWarning(Bridge bridge, String message) throws Exception {
        bridge.craftTweakerApiLogWarning.invoke(null, message);
    }

    private static void logError(Bridge bridge, String message) throws Exception {
        bridge.craftTweakerApiLogError.invoke(null, message);
    }

    private static void logError(Bridge bridge, String message, Throwable throwable) throws Exception {
        bridge.craftTweakerApiLogErrorThrowable.invoke(null, message, throwable);
    }

    private static void logBridgeFailure(String message, Throwable throwable) {
        if (BRIDGE_FAILURE_LOGGED.compareAndSet(false, true)) {
            GPOM.LOGGER.warn(message, throwable);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof java.lang.reflect.InvocationTargetException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private static TokenPosition tokenPosition(Bridge bridge, Object tokener) {
        if (tokener == null || bridge.zenTokenerGetFile == null) {
            return null;
        }
        try {
            Object parsedFile = bridge.zenTokenerGetFile.invoke(tokener);
            String fileName = parsedFile != null
                    ? (String) bridge.zenParsedFileGetFileName.invoke(parsedFile)
                    : "";
            int line = ((Integer) bridge.zenTokenerGetLine.invoke(tokener)).intValue();
            int offset = ((Integer) bridge.zenTokenerGetLineOffset.invoke(tokener)).intValue();
            return new TokenPosition(fileName, line, offset);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Set<String> setProperty(String key, String fallback) {
        String raw = System.getProperty(key, fallback);
        Set<String> values = new HashSet<>();
        for (String part : raw.split(",")) {
            String value = normalize(part);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private static int intProperty(String key, int fallback) {
        String raw = System.getProperty(key);
        if (raw == null || raw.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long beginDeepProbe() {
        return DEEP_PROBES ? StartupProfiler.beginProbe() : 0L;
    }

    private static void endDeepProbe(String label, long startedAt) {
        if (DEEP_PROBES) {
            StartupProfiler.endProbeAlways(label, startedAt);
        }
    }

    private static void endDeepProbe(LoadContext context, String label, long startedAt) {
        if (!DEEP_PROBES || startedAt == 0L) {
            return;
        }
        long elapsed = System.nanoTime() - startedAt;
        if (context != null) {
            context.recordMetric(label, elapsed);
        }
        StartupProfiler.endProbeAlways(label, startedAt);
    }

    private static void logDeepMetrics(LoadContext context, long elapsedMillis) {
        if (!DEEP_PROBES || context.metrics.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder(256);
        builder.append("CraftTweaker deep load metrics descriptor=")
                .append(context.descriptor)
                .append(" scripts=")
                .append(context.planCount)
                .append(" success=")
                .append(context.success)
                .append(" elapsed=")
                .append(elapsedMillis)
                .append(" ms");
        synchronized (context.metrics) {
            for (Map.Entry<String, Metric> entry : context.metrics.entrySet()) {
                Metric metric = entry.getValue();
                builder.append("; ")
                        .append(entry.getKey())
                        .append("=")
                        .append(formatNanos(metric.totalNanos))
                        .append(" ms max=")
                        .append(formatNanos(metric.maxNanos))
                        .append(" count=")
                        .append(metric.count);
            }
        }
        GPOM.LOGGER.info(builder.toString());
    }

    private static String formatNanos(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
    }

    private static final class ParseTask implements Callable<ParseResult> {
        private final Bridge bridge;
        private final Object environment;
        private final Object compileEnvironment;
        private final ScriptPlan plan;

        private ParseTask(Bridge bridge, Object environment, Object compileEnvironment, ScriptPlan plan) {
            this.bridge = bridge;
            this.environment = environment;
            this.compileEnvironment = compileEnvironment;
            this.plan = plan;
        }

        @Override
        public ParseResult call() {
            return parseScript(bridge, environment, compileEnvironment, plan);
        }
    }

    private static final class ReadTask implements Callable<ParseResult> {
        private final Bridge bridge;
        private final ScriptPlan plan;

        private ReadTask(Bridge bridge, ScriptPlan plan) {
            this.bridge = bridge;
            this.plan = plan;
        }

        @Override
        public ParseResult call() {
            return readScriptSource(bridge, plan);
        }
    }

    private static final class LoadContext {
        private final Bridge bridge;
        private final Object tweaker;
        private final Object loader;
        private final List errors;
        private final boolean forced;
        private final boolean syntaxCommand;
        private final Object networkSide;
        private final Object preprocessorManager;
        private final Object environment;
        private final Object compileEnvironment;
        private final Object classNameGenerator;
        private final Map classes;
        private final String descriptor;
        private final ExecutorService executor;
        private final Map<String, Metric> metrics = new LinkedHashMap<>();
        private int planCount;
        private boolean success = true;

        private LoadContext(Bridge bridge,
                            Object tweaker,
                            Object loader,
                            List errors,
                            boolean forced,
                            boolean syntaxCommand,
                            Object networkSide,
                            Object preprocessorManager,
                            Object environment,
                            Object compileEnvironment,
                            Object classNameGenerator,
                            Map classes,
                            String descriptor,
                            ExecutorService executor) {
            this.bridge = bridge;
            this.tweaker = tweaker;
            this.loader = loader;
            this.errors = errors;
            this.forced = forced;
            this.syntaxCommand = syntaxCommand;
            this.networkSide = networkSide;
            this.preprocessorManager = preprocessorManager;
            this.environment = environment;
            this.compileEnvironment = compileEnvironment;
            this.classNameGenerator = classNameGenerator;
            this.classes = classes;
            this.descriptor = descriptor;
            this.executor = executor;
        }

        private void recordMetric(String label, long elapsedNanos) {
            synchronized (metrics) {
                Metric metric = metrics.get(label);
                if (metric == null) {
                    metric = new Metric();
                    metrics.put(label, metric);
                }
                metric.add(elapsedNanos);
            }
        }
    }

    private static final class Metric {
        private long totalNanos;
        private long maxNanos;
        private int count;

        private void add(long elapsedNanos) {
            totalNanos += elapsedNanos;
            if (elapsedNanos > maxNanos) {
                maxNanos = elapsedNanos;
            }
            count++;
        }
    }

    private static final class ScriptPlan {
        private final Object script;
        private final String effectiveName;
        private final String extractedClassName;
        private final int priority;
        private final String groupName;
        private final String name;
        private final String mainName;
        private final boolean parallelAllowed;
        private ParseResult parseResult;

        private ScriptPlan(Object script,
                           String effectiveName,
                           String extractedClassName,
                           int priority,
                           String groupName,
                           String name,
                           String mainName,
                           boolean parallelAllowed) {
            this.script = script;
            this.effectiveName = effectiveName;
            this.extractedClassName = extractedClassName;
            this.priority = priority;
            this.groupName = groupName;
            this.name = name;
            this.mainName = mainName;
            this.parallelAllowed = parallelAllowed;
        }
    }

    private static final class ParsedPlan {
        private final ScriptPlan plan;
        private final Object parsedFile;

        private ParsedPlan(ScriptPlan plan, Object parsedFile) {
            this.plan = plan;
            this.parsedFile = parsedFile;
        }
    }

    private static final class ParseResult {
        private final Object parsedFile;
        private final String source;
        private final Throwable throwable;
        private final TokenPosition position;
        private final boolean parsingBlocked;

        private ParseResult(Object parsedFile, String source, Throwable throwable, TokenPosition position, boolean parsingBlocked) {
            this.parsedFile = parsedFile;
            this.source = source;
            this.throwable = throwable;
            this.position = position;
            this.parsingBlocked = parsingBlocked;
        }

        private static ParseResult parsed(Object parsedFile) {
            return new ParseResult(parsedFile, null, null, null, false);
        }

        private static ParseResult source(String source) {
            return new ParseResult(null, source, null, null, false);
        }

        private static ParseResult failure(Throwable throwable, TokenPosition position) {
            return new ParseResult(null, null, throwable, position, false);
        }

        private static ParseResult parsingBlocked() {
            return new ParseResult(null, null, null, null, true);
        }
    }

    private static final class TokenPosition {
        private final String fileName;
        private final int line;
        private final int offset;

        private TokenPosition(String fileName, int line, int offset) {
            this.fileName = fileName;
            this.line = line;
            this.offset = offset;
        }
    }

    private static final class CtScriptThreadFactory implements ThreadFactory {
        private final AtomicInteger nextId = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GPOM CT Script Parser - " + nextId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class Bridge {
        private final Class<?> craftTweakerApiClass;
        private final Method craftTweakerApiLogInfo;
        private final Method craftTweakerApiLogDefault;
        private final Method craftTweakerApiLogWarning;
        private final Method craftTweakerApiLogError;
        private final Method craftTweakerApiLogErrorThrowable;
        private final Method craftTweakerApiSetSuppressErrorFlag;
        private final Object suppressErrorForced;
        private final Field crtTweakerNetworkSide;
        private final Field crtTweakerPreprocessorManager;
        private final Field crtTweakerStartedEventList;
        private final Field crtTweakerFinishedEventList;
        private final Field crtTweakerScriptPreEventList;
        private final Field crtTweakerScriptPostEventList;
        private final Field crtTweakerDebug;
        private final Method crtTweakerLoadPreprocessor;
        private final Method crtTweakerGetTweakerDescriptor;
        private final Method scriptLoaderIsLoaded;
        private final Method scriptLoaderIsDelayed;
        private final Method scriptLoaderGetLoaderStage;
        private final Method scriptLoaderSetLoaderStage;
        private final Method scriptLoaderGetMainName;
        private final Method scriptLoaderCanExecute;
        private final Object loaderStageInvalidated;
        private final Object loaderStageLoading;
        private final Object loaderStageLoadedSuccessful;
        private final Object loaderStageError;
        private final Method preprocessorManagerClean;
        private final Method preprocessorManagerPostLoadEvent;
        private final Constructor<?> crtScriptLoadEvent;
        private final Constructor<?> loadingStartedEvent;
        private final Constructor<?> loadingFinishedEvent;
        private final Constructor<?> loadingScriptPreEvent;
        private final Constructor<?> loadingScriptPostEvent;
        private final Method eventListPublish;
        private final Method scriptFileGetLoaderNames;
        private final Method scriptFileLoaderNamesConcatCapitalized;
        private final Method scriptFileShouldBeLoadedOn;
        private final Method scriptFileGetEffectiveName;
        private final Method scriptFileGetPriority;
        private final Method scriptFileGetGroupName;
        private final Method scriptFileGetName;
        private final Method scriptFileOpen;
        private final Method scriptFileIsParsingBlocked;
        private final Method scriptFileAreBracketErrorsIgnored;
        private final Method scriptFileIsCompileBlocked;
        private final Method scriptFileIsDebugEnabled;
        private final Method scriptFileIsExecutionBlocked;
        private final Method globalRegistryGetErrors;
        private final Method globalRegistryMakeGlobalEnvironment;
        private final Method crtStoringErrorLoggerClear;
        private final Method crtStoringErrorLoggerGetErrors;
        private final Method environmentGetEnvironment;
        private final Method environmentGetClassNameGenerator;
        private final Method classNameGeneratorSetPrefix;
        private final Constructor<?> zenTokener;
        private final Constructor<?> zenTokenerString;
        private final Method zenTokenerGetFile;
        private final Method zenTokenerGetLine;
        private final Method zenTokenerGetLineOffset;
        private final Constructor<?> zenParsedFile;
        private final Method zenParsedFileGetFileName;
        private final Constructor<?> zenModule;
        private final Method zenModuleExtractClassName;
        private final Method zenModuleCompileScripts;
        private final Method zenModuleGetMain;
        private final Class<?> parseExceptionClass;
        private final Method parseExceptionGetFile;
        private final Method parseExceptionGetLine;
        private final Method parseExceptionGetLineOffset;
        private final Method parseExceptionGetExplanation;
        private final Constructor<?> singleError;
        private final Object singleErrorLevelError;

        private Bridge(Class<?> craftTweakerApiClass,
                       Method craftTweakerApiLogInfo,
                       Method craftTweakerApiLogDefault,
                       Method craftTweakerApiLogWarning,
                       Method craftTweakerApiLogError,
                       Method craftTweakerApiLogErrorThrowable,
                       Method craftTweakerApiSetSuppressErrorFlag,
                       Object suppressErrorForced,
                       Field crtTweakerNetworkSide,
                       Field crtTweakerPreprocessorManager,
                       Field crtTweakerStartedEventList,
                       Field crtTweakerFinishedEventList,
                       Field crtTweakerScriptPreEventList,
                       Field crtTweakerScriptPostEventList,
                       Field crtTweakerDebug,
                       Method crtTweakerLoadPreprocessor,
                       Method crtTweakerGetTweakerDescriptor,
                       Method scriptLoaderIsLoaded,
                       Method scriptLoaderIsDelayed,
                       Method scriptLoaderGetLoaderStage,
                       Method scriptLoaderSetLoaderStage,
                       Method scriptLoaderGetMainName,
                       Method scriptLoaderCanExecute,
                       Object loaderStageInvalidated,
                       Object loaderStageLoading,
                       Object loaderStageLoadedSuccessful,
                       Object loaderStageError,
                       Method preprocessorManagerClean,
                       Method preprocessorManagerPostLoadEvent,
                       Constructor<?> crtScriptLoadEvent,
                       Constructor<?> loadingStartedEvent,
                       Constructor<?> loadingFinishedEvent,
                       Constructor<?> loadingScriptPreEvent,
                       Constructor<?> loadingScriptPostEvent,
                       Method eventListPublish,
                       Method scriptFileGetLoaderNames,
                       Method scriptFileLoaderNamesConcatCapitalized,
                       Method scriptFileShouldBeLoadedOn,
                       Method scriptFileGetEffectiveName,
                       Method scriptFileGetPriority,
                       Method scriptFileGetGroupName,
                       Method scriptFileGetName,
                       Method scriptFileOpen,
                       Method scriptFileIsParsingBlocked,
                       Method scriptFileAreBracketErrorsIgnored,
                       Method scriptFileIsCompileBlocked,
                       Method scriptFileIsDebugEnabled,
                       Method scriptFileIsExecutionBlocked,
                       Method globalRegistryGetErrors,
                       Method globalRegistryMakeGlobalEnvironment,
                       Method crtStoringErrorLoggerClear,
                       Method crtStoringErrorLoggerGetErrors,
                       Method environmentGetEnvironment,
                       Method environmentGetClassNameGenerator,
                       Method classNameGeneratorSetPrefix,
                       Constructor<?> zenTokener,
                       Constructor<?> zenTokenerString,
                       Method zenTokenerGetFile,
                       Method zenTokenerGetLine,
                       Method zenTokenerGetLineOffset,
                       Constructor<?> zenParsedFile,
                       Method zenParsedFileGetFileName,
                       Constructor<?> zenModule,
                       Method zenModuleExtractClassName,
                       Method zenModuleCompileScripts,
                       Method zenModuleGetMain,
                       Class<?> parseExceptionClass,
                       Method parseExceptionGetFile,
                       Method parseExceptionGetLine,
                       Method parseExceptionGetLineOffset,
                       Method parseExceptionGetExplanation,
                       Constructor<?> singleError,
                       Object singleErrorLevelError) {
            this.craftTweakerApiClass = craftTweakerApiClass;
            this.craftTweakerApiLogInfo = craftTweakerApiLogInfo;
            this.craftTweakerApiLogDefault = craftTweakerApiLogDefault;
            this.craftTweakerApiLogWarning = craftTweakerApiLogWarning;
            this.craftTweakerApiLogError = craftTweakerApiLogError;
            this.craftTweakerApiLogErrorThrowable = craftTweakerApiLogErrorThrowable;
            this.craftTweakerApiSetSuppressErrorFlag = craftTweakerApiSetSuppressErrorFlag;
            this.suppressErrorForced = suppressErrorForced;
            this.crtTweakerNetworkSide = crtTweakerNetworkSide;
            this.crtTweakerPreprocessorManager = crtTweakerPreprocessorManager;
            this.crtTweakerStartedEventList = crtTweakerStartedEventList;
            this.crtTweakerFinishedEventList = crtTweakerFinishedEventList;
            this.crtTweakerScriptPreEventList = crtTweakerScriptPreEventList;
            this.crtTweakerScriptPostEventList = crtTweakerScriptPostEventList;
            this.crtTweakerDebug = crtTweakerDebug;
            this.crtTweakerLoadPreprocessor = crtTweakerLoadPreprocessor;
            this.crtTweakerGetTweakerDescriptor = crtTweakerGetTweakerDescriptor;
            this.scriptLoaderIsLoaded = scriptLoaderIsLoaded;
            this.scriptLoaderIsDelayed = scriptLoaderIsDelayed;
            this.scriptLoaderGetLoaderStage = scriptLoaderGetLoaderStage;
            this.scriptLoaderSetLoaderStage = scriptLoaderSetLoaderStage;
            this.scriptLoaderGetMainName = scriptLoaderGetMainName;
            this.scriptLoaderCanExecute = scriptLoaderCanExecute;
            this.loaderStageInvalidated = loaderStageInvalidated;
            this.loaderStageLoading = loaderStageLoading;
            this.loaderStageLoadedSuccessful = loaderStageLoadedSuccessful;
            this.loaderStageError = loaderStageError;
            this.preprocessorManagerClean = preprocessorManagerClean;
            this.preprocessorManagerPostLoadEvent = preprocessorManagerPostLoadEvent;
            this.crtScriptLoadEvent = crtScriptLoadEvent;
            this.loadingStartedEvent = loadingStartedEvent;
            this.loadingFinishedEvent = loadingFinishedEvent;
            this.loadingScriptPreEvent = loadingScriptPreEvent;
            this.loadingScriptPostEvent = loadingScriptPostEvent;
            this.eventListPublish = eventListPublish;
            this.scriptFileGetLoaderNames = scriptFileGetLoaderNames;
            this.scriptFileLoaderNamesConcatCapitalized = scriptFileLoaderNamesConcatCapitalized;
            this.scriptFileShouldBeLoadedOn = scriptFileShouldBeLoadedOn;
            this.scriptFileGetEffectiveName = scriptFileGetEffectiveName;
            this.scriptFileGetPriority = scriptFileGetPriority;
            this.scriptFileGetGroupName = scriptFileGetGroupName;
            this.scriptFileGetName = scriptFileGetName;
            this.scriptFileOpen = scriptFileOpen;
            this.scriptFileIsParsingBlocked = scriptFileIsParsingBlocked;
            this.scriptFileAreBracketErrorsIgnored = scriptFileAreBracketErrorsIgnored;
            this.scriptFileIsCompileBlocked = scriptFileIsCompileBlocked;
            this.scriptFileIsDebugEnabled = scriptFileIsDebugEnabled;
            this.scriptFileIsExecutionBlocked = scriptFileIsExecutionBlocked;
            this.globalRegistryGetErrors = globalRegistryGetErrors;
            this.globalRegistryMakeGlobalEnvironment = globalRegistryMakeGlobalEnvironment;
            this.crtStoringErrorLoggerClear = crtStoringErrorLoggerClear;
            this.crtStoringErrorLoggerGetErrors = crtStoringErrorLoggerGetErrors;
            this.environmentGetEnvironment = environmentGetEnvironment;
            this.environmentGetClassNameGenerator = environmentGetClassNameGenerator;
            this.classNameGeneratorSetPrefix = classNameGeneratorSetPrefix;
            this.zenTokener = zenTokener;
            this.zenTokenerString = zenTokenerString;
            this.zenTokenerGetFile = zenTokenerGetFile;
            this.zenTokenerGetLine = zenTokenerGetLine;
            this.zenTokenerGetLineOffset = zenTokenerGetLineOffset;
            this.zenParsedFile = zenParsedFile;
            this.zenParsedFileGetFileName = zenParsedFileGetFileName;
            this.zenModule = zenModule;
            this.zenModuleExtractClassName = zenModuleExtractClassName;
            this.zenModuleCompileScripts = zenModuleCompileScripts;
            this.zenModuleGetMain = zenModuleGetMain;
            this.parseExceptionClass = parseExceptionClass;
            this.parseExceptionGetFile = parseExceptionGetFile;
            this.parseExceptionGetLine = parseExceptionGetLine;
            this.parseExceptionGetLineOffset = parseExceptionGetLineOffset;
            this.parseExceptionGetExplanation = parseExceptionGetExplanation;
            this.singleError = singleError;
            this.singleErrorLevelError = singleErrorLevelError;
        }

        private static Bridge create() throws Exception {
            Class<?> craftTweakerApiClass = Class.forName("crafttweaker.CraftTweakerAPI");
            Class<?> suppressErrorFlagClass = Class.forName("crafttweaker.util.SuppressErrorFlag");
            Class<?> crtTweakerClass = Class.forName("crafttweaker.runtime.CrTTweaker");
            Class<?> scriptLoaderClass = Class.forName("crafttweaker.runtime.ScriptLoader");
            Class<?> loaderStageClass = Class.forName("crafttweaker.runtime.ScriptLoader$LoaderStage");
            Class<?> preprocessorManagerClass = Class.forName("crafttweaker.preprocessor.PreprocessorManager");
            Class<?> scriptFileClass = Class.forName("crafttweaker.runtime.ScriptFile");
            Class<?> networkSideClass = Class.forName("crafttweaker.api.network.NetworkSide");
            Class<?> eventListClass = Class.forName("crafttweaker.util.EventList");
            Class<?> crtScriptLoadEventClass = Class.forName("crafttweaker.preprocessor.CrTScriptLoadEvent");
            Class<?> loadingStartedEventClass = Class.forName("crafttweaker.runtime.events.CrTLoadingStartedEvent");
            Class<?> loadingFinishedEventClass = Class.forName("crafttweaker.runtime.events.CrTLoaderLoadingEvent$Finished");
            Class<?> loadingScriptPreEventClass = Class.forName("crafttweaker.runtime.events.CrTLoadingScriptEventPre");
            Class<?> loadingScriptPostEventClass = Class.forName("crafttweaker.runtime.events.CrTLoadingScriptEventPost");
            Class<?> globalRegistryClass = Class.forName("crafttweaker.zenscript.GlobalRegistry");
            Class<?> crtStoringErrorLoggerClass = Class.forName("crafttweaker.zenscript.CrtStoringErrorLogger");
            Class<?> environmentGlobalClass = Class.forName("stanhebben.zenscript.compiler.IEnvironmentGlobal");
            Class<?> zenCompileEnvironmentClass = Class.forName("stanhebben.zenscript.IZenCompileEnvironment");
            Class<?> classNameGeneratorClass = Class.forName("stanhebben.zenscript.compiler.ClassNameGenerator");
            Class<?> zenTokenerClass = Class.forName("stanhebben.zenscript.ZenTokener");
            Class<?> zenParsedFileClass = Class.forName("stanhebben.zenscript.ZenParsedFile");
            Class<?> zenModuleClass = Class.forName("stanhebben.zenscript.ZenModule");
            Class<?> parseExceptionClass = Class.forName("stanhebben.zenscript.parser.ParseException");
            Class<?> singleErrorClass = Class.forName("crafttweaker.socket.SingleError");
            Class<?> singleErrorLevelClass = Class.forName("crafttweaker.socket.SingleError$Level");

            Method craftTweakerApiLogInfo = craftTweakerApiClass.getMethod("logInfo", String.class);
            Method craftTweakerApiLogDefault = craftTweakerApiClass.getMethod("logDefault", String.class);
            Method craftTweakerApiLogWarning = craftTweakerApiClass.getMethod("logWarning", String.class);
            Method craftTweakerApiLogError = craftTweakerApiClass.getMethod("logError", String.class);
            Method craftTweakerApiLogErrorThrowable = craftTweakerApiClass.getMethod("logError", String.class, Throwable.class);
            Method craftTweakerApiSetSuppressErrorFlag = craftTweakerApiClass.getMethod("setSuppressErrorFlag", suppressErrorFlagClass);
            Object suppressErrorForced = enumConstant(suppressErrorFlagClass, "FORCED");

            Field networkSide = field(crtTweakerClass, "networkSide");
            Field preprocessorManager = field(crtTweakerClass, "preprocessorManager");
            Field startedEventList = field(crtTweakerClass, "CRT_LOADING_STARTED_EVENT_EVENT_LIST");
            Field finishedEventList = field(crtTweakerClass, "CRT_LOADING_FINISHED_EVENT_EVENT_LIST");
            Field scriptPreEventList = field(crtTweakerClass, "CRT_LOADING_SCRIPT_PRE_EVENT_LIST");
            Field scriptPostEventList = field(crtTweakerClass, "CRT_LOADING_SCRIPT_POST_EVENT_LIST");
            Field debug = field(crtTweakerClass, "DEBUG");

            Method loadPreprocessor = method(crtTweakerClass, "loadPreprocessor", boolean.class);
            Method getTweakerDescriptor = method(crtTweakerClass, "getTweakerDescriptor", String.class);
            Method scriptLoaderSetLoaderStage = scriptLoaderClass.getMethod("setLoaderStage", loaderStageClass);

            Method zenTokenerGetFile = optionalMethod(zenTokenerClass, "getFile");
            Method zenTokenerGetLine = optionalMethod(zenTokenerClass, "getLine");
            Method zenTokenerGetLineOffset = optionalMethod(zenTokenerClass, "getLineOffset");

            return new Bridge(
                    craftTweakerApiClass,
                    craftTweakerApiLogInfo,
                    craftTweakerApiLogDefault,
                    craftTweakerApiLogWarning,
                    craftTweakerApiLogError,
                    craftTweakerApiLogErrorThrowable,
                    craftTweakerApiSetSuppressErrorFlag,
                    suppressErrorForced,
                    networkSide,
                    preprocessorManager,
                    startedEventList,
                    finishedEventList,
                    scriptPreEventList,
                    scriptPostEventList,
                    debug,
                    loadPreprocessor,
                    getTweakerDescriptor,
                    scriptLoaderClass.getMethod("isLoaded"),
                    scriptLoaderClass.getMethod("isDelayed"),
                    scriptLoaderClass.getMethod("getLoaderStage"),
                    scriptLoaderSetLoaderStage,
                    scriptLoaderClass.getMethod("getMainName"),
                    scriptLoaderClass.getMethod("canExecute", String[].class),
                    enumConstant(loaderStageClass, "INVALIDATED"),
                    enumConstant(loaderStageClass, "LOADING"),
                    enumConstant(loaderStageClass, "LOADED_SUCCESSFUL"),
                    enumConstant(loaderStageClass, "ERROR"),
                    preprocessorManagerClass.getMethod("clean"),
                    preprocessorManagerClass.getMethod("postLoadEvent", crtScriptLoadEventClass),
                    crtScriptLoadEventClass.getConstructor(scriptFileClass),
                    loadingStartedEventClass.getConstructor(scriptLoaderClass, boolean.class, networkSideClass),
                    loadingFinishedEventClass.getConstructor(scriptLoaderClass, networkSideClass, boolean.class),
                    loadingScriptPreEventClass.getConstructor(String.class),
                    loadingScriptPostEventClass.getConstructor(String.class),
                    eventListClass.getMethod("publish", Object.class),
                    scriptFileClass.getMethod("getLoaderNames"),
                    scriptFileClass.getMethod("loaderNamesConcatCapitalized"),
                    scriptFileClass.getMethod("shouldBeLoadedOn", networkSideClass),
                    scriptFileClass.getMethod("getEffectiveName"),
                    scriptFileClass.getMethod("getPriority"),
                    scriptFileClass.getMethod("getGroupName"),
                    scriptFileClass.getMethod("getName"),
                    scriptFileClass.getMethod("open"),
                    scriptFileClass.getMethod("isParsingBlocked"),
                    scriptFileClass.getMethod("areBracketErrorsIgnored"),
                    scriptFileClass.getMethod("isCompileBlocked"),
                    scriptFileClass.getMethod("isDebugEnabled"),
                    scriptFileClass.getMethod("isExecutionBlocked"),
                    globalRegistryClass.getMethod("getErrors"),
                    globalRegistryClass.getMethod("makeGlobalEnvironment", Map.class, String.class),
                    crtStoringErrorLoggerClass.getMethod("clear"),
                    crtStoringErrorLoggerClass.getMethod("getErrors"),
                    environmentGlobalClass.getMethod("getEnvironment"),
                    environmentGlobalClass.getMethod("getClassNameGenerator"),
                    classNameGeneratorClass.getMethod("setPrefix", String.class),
                    zenTokenerClass.getConstructor(Reader.class, zenCompileEnvironmentClass, String.class, boolean.class),
                    zenTokenerClass.getConstructor(String.class, zenCompileEnvironmentClass, String.class, boolean.class),
                    zenTokenerGetFile,
                    zenTokenerGetLine,
                    zenTokenerGetLineOffset,
                    zenParsedFileClass.getConstructor(String.class, String.class, zenTokenerClass, environmentGlobalClass),
                    zenParsedFileClass.getMethod("getFileName"),
                    zenModuleClass.getConstructor(Map.class, ClassLoader.class),
                    zenModuleClass.getMethod("extractClassName", String.class),
                    zenModuleClass.getMethod("compileScripts", String.class, List.class, environmentGlobalClass, boolean.class),
                    zenModuleClass.getMethod("getMain"),
                    parseExceptionClass,
                    parseExceptionClass.getMethod("getFile"),
                    parseExceptionClass.getMethod("getLine"),
                    parseExceptionClass.getMethod("getLineOffset"),
                    parseExceptionClass.getMethod("getExplanation"),
                    singleErrorClass.getConstructor(String.class, int.class, int.class, String.class, singleErrorLevelClass),
                    enumConstant(singleErrorLevelClass, "ERROR")
            );
        }

        private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }

        private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        }

        private static Method optionalMethod(Class<?> owner, String name) {
            try {
                return owner.getMethod(name);
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }

        private static Object enumConstant(Class<?> enumClass, String name) {
            return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name);
        }
    }
}
