package com.l.gpom.compat.blockcraftery;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class BlockcrafteryOrphanFrameRecoveryEvents {
    private static final String TILE_EDITABLE_BLOCK = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String MATERIAL_TAG = "gpom:material_state";
    private static final ResourceLocation DEFAULT_FRAME = new ResourceLocation("blockcraftery", "editable_block");
    private static final AtomicInteger RESTORED_TOTAL = new AtomicInteger();
    private static volatile boolean registered;

    private BlockcrafteryOrphanFrameRecoveryEvents() {
    }

    public static void registerIfNeeded() {
        if (registered || !Loader.isModLoaded("blockcraftery")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new BlockcrafteryOrphanFrameRecoveryEvents());
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        World world = event.getWorld();
        if (world == null || MinecraftMappingCompat.worldIsRemote(world)) {
            return;
        }

        Chunk chunk = event.getChunk();
        Map<BlockPos, TileEntity> tileMap = MinecraftMappingCompat.chunkTileEntityMap(chunk);
        if (tileMap == null || tileMap.isEmpty()) {
            return;
        }

        Block defaultFrame = ForgeRegistries.BLOCKS.getValue(DEFAULT_FRAME);
        IBlockState defaultState = MinecraftMappingCompat.blockDefaultState(defaultFrame);
        if (defaultState == null) {
            return;
        }

        int restored = 0;
        for (Map.Entry<BlockPos, TileEntity> entry : new ArrayList<>(tileMap.entrySet())) {
            if (restoreIfOrphaned(world, entry.getKey(), entry.getValue(), defaultState)) {
                restored++;
            }
        }

        if (restored > 0) {
            int total = RESTORED_TOTAL.addAndGet(restored);
            GPOM.LOGGER.warn(
                    "[BlockcrafteryOrphanFrameRecovery] Restored {} orphaned Blockcraftery frame(s) in chunk {}; total restored this session: {}",
                    restored,
                    chunk,
                    total
            );
        }
    }

    private static boolean restoreIfOrphaned(World world, BlockPos mapPos, TileEntity tile, IBlockState defaultState) {
        if (tile == null || !TILE_EDITABLE_BLOCK.equals(tile.getClass().getName())) {
            return false;
        }

        BlockPos pos = MinecraftMappingCompat.tileEntityPos(tile);
        if (pos == null) {
            pos = mapPos;
        }
        if (pos == null) {
            return false;
        }

        IBlockState oldState = MinecraftMappingCompat.worldBlockState(world, pos);
        Block oldBlock = MinecraftMappingCompat.blockStateBlock(oldState);
        if (oldState == null || oldBlock == null || !MinecraftMappingCompat.blockIsAir(oldBlock, oldState, world, pos)) {
            return false;
        }

        NBTTagCompound oldData = MinecraftMappingCompat.tileEntityWriteToNbt(tile, new NBTTagCompound());
        if (!MinecraftMappingCompat.nbtHasKey(oldData, MATERIAL_TAG)) {
            return false;
        }

        if (!MinecraftMappingCompat.worldSetBlockState(world, pos, defaultState, 3)) {
            return false;
        }

        TileEntity restoredTile = MinecraftMappingCompat.worldTileEntity(world, pos);
        if (restoredTile == null || !TILE_EDITABLE_BLOCK.equals(restoredTile.getClass().getName())) {
            MinecraftMappingCompat.worldSetTileEntity(world, pos, tile);
            restoredTile = tile;
        }

        MinecraftMappingCompat.tileEntityReadFromNbt(restoredTile, oldData);
        MinecraftMappingCompat.tileEntityMarkDirty(restoredTile);
        MinecraftMappingCompat.worldNotifyBlockUpdate(world, pos, oldState, defaultState, 3);
        return true;
    }
}
