package com.l.gpom.compat.framed;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FramedBlockEffectiveState {
    private static final String ARCHITECTURE_TILE_SHAPE = "com.elytradev.architecture.common.tile.TileShape";
    private static final String ARCHITECTURE_BLOCK_PACKAGE = "com.elytradev.architecture.common.block.";
    private static final String BLOCKCRAFTERY_TILE_EDITABLE_BLOCK = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String BLOCKCRAFTERY_BLOCK_PACKAGE = "epicsquid.blockcraftery.block.";
    private static final String VANILLA_AIR_BLOCK = "net.minecraft.block.BlockAir";

    private static final ConcurrentMap<Class<?>, Method> ARCHITECTURE_BASE_STATE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> STATE_BLOCK_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> TILE_ENTITY_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private FramedBlockEffectiveState() {
    }

    public static IBlockAccess wrap(IBlockAccess delegate) {
        return delegate instanceof EffectiveBlockAccess ? delegate : new EffectiveBlockAccess(delegate);
    }

    public static IBlockState state(IBlockAccess world, BlockPos pos) {
        TileEntity tile = tileEntity(world, pos);
        if (tile == null) {
            return null;
        }

        String className = tile.getClass().getName();
        if (BLOCKCRAFTERY_TILE_EDITABLE_BLOCK.equals(className)) {
            return sanitized(readBlockcrafteryState(tile));
        }
        if (ARCHITECTURE_TILE_SHAPE.equals(className)) {
            return sanitized(readArchitectureBaseState(tile));
        }
        return null;
    }

    public static Block blockFromState(IBlockState state) {
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

    private static IBlockState sanitized(IBlockState state) {
        Block block = blockFromState(state);
        if (block == null || isAir(block)) {
            return null;
        }

        String className = block.getClass().getName();
        if (className.startsWith(BLOCKCRAFTERY_BLOCK_PACKAGE) || className.startsWith(ARCHITECTURE_BLOCK_PACKAGE)) {
            return null;
        }
        return state;
    }

    private static boolean isAir(Block block) {
        return VANILLA_AIR_BLOCK.equals(block.getClass().getName());
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

    private static IBlockState readBlockcrafteryState(TileEntity tile) {
        try {
            Object value = cachedField(tile.getClass(), "state", "state").get(tile);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static IBlockState readArchitectureBaseState(TileEntity tile) {
        try {
            Method method = cachedMethod(
                    ARCHITECTURE_BASE_STATE_METHODS,
                    tile.getClass(),
                    "getBaseBlockState",
                    "getBaseBlockState"
            );
            Object value = method.invoke(tile);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
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

        Method resolved = findMethod(type, mcpName, srgName, parameterTypes);
        Method previous = cache.putIfAbsent(type, resolved);
        return previous == null ? resolved : previous;
    }

    private static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        NoSuchMethodException failure = null;
        for (String name : new String[] {mcpName, srgName}) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException exception) {
                failure = exception;
            }

            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException exception) {
                    failure = exception;
                }
            }
        }
        throw failure == null ? new NoSuchMethodException(mcpName) : failure;
    }

    private static Field cachedField(Class<?> type, String mcpName, String srgName) throws ReflectiveOperationException {
        String key = type.getName() + '#' + mcpName;
        Field field = FIELD_CACHE.get(key);
        if (field != null) {
            return field;
        }

        Field resolved = findField(type, mcpName, srgName);
        Field previous = FIELD_CACHE.putIfAbsent(key, resolved);
        return previous == null ? resolved : previous;
    }

    private static Field findField(Class<?> type, String mcpName, String srgName) throws NoSuchFieldException {
        NoSuchFieldException failure = null;
        for (String name : new String[] {mcpName, srgName}) {
            try {
                Field field = type.getField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException exception) {
                failure = exception;
            }

            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException exception) {
                    failure = exception;
                }
            }
        }
        throw failure == null ? new NoSuchFieldException(mcpName) : failure;
    }

    private static final class EffectiveBlockAccess implements IBlockAccess {
        private final IBlockAccess delegate;

        private EffectiveBlockAccess(IBlockAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return delegate.getTileEntity(pos);
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return delegate.getCombinedLight(pos, lightValue);
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            IBlockState effective = state(delegate, pos);
            return effective == null ? delegate.getBlockState(pos) : effective;
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return delegate.isAirBlock(pos);
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return delegate.getBiome(pos);
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return delegate.getStrongPower(pos, direction);
        }

        @Override
        public WorldType getWorldType() {
            return delegate.getWorldType();
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            IBlockState effective = state(delegate, pos);
            Block block = blockFromState(effective);
            return block == null ? delegate.isSideSolid(pos, side, defaultValue) : block.isSideSolid(effective, this, pos, side);
        }
    }
}
