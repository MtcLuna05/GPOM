package com.l.gpom.compat.pillar;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import java.lang.reflect.Method;

/** Prevents Pillar templates from forcing neighboring chunks during population. */
public final class PillarWorldgenCompat {
    private PillarWorldgenCompat() {
    }

    public static boolean canPlaceWithoutNeighborChunkGeneration(World world, BlockPos position) {
        if (world == null || position == null) {
            return false;
        }
        IChunkProvider provider = world.getChunkProvider();
        if (provider == null) {
            return false;
        }
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        for (int x = chunkX - 1; x <= chunkX + 1; x++) {
            for (int z = chunkZ - 1; z <= chunkZ + 1; z++) {
                if (!chunkExists(provider, x, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean chunkExists(IChunkProvider provider, int x, int z) {
        try {
            Method method;
            try {
                method = provider.getClass().getMethod("chunkExists", int.class, int.class);
            } catch (NoSuchMethodException ignored) {
                method = provider.getClass().getMethod("func_73149_a", int.class, int.class);
            }
            Object result = method.invoke(provider, x, z);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
