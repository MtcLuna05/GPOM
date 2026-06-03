package com.l.gpom.optimization;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLStateEvent;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import org.apache.logging.log4j.ThreadContext;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final int PROGRESS_LOG_INTERVAL = 25;

    private FmlParallelLoadingScheduler() {
    }

    public static boolean shouldParallelize(FMLEvent event) {
        return (event instanceof FMLPostInitializationEvent && GpomEarlyConfig.parallelPostInitEnabled())
                || (event instanceof FMLLoadCompleteEvent && GpomEarlyConfig.parallelLoadCompleteEnabled());
    }

    public static void propagate(FMLEvent event, List<ModContainer> activeModList,
                                 ImmutableMap<String, EventBus> eventChannels,
                                 Multimap<String, LoaderState.ModState> modStates) {
        Set<String> parallelMods = parallelMods(event);
        Set<String> deniedMods = deniedMods(event);
        boolean continueOnModError = continueOnModError(event);
        if (parallelMods.isEmpty()) {
            return;
        }

        int parallelEligibleHandlers = countParallelAllowed(activeModList, parallelMods, deniedMods);
        int workers = Math.min(GpomEarlyConfig.parallelWorkers(), Math.max(1, parallelEligibleHandlers));
        ExecutorService executor = Executors.newFixedThreadPool(workers, runnable -> {
            Thread thread = new Thread(runnable, "GPOM FML parallel loader");
            thread.setDaemon(true);
            return thread;
        });

        ProgressManager.ProgressBar progress = ProgressManager.push(
                event.description(),
                activeModList.size(),
                true
        );
        WorkerProgressBars workerProgress = null;
        int workerProgressLanes = Math.min(workers, Math.min(parallelEligibleHandlers, GpomEarlyConfig.parallelProgressBarWorkerLanes()));
        if (GpomEarlyConfig.parallelProgressBarEnabled() && parallelEligibleHandlers > 0) {
            workerProgress = WorkerProgressBars.push(event.description(), workerProgressLanes, parallelEligibleHandlers);
        }

        long startedAt = System.nanoTime();
        int parallelHandlers = 0;
        ProgressState progressState = new ProgressState(event.getEventType(), activeModList.size(), parallelEligibleHandlers, workerProgressLanes);
        boolean completed = false;
        try {
            List<ModContainer> batch = new ArrayList<>();
            for (ModContainer mod : activeModList) {
                boolean parallelAllowed = isParallelAllowed(mod, parallelMods, deniedMods);

                if (parallelAllowed) {
                    if (hasOrderDependencyWithBatch(mod, batch)) {
                        parallelHandlers += flushBatch(event, batch, eventChannels, modStates, progress, workerProgress, progressState, executor, continueOnModError);
                        batch.clear();
                    }
                    batch.add(mod);
                    continue;
                }

                parallelHandlers += flushBatch(event, batch, eventChannels, modStates, progress, workerProgress, progressState, executor, continueOnModError);
                batch.clear();
                DispatchResult result = dispatchSingle(event, mod, eventChannels, modStates, true);
                commitResult(result, modStates);
                stepMainProgress(progress, mod, progressState);
                handleFailure(event, result, modStates, progressState, continueOnModError);
            }

            parallelHandlers += flushBatch(event, batch, eventChannels, modStates, progress, workerProgress, progressState, executor, continueOnModError);
            completed = true;
        } finally {
            if (completed && workerProgress != null) {
                workerProgress.pop();
            }
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

    private static int flushBatch(FMLEvent phaseEvent, List<ModContainer> batch,
                                  ImmutableMap<String, EventBus> eventChannels,
                                  Multimap<String, LoaderState.ModState> modStates,
                                  ProgressManager.ProgressBar progress,
                                  WorkerProgressBars workerProgress,
                                  ProgressState progressState,
                                  ExecutorService executor,
                                  boolean continueOnModError) {
        if (batch.isEmpty()) {
            return 0;
        }

        if (batch.size() == 1) {
            ModContainer mod = batch.get(0);
            int displayLane = progressState.nextDisplayLane();
            DispatchResult result = dispatchSingle(phaseEvent, mod, eventChannels, modStates, false)
                    .withDisplayLane(displayLane);
            commitResult(result, modStates);
            stepMainProgress(progress, mod, progressState);
            stepThreadedProgress(workerProgress, mod, progressState, displayLane);
            handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
            return 1;
        }

        CompletionService<DispatchResult> completionService = new ExecutorCompletionService<>(executor);
        List<Future<DispatchResult>> futures = new ArrayList<>(batch.size());
        for (ModContainer mod : batch) {
            futures.add(completionService.submit(new DispatchTask(
                    phaseEvent,
                    mod,
                    eventChannels,
                    modStates,
                    progressState.nextDisplayLane()
            )));
        }

        List<DispatchResult> results = new ArrayList<>(Collections.nCopies(batch.size(), null));
        Map<ModContainer, Integer> resultIndexes = new IdentityHashMap<>();
        for (int i = 0; i < batch.size(); i++) {
            resultIndexes.put(batch.get(i), i);
        }

        for (int i = 0; i < batch.size(); i++) {
            try {
                DispatchResult result = completionService.take().get();
                Integer index = result == null || result.mod == null ? null : resultIndexes.get(result.mod);
                if (index == null) {
                    index = i;
                }
                results.set(index, result);
                ModContainer mod = result == null || result.mod == null ? batch.get(index) : result.mod;
                stepMainProgress(progress, mod, progressState);
                stepThreadedProgress(workerProgress, mod, progressState, result == null ? -1 : result.displayLane);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                for (Future<DispatchResult> future : futures) {
                    future.cancel(true);
                }
                throw new RuntimeException("Interrupted while waiting for parallel FML loading", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                DispatchResult result = DispatchResult.failed(null, cause);
                results.set(i, result);
                stepMainProgress(progress, batch.get(i), progressState);
                stepThreadedProgress(workerProgress, batch.get(i), progressState, -1);
            }
        }

        for (DispatchResult result : results) {
            commitResult(result, modStates);
        }
        for (DispatchResult result : results) {
            handleFailure(phaseEvent, result, modStates, progressState, continueOnModError);
        }
        return batch.size();
    }

    private static void stepMainProgress(ProgressManager.ProgressBar progress, ModContainer mod, ProgressState state) {
        if (progress == null || mod == null) {
            return;
        }
        progress.step(progressLabel("done", mod, ++state.completedHandlers, state.totalHandlers));
    }

    private static void stepThreadedProgress(WorkerProgressBars progress, ModContainer mod, ProgressState state, int displayLane) {
        if (mod == null) {
            return;
        }
        ++state.completedParallelHandlers;
        if (progress != null) {
            progress.step(displayLane, mod);
        }
        maybeLogProgressSnapshot(mod, state);
    }

    private static String progressLabel(String prefix, ModContainer mod, int completed, int total) {
        StringBuilder label = new StringBuilder(prefix);
        if (completed >= 0 && total > 0) {
            label.append(' ').append(completed).append('/').append(total);
        }
        label.append(' ').append(mod.getName());
        return label.toString();
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
        try {
            if (requiredDependencyErrored(mod, modStates)) {
                GPOM.LOGGER.error(
                        "[FmlParallelLoading] Skipping event {} and marking errored mod {} since a required dependency has errored",
                        phaseEvent.getEventType(),
                        modId
                );
                return DispatchResult.state(mod, LoaderState.ModState.ERRORED);
            }

            FMLEvent event = cloneEvent(phaseEvent);
            event.applyModContainer(mod);
            ThreadContext.put("mod", modId);
            if (!mainThread) {
                FmlParallelLoadingContext.setActiveContainer(mod);
            }
            try {
                EventBus eventBus = eventChannels.get(modId);
                if (eventBus == null) {
                    throw new IllegalStateException("Missing FML event bus for mod " + modId);
                }
                eventBus.post(event);
            } finally {
                if (!mainThread) {
                    FmlParallelLoadingContext.clearActiveContainer();
                }
                ThreadContext.remove("mod");
            }

            if (event instanceof FMLStateEvent) {
                return DispatchResult.state(mod, ((FMLStateEvent) event).getModState());
            }
            return DispatchResult.ok(mod);
        } catch (Throwable throwable) {
            return DispatchResult.failed(mod, throwable);
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
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitAllowlist();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteAllowlist();
        }
        return new LinkedHashSet<>();
    }

    private static Set<String> deniedMods(FMLEvent event) {
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitDenylist();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteDenylist();
        }
        return new LinkedHashSet<>();
    }

    private static boolean continueOnModError(FMLEvent event) {
        if (event instanceof FMLPostInitializationEvent) {
            return GpomEarlyConfig.parallelPostInitContinueOnModError();
        }
        if (event instanceof FMLLoadCompleteEvent) {
            return GpomEarlyConfig.parallelLoadCompleteContinueOnModError();
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static long usedHeapMib() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
    }

    private static final class DispatchTask implements Callable<DispatchResult> {
        private final FMLEvent phaseEvent;
        private final ModContainer mod;
        private final ImmutableMap<String, EventBus> eventChannels;
        private final Multimap<String, LoaderState.ModState> modStates;
        private final int displayLane;

        private DispatchTask(FMLEvent phaseEvent, ModContainer mod,
                             ImmutableMap<String, EventBus> eventChannels,
                             Multimap<String, LoaderState.ModState> modStates,
                             int displayLane) {
            this.phaseEvent = phaseEvent;
            this.mod = mod;
            this.eventChannels = eventChannels;
            this.modStates = modStates;
            this.displayLane = displayLane;
        }

        @Override
        public DispatchResult call() {
            return dispatchSingle(phaseEvent, mod, eventChannels, modStates, false)
                    .withDisplayLane(displayLane);
        }
    }

    private static final class WorkerProgressBars {
        private final ProgressManager.ProgressBar[] bars;
        private final int[] completed;
        private final int[] totals;

        private WorkerProgressBars(ProgressManager.ProgressBar[] bars, int[] totals) {
            this.bars = bars;
            this.totals = totals;
            this.completed = new int[bars.length];
        }

        private static WorkerProgressBars push(String eventDescription, int lanes, int totalParallelHandlers) {
            ProgressManager.ProgressBar[] bars = new ProgressManager.ProgressBar[lanes];
            int[] totals = new int[lanes];
            int base = totalParallelHandlers / lanes;
            int remainder = totalParallelHandlers % lanes;
            for (int lane = 0; lane < lanes; lane++) {
                totals[lane] = base + (lane < remainder ? 1 : 0);
                bars[lane] = ProgressManager.push(
                        "GPOM worker lane " + (lane + 1) + "/" + lanes + " " + eventDescription,
                        Math.max(1, totals[lane]),
                        true
                );
            }
            return new WorkerProgressBars(bars, totals);
        }

        private void step(int displayLane, ModContainer mod) {
            if (displayLane < 0 || displayLane >= bars.length || mod == null) {
                return;
            }
            int done = ++completed[displayLane];
            bars[displayLane].step(progressLabel("done", mod, done, totals[displayLane]));
        }

        private void pop() {
            for (int lane = bars.length - 1; lane >= 0; lane--) {
                ProgressManager.pop(bars[lane]);
            }
        }
    }

    private static final class ProgressState {
        private final String eventType;
        private final int totalHandlers;
        private final int totalParallelHandlers;
        private final int displayLanes;
        private int completedHandlers;
        private int completedParallelHandlers;
        private int nextDisplayLane;
        private int lastLoggedParallelHandlers;
        private int continuedFailures;

        private ProgressState(String eventType, int totalHandlers, int totalParallelHandlers, int displayLanes) {
            this.eventType = eventType;
            this.totalHandlers = totalHandlers;
            this.totalParallelHandlers = totalParallelHandlers;
            this.displayLanes = Math.max(1, displayLanes);
        }

        private int nextDisplayLane() {
            if (totalParallelHandlers <= 0) {
                return -1;
            }
            return nextDisplayLane++ % displayLanes;
        }
    }

    private static final class DispatchResult {
        private final ModContainer mod;
        private final LoaderState.ModState state;
        private final Throwable throwable;
        private final int displayLane;

        private DispatchResult(ModContainer mod, LoaderState.ModState state, Throwable throwable, int displayLane) {
            this.mod = mod;
            this.state = state;
            this.throwable = throwable;
            this.displayLane = displayLane;
        }

        private static DispatchResult ok(ModContainer mod) {
            return new DispatchResult(mod, null, null, -1);
        }

        private static DispatchResult state(ModContainer mod, LoaderState.ModState state) {
            return new DispatchResult(mod, state, null, -1);
        }

        private static DispatchResult failed(ModContainer mod, Throwable throwable) {
            return new DispatchResult(mod, null, throwable, -1);
        }

        private DispatchResult withDisplayLane(int displayLane) {
            return new DispatchResult(mod, state, throwable, displayLane);
        }
    }
}
