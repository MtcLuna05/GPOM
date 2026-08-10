package com.l.gpom.compat.minecraft;

import com.l.gpom.GPOM;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SRG-first runtime bridge for the Alternate Current-style wire engine.
 * Hot calls use cached, exactly typed method handles to avoid reflection-array allocation.
 */
public final class AlternateCurrentCompat {
    private static final AtomicBoolean FAILURE_LOGGED = new AtomicBoolean();

    private static volatile boolean available;
    private static Block redstoneWire;
    private static Block air;
    private static IProperty<Integer> wirePower;
    private static IBlockState airState;

    private static MethodHandle posX;
    private static MethodHandle posY;
    private static MethodHandle posZ;
    private static MethodHandle worldBlockState;
    private static MethodHandle worldSetBlockState;
    private static MethodHandle worldNotifyNeighbors;
    private static MethodHandle worldNeighborPower;
    private static MethodHandle stateBlock;
    private static MethodHandle stateNormalCube;
    private static MethodHandle stateValue;
    private static MethodHandle stateWithProperty;
    private static MethodHandle stateNeighborChanged;
    private static MethodHandle blockCanPlace;
    private static MethodHandle blockDropAsItem;
    private static MethodHandle wireProvidePower;

    static {
        initialize();
    }

    private AlternateCurrentCompat() {
    }

    public static boolean isAvailable() {
        return available;
    }

    public static Block redstoneWire() {
        return redstoneWire;
    }

    public static Block air() {
        return air;
    }

    public static int x(BlockPos pos) {
        try {
            return (int) posX.invokeExact(pos);
        } catch (Throwable throwable) {
            disable("BlockPos.x", throwable);
            return 0;
        }
    }

    public static int y(BlockPos pos) {
        try {
            return (int) posY.invokeExact(pos);
        } catch (Throwable throwable) {
            disable("BlockPos.y", throwable);
            return 0;
        }
    }

    public static int z(BlockPos pos) {
        try {
            return (int) posZ.invokeExact(pos);
        } catch (Throwable throwable) {
            disable("BlockPos.z", throwable);
            return 0;
        }
    }

    public static IBlockState blockState(World world, BlockPos pos) {
        try {
            return (IBlockState) worldBlockState.invokeExact(world, pos);
        } catch (Throwable throwable) {
            disable("World.getBlockState", throwable);
            return null;
        }
    }

    public static Block block(IBlockState state) {
        if (state == null) {
            return null;
        }
        try {
            return (Block) stateBlock.invokeExact(state);
        } catch (Throwable throwable) {
            disable("IBlockState.getBlock", throwable);
            return null;
        }
    }

    public static boolean isWire(IBlockState state) {
        return state != null && block(state) == redstoneWire;
    }

    public static boolean isNormalCube(IBlockState state) {
        if (state == null) {
            return false;
        }
        try {
            return (boolean) stateNormalCube.invokeExact(state);
        } catch (Throwable throwable) {
            disable("IBlockState.isNormalCube", throwable);
            return false;
        }
    }

    public static int power(IBlockState state) {
        if (state == null) {
            return -1;
        }
        try {
            Object value = stateValue.invokeExact(state, (IProperty<?>) wirePower);
            return value instanceof Integer ? (Integer) value : -1;
        } catch (Throwable throwable) {
            disable("IBlockState.getValue(POWER)", throwable);
            return -1;
        }
    }

    public static IBlockState withPower(IBlockState state, int power) {
        if (state == null) {
            return null;
        }
        try {
            return (IBlockState) stateWithProperty.invokeExact(
                    state, (IProperty<?>) wirePower, (Comparable<?>) Integer.valueOf(power));
        } catch (Throwable throwable) {
            disable("IBlockState.withProperty(POWER)", throwable);
            return null;
        }
    }

    public static boolean setBlockState(World world, BlockPos pos, IBlockState state, int flags) {
        try {
            return (boolean) worldSetBlockState.invokeExact(world, pos, state, flags);
        } catch (Throwable throwable) {
            disable("World.setBlockState", throwable);
            return false;
        }
    }

    public static boolean notifyNeighbors(World world, BlockPos pos) {
        try {
            worldNotifyNeighbors.invokeExact(world, pos, redstoneWire, false);
            return true;
        } catch (Throwable throwable) {
            disable("World.notifyNeighborsOfStateChange", throwable);
            return false;
        }
    }

    public static boolean neighborChanged(IBlockState state, World world, BlockPos pos, BlockPos wirePos) {
        if (state == null) {
            return false;
        }
        try {
            stateNeighborChanged.invokeExact(state, world, pos, redstoneWire, wirePos);
            return true;
        } catch (Throwable throwable) {
            disable("IBlockState.neighborChanged", throwable);
            return false;
        }
    }

    public static int neighborPower(World world, BlockPos pos) {
        try {
            return (int) worldNeighborPower.invokeExact(world, pos);
        } catch (Throwable throwable) {
            disable("World.getRedstonePowerFromNeighbors", throwable);
            return -1;
        }
    }

    public static Boolean canWireStay(World world, BlockPos pos) {
        try {
            return (boolean) blockCanPlace.invokeExact(redstoneWire, world, pos);
        } catch (Throwable throwable) {
            disable("BlockRedstoneWire.canPlaceBlockAt", throwable);
            return null;
        }
    }

    public static boolean dropWire(World world, BlockPos pos, IBlockState state) {
        try {
            blockDropAsItem.invokeExact(redstoneWire, world, pos, state, 0);
            return true;
        } catch (Throwable throwable) {
            disable("Block.dropBlockAsItem", throwable);
            return false;
        }
    }

    public static boolean replaceWithAir(World world, BlockPos pos) {
        return airState != null && setBlockState(world, pos, airState, 2);
    }

    public static boolean setWireProvidesPower(boolean providesPower) {
        try {
            wireProvidePower.invokeExact((BlockRedstoneWire) redstoneWire, providesPower);
            return true;
        } catch (Throwable throwable) {
            disable("BlockRedstoneWire.canProvidePower", throwable);
            return false;
        }
    }

    public static void disableFromEngine(String operation, Throwable throwable) {
        disable(operation, throwable);
    }

    private static void initialize() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            redstoneWire = (Block) staticField(Blocks.class, "field_150488_af", "REDSTONE_WIRE").get(null);
            air = (Block) staticField(Blocks.class, "field_150350_a", "AIR").get(null);
            @SuppressWarnings("unchecked")
            IProperty<Integer> power = (IProperty<Integer>) staticField(
                    BlockRedstoneWire.class, "field_176351_O", "POWER").get(null);
            wirePower = power;

            Method getDefaultState = method(Block.class, new Class<?>[0],
                    "func_176223_P", "getDefaultState");
            airState = (IBlockState) getDefaultState.invoke(air);

            posX = getter(lookup, Vec3i.class, int.class, "field_177962_a", "x")
                    .asType(MethodType.methodType(int.class, BlockPos.class));
            posY = getter(lookup, Vec3i.class, int.class, "field_177960_b", "y")
                    .asType(MethodType.methodType(int.class, BlockPos.class));
            posZ = getter(lookup, Vec3i.class, int.class, "field_177961_c", "z")
                    .asType(MethodType.methodType(int.class, BlockPos.class));
            worldBlockState = handle(lookup, World.class, IBlockState.class,
                    new Class<?>[]{BlockPos.class}, "func_180495_p", "getBlockState");
            worldSetBlockState = handle(lookup, World.class, boolean.class,
                    new Class<?>[]{BlockPos.class, IBlockState.class, int.class},
                    "func_180501_a", "setBlockState");
            worldNotifyNeighbors = handle(lookup, World.class, void.class,
                    new Class<?>[]{BlockPos.class, Block.class, boolean.class},
                    "func_175685_c", "notifyNeighborsOfStateChange");
            worldNeighborPower = handle(lookup, World.class, int.class,
                    new Class<?>[]{BlockPos.class}, "func_175687_A", "getRedstonePowerFromNeighbors");
            stateBlock = handle(lookup, IBlockState.class, Block.class, new Class<?>[0],
                    "func_177230_c", "getBlock");
            stateNormalCube = handle(lookup, IBlockState.class, boolean.class, new Class<?>[0],
                    "func_185915_l", "isNormalCube");
            stateValue = handle(lookup, IBlockState.class, Object.class,
                    new Class<?>[]{IProperty.class}, "func_177229_b", "getValue");
            stateWithProperty = handle(lookup, IBlockState.class, IBlockState.class,
                    new Class<?>[]{IProperty.class, Comparable.class},
                    "func_177226_a", "withProperty");
            stateNeighborChanged = handle(lookup, IBlockState.class, void.class,
                    new Class<?>[]{World.class, BlockPos.class, Block.class, BlockPos.class},
                    "func_189546_a", "neighborChanged");
            blockCanPlace = handle(lookup, Block.class, boolean.class,
                    new Class<?>[]{World.class, BlockPos.class}, "func_176196_c", "canPlaceBlockAt");
            blockDropAsItem = handle(lookup, Block.class, void.class,
                    new Class<?>[]{World.class, BlockPos.class, IBlockState.class, int.class},
                    "func_176226_b", "dropBlockAsItem");
            wireProvidePower = setter(lookup, BlockRedstoneWire.class, boolean.class,
                    "field_150181_a", "canProvidePower");
            available = redstoneWire instanceof BlockRedstoneWire && air != null && wirePower != null && airState != null;
        } catch (Throwable throwable) {
            disable("initialization", throwable);
        }
    }

    private static MethodHandle handle(MethodHandles.Lookup lookup, Class<?> owner, Class<?> returnType,
                                       Class<?>[] parameters, String... names) throws ReflectiveOperationException {
        Method resolved = method(owner, parameters, names);
        Class<?>[] signature = new Class<?>[parameters.length + 1];
        signature[0] = owner;
        System.arraycopy(parameters, 0, signature, 1, parameters.length);
        return lookup.unreflect(resolved).asType(MethodType.methodType(returnType, signature));
    }

    private static MethodHandle getter(MethodHandles.Lookup lookup, Class<?> owner, Class<?> type,
                                       String... names) throws ReflectiveOperationException {
        return lookup.unreflectGetter(field(owner, type, false, names));
    }

    private static MethodHandle setter(MethodHandles.Lookup lookup, Class<?> owner, Class<?> type,
                                       String... names) throws ReflectiveOperationException {
        return lookup.unreflectSetter(field(owner, type, false, names));
    }

    private static Field staticField(Class<?> owner, String... names) throws NoSuchFieldException {
        return field(owner, null, true, names);
    }

    private static Field field(Class<?> owner, Class<?> expectedType, boolean requireStatic,
                               String... names) throws NoSuchFieldException {
        for (String name : names) {
            for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
                try {
                    Field candidate = type.getDeclaredField(name);
                    if ((expectedType == null || candidate.getType() == expectedType)
                            && (!requireStatic || Modifier.isStatic(candidate.getModifiers()))) {
                        candidate.setAccessible(true);
                        return candidate;
                    }
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        throw new NoSuchFieldException(owner.getName() + " " + java.util.Arrays.toString(names));
    }

    private static Method method(Class<?> owner, Class<?>[] parameters, String... names)
            throws NoSuchMethodException {
        for (String name : names) {
            Method found = methodInHierarchy(owner, name, parameters, new HashSet<Class<?>>());
            if (found != null) {
                found.setAccessible(true);
                return found;
            }
        }
        throw new NoSuchMethodException(owner.getName() + " " + java.util.Arrays.toString(names));
    }

    private static Method methodInHierarchy(Class<?> owner, String name, Class<?>[] parameters,
                                            Set<Class<?>> visited) {
        if (owner == null || !visited.add(owner)) {
            return null;
        }
        try {
            return owner.getDeclaredMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
        }
        for (Class<?> iface : owner.getInterfaces()) {
            Method found = methodInHierarchy(iface, name, parameters, visited);
            if (found != null) {
                return found;
            }
        }
        return methodInHierarchy(owner.getSuperclass(), name, parameters, visited);
    }

    private static void disable(String operation, Throwable throwable) {
        available = false;
        if (FAILURE_LOGGED.compareAndSet(false, true)) {
            GPOM.LOGGER.error("[GPOM Alternate Current] Disabled after compatibility failure in {}; "
                    + "vanilla redstone remains active", operation, throwable);
        }
    }
}
