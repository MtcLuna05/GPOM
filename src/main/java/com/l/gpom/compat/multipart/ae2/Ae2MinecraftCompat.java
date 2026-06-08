package com.l.gpom.compat.multipart.ae2;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class Ae2MinecraftCompat {
    private static final Method NBT_SET_TAG = findMethod(NBTTagCompound.class, "setTag", "func_74782_a", String.class, NBTBase.class);
    private static final Method NBT_GET_COMPOUND = findMethod(NBTTagCompound.class, "getCompoundTag", "func_74775_l", String.class);
    private static final Method NBT_SET_INT = findMethod(NBTTagCompound.class, "setInteger", "func_74768_a", String.class, int.class);
    private static final Method NBT_GET_INT = findMethod(NBTTagCompound.class, "getInteger", "func_74762_e", String.class);
    private static final Method NBT_SET_BYTE_ARRAY = findMethod(NBTTagCompound.class, "setByteArray", "func_74773_a", String.class, byte[].class);
    private static final Method NBT_GET_BYTE_ARRAY = findMethod(NBTTagCompound.class, "getByteArray", "func_74770_j", String.class);
    private static final Method NBT_HAS_KEY = findMethod(NBTTagCompound.class, "hasKey", "func_74764_b", String.class);
    private static final Method WORLD_GET_BLOCK_STATE = findMethod(World.class, "getBlockState", "func_180495_p", BlockPos.class);
    private static final Method WORLD_GET_TILE_ENTITY = findMethod(World.class, "getTileEntity", "func_175625_s", BlockPos.class);
    private static final Method WORLD_NOTIFY_NEIGHBORS = findMethod(World.class, "notifyNeighborsOfStateChange", "func_175685_c", BlockPos.class, Block.class, boolean.class);
    private static final Method BLOCK_DEFAULT_STATE = findMethod(Block.class, "getDefaultState", "func_176223_P");
    private static final Method BLOCK_GET_SOUND_TYPE = findMethod(Block.class, "getSoundType", IBlockState.class, World.class, BlockPos.class, Entity.class);
    private static final Method BLOCK_GET_SOUND_TYPE_SIMPLE = findMethod(Block.class, "getSoundType", "func_185467_w");
    private static final Method BLOCK_STATE_GET_BLOCK = findMethod(IBlockState.class, "getBlock", "func_177230_c");
    private static final Method TILE_MARK_DIRTY = findMethod(TileEntity.class, "markDirty", "func_70296_d");
    private static final Field WORLD_IS_REMOTE = findField(World.class, "isRemote", "field_72995_K");
    private static final Method POS_X = findMethod(BlockPos.class.getSuperclass(), "getX", "func_177958_n");
    private static final Method POS_Y = findMethod(BlockPos.class.getSuperclass(), "getY", "func_177956_o");
    private static final Method POS_Z = findMethod(BlockPos.class.getSuperclass(), "getZ", "func_177952_p");
    private static final Field VEC_X = findField(Vec3d.class, "x", "field_72450_a");
    private static final Field VEC_Y = findField(Vec3d.class, "y", "field_72448_b");
    private static final Field VEC_Z = findField(Vec3d.class, "z", "field_72449_c");

    private Ae2MinecraftCompat() {
    }

    static void setTag(NBTTagCompound tag, String key, NBTBase value) {
        if (tag == null || key == null || value == null || NBT_SET_TAG == null) {
            return;
        }
        try {
            NBT_SET_TAG.invoke(tag, key, value);
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.setTag", throwable);
        }
    }

    static NBTTagCompound getCompoundTag(NBTTagCompound tag, String key) {
        if (tag == null || key == null || NBT_GET_COMPOUND == null) {
            return new NBTTagCompound();
        }
        try {
            Object value = NBT_GET_COMPOUND.invoke(tag, key);
            return value instanceof NBTTagCompound ? (NBTTagCompound) value : new NBTTagCompound();
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.getCompoundTag", throwable);
            return new NBTTagCompound();
        }
    }

    static void setInteger(NBTTagCompound tag, String key, int value) {
        if (tag == null || key == null || NBT_SET_INT == null) {
            return;
        }
        try {
            NBT_SET_INT.invoke(tag, key, value);
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.setInteger", throwable);
        }
    }

    static int getInteger(NBTTagCompound tag, String key, int fallback) {
        if (tag == null || key == null || NBT_GET_INT == null) {
            return fallback;
        }
        try {
            Object value = NBT_GET_INT.invoke(tag, key);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.getInteger", throwable);
            return fallback;
        }
    }

    static void setByteArray(NBTTagCompound tag, String key, byte[] value) {
        if (tag == null || key == null || value == null || NBT_SET_BYTE_ARRAY == null) {
            return;
        }
        try {
            NBT_SET_BYTE_ARRAY.invoke(tag, key, value);
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.setByteArray", throwable);
        }
    }

    static byte[] getByteArray(NBTTagCompound tag, String key) {
        if (tag == null || key == null || NBT_GET_BYTE_ARRAY == null) {
            return new byte[0];
        }
        try {
            Object value = NBT_GET_BYTE_ARRAY.invoke(tag, key);
            return value instanceof byte[] ? (byte[]) value : new byte[0];
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.getByteArray", throwable);
            return new byte[0];
        }
    }

    static boolean hasKey(NBTTagCompound tag, String key) {
        if (tag == null || key == null || NBT_HAS_KEY == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(NBT_HAS_KEY.invoke(tag, key));
        } catch (Throwable throwable) {
            logBridgeFailure("NBTTagCompound.hasKey", throwable);
            return false;
        }
    }

    static IBlockState getBlockState(World world, BlockPos pos) {
        if (world == null || pos == null || WORLD_GET_BLOCK_STATE == null) {
            return null;
        }
        try {
            Object value = WORLD_GET_BLOCK_STATE.invoke(world, pos);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("World.getBlockState", throwable);
            return null;
        }
    }

    static TileEntity getTileEntity(World world, BlockPos pos) {
        if (world == null || pos == null || WORLD_GET_TILE_ENTITY == null) {
            return null;
        }
        try {
            Object value = WORLD_GET_TILE_ENTITY.invoke(world, pos);
            return value instanceof TileEntity ? (TileEntity) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("World.getTileEntity", throwable);
            return null;
        }
    }

    static Block getBlock(IBlockState state) {
        if (state == null || BLOCK_STATE_GET_BLOCK == null) {
            return null;
        }
        try {
            Object value = BLOCK_STATE_GET_BLOCK.invoke(state);
            return value instanceof Block ? (Block) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("IBlockState.getBlock", throwable);
            return null;
        }
    }

    static IBlockState getDefaultState(Block block) {
        if (block == null || BLOCK_DEFAULT_STATE == null) {
            return null;
        }
        try {
            Object value = BLOCK_DEFAULT_STATE.invoke(block);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("Block.getDefaultState", throwable);
            return null;
        }
    }

    static SoundType getSoundType(Block block, IBlockState state, World world, BlockPos pos, Entity entity) {
        if (block == null) {
            return null;
        }
        try {
            if (BLOCK_GET_SOUND_TYPE != null && state != null) {
                Object value = BLOCK_GET_SOUND_TYPE.invoke(block, state, world, pos, entity);
                if (value instanceof SoundType) {
                    return (SoundType) value;
                }
            }
            if (BLOCK_GET_SOUND_TYPE_SIMPLE != null) {
                Object value = BLOCK_GET_SOUND_TYPE_SIMPLE.invoke(block);
                if (value instanceof SoundType) {
                    return (SoundType) value;
                }
            }
        } catch (Throwable throwable) {
            logBridgeFailure("Block.getSoundType", throwable);
        }
        return null;
    }

    static void notifyNeighborsOfStateChange(World world, BlockPos pos, Block block, boolean updateObservers) {
        if (world == null || pos == null || block == null || WORLD_NOTIFY_NEIGHBORS == null) {
            return;
        }
        try {
            WORLD_NOTIFY_NEIGHBORS.invoke(world, pos, block, updateObservers);
        } catch (Throwable throwable) {
            logBridgeFailure("World.notifyNeighborsOfStateChange", throwable);
        }
    }

    static void markDirty(TileEntity tile) {
        if (tile == null || TILE_MARK_DIRTY == null) {
            return;
        }
        try {
            TILE_MARK_DIRTY.invoke(tile);
        } catch (Throwable throwable) {
            logBridgeFailure("TileEntity.markDirty", throwable);
        }
    }

    static boolean isRemote(World world) {
        if (world == null || WORLD_IS_REMOTE == null) {
            return false;
        }
        try {
            return WORLD_IS_REMOTE.getBoolean(world);
        } catch (Throwable throwable) {
            logBridgeFailure("World.isRemote", throwable);
            return false;
        }
    }

    static double x(Vec3d vec) {
        return doubleField(vec, VEC_X, "Vec3d.x");
    }

    static double y(Vec3d vec) {
        return doubleField(vec, VEC_Y, "Vec3d.y");
    }

    static double z(Vec3d vec) {
        return doubleField(vec, VEC_Z, "Vec3d.z");
    }

    static int x(BlockPos pos) {
        return intMethod(pos, POS_X, "BlockPos.getX");
    }

    static int y(BlockPos pos) {
        return intMethod(pos, POS_Y, "BlockPos.getY");
    }

    static int z(BlockPos pos) {
        return intMethod(pos, POS_Z, "BlockPos.getZ");
    }

    static Vec3d hitVec(Object rayTraceResult) {
        if (rayTraceResult == null) {
            return null;
        }
        Field field = findField(rayTraceResult.getClass(), "hitVec", "field_72307_f");
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(rayTraceResult);
            return value instanceof Vec3d ? (Vec3d) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("RayTraceResult.hitVec", throwable);
            return null;
        }
    }

    static EnumFacing hitSide(Object rayTraceResult) {
        if (rayTraceResult == null) {
            return null;
        }
        Field field = findField(rayTraceResult.getClass(), "sideHit", "field_178784_b");
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(rayTraceResult);
            return value instanceof EnumFacing ? (EnumFacing) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("RayTraceResult.sideHit", throwable);
            return null;
        }
    }

    private static double doubleField(Vec3d vec, Field field, String operation) {
        if (vec == null || field == null) {
            return 0.0D;
        }
        try {
            return field.getDouble(vec);
        } catch (Throwable throwable) {
            logBridgeFailure(operation, throwable);
            return 0.0D;
        }
    }

    private static int intMethod(BlockPos pos, Method method, String operation) {
        if (pos == null || method == null) {
            return 0;
        }
        try {
            Object value = method.invoke(pos);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable throwable) {
            logBridgeFailure(operation, throwable);
            return 0;
        }
    }

    private static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(type, mcpName, parameterTypes);
        return method != null ? method : findMethod(type, srgName, parameterTypes);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        if (type == null || name == null) {
            return null;
        }
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String mcpName, String srgName) {
        Field field = findField(type, mcpName);
        return field != null ? field : findField(type, srgName);
    }

    private static Field findField(Class<?> type, String name) {
        if (type == null || name == null) {
            return null;
        }
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static void logBridgeFailure(String operation, Throwable throwable) {
        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.warn("[GPOM Multipart] {} bridge failed", operation, throwable);
        }
    }
}
