package com.l.gpom.compat.multipart;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MultipartDescriptionPacketCompat {
    private MultipartDescriptionPacketCompat() {
    }

    public static void ensureTileAttached(TileEntity tile, World world, BlockPos pos) {
        if (tile == null || world == null || pos == null) {
            return;
        }

        if (MinecraftMappingCompat.tileEntityWorld(tile) == null) {
            MinecraftMappingCompat.tileEntitySetWorld(tile, world);
        }
        BlockPos currentPos = MinecraftMappingCompat.tileEntityPos(tile);
        if (!pos.equals(currentPos)) {
            MinecraftMappingCompat.tileEntitySetPos(tile, pos);
        }
    }
}
