package com.l.gpom.compat.architecturecraft;

import com.l.gpom.compat.framed.FramedBlockEffectiveState;
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
    private static final ConcurrentMap<Class<?>, Method> INTERCEPT_METHODS = new ConcurrentHashMap<>();
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
        try {
            double x = blockPosX(pos);
            double y = blockPosY(pos);
            double z = blockPosZ(pos);
            return new AxisAlignedBB(
                    minX(local) + x,
                    minY(local) + y,
                    minZ(local) + z,
                    maxX(local) + x,
                    maxY(local) + y,
                    maxZ(local) + z
            );
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return local;
        }
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
        try {
            double x = blockPosX(pos);
            double y = blockPosY(pos);
            double z = blockPosZ(pos);
            return new AxisAlignedBB(
                    minX(worldBox) - x,
                    minY(worldBox) - y,
                    minZ(worldBox) - z,
                    maxX(worldBox) - x,
                    maxY(worldBox) - y,
                    maxZ(worldBox) - z
            );
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return worldBox;
        }
    }

    private static AxisAlignedBB unionBoxes(AxisAlignedBB first, AxisAlignedBB second) {
        try {
            return new AxisAlignedBB(
                    Math.min(minX(first), minX(second)),
                    Math.min(minY(first), minY(second)),
                    Math.min(minZ(first), minZ(second)),
                    Math.max(maxX(first), maxX(second)),
                    Math.max(maxY(first), maxY(second)),
                    Math.max(maxZ(first), maxZ(second))
            );
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return first;
        }
    }

    private static void setBoxHit(Block block, AxisAlignedBB box) {
        try {
            cachedField(block.getClass(), "boxHit", "boxHit").set(block, box);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void setSubHit(RayTraceResult result, int subHit) {
        try {
            cachedField(RayTraceResult.class, "subHit", "field_72310_e").setInt(result, subHit);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static BoxHit intersect(AxisAlignedBB box, BlockPos pos, Vec3d start, Vec3d end) {
        try {
            Method method = cachedMethod(
                    INTERCEPT_METHODS,
                    box.getClass(),
                    "calculateIntercept",
                    "func_72327_a",
                    Vec3d.class,
                    Vec3d.class
            );
            Object value = method.invoke(box, start, end);
            if (!(value instanceof RayTraceResult)) {
                return null;
            }

            Vec3d hitVec = (Vec3d) objectField(value, RayTraceResult.class, "hitVec", "field_72307_f");
            EnumFacing side = (EnumFacing) objectField(value, RayTraceResult.class, "sideHit", "field_178784_b");
            if (hitVec == null || side == null) {
                return null;
            }
            double distance = distanceSquared(start, hitVec);
            return new BoxHit(distance, new RayTraceResult(hitVec, side, pos));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return intersectManually(box, pos, start, end);
        }
    }

    private static BoxHit intersectManually(AxisAlignedBB box, BlockPos pos, Vec3d start, Vec3d end) {
        try {
            double startX = vecX(start);
            double startY = vecY(start);
            double startZ = vecZ(start);
            double deltaX = vecX(end) - startX;
            double deltaY = vecY(end) - startY;
            double deltaZ = vecZ(end) - startZ;
            double[] interval = new double[] {0.0D, 1.0D};
            EnumFacing[] sides = new EnumFacing[] {null, null};

            if (!clipAxis(startX, deltaX, minX(box), maxX(box), EnumFacing.WEST, EnumFacing.EAST, interval, sides)
                    || !clipAxis(startY, deltaY, minY(box), maxY(box), EnumFacing.DOWN, EnumFacing.UP, interval, sides)
                    || !clipAxis(startZ, deltaZ, minZ(box), maxZ(box), EnumFacing.NORTH, EnumFacing.SOUTH, interval, sides)) {
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
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
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

    private static double distanceSquared(Vec3d start, Vec3d hit) throws ReflectiveOperationException {
        double deltaX = vecX(hit) - vecX(start);
        double deltaY = vecY(hit) - vecY(start);
        double deltaZ = vecZ(hit) - vecZ(start);
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    private static double blockPosX(BlockPos pos) throws ReflectiveOperationException {
        return numberFromMethod(pos, "getX", "func_177958_n");
    }

    private static double blockPosY(BlockPos pos) throws ReflectiveOperationException {
        return numberFromMethod(pos, "getY", "func_177956_o");
    }

    private static double blockPosZ(BlockPos pos) throws ReflectiveOperationException {
        return numberFromMethod(pos, "getZ", "func_177952_p");
    }

    private static double vecX(Vec3d vec) throws ReflectiveOperationException {
        return doubleField(vec, Vec3d.class, "x", "field_72450_a");
    }

    private static double vecY(Vec3d vec) throws ReflectiveOperationException {
        return doubleField(vec, Vec3d.class, "y", "field_72448_b");
    }

    private static double vecZ(Vec3d vec) throws ReflectiveOperationException {
        return doubleField(vec, Vec3d.class, "z", "field_72449_c");
    }

    private static double minX(AxisAlignedBB box) throws ReflectiveOperationException {
        return doubleField(box, AxisAlignedBB.class, "minX", "field_72340_a");
    }

    private static double minY(AxisAlignedBB box) throws ReflectiveOperationException {
        return doubleField(box, AxisAlignedBB.class, "minY", "field_72338_b");
    }

    private static double minZ(AxisAlignedBB box) throws ReflectiveOperationException {
        return doubleField(box, AxisAlignedBB.class, "minZ", "field_72339_c");
    }

    private static double maxX(AxisAlignedBB box) throws ReflectiveOperationException {
        return doubleField(box, AxisAlignedBB.class, "maxX", "field_72336_d");
    }

    private static double maxY(AxisAlignedBB box) throws ReflectiveOperationException {
        return doubleField(box, AxisAlignedBB.class, "maxY", "field_72337_e");
    }

    private static double maxZ(AxisAlignedBB box) throws ReflectiveOperationException {
        return doubleField(box, AxisAlignedBB.class, "maxZ", "field_72334_f");
    }

    private static double numberFromMethod(Object target, String mcpName, String srgName)
            throws ReflectiveOperationException {
        Method method = ReflectionLookup.findMethod(target.getClass(), mcpName, srgName);
        Object value = method.invoke(target);
        if (!(value instanceof Number)) {
            throw new NoSuchMethodException(mcpName);
        }
        return ((Number) value).doubleValue();
    }

    private static double doubleField(Object target, Class<?> declaringType, String mcpName, String srgName)
            throws ReflectiveOperationException {
        return cachedField(declaringType, mcpName, srgName).getDouble(target);
    }

    private static Object objectField(Object target, Class<?> declaringType, String mcpName, String srgName)
            throws ReflectiveOperationException {
        return cachedField(declaringType, mcpName, srgName).get(target);
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
