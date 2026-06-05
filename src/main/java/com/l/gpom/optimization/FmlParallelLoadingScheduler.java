package com.l.gpom.optimization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.l.gpom.GPOM;
import com.l.gpom.client.EarlySplashWindow;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLStateEvent;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import org.apache.logging.log4j.ThreadContext;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FmlParallelLoadingScheduler {
    private static final Object MOD_STATE_LOCK = new Object();
    private static final Object PROGRESS_LOCK = new Object();
    private static final int PROGRESS_LOG_INTERVAL = 25;
    private static final long MIB = 1048576L;

    private FmlParallelLoadingScheduler() {
    }

    public static boolean shouldParallelize(FMLEvent event) {
        return (event instanceof FMLConstructionEvent && GpomEarlyConfig.parallelConstructEnabled())
                || (event instanceof FMLPreInitializationEvent && GpomEarlyConfig.parallelPreInitEnabled())
                || (event instanceof FMLPostInitializationEvent && GpomEarlyConfig.parallelPostInitEnabled())
                || (event instanceof FMLInitializationEvent && GpomEarlyConfig.parallelInitEnabled())
                || (event instanceof FMLLoadCompleteEvent && GpomEarlyConfig.parallelLoadCompleteEnabled());
    }

    public static void propagate(FMLEvent event, List<ModContainer> activeModList,
                                 ImmutableMap<String, EventBus> eventChannels,
                                 Multimap<String, LoaderState.ModState> modStates) {
        Set<String> parallelMods = parallelMods(event);
        Set<String> deniedMods = deniedMods(event);
        boolean continueOnModError = continueOnModError(event);
        boolean serialHandlersAreBarriers = serialHandlersAreBarriers(event);
        if (parallelMods.isEmpty()) {
            return;
        }

        long countStartedAt = StartupProfiler.beginProbe();
        int parallelEligibleHandlers;
        try {
            parallelEligibleHandlers = countParallelAllowed(activeModList, parallelMods, deniedMods);
        } finally {
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " countParallelAllowed", countStartedAt);
        }
        int requestedWorkers = requestedWorkers(event);
        int workers = Math.min(requestedWorkers, Math.max(1, parallelEligibleHandlers));
        long executorStartedAt = StartupProfiler.beginProbe();
        ExecutorService executor;
        CompletionService<DispatchResult> completionService;
        try {
            executor = Executors.newFixedThreadPool(workers, runnable -> {
                Thread thread = new Thread(runnable, "GPOM FML parallel loader");
                thread.setDaemon(true);
                return thread;
            });
            completionService = new ExecutorCompletionService<>(executor);
        } finally {
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " executorSetup", executorStartedAt);
        }

        long progressStartedAt = StartupProfiler.beginProbe();
        ProgressManager.ProgressBar progress;
        try {
            progress = ProgressManager.push(
                    "GPOM " + phaseDisplayName(event),
                    activeModList.size(),
                    true
            );
        } finally {
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " progressSetup", progressStartedAt);
        }

        long startedAt = System.nanoTime();
        int parallelHandlers = 0;
        ProgressState progressState = new ProgressState(event.getEventType(), phaseDisplayName(event), activeModList.size(), parallelEligibleHandlers, startedAt);
        EarlySplashWindow.setPhaseProgress("FML " + phaseDisplayName(event), 0, activeModList.size());
        GPOM.LOGGER.info(
                "[FmlParallelLoading] {} starting with {} worker(s), parallelEligible={}, activeHandlers={}, serialHandlersAreBarriers={}, allowlist={}, denylist={}",
                event.getEventType(),
                workers,
                parallelEligibleHandlers,
                activeModList.size(),
                serialHandlersAreBarriers,
                parallelMods,
                deniedMods
        );
        InFlightDispatches inFlight = new InFlightDispatches(completionService);
        Set<ModContainer> submittedAhead = new LinkedHashSet<>();
        boolean completed = false;
        try {
            for (int index = 0; index < activeModList.size(); index++) {
                ModContainer mod = activeModList.get(index);
                if (submittedAhead.remove(mod)) {
                    drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, false, "pollSubmittedAhead");
                    continue;
                }

                boolean parallelAllowed = isParallelAllowed(mod, parallelMods, deniedMods);

                if (parallelAllowed) {
                    if (hasOrderDependencyWithBatch(event, mod, inFlight.mods, "workerBatch")) {
                        drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, true, "workerDependency");
                    }
                    inFlight.submit(new DispatchTask(
                            event,
                            mod,
                            eventChannels,
                            modStates,
                            progress,
                            progressState
                    ));
                    parallelHandlers++;
                    drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, false, "pollAfterSubmit");
                    continue;
                }

                if ((serialHandlersAreBarriers && inFlight.pending > 0)
                        || hasOrderDependencyWithBatch(event, mod, inFlight.mods, "serialBatch")) {
                    drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, true, "serialBarrier");
                }
                parallelHandlers += submitLoadCompleteLookahead(
                        event,
                        activeModList,
                        index + 1,
                        mod,
                        parallelMods,
                        deniedMods,
                        eventChannels,
                        modStates,
                        progress,
                        progressState,
                        inFlight,
                        submittedAhead
                );
                stepVisibleProgress(progress, mod, progressState);
                EarlySplashWindow.setPhaseProgress(
                        "FML " + progressState.displayPhaseName + " running " + progressLabel(mod),
                        progressState.completedHandlers,
                        progressState.totalHandlers
                );
                DispatchResult result = dispatchSingle(event, mod, eventChannels, modStates, true);
                commitResult(result, modStates);
                markHandlerCompleted(mod, progressState);
                handleFailure(event, result, modStates, progressState, continueOnModError);
                drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, false, "pollAfterSerial");
            }

            drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, true, "finalJoin");
            completed = true;
        } finally {
            if (completed) {
                ProgressManager.pop(progress);
            }
            executor.shutdownNow();
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        GPOM.LOGGER.info(
                "[FmlParallelLoading] {} handled with {} worker(s), parallelHandlers={}, continuedFailures={}, wall={} ms, allowlist={}, denylist={}",
                event.getEventType(),
                workers,
                parallelHandlers,
                progressState.continuedFailures,
                elapsedMillis,
                parallelMods,
                deniedMods
        );
    }

    private static void drainInFlight(FMLEvent phaseEvent,
                                      InFlightDispatches inFlight,
                                      Multimap<String, LoaderState.ModState> modStates,
                                      ProgressManager.ProgressBar progress,
                                      ProgressState progressState,
                                      boolean continueOnModError,
                                      boolean blockUntilEmpty,
                                      String reason) {
        int initialPending = inFlight.pending;
        int drained = 0;
        long startedAt = StartupProfiler.beginProbe();
        while (inFlight.pending > 0) {
            Future<DispatchResult> future;
            try {
                future = blockUntilEmpty ? inFlight.completionService.take() : inFlight.completionService.poll();
                if (future == null) {
                    if (drained > 0) {
                        StartupProfiler.endProbeAlways(
                                "FML scheduler " + eventType(phaseEvent) + " drainInFlight poll " + safeReason(reason),
                                startedAt
                        );
                    }
                    return;
                }
                DispatchResult result = future.get();
                drained++;
                inFlight.complete(result);
                commitResult(result, modStates);
                if (result != null && result.mod != null) {
                    if (!result.visibleProgressStepped) {
                        stepVisibleProgress(progress, result.mod, progressState);
                    }
                    markHandlerCompleted(result.mod, progressState);
                    stepThreadedProgress(result.mod, progressState);
                }
                handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                inFlight.cancelAll();
                throw new RuntimeException("Interrupted while waiting for parallel FML loading", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                DispatchResult result = DispatchResult.failed(null, cause);
                inFlight.complete(result);
                handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
            }
        }
        if (blockUntilEmpty || drained > 0) {
            StartupProfiler.endProbeAlways(
                    "FML scheduler " + eventType(phaseEvent) + " drainInFlight "
                            + (blockUntilEmpty ? "block " : "poll ")
                            + safeReason(reason),
                    startedAt
            );
        }
        if (blockUntilEmpty && initialPending > 0 && elapsedMillis(startedAt) >= 250L) {
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Waited {} ms draining {} in-flight {} handler(s) for {}",
                    elapsedMillis(startedAt),
                    initialPending,
                    eventType(phaseEvent),
                    safeReason(reason)
            );
        }
    }

    private static void stepVisibleProgress(ProgressManager.ProgressBar progress, ModContainer mod, ProgressState state) {
        if (progress == null || mod == null) {
            return;
        }
        synchronized (PROGRESS_LOCK) {
            if (state != null && !state.firstVisibleProgressLogged) {
                state.firstVisibleProgressLogged = true;
                GPOM.LOGGER.info(
                        "[FmlParallelLoading] {} first visible progress after {} ms: {} ({})",
                        state.eventType,
                        elapsedMillis(state.startedAtNanos),
                        mod.getModId(),
                        mod.getName()
                );
            }
            progress.step(progressLabel(mod));
        }
    }

    private static void markHandlerCompleted(ModContainer mod, ProgressState state) {
        if (mod == null) {
            return;
        }
        ++state.completedHandlers;
        EarlySplashWindow.setPhaseProgress("FML " + state.displayPhaseName, state.completedHandlers, state.totalHandlers);
    }

    private static void stepThreadedProgress(ModContainer mod, ProgressState state) {
        if (mod == null) {
            return;
        }
        ++state.completedParallelHandlers;
        maybeLogProgressSnapshot(mod, state);
    }

    private static int submitLoadCompleteLookahead(FMLEvent event,
                                                   List<ModContainer> activeModList,
                                                   int startIndex,
                                                   ModContainer serialMod,
                                                   Set<String> parallelMods,
                                                   Set<String> deniedMods,
                                                   ImmutableMap<String, EventBus> eventChannels,
                                                   Multimap<String, LoaderState.ModState> modStates,
                                                   ProgressManager.ProgressBar progress,
                                                   ProgressState progressState,
                                                   InFlightDispatches inFlight,
                                                   Set<ModContainer> submittedAhead) {
        if (!(event instanceof FMLLoadCompleteEvent) || serialMod == null || !"jei".equals(normalize(serialMod.getModId()))) {
            return 0;
        }

        int submitted = 0;
        List<ModContainer> lookaheadBatch = new ArrayList<>();
        for (int index = startIndex; index < activeModList.size(); index++) {
            ModContainer candidate = activeModList.get(index);
            if (submittedAhead.contains(candidate) || !isParallelAllowed(candidate, parallelMods, deniedMods)) {
                continue;
            }
            if (hasOrderDependency(event, serialMod, candidate, "loadCompleteSerial")
                    || hasOrderDependencyWithBatch(event, candidate, inFlight.mods, "loadCompleteInFlight")
                    || hasOrderDependencyWithBatch(event, candidate, lookaheadBatch, "loadCompleteLookahead")) {
                continue;
            }

            inFlight.submit(new DispatchTask(
                    event,
                    candidate,
                    eventChannels,
                    modStates,
                    progress,
                    progressState
            ));
            submittedAhead.add(candidate);
            lookaheadBatch.add(candidate);
            submitted++;
        }

        if (submitted > 0) {
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Queued {} independent LoadComplete lookahead handler(s) before serial HEI",
                    submitted
            );
            EarlySplashWindow.setPhaseProgress(
                    "FML LoadComplete running HEI; queued " + submitted + " later handler(s)",
                    progressState.completedHandlers,
                    progressState.totalHandlers
            );
        }
        return submitted;
    }

    private static String progressLabel(ModContainer mod) {
        return mod.getName();
    }

    private static String phaseDisplayName(FMLEvent event) {
        if (event instanceof FMLConstructionEvent) {
            return "Construction";
        }
        if (event instanceof FMLPreInitializationEvent) {
            return "PreInitialization";
        }
        if (event instanceof FMLInitializationEvent) {
            return "Initialization";
        }
        if (event instanceof FMLPostInitializationEvent) {
            return "PostInitialization";
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return "LoadComplete";
        }
        return event == null ? "FML" : event.getEventType();
    }

    private static boolean serialHandlersAreBarriers(FMLEvent event) {
        return event instanceof FMLConstructionEvent;
    }

    private static void maybeLogProgressSnapshot(ModContainer mod, ProgressState state) {
        if (state.totalParallelHandlers <= 0) {
            return;
        }
        int completed = state.completedParallelHandlers;
        if (completed != state.totalParallelHandlers
                && completed - state.lastLoggedParallelHandlers < PROGRESS_LOG_INTERVAL) {
            return;
        }
        state.lastLoggedParallelHandlers = completed;
        GPOM.LOGGER.info(
                "[FmlParallelLoading] progress {} parallel={}/{} total={}/{} heap={} MiB last={} ({})",
                state.eventType,
                completed,
                state.totalParallelHandlers,
                state.completedHandlers,
                state.totalHandlers,
                usedHeapMib(),
                mod.getModId(),
                mod.getName()
        );
    }

    private static DispatchResult dispatchSingle(FMLEvent phaseEvent, ModContainer mod,
                                                 ImmutableMap<String, EventBus> eventChannels,
                                                 Multimap<String, LoaderState.ModState> modStates,
                                                 boolean mainThread) {
        String modId = mod.getModId();
        long startedAt = System.nanoTime();
        String originalThreadName = null;
        if (!mainThread) {
            Thread currentThread = Thread.currentThread();
            originalThreadName = currentThread.getName();
            currentThread.setName("GPOM FML " + phaseDisplayName(phaseEvent) + " - " + modId);
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Starting threaded {} for {} ({})",
                    phaseEvent.getEventType(),
                    modId,
                    mod.getName()
            );
        }
        try {
            if (requiredDependencyErrored(mod, modStates)) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Skipping event {} and marking errored mod {} since a required dependency has errored",
                        phaseEvent.getEventType(),
                        modId
                );
                return DispatchResult.state(mod, LoaderState.ModState.ERRORED);
            }

            long cloneStartedAt = StartupProfiler.beginProbe();
            FMLEvent event;
            try {
                event = cloneEvent(phaseEvent);
            } finally {
                StartupProfiler.endProbe("FML dispatch " + eventType(phaseEvent) + " cloneEvent", cloneStartedAt);
            }
            long applyStartedAt = StartupProfiler.beginProbe();
            try {
                event.applyModContainer(mod);
            } finally {
                StartupProfiler.endProbe("FML dispatch " + eventType(phaseEvent) + " applyModContainer", applyStartedAt);
            }
            ThreadContext.put("mod", modId);
            FmlParallelLoadingContext.setActiveContainer(mod);
            try {
                long lookupStartedAt = StartupProfiler.beginProbe();
                EventBus eventBus = eventChannels.get(modId);
                StartupProfiler.endProbe("FML dispatch " + eventType(phaseEvent) + " eventBusLookup", lookupStartedAt);
                if (eventBus == null) {
                    throw new IllegalStateException("Missing FML event bus for mod " + modId);
                }
                long postStartedAt = StartupProfiler.beginProbe();
                try {
                    eventBus.post(event);
                } finally {
                    StartupProfiler.endProbe("FML dispatch " + eventType(phaseEvent) + " eventBusPost", postStartedAt);
                }
            } finally {
                FmlParallelLoadingContext.clearActiveContainer();
                ThreadContext.remove("mod");
            }

            DispatchResult result;
            if (event instanceof FMLStateEvent) {
                result = DispatchResult.state(mod, ((FMLStateEvent) event).getModState());
            } else {
                result = DispatchResult.ok(mod);
            }
            if (!mainThread) {
                GPOM.LOGGER.info(
                        "[FmlParallelLoading] Finished threaded {} for {} ({}) in {} ms",
                        phaseEvent.getEventType(),
                        modId,
                        mod.getName(),
                        elapsedMillis(startedAt)
                );
            }
            return result;
        } catch (Throwable throwable) {
            if (!mainThread) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Failed threaded {} for {} ({}) after {} ms",
                        phaseEvent.getEventType(),
                        modId,
                        mod.getName(),
                        elapsedMillis(startedAt),
                        throwable
                );
            }
            return DispatchResult.failed(mod, throwable);
        } finally {
            if (originalThreadName != null) {
                Thread.currentThread().setName(originalThreadName);
            }
        }
    }

    private static boolean requiredDependencyErrored(ModContainer mod,
                                                     Multimap<String, LoaderState.ModState> modStates) {
        Collection<String> requiredLabels = new LinkedHashSet<>();
        for (ArtifactVersion requirement : mod.getRequirements()) {
            if (requirement.getLabel() != null) {
                requiredLabels.add(requirement.getLabel());
            }
        }

        for (ArtifactVersion dependency : mod.getDependencies()) {
            String label = dependency.getLabel();
            if (label == null || !requiredLabels.contains(label)) {
                continue;
            }
            synchronized (MOD_STATE_LOCK) {
                if (modStates.containsEntry(label, LoaderState.ModState.ERRORED)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FMLEvent cloneEvent(FMLEvent event) {
        try {
            Constructor<? extends FMLEvent> constructor = event.getClass().asSubclass(FMLEvent.class)
                    .getConstructor(Object[].class);
            if (event instanceof FMLConstructionEvent) {
                FMLConstructionEvent constructionEvent = (FMLConstructionEvent) event;
                return constructor.newInstance((Object) new Object[] {
                        constructionEvent.getModClassLoader(),
                        constructionEvent.getASMHarvestedData(),
                        constructionEvent.getReverseDependencies()
                });
            }
            if (event instanceof FMLPreInitializationEvent) {
                FMLPreInitializationEvent preInitEvent = (FMLPreInitializationEvent) event;
                ASMDataTable asmData = preInitEvent.getAsmData();
                File configDir = preInitEvent.getModConfigurationDirectory();
                return constructor.newInstance((Object) new Object[] {asmData, configDir});
            }
            return constructor.newInstance((Object) new Object[0]);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException("Unable to clone FML event " + event.getClass().getName(), exception);
        }
    }

    private static void commitResult(DispatchResult result, Multimap<String, LoaderState.ModState> modStates) {
        if (result == null || result.mod == null || result.state == null) {
            return;
        }
        synchronized (MOD_STATE_LOCK) {
            modStates.put(result.mod.getModId(), result.state);
        }
    }

    private static void handleFailure(FMLEvent event, DispatchResult result,
                                      Multimap<String, LoaderState.ModState> modStates,
                                      ProgressState progressState,
                                      boolean continueOnModError) {
        if (result == null || result.throwable == null) {
            return;
        }
        if (!continueOnModError) {
            ModContainer mod = result.mod;
            if (mod != null) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Aborting {} after threaded failure in {} ({})",
                        event.getEventType(),
                        mod.getModId(),
                        mod.getName(),
                        result.throwable
                );
            } else {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Aborting {} after threaded failure in an unknown worker task",
                        event.getEventType(),
                        result.throwable
                );
            }
            rethrow(result.throwable);
            return;
        }

        progressState.continuedFailures++;
        ModContainer mod = result.mod;
        if (mod != null) {
            synchronized (MOD_STATE_LOCK) {
                modStates.put(mod.getModId(), LoaderState.ModState.ERRORED);
            }
            GPOM.LOGGER.error(
                    "[FmlParallelLoading] Continuing after {} failure in {} ({}); marking mod errored for diagnostics",
                    event.getEventType(),
                    mod.getModId(),
                    mod.getName(),
                    result.throwable
            );
            return;
        }

        GPOM.LOGGER.error(
                "[FmlParallelLoading] Continuing after {} failure in an unknown worker task; no mod state could be marked",
                event.getEventType(),
                result.throwable
        );
    }

    private static void rethrow(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }

    private static boolean isParallelAllowed(ModContainer mod, Set<String> parallelMods, Set<String> deniedMods) {
        String modId = normalize(mod.getModId());
        return !deniedMods.contains(modId) && (parallelMods.contains("*") || parallelMods.contains(modId));
    }

    private static int countParallelAllowed(List<ModContainer> activeModList, Set<String> parallelMods, Set<String> deniedMods) {
        int count = 0;
        for (ModContainer mod : activeModList) {
            if (isParallelAllowed(mod, parallelMods, deniedMods)) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasOrderDependencyWithBatch(ModContainer mod, List<ModContainer> batch) {
        String modId = normalize(mod.getModId());
        Set<String> modRules = dependencyLabels(mod);
        for (ModContainer batched : batch) {
            String batchedId = normalize(batched.getModId());
            if (modRules.contains(batchedId) || dependencyLabels(batched).contains(modId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOrderDependencyWithBatch(FMLEvent event, ModContainer mod, List<ModContainer> batch, String reason) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return hasOrderDependencyWithBatch(mod, batch);
        } finally {
            StartupProfiler.endProbe(
                    "FML scheduler " + eventType(event) + " dependencyScan " + safeReason(reason),
                    startedAt
            );
        }
    }

    private static boolean hasOrderDependency(ModContainer first, ModContainer second) {
        if (first == null || second == null) {
            return false;
        }
        String firstId = normalize(first.getModId());
        String secondId = normalize(second.getModId());
        return dependencyLabels(first).contains(secondId) || dependencyLabels(second).contains(firstId);
    }

    private static boolean hasOrderDependency(FMLEvent event, ModContainer first, ModContainer second, String reason) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return hasOrderDependency(first, second);
        } finally {
            StartupProfiler.endProbe(
                    "FML scheduler " + eventType(event) + " dependencyScan " + safeReason(reason),
                    startedAt
            );
        }
    }

    private static Set<String> dependencyLabels(ModContainer mod) {
        Set<String> labels = new LinkedHashSet<>();
        addLabels(labels, mod.getRequirements());
        addLabels(labels, mod.getDependencies());
        addLabels(labels, mod.getDependants());
        return labels;
    }

    private static void addLabels(Set<String> labels, Collection<ArtifactVersion> versions) {
        for (ArtifactVersion version : versions) {
            String label = normalize(version.getLabel());
            if (!label.isEmpty() && !"*".equals(label)) {
                labels.add(label);
            }
        }
    }

    private static Set<String> parallelMods(FMLEvent event) {
        if (event instanceof FMLConstructionEvent) {
            return GpomEarlyConfig.parallelConstructAllowlist();
        }
        if (event instanceof FMLPreInitializationEvent) {
            return GpomEarlyConfig.parallelPreInitAllowlist();
        }
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitAllowlist();
        }
        if (event instanceof FMLInitializationEvent) {
            return GpomEarlyConfig.parallelInitAllowlist();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteAllowlist();
        }
        return new LinkedHashSet<>();
    }

    private static Set<String> deniedMods(FMLEvent event) {
        if (event instanceof FMLConstructionEvent) {
            return GpomEarlyConfig.parallelConstructDenylist();
        }
        if (event instanceof FMLPreInitializationEvent) {
            return GpomEarlyConfig.parallelPreInitDenylist();
        }
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitDenylist();
        }
        if (event instanceof FMLInitializationEvent) {
            return GpomEarlyConfig.parallelInitDenylist();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteDenylist();
        }
        return new LinkedHashSet<>();
    }

    private static boolean continueOnModError(FMLEvent event) {
        if (event instanceof FMLConstructionEvent) {
            return GpomEarlyConfig.parallelConstructContinueOnModError();
        }
        if (event instanceof FMLPreInitializationEvent) {
            return GpomEarlyConfig.parallelPreInitContinueOnModError();
        }
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitContinueOnModError();
        }
        if (event instanceof FMLInitializationEvent) {
            return GpomEarlyConfig.parallelInitContinueOnModError();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteContinueOnModError();
        }
        return false;
    }

    private static int requestedWorkers(FMLEvent event) {
        int configured = configuredWorkers(event);
        if (configured > 0) {
            return configured;
        }
        int autoWorkers = automaticWorkers();
        GPOM.LOGGER.info(
                "[FmlParallelLoading] Auto-selected {} worker(s) for {} from cpuCount={} totalMemory={} MiB",
                autoWorkers,
                event == null ? "unknown" : event.getEventType(),
                Runtime.getRuntime().availableProcessors(),
                totalPhysicalMemoryMib()
        );
        return autoWorkers;
    }

    private static int configuredWorkers(FMLEvent event) {
        if (event instanceof FMLConstructionEvent) {
            return GpomEarlyConfig.parallelConstructWorkers();
        }
        if (event instanceof FMLPreInitializationEvent) {
            return GpomEarlyConfig.parallelPreInitWorkers();
        }
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitWorkers();
        }
        if (event instanceof FMLInitializationEvent) {
            return GpomEarlyConfig.parallelInitWorkers();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteWorkers();
        }
        return GpomEarlyConfig.parallelWorkers();
    }

    private static int automaticWorkers() {
        int processors = Math.max(1, Runtime.getRuntime().availableProcessors());
        int cpuWorkers = processors <= 2 ? 1 : Math.max(2, processors - 2);
        long memoryMib = totalPhysicalMemoryMib();
        if (memoryMib <= 0) {
            return cpuWorkers;
        }
        int memoryWorkers = Math.max(1, (int) (memoryMib / 4096L));
        return Math.max(1, Math.min(cpuWorkers, memoryWorkers));
    }

    private static long totalPhysicalMemoryMib() {
        Object osBean = ManagementFactory.getOperatingSystemMXBean();
        Long bytes = invokeLongNoArg(osBean, "getTotalMemorySize");
        if (bytes == null) {
            bytes = invokeLongNoArg(osBean, "getTotalPhysicalMemorySize");
        }
        return bytes == null || bytes <= 0 ? -1L : bytes / MIB;
    }

    private static Long invokeLongNoArg(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Number ? ((Number) value).longValue() : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long usedHeapMib() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / MIB;
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private static String eventType(FMLEvent event) {
        return event == null ? "unknown" : event.getEventType();
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isEmpty() ? "unknown" : reason;
    }

    private static final class DispatchTask implements Callable<DispatchResult> {
        private final FMLEvent phaseEvent;
        private final ModContainer mod;
        private final ImmutableMap<String, EventBus> eventChannels;
        private final Multimap<String, LoaderState.ModState> modStates;
        private final ProgressManager.ProgressBar progress;
        private final ProgressState progressState;

        private DispatchTask(FMLEvent phaseEvent, ModContainer mod,
                             ImmutableMap<String, EventBus> eventChannels,
                             Multimap<String, LoaderState.ModState> modStates,
                             ProgressManager.ProgressBar progress,
                             ProgressState progressState) {
            this.phaseEvent = phaseEvent;
            this.mod = mod;
            this.eventChannels = eventChannels;
            this.modStates = modStates;
            this.progress = progress;
            this.progressState = progressState;
        }

        @Override
        public DispatchResult call() {
            DispatchResult result = dispatchSingle(phaseEvent, mod, eventChannels, modStates, false);
            stepVisibleProgress(progress, mod, progressState);
            return result.withVisibleProgressStepped();
        }
    }

    private static final class InFlightDispatches {
        private final CompletionService<DispatchResult> completionService;
        private final List<ModContainer> mods = new ArrayList<>();
        private final List<Future<DispatchResult>> futures = new ArrayList<>();
        private int pending;

        private InFlightDispatches(CompletionService<DispatchResult> completionService) {
            this.completionService = completionService;
        }

        private void submit(DispatchTask task) {
            mods.add(task.mod);
            futures.add(completionService.submit(task));
            pending++;
        }

        private void complete(DispatchResult result) {
            pending = Math.max(0, pending - 1);
            if (result != null && result.mod != null) {
                mods.remove(result.mod);
            }
            if (pending == 0) {
                mods.clear();
                futures.clear();
            }
        }

        private void cancelAll() {
            for (Future<DispatchResult> future : futures) {
                future.cancel(true);
            }
            pending = 0;
            mods.clear();
            futures.clear();
        }
    }

    private static final class ProgressState {
        private final String eventType;
        private final String displayPhaseName;
        private final int totalHandlers;
        private final int totalParallelHandlers;
        private final long startedAtNanos;
        private int completedHandlers;
        private int completedParallelHandlers;
        private int lastLoggedParallelHandlers;
        private int continuedFailures;
        private boolean firstVisibleProgressLogged;

        private ProgressState(String eventType, String displayPhaseName, int totalHandlers, int totalParallelHandlers, long startedAtNanos) {
            this.eventType = eventType;
            this.displayPhaseName = displayPhaseName;
            this.totalHandlers = totalHandlers;
            this.totalParallelHandlers = totalParallelHandlers;
            this.startedAtNanos = startedAtNanos;
        }

    }

    private static final class DispatchResult {
        private final ModContainer mod;
        private final LoaderState.ModState state;
        private final Throwable throwable;
        private final boolean visibleProgressStepped;

        private DispatchResult(ModContainer mod, LoaderState.ModState state, Throwable throwable) {
            this(mod, state, throwable, false);
        }

        private DispatchResult(ModContainer mod, LoaderState.ModState state, Throwable throwable, boolean visibleProgressStepped) {
            this.mod = mod;
            this.state = state;
            this.throwable = throwable;
            this.visibleProgressStepped = visibleProgressStepped;
        }

        private static DispatchResult ok(ModContainer mod) {
            return new DispatchResult(mod, null, null);
        }

        private static DispatchResult state(ModContainer mod, LoaderState.ModState state) {
            return new DispatchResult(mod, state, null);
        }

        private static DispatchResult failed(ModContainer mod, Throwable throwable) {
            return new DispatchResult(mod, null, throwable);
        }

        private DispatchResult withVisibleProgressStepped() {
            if (visibleProgressStepped) {
                return this;
            }
            return new DispatchResult(mod, state, throwable, true);
        }
    }
}
