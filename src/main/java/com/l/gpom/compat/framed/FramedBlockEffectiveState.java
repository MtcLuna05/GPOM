package com.l.gpom.compat.framed;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FramedBlockEffectiveState {
    private static final String ARCHITECTURE_TILE_SHAPE = "com.elytradev.architecture.common.tile.TileShape";
    private static final String ARCHITECTURE_BLOCK_PACKAGE = "com.elytradev.architecture.common.block.";
    private static final String BLOCKCRAFTERY_TILE_EDITABLE_BLOCK = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String BLOCKCRAFTERY_BLOCK_PACKAGE = "epicsquid.blockcraftery.block.";
    private static final String CELERITAS_BLOCK_ACCESS = "org.taumc.celeritas.impl.world.cloned.CeleritasBlockAccess";
    private static final String CELERITAS_CLASS_PREFIX = "org.taumc.celeritas.";
    private static final String CELERITAS_MOD_ID = "celeritas";
    private static final String VANILLA_AIR_BLOCK = "net.minecraft.block.BlockAir";

    private static final ConcurrentMap<Class<?>, Method> ARCHITECTURE_BASE_STATE_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Method> BACKING_WORLD_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Field> BACKING_WORLD_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Field> DISCOVERED_BACKING_WORLD_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Boolean> NO_BACKING_WORLD_METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Boolean> NO_BACKING_WORLD_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Boolean> NO_DISCOVERED_BACKING_WORLD_FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static volatile Boolean celeritasInstalled;

    private FramedBlockEffectiveState() {
    }

    public static IBlockAccess wrap(IBlockAccess delegate) {
        return delegate instanceof EffectiveBlockAccess ? delegate : new EffectiveBlockAccess(delegate);
    }

    public static IBlockState state(IBlockAccess world, BlockPos pos) {
        // Celeritas copies legacy tile data into its WorldSlice. The live world
        // keeps GPOM's persisted material state, and CTM must see that state
        // for this frame and every framed neighbor.
        IBlockAccess backing = backingWorld(world);
        TileEntity tile = backing != null && backing != world
                ? tileEntity(backing, pos) : tileEntity(world, pos);
        if (tile == null && backing != null && backing != world) {
            tile = tileEntity(world, pos);
        }
        if (tile == null) {
            return null;
        }

        String className = tile.getClass().getName();
        if (BLOCKCRAFTERY_TILE_EDITABLE_BLOCK.equals(className)) {
            FramedMaterialData.MaterialStates saved = FramedMaterialData.states(tile, "blockcraftery");
            IBlockState persisted = saved.present() ? sanitized(saved.primary()) : null;
            return persisted != null ? persisted : sanitized(readBlockcrafteryState(tile));
        }
        if (ARCHITECTURE_TILE_SHAPE.equals(className)) {
            FramedMaterialData.MaterialStates saved = FramedMaterialData.states(tile, "architecturecraft");
            IBlockState persisted = saved.present() ? sanitized(saved.primary()) : null;
            return persisted != null ? persisted : sanitized(readArchitectureBaseState(tile));
        }
        return null;
    }

    public static Block blockFromState(IBlockState state) {
        if (state == null) {
            return null;
        }
        try {
            return state.getBlock();
        } catch (RuntimeException | LinkageError ignored) {
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
        return world == null || pos == null ? null : MinecraftMappingCompat.worldTileEntity(world, pos);
    }

    public static IBlockAccess backingWorld(IBlockAccess world) {
        if (world == null) {
            return null;
        }
        IBlockAccess current = world;
        for (int depth = 0; depth < 4; depth++) {
            if (current instanceof World) {
                break;
            }
            IBlockAccess next = directBackingWorld(current);
            if (next == null || next == current || next == world) {
                break;
            }
            current = next;
        }
        if (current != world) {
            return current;
        }
        IBlockAccess clientWorld = ClientAccess.world(ClientAccess.minecraft());
        return clientWorld != world ? clientWorld : null;
    }

    private static IBlockAccess directBackingWorld(IBlockAccess world) {
        IBlockAccess backing = backingWorldFromCeleritasMethod(world);
        if (backing != null) {
            return backing;
        }
        backing = backingWorldFromNamedField(world);
        if (backing != null) {
            return backing;
        }
        return backingWorldFromDiscoveredField(world);
    }

    private static IBlockAccess backingWorldFromCeleritasMethod(IBlockAccess world) {
        Class<?> type = world.getClass();
        if (!isCeleritasBlockAccess(type)) {
            return null;
        }
        if (NO_BACKING_WORLD_METHODS.containsKey(type)) {
            return null;
        }
        try {
            Method method = BACKING_WORLD_METHODS.get(type);
            if (method == null) {
                method = ReflectionLookup.findMethod(type, "getWorld", "getWorld");
                Method previous = BACKING_WORLD_METHODS.putIfAbsent(type, method);
                method = previous == null ? method : previous;
            }
            Object value = method.invoke(world);
            return value instanceof IBlockAccess && value != world ? (IBlockAccess) value : null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            NO_BACKING_WORLD_METHODS.putIfAbsent(type, Boolean.TRUE);
            return null;
        }
    }

    private static boolean isCeleritasBlockAccess(Class<?> type) {
        return celeritasInstalled() && hasCeleritasTypeName(type);
    }

    private static boolean celeritasInstalled() {
        Boolean installed = celeritasInstalled;
        if (installed != null) {
            return installed;
        }
        boolean detected;
        try {
            detected = Loader.isModLoaded(CELERITAS_MOD_ID);
        } catch (RuntimeException | LinkageError ignored) {
            detected = false;
        }
        celeritasInstalled = detected;
        return detected;
    }

    private static boolean hasCeleritasTypeName(Class<?> type) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            String name = current.getName();
            if (name.startsWith(CELERITAS_CLASS_PREFIX)) {
                return true;
            }
            for (Class<?> iface : current.getInterfaces()) {
                if (hasCeleritasInterfaceName(iface)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCeleritasInterfaceName(Class<?> type) {
        if (type == null) {
            return false;
        }
        String name = type.getName();
        if (CELERITAS_BLOCK_ACCESS.equals(name) || name.startsWith(CELERITAS_CLASS_PREFIX)) {
            return true;
        }
        for (Class<?> iface : type.getInterfaces()) {
            if (hasCeleritasInterfaceName(iface)) {
                return true;
            }
        }
        return false;
    }

    private static IBlockAccess backingWorldFromNamedField(IBlockAccess world) {
        Class<?> type = world.getClass();
        if (NO_BACKING_WORLD_FIELDS.containsKey(type)) {
            return null;
        }
        try {
            Field field = BACKING_WORLD_FIELDS.get(type);
            if (field == null) {
                field = ReflectionLookup.findField(
                        type,
                        new String[] {"world", "delegate", "parent", "wrapped"}
                );
                Field previous = BACKING_WORLD_FIELDS.putIfAbsent(type, field);
                field = previous == null ? field : previous;
            }
            Object value = field.get(world);
            return value instanceof IBlockAccess && value != world ? (IBlockAccess) value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            NO_BACKING_WORLD_FIELDS.putIfAbsent(type, Boolean.TRUE);
            return null;
        }
    }

    private static IBlockAccess backingWorldFromDiscoveredField(IBlockAccess world) {
        Class<?> type = world.getClass();
        if (NO_DISCOVERED_BACKING_WORLD_FIELDS.containsKey(type)) {
            return null;
        }
        Field field = DISCOVERED_BACKING_WORLD_FIELDS.get(type);
        if (field != null) {
            try {
                Object value = field.get(world);
                return value instanceof IBlockAccess && value != world ? (IBlockAccess) value : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                NO_DISCOVERED_BACKING_WORLD_FIELDS.putIfAbsent(type, Boolean.TRUE);
                return null;
            }
        }

        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (Field candidate : current.getDeclaredFields()) {
                int modifiers = candidate.getModifiers();
                if (Modifier.isStatic(modifiers) || !IBlockAccess.class.isAssignableFrom(candidate.getType())) {
                    continue;
                }
                try {
                    candidate.setAccessible(true);
                    Field previous = DISCOVERED_BACKING_WORLD_FIELDS.putIfAbsent(type, candidate);
                    field = previous == null ? candidate : previous;
                    Object value = field.get(world);
                    if (value instanceof IBlockAccess && value != world) {
                        return (IBlockAccess) value;
                    }
                    return null;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    // Continue through generated WorldSlice fields.
                }
            }
        }
        NO_DISCOVERED_BACKING_WORLD_FIELDS.putIfAbsent(type, Boolean.TRUE);
        return null;
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
            try {
                return delegate.getCombinedLight(pos, lightValue);
            } catch (RuntimeException | LinkageError ignored) {
                return 0;
            }
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
            try {
                return delegate.getBlockState(pos);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return func_175623_d(pos);
        }

        public boolean func_175623_d(BlockPos pos) {
            try {
                return delegate.isAirBlock(pos);
            } catch (RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        @Override
        public Biome getBiome(BlockPos pos) {
            return func_180494_b(pos);
        }

        public Biome func_180494_b(BlockPos pos) {
            try {
                return delegate.getBiome(pos);
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return func_175627_a(pos, direction);
        }

        public int func_175627_a(BlockPos pos, EnumFacing direction) {
            try {
                return delegate.getStrongPower(pos, direction);
            } catch (RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        @Override
        public WorldType getWorldType() {
            return func_175624_G();
        }

        public WorldType func_175624_G() {
            try {
                return delegate.getWorldType();
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
            IBlockState effective = state(delegate, pos);
            Block block = blockFromState(effective);
            if (block != null) {
                return block.isSideSolid(effective, this, pos, side);
            }
            try {
                return delegate.isSideSolid(pos, side, defaultValue);
            } catch (RuntimeException | LinkageError ignored) {
                return defaultValue;
            }
        }
    }
}
