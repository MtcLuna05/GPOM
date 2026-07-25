package com.l.gpom.compat.blockcraftery;

import com.l.gpom.compat.framed.FramedBlockEffectiveState;
import com.l.gpom.compat.framed.FramedMaterialData;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class BlockcrafteryCompat {
    private static final String TILE_EDITABLE_BLOCK = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String BLOCKCRAFTERY_BLOCK_PACKAGE = "epicsquid.blockcraftery.block.";
    private static final String BLOCKCRAFTERY_SLANT = "epicsquid.blockcraftery.block.BlockEditableSlant";
    private static final String BLOCKCRAFTERY_CORNER = "epicsquid.blockcraftery.block.BlockEditableCorner";
    private static final String VANILLA_AIR_BLOCK = "net.minecraft.block.BlockAir";
    private static final AxisAlignedBB FULL_BLOCK = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
    private static final double EPSILON = 1.0E-7D;

    private static final ConcurrentMap<Class<?>, Method> ADD_COLLISION_BOX_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BOUNDING_BOX_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> CAN_PLACE_SIDE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> CHECK_NO_ENTITY_COLLISION_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> COLLISION_RAY_TRACE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> DEFAULT_STATE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> DOES_SIDE_BLOCK_RENDERING_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BLOCK_FACE_SHAPE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> INTERCEPT_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> MATERIAL_REPLACEABLE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> SELECTED_BOX_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> SHOULD_SIDE_RENDER_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> STATE_BLOCK_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> STATE_MATERIAL_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> TILE_ENTITY_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> WORLD_BLOCK_STATE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BLOCK_POS_X_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BLOCK_POS_Y_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BLOCK_POS_Z_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private static volatile Field tileStateField;
    private static final Map<IBlockState, Boolean> FUSED_GLASS_STATES = Collections.synchronizedMap(new WeakHashMap<IBlockState, Boolean>());

    public static boolean isFusedGlassState(IBlockState state) {
        return Boolean.TRUE.equals(FUSED_GLASS_STATES.get(state));
    }

    private BlockcrafteryCompat() {
    }

    public static RayTraceResult rayTraceShape(
            Block block,
            IBlockState state,
            World world,
            BlockPos pos,
            Vec3d start,
            Vec3d end
    ) {
        List<AxisAlignedBB> boxes = shapeBoxes(block, state, world, pos);
        return nearestHit(pos, start, end, boxes);
    }

    public static AxisAlignedBB selectedShapeBox(Block block, IBlockState state, World world, BlockPos pos) {
        AxisAlignedBB union = null;
        for (AxisAlignedBB box : shapeBoxes(block, state, world, pos)) {
            union = union == null ? box : unionBoxes(union, box, pos);
        }
        return union == null ? fullWorldBox(pos) : union;
    }

    public static Boolean mayPlaceBlockcrafteryShape(
            World world,
            Block block,
            BlockPos pos,
            boolean skipCollisionCheck,
            EnumFacing sidePlacedOn,
            Entity placer
    ) {
        if (!isBlockcrafteryShape(block) || skipCollisionCheck) {
            return null;
        }
        // Preserve Forge's non-player placement event behavior by letting vanilla handle it.
        if (placer != null && !(placer instanceof EntityPlayer)) {
            return null;
        }

        IBlockState defaultState = defaultState(block);
        IBlockState existingState = blockState(world, pos);
        if (defaultState == null || existingState == null) {
            return null;
        }
        if (!shapeCollisionClear(block, defaultState, world, pos)) {
            return Boolean.FALSE;
        }
        Boolean replaceable = replaceableAndPlaceable(world, block, existingState, pos, sidePlacedOn);
        return replaceable == null ? null : replaceable;
    }

    public static RayTraceResult rayTraceCopiedBlock(
            Block hostBlock,
            IBlockState hostState,
            World world,
            BlockPos pos,
            Vec3d start,
            Vec3d end
    ) {
        IBlockState copied = copiedState(hostBlock, world, pos);
        Block copiedBlock = blockFromState(copied);
        if (copiedBlock == null) {
            return nearestHit(pos, start, end, singletonFullBlock(pos));
        }

        try {
            Method method = cachedMethod(
                    COLLISION_RAY_TRACE_METHODS,
                    copiedBlock.getClass(),
                    "collisionRayTrace",
                    "func_180636_a",
                    IBlockState.class,
                    World.class,
                    BlockPos.class,
                    Vec3d.class,
                    Vec3d.class
            );
            return (RayTraceResult) method.invoke(copiedBlock, copied, world, pos, start, end);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return nearestHit(pos, start, end, singletonFullBlock(pos));
        }
    }

    public static AxisAlignedBB copiedBoundingBoxOrFull(Block hostBlock, IBlockState hostState, IBlockAccess world, BlockPos pos) {
        IBlockState copied = copiedState(hostBlock, world, pos);
        Block copiedBlock = blockFromState(copied);
        if (copiedBlock == null) {
            return FULL_BLOCK;
        }

        try {
            Method method = cachedMethod(
                    BOUNDING_BOX_METHODS,
                    copiedBlock.getClass(),
                    "getBoundingBox",
                    "func_185496_a",
                    IBlockState.class,
                    IBlockAccess.class,
                    BlockPos.class
            );
            AxisAlignedBB box = (AxisAlignedBB) method.invoke(copiedBlock, copied, world, pos);
            return box == null ? FULL_BLOCK : box;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return FULL_BLOCK;
        }
    }

    public static AxisAlignedBB copiedSelectedBoxOrFull(Block hostBlock, IBlockState hostState, World world, BlockPos pos) {
        IBlockState copied = copiedState(hostBlock, world, pos);
        Block copiedBlock = blockFromState(copied);
        if (copiedBlock == null) {
            return fullWorldBox(pos);
        }

        try {
            Method method = cachedMethod(
                    SELECTED_BOX_METHODS,
                    copiedBlock.getClass(),
                    "getSelectedBoundingBox",
                    "func_180640_a",
                    IBlockState.class,
                    World.class,
                    BlockPos.class
            );
            AxisAlignedBB box = (AxisAlignedBB) method.invoke(copiedBlock, copied, world, pos);
            return box == null ? fullWorldBox(pos) : box;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fullWorldBox(pos);
        }
    }

    public static boolean copiedShouldSideBeRendered(
            Block hostBlock,
            IBlockState hostState,
            IBlockAccess world,
            BlockPos pos,
            EnumFacing side
    ) {
        BlockPos hostPos = renderHostPos(hostBlock, pos, side);
        if (hostPos == null) {
            return true;
        }
        if (sameNonSolidMaterialNeighborCoversSide(hostBlock, hostState, world, hostPos, side)) {
            return false;
        }
        return copiedBlockBoolean(
                hostBlock,
                world,
                hostPos,
                pos,
                SHOULD_SIDE_RENDER_METHODS,
                "shouldSideBeRendered",
                "func_176225_a",
                true,
                side
        );
    }

    public static boolean copiedDoesSideBlockRendering(
            Block hostBlock,
            IBlockState hostState,
            IBlockAccess world,
            BlockPos pos,
            EnumFacing side
    ) {
        if (sameNonSolidMaterialNeighborCoversSide(hostBlock, hostState, world, pos, side)) {
            return true;
        }
        boolean fallback = copiedShouldSideBeRendered(hostBlock, hostState, world, pos, side);
        return copiedBlockBoolean(
                hostBlock,
                world,
                pos,
                pos,
                DOES_SIDE_BLOCK_RENDERING_METHODS,
                "doesSideBlockRendering",
                "doesSideBlockRendering",
                fallback,
                side
        );
    }

    private static List<AxisAlignedBB> shapeBoxes(Block block, IBlockState state, World world, BlockPos pos) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        AxisAlignedBB mask = fullWorldBox(pos);
        try {
            Method method = cachedMethod(
                    ADD_COLLISION_BOX_METHODS,
                    block.getClass(),
                    "addCollisionBoxToList",
                    "func_185477_a",
                    IBlockState.class,
                    World.class,
                    BlockPos.class,
                    AxisAlignedBB.class,
                    List.class,
                    Entity.class,
                    boolean.class
            );
            method.invoke(block, state, world, pos, mask, boxes, null, false);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            boxes.clear();
        }
        if (boxes.isEmpty()) {
            boxes.add(mask);
        }
        return boxes;
    }

    private static List<AxisAlignedBB> singletonFullBlock(BlockPos pos) {
        List<AxisAlignedBB> boxes = new ArrayList<>(1);
        boxes.add(fullWorldBox(pos));
        return boxes;
    }

    private static boolean shapeCollisionClear(Block block, IBlockState state, World world, BlockPos pos) {
        for (AxisAlignedBB box : shapeBoxes(block, state, world, pos)) {
            if (!checkNoEntityCollision(world, box)) {
                return false;
            }
        }
        return true;
    }

    private static boolean copiedBlockBoolean(
            Block hostBlock,
            IBlockAccess world,
            BlockPos hostPos,
            BlockPos queryPos,
            ConcurrentMap<Class<?>, Method> cache,
            String mcpName,
            String srgName,
            boolean fallback,
            EnumFacing side
    ) {
        IBlockState copied = copiedState(hostBlock, world, hostPos);
        Block copiedBlock = blockFromState(copied);
        if (copiedBlock == null) {
            return fallback;
        }

        try {
            Method method = cachedMethod(
                    cache,
                    copiedBlock.getClass(),
                    mcpName,
                    srgName,
                    IBlockState.class,
                    IBlockAccess.class,
                    BlockPos.class,
                    EnumFacing.class
            );
            Object value = method.invoke(copiedBlock, copied, FramedBlockEffectiveState.wrap(world), queryPos, side);
            return value instanceof Boolean ? (Boolean) value : fallback;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return fallback;
        }
    }

    private static boolean checkNoEntityCollision(World world, AxisAlignedBB box) {
        try {
            Method method = cachedMethod(
                    CHECK_NO_ENTITY_COLLISION_METHODS,
                    world.getClass(),
                    "checkNoEntityCollision",
                    "func_72855_b",
                    AxisAlignedBB.class
            );
            Object value = method.invoke(world, box);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static RayTraceResult nearestHit(
            BlockPos pos,
            Vec3d start,
            Vec3d end,
            List<AxisAlignedBB> worldBoxes
    ) {
        RayTraceResult best = null;
        double bestDistance = Double.MAX_VALUE;

        for (AxisAlignedBB worldBox : worldBoxes) {
            BoxHit hit = intersect(worldBox, pos, start, end);
            if (hit == null || hit.distance >= bestDistance) {
                continue;
            }

            bestDistance = hit.distance;
            best = hit.result;
        }
        return best;
    }

    private static boolean sameNonSolidMaterialNeighborCoversSide(
            Block hostBlock,
            IBlockState hostState,
            IBlockAccess world,
            BlockPos pos,
            EnumFacing side
    ) {
        if (world == null || pos == null || side == null) {
            return false;
        }
        IBlockState copied = copiedState(hostBlock, world, pos);
        if (copied == null || solidRenderState(copied)) {
            return false;
        }
        EnumFacing cullSide = materialCullSide(hostBlock, side);
        if (!shapeFaceCanCull(hostBlock, hostState, world, pos, cullSide)) {
            return false;
        }
        BlockPos neighborPos = MinecraftMappingCompat.blockPosOffset(pos, cullSide);
        if (neighborPos == null || neighborPos == pos) {
            return false;
        }
        IBlockState neighbor = FramedBlockEffectiveState.state(world, neighborPos);
        if (neighbor == null) {
            neighbor = blockState(world, neighborPos);
        }
        if (!sameMaterialState(copied, neighbor)) {
            return false;
        }
        return neighborCoversHostSide(hostBlock, hostState, world, pos, cullSide, neighborPos);
    }

    private static boolean neighborCoversHostSide(
            Block hostBlock,
            IBlockState hostState,
            IBlockAccess world,
            BlockPos pos,
            EnumFacing side,
            BlockPos neighborPos
    ) {
        List<AxisAlignedBB> hostBoxes = localBoxes(hostBlock, hostState, world, pos);
        IBlockState neighborHostState = blockState(world, neighborPos);
        Block neighborHostBlock = blockFromState(neighborHostState);
        if (neighborHostBlock == null) {
            return false;
        }
        if (!shapeFaceCanCull(neighborHostBlock, neighborHostState, world, neighborPos, opposite(side))) {
            return false;
        }
        List<AxisAlignedBB> neighborBoxes = localBoxes(neighborHostBlock, neighborHostState, world, neighborPos);
        boolean touchesRenderedSide = false;
        for (AxisAlignedBB hostBox : hostBoxes) {
            if (!touchesSide(hostBox, side, false)) {
                continue;
            }
            touchesRenderedSide = true;
            if (!coveredByNeighbor(hostBox, neighborBoxes, side)) {
                return false;
            }
        }
        return touchesRenderedSide;
    }

    private static List<AxisAlignedBB> localBoxes(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        if (isBlockcrafteryShape(block)) {
            List<AxisAlignedBB> boxes = localShapeBoxes(block, state, world, pos);
            if (!boxes.isEmpty()) {
                return boxes;
            }
        }
        List<AxisAlignedBB> boxes = new ArrayList<>(1);
        AxisAlignedBB box = blockBoundingBox(block, state, world, pos);
        boxes.add(box == null ? FULL_BLOCK : box);
        return boxes;
    }

    private static List<AxisAlignedBB> localShapeBoxes(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        List<AxisAlignedBB> local = new ArrayList<>();
        World shapeWorld = world instanceof World ? (World) world : null;
        for (AxisAlignedBB worldBox : shapeBoxes(block, state, shapeWorld, pos)) {
            AxisAlignedBB localBox = localizeBox(worldBox, pos);
            if (localBox != null) {
                local.add(localBox);
            }
        }
        return local;
    }

    private static AxisAlignedBB localizeBox(AxisAlignedBB worldBox, BlockPos pos) {
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
            return null;
        }
    }

    private static AxisAlignedBB blockBoundingBox(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        try {
            Method method = cachedMethod(
                    BOUNDING_BOX_METHODS,
                    block.getClass(),
                    "getBoundingBox",
                    "func_185496_a",
                    IBlockState.class,
                    IBlockAccess.class,
                    BlockPos.class
            );
            Object value = method.invoke(block, state, world, pos);
            return value instanceof AxisAlignedBB ? (AxisAlignedBB) value : FULL_BLOCK;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return FULL_BLOCK;
        }
    }

    private static boolean coveredByNeighbor(AxisAlignedBB hostBox, List<AxisAlignedBB> neighborBoxes, EnumFacing side) {
        for (AxisAlignedBB neighborBox : neighborBoxes) {
            if (touchesSide(neighborBox, side, true) && coversPerpendicularAxes(neighborBox, hostBox, side)) {
                return true;
            }
        }
        return false;
    }

    private static boolean touchesSide(AxisAlignedBB box, EnumFacing side, boolean neighbor) {
        try {
            switch (side) {
                case DOWN:
                    return neighbor ? maxY(box) >= 1.0D - EPSILON : minY(box) <= EPSILON;
                case UP:
                    return neighbor ? minY(box) <= EPSILON : maxY(box) >= 1.0D - EPSILON;
                case NORTH:
                    return neighbor ? maxZ(box) >= 1.0D - EPSILON : minZ(box) <= EPSILON;
                case SOUTH:
                    return neighbor ? minZ(box) <= EPSILON : maxZ(box) >= 1.0D - EPSILON;
                case WEST:
                    return neighbor ? maxX(box) >= 1.0D - EPSILON : minX(box) <= EPSILON;
                case EAST:
                    return neighbor ? minX(box) <= EPSILON : maxX(box) >= 1.0D - EPSILON;
                default:
                    return false;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean coversPerpendicularAxes(AxisAlignedBB covering, AxisAlignedBB covered, EnumFacing side) {
        try {
            switch (side) {
                case DOWN:
                case UP:
                    return minX(covering) <= minX(covered) + EPSILON
                            && maxX(covering) >= maxX(covered) - EPSILON
                            && minZ(covering) <= minZ(covered) + EPSILON
                            && maxZ(covering) >= maxZ(covered) - EPSILON;
                case NORTH:
                case SOUTH:
                    return minX(covering) <= minX(covered) + EPSILON
                            && maxX(covering) >= maxX(covered) - EPSILON
                            && minY(covering) <= minY(covered) + EPSILON
                            && maxY(covering) >= maxY(covered) - EPSILON;
                case WEST:
                case EAST:
                    return minY(covering) <= minY(covered) + EPSILON
                            && maxY(covering) >= maxY(covered) - EPSILON
                            && minZ(covering) <= minZ(covered) + EPSILON
                            && maxZ(covering) >= maxZ(covered) - EPSILON;
                default:
                    return false;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean solidRenderState(IBlockState state) {
        try {
            return MinecraftMappingCompat.blockStateIsOpaqueCube(state);
        } catch (RuntimeException | LinkageError ignored) {
            return true;
        }
    }

    private static boolean shapeFaceCanCull(Block block, IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        if (!isBlockcrafteryShape(block)) {
            return true;
        }
        if (state == null || world == null || pos == null || side == null) {
            return false;
        }

        try {
            Method method = cachedMethod(
                    BLOCK_FACE_SHAPE_METHODS,
                    block.getClass(),
                    "getBlockFaceShape",
                    "func_193383_a",
                    IBlockAccess.class,
                    IBlockState.class,
                    BlockPos.class,
                    EnumFacing.class
            );
            Object value = method.invoke(block, world, state, pos, side);
            return value instanceof Enum && "SOLID".equals(((Enum<?>) value).name());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static EnumFacing materialCullSide(Block hostBlock, EnumFacing renderSide) {
        return renderSide;
    }

    private static BlockPos renderHostPos(Block hostBlock, BlockPos pos, EnumFacing side) {
        return isBlockcrafteryShape(hostBlock) ? hostPosForRenderedSide(pos, side) : pos;
    }

    private static BlockPos hostPosForRenderedSide(BlockPos neighborPos, EnumFacing side) {
        if (neighborPos == null || side == null) {
            return null;
        }
        BlockPos hostPos = MinecraftMappingCompat.blockPosOffset(neighborPos, opposite(side));
        return hostPos == neighborPos ? null : hostPos;
    }

    private static boolean sameMaterialState(IBlockState first, IBlockState second) {
        Block firstBlock = blockFromState(first);
        Block secondBlock = blockFromState(second);
        if (firstBlock == null || secondBlock == null || firstBlock != secondBlock) {
            return false;
        }
        if (first.equals(second)) {
            return true;
        }
        try {
            return MinecraftMappingCompat.blockMetaFromState(firstBlock, first)
                    == MinecraftMappingCompat.blockMetaFromState(secondBlock, second);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static IBlockState copiedState(Block hostBlock, IBlockAccess world, BlockPos pos) {
        TileEntity tile = tileEntity(world, pos);
        if (tile == null || !TILE_EDITABLE_BLOCK.equals(tile.getClass().getName())) {
            return null;
        }

        IBlockState copied = readCopiedState(tile);
        Block copiedBlock = blockFromState(copied);
        if (copiedBlock == null || copiedBlock == hostBlock || isAir(copiedBlock)) {
            return null;
        }
        return copiedBlock.getClass().getName().startsWith(BLOCKCRAFTERY_BLOCK_PACKAGE) ? null : copied;
    }

    private static TileEntity tileEntity(IBlockAccess world, BlockPos pos) {
        try {
            Method method = cachedMethod(
                    TILE_ENTITY_METHODS,
                    world.getClass(),
                    "getTileEntity",
                    "func_175625_s",
                    BlockPos.class
            );
            Object value = method.invoke(world, pos);
            return value instanceof TileEntity ? (TileEntity) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static IBlockState readCopiedState(TileEntity tile) {
        FramedMaterialData.MaterialStates saved = FramedMaterialData.states(tile, "blockcraftery");
        if (saved.present()) {
            return saved.primary();
        }
        try {
            Field field = tileStateField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(tile.getClass())) {
                Class<?> type = tile.getClass();
                while (type != null && field == null) {
                    try {
                        field = type.getDeclaredField("state");
                    } catch (NoSuchFieldException ignored) {
                        type = type.getSuperclass();
                    }
                }
                if (field == null) {
                    return null;
                }
                field.setAccessible(true);
                tileStateField = field;
            }
            Object value = field.get(tile);
            if (!(value instanceof IBlockState)) {
                return null;
            }
            IBlockState state = (IBlockState) value;
            try {
                Field stackField = tile.getClass().getDeclaredField("stack");
                stackField.setAccessible(true);
                Object stack = stackField.get(tile);
                Object tag = MinecraftMappingCompat.invoke(stack, "itemStack.writeToNBT",
                        new Class<?>[]{net.minecraft.nbt.NBTTagCompound.class},
                        new Object[]{new net.minecraft.nbt.NBTTagCompound()},
                        "func_77955_b", "writeToNBT");
                if (tag instanceof net.minecraft.nbt.NBTTagCompound) {
                    Object id = MinecraftMappingCompat.invoke(tag, "nbt.getString",
                            new Class<?>[]{String.class}, new Object[]{"id"},
                            "func_74779_i", "getString");
                    if (id instanceof String && ((String) id).endsWith("block_fused_glass")) {
                        FUSED_GLASS_STATES.put(state, Boolean.TRUE);
                        state = fusedGlassState(state);
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
            return state;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static IBlockState fusedGlassState(IBlockState state) {
        try {
            Object properties = MinecraftMappingCompat.invoke(state, "blockState.getProperties",
                    MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                    "func_177228_b", "getProperties");
            if (!(properties instanceof Map)) {
                return state;
            }
            for (Object raw : ((Map<?, ?>) properties).keySet()) {
                if (!(raw instanceof IProperty)) {
                    continue;
                }
                IProperty<?> property = (IProperty<?>) raw;
                Object name = MinecraftMappingCompat.invoke(property, "property.getName",
                        MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                        "func_177701_a", "getName");
                if (!(name instanceof String) || !"kind".equals(name)) {
                    continue;
                }
                Object allowed = MinecraftMappingCompat.invoke(property, "property.getAllowedValues",
                        MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                        "func_177700_c", "getAllowedValues");
                if (!(allowed instanceof Iterable)) {
                    continue;
                }
                for (Object value : (Iterable<?>) allowed) {
                    Object valueName = MinecraftMappingCompat.invoke(property, "property.getName(value)",
                            new Class<?>[]{Comparable.class}, new Object[]{value},
                            "func_177702_a", "getName");
                    if ("fused_glass".equals(valueName) || "glass".equals(valueName)) {
                        Object updated = MinecraftMappingCompat.invoke(state, "blockState.withProperty",
                                new Class<?>[]{IProperty.class, Comparable.class}, new Object[]{property, value},
                                "func_177226_a", "withProperty");
                        return updated instanceof IBlockState ? (IBlockState) updated : state;
                    }
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return state;
    }

    private static Block blockFromState(IBlockState state) {
        if (state == null) {
            return null;
        }
        try {
            Method method = cachedMethod(
                    STATE_BLOCK_METHODS,
                    state.getClass(),
                    "getBlock",
                    "func_177230_c"
            );
            Object value = method.invoke(state);
            return value instanceof Block ? (Block) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static IBlockState blockState(IBlockAccess world, BlockPos pos) {
        try {
            Method method = cachedMethod(
                    WORLD_BLOCK_STATE_METHODS,
                    world.getClass(),
                    "getBlockState",
                    "func_180495_p",
                    BlockPos.class
            );
            Object value = method.invoke(world, pos);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static IBlockState defaultState(Block block) {
        try {
            Method method = cachedMethod(
                    DEFAULT_STATE_METHODS,
                    block.getClass(),
                    "getDefaultState",
                    "func_176223_P"
            );
            Object value = method.invoke(block);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Boolean replaceableAndPlaceable(
            World world,
            Block block,
            IBlockState existingState,
            BlockPos pos,
            EnumFacing sidePlacedOn
    ) {
        try {
            Object material = material(existingState);
            if (isAnvilOverCircuits(block, material)) {
                return Boolean.TRUE;
            }
            return Boolean.valueOf(materialReplaceable(material) && canPlaceBlockOnSide(block, world, pos, sidePlacedOn));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static Object material(IBlockState state) throws ReflectiveOperationException {
        Method method = cachedMethod(
                STATE_MATERIAL_METHODS,
                state.getClass(),
                "getMaterial",
                "func_185904_a"
        );
        return method.invoke(state);
    }

    private static boolean materialReplaceable(Object material) throws ReflectiveOperationException {
        Method method = cachedMethod(
                MATERIAL_REPLACEABLE_METHODS,
                material.getClass(),
                "isReplaceable",
                "func_76222_j"
        );
        Object value = method.invoke(material);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean canPlaceBlockOnSide(Block block, World world, BlockPos pos, EnumFacing sidePlacedOn)
            throws ReflectiveOperationException {
        Method method = cachedMethod(
                CAN_PLACE_SIDE_METHODS,
                block.getClass(),
                "canPlaceBlockOnSide",
                "func_176198_a",
                World.class,
                BlockPos.class,
                EnumFacing.class
        );
        Object value = method.invoke(block, world, pos, sidePlacedOn);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean isAnvilOverCircuits(Block block, Object material) {
        try {
            return material == circuitsMaterial() && block == anvilBlock();
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static Object circuitsMaterial() throws ReflectiveOperationException {
        return cachedField(Material.class, "CIRCUITS", "field_151594_q").get(null);
    }

    private static Object anvilBlock() throws ReflectiveOperationException {
        return cachedField(Blocks.class, "ANVIL", "field_150467_bQ").get(null);
    }

    private static boolean isAir(Block block) {
        return VANILLA_AIR_BLOCK.equals(block.getClass().getName());
    }

    private static boolean isBlockcrafteryShape(Block block) {
        String className = block.getClass().getName();
        return BLOCKCRAFTERY_SLANT.equals(className) || BLOCKCRAFTERY_CORNER.equals(className);
    }

    private static EnumFacing opposite(EnumFacing side) {
        if (side == null) {
            return EnumFacing.UP;
        }
        switch (side) {
            case DOWN:
                return EnumFacing.UP;
            case UP:
                return EnumFacing.DOWN;
            case NORTH:
                return EnumFacing.SOUTH;
            case SOUTH:
                return EnumFacing.NORTH;
            case WEST:
                return EnumFacing.EAST;
            case EAST:
            default:
                return EnumFacing.WEST;
        }
    }

    private static AxisAlignedBB fullWorldBox(BlockPos pos) {
        try {
            double x = blockPosX(pos);
            double y = blockPosY(pos);
            double z = blockPosZ(pos);
            return new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return FULL_BLOCK;
        }
    }

    private static AxisAlignedBB unionBoxes(AxisAlignedBB first, AxisAlignedBB second, BlockPos fallbackPos) {
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
            return fullWorldBox(fallbackPos);
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
            double deltaX = vecX(hitVec) - vecX(start);
            double deltaY = vecY(hitVec) - vecY(start);
            double deltaZ = vecZ(hitVec) - vecZ(start);
            double distance = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
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
            return new BoxHit(distance, new RayTraceResult(hitVec, side, pos));
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

    private static double blockPosX(BlockPos pos) throws ReflectiveOperationException {
        return numberFromMethod(BLOCK_POS_X_METHODS, pos, "getX", "func_177958_n");
    }

    private static double blockPosY(BlockPos pos) throws ReflectiveOperationException {
        return numberFromMethod(BLOCK_POS_Y_METHODS, pos, "getY", "func_177956_o");
    }

    private static double blockPosZ(BlockPos pos) throws ReflectiveOperationException {
        return numberFromMethod(BLOCK_POS_Z_METHODS, pos, "getZ", "func_177952_p");
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

    private static double numberFromMethod(ConcurrentMap<Class<?>, Method> cache, Object target, String mcpName, String srgName)
            throws ReflectiveOperationException {
        Method method = cachedMethod(cache, target.getClass(), mcpName, srgName);
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
