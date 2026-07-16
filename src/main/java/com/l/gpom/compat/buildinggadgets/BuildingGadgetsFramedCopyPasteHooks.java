package com.l.gpom.compat.buildinggadgets;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildingGadgetsFramedCopyPasteHooks {
    private static final String BLOCKCRAFTERY_TILE = "epicsquid.blockcraftery.tile.TileEditableBlock";
    private static final String ARCHITECTURE_TILE = "com.elytradev.architecture.common.tile.TileShape";
    private static final String TAG_TILE_DATA = "gpomFramedTileData";
    private static final String TAG_REL_POS = "relPos";
    private static final String TAG_KIND = "kind";
    private static final String TAG_TILE = "tile";
    private static final String TAG_CONSTRUCTION_TILE = "gpomArchitectureTileData";
    private static final String KIND_ARCHITECTURE = "architecturecraft";
    private static final Map<String, NBTTagCompound> PENDING_TILE_DATA = new ConcurrentHashMap<>();

    private BuildingGadgetsFramedCopyPasteHooks() {
    }

    public static TileEntity allowFramedTileCopy(World world, BlockPos pos) {
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
        return shouldCopyAsStateOnly(tile) ? null : tile;
    }

    public static void storeCopiedTileData(World world,
                                           BlockPos start,
                                           BlockPos end,
                                           ItemStack gadgetStack,
                                           EntityPlayer player,
                                           Object gadget,
                                           boolean copied) {
        if (!copied || world == null || start == null || end == null || gadgetStack == null || gadget == null) {
            return;
        }
        try {
            Object save = worldSave(world, gadget);
            String uuid = gadgetUuid(gadget, gadgetStack);
            if (uuid == null || uuid.isEmpty()) {
                return;
            }
            NBTTagCompound root = worldSaveCompound(save, uuid);
            if (root == null) {
                return;
            }

            NBTTagList list = new NBTTagList();
            int minX = Math.min(MinecraftMappingCompat.blockPosX(start), MinecraftMappingCompat.blockPosX(end));
            int minY = Math.min(MinecraftMappingCompat.blockPosY(start), MinecraftMappingCompat.blockPosY(end));
            int minZ = Math.min(MinecraftMappingCompat.blockPosZ(start), MinecraftMappingCompat.blockPosZ(end));
            int maxX = Math.max(MinecraftMappingCompat.blockPosX(start), MinecraftMappingCompat.blockPosX(end));
            int maxY = Math.max(MinecraftMappingCompat.blockPosY(start), MinecraftMappingCompat.blockPosY(end));
            int maxZ = Math.max(MinecraftMappingCompat.blockPosZ(start), MinecraftMappingCompat.blockPosZ(end));
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
                        NBTTagCompound tileTag = architectureTileData(tile);
                        if (MinecraftMappingCompat.nbtIsEmpty(tileTag)) {
                            continue;
                        }
                        NBTTagCompound entry = new NBTTagCompound();
                        MinecraftMappingCompat.nbtSetInteger(entry, TAG_REL_POS, relPosToInt(start, pos));
                        MinecraftMappingCompat.nbtSetString(entry, TAG_KIND, KIND_ARCHITECTURE);
                        MinecraftMappingCompat.nbtSetTag(entry, TAG_TILE, MinecraftMappingCompat.nbtCopy(tileTag));
                        MinecraftMappingCompat.nbtListAppend(list, entry);
                    }
                }
            }
            if (MinecraftMappingCompat.nbtListSize(list) > 0) {
                MinecraftMappingCompat.nbtSetTag(root, TAG_TILE_DATA, list);
            } else {
                MinecraftMappingCompat.nbtRemoveTag(root, TAG_TILE_DATA);
            }
            worldSaveMarkForSaving(save);
        } catch (Throwable ignored) {
        }
    }

    public static void queueTileDataForPlacement(World world, BlockPos targetPos, EntityPlayer player, Object gadget) {
        if (world == null || targetPos == null || player == null || gadget == null) {
            return;
        }
        try {
            ItemStack gadgetStack = gadgetFromPlayer(gadget, player);
            if (MinecraftMappingCompat.itemStackIsEmpty(gadgetStack)) {
                return;
            }
            NBTTagCompound stackTag = MinecraftMappingCompat.itemStackTagCompound(gadgetStack);
            if (stackTag == null) {
                return;
            }
            BlockPos anchor = posFromStackNbt(gadgetStack, "lastBuild");
            if (anchor == null) {
                return;
            }
            String uuid = MinecraftMappingCompat.nbtGetString(stackTag, "UUID");
            if (uuid.isEmpty()) {
                return;
            }
            NBTTagCompound root = worldSaveCompound(worldSave(world, gadget), uuid);
            if (root == null || !MinecraftMappingCompat.nbtHasKey(root, TAG_TILE_DATA)) {
                return;
            }
            int relPos = relPosToInt(anchor, targetPos);
            NBTTagList list = MinecraftMappingCompat.nbtGetTagList(root, TAG_TILE_DATA, 10);
            for (int i = 0; i < MinecraftMappingCompat.nbtListSize(list); i++) {
                NBTTagCompound entry = MinecraftMappingCompat.nbtListCompoundAt(list, i);
                if (MinecraftMappingCompat.nbtGetInteger(entry, TAG_REL_POS) != relPos
                        || !KIND_ARCHITECTURE.equals(MinecraftMappingCompat.nbtGetString(entry, TAG_KIND))) {
                    continue;
                }
                NBTTagCompound tileTag = MinecraftMappingCompat.nbtGetCompoundTag(entry, TAG_TILE);
                if (!MinecraftMappingCompat.nbtIsEmpty(tileTag)) {
                    PENDING_TILE_DATA.put(key(world, targetPos), MinecraftMappingCompat.nbtCopy(tileTag));
                }
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void attachPendingTileDataToConstructionTile(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        NBTTagCompound tileTag = PENDING_TILE_DATA.remove(key(world, pos));
        if (MinecraftMappingCompat.nbtIsEmpty(tileTag)) {
            return;
        }
        try {
            TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
            if (!isConstructionTile(tile)) {
                PENDING_TILE_DATA.put(key(world, pos), tileTag);
                return;
            }
            storeConstructionTileData(tile, tileTag);
            MinecraftMappingCompat.tileEntityMarkDirty(tile);
            IBlockState state = MinecraftMappingCompat.worldBlockState(world, pos);
            if (state != null) {
                MinecraftMappingCompat.worldNotifyBlockUpdate(world, pos, state, state, 3);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void preserveConstructionTileDataBeforeReplace(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return;
        }
        try {
            NBTTagCompound tileTag = constructionTileData(MinecraftMappingCompat.worldTileEntity(world, pos));
            if (!MinecraftMappingCompat.nbtIsEmpty(tileTag)) {
                PENDING_TILE_DATA.put(key(world, pos), MinecraftMappingCompat.nbtCopy(tileTag));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void applyPendingTileData(World world, BlockPos pos, IBlockState placedState) {
        if (world == null || pos == null) {
            return;
        }
        NBTTagCompound tileTag = PENDING_TILE_DATA.remove(key(world, pos));
        if (MinecraftMappingCompat.nbtIsEmpty(tileTag)) {
            return;
        }
        try {
            TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
            if (!isArchitectureTile(tile)) {
                return;
            }
            MinecraftMappingCompat.nbtSetInteger(tileTag, "x", MinecraftMappingCompat.blockPosX(pos));
            MinecraftMappingCompat.nbtSetInteger(tileTag, "y", MinecraftMappingCompat.blockPosY(pos));
            MinecraftMappingCompat.nbtSetInteger(tileTag, "z", MinecraftMappingCompat.blockPosZ(pos));
            MinecraftMappingCompat.tileEntityReadFromNbt(tile, tileTag);
            MinecraftMappingCompat.tileEntityMarkDirty(tile);
            IBlockState state = placedState != null ? placedState : MinecraftMappingCompat.worldBlockState(world, pos);
            if (state != null) {
                MinecraftMappingCompat.worldNotifyBlockUpdate(world, pos, state, state, 3);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean shouldCopyAsStateOnly(TileEntity tile) {
        if (tile == null) {
            return false;
        }
        String name = tile.getClass().getName();
        return BLOCKCRAFTERY_TILE.equals(name) || ARCHITECTURE_TILE.equals(name);
    }

    private static boolean isArchitectureTile(TileEntity tile) {
        return tile != null && ARCHITECTURE_TILE.equals(tile.getClass().getName());
    }

    private static boolean isConstructionTile(TileEntity tile) {
        return tile != null && "com.direwolf20.buildinggadgets.common.blocks.ConstructionBlockTileEntity".equals(tile.getClass().getName());
    }

    private static NBTTagCompound architectureTileData(TileEntity tile) {
        if (isArchitectureTile(tile)) {
            return MinecraftMappingCompat.tileEntityWriteToNbt(tile, new NBTTagCompound());
        }
        return constructionTileData(tile);
    }

    private static NBTTagCompound constructionTileData(TileEntity tile) {
        if (!isConstructionTile(tile)) {
            return new NBTTagCompound();
        }
        NBTTagCompound data = MinecraftMappingCompat.tileEntityCustomData(tile);
        NBTTagCompound stored = MinecraftMappingCompat.nbtGetCompoundTag(data, TAG_CONSTRUCTION_TILE);
        return MinecraftMappingCompat.nbtIsEmpty(stored) ? new NBTTagCompound() : MinecraftMappingCompat.nbtCopy(stored);
    }

    private static void storeConstructionTileData(TileEntity tile, NBTTagCompound tileTag) {
        if (!isConstructionTile(tile) || MinecraftMappingCompat.nbtIsEmpty(tileTag)) {
            return;
        }
        NBTTagCompound data = MinecraftMappingCompat.tileEntityCustomData(tile);
        MinecraftMappingCompat.nbtSetTag(data, TAG_CONSTRUCTION_TILE, MinecraftMappingCompat.nbtCopy(tileTag));
    }

    private static Object worldSave(World world, Object gadget) throws Exception {
        Class<?> type = Class.forName(worldSaveClassName(gadget), false, gadget.getClass().getClassLoader());
        Method method = type.getMethod("getWorldSave", World.class);
        return method.invoke(null, world);
    }

    private static NBTTagCompound worldSaveCompound(Object save, String uuid) throws Exception {
        if (save == null) {
            return null;
        }
        Object value = save.getClass().getMethod("getCompoundFromUUID", String.class).invoke(save, uuid);
        return value instanceof NBTTagCompound ? (NBTTagCompound) value : null;
    }

    private static void worldSaveMarkForSaving(Object save) throws Exception {
        if (save != null) {
            save.getClass().getMethod("markForSaving").invoke(save);
        }
    }

    private static String gadgetUuid(Object gadget, ItemStack stack) throws Exception {
        Object value = gadget.getClass().getMethod("getUUID", ItemStack.class).invoke(gadget, stack);
        return value instanceof String ? (String) value : "";
    }

    private static ItemStack gadgetFromPlayer(Object gadget, EntityPlayer player) throws Exception {
        Object value = gadget.getClass().getMethod("getGadget", EntityPlayer.class).invoke(null, player);
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    private static BlockPos posFromStackNbt(ItemStack stack, String key) {
        NBTTagCompound stackTag = MinecraftMappingCompat.itemStackTagCompound(stack);
        if (stackTag == null) {
            return null;
        }
        NBTTagCompound posTag = MinecraftMappingCompat.nbtGetCompoundTag(stackTag, key);
        return MinecraftMappingCompat.nbtIsEmpty(posTag) ? null : MinecraftMappingCompat.nbtUtilReadBlockPos(posTag);
    }

    private static int relPosToInt(BlockPos anchor, BlockPos pos) {
        int x = (MinecraftMappingCompat.blockPosX(pos) - MinecraftMappingCompat.blockPosX(anchor)) & 255;
        int y = (MinecraftMappingCompat.blockPosY(pos) - MinecraftMappingCompat.blockPosY(anchor)) & 255;
        int z = (MinecraftMappingCompat.blockPosZ(pos) - MinecraftMappingCompat.blockPosZ(anchor)) & 255;
        return (x << 16) + (y << 8) + z;
    }

    private static String worldSaveClassName(Object gadget) {
        String gadgetName = gadget.getClass().getName();
        int commonIndex = gadgetName.indexOf(".common.");
        String root = commonIndex > 0 ? gadgetName.substring(0, commonIndex) : "com.direwolf20.buildinggadgets";
        return root + ".common.tools.WorldSave";
    }

    private static String key(World world, BlockPos pos) {
        Integer dimension = MinecraftMappingCompat.worldDimension(world);
        int dim = dimension == null ? 0 : dimension;
        return dim + ":" + MinecraftMappingCompat.blockPosX(pos) + ":"
                + MinecraftMappingCompat.blockPosY(pos) + ":"
                + MinecraftMappingCompat.blockPosZ(pos);
    }
}
