package com.l.gpom.profiling;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionFields;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorldLifecycleProfiler {
    private static final MemoryMXBean MEMORY = ManagementFactory.getMemoryMXBean();
    private static final Object LOCK = new Object();
    private static final List<DelayedSnapshot> DELAYED = new ArrayList<>();
    private static final List<TrackedWorld> TRACKED_WORLDS = new ArrayList<>();
    private static final int MAX_GRAPH_OBJECTS = 6000;
    private static final int MAX_TRACKED_WORLDS = 96;
    private static int nextTransitionId;
    private static ActiveTransition activeTransition;
    private static TextureBaseline textureBaseline;

    private WorldLifecycleProfiler() {
    }

    public static void beginLoadWorld(Minecraft minecraft, Object previousWorld, Object nextWorld, String message) {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            activeTransition = new ActiveTransition(++nextTransitionId, transitionKind(previousWorld, nextWorld), previousWorld, nextWorld, message);
            if (previousWorld != null && previousWorld != nextWorld) {
                trackWorld(previousWorld, activeTransition, "loadWorld-previous");
            }
            Snapshot snapshot = capture(minecraft, previousWorld, nextWorld, "before");
            logSnapshot(activeTransition, snapshot);
        }
    }

    public static void endLoadWorld(Minecraft minecraft, Object currentWorld, Object player, Object screen) {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            ActiveTransition transition = activeTransition;
            if (transition == null) {
                return;
            }
            Snapshot snapshot = capture(minecraft, currentWorld, currentWorld, "after");
            logSnapshot(transition, snapshot.withClientState(player, screen));
            scheduleDelayedSnapshots(transition);
            activeTransition = null;
        }
    }

    public static void checkpoint(Minecraft minecraft, Object previousWorld, Object nextWorld, String point) {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            ActiveTransition transition = activeTransition;
            if (transition == null) {
                return;
            }
            Snapshot snapshot = capture(minecraft, previousWorld, nextWorld, point);
            logSnapshot(transition, snapshot);
        }
    }

    public static void clientTick(Minecraft minecraft) {
        if (!enabled()) {
            return;
        }
        long now = System.nanoTime();
        synchronized (LOCK) {
            for (int i = DELAYED.size() - 1; i >= 0; i--) {
                DelayedSnapshot delayed = DELAYED.get(i);
                if (now < delayed.dueAtNanos) {
                    continue;
                }
                DELAYED.remove(i);
                if (GpomEarlyConfig.worldLifecycleProfilerForceGcBeforeSnapshots()) {
                    System.gc();
                }
                Object currentWorld = ReflectionFields.get(minecraft, "world", "world", "field_71441_e", "f");
                Object player = ReflectionFields.get(minecraft, "player", "player", "field_71439_g", "h");
                Object screen = ReflectionFields.get(minecraft, "currentScreen", "currentScreen", "field_71462_r", "m");
                Snapshot snapshot = capture(minecraft, currentWorld, currentWorld, "delayed+" + delayed.delayMillis + "ms")
                        .withClientState(player, screen);
                logSnapshot(delayed.transition, snapshot);
                logRetainedWorlds(delayed.transition, snapshot);
            }
        }
    }

    public static void worldEvent(String event, Object world) {
        if (!enabled()) {
            return;
        }
        synchronized (LOCK) {
            ActiveTransition transition = activeTransition;
            if ("unload".equals(event)) {
                trackWorld(world, transition, "WorldEvent.Unload");
            }
            RetainedWorldProfile retained = retainedWorldProfile(world);
            AsyncProbeLogger.info(
                    "[WorldLifecycleEvent] {} transition={} world={} side={} retainedOldWorlds={} tracked={} cleared={} retained={}",
                    clean(event),
                    transition == null ? "none" : "#" + transition.id + ' ' + transition.kind,
                    worldLabel(world),
                    worldSide(world),
                    retained.liveOldWorlds,
                    retained.totalRefs,
                    retained.clearedRefs,
                    formatList(retained.liveLabels, Math.max(1, GpomEarlyConfig.worldLifecycleProfilerDeepAttributionMaxEntries()))
            );
        }
    }

    private static void scheduleDelayedSnapshots(ActiveTransition transition) {
        long now = System.nanoTime();
        for (long delayMillis : delayedSnapshotMillis()) {
            if (delayMillis <= 0L) {
                continue;
            }
            DELAYED.add(new DelayedSnapshot(transition.copy(), delayMillis, now + delayMillis * 1_000_000L));
        }
    }

    private static Snapshot capture(Minecraft minecraft, Object previousWorld, Object nextWorld, String point) {
        Runtime runtime = Runtime.getRuntime();
        MemoryUsage heap = MEMORY.getHeapMemoryUsage();
        MemoryUsage nonHeap = MEMORY.getNonHeapMemoryUsage();
        Snapshot snapshot = new Snapshot(point);
        snapshot.heapUsedBytes = heap.getUsed();
        snapshot.heapCommittedBytes = heap.getCommitted();
        snapshot.nonHeapUsedBytes = nonHeap.getUsed();
        snapshot.runtimeUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        snapshot.runtimeCommittedBytes = runtime.totalMemory();
        snapshot.directBufferBytes = bufferPoolBytes("direct");
        snapshot.mappedBufferBytes = bufferPoolBytes("mapped");
        snapshot.previousWorld = worldLabel(previousWorld);
        snapshot.nextWorld = worldLabel(nextWorld);
        snapshot.worldEntities = countCollection(nextWorld, "loadedEntityList", "loadedEntityList", "field_72996_f", "f");
        snapshot.worldTiles = countCollection(nextWorld, "loadedTileEntityList", "loadedTileEntityList", "field_147482_g", "g");
        snapshot.worldWeather = countCollection(nextWorld, "weatherEffects", "weatherEffects", "field_73007_j", "j");
        snapshot.worldPlayers = countCollection(nextWorld, "playerEntities", "playerEntities", "field_73010_i", "i");
        snapshot.renderChunksToUpdate = -1;
        snapshot.renderInfos = -1;
        snapshot.tileEntitiesToRender = -1;
        snapshot.damagedBlocks = -1;
        snapshot.soundPositions = -1;
        snapshot.textureObjects = -1;
        snapshot.tickableTextures = -1;
        snapshot.particleEmitters = -1;
        snapshot.particleLayerEntries = -1;
        snapshot.playingSounds = -1;
        snapshot.delayedSounds = -1;
        snapshot.retainedTrackedWorldRefs = -1;
        snapshot.retainedClearedWorldRefs = -1;
        snapshot.retainedOldWorlds = -1;
        snapshot.retainedOldWorldLabels = Collections.emptyList();
        captureRetainedWorlds(snapshot, nextWorld);

        if (minecraft != null) {
            Object renderGlobal = ReflectionFields.get(minecraft, "renderGlobal", "renderGlobal", "field_71438_f", "g");
            snapshot.renderChunksToUpdate = countCollection(renderGlobal, "chunksToUpdate", "chunksToUpdate", "field_175009_l", "l");
            snapshot.renderInfos = countCollection(renderGlobal, "renderInfos", "renderInfos", "field_72755_R", "m");
            snapshot.tileEntitiesToRender = countCollection(renderGlobal, "setTileEntities", "setTileEntities", "field_181024_n", "n");
            snapshot.damagedBlocks = countMap(renderGlobal, "damagedBlocks", "damagedBlocks", "field_72738_E", "x");
            snapshot.soundPositions = countMap(renderGlobal, "mapSoundPositions", "mapSoundPositions", "field_147593_P", "y");

            Object textureManager = ReflectionFields.get(minecraft, "renderEngine", "renderEngine", "field_71446_o", "Y");
            snapshot.textureObjects = countMap(textureManager, "mapTextureObjects", "mapTextureObjects", "field_110585_a", "b");
            snapshot.tickableTextures = countCollection(textureManager, "listTickables", "listTickables", "field_110583_b", "c");

            Object effectRenderer = ReflectionFields.get(minecraft, "effectRenderer", "effectRenderer", "field_71452_i", "j");
            snapshot.particleEmitters = countCollection(effectRenderer, "particleEmitters", "particleEmitters", "field_178933_d", "d");
            snapshot.particleLayerEntries = countParticleLayers(effectRenderer);

            Object soundHandler = ReflectionFields.get(minecraft, "soundHandler", "soundHandler", "field_147127_av", "aa");
            Object soundManager = soundHandler == null ? null : ReflectionFields.get(soundHandler, "sndManager", "sndManager", "field_147694_f", "f");
            snapshot.playingSounds = countMap(soundManager, "playingSounds", "playingSounds", "field_148629_h", "h");
            snapshot.delayedSounds = countMap(soundManager, "delayedSounds", "delayedSounds", "field_148630_i", "i");

            if (GpomEarlyConfig.worldLifecycleProfilerDeepAttributionEnabled()) {
                captureDeepAttribution(snapshot, minecraft, renderGlobal, textureManager, effectRenderer, soundManager);
            }
        }
        return snapshot;
    }

    private static void logSnapshot(ActiveTransition transition, Snapshot snapshot) {
        long elapsedMs = transition.elapsedMillis();
        AsyncProbeLogger.info(
                "[WorldLifecycle] #{} {} {} elapsed={}ms msg=\"{}\" prev={} next={} heap={}MiB/{}MiB runtime={}MiB/{}MiB nonHeap={}MiB direct={}MiB mapped={}MiB world[e={},te={},weather={},players={}] render[chunks={},infos={},tes={},damage={},sounds={}] client[textures={},tickTex={},particles={},emitters={},playingSounds={},delayedSounds={}] retained[oldWorlds={},tracked={},cleared={}] player={} screen={}",
                transition.id,
                transition.kind,
                snapshot.point,
                elapsedMs,
                clean(transition.message),
                snapshot.previousWorld,
                snapshot.nextWorld,
                mib(snapshot.heapUsedBytes),
                mib(snapshot.heapCommittedBytes),
                mib(snapshot.runtimeUsedBytes),
                mib(snapshot.runtimeCommittedBytes),
                mib(snapshot.nonHeapUsedBytes),
                mib(snapshot.directBufferBytes),
                mib(snapshot.mappedBufferBytes),
                snapshot.worldEntities,
                snapshot.worldTiles,
                snapshot.worldWeather,
                snapshot.worldPlayers,
                snapshot.renderChunksToUpdate,
                snapshot.renderInfos,
                snapshot.tileEntitiesToRender,
                snapshot.damagedBlocks,
                snapshot.soundPositions,
                snapshot.textureObjects,
                snapshot.tickableTextures,
                snapshot.particleLayerEntries,
                snapshot.particleEmitters,
                snapshot.playingSounds,
                snapshot.delayedSounds,
                snapshot.retainedOldWorlds,
                snapshot.retainedTrackedWorldRefs,
                snapshot.retainedClearedWorldRefs,
                snapshot.playerPresent,
                snapshot.screenName
        );

        if (GpomEarlyConfig.worldLifecycleProfilerDeepAttributionEnabled()) {
            logDeepSnapshot(transition, snapshot);
        }
    }

    private static void logRetainedWorlds(ActiveTransition transition, Snapshot snapshot) {
        if (snapshot.retainedOldWorlds <= 0) {
            return;
        }
        AsyncProbeLogger.warn(
                "[WorldLifecycleLeakProbe] #{} {} {} retainedOldWorlds={} tracked={} cleared={} labels={}",
                transition.id,
                transition.kind,
                snapshot.point,
                snapshot.retainedOldWorlds,
                snapshot.retainedTrackedWorldRefs,
                snapshot.retainedClearedWorldRefs,
                formatList(snapshot.retainedOldWorldLabels, Math.max(1, GpomEarlyConfig.worldLifecycleProfilerDeepAttributionMaxEntries()))
        );
    }

    private static void captureRetainedWorlds(Snapshot snapshot, Object currentWorld) {
        RetainedWorldProfile retained = retainedWorldProfile(currentWorld);
        snapshot.retainedTrackedWorldRefs = retained.totalRefs;
        snapshot.retainedClearedWorldRefs = retained.clearedRefs;
        snapshot.retainedOldWorlds = retained.liveOldWorlds;
        snapshot.retainedOldWorldLabels = retained.liveLabels;
    }

    private static void captureDeepAttribution(Snapshot snapshot, Minecraft minecraft, Object renderGlobal, Object textureManager, Object effectRenderer, Object soundManager) {
        try {
            snapshot.textureProfile = captureTextureProfile(textureManager);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "textures=" + throwable.getClass().getSimpleName());
        }
        try {
            snapshot.renderProfile = captureRenderProfile(renderGlobal);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "render=" + throwable.getClass().getSimpleName());
        }
        try {
            Object resourceManager = fieldValue(minecraft, "mcResourceManager", "resourceManager", "field_110451_am", "an");
            snapshot.resourceProfile = captureResourceProfile(resourceManager);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "resources=" + throwable.getClass().getSimpleName());
        }
        try {
            snapshot.loliAsmStatefulProfile = captureLoliAsmStatefulProfile();
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "loliasm=" + throwable.getClass().getSimpleName());
        }
        try {
            snapshot.renderGraphProfile = captureObjectGraph("renderGlobal", renderGlobal);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "renderGraph=" + throwable.getClass().getSimpleName());
        }
        try {
            snapshot.textureGraphProfile = captureObjectGraph("textureManager", textureManager);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "textureGraph=" + throwable.getClass().getSimpleName());
        }
        try {
            snapshot.effectGraphProfile = captureObjectGraph("effectRenderer", effectRenderer);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "effectGraph=" + throwable.getClass().getSimpleName());
        }
        try {
            snapshot.soundGraphProfile = captureObjectGraph("soundManager", soundManager);
        } catch (Throwable throwable) {
            snapshot.deepFailure = append(snapshot.deepFailure, "soundGraph=" + throwable.getClass().getSimpleName());
        }
    }

    private static void logDeepSnapshot(ActiveTransition transition, Snapshot snapshot) {
        int limit = Math.max(1, GpomEarlyConfig.worldLifecycleProfilerDeepAttributionMaxEntries());
        String prefix = "[WorldLifecycleDeep] #" + transition.id + ' ' + transition.kind + ' ' + snapshot.point;
        if (snapshot.textureProfile != null) {
            TextureProfile texture = snapshot.textureProfile;
            AsyncProbeLogger.info(
                    "{} textures total={} tickable={} baseline={} added={} removed={} domains={} addedDomains={} classes={} addedClasses={} addedSamples={}",
                    prefix,
                    texture.total,
                    texture.tickable,
                    texture.baselineTotal,
                    texture.addedSinceBaseline,
                    texture.removedSinceBaseline,
                    formatTop(texture.domainCounts, limit),
                    formatTop(texture.addedDomainCounts, limit),
                    formatTop(texture.classCounts, limit),
                    formatTop(texture.addedClassCounts, limit),
                    formatList(texture.addedSamples, limit)
            );
        }
        if (snapshot.renderProfile != null) {
            RenderProfile render = snapshot.renderProfile;
            AsyncProbeLogger.info(
                    "{} render viewFrustum={} renderChunks={} dispatcher={} renderer={} globalFields={} frustumFields={} dispatcherFields={}",
                    prefix,
                    render.viewFrustumClass,
                    render.viewFrustumRenderChunks,
                    render.dispatcherClass,
                    render.renderContainerClass,
                    formatTop(render.renderGlobalFieldSizes, limit),
                    formatTop(render.viewFrustumFieldSizes, limit),
                    formatTop(render.dispatcherFieldSizes, limit)
            );
        }
        if (snapshot.resourceProfile != null) {
            ResourceProfile resources = snapshot.resourceProfile;
            AsyncProbeLogger.info(
                    "{} resources manager={} domains={} domainKeys={} reloadListeners={} listenerClasses={}",
                    prefix,
                    resources.managerClass,
                    resources.domainManagers,
                    formatTop(resources.domainCounts, limit),
                    resources.reloadListeners,
                    formatTop(resources.reloadListenerClasses, limit)
            );
        }
        if (snapshot.loliAsmStatefulProfile != null) {
            LoliAsmStatefulProfile profile = snapshot.loliAsmStatefulProfile;
            AsyncProbeLogger.info(
                    "{} loliasmStateful entries={} live={} cleared={} classes={}",
                    prefix,
                    profile.entries,
                    profile.live,
                    profile.cleared,
                    formatTop(profile.classCounts, limit)
            );
        }
        logGraph(prefix, snapshot.renderGraphProfile, limit);
        logGraph(prefix, snapshot.textureGraphProfile, limit);
        logGraph(prefix, snapshot.effectGraphProfile, limit);
        logGraph(prefix, snapshot.soundGraphProfile, limit);
        if (snapshot.deepFailure != null) {
            AsyncProbeLogger.warn("{} failures {}", prefix, snapshot.deepFailure);
        }
    }

    private static void logGraph(String prefix, ObjectGraphProfile graph, int limit) {
        if (graph == null) {
            return;
        }
        AsyncProbeLogger.info(
                "{} graph root={} visited={} truncated={} byteBuffers={} buffers={} bufferCapacity={}MiB top={} interesting={} bufferClasses={}",
                prefix,
                graph.rootName,
                graph.visited,
                graph.truncated,
                graph.byteBuffers,
                graph.buffers,
                mib(graph.bufferCapacityBytes),
                formatTop(graph.classCounts, limit),
                formatTop(graph.interestingClassCounts, limit),
                formatTop(graph.bufferClassCounts, limit)
        );
    }

    private static TextureProfile captureTextureProfile(Object textureManager) {
        TextureProfile profile = new TextureProfile();
        Object rawMap = fieldValue(textureManager, "mapTextureObjects", "field_110585_a", "mapTextureObjects", "b");
        if (!(rawMap instanceof Map)) {
            return profile;
        }

        Map<?, ?> textures = (Map<?, ?>) rawMap;
        profile.total = textures.size();
        profile.tickable = countCollectionQuiet(textureManager, "field_110583_b", "listTickables", "c");

        Map<String, Object> current = new HashMap<>();
        for (Map.Entry<?, ?> entry : textures.entrySet()) {
            String key = textureKey(entry.getKey());
            current.put(key, entry.getValue());
            increment(profile.domainCounts, textureDomain(key));
            increment(profile.classCounts, className(entry.getValue()));
        }

        TextureBaseline baseline = textureBaseline;
        if (baseline == null && !current.isEmpty()) {
            baseline = new TextureBaseline(current.keySet());
            textureBaseline = baseline;
        }

        if (baseline != null) {
            profile.baselineTotal = baseline.keys.size();
            for (Map.Entry<String, Object> entry : current.entrySet()) {
                if (baseline.keys.contains(entry.getKey())) {
                    continue;
                }
                profile.addedSinceBaseline++;
                increment(profile.addedDomainCounts, textureDomain(entry.getKey()));
                increment(profile.addedClassCounts, className(entry.getValue()));
                if (profile.addedSamples.size() < Math.max(1, GpomEarlyConfig.worldLifecycleProfilerDeepAttributionMaxEntries())) {
                    profile.addedSamples.add(entry.getKey() + "->" + simpleClassName(entry.getValue()));
                }
            }
            for (String baselineKey : baseline.keys) {
                if (!current.containsKey(baselineKey)) {
                    profile.removedSinceBaseline++;
                }
            }
        }
        return profile;
    }

    private static RenderProfile captureRenderProfile(Object renderGlobal) {
        RenderProfile profile = new RenderProfile();
        profile.renderGlobalFieldSizes = fieldSizeSummary(renderGlobal);
        Object viewFrustum = fieldValue(renderGlobal, "viewFrustum", "field_175008_n", "viewFrustum", "n");
        profile.viewFrustumClass = className(viewFrustum);
        profile.viewFrustumFieldSizes = fieldSizeSummary(viewFrustum);
        profile.viewFrustumRenderChunks = countNestedRenderChunks(viewFrustum);

        Object dispatcher = fieldValue(renderGlobal, "renderDispatcher", "field_174995_M", "renderDispatcher", "M");
        profile.dispatcherClass = className(dispatcher);
        profile.dispatcherFieldSizes = fieldSizeSummary(dispatcher);

        Object renderContainer = fieldValue(renderGlobal, "renderContainer", "field_174996_N", "renderContainer", "N");
        profile.renderContainerClass = className(renderContainer);
        return profile;
    }

    private static ResourceProfile captureResourceProfile(Object resourceManager) {
        ResourceProfile profile = new ResourceProfile();
        profile.managerClass = className(resourceManager);
        Object domains = fieldValue(resourceManager, "domainResourceManagers", "field_110548_a", "domainResourceManagers", "a");
        if (domains instanceof Map) {
            Map<?, ?> domainMap = (Map<?, ?>) domains;
            profile.domainManagers = domainMap.size();
            for (Object key : domainMap.keySet()) {
                increment(profile.domainCounts, String.valueOf(key));
            }
        }

        Object listeners = fieldValue(resourceManager, "reloadListeners", "field_110546_b", "reloadListeners", "b");
        if (listeners instanceof Collection) {
            Collection<?> collection = (Collection<?>) listeners;
            profile.reloadListeners = collection.size();
            for (Object listener : collection) {
                increment(profile.reloadListenerClasses, className(listener));
            }
        }
        return profile;
    }

    private static LoliAsmStatefulProfile captureLoliAsmStatefulProfile() throws NoSuchFieldException, IllegalAccessException {
        Class<?> statefulClass = tryLoadClass("zone.rong.loliasm.common.crashes.IStateful");
        if (statefulClass == null) {
            return null;
        }
        LoliAsmStatefulProfile profile = new LoliAsmStatefulProfile();
        Field field = statefulClass.getDeclaredField("INSTANCES");
        field.setAccessible(true);
        Object rawInstances = field.get(null);
        if (!(rawInstances instanceof Collection)) {
            return profile;
        }
        for (Object value : (Collection<?>) rawInstances) {
            profile.entries++;
            Object referent = value instanceof WeakReference ? ((WeakReference<?>) value).get() : value;
            if (referent == null) {
                profile.cleared++;
            } else {
                profile.live++;
                increment(profile.classCounts, className(referent));
            }
        }
        return profile;
    }

    private static ObjectGraphProfile captureObjectGraph(String rootName, Object root) {
        ObjectGraphProfile profile = new ObjectGraphProfile(rootName);
        if (root == null) {
            return profile;
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<GraphNode> pending = new ArrayList<>();
        pending.add(new GraphNode(root, 0));
        while (!pending.isEmpty()) {
            GraphNode node = pending.remove(pending.size() - 1);
            Object value = node.value;
            if (value == null || visited.contains(value)) {
                continue;
            }
            if (visited.size() >= MAX_GRAPH_OBJECTS) {
                profile.truncated = true;
                break;
            }
            visited.add(value);
            profile.visited++;
            String className = className(value);
            increment(profile.classCounts, className);
            if (isInterestingClass(className)) {
                increment(profile.interestingClassCounts, className);
            }
            if (isByteBufferClass(className)) {
                profile.byteBuffers++;
            }
            if (value instanceof Buffer) {
                profile.buffers++;
                profile.bufferCapacityBytes += estimatedBufferBytes((Buffer) value);
                increment(profile.bufferClassCounts, className);
            }
            if (node.depth >= 4 || !shouldTraverse(value)) {
                continue;
            }
            enqueueChildren(value, node.depth + 1, pending, visited);
        }
        return profile;
    }

    private static void enqueueChildren(Object value, int depth, List<GraphNode> pending, Set<Object> visited) {
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Math.min(Array.getLength(value), 4096);
            for (int i = 0; i < length; i++) {
                enqueue(Array.get(value, i), depth, pending, visited);
            }
            return;
        }
        if (value instanceof Collection) {
            int count = 0;
            for (Object element : (Collection<?>) value) {
                enqueue(element, depth, pending, visited);
                if (++count >= 4096) {
                    break;
                }
            }
            return;
        }
        if (value instanceof Map) {
            int count = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                enqueue(entry.getKey(), depth, pending, visited);
                enqueue(entry.getValue(), depth, pending, visited);
                if (++count >= 4096) {
                    break;
                }
            }
            return;
        }

        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                break;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    enqueue(field.get(value), depth, pending, visited);
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    private static void enqueue(Object value, int depth, List<GraphNode> pending, Set<Object> visited) {
        if (value == null || visited.contains(value) || isScalar(value)) {
            return;
        }
        pending.add(new GraphNode(value, depth));
    }

    private static boolean shouldTraverse(Object value) {
        if (isScalar(value)) {
            return false;
        }
        String name = value.getClass().getName();
        return !name.startsWith("net.minecraft.world.")
                && !name.startsWith("net.minecraft.server.")
                && !name.equals("net.minecraft.client.Minecraft")
                && !name.startsWith("java.lang.reflect.")
                && !name.startsWith("sun.")
                && !name.startsWith("jdk.");
    }

    private static boolean isScalar(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum
                || value instanceof Class;
    }

    private static boolean isInterestingClass(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains("nothirium")
                || lower.contains("render")
                || lower.contains("texture")
                || lower.contains("chunk")
                || lower.contains("buffer")
                || lower.contains("vbo")
                || lower.contains("vertex")
                || lower.contains("framebuffer")
                || lower.contains("particle")
                || lower.contains("sound")
                || lower.contains("astral")
                || lower.contains("betweenlands")
                || lower.contains("hammer")
                || lower.contains("vintage")
                || lower.contains("loli");
    }

    private static boolean isByteBufferClass(String name) {
        return name.startsWith("java.nio.") && name.endsWith("ByteBuffer");
    }

    private static void trackWorld(Object world, ActiveTransition transition, String reason) {
        if (world == null || isAlreadyTracked(world)) {
            pruneTrackedWorlds(false);
            return;
        }
        TRACKED_WORLDS.add(new TrackedWorld(
                new WeakReference<>(world),
                worldLabel(world),
                transition == null ? -1 : transition.id,
                reason,
                System.nanoTime()
        ));
        pruneTrackedWorlds(true);
    }

    private static boolean isAlreadyTracked(Object world) {
        for (TrackedWorld tracked : TRACKED_WORLDS) {
            Object value = tracked.reference.get();
            if (value == world) {
                return true;
            }
        }
        return false;
    }

    private static RetainedWorldProfile retainedWorldProfile(Object currentWorld) {
        RetainedWorldProfile profile = new RetainedWorldProfile();
        long now = System.nanoTime();
        for (TrackedWorld tracked : TRACKED_WORLDS) {
            profile.totalRefs++;
            Object value = tracked.reference.get();
            if (value == null) {
                profile.clearedRefs++;
                continue;
            }
            if (value == currentWorld) {
                continue;
            }
            profile.liveOldWorlds++;
            if (profile.liveLabels.size() < Math.max(1, GpomEarlyConfig.worldLifecycleProfilerDeepAttributionMaxEntries())) {
                long ageMillis = Math.max(0L, (now - tracked.createdAtNanos) / 1_000_000L);
                profile.liveLabels.add(tracked.label + "/age=" + ageMillis + "ms/source=" + tracked.reason + "/transition=" + tracked.transitionId);
            }
        }
        pruneTrackedWorlds(false);
        return profile;
    }

    private static void pruneTrackedWorlds(boolean enforceLimit) {
        for (int i = TRACKED_WORLDS.size() - 1; i >= 0; i--) {
            if (TRACKED_WORLDS.get(i).reference.get() == null) {
                TRACKED_WORLDS.remove(i);
            }
        }
        if (!enforceLimit) {
            return;
        }
        while (TRACKED_WORLDS.size() > MAX_TRACKED_WORLDS) {
            TRACKED_WORLDS.remove(0);
        }
    }

    private static long estimatedBufferBytes(Buffer buffer) {
        int elementSize = 1;
        if (buffer instanceof java.nio.CharBuffer || buffer instanceof java.nio.ShortBuffer) {
            elementSize = 2;
        } else if (buffer instanceof java.nio.FloatBuffer || buffer instanceof java.nio.IntBuffer) {
            elementSize = 4;
        } else if (buffer instanceof java.nio.DoubleBuffer || buffer instanceof java.nio.LongBuffer) {
            elementSize = 8;
        }
        return (long) buffer.capacity() * elementSize;
    }

    private static Map<String, Integer> fieldSizeSummary(Object owner) {
        Map<String, Integer> result = new HashMap<>();
        if (owner == null) {
            return result;
        }
        Class<?> current = owner.getClass();
        while (current != null && current != Object.class) {
            Field[] fields;
            try {
                fields = current.getDeclaredFields();
            } catch (Throwable ignored) {
                break;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    int size = objectSize(value);
                    if (size >= 0) {
                        result.put(current.getSimpleName() + "." + field.getName(), size);
                    }
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return result;
    }

    private static int countNestedRenderChunks(Object viewFrustum) {
        Object renderChunks = fieldValue(viewFrustum, "renderChunks", "field_178164_f", "renderChunks", "f");
        return renderChunks == null ? -1 : objectSize(renderChunks);
    }

    private static int objectSize(Object value) {
        if (value == null) {
            return -1;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        if (value instanceof Map) {
            return ((Map<?, ?>) value).size();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value);
        }
        return -1;
    }

    private static int countCollectionQuiet(Object owner, String... names) {
        Object value = fieldValue(owner, names);
        return value instanceof Collection ? ((Collection<?>) value).size() : -1;
    }

    private static Object fieldValue(Object owner, String... names) {
        if (owner == null) {
            return null;
        }
        Class<?> current = owner.getClass();
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.get(owner);
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String textureKey(Object key) {
        return key == null ? "<null>" : String.valueOf(key);
    }

    private static String textureDomain(String key) {
        if (key == null) {
            return "<null>";
        }
        int colon = key.indexOf(':');
        return colon <= 0 ? "<none>" : key.substring(0, colon);
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String simpleClassName(Object value) {
        if (value == null) {
            return "null";
        }
        String name = value.getClass().getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(dot + 1);
    }

    private static void increment(Map<String, Integer> map, String key) {
        map.put(key, map.getOrDefault(key, 0) + 1);
    }

    private static String formatTop(Map<String, Integer> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        StringBuilder builder = new StringBuilder();
        int count = Math.min(Math.max(1, limit), entries.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            Map.Entry<String, Integer> entry = entries.get(i);
            builder.append(shortName(entry.getKey())).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String formatList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(Math.max(1, limit), values.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String shortName(String value) {
        if (value == null) {
            return "null";
        }
        if (value.startsWith("net.minecraft.")) {
            return "mc." + value.substring("net.minecraft.".length());
        }
        if (value.startsWith("thebetweenlands.")) {
            return "betweenlands." + value.substring("thebetweenlands.".length());
        }
        if (value.startsWith("hellfirepvp.astralsorcery.")) {
            return "astral." + value.substring("hellfirepvp.astralsorcery.".length());
        }
        if (value.startsWith("meldexun.nothirium.")) {
            return "nothirium." + value.substring("meldexun.nothirium.".length());
        }
        return value;
    }

    private static String append(String current, String addition) {
        return current == null ? addition : current + ',' + addition;
    }

    private static Class<?> tryLoadClass(String className) {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(className, false, context);
            } catch (ClassNotFoundException ignored) {
            }
        }
        try {
            return Class.forName(className, false, WorldLifecycleProfiler.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }

    private static List<Long> delayedSnapshotMillis() {
        String raw = GpomEarlyConfig.worldLifecycleProfilerDelayedSnapshotMillis();
        List<Long> values = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return values;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            try {
                values.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return values;
    }

    private static String transitionKind(Object previousWorld, Object nextWorld) {
        if (previousWorld == null && nextWorld != null) {
            return "load";
        }
        if (previousWorld != null && nextWorld == null) {
            return "unload";
        }
        if (previousWorld != null && previousWorld != nextWorld) {
            return "switch";
        }
        return "refresh";
    }

    private static String worldLabel(Object world) {
        if (!(world instanceof World)) {
            return world == null ? "null" : world.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(world));
        }
        World typed = (World) world;
        String dimension = "?";
        Integer value = MinecraftMappingCompat.worldDimension(typed);
        if (value != null) {
            dimension = Integer.toString(value);
        }
        return typed.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(typed)) + "/dim=" + dimension;
    }

    private static String worldSide(Object world) {
        if (!(world instanceof World)) {
            return "?";
        }
        return MinecraftMappingCompat.worldIsRemote((World) world) ? "client" : "server";
    }

    private static long bufferPoolBytes(String name) {
        for (BufferPoolMXBean bean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (name.equals(bean.getName())) {
                return Math.max(0L, bean.getMemoryUsed());
            }
        }
        return 0L;
    }

    private static int countCollection(Object owner, String purpose, String... names) {
        if (owner == null) {
            return -1;
        }
        Object value = ReflectionFields.get(owner, purpose, names);
        return value instanceof Collection ? ((Collection<?>) value).size() : -1;
    }

    private static int countMap(Object owner, String purpose, String... names) {
        if (owner == null) {
            return -1;
        }
        Object value = ReflectionFields.get(owner, purpose, names);
        return value instanceof Map ? ((Map<?, ?>) value).size() : -1;
    }

    private static int countParticleLayers(Object effectRenderer) {
        if (effectRenderer == null) {
            return -1;
        }
        Object value = ReflectionFields.get(effectRenderer, "fxLayers", "fxLayers", "field_78876_b", "b");
        if (value instanceof Collection[]) {
            int total = 0;
            for (Collection<?> collection : (Collection<?>[]) value) {
                total += collection == null ? 0 : collection.size();
            }
            return total;
        }
        if (value != null && value.getClass().isArray()) {
            return countArrayCollections(value);
        }
        return -1;
    }

    private static int countArrayCollections(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Collection) {
            return ((Collection<?>) value).size();
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return 0;
        }
        int total = 0;
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            total += countArrayCollections(Array.get(value, i));
        }
        return total;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static String mib(long bytes) {
        return String.format(Locale.ROOT, "%.1f", bytes / 1048576.0D);
    }

    private static boolean enabled() {
        return GpomEarlyConfig.worldLifecycleProfilerEnabled();
    }

    private static final class TextureBaseline {
        private final Set<String> keys;

        private TextureBaseline(Set<String> keys) {
            this.keys = new HashSet<>(keys);
        }
    }

    private static final class TextureProfile {
        private int total = -1;
        private int tickable = -1;
        private int baselineTotal = -1;
        private int addedSinceBaseline;
        private int removedSinceBaseline;
        private final Map<String, Integer> domainCounts = new HashMap<>();
        private final Map<String, Integer> classCounts = new HashMap<>();
        private final Map<String, Integer> addedDomainCounts = new HashMap<>();
        private final Map<String, Integer> addedClassCounts = new HashMap<>();
        private final List<String> addedSamples = new ArrayList<>();
    }

    private static final class RenderProfile {
        private String viewFrustumClass = "null";
        private int viewFrustumRenderChunks = -1;
        private String dispatcherClass = "null";
        private String renderContainerClass = "null";
        private Map<String, Integer> renderGlobalFieldSizes = Collections.emptyMap();
        private Map<String, Integer> viewFrustumFieldSizes = Collections.emptyMap();
        private Map<String, Integer> dispatcherFieldSizes = Collections.emptyMap();
    }

    private static final class ResourceProfile {
        private String managerClass = "null";
        private int domainManagers = -1;
        private int reloadListeners = -1;
        private final Map<String, Integer> domainCounts = new HashMap<>();
        private final Map<String, Integer> reloadListenerClasses = new HashMap<>();
    }

    private static final class LoliAsmStatefulProfile {
        private int entries;
        private int live;
        private int cleared;
        private final Map<String, Integer> classCounts = new HashMap<>();
    }

    private static final class ObjectGraphProfile {
        private final String rootName;
        private int visited;
        private int byteBuffers;
        private int buffers;
        private long bufferCapacityBytes;
        private boolean truncated;
        private final Map<String, Integer> classCounts = new HashMap<>();
        private final Map<String, Integer> interestingClassCounts = new HashMap<>();
        private final Map<String, Integer> bufferClassCounts = new HashMap<>();

        private ObjectGraphProfile(String rootName) {
            this.rootName = rootName;
        }
    }

    private static final class GraphNode {
        private final Object value;
        private final int depth;

        private GraphNode(Object value, int depth) {
            this.value = value;
            this.depth = depth;
        }
    }

    private static final class ActiveTransition {
        private final int id;
        private final String kind;
        private final String previousWorld;
        private final String nextWorld;
        private final String message;
        private final long startedAtNanos;

        private ActiveTransition(int id, String kind, Object previousWorld, Object nextWorld, String message) {
            this(id, kind, worldLabel(previousWorld), worldLabel(nextWorld), message, System.nanoTime());
        }

        private ActiveTransition(int id, String kind, String previousWorld, String nextWorld, String message, long startedAtNanos) {
            this.id = id;
            this.kind = kind;
            this.previousWorld = previousWorld;
            this.nextWorld = nextWorld;
            this.message = message;
            this.startedAtNanos = startedAtNanos;
        }

        private ActiveTransition copy() {
            return new ActiveTransition(id, kind, previousWorld, nextWorld, message, startedAtNanos);
        }

        private long elapsedMillis() {
            return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
        }
    }

    private static final class DelayedSnapshot {
        private final ActiveTransition transition;
        private final long delayMillis;
        private final long dueAtNanos;

        private DelayedSnapshot(ActiveTransition transition, long delayMillis, long dueAtNanos) {
            this.transition = transition;
            this.delayMillis = delayMillis;
            this.dueAtNanos = dueAtNanos;
        }
    }

    private static final class TrackedWorld {
        private final WeakReference<Object> reference;
        private final String label;
        private final int transitionId;
        private final String reason;
        private final long createdAtNanos;

        private TrackedWorld(WeakReference<Object> reference, String label, int transitionId, String reason, long createdAtNanos) {
            this.reference = reference;
            this.label = label;
            this.transitionId = transitionId;
            this.reason = reason;
            this.createdAtNanos = createdAtNanos;
        }
    }

    private static final class RetainedWorldProfile {
        private int totalRefs;
        private int clearedRefs;
        private int liveOldWorlds;
        private final List<String> liveLabels = new ArrayList<>();
    }

    private static final class Snapshot {
        private final String point;
        private long heapUsedBytes;
        private long heapCommittedBytes;
        private long runtimeUsedBytes;
        private long runtimeCommittedBytes;
        private long nonHeapUsedBytes;
        private long directBufferBytes;
        private long mappedBufferBytes;
        private String previousWorld;
        private String nextWorld;
        private int worldEntities;
        private int worldTiles;
        private int worldWeather;
        private int worldPlayers;
        private int renderChunksToUpdate;
        private int renderInfos;
        private int tileEntitiesToRender;
        private int damagedBlocks;
        private int soundPositions;
        private int textureObjects;
        private int tickableTextures;
        private int particleEmitters;
        private int particleLayerEntries;
        private int playingSounds;
        private int delayedSounds;
        private int retainedTrackedWorldRefs;
        private int retainedClearedWorldRefs;
        private int retainedOldWorlds;
        private List<String> retainedOldWorldLabels;
        private boolean playerPresent;
        private String screenName = "null";
        private TextureProfile textureProfile;
        private RenderProfile renderProfile;
        private ResourceProfile resourceProfile;
        private LoliAsmStatefulProfile loliAsmStatefulProfile;
        private ObjectGraphProfile renderGraphProfile;
        private ObjectGraphProfile textureGraphProfile;
        private ObjectGraphProfile effectGraphProfile;
        private ObjectGraphProfile soundGraphProfile;
        private String deepFailure;

        private Snapshot(String point) {
            this.point = point;
        }

        private Snapshot withClientState(Object player, Object screen) {
            playerPresent = player != null;
            screenName = screen == null ? "null" : screen.getClass().getName();
            return this;
        }
    }
}
