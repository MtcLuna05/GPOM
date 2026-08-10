package com.l.gpom.compat.hei;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Time-slices Woot's own Tartarus learner on the integrated-server thread. No entity/world
 * mutation is performed by worker threads; "background" means bounded work across normal ticks.
 */
final class WootUnsupportedLootLearner {
    private static final WootUnsupportedLootLearner INSTANCE = new WootUnsupportedLootLearner();

    private static boolean registered;
    private static volatile Map<String, Set<String>> configuredMobs = Collections.emptyMap();

    private final List<Lane> lanes = new ArrayList<>();
    private List<Task> tasks = Collections.emptyList();
    private MinecraftServer activeServer;
    private Bridge bridge;
    private int taskCursor;
    private long samples;
    private boolean finished;
    private boolean failed;

    private WootUnsupportedLootLearner() {
    }

    static synchronized void configure(Map<String, Set<String>> unsupportedFunctionsByMob) {
        if (!GpomEarlyConfig.heiWootUnsupportedFunctionLearningEnabled()) {
            WootJeiDiagnostics.log("Unsupported-function runtime learning disabled by config; candidates={}",
                    unsupportedFunctionsByMob.size());
            return;
        }
        if (unsupportedFunctionsByMob.isEmpty()) {
            WootJeiDiagnostics.log("Unsupported-function runtime learning has no candidate mobs");
            return;
        }
        configuredMobs = immutableCopy(unsupportedFunctionsByMob);
        INSTANCE.reset(null);
        if (!registered) {
            registered = true;
            FMLCommonHandler.instance().bus().register(INSTANCE);
        }
        WootJeiDiagnostics.log("Configured background Woot learning for {} unsupported-function mob(s), boxes={}",
                configuredMobs.size(), GpomEarlyConfig.heiWootUnsupportedFunctionLearningParallelBoxes());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || configuredMobs.isEmpty() || failed || finished) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        if (activeServer != server) {
            reset(server);
        }
        WorldServer overworld = MinecraftMappingCompat.minecraftServerWorld(server, 0);
        if (overworld == null) {
            return;
        }
        try {
            ensureStarted();
            tickLanes(overworld);
        } catch (Throwable throwable) {
            failed = true;
            releaseLanes();
            WootJeiDiagnostics.error("Background Woot learning failed and was disabled for this world", throwable);
        }
    }

    private void ensureStarted() throws ReflectiveOperationException {
        if (bridge != null) {
            return;
        }
        bridge = Bridge.create();
        List<String> mobNames = new ArrayList<>(configuredMobs.keySet());
        Collections.sort(mobNames);
        List<Task> pending = new ArrayList<>();
        for (String mobName : mobNames) {
            Object wootMobName = bridge.mobNameConstructor.newInstance(mobName);
            for (Object enchantment : bridge.enchantments) {
                pending.add(new Task(mobName, wootMobName, enchantment));
            }
        }
        tasks = Collections.unmodifiableList(pending);
        int laneCount = GpomEarlyConfig.heiWootUnsupportedFunctionLearningParallelBoxes();
        for (int laneIndex = 0; laneIndex < laneCount; laneIndex++) {
            int spawnId = ((Number) bridge.allocateSpawnBox.invoke(bridge.manager)).intValue();
            if (spawnId < 0) {
                break;
            }
            lanes.add(new Lane(spawnId));
        }
        if (lanes.isEmpty()) {
            throw new IllegalStateException("Woot Tartarus has no free spawn boxes");
        }
        WootJeiDiagnostics.log("Background Woot learning started: mobs={}, tasks={}, boxes={}",
                configuredMobs.size(), tasks.size(), lanes.size());
    }

    private void tickLanes(World world) throws ReflectiveOperationException {
        for (Lane lane : lanes) {
            collect(lane, world);
            Task task = nextTask();
            if (task == null) {
                continue;
            }
            bridge.spawnInBox.invoke(bridge.manager, world, lane.spawnId, task.mobName, task.enchantment);
            lane.pending = task;
        }
        if (nextTask() == null && allLanesIdle()) {
            finish();
        }
    }

    private void collect(Lane lane, World world) throws ReflectiveOperationException {
        if (lane.pending == null) {
            return;
        }
        Object value = bridge.getLootInBox.invoke(bridge.manager, world, lane.spawnId);
        List<?> drops = value instanceof List ? (List<?>) value : Collections.emptyList();
        bridge.learn.invoke(bridge.repository, lane.pending.mobName, lane.pending.enchantment, drops, true);
        for (Object drop : drops) {
            if (drop instanceof Entity) {
                MinecraftMappingCompat.entitySetDead((Entity) drop);
            }
        }
        samples++;
        if (samples % 1000L == 0L) {
            WootJeiDiagnostics.log("Background Woot learning progress: samples={}, remainingTasks={}",
                    samples, Math.max(0, tasks.size() - taskCursor));
        }
        lane.pending = null;
    }

    private Task nextTask() throws ReflectiveOperationException {
        while (taskCursor < tasks.size()) {
            Task task = tasks.get(taskCursor);
            boolean full = Boolean.TRUE.equals(bridge.isFull.invoke(
                    bridge.repository, task.mobName, task.enchantment));
            if (!full) {
                return task;
            }
            WootJeiDiagnostics.log("Background Woot learning task complete: mob={}, looting={}",
                    task.registryName, task.enchantment);
            taskCursor++;
            bridge.writeToJsonFile.invoke(bridge.repository, bridge.lootFile);
        }
        return null;
    }

    private boolean allLanesIdle() {
        for (Lane lane : lanes) {
            if (lane.pending != null) {
                return false;
            }
        }
        return true;
    }

    private void finish() throws ReflectiveOperationException {
        bridge.writeToJsonFile.invoke(bridge.repository, bridge.lootFile);
        releaseLanes();
        finished = true;
        WootJeiDiagnostics.log("Background Woot learning finished: mobs={}, samples={}, saved={}",
                configuredMobs.size(), samples, bridge.lootFile);
    }

    private void releaseLanes() {
        if (bridge != null) {
            for (Lane lane : lanes) {
                try {
                    bridge.freeSpawnBox.invoke(bridge.manager, lane.spawnId);
                } catch (Throwable ignored) {
                }
            }
        }
        lanes.clear();
    }

    private void reset(MinecraftServer server) {
        releaseLanes();
        activeServer = server;
        bridge = null;
        tasks = Collections.emptyList();
        taskCursor = 0;
        samples = 0L;
        finished = false;
        failed = false;
    }

    private static Map<String, Set<String>> immutableCopy(Map<String, Set<String>> source) {
        List<Map.Entry<String, Set<String>>> entries = new ArrayList<>(source.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        Map<String, Set<String>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : entries) {
            copied.put(entry.getKey(), Collections.unmodifiableSet(new java.util.LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copied);
    }

    private static final class Lane {
        final int spawnId;
        Task pending;

        private Lane(int spawnId) {
            this.spawnId = spawnId;
        }
    }

    private static final class Task {
        final String registryName;
        final Object mobName;
        final Object enchantment;

        private Task(String registryName, Object mobName, Object enchantment) {
            this.registryName = registryName;
            this.mobName = mobName;
            this.enchantment = enchantment;
        }
    }

    private static final class Bridge {
        final Object manager;
        final Object repository;
        final Constructor<?> mobNameConstructor;
        final Object[] enchantments;
        final Method allocateSpawnBox;
        final Method freeSpawnBox;
        final Method spawnInBox;
        final Method getLootInBox;
        final Method learn;
        final Method isFull;
        final Method writeToJsonFile;
        final File lootFile;

        private Bridge(Object manager,
                       Object repository,
                       Constructor<?> mobNameConstructor,
                       Object[] enchantments,
                       Method allocateSpawnBox,
                       Method freeSpawnBox,
                       Method spawnInBox,
                       Method getLootInBox,
                       Method learn,
                       Method isFull,
                       Method writeToJsonFile,
                       File lootFile) {
            this.manager = manager;
            this.repository = repository;
            this.mobNameConstructor = mobNameConstructor;
            this.enchantments = enchantments;
            this.allocateSpawnBox = allocateSpawnBox;
            this.freeSpawnBox = freeSpawnBox;
            this.spawnInBox = spawnInBox;
            this.getLootInBox = getLootInBox;
            this.learn = learn;
            this.isFull = isFull;
            this.writeToJsonFile = writeToJsonFile;
            this.lootFile = lootFile;
        }

        static Bridge create() throws ReflectiveOperationException {
            ClassLoader loader = WootUnsupportedLootLearner.class.getClassLoader();
            Class<?> woot = Class.forName("ipsis.Woot", true, loader);
            Class<?> mobName = Class.forName("ipsis.woot.util.WootMobName", true, loader);
            Class<?> enchantment = Class.forName("ipsis.woot.util.EnumEnchantKey", true, loader);
            Class<?> managerType = Class.forName("ipsis.woot.loot.schools.TartarusManager", true, loader);
            Class<?> repositoryType = Class.forName("ipsis.woot.loot.repository.LootRepository", true, loader);

            Object manager = publicStaticField(woot, "tartarusManager").get(null);
            Object repository = publicStaticField(woot, "lootRepository").get(null);
            if (manager == null || repository == null) {
                throw new IllegalStateException("Woot server learning services are not initialized");
            }

            Class<?> files = Class.forName("ipsis.woot.reference.Files", true, loader);
            File lootFile = (File) publicStaticField(files, "lootFile").get(null);
            if (lootFile == null) {
                throw new IllegalStateException("Woot loot.json path is unavailable");
            }

            return new Bridge(
                    manager,
                    repository,
                    mobName.getConstructor(String.class),
                    enchantment.getEnumConstants(),
                    managerType.getMethod("allocateSpawnBoxId"),
                    managerType.getMethod("freeSpawnBoxId", int.class),
                    managerType.getMethod("spawnInBox", World.class, int.class, mobName, enchantment),
                    managerType.getMethod("getLootInBox", World.class, int.class),
                    repositoryType.getMethod("learn", mobName, enchantment, List.class, boolean.class),
                    repositoryType.getMethod("isFull", mobName, enchantment),
                    repositoryType.getMethod("writeToJsonFile", File.class),
                    lootFile);
        }

        private static Field publicStaticField(Class<?> owner, String name) throws NoSuchFieldException {
            Field field = owner.getField(name);
            field.setAccessible(true);
            return field;
        }
    }
}
