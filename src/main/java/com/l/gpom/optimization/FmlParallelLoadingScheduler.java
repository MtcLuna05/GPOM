package com.l.gpom.optimization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.l.gpom.GPOM;
import com.l.gpom.util.EarlySplashBridge;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.core.ChickenAsmConcurrencyTransformer;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.Loader;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class FmlParallelLoadingScheduler {
    private static final Object MOD_STATE_LOCK = new Object();
    private static final Object PROGRESS_LOCK = new Object();
    private static final int PROGRESS_LOG_INTERVAL = 25;
    private static final long MIB = 1048576L;
    private static final byte[][] OPENGL_THREAD_AFFINITY_PATTERNS = new byte[][] {
            ascii("org/lwjgl/opengl/"),
            ascii("org/lwjgl/input/")
    };
    private static final Map<String, Boolean> OPENGL_REFERENCE_CACHE = Collections.synchronizedMap(new HashMap<>());
    private static final Set<String> REPORTED_OPENGL_SERIAL_MODS = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Set<String> POST_INIT_DRAIN_BEFORE_SERIAL_MODS = fixedSet(
            "cyclopscore",
            "integrateddynamics",
            "integrateddynamicscompat",
            "integratednbt",
            "integratedtunnels",
            "integratedtunnelscompat"
    );
    private static final Set<String> INIT_CAPABILITY_ATTACH_SERIAL_MODS = fixedSet(
            "careerbees",
            "commoncapabilities",
            "cyclopscore",
            "integratedderivative",
            "integrateddynamics",
            "integrateddynamicscompat",
            "integratednbt",
            "integratedtunnels",
            "integratedtunnelscompat"
    );
    private static final Set<String> TRANSFORMER_CLASSLOAD_SERIAL_MODS = fixedSet(
            "advancedrocketry",
            "advancedrocketrycore",
            "ausm"
    );
    private static volatile Field progressBarMessageField;

    private FmlParallelLoadingScheduler() {
    }

    public static boolean shouldParallelize(FMLEvent event) {
        return (event instanceof FMLConstructionEvent && GpomEarlyConfig.parallelConstructEnabled())
                || (event instanceof FMLPreInitializationEvent && GpomEarlyConfig.parallelPreInitEnabled())
                || (event instanceof FMLPostInitializationEvent && GpomEarlyConfig.parallelPostInitEnabled())
                || (event instanceof FMLInitializationEvent && GpomEarlyConfig.parallelInitEnabled())
                || (event instanceof FMLLoadCompleteEvent && GpomEarlyConfig.parallelLoadCompleteEnabled());
    }

    private static boolean parallelDagEnabled(FMLEvent event) {
        if (!shouldParallelize(event)) {
            return false;
        }
        return (event instanceof FMLConstructionEvent && GpomEarlyConfig.parallelConstructDagEnabled())
                || (event instanceof FMLPreInitializationEvent && GpomEarlyConfig.parallelPreInitDagEnabled())
                || (event instanceof FMLInitializationEvent && GpomEarlyConfig.parallelInitDagEnabled())
                || (event instanceof FMLPostInitializationEvent && GpomEarlyConfig.parallelPostInitDagEnabled())
                || (event instanceof FMLLoadCompleteEvent && GpomEarlyConfig.parallelLoadCompleteDagEnabled());
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
        if (event instanceof FMLConstructionEvent || event instanceof FMLPreInitializationEvent) {
            ChickenAsmConcurrencyTransformer.hardenRuntimeCaches();
            ChickenAsmConcurrencyTransformer.preloadObfMapping();
        }
        clearThreadedBreadcrumbs(event);
        if (parallelDagEnabled(event)) {
            propagateDag(event, activeModList, eventChannels, modStates, parallelMods, deniedMods, continueOnModError);
            return;
        }

        long countStartedAt = StartupProfiler.beginProbe();
        int parallelEligibleHandlers;
        try {
            parallelEligibleHandlers = countParallelAllowed(event, activeModList, parallelMods, deniedMods);
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
        EarlySplashBridge.setPhaseProgress("FML " + phaseDisplayName(event), 0, activeModList.size());
        if (schedulerLogsEnabled()) {
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
        }
        PreInitClassPrewarmer.WarmHandle prewarmHandle = PreInitClassPrewarmer.WarmHandle.noop();
        boolean prewarmStarted = false;
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

                boolean parallelAllowed = isParallelAllowed(event, mod, parallelMods, deniedMods);

                if (parallelAllowed) {
                    if (!prewarmStarted && shouldStartPreInitPrewarmer(event, progressState, -1L)) {
                        prewarmHandle = startPreInitPrewarmer(activeModList, workers, progressState);
                        prewarmStarted = true;
                    }
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

                if (((serialHandlersAreBarriers || shouldDrainWorkersBeforeSerial(event, mod)) && inFlight.pending > 0)
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
                String serialWaitMessage = waitingFor(mod);
                setVisibleProgressMessage(progress, serialWaitMessage);
                stepVisibleProgress(progress, mod, progressState);
                EarlySplashBridge.setPhaseProgress(
                        "FML " + progressState.displayPhaseName + " " + serialWaitMessage,
                        progressState.completedHandlers,
                        progressState.totalHandlers
                );
                long serialStartedAt = System.nanoTime();
                DispatchResult result;
                try (PreInitClassPrewarmer.SerialPause ignored = PreInitClassPrewarmer.pauseDuringSerialHandler()) {
                    result = dispatchSingle(event, mod, eventChannels, modStates, true);
                }
                long serialElapsedMillis = elapsedMillis(serialStartedAt);
                if (serialElapsedMillis >= 250L) {
                    logVisibleWait(serialWaitMessage);
                }
                commitResult(result, modStates);
                markHandlerCompleted(mod, progressState);
                handleFailure(event, result, modStates, progressState, continueOnModError);
                if (!prewarmStarted && shouldStartPreInitPrewarmer(event, progressState, serialElapsedMillis)) {
                    prewarmHandle = startPreInitPrewarmer(activeModList, workers, progressState);
                    prewarmStarted = true;
                }
                drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, false, "pollAfterSerial");
            }

            drainInFlight(event, inFlight, modStates, progress, progressState, continueOnModError, true, "finalJoin");
            completed = true;
        } finally {
            if (completed) {
                ProgressManager.pop(progress);
            }
            prewarmHandle.close();
            executor.shutdownNow();
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (schedulerLogsEnabled()) {
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
    }

    private static void propagateDag(FMLEvent event,
                                     List<ModContainer> activeModList,
                                     ImmutableMap<String, EventBus> eventChannels,
                                     Multimap<String, LoaderState.ModState> modStates,
                                     Set<String> parallelMods,
                                     Set<String> deniedMods,
                                     boolean continueOnModError) {
        long countStartedAt = StartupProfiler.beginProbe();
        int parallelEligibleHandlers;
        try {
            parallelEligibleHandlers = countParallelAllowed(event, activeModList, parallelMods, deniedMods);
        } finally {
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " dag countParallelAllowed", countStartedAt);
        }
        int requestedWorkers = requestedWorkers(event);
        int workers = Math.min(requestedWorkers, Math.max(1, parallelEligibleHandlers));
        int maxInFlight = dagMaxInFlight(event, workers);
        boolean serialHandlersAreBarriers = dagSerialHandlersAreBarriers(event);
        boolean serialHandlersDrainWorkers = dagSerialHandlersDrainWorkers(event);

        long graphStartedAt = StartupProfiler.beginProbe();
        List<DagNode> nodes;
        Map<String, DagNode> nodesByModId;
        try {
            nodes = buildDependencyDag(event, activeModList, parallelMods, deniedMods);
            nodesByModId = new HashMap<>();
            for (DagNode node : nodes) {
                nodesByModId.put(normalize(node.mod.getModId()), node);
            }
        } finally {
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " dag build", graphStartedAt);
        }

        long executorStartedAt = StartupProfiler.beginProbe();
        ExecutorService executor;
        CompletionService<DispatchResult> completionService;
        try {
            executor = Executors.newFixedThreadPool(workers, runnable -> {
                Thread thread = new Thread(runnable, "GPOM FML " + phaseDisplayName(event) + " DAG loader");
                thread.setDaemon(true);
                return thread;
            });
            completionService = new ExecutorCompletionService<>(executor);
        } finally {
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " dag executorSetup", executorStartedAt);
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
            StartupProfiler.endProbe("FML scheduler " + event.getEventType() + " dag progressSetup", progressStartedAt);
        }

        long startedAt = System.nanoTime();
        int parallelHandlers = 0;
        ProgressState progressState = new ProgressState(event.getEventType(), phaseDisplayName(event), activeModList.size(), parallelEligibleHandlers, startedAt);
        DagDiagnostics diagnostics = new DagDiagnostics(event);
        EarlySplashBridge.setPhaseProgress("FML " + phaseDisplayName(event), 0, activeModList.size());
        if (schedulerLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] {} DAG starting with {} worker(s), maxInFlight={}, parallelEligible={}, activeHandlers={}, serialHandlersAreBarriers={}, serialHandlersDrainWorkers={}, allowlist={}, denylist={}",
                    event.getEventType(),
                    workers,
                    maxInFlight,
                    parallelEligibleHandlers,
                    activeModList.size(),
                    serialHandlersAreBarriers,
                    serialHandlersDrainWorkers,
                    parallelMods,
                    deniedMods
            );
        }

        PreInitClassPrewarmer.WarmHandle prewarmHandle = PreInitClassPrewarmer.WarmHandle.noop();
        boolean prewarmStarted = false;
        InFlightDispatches inFlight = new InFlightDispatches(completionService);
        boolean completed = false;
        try {
            while (progressState.completedHandlers < nodes.size()) {
                if (!prewarmStarted && shouldStartPreInitPrewarmer(event, progressState, -1L)) {
                    prewarmHandle = startPreInitPrewarmer(activeModList, workers, progressState);
                    prewarmStarted = true;
                }
                DagNode nextSerial = nextIncompleteSerial(nodes);
                int serialCutoff = (serialHandlersAreBarriers && nextSerial != null) ? nextSerial.index : Integer.MAX_VALUE;
                parallelHandlers += submitReadyDagWorkers(
                        event,
                        nodes,
                        serialCutoff,
                        Integer.MAX_VALUE,
                        maxInFlight,
                        eventChannels,
                        modStates,
                        progress,
                        progressState,
                        inFlight
                );

                if (nextSerial != null && !nextSerial.completed && nextSerial.remainingDependencies == 0) {
                    if (shouldDrainWorkersBeforeDagSerial(event, nextSerial, serialHandlersDrainWorkers) && inFlight.pending > 0) {
                        drainOneDagInFlight(event, inFlight, nodesByModId, modStates, progress, progressState, continueOnModError, "dagSerialBarrier", diagnostics);
                        continue;
                    }
                    long serialElapsedMillis = runDagSerialNode(event, nextSerial, eventChannels, modStates, progress, progressState, continueOnModError, diagnostics);
                    if (!prewarmStarted && shouldStartPreInitPrewarmer(event, progressState, serialElapsedMillis)) {
                        prewarmHandle = startPreInitPrewarmer(activeModList, workers, progressState);
                        prewarmStarted = true;
                    }
                    continue;
                }

                if (nextSerial == null) {
                    int submitted = submitReadyDagWorkers(
                            event,
                            nodes,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            maxInFlight,
                            eventChannels,
                            modStates,
                            progress,
                            progressState,
                            inFlight
                    );
                    parallelHandlers += submitted;
                    if (submitted > 0) {
                        continue;
                    }
                } else {
                    DagNode readySerialPredecessor = firstReadySerialPredecessor(nodes, nextSerial);
                    if (readySerialPredecessor != null) {
                        if (shouldDrainWorkersBeforeDagSerial(event, readySerialPredecessor, serialHandlersDrainWorkers) && inFlight.pending > 0) {
                            drainOneDagInFlight(event, inFlight, nodesByModId, modStates, progress, progressState, continueOnModError, "dagSerialPredecessorBarrier", diagnostics);
                            continue;
                        }
                        long serialElapsedMillis = runDagSerialNode(event, readySerialPredecessor, eventChannels, modStates, progress, progressState, continueOnModError, diagnostics);
                        if (!prewarmStarted && shouldStartPreInitPrewarmer(event, progressState, serialElapsedMillis)) {
                            prewarmHandle = startPreInitPrewarmer(activeModList, workers, progressState);
                            prewarmStarted = true;
                        }
                        continue;
                    }

                    int submitted = submitReadyDagPredecessors(
                            event,
                            nodes,
                            nextSerial,
                            Integer.MAX_VALUE,
                            maxInFlight,
                            eventChannels,
                            modStates,
                            progress,
                            progressState,
                            inFlight
                    );
                    parallelHandlers += submitted;
                    if (submitted > 0) {
                        continue;
                    }
                }

                if (inFlight.pending > 0) {
                    drainOneDagInFlight(event, inFlight, nodesByModId, modStates, progress, progressState, continueOnModError, "dagWait", diagnostics);
                    continue;
                }

                DagNode forced = firstIncompleteNode(nodes);
                if (forced == null) {
                    break;
                }
                GPOM.LOGGER.warn(
                        "[FmlParallelLoading] {} DAG found no ready node; forcing {} ({}) with {} unresolved dependency edge(s): {}",
                        event.getEventType(),
                        forced.mod.getModId(),
                        forced.mod.getName(),
                        forced.remainingDependencies,
                        unresolvedDependencySummary(forced)
                );
                long serialElapsedMillis = runDagSerialNode(event, forced, eventChannels, modStates, progress, progressState, continueOnModError, diagnostics);
                if (!prewarmStarted && shouldStartPreInitPrewarmer(event, progressState, serialElapsedMillis)) {
                    prewarmHandle = startPreInitPrewarmer(activeModList, workers, progressState);
                    prewarmStarted = true;
                }
            }
            completed = true;
        } finally {
            if (completed) {
                ProgressManager.pop(progress);
            }
            prewarmHandle.close();
            executor.shutdownNow();
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        if (schedulerLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] {} DAG handled with {} worker(s), maxInFlight={}, parallelHandlers={}, continuedFailures={}, wall={} ms, allowlist={}, denylist={}",
                    event.getEventType(),
                    workers,
                    maxInFlight,
                    parallelHandlers,
                    progressState.continuedFailures,
                    elapsedMillis,
                    parallelMods,
                    deniedMods
            );
        }
        diagnostics.logSummary(elapsedMillis);
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
        String waitMessage = "Waiting for none";
        long waitStartedAt = System.nanoTime();
        long probeStartedAt = StartupProfiler.beginProbe();
        while (inFlight.pending > 0) {
            Future<DispatchResult> future;
            try {
                if (blockUntilEmpty) {
                    waitMessage = inFlight.longestRunningWaitMessage();
                    setVisibleProgressMessage(progress, waitMessage);
                }
                if (blockUntilEmpty) {
                    try (PreInitClassPrewarmer.SerialPause ignored = PreInitClassPrewarmer.pauseDuringBlockingWait()) {
                        future = inFlight.completionService.take();
                    }
                } else {
                    future = inFlight.completionService.poll();
                }
                if (future == null) {
                    if (drained > 0) {
                        StartupProfiler.endProbeAlways(
                                "FML scheduler " + eventType(phaseEvent) + " drainInFlight poll " + safeReason(reason),
                                probeStartedAt
                        );
                    }
                    return;
                }
                DispatchResult result = future.get();
                drained++;
                inFlight.complete(result);
                commitResult(result, modStates);
                if (result != null && result.mod != null) {
                    stepVisibleProgress(progress, result.mod, progressState);
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
                DispatchResult result = DispatchResult.failed(null, cause, 0L, true);
                inFlight.complete(result);
                handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
            }
        }
        if (blockUntilEmpty || drained > 0) {
            StartupProfiler.endProbeAlways(
                    "FML scheduler " + eventType(phaseEvent) + " drainInFlight "
                            + (blockUntilEmpty ? "block " : "poll ")
                            + safeReason(reason)
                            + (blockUntilEmpty ? " " + waitMessage : ""),
                    probeStartedAt
            );
        }
        if (blockUntilEmpty && initialPending > 0 && elapsedMillis(waitStartedAt) >= 250L) {
            logVisibleWait(waitMessage);
        }
    }

    private static void setVisibleProgressMessage(ProgressManager.ProgressBar progress, String message) {
        if (progress == null || message == null || message.isEmpty()) {
            return;
        }
        try {
            Field field = progressBarMessageField;
            if (field == null) {
                field = ProgressManager.ProgressBar.class.getDeclaredField("message");
                field.setAccessible(true);
                progressBarMessageField = field;
            }
            field.set(progress, message);
        } catch (Throwable ignored) {
        }
    }

    private static void stepVisibleProgress(ProgressManager.ProgressBar progress, ModContainer mod, ProgressState state) {
        if (progress == null || mod == null) {
            return;
        }
        synchronized (PROGRESS_LOCK) {
            if (state != null && !state.firstVisibleProgressLogged) {
                state.firstVisibleProgressLogged = true;
                if (schedulerLogsEnabled()) {
                    GPOM.LOGGER.info(
                            "[FmlParallelLoading] {} first visible progress after {} ms: {} ({})",
                            state.eventType,
                            elapsedMillis(state.startedAtNanos),
                            mod.getModId(),
                            mod.getName()
                    );
                }
            }
            progress.step(progressLabel(mod));
        }
    }

    private static void markHandlerCompleted(ModContainer mod, ProgressState state) {
        if (mod == null) {
            return;
        }
        ++state.completedHandlers;
        EarlySplashBridge.setPhaseProgress("FML " + state.displayPhaseName, state.completedHandlers, state.totalHandlers);
    }

    private static void stepThreadedProgress(ModContainer mod, ProgressState state) {
        if (mod == null) {
            return;
        }
        ++state.completedParallelHandlers;
        maybeLogProgressSnapshot(mod, state);
    }

    private static boolean shouldStartPreInitPrewarmer(FMLEvent event, ProgressState state, long completedSerialMillis) {
        if (!(event instanceof FMLPreInitializationEvent) || !GpomEarlyConfig.preInitClassPrewarmEnabled()) {
            return false;
        }
        long serialThresholdMillis = GpomEarlyConfig.preInitClassPrewarmDeferUntilSerialMillis();
        if (completedSerialMillis >= 0L && completedSerialMillis >= serialThresholdMillis) {
            return true;
        }
        int minCompletedHandlers = GpomEarlyConfig.preInitClassPrewarmDeferMinCompletedHandlers();
        return state != null && state.completedHandlers >= minCompletedHandlers;
    }

    private static PreInitClassPrewarmer.WarmHandle startPreInitPrewarmer(List<ModContainer> activeModList,
                                                                          int workers,
                                                                          ProgressState state) {
        if (schedulerLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[PreInitClassPrewarmer] Deferred async prewarm start after {} completed handler(s)",
                    state == null ? 0 : state.completedHandlers
            );
        }
        return PreInitClassPrewarmer.startAsync(activeModList, workers);
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
            if (submittedAhead.contains(candidate) || !isParallelAllowed(event, candidate, parallelMods, deniedMods)) {
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
            if (schedulerLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[FmlParallelLoading] Queued {} independent LoadComplete lookahead handler(s) before serial HEI",
                        submitted
                );
            }
            EarlySplashBridge.setPhaseProgress(
                    "FML LoadComplete running HEI; queued " + submitted + " later handler(s)",
                    progressState.completedHandlers,
                    progressState.totalHandlers
            );
        }
        return submitted;
    }

    private static List<DagNode> buildDependencyDag(FMLEvent event,
                                                    List<ModContainer> activeModList,
                                                    Set<String> parallelMods,
                                                    Set<String> deniedMods) {
        List<DagNode> nodes = new ArrayList<>(activeModList.size());
        Map<String, DagNode> nodesByModId = new HashMap<>();
        for (int index = 0; index < activeModList.size(); index++) {
            ModContainer mod = activeModList.get(index);
            DagNode node = new DagNode(index, mod, isParallelAllowed(event, mod, parallelMods, deniedMods));
            nodes.add(node);
            nodesByModId.put(normalize(mod.getModId()), node);
        }

        for (DagNode node : nodes) {
            for (String dependency : directDependencyLabels(node.mod)) {
                DagNode dependencyNode = nodesByModId.get(dependency);
                if (dependencyNode != null) {
                    addDagEdge(event, dependencyNode, node);
                }
            }
            for (String dependant : directDependantLabels(node.mod)) {
                DagNode dependantNode = nodesByModId.get(dependant);
                if (dependantNode != null) {
                    addDagEdge(event, node, dependantNode);
                }
            }
        }
        addKnownLifecycleDagEdges(event, nodesByModId);

        // Barrier phases use serialCutoff at runtime. Encoding serial barriers
        // as graph edges can deadlock when a later mod is an explicit dependency
        // of an earlier serial handler.
        return nodes;
    }

    private static void addKnownLifecycleDagEdges(FMLEvent event, Map<String, DagNode> nodesByModId) {
        if (event instanceof FMLPreInitializationEvent) {
            // Bewitchment registers ore entries during object construction, which can synchronously
            // hit Extra Utilities 2 ore listeners before XU2 finishes initializing its recipe API.
            addKnownDagEdge(event, nodesByModId, "extrautils2", "bewitchment");
        }

        if (event instanceof FMLPostInitializationEvent) {
            addKnownDagEdge(event, nodesByModId, "integrateddynamics", "integratedtunnels");
            addKnownDagEdge(event, nodesByModId, "integrateddynamics", "integrateddynamicscompat");
            addKnownDagEdge(event, nodesByModId, "integrateddynamics", "integratedtunnelscompat");
            addKnownDagEdge(event, nodesByModId, "integrateddynamicscompat", "integratedtunnels");
            addKnownDagEdge(event, nodesByModId, "integrateddynamicscompat", "integratedtunnelscompat");
            addKnownDagEdge(event, nodesByModId, "integratedtunnels", "integratedtunnelscompat");
        }
    }

    private static void addKnownDagEdge(FMLEvent event, Map<String, DagNode> nodesByModId, String before, String after) {
        DagNode beforeNode = nodesByModId.get(normalize(before));
        DagNode afterNode = nodesByModId.get(normalize(after));
        if (beforeNode != null && afterNode != null) {
            addDagEdge(event, beforeNode, afterNode);
        }
    }

    private static void addDagEdge(FMLEvent event, DagNode before, DagNode after) {
        if (before == null || after == null || before == after) {
            return;
        }
        if (before.successors.contains(after)) {
            return;
        }
        if (hasDagPath(after, before)) {
            if (schedulerLogsEnabled()) {
                GPOM.LOGGER.warn(
                        "[FmlParallelLoading] Skipping cyclic {} DAG edge {} -> {}",
                        eventType(event),
                        normalize(before.mod.getModId()),
                        normalize(after.mod.getModId())
                );
            }
            return;
        }
        before.successors.add(after);
        after.predecessors.add(before);
        after.remainingDependencies++;
    }

    private static int dagMaxInFlight(FMLEvent event, int workers) {
        if (event instanceof FMLConstructionEvent || event instanceof FMLPostInitializationEvent) {
            return Math.max(1, workers);
        }
        return Math.max(1, workers * 2);
    }

    private static int submitReadyDagWorkers(FMLEvent event,
                                             List<DagNode> nodes,
                                             int indexCutoff,
                                             int maxSubmits,
                                             int maxInFlight,
                                             ImmutableMap<String, EventBus> eventChannels,
                                             Multimap<String, LoaderState.ModState> modStates,
                                             ProgressManager.ProgressBar progress,
                                             ProgressState progressState,
                                             InFlightDispatches inFlight) {
        int submitted = 0;
        while (submitted < maxSubmits && inFlight.pending < maxInFlight) {
            DagNode node = firstReadyParallelNode(nodes, indexCutoff);
            if (node == null) {
                break;
            }
            node.submitted = true;
            inFlight.submit(new DispatchTask(
                    event,
                    node.mod,
                    eventChannels,
                    modStates,
                    progress,
                    progressState
            ));
            submitted++;
        }
        return submitted;
    }

    private static int submitReadyDagPredecessors(FMLEvent event,
                                                  List<DagNode> nodes,
                                                  DagNode blockedNode,
                                                  int maxSubmits,
                                                  int maxInFlight,
                                                  ImmutableMap<String, EventBus> eventChannels,
                                                  Multimap<String, LoaderState.ModState> modStates,
                                                  ProgressManager.ProgressBar progress,
                                                  ProgressState progressState,
                                                  InFlightDispatches inFlight) {
        Set<DagNode> predecessors = unresolvedPredecessorClosure(blockedNode);
        int submitted = 0;
        while (submitted < maxSubmits && inFlight.pending < maxInFlight) {
            DagNode node = firstReadyParallelNode(nodes, Integer.MAX_VALUE, predecessors);
            if (node == null) {
                break;
            }
            node.submitted = true;
            inFlight.submit(new DispatchTask(
                    event,
                    node.mod,
                    eventChannels,
                    modStates,
                    progress,
                    progressState
            ));
            submitted++;
        }
        if (submitted > 0 && schedulerLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] {} DAG queued {} predecessor handler(s) for blocked serial {} ({})",
                    event.getEventType(),
                    submitted,
                    blockedNode.mod.getModId(),
                    blockedNode.mod.getName()
            );
        }
        return submitted;
    }

    private static DagNode firstReadyParallelNode(List<DagNode> nodes, int indexCutoff) {
        return firstReadyParallelNode(nodes, indexCutoff, null);
    }

    private static DagNode firstReadyParallelNode(List<DagNode> nodes, int indexCutoff, Set<DagNode> candidates) {
        for (int index = 0; index < nodes.size(); index++) {
            DagNode node = nodes.get(index);
            if (node.index >= indexCutoff) {
                continue;
            }
            if (candidates != null && !candidates.contains(node)) {
                continue;
            }
            if (node.parallelAllowed && !node.submitted && !node.completed && node.remainingDependencies == 0) {
                return node;
            }
        }
        return null;
    }

    private static DagNode firstReadySerialPredecessor(List<DagNode> nodes, DagNode blockedNode) {
        Set<DagNode> predecessors = unresolvedPredecessorClosure(blockedNode);
        for (DagNode node : nodes) {
            if (!predecessors.contains(node)) {
                continue;
            }
            if (!node.parallelAllowed && !node.submitted && !node.completed && node.remainingDependencies == 0) {
                return node;
            }
        }
        return null;
    }

    private static Set<DagNode> unresolvedPredecessorClosure(DagNode node) {
        Set<DagNode> predecessors = new LinkedHashSet<>();
        collectUnresolvedPredecessors(node, predecessors);
        return predecessors;
    }

    private static void collectUnresolvedPredecessors(DagNode node, Set<DagNode> predecessors) {
        if (node == null) {
            return;
        }
        for (DagNode predecessor : node.predecessors) {
            if (predecessor.completed || !predecessors.add(predecessor)) {
                continue;
            }
            collectUnresolvedPredecessors(predecessor, predecessors);
        }
    }

    private static boolean hasDagPath(DagNode start, DagNode target) {
        if (start == null || target == null) {
            return false;
        }
        if (start == target) {
            return true;
        }
        Set<DagNode> seen = new LinkedHashSet<>();
        List<DagNode> stack = new ArrayList<>();
        stack.add(start);
        while (!stack.isEmpty()) {
            DagNode current = stack.remove(stack.size() - 1);
            if (!seen.add(current)) {
                continue;
            }
            for (DagNode successor : current.successors) {
                if (successor == target) {
                    return true;
                }
                stack.add(successor);
            }
        }
        return false;
    }

    private static DagNode nextIncompleteSerial(List<DagNode> nodes) {
        for (DagNode node : nodes) {
            if (!node.parallelAllowed && !node.completed) {
                return node;
            }
        }
        return null;
    }

    private static DagNode firstIncompleteNode(List<DagNode> nodes) {
        for (DagNode node : nodes) {
            if (!node.completed && !node.submitted) {
                return node;
            }
        }
        return null;
    }

    private static long runDagSerialNode(FMLEvent event,
                                         DagNode node,
                                         ImmutableMap<String, EventBus> eventChannels,
                                         Multimap<String, LoaderState.ModState> modStates,
                                         ProgressManager.ProgressBar progress,
                                         ProgressState progressState,
                                         boolean continueOnModError,
                                         DagDiagnostics diagnostics) {
        node.submitted = true;
        String serialWaitMessage = waitingFor(node.mod);
        setVisibleProgressMessage(progress, serialWaitMessage);
        stepVisibleProgress(progress, node.mod, progressState);
        EarlySplashBridge.setPhaseProgress(
                "FML " + progressState.displayPhaseName + " " + serialWaitMessage,
                progressState.completedHandlers,
                progressState.totalHandlers
        );
        long serialStartedAt = System.nanoTime();
        DispatchResult result;
        try (PreInitClassPrewarmer.SerialPause ignored = PreInitClassPrewarmer.pauseDuringSerialHandler()) {
            result = dispatchSingle(event, node.mod, eventChannels, modStates, true);
        }
        long serialElapsedMillis = elapsedMillis(serialStartedAt);
        if (diagnostics != null) {
            diagnostics.recordSerial(node, System.nanoTime() - serialStartedAt);
        }
        if (serialElapsedMillis >= 250L) {
            logVisibleWait(serialWaitMessage);
        }
        commitResult(result, modStates);
        markHandlerCompleted(node.mod, progressState);
        handleFailure(event, result, modStates, progressState, continueOnModError);
        completeDagNode(node);
        return serialElapsedMillis;
    }

    private static boolean drainOneDagInFlight(FMLEvent phaseEvent,
                                               InFlightDispatches inFlight,
                                               Map<String, DagNode> nodesByModId,
                                               Multimap<String, LoaderState.ModState> modStates,
                                               ProgressManager.ProgressBar progress,
                                               ProgressState progressState,
                                               boolean continueOnModError,
                                               String reason,
                                               DagDiagnostics diagnostics) {
        if (inFlight.pending <= 0) {
            return false;
        }

        String waitMessage = inFlight.longestRunningWaitMessage();
        setVisibleProgressMessage(progress, waitMessage);
        int pendingBeforeWait = inFlight.pending;
        long waitStartedAt = System.nanoTime();
        long probeStartedAt = StartupProfiler.beginProbe();
        try {
            Future<DispatchResult> future;
            try (PreInitClassPrewarmer.SerialPause ignored = PreInitClassPrewarmer.pauseDuringBlockingWait()) {
                future = inFlight.completionService.take();
            }
            DispatchResult result = future.get();
            long waitNanos = System.nanoTime() - waitStartedAt;
            if (diagnostics != null) {
                diagnostics.recordParallel(result);
                diagnostics.recordWait(reason, waitMessage, pendingBeforeWait, result, waitNanos);
            }
            inFlight.complete(result);
            commitResult(result, modStates);
            if (result != null && result.mod != null) {
                stepVisibleProgress(progress, result.mod, progressState);
                markHandlerCompleted(result.mod, progressState);
                stepThreadedProgress(result.mod, progressState);
                DagNode node = nodesByModId.get(normalize(result.mod.getModId()));
                if (node != null) {
                    completeDagNode(node);
                }
            }
            handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            inFlight.cancelAll();
            throw new RuntimeException("Interrupted while waiting for parallel FML loading", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            DispatchResult result = DispatchResult.failed(null, cause, 0L, true);
            inFlight.complete(result);
            handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
            return true;
        } finally {
            StartupProfiler.endProbeAlways(
                    "FML scheduler " + eventType(phaseEvent) + " dag drainOne "
                            + safeReason(reason) + " " + waitMessage,
                    probeStartedAt
            );
            if (elapsedMillis(waitStartedAt) >= 250L) {
                logVisibleWait(waitMessage);
            }
        }
    }

    private static void logVisibleWait(String waitMessage) {
        if (waitMessage == null || waitMessage.isEmpty() || "Waiting for none".equals(waitMessage)) {
            return;
        }
        if (schedulerLogsEnabled()) {
            GPOM.LOGGER.info("[FmlParallelLoading] {}", waitMessage);
        }
    }

    private static void completeDagNode(DagNode node) {
        if (node == null || node.completed) {
            return;
        }
        node.completed = true;
        for (DagNode successor : node.successors) {
            successor.remainingDependencies = Math.max(0, successor.remainingDependencies - 1);
        }
    }

    private static String unresolvedDependencySummary(DagNode node) {
        if (node == null || node.predecessors.isEmpty()) {
            return "-";
        }
        List<String> unresolved = new ArrayList<>();
        for (DagNode predecessor : node.predecessors) {
            if (predecessor.completed) {
                continue;
            }
            unresolved.add(normalize(predecessor.mod.getModId())
                    + (predecessor.parallelAllowed ? "/parallel" : "/serial")
                    + (predecessor.submitted ? "/submitted" : "/pending"));
            if (unresolved.size() >= 8) {
                break;
            }
        }
        if (unresolved.isEmpty()) {
            return "-";
        }
        return unresolved.toString();
    }

    private static void clearThreadedBreadcrumbs(FMLEvent event) {
        if (!GpomEarlyConfig.parallelAutoQuarantineGlErrorsEnabled()) {
            return;
        }
        File directory = breadcrumbDirectory();
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                try {
                    Files.deleteIfExists(file.toPath());
                } catch (IOException exception) {
                    GPOM.LOGGER.warn("[FmlParallelLoading] Failed to clear stale breadcrumb {}", file, exception);
                }
            }
        }
    }

    private static File writeThreadedBreadcrumb(FMLEvent event, ModContainer mod) {
        if (!GpomEarlyConfig.parallelAutoQuarantineGlErrorsEnabled() || event == null || mod == null) {
            return null;
        }
        String modId = normalize(mod.getModId());
        if (modId.isEmpty()) {
            return null;
        }

        File directory = breadcrumbDirectory();
        String eventType = eventType(event);
        File activeFile = new File(directory, safeFileName(eventType + "-" + modId) + ".properties");
        List<String> lines = threadedBreadcrumbLines(event, mod);
        try {
            Files.createDirectories(directory.toPath());
            Files.write(activeFile.toPath(), lines, StandardCharsets.UTF_8);
            Files.write(lastThreadedBreadcrumbFile().toPath(), lines, StandardCharsets.UTF_8);
            return activeFile;
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to write threaded breadcrumb for {} ({})", modId, mod.getName(), exception);
            return null;
        }
    }

    private static List<String> threadedBreadcrumbLines(FMLEvent event, ModContainer mod) {
        Set<String> related = quarantineModSet(mod);
        related.remove(normalize(mod.getModId()));
        List<String> relatedList = new ArrayList<>(related);
        Collections.sort(relatedList);

        List<String> lines = new ArrayList<>();
        lines.add("phase=" + eventType(event));
        lines.add("phaseDisplayName=" + phaseDisplayName(event));
        lines.add("denylistKey=" + denylistProperty(event));
        lines.add("modId=" + normalize(mod.getModId()));
        lines.add("modName=" + sanitizePropertyValue(mod.getName()));
        lines.add("threadName=" + sanitizePropertyValue(Thread.currentThread().getName()));
        lines.add("startedAtNanos=" + System.nanoTime());
        lines.add("related=" + String.join(",", relatedList));
        return lines;
    }

    private static void deleteThreadedBreadcrumb(File file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException exception) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to delete threaded breadcrumb {}", file, exception);
        }
    }

    private static File breadcrumbDirectory() {
        return new File(new File(System.getProperty("user.dir", "."), "config"), "gpom-parallel-active");
    }

    private static File lastThreadedBreadcrumbFile() {
        return new File(new File(System.getProperty("user.dir", "."), "config"), "gpom-parallel-last-threaded.properties");
    }

    private static String safeFileName(String value) {
        String normalized = normalize(value);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char c = normalized.charAt(index);
            builder.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-' ? c : '_');
        }
        return builder.length() == 0 ? "unknown" : builder.toString();
    }

    private static String sanitizePropertyValue(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static String progressLabel(ModContainer mod) {
        return waitingFor(mod);
    }

    private static String waitingFor(ModContainer mod) {
        return "Waiting for " + modName(mod);
    }

    private static String modName(ModContainer mod) {
        return mod == null ? "unknown" : mod.getName();
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
        return event instanceof FMLConstructionEvent
                || event instanceof FMLPreInitializationEvent;
    }

    private static boolean dagSerialHandlersAreBarriers(FMLEvent event) {
        return event instanceof FMLConstructionEvent
                || event instanceof FMLPreInitializationEvent
                || event instanceof FMLInitializationEvent
                || event instanceof FMLPostInitializationEvent;
    }

    private static boolean dagSerialHandlersDrainWorkers(FMLEvent event) {
        return event instanceof FMLConstructionEvent
                || event instanceof FMLPreInitializationEvent;
    }

    private static boolean shouldDrainWorkersBeforeDagSerial(FMLEvent event, DagNode node, boolean phaseDrainsWorkers) {
        if (phaseDrainsWorkers) {
            return true;
        }
        if (node != null && shouldDrainWorkersBeforeSerial(event, node.mod)) {
            return true;
        }
        return event instanceof FMLPostInitializationEvent
                && node != null
                && POST_INIT_DRAIN_BEFORE_SERIAL_MODS.contains(normalize(node.mod.getModId()));
    }

    private static boolean shouldDrainWorkersBeforeSerial(FMLEvent event, ModContainer mod) {
        return isInitCapabilityAttachSerialMod(event, mod);
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
        if (schedulerLogsEnabled()) {
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
    }

    private static DispatchResult dispatchSingle(FMLEvent phaseEvent, ModContainer mod,
                                                 ImmutableMap<String, EventBus> eventChannels,
                                                 Multimap<String, LoaderState.ModState> modStates,
                                                 boolean mainThread) {
        String modId = mod.getModId();
        long startedAt = System.nanoTime();
        String originalThreadName = null;
        File breadcrumb = null;
        if (!mainThread) {
            Thread currentThread = Thread.currentThread();
            originalThreadName = currentThread.getName();
            currentThread.setName("GPOM FML " + phaseDisplayName(phaseEvent) + " - " + modId);
            breadcrumb = writeThreadedBreadcrumb(phaseEvent, mod);
            if (schedulerLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[FmlParallelLoading] Starting threaded {} for {} ({})",
                        phaseEvent.getEventType(),
                        modId,
                        mod.getName()
                );
            }
        }
        try {
            if (requiredDependencyErrored(mod, modStates)) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Skipping event {} and marking errored mod {} since a required dependency has errored",
                        phaseEvent.getEventType(),
                        modId
                );
                return DispatchResult.state(mod, LoaderState.ModState.ERRORED, System.nanoTime() - startedAt, !mainThread);
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
            Loader loader = mainThread ? Loader.instance() : null;
            ModContainer previousActiveContainer = null;
            if (mainThread) {
                previousActiveContainer = loader.activeModContainer();
                loader.setActiveModContainer(mod);
            } else {
                FmlParallelLoadingContext.setActiveContainer(mod);
            }
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
                    StartupProfiler.endProbe("FML dispatch " + eventType(phaseEvent) + " eventBusPost " + modId, postStartedAt);
                }
            } finally {
                if (mainThread) {
                    loader.setActiveModContainer(previousActiveContainer);
                } else {
                    FmlParallelLoadingContext.clearActiveContainer();
                }
                ThreadContext.remove("mod");
            }

            DispatchResult result;
            long elapsedNanos = System.nanoTime() - startedAt;
            if (event instanceof FMLStateEvent) {
                result = DispatchResult.state(mod, ((FMLStateEvent) event).getModState(), elapsedNanos, !mainThread);
            } else {
                result = DispatchResult.ok(mod, elapsedNanos, !mainThread);
            }
            if (!mainThread && schedulerLogsEnabled()) {
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
            long elapsedNanos = System.nanoTime() - startedAt;
            if (!mainThread) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Failed threaded {} for {} ({}) after {} ms",
                        phaseEvent.getEventType(),
                        modId,
                        mod.getName(),
                        elapsedNanos / 1_000_000L,
                        throwable
                );
            }
            return DispatchResult.failed(mod, throwable, elapsedNanos, !mainThread);
        } finally {
            deleteThreadedBreadcrumb(breadcrumb);
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
        boolean glThreadFailure = result.mod != null && isGlThreadFailure(result.throwable);
        if (glThreadFailure && autoQuarantineGlFailure(event, result)) {
            rethrow(new RuntimeException(
                    "GPOM auto-quarantined a threaded OpenGL failure; relaunch required for main-thread retry",
                    result.throwable
            ));
            return;
        }
        if (glThreadFailure) {
            logManualGlFailure(event, result);
        }
        if (!continueOnModError) {
            ModContainer mod = result.mod;
            if (mod != null) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Aborting {} after {} failure in {} ({})",
                        event.getEventType(),
                        result.workerThread ? "threaded" : "serial",
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

    private static boolean autoQuarantineGlFailure(FMLEvent event, DispatchResult result) {
        if (!GpomEarlyConfig.parallelAutoQuarantineGlErrorsEnabled()
                || result == null
                || result.mod == null
                || result.throwable == null
                || !isGlThreadFailure(result.throwable)) {
            return false;
        }

        String denylistKey = denylistProperty(event);
        if (denylistKey == null) {
            return false;
        }

        Set<String> quarantineMods = quarantineModSet(result.mod);
        boolean changed = GpomEarlyConfig.appendCsvValues(denylistKey, quarantineMods);
        GPOM.LOGGER.error(
                "[FmlParallelLoading] Auto-quarantined threaded OpenGL failure in {} ({}); appended {} to {} changed={}. Relaunch required for main-thread retry.",
                result.mod.getModId(),
                result.mod.getName(),
                quarantineMods,
                denylistKey,
                changed,
                result.throwable
        );
        return true;
    }

    private static void logManualGlFailure(FMLEvent event, DispatchResult result) {
        if (result == null || result.mod == null) {
            return;
        }
        GPOM.LOGGER.warn(
                "[FmlParallelLoading] Threaded OpenGL failure in {} ({}) during {}; leaving config unchanged because fml.parallel.autoQuarantineGlErrors.enabled=false",
                result.mod.getModId(),
                result.mod.getName(),
                event.getEventType()
        );
    }

    private static Set<String> quarantineModSet(ModContainer mod) {
        Set<String> mods = new LinkedHashSet<>();
        String modId = normalize(mod.getModId());
        if (isUsefulQuarantineModId(modId)) {
            mods.add(modId);
        }
        if (GpomEarlyConfig.parallelAutoQuarantineGlErrorsIncludeRelatedMods()) {
            for (String related : dependencyLabels(mod)) {
                if (isUsefulQuarantineModId(related)) {
                    mods.add(related);
                }
            }
        }
        return mods;
    }

    private static boolean isGlThreadFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String className = current.getClass().getName().toLowerCase(Locale.ROOT);
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(Locale.ROOT);
            if (className.contains("opengl") || className.contains("lwjgl")) {
                return true;
            }
            if ((message.contains("opengl") || message.contains("gl context") || message.contains("no context"))
                    && (message.contains("thread") || message.contains("current") || message.contains("context"))) {
                return true;
            }
            for (StackTraceElement element : current.getStackTrace()) {
                String elementClass = element.getClassName().toLowerCase(Locale.ROOT);
                if (elementClass.startsWith("org.lwjgl.opengl")
                        && (message.contains("thread") || message.contains("context") || message.contains("current"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String denylistProperty(FMLEvent event) {
        if (event instanceof FMLConstructionEvent) {
            return "fml.parallel.construct.denylist";
        }
        if (event instanceof FMLPreInitializationEvent) {
            return "fml.parallel.preInit.denylist";
        }
        if (event instanceof FMLInitializationEvent) {
            return "fml.parallel.init.denylist";
        }
        if (event instanceof FMLPostInitializationEvent) {
            return "fml.parallel.postInit.denylist";
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return "fml.parallel.loadComplete.denylist";
        }
        return null;
    }

    private static boolean isUsefulQuarantineModId(String modId) {
        return modId != null
                && !modId.isEmpty()
                && !"minecraft".equals(modId)
                && !"mcp".equals(modId)
                && !"fml".equals(modId)
                && !"forge".equals(modId);
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

    private static boolean isParallelAllowed(FMLEvent event, ModContainer mod, Set<String> parallelMods, Set<String> deniedMods) {
        return isParallelAllowed(mod, parallelMods, deniedMods)
                && !isConstructionMainThreadOnlyMod(event, mod)
                && !isInitCapabilityAttachSerialMod(event, mod)
                && !requiresMainThreadForClientLifecycle(event, mod);
    }

    private static boolean isConstructionMainThreadOnlyMod(FMLEvent event, ModContainer mod) {
        return event instanceof FMLConstructionEvent
                && mod != null
                && "jei".equals(normalize(mod.getModId()));
    }

    private static boolean isInitCapabilityAttachSerialMod(FMLEvent event, ModContainer mod) {
        return event instanceof FMLInitializationEvent
                && mod != null
                && INIT_CAPABILITY_ATTACH_SERIAL_MODS.contains(normalize(mod.getModId()));
    }

    private static boolean requiresMainThreadForClientLifecycle(FMLEvent event, ModContainer mod) {
        if (!GpomEarlyConfig.parallelClientLifecycleOpenGlScanEnabled()
                || !isClientLifecycleThreadAffinityPhase(event)
                || !modSourceHasOpenGlReference(mod)) {
            return false;
        }
        logOpenGlThreadAffinitySerial(event, mod);
        return true;
    }

    private static boolean isClientLifecycleThreadAffinityPhase(FMLEvent event) {
        return event instanceof FMLPreInitializationEvent
                || event instanceof FMLInitializationEvent
                || event instanceof FMLPostInitializationEvent
                || event instanceof FMLLoadCompleteEvent;
    }

    private static void logOpenGlThreadAffinitySerial(FMLEvent event, ModContainer mod) {
        if (!schedulerLogsEnabled() || mod == null) {
            return;
        }
        String key = eventType(event) + ":" + normalize(mod.getModId());
        boolean shouldLog;
        synchronized (REPORTED_OPENGL_SERIAL_MODS) {
            shouldLog = REPORTED_OPENGL_SERIAL_MODS.add(key);
        }
        if (!shouldLog) {
            return;
        }
        GPOM.LOGGER.debug(
                "[FmlParallelLoading] Keeping {} ({}) on the main thread for {} because its source references LWJGL/OpenGL bytecode",
                mod.getModId(),
                mod.getName(),
                event.getEventType()
        );
    }

    private static boolean modSourceHasOpenGlReference(ModContainer mod) {
        if (mod == null || mod.getSource() == null) {
            return false;
        }
        File source = mod.getSource();
        String key = source.getAbsolutePath() + ":" + source.lastModified() + ":" + source.length();
        Boolean cached = OPENGL_REFERENCE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        try {
            result = sourceHasOpenGlReference(source);
        } catch (Throwable throwable) {
            if (schedulerLogsEnabled()) {
                GPOM.LOGGER.warn(
                        "[FmlParallelLoading] Failed to scan {} ({}) for OpenGL thread-affinity references; leaving it eligible for worker dispatch",
                        mod.getModId(),
                        mod.getName(),
                        throwable
                );
            }
        }
        OPENGL_REFERENCE_CACHE.put(key, result);
        return result;
    }

    private static boolean sourceHasOpenGlReference(File source) throws IOException {
        if (!source.exists()) {
            return false;
        }
        if (source.isDirectory()) {
            return directoryHasOpenGlReference(source);
        }
        if (!source.isFile()) {
            return false;
        }
        try (JarFile jarFile = new JarFile(source)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream input = jarFile.getInputStream(entry)) {
                    if (containsAny(readAllBytes(input), OPENGL_THREAD_AFFINITY_PATTERNS)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean directoryHasOpenGlReference(File directory) throws IOException {
        File[] children = directory.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (directoryHasOpenGlReference(child)) {
                    return true;
                }
                continue;
            }
            if (!child.isFile() || !child.getName().endsWith(".class")) {
                continue;
            }
            if (containsAny(Files.readAllBytes(child.toPath()), OPENGL_THREAD_AFFINITY_PATTERNS)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean containsAny(byte[] data, byte[][] patterns) {
        for (byte[] pattern : patterns) {
            if (contains(data, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(byte[] data, byte[] pattern) {
        if (data.length < pattern.length) {
            return false;
        }
        int limit = data.length - pattern.length;
        for (int index = 0; index <= limit; index++) {
            int matched = 0;
            while (matched < pattern.length && data[index + matched] == pattern[matched]) {
                matched++;
            }
            if (matched == pattern.length) {
                return true;
            }
        }
        return false;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static int countParallelAllowed(FMLEvent event, List<ModContainer> activeModList, Set<String> parallelMods, Set<String> deniedMods) {
        int count = 0;
        for (ModContainer mod : activeModList) {
            if (isParallelAllowed(event, mod, parallelMods, deniedMods)) {
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

    private static Set<String> directDependencyLabels(ModContainer mod) {
        Set<String> labels = new LinkedHashSet<>();
        if (mod == null) {
            return labels;
        }
        addLabels(labels, mod.getDependencies());
        return labels;
    }

    private static Set<String> directDependantLabels(ModContainer mod) {
        Set<String> labels = new LinkedHashSet<>();
        if (mod == null) {
            return labels;
        }
        addLabels(labels, mod.getDependants());
        return labels;
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
        Set<String> configured;
        if (event instanceof FMLConstructionEvent) {
            configured = GpomEarlyConfig.parallelConstructDenylist();
        } else if (event instanceof FMLPreInitializationEvent) {
            configured = GpomEarlyConfig.parallelPreInitDenylist();
        } else if (event instanceof FMLPostInitializationEvent) {
            configured = GpomEarlyConfig.parallelPostInitDenylist();
        } else if (event instanceof FMLInitializationEvent) {
            configured = GpomEarlyConfig.parallelInitDenylist();
        } else if (event instanceof FMLLoadCompleteEvent) {
            configured = GpomEarlyConfig.parallelLoadCompleteDenylist();
        } else {
            configured = new LinkedHashSet<>();
        }
        return withForcedDeniedMods(event, configured);
    }

    private static Set<String> withForcedDeniedMods(FMLEvent event, Set<String> configured) {
        boolean forceInitSerial = event instanceof FMLInitializationEvent;
        boolean forceTransformerSerial = event instanceof FMLConstructionEvent || event instanceof FMLPreInitializationEvent;
        if (!forceInitSerial && !forceTransformerSerial) {
            return configured;
        }
        Set<String> merged = new LinkedHashSet<>(configured);
        if (forceInitSerial) {
            merged.addAll(INIT_CAPABILITY_ATTACH_SERIAL_MODS);
        }
        if (forceTransformerSerial) {
            merged.addAll(TRANSFORMER_CLASSLOAD_SERIAL_MODS);
        }
        return merged;
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
        if (schedulerLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Auto-selected {} worker(s) for {} from cpuCount={} totalMemory={} MiB",
                    autoWorkers,
                    event == null ? "unknown" : event.getEventType(),
                    Runtime.getRuntime().availableProcessors(),
                    totalPhysicalMemoryMib()
            );
        }
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

    private static boolean schedulerLogsEnabled() {
        return GpomEarlyConfig.fmlSchedulerLogsEnabled();
    }

    private static Set<String> fixedSet(String... values) {
        Set<String> set = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.trim().isEmpty()) {
                    set.add(normalize(value));
                }
            }
        }
        return Collections.unmodifiableSet(set);
    }

    private static final class DagNode {
        private final int index;
        private final ModContainer mod;
        private final boolean parallelAllowed;
        private final List<DagNode> predecessors = new ArrayList<>();
        private final List<DagNode> successors = new ArrayList<>();
        private int remainingDependencies;
        private boolean submitted;
        private boolean completed;

        private DagNode(int index, ModContainer mod, boolean parallelAllowed) {
            this.index = index;
            this.mod = mod;
            this.parallelAllowed = parallelAllowed;
        }
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
            return dispatchSingle(phaseEvent, mod, eventChannels, modStates, false);
        }
    }

    private static final class InFlightDispatches {
        private final CompletionService<DispatchResult> completionService;
        private final List<ModContainer> mods = new ArrayList<>();
        private final List<Future<DispatchResult>> futures = new ArrayList<>();
        private final List<Long> submittedAtNanos = new ArrayList<>();
        private int pending;

        private InFlightDispatches(CompletionService<DispatchResult> completionService) {
            this.completionService = completionService;
        }

        private void submit(DispatchTask task) {
            mods.add(task.mod);
            submittedAtNanos.add(System.nanoTime());
            futures.add(completionService.submit(task));
            pending++;
        }

        private String longestRunningWaitMessage() {
            if (mods.isEmpty()) {
                return "Waiting for none";
            }

            int index = 0;
            long oldest = submittedAtNanos.isEmpty() ? System.nanoTime() : submittedAtNanos.get(0);
            for (int i = 1; i < mods.size() && i < submittedAtNanos.size(); i++) {
                long submittedAt = submittedAtNanos.get(i);
                if (submittedAt < oldest) {
                    oldest = submittedAt;
                    index = i;
                }
            }
            return waitingFor(mods.get(index));
        }

        private void complete(DispatchResult result) {
            pending = Math.max(0, pending - 1);
            if (result != null && result.mod != null) {
                int index = mods.indexOf(result.mod);
                if (index >= 0) {
                    mods.remove(index);
                    if (index < futures.size()) {
                        futures.remove(index);
                    }
                    if (index < submittedAtNanos.size()) {
                        submittedAtNanos.remove(index);
                    }
                }
            }
            if (pending == 0) {
                mods.clear();
                futures.clear();
                submittedAtNanos.clear();
            }
        }

        private void cancelAll() {
            for (Future<DispatchResult> future : futures) {
                future.cancel(true);
            }
            pending = 0;
            mods.clear();
            futures.clear();
            submittedAtNanos.clear();
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

    private static final class DagDiagnostics {
        private static final int TOP_LIMIT = 12;
        private final boolean enabled;
        private final String eventType;
        private final String logPrefix;
        private final List<HandlerTiming> serialHandlers = new ArrayList<>();
        private final List<HandlerTiming> parallelHandlers = new ArrayList<>();
        private final List<WaitTiming> waits = new ArrayList<>();

        private DagDiagnostics(FMLEvent event) {
            boolean construct = event instanceof FMLConstructionEvent;
            boolean preInit = event instanceof FMLPreInitializationEvent;
            boolean loadComplete = event instanceof FMLLoadCompleteEvent;
            this.enabled = (construct && GpomEarlyConfig.startupProfilerConstructCriticalPathLogsEnabled())
                    || (preInit && GpomEarlyConfig.startupProfilerPreInitCriticalPathLogsEnabled())
                    || (loadComplete && GpomEarlyConfig.startupProfilerLoadCompleteCriticalPathLogsEnabled());
            this.eventType = eventType(event);
            this.logPrefix = loadComplete
                    ? "LoadCompleteCriticalPath"
                    : construct ? "ConstructionCriticalPath" : "PreInitCriticalPath";
        }

        private void recordSerial(DagNode node, long elapsedNanos) {
            if (!enabled || node == null || node.mod == null || elapsedNanos <= 0L) {
                return;
            }
            serialHandlers.add(new HandlerTiming(node.mod, elapsedNanos, node.index));
        }

        private void recordParallel(DispatchResult result) {
            if (!enabled || result == null || result.mod == null || result.elapsedNanos <= 0L) {
                return;
            }
            parallelHandlers.add(new HandlerTiming(result.mod, result.elapsedNanos, -1));
        }

        private void recordWait(String reason, String waitMessage, int pendingBeforeWait, DispatchResult result, long waitNanos) {
            if (!enabled || waitNanos <= 0L) {
                return;
            }
            ModContainer completed = result == null ? null : result.mod;
            waits.add(new WaitTiming(reason, waitMessage, pendingBeforeWait, completed, waitNanos));
        }

        private void logSummary(long wallMillis) {
            if (!enabled) {
                return;
            }
            long serialTotal = totalHandlerNanos(serialHandlers);
            long parallelTotal = totalHandlerNanos(parallelHandlers);
            long waitTotal = totalWaitNanos(waits);
            long remainingWall = Math.max(0L, wallMillis * 1_000_000L - serialTotal - waitTotal);
            GPOM.LOGGER.info(
                    "[{}] {} DAG wall={} ms serialMainThread={} ms serialHandlers={} parallelInclusive={} ms parallelHandlers={} blockingWait={} ms waits={} remainingWall={} ms",
                    logPrefix,
                    eventType,
                    wallMillis,
                    serialTotal / 1_000_000L,
                    serialHandlers.size(),
                    parallelTotal / 1_000_000L,
                    parallelHandlers.size(),
                    waitTotal / 1_000_000L,
                    waits.size(),
                    remainingWall / 1_000_000L
            );
            logTopHandlers("serial", serialHandlers);
            logTopHandlers("parallel", parallelHandlers);
            logTopWaits();
        }

        private void logTopHandlers(String label, List<HandlerTiming> timings) {
            List<HandlerTiming> sorted = new ArrayList<>(timings);
            Collections.sort(sorted, Comparator.comparingLong((HandlerTiming timing) -> timing.elapsedNanos).reversed());
            int limit = Math.min(TOP_LIMIT, sorted.size());
            for (int i = 0; i < limit; i++) {
                HandlerTiming timing = sorted.get(i);
                GPOM.LOGGER.info(
                        "[{}] top {} #{} {} ms index={} - {} ({})",
                        logPrefix,
                        label,
                        i + 1,
                        timing.elapsedNanos / 1_000_000L,
                        timing.index,
                        timing.modId,
                        timing.modName
                );
            }
        }

        private void logTopWaits() {
            List<WaitTiming> sorted = new ArrayList<>(waits);
            Collections.sort(sorted, Comparator.comparingLong((WaitTiming timing) -> timing.elapsedNanos).reversed());
            int limit = Math.min(TOP_LIMIT, sorted.size());
            for (int i = 0; i < limit; i++) {
                WaitTiming timing = sorted.get(i);
                GPOM.LOGGER.info(
                        "[{}] top wait #{} {} ms reason={} pending={} waitedFor={} completed={} ({})",
                        logPrefix,
                        i + 1,
                        timing.elapsedNanos / 1_000_000L,
                        timing.reason,
                        timing.pendingBeforeWait,
                        timing.waitMessage,
                        timing.completedModId,
                        timing.completedModName
                );
            }
        }

        private static long totalHandlerNanos(List<HandlerTiming> timings) {
            long total = 0L;
            for (HandlerTiming timing : timings) {
                total += timing.elapsedNanos;
            }
            return total;
        }

        private static long totalWaitNanos(List<WaitTiming> timings) {
            long total = 0L;
            for (WaitTiming timing : timings) {
                total += timing.elapsedNanos;
            }
            return total;
        }
    }

    private static final class HandlerTiming {
        private final String modId;
        private final String modName;
        private final long elapsedNanos;
        private final int index;

        private HandlerTiming(ModContainer mod, long elapsedNanos, int index) {
            this.modId = mod == null ? "unknown" : normalize(mod.getModId());
            this.modName = mod == null ? "unknown" : mod.getName();
            this.elapsedNanos = elapsedNanos;
            this.index = index;
        }
    }

    private static final class WaitTiming {
        private final String reason;
        private final String waitMessage;
        private final int pendingBeforeWait;
        private final String completedModId;
        private final String completedModName;
        private final long elapsedNanos;

        private WaitTiming(String reason, String waitMessage, int pendingBeforeWait, ModContainer completed, long elapsedNanos) {
            this.reason = safeReason(reason);
            this.waitMessage = waitMessage == null ? "Waiting for unknown" : waitMessage;
            this.pendingBeforeWait = pendingBeforeWait;
            this.completedModId = completed == null ? "unknown" : normalize(completed.getModId());
            this.completedModName = completed == null ? "unknown" : completed.getName();
            this.elapsedNanos = elapsedNanos;
        }
    }

    private static final class DispatchResult {
        private final ModContainer mod;
        private final LoaderState.ModState state;
        private final Throwable throwable;
        private final long elapsedNanos;
        private final boolean workerThread;

        private DispatchResult(ModContainer mod, LoaderState.ModState state, Throwable throwable, long elapsedNanos, boolean workerThread) {
            this.mod = mod;
            this.state = state;
            this.throwable = throwable;
            this.elapsedNanos = elapsedNanos;
            this.workerThread = workerThread;
        }

        private static DispatchResult ok(ModContainer mod) {
            return ok(mod, 0L);
        }

        private static DispatchResult ok(ModContainer mod, long elapsedNanos) {
            return ok(mod, elapsedNanos, false);
        }

        private static DispatchResult ok(ModContainer mod, long elapsedNanos, boolean workerThread) {
            return new DispatchResult(mod, null, null, elapsedNanos, workerThread);
        }

        private static DispatchResult state(ModContainer mod, LoaderState.ModState state) {
            return state(mod, state, 0L);
        }

        private static DispatchResult state(ModContainer mod, LoaderState.ModState state, long elapsedNanos) {
            return state(mod, state, elapsedNanos, false);
        }

        private static DispatchResult state(ModContainer mod, LoaderState.ModState state, long elapsedNanos, boolean workerThread) {
            return new DispatchResult(mod, state, null, elapsedNanos, workerThread);
        }

        private static DispatchResult failed(ModContainer mod, Throwable throwable) {
            return failed(mod, throwable, 0L);
        }

        private static DispatchResult failed(ModContainer mod, Throwable throwable, long elapsedNanos) {
            return failed(mod, throwable, elapsedNanos, false);
        }

        private static DispatchResult failed(ModContainer mod, Throwable throwable, long elapsedNanos, boolean workerThread) {
            return new DispatchResult(mod, null, throwable, elapsedNanos, workerThread);
        }
    }
}
