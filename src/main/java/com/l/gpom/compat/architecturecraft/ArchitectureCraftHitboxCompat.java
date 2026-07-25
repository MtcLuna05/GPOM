package com.l.gpom.compat.architecturecraft;

import com.l.gpom.compat.framed.FramedBlockEffectiveState;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ArchitectureCraftHitboxCompat {
    private static final double EPSILON = 1.0E-7D;
    private static final AxisAlignedBB FULL_BLOCK = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    private static final ConcurrentMap<Class<?>, Method> GLOBAL_COLLISION_BOX_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> SHOULD_SIDE_RENDER_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private ArchitectureCraftHitboxCompat() {
    }

    public static RayTraceResult rayTraceShape(
            Block block,
            IBlockState state,
            World world,
            BlockPos pos,
            Vec3d start,
            Vec3d end
    ) {
        setBoxHit(block, null);
        List<AxisAlignedBB> boxes = globalCollisionBoxes(block, world, pos, state);
        if (boxes == null || boxes.isEmpty()) {
            return null;
        }

        BoxHit best = null;
        int bestIndex = -1;
        AxisAlignedBB bestBox = null;
        for (int index = 0; index < boxes.size(); index++) {
            AxisAlignedBB box = boxes.get(index);
            if (box == null) {
                continue;
            }

            BoxHit hit = intersect(box, pos, start, end);
            if (hit == null || (best != null && hit.distance >= best.distance)) {
                continue;
            }

            best = hit;
            bestIndex = index;
            bestBox = box;
        }

        if (best == null) {
            return null;
        }

        setBoxHit(block, localBox(bestBox, pos));
        setSubHit(best.result, bestIndex);
        return best.result;
    }

    public static AxisAlignedBB boundingBox(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        AxisAlignedBB bounds = collisionBounds(block, state, world, pos);
        return bounds == null ? FULL_BLOCK : bounds;
    }

    public static AxisAlignedBB selectedBoundingBox(Block block, IBlockState state, World world, BlockPos pos) {
        AxisAlignedBB local = boundingBox(block, state, world, pos);
        if (local == null) {
            return local;
        }
        double x = MinecraftMappingCompat.blockPosX(pos);
        double y = MinecraftMappingCompat.blockPosY(pos);
        double z = MinecraftMappingCompat.blockPosZ(pos);
        return new AxisAlignedBB(
                MinecraftMappingCompat.aabbMinX(local) + x,
                MinecraftMappingCompat.aabbMinY(local) + y,
                MinecraftMappingCompat.aabbMinZ(local) + z,
                MinecraftMappingCompat.aabbMaxX(local) + x,
                MinecraftMappingCompat.aabbMaxY(local) + y,
                MinecraftMappingCompat.aabbMaxZ(local) + z
        );
    }

    public static boolean baseShouldSideBeRendered(
            Block block,
            IBlockState state,
            IBlockAccess world,
            BlockPos pos,
            EnumFacing side
    ) {
        IBlockState base = FramedBlockEffectiveState.state(world, pos);
        Block baseBlock = FramedBlockEffectiveState.blockFromState(base);
        if (baseBlock == null) {
            return true;
        }

        try {
            Method method = cachedMethod(
                    SHOULD_SIDE_RENDER_METHODS,
                    baseBlock.getClass(),
                    "shouldSideBeRendered",
                    "func_176225_a",
                    IBlockState.class,
                    IBlockAccess.class,
                    BlockPos.class,
                    EnumFacing.class
            );
            Object value = method.invoke(baseBlock, base, FramedBlockEffectiveState.wrap(world), pos, side);
            return value instanceof Boolean ? (Boolean) value : true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return true;
        }
    }

    private static AxisAlignedBB collisionBounds(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        List<AxisAlignedBB> boxes = globalCollisionBoxes(block, world, pos, state);
        if (boxes == null || boxes.isEmpty()) {
            return FULL_BLOCK;
        }

        AxisAlignedBB union = null;
        for (AxisAlignedBB box : boxes) {
            if (box == null) {
                continue;
            }
            union = union == null ? box : unionBoxes(union, box);
        }
        return union == null ? FULL_BLOCK : localBox(union, pos);
    }

    @SuppressWarnings("unchecked")
    private static List<AxisAlignedBB> globalCollisionBoxes(Block block, IBlockAccess world, BlockPos pos, IBlockState state) {
        try {
            Method method = cachedMethod(
                    GLOBAL_COLLISION_BOX_METHODS,
                    block.getClass(),
                    "getGlobalCollisionBoxes",
                    "getGlobalCollisionBoxes",
                    IBlockAccess.class,
                    BlockPos.class,
                    IBlockState.class,
                    Entity.class
            );
            Object value = method.invoke(block, world, pos, state, null);
            return value instanceof List ? (List<AxisAlignedBB>) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static AxisAlignedBB localBox(AxisAlignedBB worldBox, BlockPos pos) {
        if (worldBox == null) {
            return null;
        }
        double x = MinecraftMappingCompat.blockPosX(pos);
        double y = MinecraftMappingCompat.blockPosY(pos);
        double z = MinecraftMappingCompat.blockPosZ(pos);
        return new AxisAlignedBB(
                MinecraftMappingCompat.aabbMinX(worldBox) - x,
                MinecraftMappingCompat.aabbMinY(worldBox) - y,
                MinecraftMappingCompat.aabbMinZ(worldBox) - z,
                MinecraftMappingCompat.aabbMaxX(worldBox) - x,
                MinecraftMappingCompat.aabbMaxY(worldBox) - y,
                MinecraftMappingCompat.aabbMaxZ(worldBox) - z
        );
    }

    private static AxisAlignedBB unionBoxes(AxisAlignedBB first, AxisAlignedBB second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return new AxisAlignedBB(
                Math.min(MinecraftMappingCompat.aabbMinX(first), MinecraftMappingCompat.aabbMinX(second)),
                Math.min(MinecraftMappingCompat.aabbMinY(first), MinecraftMappingCompat.aabbMinY(second)),
                Math.min(MinecraftMappingCompat.aabbMinZ(first), MinecraftMappingCompat.aabbMinZ(second)),
                Math.max(MinecraftMappingCompat.aabbMaxX(first), MinecraftMappingCompat.aabbMaxX(second)),
                Math.max(MinecraftMappingCompat.aabbMaxY(first), MinecraftMappingCompat.aabbMaxY(second)),
                Math.max(MinecraftMappingCompat.aabbMaxZ(first), MinecraftMappingCompat.aabbMaxZ(second))
        );
    }

    private static void setBoxHit(Block block, AxisAlignedBB box) {
        try {
            cachedField(block.getClass(), "boxHit", "boxHit").set(block, box);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void setSubHit(RayTraceResult result, int subHit) {
        if (result != null) {
            MinecraftMappingCompat.setFieldValue(result, "rayTraceResult.subHit", subHit, "field_72310_e", "subHit");
        }
    }

    private static BoxHit intersect(AxisAlignedBB box, BlockPos pos, Vec3d start, Vec3d end) {
        try {
            Object value = MinecraftMappingCompat.invoke(box, "axisAlignedBB.calculateIntercept",
                    new Class<?>[]{Vec3d.class, Vec3d.class}, new Object[]{start, end},
                    "func_72327_a", "calculateIntercept");
            if (!(value instanceof RayTraceResult)) {
                return null;
            }

            RayTraceResult result = (RayTraceResult) value;
            Vec3d hitVec = MinecraftMappingCompat.rayTraceHitVec(result);
            EnumFacing side = MinecraftMappingCompat.rayTraceSideHit(result);
            if (hitVec == null || side == null) {
                return null;
            }
            double distance = distanceSquared(start, hitVec);
            return new BoxHit(distance, new RayTraceResult(hitVec, side, pos));
        } catch (RuntimeException | LinkageError ignored) {
            return intersectManually(box, pos, start, end);
        }
    }

    private static BoxHit intersectManually(AxisAlignedBB box, BlockPos pos, Vec3d start, Vec3d end) {
        double startX = MinecraftMappingCompat.vecX(start);
        double startY = MinecraftMappingCompat.vecY(start);
        double startZ = MinecraftMappingCompat.vecZ(start);
        double deltaX = MinecraftMappingCompat.vecX(end) - startX;
        double deltaY = MinecraftMappingCompat.vecY(end) - startY;
        double deltaZ = MinecraftMappingCompat.vecZ(end) - startZ;
        double[] interval = new double[] {0.0D, 1.0D};
        EnumFacing[] sides = new EnumFacing[] {null, null};

        if (!clipAxis(startX, deltaX, MinecraftMappingCompat.aabbMinX(box), MinecraftMappingCompat.aabbMaxX(box), EnumFacing.WEST, EnumFacing.EAST, interval, sides)
                || !clipAxis(startY, deltaY, MinecraftMappingCompat.aabbMinY(box), MinecraftMappingCompat.aabbMaxY(box), EnumFacing.DOWN, EnumFacing.UP, interval, sides)
                || !clipAxis(startZ, deltaZ, MinecraftMappingCompat.aabbMinZ(box), MinecraftMappingCompat.aabbMaxZ(box), EnumFacing.NORTH, EnumFacing.SOUTH, interval, sides)) {
            return null;
        }

        double distance = interval[0];
        EnumFacing side = sides[0];
        if (side == null) {
            distance = interval[1];
            side = sides[1];
        }
        if (side == null || distance < 0.0D || distance > 1.0D) {
            return null;
        }

        Vec3d hitVec = new Vec3d(
                startX + deltaX * distance,
                startY + deltaY * distance,
                startZ + deltaZ * distance
        );
        return new BoxHit(distanceSquared(start, hitVec), new RayTraceResult(hitVec, side, pos));
    }

    private static boolean clipAxis(
            double start,
            double delta,
            double min,
            double max,
            EnumFacing minSide,
            EnumFacing maxSide,
            double[] interval,
            EnumFacing[] sides
    ) {
        if (Math.abs(delta) < EPSILON) {
            return start >= min && start <= max;
        }

        double near = (min - start) / delta;
        double far = (max - start) / delta;
        EnumFacing nearSide = minSide;
        EnumFacing farSide = maxSide;
        if (near > far) {
            double swapped = near;
            near = far;
            far = swapped;
            nearSide = maxSide;
            farSide = minSide;
        }

        if (near > interval[0]) {
            interval[0] = near;
            sides[0] = nearSide;
        }
        if (far < interval[1]) {
            interval[1] = far;
            sides[1] = farSide;
        }
        return interval[1] >= interval[0];
    }

    private static double distanceSquared(Vec3d start, Vec3d hit) {
        double deltaX = MinecraftMappingCompat.vecX(hit) - MinecraftMappingCompat.vecX(start);
        double deltaY = MinecraftMappingCompat.vecY(hit) - MinecraftMappingCompat.vecY(start);
        double deltaZ = MinecraftMappingCompat.vecZ(hit) - MinecraftMappingCompat.vecZ(start);
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static Method cachedMethod(
            ConcurrentMap<Class<?>, Method> cache,
            Class<?> type,
            String mcpName,
            String srgName,
            Class<?>... parameterTypes
    ) throws ReflectiveOperationException {
        Method method = cache.get(type);
        if (method != null) {
            return method;
        }

        Method resolved = ReflectionLookup.findMethod(type, mcpName, srgName, parameterTypes);
        Method previous = cache.putIfAbsent(type, resolved);
        return previous == null ? resolved : previous;
    }

    private static Field cachedField(Class<?> type, String mcpName, String srgName) throws ReflectiveOperationException {
        String key = type.getName() + '#' + mcpName;
        Field field = FIELD_CACHE.get(key);
        if (field != null) {
            return field;
        }

        Field resolved = ReflectionLookup.findField(type, mcpName, srgName);
        Field previous = FIELD_CACHE.putIfAbsent(key, resolved);
        return previous == null ? resolved : previous;
    }

    private static final class BoxHit {
        private final double distance;
        private final RayTraceResult result;

        private BoxHit(double distance, RayTraceResult result) {
            this.distance = distance;
            this.result = result;
        }
    }
}
