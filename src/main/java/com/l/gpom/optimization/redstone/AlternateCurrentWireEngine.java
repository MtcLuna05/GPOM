package com.l.gpom.optimization.redstone;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.AlternateCurrentCompat;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.lang.ref.WeakReference;

/**
 * A Forge 1.12-specific, independently integrated network solver inspired by
 * Space Walker's MIT-licensed Alternate Current algorithm.
 *
 * It discovers each connected vanilla wire network, samples non-wire input once
 * per wire, propagates the final signal through the graph, writes each changed
 * wire once, and only then dispatches boundary block updates.
 */
public final class AlternateCurrentWireEngine {
    private static final int[][] HORIZONTAL = {
            {-1, 0, 0},
            {0, 0, -1},
            {1, 0, 0},
            {0, 0, 1}
    };
    private static final int[][] ALL_DIRECTIONS = {
            {-1, 0, 0},
            {0, 0, -1},
            {1, 0, 0},
            {0, 0, 1},
            {0, -1, 0},
            {0, 1, 0}
    };
    private static final Map<WorldServer, Engine> ENGINES =
            Collections.synchronizedMap(new WeakHashMap<WorldServer, Engine>());

    private AlternateCurrentWireEngine() {
    }

    public static boolean shouldReplaceVanilla(World world) {
        return world instanceof WorldServer
                && !MinecraftMappingCompat.worldIsRemote(world)
                && AlternateCurrentCompat.isAvailable();
    }

    public static void onWireAdded(World world, BlockPos position) {
        if (shouldReplaceVanilla(world)) {
            engine((WorldServer) world).enqueue(position, false);
        }
    }

    public static void onWireRemoved(World world, BlockPos position) {
        if (shouldReplaceVanilla(world)) {
            engine((WorldServer) world).enqueue(position, true);
        }
    }

    public static boolean onWireNeighborChanged(World world, BlockPos position) {
        if (!shouldReplaceVanilla(world)) {
            return false;
        }
        IBlockState state = AlternateCurrentCompat.blockState(world, position);
        if (!AlternateCurrentCompat.isAvailable() || !AlternateCurrentCompat.isWire(state)) {
            return false;
        }
        Boolean canStay = AlternateCurrentCompat.canWireStay(world, position);
        if (canStay == null) {
            return false;
        }
        if (!canStay) {
            if (!AlternateCurrentCompat.dropWire(world, position, state)
                    || !AlternateCurrentCompat.replaceWithAir(world, position)) {
                return false;
            }
            return engine((WorldServer) world).enqueue(position, true);
        }
        return engine((WorldServer) world).enqueue(position, false);
    }

    private static Engine engine(WorldServer world) {
        synchronized (ENGINES) {
            Engine engine = ENGINES.get(world);
            if (engine == null) {
                engine = new Engine(world);
                ENGINES.put(world, engine);
            }
            return engine;
        }
    }

    private static final class Engine {
        private static final byte PRESENT = 1;
        private static final byte REMOVED = 2;

        // The WeakHashMap key must not be retained by its value, otherwise
        // leaving an integrated-server world permanently retains that world.
        private final WeakReference<WorldServer> world;
        private final ArrayDeque<Long> requests = new ArrayDeque<>();
        private final Long2ByteOpenHashMap requestTypes = new Long2ByteOpenHashMap();
        private final Long2ObjectOpenHashMap<WireNode> network = new Long2ObjectOpenHashMap<>();
        private final ArrayDeque<WireNode> discovery = new ArrayDeque<>();
        private final Long2ObjectOpenHashMap<BoundaryUpdate> boundary = new Long2ObjectOpenHashMap<>();
        private final ArrayDeque<WireNode>[] powerQueues;

        private boolean processing;
        private long networks;
        private long wires;
        private long changes;
        private long totalNanos;
        private long nextReportNanos;

        @SuppressWarnings("unchecked")
        private Engine(WorldServer world) {
            this.world = new WeakReference<>(world);
            this.powerQueues = new ArrayDeque[16];
            for (int power = 0; power < powerQueues.length; power++) {
                powerQueues[power] = new ArrayDeque<>();
            }
            nextReportNanos = System.nanoTime()
                    + GpomEarlyConfig.redstoneProfilerIntervalSeconds() * 1_000_000_000L;
        }

        private boolean enqueue(BlockPos position, boolean removed) {
            if (position == null || !AlternateCurrentCompat.isAvailable()) {
                return false;
            }
            long key = key(AlternateCurrentCompat.x(position), AlternateCurrentCompat.y(position),
                    AlternateCurrentCompat.z(position));
            if (!AlternateCurrentCompat.isAvailable()) {
                return false;
            }
            byte type = removed ? REMOVED : PRESENT;
            if (requestTypes.containsKey(key)) {
                if (removed) {
                    requestTypes.put(key, REMOVED);
                }
            } else {
                requestTypes.put(key, type);
                requests.addLast(key);
            }
            if (processing) {
                return true;
            }

            processing = true;
            boolean handled = true;
            try {
                while (!requests.isEmpty() && AlternateCurrentCompat.isAvailable()) {
                    long request = requests.removeFirst();
                    byte requestType = requestTypes.remove(request);
                    if (!solve(request, requestType == REMOVED)) {
                        handled = false;
                        requests.clear();
                        requestTypes.clear();
                        break;
                    }
                }
            } catch (Throwable throwable) {
                AlternateCurrentCompat.disableFromEngine("network solve", throwable);
                handled = false;
                requests.clear();
                requestTypes.clear();
            } finally {
                processing = false;
            }
            reportIfDue();
            return handled && AlternateCurrentCompat.isAvailable();
        }

        private boolean solve(long rootKey, boolean removed) {
            long started = System.nanoTime();
            network.clear();
            discovery.clear();
            boundary.clear();
            for (ArrayDeque<WireNode> queue : powerQueues) {
                queue.clear();
            }

            int rootX = unpackX(rootKey);
            int rootY = unpackY(rootKey);
            int rootZ = unpackZ(rootKey);
            IBlockState rootState = state(rootX, rootY, rootZ);
            boolean rootIsWire = AlternateCurrentCompat.isWire(rootState);
            if (!removed && rootIsWire) {
                if (addWire(rootX, rootY, rootZ, rootState) == null) {
                    return AlternateCurrentCompat.isAvailable();
                }
            } else {
                seedAroundRemovedWire(rootX, rootY, rootZ, rootKey);
            }

            int maximum = GpomEarlyConfig.alternateCurrentMaxNetworkSize();
            while (!discovery.isEmpty()) {
                WireNode node = discovery.removeFirst();
                if (network.size() > maximum) {
                    IllegalStateException failure = new IllegalStateException(
                            "wire network exceeded configured maximum " + maximum);
                    AlternateCurrentCompat.disableFromEngine("network size guard", failure);
                    return false;
                }
                discoverConnections(node, rootKey, removed || !rootIsWire);
                if (!AlternateCurrentCompat.isAvailable()) {
                    return false;
                }
            }
            if (network.isEmpty()) {
                return true;
            }

            if (!sampleExternalPower()) {
                return false;
            }
            propagatePower();
            if (!applyFinalPower()) {
                return false;
            }
            dispatchBoundaryUpdates();

            networks++;
            wires += network.size();
            totalNanos += System.nanoTime() - started;
            return AlternateCurrentCompat.isAvailable();
        }

        private void seedAroundRemovedWire(int x, int y, int z, long removedKey) {
            boolean aboveSolid = isNormalCube(x, y + 1, z);
            for (int[] step : HORIZONTAL) {
                int sideX = x + step[0];
                int sideZ = z + step[2];
                IBlockState side = state(sideX, y, sideZ);
                if (AlternateCurrentCompat.isWire(side)) {
                    addWire(sideX, y, sideZ, side);
                    continue;
                }
                boolean sideSolid = AlternateCurrentCompat.isNormalCube(side);
                if (sideSolid && !aboveSolid) {
                    addWireIfPresent(sideX, y + 1, sideZ, removedKey);
                } else if (!sideSolid) {
                    addWireIfPresent(sideX, y - 1, sideZ, removedKey);
                }
            }
        }

        private void discoverConnections(WireNode node, long excludedKey, boolean excludeRoot) {
            boolean aboveSolid = isNormalCube(node.x, node.y + 1, node.z);
            for (int[] step : HORIZONTAL) {
                int sideX = node.x + step[0];
                int sideZ = node.z + step[2];
                IBlockState side = state(sideX, node.y, sideZ);
                if (AlternateCurrentCompat.isWire(side)) {
                    connect(node, sideX, node.y, sideZ, side, excludedKey, excludeRoot);
                    continue;
                }
                boolean sideSolid = AlternateCurrentCompat.isNormalCube(side);
                if (sideSolid && !aboveSolid) {
                    connectIfPresent(node, sideX, node.y + 1, sideZ, excludedKey, excludeRoot);
                } else if (!sideSolid) {
                    connectIfPresent(node, sideX, node.y - 1, sideZ, excludedKey, excludeRoot);
                }
            }
        }

        private void connectIfPresent(WireNode owner, int x, int y, int z,
                                      long excludedKey, boolean excludeRoot) {
            if (y < 0 || y >= 256) {
                return;
            }
            IBlockState candidate = state(x, y, z);
            if (AlternateCurrentCompat.isWire(candidate)) {
                connect(owner, x, y, z, candidate, excludedKey, excludeRoot);
            }
        }

        private void connect(WireNode owner, int x, int y, int z, IBlockState state,
                             long excludedKey, boolean excludeRoot) {
            long candidateKey = key(x, y, z);
            if (excludeRoot && candidateKey == excludedKey) {
                return;
            }
            WireNode candidate = addWire(x, y, z, state);
            if (candidate != null) {
                owner.addNeighbor(candidate);
                candidate.addNeighbor(owner);
            }
        }

        private WireNode addWireIfPresent(int x, int y, int z, long excludedKey) {
            if (y < 0 || y >= 256 || key(x, y, z) == excludedKey) {
                return null;
            }
            IBlockState state = state(x, y, z);
            return AlternateCurrentCompat.isWire(state) ? addWire(x, y, z, state) : null;
        }

        private WireNode addWire(int x, int y, int z, IBlockState state) {
            if (y < 0 || y >= 256 || !AlternateCurrentCompat.isWire(state)) {
                return null;
            }
            long key = key(x, y, z);
            WireNode existing = network.get(key);
            if (existing != null) {
                return existing;
            }
            int power = AlternateCurrentCompat.power(state);
            if (power < 0) {
                return null;
            }
            WireNode node = new WireNode(key, x, y, z, state, power);
            network.put(key, node);
            discovery.addLast(node);
            return node;
        }

        private boolean sampleExternalPower() {
            if (!AlternateCurrentCompat.setWireProvidesPower(false)) {
                return false;
            }
            boolean success = true;
            try {
                for (WireNode node : network.values()) {
                    int external = AlternateCurrentCompat.neighborPower(world(), node.position());
                    if (external < 0) {
                        success = false;
                        break;
                    }
                    node.targetPower = Math.min(15, external);
                    if (node.targetPower > 0) {
                        powerQueues[node.targetPower].addLast(node);
                    }
                }
            } finally {
                if (!AlternateCurrentCompat.setWireProvidesPower(true)) {
                    success = false;
                }
            }
            return success;
        }

        private void propagatePower() {
            for (int power = 15; power > 0; power--) {
                ArrayDeque<WireNode> queue = powerQueues[power];
                while (!queue.isEmpty()) {
                    WireNode node = queue.removeFirst();
                    if (node.targetPower != power) {
                        continue;
                    }
                    int offered = power - 1;
                    for (int index = 0; index < node.neighborCount; index++) {
                        WireNode neighbor = node.neighbors[index];
                        if (offered > neighbor.targetPower) {
                            neighbor.targetPower = offered;
                            if (offered > 0) {
                                powerQueues[offered].addLast(neighbor);
                            }
                        }
                    }
                }
            }
        }

        private boolean applyFinalPower() {
            for (WireNode node : network.values()) {
                if (node.currentPower == node.targetPower) {
                    continue;
                }
                IBlockState current = AlternateCurrentCompat.blockState(world(), node.position());
                if (!AlternateCurrentCompat.isWire(current)) {
                    continue;
                }
                IBlockState updated = AlternateCurrentCompat.withPower(current, node.targetPower);
                if (updated == null || !AlternateCurrentCompat.setBlockState(world(), node.position(), updated, 2)) {
                    return false;
                }
                node.state = updated;
                changes++;
                queueBoundary(node);
            }
            return true;
        }

        private void queueBoundary(WireNode source) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (distance == 0 || distance > 2) {
                            continue;
                        }
                        int x = source.x + dx;
                        int y = source.y + dy;
                        int z = source.z + dz;
                        if (y < 0 || y >= 256) {
                            continue;
                        }
                        long targetKey = key(x, y, z);
                        if (network.containsKey(targetKey) || boundary.containsKey(targetKey)) {
                            continue;
                        }
                        boundary.put(targetKey, new BoundaryUpdate(
                                new BlockPos(x, y, z), source.position()));
                    }
                }
            }
        }

        private void dispatchBoundaryUpdates() {
            for (BoundaryUpdate update : boundary.values()) {
                IBlockState state = AlternateCurrentCompat.blockState(world(), update.position);
                if (AlternateCurrentCompat.block(state) != AlternateCurrentCompat.air()) {
                    AlternateCurrentCompat.neighborChanged(
                            state, world(), update.position, update.sourceWirePosition);
                }
                if (!AlternateCurrentCompat.isAvailable()) {
                    return;
                }
            }
        }

        private IBlockState state(int x, int y, int z) {
            if (y < 0 || y >= 256) {
                return null;
            }
            return AlternateCurrentCompat.blockState(world(), new BlockPos(x, y, z));
        }

        private WorldServer world() {
            return world.get();
        }

        private boolean isNormalCube(int x, int y, int z) {
            return AlternateCurrentCompat.isNormalCube(state(x, y, z));
        }

        private void reportIfDue() {
            long now = System.nanoTime();
            if (now < nextReportNanos) {
                return;
            }
            GPOM.LOGGER.info("[GPOM Alternate Current] networks={} wires={} changes={} totalMillis={} "
                            + "averageMicrosPerNetwork={}",
                    networks, wires, changes, totalNanos / 1_000_000L,
                    networks == 0L ? 0L : totalNanos / networks / 1_000L);
            networks = 0L;
            wires = 0L;
            changes = 0L;
            totalNanos = 0L;
            nextReportNanos = now
                    + GpomEarlyConfig.redstoneProfilerIntervalSeconds() * 1_000_000_000L;
        }
    }

    private static final class WireNode {
        private final long key;
        private final int x;
        private final int y;
        private final int z;
        private final BlockPos position;
        private final int currentPower;
        private final WireNode[] neighbors = new WireNode[4];
        private int neighborCount;
        private int targetPower;
        private IBlockState state;

        private WireNode(long key, int x, int y, int z, IBlockState state, int currentPower) {
            this.key = key;
            this.x = x;
            this.y = y;
            this.z = z;
            this.position = new BlockPos(x, y, z);
            this.state = state;
            this.currentPower = currentPower;
        }

        private BlockPos position() {
            return position;
        }

        private void addNeighbor(WireNode neighbor) {
            if (neighbor == this) {
                return;
            }
            for (int index = 0; index < neighborCount; index++) {
                if (neighbors[index] == neighbor) {
                    return;
                }
            }
            if (neighborCount < neighbors.length) {
                neighbors[neighborCount++] = neighbor;
            }
        }
    }

    private static final class BoundaryUpdate {
        private final BlockPos position;
        private final BlockPos sourceWirePosition;

        private BoundaryUpdate(BlockPos position, BlockPos sourceWirePosition) {
            this.position = position;
            this.sourceWirePosition = sourceWirePosition;
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38
                | ((long) z & 0x3FFFFFFL) << 12
                | (long) y & 0xFFFL;
    }

    private static int unpackX(long key) {
        return (int) (key >> 38);
    }

    private static int unpackY(long key) {
        return (int) (key & 0xFFFL);
    }

    private static int unpackZ(long key) {
        return (int) (key << 26 >> 38);
    }
}
