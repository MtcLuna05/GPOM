package com.l.gpom.compat.blockcraftery;

import com.l.gpom.GPOM;
import com.l.gpom.compat.framed.FramedMaterialData;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact-version compatibility layer for complementary Blockcraftery framed slopes. */
public final class BlockcrafteryDoubleSlopeCompat {
    private static final String TAG = "gpom:double_slope";
    private static final String SECONDARY_STATE = "secondaryState";
    private static final String SECONDARY_STACK = "secondaryStack";
    private static final String BLOCKCRAFTERY_SLANT = "epicsquid.blockcraftery.block.BlockEditableSlant";
    private static final String BLOCKCRAFTERY_EDITABLE = "epicsquid.blockcraftery.block.IEditableBlock";
    private static final String BLOCKCRAFTERY_CONFIG = "epicsquid.blockcraftery.ConfigManager";
    private static final double HALF_EPSILON = 1.0E-5D;
    private static final AtomicInteger ACTIVATION_DIAGNOSTIC_BUDGET = new AtomicInteger(24);

    private BlockcrafteryDoubleSlopeCompat() {
    }

    public static void read(BlockcrafteryDoubleSlopeAccess access, NBTTagCompound root) {
        if (access == null || root == null || !MinecraftMappingCompat.nbtHasKey(root, TAG)) {
            if (access != null) {
                access.gpom$setDoubleSlopeData(null);
            }
            return;
        }
        NBTTagCompound data = MinecraftMappingCompat.nbtGetCompoundTag(root, TAG);
        if (MinecraftMappingCompat.nbtIsEmpty(data)
                || !MinecraftMappingCompat.nbtHasKey(data, SECONDARY_STATE)
                || FramedMaterialData.deserializeState(
                        MinecraftMappingCompat.nbtGetCompoundTag(data, SECONDARY_STATE)) == null) {
            access.gpom$setDoubleSlopeData(null);
            return;
        }
        access.gpom$setDoubleSlopeData(MinecraftMappingCompat.nbtCopy(data));
    }

    public static void write(BlockcrafteryDoubleSlopeAccess access, NBTTagCompound root) {
        if (access == null || root == null) {
            return;
        }
        NBTTagCompound data = access.gpom$getDoubleSlopeData();
        if (data == null || MinecraftMappingCompat.nbtIsEmpty(data)) {
            MinecraftMappingCompat.nbtRemoveTag(root, TAG);
        } else {
            MinecraftMappingCompat.nbtSetTag(root, TAG, MinecraftMappingCompat.nbtCopy(data));
        }
    }

    public static boolean isDoubled(Object tile) {
        return tile instanceof BlockcrafteryDoubleSlopeAccess
                && ((BlockcrafteryDoubleSlopeAccess) tile).gpom$getDoubleSlopeData() != null;
    }

    public static IBlockState secondaryState(Object tile) {
        if (!(tile instanceof BlockcrafteryDoubleSlopeAccess)) {
            return null;
        }
        NBTTagCompound data = ((BlockcrafteryDoubleSlopeAccess) tile).gpom$getDoubleSlopeData();
        if (data == null || !MinecraftMappingCompat.nbtHasKey(data, SECONDARY_STATE)) {
            return null;
        }
        return FramedMaterialData.deserializeState(
                MinecraftMappingCompat.nbtGetCompoundTag(data, SECONDARY_STATE));
    }

    public static IBlockState secondaryState(IBlockAccess world, BlockPos pos) {
        return secondaryState(MinecraftMappingCompat.worldTileEntity(world, pos));
    }

    public static IBlockState attachSecondary(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            return state;
        }
        IBlockState secondary = secondaryState(world, pos);
        if (secondary == null) {
            return state;
        }
        try {
            return ((IExtendedBlockState) state).withProperty(
                    BlockcrafteryDoubleSlopeStateProperty.INSTANCE,
                    secondary
            );
        } catch (RuntimeException | LinkageError ignored) {
            return state;
        }
    }

    public static IBlockState secondaryState(IExtendedBlockState state) {
        if (state == null) {
            return null;
        }
        try {
            Object value = state.getValue(BlockcrafteryDoubleSlopeStateProperty.INSTANCE);
            return value instanceof IBlockState ? (IBlockState) value : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    public static IBlockState complementaryState(IBlockState state) {
        Integer vert = integerProperty(state, "vert");
        Integer dir = integerProperty(state, "dir");
        if (vert == null || dir == null) {
            return state;
        }
        int complementaryVert = vert == 1 ? 1 : 2 - vert;
        int complementaryDir = vert == 1 ? (dir + 2) & 3 : dir ^ 1;

        // Rebuild both orientation axes atomically from BlockSlantBase's exact
        // metadata encoding. This keeps geometry selection independent of the
        // runtime ExtendedState implementation and cannot leave a dir-only
        // intermediate orientation behind.
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        IBlockState rebuilt = MinecraftMappingCompat.blockStateFromMeta(
                block,
                complementaryVert * 4 + complementaryDir
        );
        if (rebuilt != null
                && Integer.valueOf(complementaryVert).equals(integerProperty(rebuilt, "vert"))
                && Integer.valueOf(complementaryDir).equals(integerProperty(rebuilt, "dir"))) {
            return rebuilt;
        }

        IBlockState updated = withIntegerProperty(state, "vert", complementaryVert);
        return withIntegerProperty(updated, "dir", complementaryDir);
    }

    /** Returns null when Blockcraftery's original activation logic should continue. */
    public static Boolean activate(
            BlockcrafteryDoubleSlopeAccess access,
            World world,
            BlockPos pos,
            IBlockState hostState,
            EntityPlayer player,
            EnumHand hand,
            EnumFacing side,
            float hitX,
            float hitY,
            float hitZ
    ) {
        if (!(access instanceof TileEntity) || world == null || pos == null || hostState == null
                || player == null || isOffHand(hand) || !isSlope(hostState)) {
            return null;
        }

        ItemStack held = MinecraftMappingCompat.playerHeldItem(player, hand);
        Block heldBlock = blockFromStack(held);
        boolean remote = MinecraftMappingCompat.worldIsRemote(world);
        if (isDoubled(access) || sameSlopeBlock(hostState, heldBlock)) {
            logActivation(remote, pos, hostState, isDoubled(access), heldBlock);
        }
        if (!isDoubled(access)) {
            if (!sameSlopeBlock(hostState, heldBlock)) {
                return null;
            }
            IBlockState air = airState();
            if (air == null) {
                return Boolean.FALSE;
            }
            if (remote) {
                playPlacementSound(world, player, pos);
                return Boolean.TRUE;
            }
            NBTTagCompound data = new NBTTagCompound();
            MinecraftMappingCompat.nbtSetInteger(data, "version", 1);
            MinecraftMappingCompat.nbtSetTag(data, SECONDARY_STATE, FramedMaterialData.serializeState(air));
            access.gpom$setDoubleSlopeData(data);
            consumeOne(player, held);
            sync((TileEntity) access, world, pos, hostState);
            playPlacementSound(world, player, pos);
            return Boolean.TRUE;
        }

        boolean secondaryHalf = isSecondaryHalf(hostState, hitX, hitY, hitZ);
        if (!secondaryHalf) {
            return null;
        }

        if (MinecraftMappingCompat.playerIsSneaking(player) && MinecraftMappingCompat.itemStackIsEmpty(held)) {
            if (remote) {
                return Boolean.TRUE;
            }
            ItemStack oldMaterial = secondaryStack(access);
            IBlockState secondary = secondaryState(access);
            if (isAir(secondary)) {
                access.gpom$setDoubleSlopeData(null);
                giveOrDrop(world, pos, player, slopeFrame(hostState));
            } else {
                giveOrDrop(world, pos, player, oldMaterial);
                setSecondary(access, airState(), null);
            }
            sync((TileEntity) access, world, pos, hostState);
            return Boolean.TRUE;
        }

        // Let Blockcraftery's native handler see non-block items such as glowstone.
        if (heldBlock == null) {
            return null;
        }
        if (sameSlopeBlock(hostState, heldBlock) || isEditableBlock(heldBlock) || isAirBlock(heldBlock)) {
            return Boolean.FALSE;
        }
        IBlockState current = secondaryState(access);
        if (!isAir(current) && !configFlag("rightClickReplace")) {
            return Boolean.FALSE;
        }

        Object placed = MinecraftMappingCompat.invoke(
                heldBlock,
                "block.getStateForPlacement",
                new Class<?>[]{World.class, BlockPos.class, EnumFacing.class, float.class, float.class,
                        float.class, int.class, net.minecraft.entity.EntityLivingBase.class},
                new Object[]{world, pos, side, hitX, hitY, hitZ,
                        MinecraftMappingCompat.itemStackMetadata(held), player},
                "func_180642_a", "getStateForPlacement"
        );
        if (!(placed instanceof IBlockState) || placed == current) {
            return Boolean.FALSE;
        }

        if (remote) {
            playPlacementSound(world, player, pos);
            return Boolean.TRUE;
        }

        if (!MinecraftMappingCompat.playerIsCreative(player)
                && !configFlag("freeDecoration")) {
            giveOrDrop(world, pos, player, secondaryStack(access));
        }
        ItemStack stored = MinecraftMappingCompat.itemStackCopy(held);
        MinecraftMappingCompat.itemStackSetCount(stored, 1);
        setSecondary(access, (IBlockState) placed, configFlag("freeDecoration") ? null : stored);
        if (!MinecraftMappingCompat.playerIsCreative(player) && !configFlag("freeDecoration")) {
            MinecraftMappingCompat.itemStackShrink(held, 1);
        }
        sync((TileEntity) access, world, pos, hostState);
        playPlacementSound(world, player, pos);
        return Boolean.TRUE;
    }

    private static void logActivation(
            boolean remote,
            BlockPos pos,
            IBlockState hostState,
            boolean doubled,
            Block heldBlock
    ) {
        int remaining = ACTIVATION_DIAGNOSTIC_BUDGET.getAndDecrement();
        if (remaining <= 0) {
            return;
        }
        GPOM.LOGGER.info(
                "[GPOM Double Slope] activation side={} pos={},{},{} doubled={} held={} vert={} dir={}",
                remote ? "client" : "server",
                MinecraftMappingCompat.blockPosX(pos),
                MinecraftMappingCompat.blockPosY(pos),
                MinecraftMappingCompat.blockPosZ(pos),
                doubled,
                heldBlock == null ? "none" : heldBlock.getClass().getName(),
                integerProperty(hostState, "vert"),
                integerProperty(hostState, "dir")
        );
    }

    public static void beforeBreak(
            BlockcrafteryDoubleSlopeAccess access,
            World world,
            BlockPos pos,
            IBlockState hostState,
            EntityPlayer player
    ) {
        if (!isDoubled(access) || world == null || MinecraftMappingCompat.worldIsRemote(world)
                || (player != null && MinecraftMappingCompat.playerIsCreative(player))) {
            return;
        }
        drop(world, pos, slopeFrame(hostState));
        if (!configFlag("freeDecoration")) {
            drop(world, pos, secondaryStack(access));
        }
    }

    public static boolean addFullCollisionIfDoubled(
            IBlockAccess world,
            BlockPos pos,
            AxisAlignedBB mask,
            List<AxisAlignedBB> boxes
    ) {
        if (!isDoubled(MinecraftMappingCompat.worldTileEntity(world, pos))) {
            return false;
        }
        int x = MinecraftMappingCompat.blockPosX(pos);
        int y = MinecraftMappingCompat.blockPosY(pos);
        int z = MinecraftMappingCompat.blockPosZ(pos);
        AxisAlignedBB full = new AxisAlignedBB(x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
        if (mask == null || intersects(full, mask)) {
            boxes.add(full);
        }
        return true;
    }

    private static void setSecondary(BlockcrafteryDoubleSlopeAccess access, IBlockState state, ItemStack stack) {
        NBTTagCompound data = access.gpom$getDoubleSlopeData();
        if (data == null) {
            data = new NBTTagCompound();
            MinecraftMappingCompat.nbtSetInteger(data, "version", 1);
        }
        NBTTagCompound stateTag = FramedMaterialData.serializeState(state);
        if (stateTag != null) {
            MinecraftMappingCompat.nbtSetTag(data, SECONDARY_STATE, stateTag);
        }
        if (stack == null || MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            MinecraftMappingCompat.nbtRemoveTag(data, SECONDARY_STACK);
        } else {
            NBTTagCompound stackTag = new NBTTagCompound();
            MinecraftMappingCompat.invoke(stack, "itemStack.writeToNBT",
                    new Class<?>[]{NBTTagCompound.class}, new Object[]{stackTag},
                    "func_77955_b", "writeToNBT");
            MinecraftMappingCompat.nbtSetTag(data, SECONDARY_STACK, stackTag);
        }
        access.gpom$setDoubleSlopeData(data);
    }

    private static ItemStack secondaryStack(BlockcrafteryDoubleSlopeAccess access) {
        NBTTagCompound data = access == null ? null : access.gpom$getDoubleSlopeData();
        if (data == null || !MinecraftMappingCompat.nbtHasKey(data, SECONDARY_STACK)) {
            return MinecraftMappingCompat.emptyStack();
        }
        try {
            return new ItemStack(MinecraftMappingCompat.nbtGetCompoundTag(data, SECONDARY_STACK));
        } catch (RuntimeException | LinkageError ignored) {
            return MinecraftMappingCompat.emptyStack();
        }
    }

    private static ItemStack slopeFrame(IBlockState hostState) {
        Block block = MinecraftMappingCompat.blockStateBlock(hostState);
        Object item = MinecraftMappingCompat.invokeStatic(
                Item.class,
                "item.getItemFromBlock",
                new Class<?>[]{Block.class},
                new Object[]{block},
                "func_150898_a", "getItemFromBlock"
        );
        return item instanceof Item ? new ItemStack((Item) item) : MinecraftMappingCompat.emptyStack();
    }

    private static void consumeOne(EntityPlayer player, ItemStack held) {
        if (!MinecraftMappingCompat.playerIsCreative(player)) {
            MinecraftMappingCompat.itemStackShrink(held, 1);
        }
    }

    private static void giveOrDrop(World world, BlockPos pos, EntityPlayer player, ItemStack stack) {
        if (stack == null || MinecraftMappingCompat.itemStackIsEmpty(stack)
                || MinecraftMappingCompat.playerIsCreative(player) || MinecraftMappingCompat.worldIsRemote(world)) {
            return;
        }
        ItemStack copy = MinecraftMappingCompat.itemStackCopy(stack);
        if (!MinecraftMappingCompat.addToPlayerInventory(player, copy)) {
            drop(world, pos, copy);
        }
    }

    private static void drop(World world, BlockPos pos, ItemStack stack) {
        if (stack == null || MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return;
        }
        EntityItem entity = new EntityItem(
                world,
                MinecraftMappingCompat.blockPosX(pos) + 0.5D,
                MinecraftMappingCompat.blockPosY(pos) + 0.5D,
                MinecraftMappingCompat.blockPosZ(pos) + 0.5D,
                MinecraftMappingCompat.itemStackCopy(stack)
        );
        MinecraftMappingCompat.worldSpawnEntity(world, entity);
    }

    private static void sync(TileEntity tile, World world, BlockPos pos, IBlockState hostState) {
        MinecraftMappingCompat.tileEntityMarkDirty(tile);
        // Bit 2 sends the tile update and schedules the client render rebuild.
        // Keep bit 8 to match Blockcraftery's existing observer-update behavior.
        MinecraftMappingCompat.worldNotifyBlockUpdate(world, pos, hostState, hostState, 2 | 8);
    }

    private static void playPlacementSound(World world, EntityPlayer player, BlockPos pos) {
        Object sound = MinecraftMappingCompat.staticFieldValue(
                net.minecraft.init.SoundEvents.class,
                "soundEvents.blockStonePlace",
                "field_187620_cL",
                "BLOCK_STONE_PLACE"
        );
        if (sound instanceof SoundEvent) {
            SoundCategory category = soundCategory("BLOCKS");
            if (category != null) {
                MinecraftMappingCompat.worldPlaySound(world, player, pos, (SoundEvent) sound,
                        category, 1.0F, 1.0F);
            }
        }
    }

    private static boolean isOffHand(EnumHand hand) {
        return hand != null && "OFF_HAND".equals(hand.name());
    }

    private static SoundCategory soundCategory(String name) {
        try {
            return Enum.valueOf(SoundCategory.class, name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Block blockFromStack(ItemStack stack) {
        if (stack == null || MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return null;
        }
        Object block = MinecraftMappingCompat.invokeStatic(
                Block.class,
                "block.getBlockFromItem",
                new Class<?>[]{Item.class},
                new Object[]{MinecraftMappingCompat.itemStackItem(stack)},
                "func_149634_a", "getBlockFromItem"
        );
        return block instanceof Block ? (Block) block : null;
    }

    private static IBlockState airState() {
        Object block = MinecraftMappingCompat.staticFieldValue(
                net.minecraft.init.Blocks.class,
                "blocks.air",
                "field_150350_a",
                "AIR"
        );
        return block instanceof Block ? MinecraftMappingCompat.blockDefaultState((Block) block) : null;
    }

    private static boolean isAir(IBlockState state) {
        return state == null || isAirBlock(MinecraftMappingCompat.blockStateBlock(state));
    }

    private static boolean isAirBlock(Block block) {
        return block == null || "net.minecraft.block.BlockAir".equals(block.getClass().getName());
    }

    private static boolean sameSlopeBlock(IBlockState hostState, Block candidate) {
        Block host = MinecraftMappingCompat.blockStateBlock(hostState);
        return host != null && candidate == host && BLOCKCRAFTERY_SLANT.equals(host.getClass().getName());
    }

    private static boolean isSlope(IBlockState state) {
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        return block != null && BLOCKCRAFTERY_SLANT.equals(block.getClass().getName());
    }

    private static boolean isEditableBlock(Block block) {
        Class<?> type = block == null ? null : block.getClass();
        while (type != null) {
            for (Class<?> iface : type.getInterfaces()) {
                if (BLOCKCRAFTERY_EDITABLE.equals(iface.getName())) {
                    return true;
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private static boolean configFlag(String name) {
        try {
            Class<?> config = Class.forName(BLOCKCRAFTERY_CONFIG, false,
                    BlockcrafteryDoubleSlopeCompat.class.getClassLoader());
            Object value = MinecraftMappingCompat.staticFieldValue(config, "blockcraftery." + name, name);
            return value instanceof Boolean && (Boolean) value;
        } catch (ClassNotFoundException | LinkageError | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isSecondaryHalf(IBlockState state, double x, double y, double z) {
        Integer vert = integerProperty(state, "vert");
        Integer dir = integerProperty(state, "dir");
        if (vert == null || dir == null) {
            return false;
        }
        double primaryValue;
        if (vert == 0) {
            switch (dir) {
                case 0: primaryValue = 1.0D - y - z; break;
                case 1: primaryValue = z - y; break;
                case 2: primaryValue = 1.0D - y - x; break;
                default: primaryValue = x - y; break;
            }
        } else if (vert == 2) {
            switch (dir) {
                case 0: primaryValue = y - z; break;
                case 1: primaryValue = y + z - 1.0D; break;
                case 2: primaryValue = y - x; break;
                default: primaryValue = y + x - 1.0D; break;
            }
        } else {
            switch (dir) {
                case 0: primaryValue = 1.0D - x - z; break;
                case 1: primaryValue = x - z; break;
                case 2: primaryValue = x + z - 1.0D; break;
                default: primaryValue = z - x; break;
            }
        }
        return primaryValue < -HALF_EPSILON;
    }

    private static Integer integerProperty(IBlockState state, String name) {
        IProperty<?> property = property(state, name);
        if (property == null) {
            return null;
        }
        Object value = MinecraftMappingCompat.invoke(state, "blockState.getValue",
                new Class<?>[]{IProperty.class}, new Object[]{property},
                "func_177229_b", "getValue");
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static IBlockState withIntegerProperty(IBlockState state, String name, int value) {
        IProperty property = property(state, name);
        if (property == null) {
            return state;
        }
        Object updated = MinecraftMappingCompat.invoke(state, "blockState.withProperty",
                new Class<?>[]{IProperty.class, Comparable.class},
                new Object[]{property, Integer.valueOf(value)},
                "func_177226_a", "withProperty");
        return updated instanceof IBlockState ? (IBlockState) updated : state;
    }

    private static IProperty<?> property(IBlockState state, String wanted) {
        Object properties = MinecraftMappingCompat.invoke(state, "blockState.getProperties",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_177228_b", "getProperties");
        if (!(properties instanceof Map)) {
            return null;
        }
        for (Object raw : ((Map<?, ?>) properties).keySet()) {
            if (!(raw instanceof IProperty)) {
                continue;
            }
            Object name = MinecraftMappingCompat.invoke(raw, "property.getName",
                    MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                    "func_177701_a", "getName");
            if (wanted.equals(name)) {
                return (IProperty<?>) raw;
            }
        }
        return null;
    }

    private static boolean intersects(AxisAlignedBB first, AxisAlignedBB second) {
        return MinecraftMappingCompat.aabbMaxX(first) > MinecraftMappingCompat.aabbMinX(second)
                && MinecraftMappingCompat.aabbMinX(first) < MinecraftMappingCompat.aabbMaxX(second)
                && MinecraftMappingCompat.aabbMaxY(first) > MinecraftMappingCompat.aabbMinY(second)
                && MinecraftMappingCompat.aabbMinY(first) < MinecraftMappingCompat.aabbMaxY(second)
                && MinecraftMappingCompat.aabbMaxZ(first) > MinecraftMappingCompat.aabbMinZ(second)
                && MinecraftMappingCompat.aabbMinZ(first) < MinecraftMappingCompat.aabbMaxZ(second);
    }
}
