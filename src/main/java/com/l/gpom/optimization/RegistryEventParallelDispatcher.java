package com.l.gpom.optimization;

import com.google.common.base.Throwables;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventExceptionHandler;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.IForgeRegistryModifiable;
import org.apache.logging.log4j.ThreadContext;

import java.lang.reflect.Field;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class RegistryEventParallelDispatcher {
    private static final boolean ENABLED = GpomEarlyConfig.registryParallelRegisterEventsEnabled();
    private static final boolean QUEUED_COMMIT = GpomEarlyConfig.registryParallelRegisterEventsQueuedCommitEnabled();
    private static final Set<String> REGISTRIES = GpomEarlyConfig.registryParallelRegisterEventsRegistries();
    private static final Set<String> IMMEDIATE_COMMIT_REGISTRIES = GpomEarlyConfig.registryParallelRegisterEventsImmediateCommitRegistries();
    private static final boolean PROXY_EVENT_REGISTRY = GpomEarlyConfig.registryParallelRegisterEventsProxyEventRegistryEnabled();
    private static final Set<String> PROXY_EVENT_REGISTRY_DENYLIST = GpomEarlyConfig.registryParallelRegisterEventsProxyEventRegistryDenylist();
    private static final boolean DEPENDENCY_GATING = GpomEarlyConfig.registryParallelRegisterEventsDependencyGatingEnabled();
    private static final boolean PROXY_IMMEDIATE_REGISTRIES = GpomEarlyConfig.registryParallelRegisterEventsProxyImmediateRegistriesEnabled();
    private static final Set<String> ORDERED_WAVE_REGISTRIES = GpomEarlyConfig.registryParallelRegisterEventsOrderedWaveRegistries();
    private static final int IMMEDIATE_COMMIT_WAIT_DIAGNOSTICS_MILLIS = GpomEarlyConfig.registryParallelRegisterEventsImmediateCommitWaitDiagnosticsMillis();
    private static final Set<String> ALLOWLIST = GpomEarlyConfig.registryParallelRegisterEventsAllowlist();
    private static final Set<String> DENYLIST = GpomEarlyConfig.registryParallelRegisterEventsDenylist();
    private static final int CONFIGURED_WORKERS = GpomEarlyConfig.registryParallelRegisterEventsWorkers();
    private static final boolean DEEP_DIAGNOSTICS = GpomEarlyConfig.registryParallelRegisterEventsDeepDiagnosticsEnabled();
    private static final Object EXECUTOR_LOCK = new Object();
    private static volatile ExecutorService executor;
    private static volatile int executorWorkers;
    private static volatile Field eventBusIdField;
    private static volatile Field eventBusExceptionHandlerField;
    private static volatile Field asmOwnerField;
    private static volatile Field asmHandlerField;
    private static volatile Field asmReadableField;
    private static final Set<String> LOGGED_ENABLED_REGISTRIES = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final Set<String> LOGGED_NO_PARALLEL = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final ThreadLocal<QueuingRegistry> ACTIVE_QUEUING_REGISTRY = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> BYPASS_WORKER_QUEUE = new ThreadLocal<>();

    private RegistryEventParallelDispatcher() {
    }

    public static boolean postOrFallback(EventBus eventBus, Event event) {
        if (!shouldHandle(event)) {
            return eventBus.post(event);
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            return postParallel(eventBus, castRegister(event));
        } catch (DispatchSetupException exception) {
            GPOM.LOGGER.warn(
                    "[RegistryParallel] Could not install parallel dispatch for {}; falling back to Forge EventBus.post",
                    eventName(event),
                    exception.getCause() == null ? exception : exception.getCause()
            );
            return eventBus.post(event);
        } finally {
            if (startedAt != 0L) {
                StartupProfiler.endProbeAlways("RegistryParallel " + eventName(event) + " total", startedAt);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static RegistryEvent.Register castRegister(Event event) {
        return (RegistryEvent.Register) event;
    }

    private static boolean shouldHandle(Event event) {
        if (!ENABLED || !(event instanceof RegistryEvent.Register)) {
            return false;
        }
        ResourceLocation registryName = ((RegistryEvent.Register<?>) event).getName();
        String normalized = registryName == null ? "" : normalize(registryName.toString());
        return REGISTRIES.contains("*") || REGISTRIES.contains(normalized);
    }

    private static boolean postParallel(EventBus eventBus, RegistryEvent.Register event) {
        try {
            IEventListener[] listeners = event.getListenerList().getListeners(eventBusId(eventBus));
            if (listeners.length <= 1) {
                return eventBus.post(event);
            }

            List<ListenerPlan> plans = new ArrayList<>(listeners.length);
            String registryName = registryName(event);
            for (int i = 0; i < listeners.length; i++) {
                plans.add(planFor(listeners[i], i, registryName));
            }

            if (!hasParallelWork(plans)) {
                logNoParallelOnce(event, plans);
                return eventBus.post(event);
            }

            logEnabledOnce(event, plans);
            if (QUEUED_COMMIT) {
                postQueued(eventBus, event, listeners, plans);
            } else {
                postDirect(eventBus, event, listeners, plans);
            }
            return event.isCancelable() && event.isCanceled();
        } catch (DispatchSetupException exception) {
            throw exception;
        } catch (Throwable throwable) {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            if (throwable instanceof Error) {
                throw (Error) throwable;
            }
            throw new DispatchSetupException(throwable);
        }
    }

    private static boolean hasParallelWork(List<ListenerPlan> plans) {
        for (ListenerPlan plan : plans) {
            if (plan.parallel) {
                return true;
            }
        }
        return false;
    }

    private static void postQueued(EventBus eventBus,
                                   RegistryEvent.Register event,
                                   IEventListener[] listeners,
                                   List<ListenerPlan> plans) throws Exception {
        List<ListenerPlan> batch = new ArrayList<>();
        for (ListenerPlan plan : plans) {
            if (plan.parallel) {
                batch.add(plan);
                continue;
            }
            flushQueuedBatch(eventBus, event, listeners, batch);
            batch.clear();
            invokeSerial(event, plan);
        }
        flushQueuedBatch(eventBus, event, listeners, batch);
    }

    private static void flushQueuedBatch(EventBus eventBus,
                                         RegistryEvent.Register event,
                                         IEventListener[] listeners,
                                         List<ListenerPlan> batch) throws Exception {
        if (batch.isEmpty()) {
            return;
        }

        String registryName = registryName(event);
        List<List<ExecutionUnit>> waves = dependencyWaves(registryName, batch);
        boolean commitAfterEachWave = usesImmediateCommit(registryName);
        long startedAt = StartupProfiler.beginProbe();
        List<ListenerResult> results = new ArrayList<>(batch.size());
        try {
            for (List<ExecutionUnit> wave : waves) {
                List<ListenerResult> waveResults = runQueuedWave(eventBus, event, listeners, wave);
                results.addAll(waveResults);
                if (commitAfterEachWave) {
                    commitResults(eventBus, event, listeners, waveResults);
                }
                if (hasFailure(waveResults)) {
                    break;
                }
            }
        } finally {
            if (startedAt != 0L) {
                StartupProfiler.endProbeAlways(
                        "RegistryParallel " + eventName(event)
                                + " parallel batch size=" + batch.size()
                                + " waves=" + waves.size(),
                        startedAt
                );
            }
        }

        long commitStartedAt = StartupProfiler.beginProbe();
        try {
            if (commitAfterEachWave) {
                handleFailures(eventBus, event, listeners, results);
            } else {
                commitResults(eventBus, event, listeners, results);
            }
        } finally {
            if (commitStartedAt != 0L) {
                StartupProfiler.endProbeAlways(
                        "RegistryParallel " + eventName(event)
                                + " queued commit size=" + batch.size()
                                + " mutations=" + mutationCount(results),
                        commitStartedAt
                );
            }
        }
    }

    private static void commitResults(EventBus eventBus,
                                      RegistryEvent.Register event,
                                      IEventListener[] listeners,
                                      List<ListenerResult> results) throws Exception {
        for (ListenerResult result : results) {
            if (result.failure != null) {
                handleListenerFailure(eventBus, event, listeners, result.index, result.failure);
            }
            result.registry.commitToBacking();
        }
    }

    private static void handleFailures(EventBus eventBus,
                                       RegistryEvent.Register event,
                                       IEventListener[] listeners,
                                       List<ListenerResult> results) throws Exception {
        for (ListenerResult result : results) {
            if (result.failure != null) {
                handleListenerFailure(eventBus, event, listeners, result.index, result.failure);
            }
        }
    }

    private static List<ListenerResult> runQueuedWave(EventBus eventBus,
                                                      RegistryEvent.Register event,
                                                      IEventListener[] listeners,
                                                      List<ExecutionUnit> wave) throws Exception {
        List<ListenerResult> results = new ArrayList<>(listenerCount(wave));
        ImmediateCommitCoordinator coordinator = immediateCommitCoordinator(event, wave);
        if (wave.size() == 1) {
            results.addAll(runQueuedUnit(event, wave.get(0), coordinator));
            return results;
        }

        List<Future<List<ListenerResult>>> futures = new ArrayList<>(wave.size());
        ExecutorService executor = executor();
        for (ExecutionUnit unit : wave) {
            futures.add(executor.submit(new QueuedTask(event, unit, coordinator)));
        }
        for (Future<List<ListenerResult>> future : futures) {
            try {
                results.addAll(future.get());
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                if (cause instanceof ListenerFailure) {
                    ListenerFailure failure = (ListenerFailure) cause;
                    handleListenerFailure(eventBus, event, listeners, failure.index, failure.getCause());
                }
                throw cause instanceof Exception ? (Exception) cause : new RuntimeException(cause);
            }
        }
        return results;
    }

    private static List<ListenerResult> runQueuedUnit(RegistryEvent.Register event,
                                                      ExecutionUnit unit,
                                                      ImmediateCommitCoordinator coordinator) {
        List<ListenerResult> results = new ArrayList<>(unit.plans.size());
        for (ListenerPlan plan : unit.plans) {
            ListenerResult result = runQueuedListener(event, plan, coordinator);
            results.add(result);
            if (result.failure != null) {
                break;
            }
        }
        return results;
    }

    private static boolean hasFailure(List<ListenerResult> results) {
        for (ListenerResult result : results) {
            if (result != null && result.failure != null) {
                return true;
            }
        }
        return false;
    }

    private static ListenerResult runQueuedListener(RegistryEvent.Register originalEvent,
                                                    ListenerPlan plan,
                                                    ImmediateCommitCoordinator coordinator) {
        QueuingRegistry registry = new QueuingRegistry(originalEvent.getRegistry(), registryName(originalEvent), plan.index, coordinator);
        RegistryEvent.Register event = workerEvent(originalEvent, registry, plan);
        long listenerStartedAt = beginDeepDiagnosticsProbe();
        Throwable failure = null;
        try {
            ACTIVE_QUEUING_REGISTRY.set(registry);
            invokeWithOwner(event, plan.invokeTarget, plan.owner, true);
            return ListenerResult.ok(plan.index, registry);
        } catch (Throwable throwable) {
            failure = throwable;
            return ListenerResult.failed(plan.index, registry, throwable);
        } finally {
            ACTIVE_QUEUING_REGISTRY.remove();
            try {
                if (coordinator != null) {
                    coordinator.listenerFinished(plan.index, registry, failure);
                }
            } finally {
                endListenerDeepDiagnosticsProbe(
                        originalEvent,
                        plan,
                        usesImmediateCommit(registryName(originalEvent)) ? "worker immediate" : "worker queued",
                        listenerStartedAt
                );
            }
        }
    }

    public static boolean queueWorkerRegistration(IForgeRegistryEntry<?> entry) {
        if (Boolean.TRUE.equals(BYPASS_WORKER_QUEUE.get())) {
            return false;
        }
        QueuingRegistry registry = ACTIVE_QUEUING_REGISTRY.get();
        if (registry == null || entry == null || !registry.accepts(entry)) {
            return false;
        }
        registry.registerFromWorker(entry);
        return true;
    }

    public static boolean queueWorkerRegistration(IForgeRegistry<?> backing, IForgeRegistryEntry<?> entry) {
        if (Boolean.TRUE.equals(BYPASS_WORKER_QUEUE.get())) {
            return false;
        }
        QueuingRegistry registry = ACTIVE_QUEUING_REGISTRY.get();
        if (registry == null || backing == null || registry.backing != backing || entry == null || !registry.accepts(entry)) {
            return false;
        }
        registry.registerFromWorker(entry);
        return true;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RegistryEvent.Register workerEvent(RegistryEvent.Register originalEvent, QueuingRegistry registry, ListenerPlan plan) {
        IForgeRegistry eventRegistry = useEventRegistryProxy(originalEvent, plan) ? registry : originalEvent.getRegistry();
        return new RegistryEvent.Register(originalEvent.getName(), eventRegistry);
    }

    private static boolean useEventRegistryProxy(RegistryEvent.Register event, ListenerPlan plan) {
        // Immediate registries need writes to be visible before the listener returns. When enabled,
        // GPOM's proxy commits those writes immediately in original listener order.
        return PROXY_EVENT_REGISTRY
                && (!usesImmediateCommit(registryName(event)) || PROXY_IMMEDIATE_REGISTRIES)
                && !isDenied(PROXY_EVENT_REGISTRY_DENYLIST, plan.modId, registryName(event));
    }

    private static void postDirect(EventBus eventBus,
                                   RegistryEvent.Register event,
                                   IEventListener[] listeners,
                                   List<ListenerPlan> plans) throws Exception {
        List<ListenerPlan> batch = new ArrayList<>();
        for (ListenerPlan plan : plans) {
            if (plan.parallel) {
                batch.add(plan);
                continue;
            }
            flushDirectBatch(eventBus, event, listeners, batch);
            batch.clear();
            invokeSerial(event, plan);
        }
        flushDirectBatch(eventBus, event, listeners, batch);
    }

    private static void flushDirectBatch(EventBus eventBus,
                                         RegistryEvent.Register event,
                                         IEventListener[] listeners,
                                         List<ListenerPlan> batch) throws Exception {
        if (batch.isEmpty()) {
            return;
        }

        List<List<ExecutionUnit>> waves = dependencyWaves(registryName(event), batch);
        for (List<ExecutionUnit> wave : waves) {
            runDirectWave(eventBus, event, listeners, wave);
        }
    }

    private static void runDirectWave(EventBus eventBus,
                                      RegistryEvent.Register event,
                                      IEventListener[] listeners,
                                      List<ExecutionUnit> wave) throws Exception {
        if (wave.size() == 1) {
            ListenerFailure failure = invokeDirectUnit(event, wave.get(0));
            if (failure != null) {
                handleListenerFailure(eventBus, event, listeners, failure.index, failure.getCause());
            }
            return;
        }

        List<Future<ListenerFailure>> futures = new ArrayList<>(wave.size());
        ExecutorService executor = executor();
        for (ExecutionUnit unit : wave) {
            futures.add(executor.submit(new DirectTask(event, unit)));
        }
        for (Future<ListenerFailure> future : futures) {
            ListenerFailure failure = future.get();
            if (failure != null) {
                handleListenerFailure(eventBus, event, listeners, failure.index, failure.getCause());
            }
        }
    }

    private static ListenerFailure invokeDirectUnit(RegistryEvent.Register event, ExecutionUnit unit) {
        for (ListenerPlan plan : unit.plans) {
            long startedAt = beginDeepDiagnosticsProbe();
            try {
                invokeWithOwner(event, plan.invokeTarget, plan.owner, true);
            } catch (Throwable throwable) {
                return new ListenerFailure(plan.index, throwable);
            } finally {
                endListenerDeepDiagnosticsProbe(event, plan, "worker direct", startedAt);
            }
        }
        return null;
    }

    private static void invokeSerial(RegistryEvent.Register event, ListenerPlan plan) throws Exception {
        long startedAt = beginDeepDiagnosticsProbe();
        try {
            invokeWithOwner(event, plan.original, plan.owner, false);
        } finally {
            endListenerDeepDiagnosticsProbe(
                    event,
                    plan,
                    "serial " + serialReason(plan, registryName(event)),
                    startedAt
            );
        }
    }

    private static void invokeWithFailureHandling(EventBus eventBus,
                                                  RegistryEvent.Register originalEvent,
                                                  IEventListener[] listeners,
                                                  ListenerPlan plan,
                                                  Event event,
                                                  boolean workerContext) throws Exception {
        long startedAt = workerContext ? beginDeepDiagnosticsProbe() : 0L;
        try {
            invokeWithOwner(event, workerContext ? plan.invokeTarget : plan.original, plan.owner, workerContext);
        } catch (Throwable throwable) {
            handleListenerFailure(eventBus, originalEvent, listeners, plan.index, throwable);
        } finally {
            if (workerContext) {
                endListenerDeepDiagnosticsProbe(originalEvent, plan, "worker direct", startedAt);
            }
        }
    }

    private static void invokeWithOwner(Event event,
                                        IEventListener listener,
                                        ModContainer owner,
                                        boolean workerContext) {
        String modId = safeModId(owner);
        String previousName = null;
        Thread thread = null;
        if (workerContext) {
            thread = Thread.currentThread();
            previousName = thread.getName();
            thread.setName("GPOM Registry - " + modId);
            ThreadContext.put("mod", modId);
            FmlParallelLoadingContext.setActiveContainer(owner);
            if (event instanceof IContextSetter) {
                ((IContextSetter) event).setModContainer(owner);
            }
        }
        try {
            listener.invoke(event);
        } finally {
            if (workerContext) {
                FmlParallelLoadingContext.clearActiveContainer();
                ThreadContext.remove("mod");
                thread.setName(previousName);
            }
        }
    }

    private static void handleListenerFailure(EventBus eventBus,
                                              Event event,
                                              IEventListener[] listeners,
                                              int index,
                                              Throwable throwable) throws Exception {
        IEventExceptionHandler exceptionHandler = eventBusExceptionHandler(eventBus);
        exceptionHandler.handleException(eventBus, event, listeners, index, throwable);
        Throwables.throwIfUnchecked(throwable);
        throw new RuntimeException(throwable);
    }

    private static ListenerPlan planFor(IEventListener listener, int index, String registryName) {
        ListenerTarget target = unwrap(listener);
        String modId = safeModId(target.owner);
        boolean ownerKnown = !normalize(modId).isEmpty();
        boolean targetAvailable = target.parallelTarget != null;
        boolean hardSerial = isEnderIoTileLifecycleListener(listener, target, registryName);
        boolean allowed = !hardSerial && ownerKnown && isAllowed(modId, registryName);
        boolean parallel = allowed && targetAvailable;
        return new ListenerPlan(
                index,
                listener,
                targetAvailable ? target.parallelTarget : listener,
                target.owner,
                targetAvailable,
                parallel,
                normalize(modId),
                dependencyLabels(target.owner, true),
                dependencyLabels(target.owner, false)
        );
    }

    private static List<List<ExecutionUnit>> dependencyWaves(String registryName, List<ListenerPlan> batch) {
        List<ExecutionUnit> units = compactExecutionUnits(batch);
        if (!DEPENDENCY_GATING || units.size() <= 1) {
            return Collections.singletonList(units);
        }

        if (usesOrderedWaves(registryName)) {
            return orderedDependencyWaves(units);
        }

        return topologicalDependencyWaves(units);
    }

    private static List<ExecutionUnit> compactExecutionUnits(List<ListenerPlan> batch) {
        if (batch.isEmpty()) {
            return Collections.emptyList();
        }
        List<ExecutionUnit> units = new ArrayList<>();
        List<ListenerPlan> current = new ArrayList<>();
        String currentModId = null;
        for (ListenerPlan plan : batch) {
            if (!current.isEmpty() && !plan.modId.equals(currentModId)) {
                units.add(new ExecutionUnit(current));
                current = new ArrayList<>();
            }
            current.add(plan);
            currentModId = plan.modId;
        }
        if (!current.isEmpty()) {
            units.add(new ExecutionUnit(current));
        }
        return units;
    }

    private static List<List<ExecutionUnit>> orderedDependencyWaves(List<ExecutionUnit> units) {
        List<List<ExecutionUnit>> waves = new ArrayList<>();
        List<ExecutionUnit> wave = new ArrayList<>();
        for (ExecutionUnit unit : units) {
            if (!wave.isEmpty() && waitsForWave(unit, wave)) {
                waves.add(wave);
                wave = new ArrayList<>();
            }
            wave.add(unit);
        }
        if (!wave.isEmpty()) {
            waves.add(wave);
        }
        return waves;
    }

    private static List<List<ExecutionUnit>> topologicalDependencyWaves(List<ExecutionUnit> units) {
        int size = units.size();
        List<List<Integer>> dependants = new ArrayList<>(size);
        List<Set<Integer>> dependencies = new ArrayList<>(size);
        int[] remainingDependencies = new int[size];
        boolean[] scheduled = new boolean[size];

        for (int i = 0; i < size; i++) {
            dependants.add(new ArrayList<>());
            dependencies.add(new LinkedHashSet<>());
        }

        for (int i = 0; i < size; i++) {
            ExecutionUnit unit = units.get(i);
            for (int j = 0; j < i; j++) {
                ExecutionUnit earlier = units.get(j);
                if (dependsOnEarlier(unit, earlier)) {
                    dependencies.get(i).add(j);
                }
            }
        }

        for (int i = 0; i < size; i++) {
            Set<Integer> planDependencies = dependencies.get(i);
            remainingDependencies[i] = planDependencies.size();
            for (Integer dependency : planDependencies) {
                dependants.get(dependency).add(i);
            }
        }

        List<List<ExecutionUnit>> waves = new ArrayList<>();
        int scheduledCount = 0;
        while (scheduledCount < size) {
            List<Integer> wavePositions = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (!scheduled[i] && remainingDependencies[i] == 0) {
                    wavePositions.add(i);
                }
            }

            if (wavePositions.isEmpty()) {
                // Edges are only created from earlier listeners to later listeners, so a cycle should be
                // impossible. Keep a safe fallback rather than aborting startup if a future dependency
                // source behaves unexpectedly.
                for (int i = 0; i < size; i++) {
                    if (!scheduled[i]) {
                        wavePositions.add(i);
                        break;
                    }
                }
            }

            List<ExecutionUnit> wave = new ArrayList<>(wavePositions.size());
            for (Integer position : wavePositions) {
                scheduled[position] = true;
                scheduledCount++;
                wave.add(units.get(position));
            }
            waves.add(wave);

            for (Integer position : wavePositions) {
                for (Integer dependant : dependants.get(position)) {
                    remainingDependencies[dependant]--;
                }
            }
        }
        return waves;
    }

    private static boolean dependsOnEarlier(ExecutionUnit unit, ExecutionUnit earlier) {
        if (unit.modId.isEmpty()) {
            return false;
        }
        return unit.modId.equals(earlier.modId)
                || matchesDependency(unit.dependenciesBefore, earlier.modId)
                || matchesDependency(earlier.dependantsAfter, unit.modId);
    }

    private static boolean dependsOnEarlier(ListenerPlan plan, ListenerPlan earlier) {
        if (plan.modId.isEmpty()) {
            return false;
        }
        return plan.modId.equals(earlier.modId)
                || matchesDependency(plan.dependenciesBefore, earlier.modId)
                || matchesDependency(earlier.dependantsAfter, plan.modId);
    }

    private static boolean waitsForWave(ExecutionUnit unit, List<ExecutionUnit> wave) {
        for (ExecutionUnit earlier : wave) {
            if (dependsOnEarlier(unit, earlier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDependency(Set<String> labels, String modId) {
        return !labels.isEmpty() && (labels.contains("*") || labels.contains(modId));
    }

    private static ImmediateCommitCoordinator immediateCommitCoordinator(RegistryEvent.Register event, List<ExecutionUnit> wave) {
        if (!usesImmediateCommit(registryName(event)) || wave.size() <= 1) {
            return null;
        }
        return new ImmediateCommitCoordinator(registryName(event), wave);
    }

    private static int listenerCount(List<ExecutionUnit> units) {
        int count = 0;
        for (ExecutionUnit unit : units) {
            count += unit.plans.size();
        }
        return count;
    }

    private static Set<String> dependencyLabels(ModContainer owner, boolean beforeCurrent) {
        if (owner == null) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        if (beforeCurrent) {
            addArtifactLabels(labels, owner.getRequirements());
            addArtifactLabels(labels, owner.getDependencies());
        } else {
            addArtifactLabels(labels, owner.getDependants());
        }
        return labels.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(labels);
    }

    private static void addArtifactLabels(Set<String> labels, Collection<ArtifactVersion> versions) {
        if (versions == null || versions.isEmpty()) {
            return;
        }
        for (ArtifactVersion version : versions) {
            if (version == null) {
                continue;
            }
            String label = normalize(version.getLabel());
            if (!label.isEmpty()) {
                labels.add(label);
            }
        }
    }

    private static boolean isAllowed(String modId, String registryName) {
        String normalized = normalize(modId);
        if (normalized.isEmpty() || isDenied(normalized, registryName)) {
            return false;
        }
        return ALLOWLIST.contains("*") || ALLOWLIST.contains(normalized);
    }

    private static boolean isDenied(String normalizedModId, String registryName) {
        return isDenied(DENYLIST, normalizedModId, registryName);
    }

    private static boolean isDenied(Set<String> denylist, String normalizedModId, String registryName) {
        if (denylist.contains(normalizedModId)) {
            return true;
        }
        String normalizedRegistry = normalize(registryName);
        return !normalizedRegistry.isEmpty() && denylist.contains(normalizedModId + "@" + normalizedRegistry);
    }

    private static long beginDeepDiagnosticsProbe() {
        return DEEP_DIAGNOSTICS ? StartupProfiler.beginProbe() : 0L;
    }

    private static void endListenerDeepDiagnosticsProbe(RegistryEvent.Register event,
                                                       ListenerPlan plan,
                                                       String mode,
                                                       long startedAt) {
        if (startedAt == 0L) {
            return;
        }
        StartupProfiler.endProbeAlways(
                "RegistryParallel " + eventName(event)
                        + ' ' + mode
                        + " mod=" + modLabel(plan),
                startedAt
        );
    }

    private static String serialReason(ListenerPlan plan, String registryName) {
        if (plan.modId.isEmpty()) {
            return "ownerless";
        }
        if (isDenied(plan.modId, registryName)) {
            return "denied";
        }
        if (!plan.targetAvailable) {
            return "noTarget";
        }
        return "notAllowed";
    }

    private static String modLabel(ListenerPlan plan) {
        return plan.modId.isEmpty() ? "<unknown>" : plan.modId;
    }

    private static ListenerTarget unwrap(IEventListener listener) {
        if (listener instanceof ASMEventHandler) {
            return new ListenerTarget(ownerFromAsm((ASMEventHandler) listener), listener);
        }
        if (isContextSetter(listener)) {
            ModContainer owner = ownerFromContextSetter(listener);
            ASMEventHandler asm = asmFromContextSetter(listener);
            if (asm != null) {
                return new ListenerTarget(owner != null ? owner : ownerFromAsm(asm), asm);
            }
            return new ListenerTarget(owner, null);
        }
        if (isGpomLazyStaticListener(listener)) {
            ModContainer owner = ownerFromGenericListener(listener);
            return new ListenerTarget(owner, owner == null ? null : listener);
        }
        ListenerTarget captured = unwrapCapturedListenerFields(listener, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (captured.parallelTarget != null || captured.owner != null) {
            return captured;
        }
        return new ListenerTarget(ownerFromGenericListener(listener), null);
    }

    private static ListenerTarget unwrap(IEventListener listener, Set<IEventListener> seen) {
        if (listener instanceof ASMEventHandler) {
            return new ListenerTarget(ownerFromAsm((ASMEventHandler) listener), listener);
        }
        if (isContextSetter(listener)) {
            ModContainer owner = ownerFromContextSetter(listener);
            ASMEventHandler asm = asmFromContextSetter(listener);
            if (asm != null) {
                return new ListenerTarget(owner != null ? owner : ownerFromAsm(asm), asm);
            }
            return new ListenerTarget(owner, null);
        }
        if (isGpomLazyStaticListener(listener)) {
            ModContainer owner = ownerFromGenericListener(listener);
            return new ListenerTarget(owner, owner == null ? null : listener);
        }
        return unwrapCapturedListenerFields(listener, seen);
    }

    private static ListenerTarget unwrapCapturedListenerFields(IEventListener listener, Set<IEventListener> seen) {
        if (listener == null || !seen.add(listener)) {
            return ListenerTarget.empty();
        }

        ModContainer owner = null;
        ASMEventHandler asm = null;
        Class<?> current = listener.getClass();
        while (current != null) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                fields = new Field[0];
            }
            for (Field field : fields) {
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(listener);
                } catch (Throwable ignored) {
                    continue;
                }
                if (value instanceof ModContainer) {
                    if (owner == null) {
                        owner = (ModContainer) value;
                    }
                    continue;
                }
                if (value instanceof ASMEventHandler) {
                    asm = (ASMEventHandler) value;
                    if (owner == null) {
                        owner = ownerFromAsm(asm);
                    }
                    continue;
                }
                if (value instanceof IEventListener && value != listener) {
                    ListenerTarget nested = unwrap((IEventListener) value, seen);
                    if (nested.parallelTarget != null) {
                        return new ListenerTarget(owner != null ? owner : nested.owner, nested.parallelTarget);
                    }
                    if (owner == null && nested.owner != null) {
                        owner = nested.owner;
                    }
                }
            }
            current = current.getSuperclass();
        }

        if (asm != null) {
            return new ListenerTarget(owner != null ? owner : ownerFromAsm(asm), asm);
        }
        return owner == null ? ListenerTarget.empty() : new ListenerTarget(owner, null);
    }

    private static boolean isContextSetter(IEventListener listener) {
        return listener != null
                && "net.minecraftforge.fml.common.eventhandler.EventBus$ContextSetterEventListener".equals(listener.getClass().getName());
    }

    private static boolean isGpomLazyStaticListener(IEventListener listener) {
        if (listener == null) {
            return false;
        }
        String className = listener.getClass().getName();
        return "com.l.gpom.optimization.EventBusRegistrationOptimizations$LazyStaticEventBusListener".equals(className)
                || "com.l.gpom.optimization.ForgeConstructionAnnotationOptimizations$LazyStaticSubscriberListener".equals(className);
    }

    private static ModContainer ownerFromAsm(ASMEventHandler handler) {
        try {
            return (ModContainer) asmOwnerField().get(handler);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ModContainer ownerFromContextSetter(IEventListener listener) {
        try {
            return (ModContainer) findField(listener.getClass(), "owner").get(listener);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ASMEventHandler asmFromContextSetter(IEventListener listener) {
        try {
            return (ASMEventHandler) findField(listener.getClass(), "asm").get(listener);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ModContainer ownerFromGenericListener(IEventListener listener) {
        if (listener == null) {
            return null;
        }
        try {
            Field field = findField(listener.getClass(), "owner");
            Object value = field.get(listener);
            return value instanceof ModContainer ? (ModContainer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isEnderIoTileLifecycleListener(IEventListener listener, ListenerTarget target, String registryName) {
        String registry = normalize(registryName);
        if (!"minecraft:blocks".equals(registry) && !"minecraft:items".equals(registry)) {
            return false;
        }

        String ownerModId = normalize(safeModId(target.owner));
        if (ownerModId.startsWith("enderio")) {
            return true;
        }

        return listenerReferencesEnderIo(listener, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
    }

    private static boolean listenerReferencesEnderIo(Object value, Set<Object> seen, int depth) {
        if (value == null || depth > 4) {
            return false;
        }
        if (value instanceof String) {
            return isEnderIoTileLifecycleText((String) value);
        }
        if (value instanceof ModContainer) {
            return normalize(((ModContainer) value).getModId()).startsWith("enderio");
        }
        if (value instanceof Class<?>) {
            return isEnderIoTileLifecycleClassName(((Class<?>) value).getName());
        }

        Class<?> type = value.getClass();
        if (isEnderIoTileLifecycleClassName(type.getName())) {
            return true;
        }
        if (!seen.add(value)) {
            return false;
        }

        if (value instanceof ASMEventHandler) {
            if (listenerReferencesEnderIo(reflectFieldValue(value, asmHandlerField(), "handler"), seen, depth + 1)) {
                return true;
            }
            if (listenerReferencesEnderIo(reflectFieldValue(value, asmReadableField(), "readable"), seen, depth + 1)) {
                return true;
            }
        }

        if (!isSafeListenerContainer(type)) {
            return false;
        }

        Class<?> current = type;
        while (current != null) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                fields = new Field[0];
            }
            for (Field field : fields) {
                if (field.getType().isPrimitive()) {
                    continue;
                }
                Object nested;
                try {
                    field.setAccessible(true);
                    nested = field.get(value);
                } catch (Throwable ignored) {
                    continue;
                }
                if (listenerReferencesEnderIo(nested, seen, depth + 1)) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isSafeListenerContainer(Class<?> type) {
        String name = type == null ? "" : type.getName();
        return name.startsWith("net.minecraftforge.fml.common.eventhandler.")
                || name.startsWith("com.google.common.eventbus.")
                || name.startsWith("com.l.gpom.")
                || name.contains("ASMEventHandler")
                || name.contains("EventBus");
    }

    private static boolean isEnderIoTileLifecycleText(String value) {
        String text = value == null ? "" : value;
        return text.contains("crazypants.enderio.")
                || text.contains("ConduitTileEntity")
                || text.contains("ConduitObject")
                || text.contains("TileConduitBundle");
    }

    private static boolean isEnderIoTileLifecycleClassName(String className) {
        return className != null && className.startsWith("crazypants.enderio.");
    }

    private static Object reflectFieldValue(Object owner, Field field, String fallbackName) {
        if (owner == null || field == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (Throwable ignored) {
            try {
                return findField(owner.getClass(), fallbackName).get(owner);
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private static int eventBusId(EventBus eventBus) throws Exception {
        return eventBusIdField().getInt(eventBus);
    }

    private static IEventExceptionHandler eventBusExceptionHandler(EventBus eventBus) throws Exception {
        return (IEventExceptionHandler) eventBusExceptionHandlerField().get(eventBus);
    }

    private static Field eventBusIdField() throws NoSuchFieldException {
        Field field = eventBusIdField;
        if (field == null) {
            field = EventBus.class.getDeclaredField("busID");
            field.setAccessible(true);
            eventBusIdField = field;
        }
        return field;
    }

    private static Field eventBusExceptionHandlerField() throws NoSuchFieldException {
        Field field = eventBusExceptionHandlerField;
        if (field == null) {
            field = EventBus.class.getDeclaredField("exceptionHandler");
            field.setAccessible(true);
            eventBusExceptionHandlerField = field;
        }
        return field;
    }

    private static Field asmOwnerField() throws NoSuchFieldException {
        Field field = asmOwnerField;
        if (field == null) {
            field = ASMEventHandler.class.getDeclaredField("owner");
            field.setAccessible(true);
            asmOwnerField = field;
        }
        return field;
    }

    private static Field asmHandlerField() {
        Field field = asmHandlerField;
        if (field == null) {
            try {
                field = ASMEventHandler.class.getDeclaredField("handler");
                field.setAccessible(true);
                asmHandlerField = field;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return field;
    }

    private static Field asmReadableField() {
        Field field = asmReadableField;
        if (field == null) {
            try {
                field = ASMEventHandler.class.getDeclaredField("readable");
                field.setAccessible(true);
                asmReadableField = field;
            } catch (Throwable ignored) {
                return null;
            }
        }
        return field;
    }

    private static Field findField(Class<?> ownerClass, String name) throws NoSuchFieldException {
        Class<?> current = ownerClass;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(ownerClass.getName() + '#' + name);
    }

    private static ExecutorService executor() {
        int workers = workerCount();
        ExecutorService current = executor;
        if (current != null && executorWorkers == workers) {
            return current;
        }
        synchronized (EXECUTOR_LOCK) {
            current = executor;
            if (current != null && executorWorkers == workers) {
                return current;
            }
            if (current != null) {
                current.shutdownNow();
            }
            executorWorkers = workers;
            executor = Executors.newFixedThreadPool(workers, new RegistryThreadFactory());
            return executor;
        }
    }

    private static int workerCount() {
        if (CONFIGURED_WORKERS > 0) {
            return CONFIGURED_WORKERS;
        }
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, Math.min(6, processors - 1));
    }

    private static void logEnabledOnce(RegistryEvent.Register<?> event, List<ListenerPlan> plans) {
        String registry = event.getName() == null ? "<unknown>" : event.getName().toString();
        if (!GpomEarlyConfig.optimizationInfoLogsEnabled() || !LOGGED_ENABLED_REGISTRIES.add(registry)) {
            return;
        }
        int parallel = 0;
        int serial = 0;
        for (ListenerPlan plan : plans) {
            if (plan.parallel) {
                parallel++;
            } else {
                serial++;
            }
        }
        GPOM.LOGGER.info(
                "[RegistryParallel] enabled queuedCommit={} immediateCommit={} proxyEventRegistry={} dependencyGating={} workers={} registry={} parallelListeners={} executionUnits={} serialListeners={}",
                QUEUED_COMMIT,
                usesImmediateCommit(registry),
                PROXY_EVENT_REGISTRY && (!usesImmediateCommit(registry) || PROXY_IMMEDIATE_REGISTRIES),
                DEPENDENCY_GATING,
                workerCount(),
                registry,
                parallel,
                compactExecutionUnits(parallelPlans(plans)).size(),
                serial
        );
    }

    private static List<ListenerPlan> parallelPlans(List<ListenerPlan> plans) {
        List<ListenerPlan> parallelPlans = new ArrayList<>();
        for (ListenerPlan plan : plans) {
            if (plan.parallel) {
                parallelPlans.add(plan);
            }
        }
        return parallelPlans;
    }

    private static void logNoParallelOnce(RegistryEvent.Register<?> event, List<ListenerPlan> plans) {
        String registry = event.getName() == null ? "<unknown>" : event.getName().toString();
        if (!LOGGED_NO_PARALLEL.add(registry)) {
            return;
        }

        int context = 0;
        int asm = 0;
        int generic = 0;
        int ownerless = 0;
        int denied = 0;
        int noTarget = 0;
        for (ListenerPlan plan : plans) {
            if (isContextSetter(plan.original)) {
                context++;
            } else if (plan.original instanceof ASMEventHandler) {
                asm++;
            } else {
                generic++;
            }

            String modId = normalize(safeModId(plan.owner));
            if (modId.isEmpty()) {
                ownerless++;
            } else if (isDenied(modId, registry)) {
                denied++;
            }
            if (!plan.targetAvailable) {
                noTarget++;
            }
        }

        GPOM.LOGGER.warn(
                "[RegistryParallel] enabled for {} but found no parallel listeners; total={} context={} asm={} generic={} ownerless={} denied={} noParallelTarget={}",
                registry,
                plans.size(),
                context,
                asm,
                generic,
                ownerless,
                denied,
                noTarget
        );
    }

    private static String eventName(Event event) {
        if (event instanceof RegistryEvent.Register) {
            return "RegistryEvent.Register " + registryName((RegistryEvent.Register<?>) event);
        }
        return event == null ? "<null>" : event.getClass().getName();
    }

    private static String registryName(RegistryEvent.Register<?> event) {
        ResourceLocation name = event.getName();
        return name == null ? "<unknown>" : name.toString();
    }

    private static int mutationCount(List<ListenerResult> results) {
        int mutations = 0;
        for (ListenerResult result : results) {
            if (result != null && result.registry != null) {
                mutations += result.registry.mutationCount();
            }
        }
        return mutations;
    }

    private static String safeModId(ModContainer owner) {
        return owner == null ? "" : owner.getModId();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean usesImmediateCommit(String registryName) {
        String normalized = normalize(registryName);
        return IMMEDIATE_COMMIT_REGISTRIES.contains("*")
                || (!normalized.isEmpty() && IMMEDIATE_COMMIT_REGISTRIES.contains(normalized));
    }

    private static boolean usesOrderedWaves(String registryName) {
        String normalized = normalize(registryName);
        return ORDERED_WAVE_REGISTRIES.contains("*")
                || (!normalized.isEmpty() && ORDERED_WAVE_REGISTRIES.contains(normalized));
    }

    private static final class RegistryThreadFactory implements ThreadFactory {
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "GPOM Registry Worker #" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static final class QueuedTask implements Callable<List<ListenerResult>> {
        private final RegistryEvent.Register event;
        private final ExecutionUnit unit;
        private final ImmediateCommitCoordinator coordinator;

        private QueuedTask(RegistryEvent.Register event, ExecutionUnit unit, ImmediateCommitCoordinator coordinator) {
            this.event = event;
            this.unit = unit;
            this.coordinator = coordinator;
        }

        @Override
        public List<ListenerResult> call() {
            return runQueuedUnit(event, unit, coordinator);
        }
    }

    private static final class DirectTask implements Callable<ListenerFailure> {
        private final RegistryEvent.Register event;
        private final ExecutionUnit unit;

        private DirectTask(RegistryEvent.Register event, ExecutionUnit unit) {
            this.event = event;
            this.unit = unit;
        }

        @Override
        public ListenerFailure call() {
            return invokeDirectUnit(event, unit);
        }
    }

    private static final class ExecutionUnit {
        private final List<ListenerPlan> plans;
        private final String modId;
        private final Set<String> dependenciesBefore;
        private final Set<String> dependantsAfter;

        private ExecutionUnit(List<ListenerPlan> plans) {
            this.plans = Collections.unmodifiableList(new ArrayList<>(plans));
            this.modId = plans.isEmpty() ? "" : plans.get(0).modId;
            this.dependenciesBefore = mergedLabels(plans, true);
            this.dependantsAfter = mergedLabels(plans, false);
        }

        private static Set<String> mergedLabels(List<ListenerPlan> plans, boolean dependenciesBefore) {
            LinkedHashSet<String> labels = new LinkedHashSet<>();
            for (ListenerPlan plan : plans) {
                labels.addAll(dependenciesBefore ? plan.dependenciesBefore : plan.dependantsAfter);
            }
            return labels.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(labels);
        }
    }

    private static final class ListenerPlan {
        private final int index;
        private final IEventListener original;
        private final IEventListener invokeTarget;
        private final ModContainer owner;
        private final boolean targetAvailable;
        private final boolean parallel;
        private final String modId;
        private final Set<String> dependenciesBefore;
        private final Set<String> dependantsAfter;

        private ListenerPlan(int index,
                             IEventListener original,
                             IEventListener invokeTarget,
                             ModContainer owner,
                             boolean targetAvailable,
                             boolean parallel,
                             String modId,
                             Set<String> dependenciesBefore,
                             Set<String> dependantsAfter) {
            this.index = index;
            this.original = original;
            this.invokeTarget = invokeTarget;
            this.owner = owner;
            this.targetAvailable = targetAvailable;
            this.parallel = parallel;
            this.modId = modId;
            this.dependenciesBefore = dependenciesBefore;
            this.dependantsAfter = dependantsAfter;
        }
    }

    private static final class ListenerTarget {
        private final ModContainer owner;
        private final IEventListener parallelTarget;

        private ListenerTarget(ModContainer owner, IEventListener parallelTarget) {
            this.owner = owner;
            this.parallelTarget = parallelTarget;
        }

        private static ListenerTarget empty() {
            return new ListenerTarget(null, null);
        }
    }

    private static final class ListenerResult {
        private final int index;
        private final QueuingRegistry registry;
        private final Throwable failure;

        private ListenerResult(int index, QueuingRegistry registry, Throwable failure) {
            this.index = index;
            this.registry = registry;
            this.failure = failure;
        }

        private static ListenerResult ok(int index, QueuingRegistry registry) {
            return new ListenerResult(index, registry, null);
        }

        private static ListenerResult failed(int index, QueuingRegistry registry, Throwable failure) {
            return new ListenerResult(index, registry, failure);
        }
    }

    private static final class ListenerFailure extends RuntimeException {
        private final int index;

        private ListenerFailure(int index, Throwable cause) {
            super(cause);
            this.index = index;
        }
    }

    private static final class DispatchSetupException extends RuntimeException {
        private DispatchSetupException(Throwable cause) {
            super(cause);
        }
    }

    private interface RegistryMutation {
        void apply(IForgeRegistry registry);
    }

    private static final class ImmediateCommitCoordinator {
        private final String registryName;
        private final List<Integer> listenerOrder;
        private final Map<Integer, String> listenerLabels;
        private final Set<Integer> completed = new LinkedHashSet<>();
        private final Map<Integer, QueuingRegistry> completedRegistries = new LinkedHashMap<>();
        private int activePosition;
        private int failedIndex = Integer.MAX_VALUE;

        private ImmediateCommitCoordinator(String registryName, List<ExecutionUnit> units) {
            this.registryName = registryName;
            List<Integer> order = new ArrayList<>(listenerCount(units));
            Map<Integer, String> labels = new LinkedHashMap<>();
            for (ExecutionUnit unit : units) {
                for (ListenerPlan plan : unit.plans) {
                    order.add(plan.index);
                    labels.put(plan.index, listenerLabel(plan));
                }
            }
            this.listenerOrder = Collections.unmodifiableList(order);
            this.listenerLabels = Collections.unmodifiableMap(labels);
        }

        private void awaitTurn(int listenerIndex) {
            long startedAt = System.nanoTime();
            long diagnosticIntervalNanos = IMMEDIATE_COMMIT_WAIT_DIAGNOSTICS_MILLIS <= 0
                    ? 0L
                    : IMMEDIATE_COMMIT_WAIT_DIAGNOSTICS_MILLIS * 1_000_000L;
            long nextDiagnosticAt = diagnosticIntervalNanos <= 0L ? Long.MAX_VALUE : startedAt + diagnosticIntervalNanos;
            synchronized (this) {
                while (true) {
                    if (failedIndex < listenerIndex) {
                        throw new EarlierRegistryListenerFailed(failedIndex, listenerIndex);
                    }
                    if (activePosition >= listenerOrder.size()
                            || listenerOrder.get(activePosition) == listenerIndex) {
                        return;
                    }
                    long waitMillis = 0L;
                    if (diagnosticIntervalNanos > 0L) {
                        long now = System.nanoTime();
                        if (now >= nextDiagnosticAt) {
                            logWaitDiagnostic(listenerIndex, (now - startedAt) / 1_000_000L);
                            nextDiagnosticAt = now + diagnosticIntervalNanos;
                        }
                        waitMillis = Math.max(1L, Math.min(
                                IMMEDIATE_COMMIT_WAIT_DIAGNOSTICS_MILLIS,
                                Math.max(1L, (nextDiagnosticAt - now) / 1_000_000L)
                        ));
                    }
                    try {
                        if (waitMillis > 0L) {
                            this.wait(waitMillis);
                        } else {
                            this.wait();
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted while waiting for ordered registry commit turn", exception);
                    }
                }
            }
        }

        private boolean isTurn(int listenerIndex) {
            synchronized (this) {
                return activePosition < listenerOrder.size()
                        && listenerOrder.get(activePosition) == listenerIndex;
            }
        }

        private void logWaitDiagnostic(int blockedIndex, long waitedMillis) {
            int activeIndex = activePosition < listenerOrder.size() ? listenerOrder.get(activePosition) : -1;
            GPOM.LOGGER.warn(
                    "[RegistryParallel] immediate commit wait registry={} blocked={} waited={}ms activePosition={}/{} active={} activeCompleted={} completed={} failed={} orderWindow={}",
                    registryName,
                    listenerLabel(blockedIndex),
                    waitedMillis,
                    activePosition,
                    listenerOrder.size(),
                    listenerLabel(activeIndex),
                    completed.contains(activeIndex),
                    completed.size(),
                    failedIndex == Integer.MAX_VALUE ? "<none>" : listenerLabel(failedIndex),
                    listenerWindow()
            );
        }

        private String listenerWindow() {
            if (listenerOrder.isEmpty()) {
                return "[]";
            }
            int from = Math.max(0, activePosition - 3);
            int to = Math.min(listenerOrder.size(), activePosition + 4);
            StringBuilder builder = new StringBuilder("[");
            for (int i = from; i < to; i++) {
                if (i > from) {
                    builder.append(", ");
                }
                if (i == activePosition) {
                    builder.append('*');
                }
                builder.append(listenerLabel(listenerOrder.get(i)));
            }
            if (to < listenerOrder.size()) {
                builder.append(", ...");
            }
            return builder.append(']').toString();
        }

        private String listenerLabel(int listenerIndex) {
            String label = listenerLabels.get(listenerIndex);
            return label == null ? "#" + listenerIndex + " <unknown>" : label;
        }

        private static String listenerLabel(ListenerPlan plan) {
            return "#" + plan.index + " " + modLabel(plan);
        }

        private void listenerFinished(int listenerIndex, QueuingRegistry registry, Throwable failure) {
            boolean recorded = false;
            while (true) {
                QueuingRegistry readyRegistry;
                synchronized (this) {
                    if (!recorded) {
                        if (failure != null && listenerIndex < failedIndex) {
                            failedIndex = listenerIndex;
                        }
                        completed.add(listenerIndex);
                        completedRegistries.put(listenerIndex, registry);
                        recorded = true;
                    }
                    if (activePosition >= listenerOrder.size()) {
                        this.notifyAll();
                        return;
                    }
                    int activeListener = listenerOrder.get(activePosition);
                    if (failedIndex <= activeListener) {
                        activePosition = listenerOrder.size();
                        this.notifyAll();
                        return;
                    }
                    if (!completed.contains(activeListener)) {
                        this.notifyAll();
                        return;
                    }
                    readyRegistry = completedRegistries.get(activeListener);
                }

                if (readyRegistry != null) {
                    readyRegistry.commitDeferredImmediateMutations();
                }

                synchronized (this) {
                    activePosition++;
                    this.notifyAll();
                }
            }
        }
    }

    private static final class EarlierRegistryListenerFailed extends RuntimeException {
        private EarlierRegistryListenerFailed(int failedIndex, int blockedIndex) {
            super("Registry listener " + blockedIndex + " was blocked because earlier listener " + failedIndex + " failed");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static final class QueuingRegistry implements IForgeRegistryModifiable {
        private final IForgeRegistry backing;
        private final IForgeRegistryModifiable modifiableBacking;
        private final boolean immediateCommit;
        private final int listenerIndex;
        private final ImmediateCommitCoordinator immediateCoordinator;
        private final List<RegistryMutation> mutations = new ArrayList<>();
        private final LinkedHashMap<ResourceLocation, IForgeRegistryEntry> localValues = new LinkedHashMap<>();
        private final Set<ResourceLocation> removedKeys = new LinkedHashSet<>();
        private int immediateMutations;
        private int committedQueuedMutations;
        private boolean cleared;

        private QueuingRegistry(IForgeRegistry backing,
                                String registryName,
                                int listenerIndex,
                                ImmediateCommitCoordinator immediateCoordinator) {
            this.backing = backing;
            this.modifiableBacking = backing instanceof IForgeRegistryModifiable ? (IForgeRegistryModifiable) backing : null;
            this.immediateCommit = usesImmediateCommit(registryName);
            this.listenerIndex = listenerIndex;
            this.immediateCoordinator = immediateCoordinator;
        }

        private boolean accepts(IForgeRegistryEntry value) {
            return value != null && getRegistrySuperType().isAssignableFrom(value.getClass());
        }

        @Override
        public Class getRegistrySuperType() {
            return backing.getRegistrySuperType();
        }

        @Override
        public void register(IForgeRegistryEntry value) {
            if (immediateCommit) {
                registerImmediate(value);
                return;
            }
            queueRegister(value);
        }

        private void queueRegister(IForgeRegistryEntry value) {
            if (value == null) {
                mutations.add(registry -> registry.register(null));
                return;
            }
            ResourceLocation key = value.getRegistryName();
            if (key != null) {
                localValues.put(key, value);
                removedKeys.remove(key);
            }
            mutations.add(registry -> registry.register(value));
        }

        private void registerFromWorker(IForgeRegistryEntry value) {
            if (!immediateCommit) {
                queueRegister(value);
                return;
            }

            registerImmediate(value);
        }

        private void registerImmediate(IForgeRegistryEntry value) {
            if (value == null) {
                queueRegister(null);
                return;
            }
            ResourceLocation key = value.getRegistryName();
            if (key != null) {
                localValues.put(key, value);
                removedKeys.remove(key);
            }
            applyImmediateMutation(registry -> registry.register(value));
        }

        @Override
        @SafeVarargs
        public final void registerAll(IForgeRegistryEntry... values) {
            if (values == null) {
                return;
            }
            for (IForgeRegistryEntry value : values) {
                register(value);
            }
        }

        @Override
        public boolean containsKey(ResourceLocation key) {
            if (key == null) {
                return false;
            }
            if (localValues.containsKey(key)) {
                return true;
            }
            if (cleared || removedKeys.contains(key)) {
                return false;
            }
            return backing.containsKey(key);
        }

        @Override
        public boolean containsValue(IForgeRegistryEntry value) {
            if (localValues.containsValue(value)) {
                return true;
            }
            if (cleared) {
                return false;
            }
            return backing.containsValue(value);
        }

        @Override
        public IForgeRegistryEntry getValue(ResourceLocation key) {
            if (key == null) {
                return (IForgeRegistryEntry) backing.getValue(null);
            }
            IForgeRegistryEntry local = localValues.get(key);
            if (local != null) {
                return local;
            }
            if (cleared || removedKeys.contains(key)) {
                return null;
            }
            return (IForgeRegistryEntry) backing.getValue(key);
        }

        @Override
        public ResourceLocation getKey(IForgeRegistryEntry value) {
            for (Map.Entry<ResourceLocation, IForgeRegistryEntry> entry : localValues.entrySet()) {
                if (entry.getValue() == value || (entry.getValue() != null && entry.getValue().equals(value))) {
                    return entry.getKey();
                }
            }
            if (cleared) {
                return null;
            }
            return backing.getKey(value);
        }

        @Override
        public Set<ResourceLocation> getKeys() {
            LinkedHashSet<ResourceLocation> keys = new LinkedHashSet<>();
            if (!cleared) {
                keys.addAll(backing.getKeys());
                keys.removeAll(removedKeys);
            }
            keys.addAll(localValues.keySet());
            return Collections.unmodifiableSet(keys);
        }

        @Override
        public List getValues() {
            return new ArrayList<>(combinedValues());
        }

        @Override
        public Collection getValuesCollection() {
            return Collections.unmodifiableList(new ArrayList<>(combinedValues()));
        }

        @Override
        public Set getEntries() {
            LinkedHashSet<Map.Entry<ResourceLocation, IForgeRegistryEntry>> entries = new LinkedHashSet<>();
            if (!cleared) {
                for (Object object : backing.getEntries()) {
                    Map.Entry<ResourceLocation, IForgeRegistryEntry> entry = (Map.Entry<ResourceLocation, IForgeRegistryEntry>) object;
                    if (!removedKeys.contains(entry.getKey()) && !localValues.containsKey(entry.getKey())) {
                        entries.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
                    }
                }
            }
            for (Map.Entry<ResourceLocation, IForgeRegistryEntry> entry : localValues.entrySet()) {
                entries.add(new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), entry.getValue()));
            }
            return Collections.unmodifiableSet(entries);
        }

        @Override
        public Object getSlaveMap(ResourceLocation name, Class type) {
            return backing.getSlaveMap(name, type);
        }

        @Override
        public java.util.Iterator iterator() {
            return getValues().iterator();
        }

        @Override
        public void clear() {
            if (modifiableBacking == null) {
                throw new UnsupportedOperationException("Backing registry is not modifiable");
            }
            if (immediateCommit) {
                cleared = true;
                localValues.clear();
                removedKeys.clear();
                applyImmediateMutation(registry -> ((IForgeRegistryModifiable) registry).clear());
                return;
            }
            cleared = true;
            localValues.clear();
            removedKeys.clear();
            mutations.add(registry -> ((IForgeRegistryModifiable) registry).clear());
        }

        @Override
        public IForgeRegistryEntry remove(ResourceLocation key) {
            if (modifiableBacking == null) {
                throw new UnsupportedOperationException("Backing registry is not modifiable");
            }
            IForgeRegistryEntry existing = getValue(key);
            if (immediateCommit) {
                if (key != null) {
                    localValues.remove(key);
                    if (!cleared) {
                        removedKeys.add(key);
                    }
                }
                applyImmediateMutation(registry -> ((IForgeRegistryModifiable) registry).remove(key));
                return existing;
            }
            if (key != null) {
                localValues.remove(key);
                if (!cleared) {
                    removedKeys.add(key);
                }
            }
            mutations.add(registry -> ((IForgeRegistryModifiable) registry).remove(key));
            return existing;
        }

        @Override
        public boolean isLocked() {
            return modifiableBacking != null && modifiableBacking.isLocked();
        }

        private List<IForgeRegistryEntry> combinedValues() {
            List<IForgeRegistryEntry> values = new ArrayList<>();
            if (!cleared) {
                for (Object object : backing.getValues()) {
                    IForgeRegistryEntry value = (IForgeRegistryEntry) object;
                    ResourceLocation key = backing.getKey(value);
                    if (key == null || (!removedKeys.contains(key) && !localValues.containsKey(key))) {
                        values.add(value);
                    }
                }
            }
            values.addAll(localValues.values());
            return values;
        }

        private void commitDeferredImmediateMutations() {
            if (immediateCommit) {
                commitToBacking();
            }
        }

        private void applyImmediateMutation(RegistryMutation mutation) {
            if (immediateCoordinator != null && !immediateCoordinator.isTurn(listenerIndex)) {
                mutations.add(mutation);
                return;
            }
            BYPASS_WORKER_QUEUE.set(Boolean.TRUE);
            try {
                mutation.apply(backing);
            } finally {
                BYPASS_WORKER_QUEUE.remove();
            }
            immediateMutations++;
        }

        private void commitToBacking() {
            if (mutations.isEmpty()) {
                return;
            }
            List<RegistryMutation> pending = new ArrayList<>(mutations);
            mutations.clear();
            BYPASS_WORKER_QUEUE.set(Boolean.TRUE);
            try {
                for (RegistryMutation mutation : pending) {
                    mutation.apply(backing);
                }
            } finally {
                BYPASS_WORKER_QUEUE.remove();
            }
            committedQueuedMutations += pending.size();
        }

        private int mutationCount() {
            return mutations.size() + immediateMutations + committedQueuedMutations;
        }
    }
}
