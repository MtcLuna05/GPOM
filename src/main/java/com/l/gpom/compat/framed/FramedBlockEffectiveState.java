package com.l.gpom.compat.framed;

import com.l.gpom.util.ReflectionLookup;
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
    private static final ConcurrentMap<Class<?>, Method> BLOCK_STATE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> COMBINED_LIGHT_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> IS_AIR_BLOCK_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BIOME_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> STRONG_POWER_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> WORLD_TYPE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> SIDE_SOLID_METHODS = new ConcurrentHashMap<>();
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

    private static final class EffectiveBlockAccess implements IBlockAccess {
        private final IBlockAccess delegate;

        private EffectiveBlockAccess(IBlockAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return func_175625_s(pos);
        }

        public TileEntity func_175625_s(BlockPos pos) {
            return tileEntity(delegate, pos);
        }

        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return func_175626_b(pos, lightValue);
        }

        public int func_175626_b(BlockPos pos, int lightValue) {
            Object value = invokeDelegate(
                    COMBINED_LIGHT_METHODS,
                    "getCombinedLight",
                    "func_175626_b",
                    new Class<?>[]{BlockPos.class, int.class},
                    pos,
                    lightValue
            );
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return func_180495_p(pos);
        }

        public IBlockState func_180495_p(BlockPos pos) {
            IBlockState effective = state(delegate, pos);
            if (effective != null) {
                return effective;
            }
            Object value = invokeDelegate(
                    BLOCK_STATE_METHODS,
                    "getBlockState",
                    "func_180495_p",
                    new Class<?>[]{BlockPos.class},
                    pos
            );
            return value instanceof IBlockState ? (IBlockState) value : null;
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return func_175623_d(pos);
        }

        public boolean func_175623_d(BlockPos pos) {
            Object value = invokeDelegate(
                    IS_AIR_BLOCK_METHODS,
                    "isAirBlock",
                    "func_175623_d",
                    new Class<?>[]{BlockPos.class},
                    pos
            );
            return value instanceof Boolean && (Boolean) value;
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return func_180494_b(pos);
        }

        public Biome func_180494_b(BlockPos pos) {
            Object value = invokeDelegate(
                    BIOME_METHODS,
                    "getBiome",
                    "func_180494_b",
                    new Class<?>[]{BlockPos.class},
                    pos
            );
            return value instanceof Biome ? (Biome) value : null;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return func_175627_a(pos, direction);
        }

        public int func_175627_a(BlockPos pos, EnumFacing direction) {
            Object value = invokeDelegate(
                    STRONG_POWER_METHODS,
                    "getStrongPower",
                    "func_175627_a",
                    new Class<?>[]{BlockPos.class, EnumFacing.class},
                    pos,
                    direction
            );
            return value instanceof Number ? ((Number) value).intValue() : 0;
        }

        @Override
        public WorldType getWorldType() {
            return func_175624_G();
        }

        public WorldType func_175624_G() {
            Object value = invokeDelegate(
                    WORLD_TYPE_METHODS,
                    "getWorldType",
                    "func_175624_G",
                    new Class<?>[0]
            );
            return value instanceof WorldType ? (WorldType) value : null;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            IBlockState effective = state(delegate, pos);
            Block block = blockFromState(effective);
            if (block != null) {
                return block.isSideSolid(effective, this, pos, side);
            }
            Object value = invokeDelegate(
                    SIDE_SOLID_METHODS,
                    "isSideSolid",
                    "isSideSolid",
                    new Class<?>[]{BlockPos.class, EnumFacing.class, boolean.class},
                    pos,
                    side,
                    defaultValue
            );
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        private Object invokeDelegate(
                ConcurrentMap<Class<?>, Method> cache,
                String mcpName,
                String srgName,
                Class<?>[] parameterTypes,
                Object... args
        ) {
            try {
                Method method = cache == null ? null : cache.get(delegate.getClass());
                if (method == null) {
                    method = ReflectionLookup.findMethod(delegate.getClass(), mcpName, srgName, parameterTypes);
                    if (cache != null) {
                        Method previous = cache.putIfAbsent(delegate.getClass(), method);
                        method = previous == null ? method : previous;
                    }
                }
                return method.invoke(delegate, args);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return null;
            }
        }
    }
}
