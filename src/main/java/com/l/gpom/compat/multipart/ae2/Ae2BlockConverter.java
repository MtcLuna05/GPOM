package com.l.gpom.compat.multipart.ae2;

import appeng.block.networking.BlockCableBus;
import appeng.tile.networking.TileCableBus;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.api.IPartConverter;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class Ae2BlockConverter implements IPartConverter {
    @Override
    public boolean canConvert(World world, BlockPos pos, IBlockState state) {
        return GpomEarlyConfig.multipartCompatAe2BlockConverterEnabled()
                && state != null
                && Ae2MinecraftCompat.getBlock(state) instanceof BlockCableBus
                && Ae2MinecraftCompat.getTileEntity(world, pos) instanceof TileCableBus;
    }

    @Override
    public TMultiPart convert(World world, BlockPos pos, IBlockState state) {
        TileEntity tile = Ae2MinecraftCompat.getTileEntity(world, pos);
        if (!(tile instanceof TileCableBus)) {
            return null;
        }

        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.info("[GPOM Multipart] Converting AE2 cable-bus block at {} into hosted multipart", pos);
        }
        return Ae2CableBusMultipart.fromCableBus((TileCableBus) tile);
    }
}
