package com.l.gpom.compat.betterportals;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BetterPortalsAetherPortalBreakCleanup {
    private static final String AETHER_PORTAL_SIZE = "com.gildedgames.the_aether.blocks.portal.AetherPortalSize";
    private static final String AETHER_BLOCKS = "com.gildedgames.the_aether.blocks.BlocksAether";
    private static final String BETTER_PORTALS_AETHER_ENTITY =
            "de.johni0702.minecraft.betterportals.impl.aether.common.entity.AetherPortalEntity";
    private static final int NEARBY_PORTAL_SCAN_RADIUS = 8;
    private static final int PORTAL_CLUSTER_SCAN_LIMIT = 512;
    private static final ThreadLocal<Boolean> CLEANING = new ThreadLocal<>();

    private BetterPortalsAetherPortalBreakCleanup() {
    }

    public static void onBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState) {
        if (world == null || pos == null || isRemoteWorld(world) || Boolean.TRUE.equals(CLEANING.get())) {
            return;
        }
        if (!isRelevantAetherPortalUpdate(oldState, newState)) {
            return;
        }

        try {
            Block portalBlock = currentAetherPortalBlock();
            if (portalBlock == null) {
                return;
            }

            Set<BlockPos> nearbyPortalBlocks = findNearbyPortalBlocks(world, pos, portalBlock);
            int removedEntities = removeInvalidPortalEntities(world, pos, nearbyPortalBlocks);
            int removedBlocks = removeInvalidPortalBlockClusters(world, nearbyPortalBlocks, portalBlock);
            if (removedEntities > 0 || removedBlocks > 0 || !nearbyPortalBlocks.isEmpty()) {
                log("block-update cleanup"
                        + " pos=" + pos
                        + " old=" + registryName(oldState)
                        + " new=" + registryName(newState)
                        + " nearbyPortalBlocks=" + nearbyPortalBlocks.size()
                        + " removedEntities=" + removedEntities
                        + " removedBlocks=" + removedBlocks);
            }
        } catch (Throwable throwable) {
            log("block-update cleanup failed: " + throwable.getClass().getName()
                    + (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
        }
    }

    private static boolean isRelevantAetherPortalUpdate(IBlockState oldState, IBlockState newState) {
        boolean oldGlowstone = isGlowstone(oldState);
        boolean newGlowstone = isGlowstone(newState);
        boolean oldPortal = isAetherPortalLike(oldState);
        boolean newPortal = isAetherPortalLike(newState);
        return (oldGlowstone && !newGlowstone) || (oldPortal && !newPortal);
    }

    private static boolean isGlowstone(IBlockState state) {
        try {
            return state != null && blockFromState(state) == glowstoneBlock();
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean isAetherPortalLike(IBlockState state) {
        if (state == null) {
            return false;
        }
        try {
            Block block = blockFromState(state);
            String className = block.getClass().getName();
            if (className.contains("aether") && className.contains("Portal")) {
                return true;
            }
            String registryName = registryName(block);
            return registryName.endsWith(":aether_portal") || "aether_portal".equals(registryName);
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Set<BlockPos> findNearbyPortalBlocks(World world, BlockPos center, Block portalBlock)
            throws ReflectiveOperationException {
        Set<BlockPos> result = new HashSet<>();
        for (int dx = -NEARBY_PORTAL_SCAN_RADIUS; dx <= NEARBY_PORTAL_SCAN_RADIUS; dx++) {
            for (int dy = -NEARBY_PORTAL_SCAN_RADIUS; dy <= NEARBY_PORTAL_SCAN_RADIUS; dy++) {
                for (int dz = -NEARBY_PORTAL_SCAN_RADIUS; dz <= NEARBY_PORTAL_SCAN_RADIUS; dz++) {
                    BlockPos scanPos = offset(center, dx, dy, dz);
                    if (isPortalState(blockState(world, scanPos), portalBlock)) {
                        result.add(scanPos);
                    }
                }
            }
        }
        return result;
    }

    private static int removeInvalidPortalEntities(World world, BlockPos changedPos, Set<BlockPos> nearbyPortalBlocks)
            throws ReflectiveOperationException {
        int removed = 0;
        List<?> entities = new ArrayList<>(loadedEntities(world));
        for (Object value : entities) {
            if (!(value instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) value;
            if (!isBetterPortalsAetherEntity(entity)) {
                continue;
            }

            Set<BlockPos> localBlocks = portalLocalBlocks(entity);
            if (localBlocks.isEmpty() || !isRelatedToUpdate(localBlocks, changedPos, nearbyPortalBlocks)) {
                continue;
            }
            if (isAnyPortalShapeValid(world, localBlocks)) {
                continue;
            }

            CLEANING.set(Boolean.TRUE);
            try {
                setDead(entity);
            } finally {
                CLEANING.remove();
            }
            removed++;
        }
        return removed;
    }

    private static int removeInvalidPortalBlockClusters(World world, Set<BlockPos> nearbyPortalBlocks, Block portalBlock)
            throws ReflectiveOperationException {
        int removed = 0;
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos portalPos : nearbyPortalBlocks) {
            if (visited.contains(portalPos) || !isPortalState(blockState(world, portalPos), portalBlock)) {
                continue;
            }

            Set<BlockPos> cluster = collectPortalCluster(world, portalPos, portalBlock);
            visited.addAll(cluster);
            if (cluster.isEmpty() || isAnyPortalShapeValid(world, cluster)) {
                continue;
            }

            CLEANING.set(Boolean.TRUE);
            try {
                for (BlockPos stalePortalPos : cluster) {
                    if (isPortalState(blockState(world, stalePortalPos), portalBlock)) {
                        destroyBlock(world, stalePortalPos);
                        removed++;
                    }
                }
            } finally {
                CLEANING.remove();
            }
        }
        return removed;
    }

    private static Set<BlockPos> collectPortalCluster(World world, BlockPos startPos, Block portalBlock)
            throws ReflectiveOperationException {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        visited.add(startPos);
        queue.add(startPos);
        while (!queue.isEmpty() && visited.size() < PORTAL_CLUSTER_SCAN_LIMIT) {
            BlockPos current = queue.removeFirst();
            enqueuePortalNeighbor(world, portalBlock, offset(current, 1, 0, 0), visited, queue);
            enqueuePortalNeighbor(world, portalBlock, offset(current, -1, 0, 0), visited, queue);
            enqueuePortalNeighbor(world, portalBlock, offset(current, 0, 1, 0), visited, queue);
            enqueuePortalNeighbor(world, portalBlock, offset(current, 0, -1, 0), visited, queue);
            enqueuePortalNeighbor(world, portalBlock, offset(current, 0, 0, 1), visited, queue);
            enqueuePortalNeighbor(world, portalBlock, offset(current, 0, 0, -1), visited, queue);
        }
        return visited;
    }

    private static void enqueuePortalNeighbor(
            World world,
            Block portalBlock,
            BlockPos pos,
            Set<BlockPos> visited,
            Deque<BlockPos> queue
    ) throws ReflectiveOperationException {
        if (visited.size() >= PORTAL_CLUSTER_SCAN_LIMIT || visited.contains(pos)) {
            return;
        }
        if (!isPortalState(blockState(world, pos), portalBlock)) {
            return;
        }
        visited.add(pos);
        queue.addLast(pos);
    }

    private static boolean isPortalState(IBlockState state, Block portalBlock) throws ReflectiveOperationException {
        return state != null && blockFromState(state) == portalBlock;
    }

    private static boolean isRelatedToUpdate(Set<BlockPos> localBlocks, BlockPos changedPos, Set<BlockPos> nearbyPortalBlocks)
            throws ReflectiveOperationException {
        for (BlockPos portalPos : localBlocks) {
            if (nearbyPortalBlocks.contains(portalPos) || distanceSq(portalPos, changedPos) <= 256.0D) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnyPortalShapeValid(World world, Set<BlockPos> portalBlocks)
            throws ReflectiveOperationException {
        for (BlockPos portalPos : portalBlocks) {
            if (isPortalShapeValid(world, portalPos, EnumFacing.Axis.X)
                    || isPortalShapeValid(world, portalPos, EnumFacing.Axis.Z)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPortalShapeValid(World world, BlockPos pos, EnumFacing.Axis axis)
            throws ReflectiveOperationException {
        Class<?> portalSizeClass = Class.forName(AETHER_PORTAL_SIZE, false,
                BetterPortalsAetherPortalBreakCleanup.class.getClassLoader());
        Constructor<?> constructor = portalSizeClass.getConstructor(World.class, BlockPos.class, EnumFacing.Axis.class);
        Object portalSize = constructor.newInstance(world, pos, axis);
        Method isValid = portalSizeClass.getMethod("isValid");
        Object valid = isValid.invoke(portalSize);
        if (!(valid instanceof Boolean) || !(Boolean) valid) {
            return false;
        }
        int width = intField(portalSize, "width");
        int height = intField(portalSize, "height");
        int portalBlockCount = intField(portalSize, "portalBlockCount");
        return width > 0 && height > 0 && portalBlockCount >= width * height;
    }

    private static Set<BlockPos> portalLocalBlocks(Entity entity) throws ReflectiveOperationException {
        Object portal = invoke(entity, "getPortal");
        Object blocks = invoke(portal, "getLocalBlocks");
        Set<BlockPos> result = new HashSet<>();
        if (!(blocks instanceof Iterable<?>)) {
            return result;
        }
        for (Object block : (Iterable<?>) blocks) {
            if (block instanceof BlockPos) {
                result.add((BlockPos) block);
            }
        }
        return result;
    }

    private static boolean isBetterPortalsAetherEntity(Entity entity) {
        return entity != null && BETTER_PORTALS_AETHER_ENTITY.equals(entity.getClass().getName());
    }

    private static Block currentAetherPortalBlock() throws ReflectiveOperationException {
        Class<?> blocksClass = Class.forName(AETHER_BLOCKS, false,
                BetterPortalsAetherPortalBreakCleanup.class.getClassLoader());
        for (String name : new String[]{"aether_portal", "aetherPortal", "AETHER_PORTAL"}) {
            Field field = findField(blocksClass, name);
            if (field == null) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Block) {
                return (Block) value;
            }
        }
        return null;
    }

    private static boolean isRemoteWorld(World world) {
        return booleanField(world, "field_72995_K", "isRemote");
    }

    private static Block blockFromState(IBlockState state) throws ReflectiveOperationException {
        Object block = invoke(state, new String[]{"func_177230_c", "getBlock"});
        if (block instanceof Block) {
            return (Block) block;
        }
        throw new NoSuchMethodException(state.getClass().getName() + ".getBlock");
    }

    private static Block glowstoneBlock() throws ReflectiveOperationException {
        Object block = staticField(Blocks.class, "field_150426_aN", "GLOWSTONE");
        if (block instanceof Block) {
            return (Block) block;
        }
        throw new NoSuchFieldException(Blocks.class.getName() + ".GLOWSTONE");
    }

    private static BlockPos offset(BlockPos pos, int dx, int dy, int dz) throws ReflectiveOperationException {
        Object result = invoke(pos, new String[]{"func_177982_a", "add"}, dx, dy, dz);
        if (result instanceof BlockPos) {
            return (BlockPos) result;
        }
        throw new NoSuchMethodException(pos.getClass().getName() + ".add");
    }

    private static IBlockState blockState(World world, BlockPos pos) throws ReflectiveOperationException {
        Object result = invoke(world, new String[]{"func_180495_p", "getBlockState"}, pos);
        if (result instanceof IBlockState) {
            return (IBlockState) result;
        }
        throw new NoSuchMethodException(world.getClass().getName() + ".getBlockState");
    }

    private static List<?> loadedEntities(World world) throws ReflectiveOperationException {
        Object value = fieldValue(world, "field_72996_f", "loadedEntityList");
        if (value instanceof List<?>) {
            return (List<?>) value;
        }
        if (value instanceof Iterable<?>) {
            List<Object> result = new ArrayList<>();
            for (Object entry : (Iterable<?>) value) {
                result.add(entry);
            }
            return result;
        }
        throw new NoSuchFieldException(world.getClass().getName() + ".loadedEntityList");
    }

    private static void setDead(Entity entity) throws ReflectiveOperationException {
        invoke(entity, new String[]{"func_70106_y", "setDead"});
    }

    private static void destroyBlock(World world, BlockPos pos) throws ReflectiveOperationException {
        invoke(world, new String[]{"func_175698_g", "setBlockToAir"}, pos);
    }

    private static double distanceSq(BlockPos first, BlockPos second) throws ReflectiveOperationException {
        int dx = intValue(invoke(first, new String[]{"func_177958_n", "getX"}))
                - intValue(invoke(second, new String[]{"func_177958_n", "getX"}));
        int dy = intValue(invoke(first, new String[]{"func_177956_o", "getY"}))
                - intValue(invoke(second, new String[]{"func_177956_o", "getY"}));
        int dz = intValue(invoke(first, new String[]{"func_177952_p", "getZ"}))
                - intValue(invoke(second, new String[]{"func_177952_p", "getZ"}));
        return dx * dx + dy * dy + dz * dz;
    }

    private static int intField(Object target, String name) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(target.getClass().getName() + "." + name);
        }
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static Object invoke(Object target, String name) throws ReflectiveOperationException {
        return invoke(target, new String[]{name});
    }

    private static Object invoke(Object target, String[] names, Object... args) throws ReflectiveOperationException {
        for (String name : names) {
            Method method = findMethod(target.getClass(), name, args);
            if (method != null) {
                return method.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + java.util.Arrays.toString(names));
    }

    private static Method findMethod(Class<?> owner, String name, Object... args) {
        for (Method method : owner.getMethods()) {
            if (name.equals(method.getName()) && accepts(method.getParameterTypes(), args)) {
                method.setAccessible(true);
                return method;
            }
        }
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (name.equals(method.getName()) && accepts(method.getParameterTypes(), args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
        }
        return null;
    }

    private static boolean accepts(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int index = 0; index < parameterTypes.length; index++) {
            Object arg = args[index];
            if (arg != null && !wrap(parameterTypes[index]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return Void.class;
    }

    private static Object fieldValue(Object target, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            Field field = findField(target.getClass(), name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }
        }
        throw new NoSuchFieldException(target.getClass().getName() + "." + java.util.Arrays.toString(names));
    }

    private static Object staticField(Class<?> owner, String... names) throws ReflectiveOperationException {
        for (String name : names) {
            Field field = findField(owner, name);
            if (field != null) {
                field.setAccessible(true);
                return field.get(null);
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + java.util.Arrays.toString(names));
    }

    private static boolean booleanField(Object target, String... names) {
        try {
            Object value = fieldValue(target, names);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static int intValue(Object value) throws ReflectiveOperationException {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new ReflectiveOperationException("Expected int value, got " + value);
    }

    private static Field findField(Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static String registryName(IBlockState state) {
        if (state == null) {
            return "null";
        }
        try {
            return registryName(blockFromState(state));
        } catch (ReflectiveOperationException ignored) {
            return state.getClass().getName();
        }
    }

    private static String registryName(Block block) {
        if (block == null) {
            return "null";
        }
        try {
            Object registryName = invoke(block, "getRegistryName");
            return registryName == null ? block.getClass().getName() : String.valueOf(registryName);
        } catch (ReflectiveOperationException ignored) {
            return block.getClass().getName();
        }
    }

    private static void log(String message) {
        try {
            System.out.println("[GPOM BetterPortals Aether Break] " + message);
        } catch (Throwable ignored) {
        }
    }
}
