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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class FmlParallelLoadingScheduler {
    private static final Object MOD_STATE_LOCK = new Object();

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
        if (parallelMods.isEmpty()) {
            return;
        }

        int workers = Math.min(GpomEarlyConfig.parallelWorkers(), Math.max(1, parallelMods.size()));
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
        ProgressManager.ProgressBar threadedProgress = null;
        if (GpomEarlyConfig.parallelProgressBarEnabled()) {
            threadedProgress = ProgressManager.push("GPOM threaded " + event.description(), 1, true);
        }

        long startedAt = System.nanoTime();
        int parallelHandlers = 0;
        boolean completed = false;
        try {
            List<ModContainer> batch = new ArrayList<>();
            for (ModContainer mod : activeModList) {
                if (isParallelAllowed(mod, parallelMods) && !hasOrderDependencyWithBatch(mod, batch)) {
                    batch.add(mod);
                    continue;
                }

                parallelHandlers += flushBatch(event, batch, eventChannels, modStates, progress, threadedProgress, executor);
                batch.clear();

                progress.step(mod.getName());
                DispatchResult result = dispatchSingle(event, mod, eventChannels, modStates, true);
                commitResult(result, modStates);
                rethrowIfFailed(result);

                if (isParallelAllowed(mod, parallelMods)) {
                    batch.add(mod);
                }
            }

            parallelHandlers += flushBatch(event, batch, eventChannels, modStates, progress, threadedProgress, executor);
            if (threadedProgress != null) {
                threadedProgress.step("completed");
            }
            completed = true;
        } finally {
            if (completed && threadedProgress != null) {
                ProgressManager.pop(threadedProgress);
            }
            if (completed) {
                ProgressManager.pop(progress);
            }
            executor.shutdownNow();
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        GPOM.LOGGER.info(
                "[FmlParallelLoading] {} handled with {} worker(s), parallelHandlers={}, wall={} ms, allowlist={}",
                event.getEventType(),
                workers,
                parallelHandlers,
                elapsedMillis,
                parallelMods
        );
    }

    private static int flushBatch(FMLEvent phaseEvent, List<ModContainer> batch,
                                  ImmutableMap<String, EventBus> eventChannels,
                                  Multimap<String, LoaderState.ModState> modStates,
                                  ProgressManager.ProgressBar progress,
                                  ProgressManager.ProgressBar threadedProgress,
                                  ExecutorService executor) {
        if (batch.isEmpty()) {
            return 0;
        }

        if (batch.size() == 1) {
            ModContainer mod = batch.get(0);
            progress.step(mod.getName());
            DispatchResult result = dispatchSingle(phaseEvent, mod, eventChannels, modStates, false);
            commitResult(result, modStates);
            rethrowIfFailed(result);
            return 1;
        }

        List<Future<DispatchResult>> futures = new ArrayList<>(batch.size());
        for (ModContainer mod : batch) {
            progress.step(mod.getName());
            futures.add(executor.submit(new DispatchTask(phaseEvent, mod, eventChannels, modStates)));
        }

        List<DispatchResult> results = new ArrayList<>(batch.size());
        for (Future<DispatchResult> future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for parallel FML loading", exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                results.add(DispatchResult.failed(null, cause));
            }
        }

        for (DispatchResult result : results) {
            commitResult(result, modStates);
        }
        for (DispatchResult result : results) {
            rethrowIfFailed(result);
        }
        return batch.size();
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

    private static void rethrowIfFailed(DispatchResult result) {
        if (result == null || result.throwable == null) {
            return;
        }
        Throwable throwable = result.throwable;
        if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
        }
        if (throwable instanceof Error) {
            throw (Error) throwable;
        }
        throw new RuntimeException(throwable);
    }

    private static boolean isParallelAllowed(ModContainer mod, Set<String> parallelMods) {
        return parallelMods.contains("*") || parallelMods.contains(normalize(mod.getModId()));
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

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class DispatchTask implements Callable<DispatchResult> {
        private final FMLEvent phaseEvent;
        private final ModContainer mod;
        private final ImmutableMap<String, EventBus> eventChannels;
        private final Multimap<String, LoaderState.ModState> modStates;

        private DispatchTask(FMLEvent phaseEvent, ModContainer mod,
                             ImmutableMap<String, EventBus> eventChannels,
                             Multimap<String, LoaderState.ModState> modStates) {
            this.phaseEvent = phaseEvent;
            this.mod = mod;
            this.eventChannels = eventChannels;
            this.modStates = modStates;
        }

        @Override
        public DispatchResult call() {
            return dispatchSingle(phaseEvent, mod, eventChannels, modStates, false);
        }
    }

    private static final class DispatchResult {
        private final ModContainer mod;
        private final LoaderState.ModState state;
        private final Throwable throwable;

        private DispatchResult(ModContainer mod, LoaderState.ModState state, Throwable throwable) {
            this.mod = mod;
            this.state = state;
            this.throwable = throwable;
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
    }
}
