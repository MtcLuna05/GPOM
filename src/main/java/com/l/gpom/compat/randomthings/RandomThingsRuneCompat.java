package com.l.gpom.compat.randomthings;

import com.google.common.primitives.Ints;
import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.WeakHashMap;

public final class RandomThingsRuneCompat {
    private static final String RUNE_DATA_KEY = "runeData";
    private static final String GRID_KEY = "gpomRuneGrid";
    private static final String PIXELS_KEY = "gpomRunePixels";
    private static final String OCCUPIED_KEY = "gpomRuneOccupied";
    private static final String DISCONNECTED_EDGES_KEY = "gpomRuneDisconnectedEdges";
    private static final String CONNECTION_EDGES_KEY = "gpomRuneConnectionEdges";
    private static final String PATTERN_PIXELS_KEY = "gpomRunePatternPixels";
    private static final String PATTERN_OCCUPIED_KEY = "gpomRunePatternOccupied";
    private static final String PATTERN_DISCONNECTED_EDGES_KEY = "gpomRunePatternDisconnectedEdges";
    private static final String PATTERN_CONNECTION_EDGES_KEY = "gpomRunePatternConnectionEdges";
    private static final int NORTH = 0;
    private static final int EAST = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 3;
    private static final int[] OPPOSITE = {SOUTH, WEST, NORTH, EAST};
    private static final EnumFacing[] HORIZONTAL = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST};
    private static final int RENDER_METADATA_MARKER = 1 << 8;
    private static final float CONNECTION_QUAD_Y = 0.30F / 16.0F;
    private static final float RUNE_QUAD_Y = 0.34F / 16.0F;
    private static final long MANUAL_TOGGLE_REPEAT_MS = 800L;
    private static final Map<int[][], RenderConnectionState> RENDER_CONNECTIONS = Collections.synchronizedMap(new WeakHashMap<int[][], RenderConnectionState>());
    private static final Map<String, Long> RECENT_MANUAL_TOGGLES = Collections.synchronizedMap(new HashMap<String, Long>());
    private static WeakReference<Block> runeBase = new WeakReference<>(null);
    private static WeakReference<Item> runeDust = new WeakReference<>(null);
    private static WeakReference<Item> runePattern = new WeakReference<>(null);
    private static Method syncTileEntity;
    private static Field randomThingsFlatRunes;
    private static Method playerNameMethod;
    private static volatile VertexFormat itemVertexFormat;
    private static volatile boolean openSettingsFailureLogged;

    private RandomThingsRuneCompat() {
    }

    public static String playerName(EntityPlayer player) {
        if (player == null) {
            return "null";
        }
        try {
            Method method = playerNameMethod;
            if (method == null) {
                method = findNoArgMethod(player.getClass(), "func_70005_c_", "getName");
                playerNameMethod = method;
            }
            Object value = method.invoke(player);
            return value instanceof String ? (String) value : player.getClass().getName();
        } catch (Throwable ignored) {
            return player.getClass().getName();
        }
    }

    public static boolean enabled() {
        return GpomEarlyConfig.randomThingsImprovedRunicDustEnabled() && GpomRemoteEnvironment.serverFeaturesAllowed();
    }

    public static boolean shouldUseCustomPlacement() {
        return enabled();
    }

    public static boolean shouldUseCustomRendering(int[][] runeData) {
        return hasCustomGrid(runeData) || hasEncodedConnectionBits(runeData) || hasRenderableCustomMetadata(runeData) || hasNonDefaultRenderedRuneSetting(runeData);
    }

    public static boolean shouldUseCustomExtendedState(int[][] runeData) {
        return enabled() || hasCustomGrid(runeData) || hasRenderableCustomMetadata(runeData);
    }

    public static void onConstruct(GpomRuneDataAccess access) {
        // Keep new tile entities stock-shaped until placement supplies a per-rune resolution.
    }

    public static boolean readRuneData(GpomRuneDataAccess access, NBTTagCompound compound, boolean sync) {
        Object listValue = MinecraftMappingCompat.invoke(compound, "nbt.getTagList",
                new Class<?>[]{String.class, int.class}, new Object[]{RUNE_DATA_KEY, 11},
                "func_150295_c", "getTagList");
        NBTTagList list = listValue instanceof NBTTagList ? (NBTTagList) listValue : new NBTTagList();
        Object listSizeValue = MinecraftMappingCompat.invoke(list, "nbtList.tagCount",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS, "func_74745_c", "tagCount");
        int listSize = listSizeValue instanceof Number ? ((Number) listSizeValue).intValue() : 0;
        boolean custom = MinecraftMappingCompat.nbtHasKey(compound, GRID_KEY)
                || MinecraftMappingCompat.nbtHasKey(compound, PIXELS_KEY)
                || MinecraftMappingCompat.nbtHasKey(compound, DISCONNECTED_EDGES_KEY)
                || MinecraftMappingCompat.nbtHasKey(compound, CONNECTION_EDGES_KEY)
                || listSize > 4;
        if (!enabled() && !custom) {
            return false;
        }

        if (!custom) {
            return false;
        }

        int size = MinecraftMappingCompat.nbtHasKey(compound, GRID_KEY)
                ? nbtInteger(compound, GRID_KEY)
                : (listSize > 0 ? listSize : 4);
        size = clampGridSize(size);
        int[][] data = emptyGrid(size);
        int[] connections;
        if (MinecraftMappingCompat.nbtHasKey(compound, PIXELS_KEY)) {
            connections = readCompactPixels(compound, PIXELS_KEY, OCCUPIED_KEY, data, size);
        } else {
            for (int x = 0; x < Math.min(size, listSize); x++) {
                Object rowValue = MinecraftMappingCompat.invoke(list, "nbtList.getIntArrayAt",
                        new Class<?>[]{int.class}, new Object[]{x}, "func_150306_c", "getIntArrayAt");
                int[] row = rowValue instanceof int[] ? (int[]) rowValue : new int[0];
                for (int z = 0; z < Math.min(size, row.length); z++) {
                    data[x][z] = normalizeRune(row[z]);
                }
            }
            if (MinecraftMappingCompat.nbtHasKey(compound, CONNECTION_EDGES_KEY)) {
                connections = normalizeSideArray(MinecraftMappingCompat.nbtGetIntArray(compound, CONNECTION_EDGES_KEY), size);
            } else {
                connections = legacyConnections(data, normalizeSideArray(MinecraftMappingCompat.nbtGetIntArray(compound, DISCONNECTED_EDGES_KEY), size));
            }
        }
        access.gpom$setRuneDataRaw(data);
        access.gpom$setRuneDisconnectedEdges(connections);
        access.gpom$setRuneConnectionMetadata(true);
        return true;
    }

    public static void writeRuneData(GpomRuneDataAccess access, NBTTagCompound compound, boolean sync) {
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        int[] connections = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), data.length);
        boolean custom = access.gpom$hasRuneConnectionMetadata() || data.length != 4 || hasAnySide(connections);
        if (!custom) {
            return;
        }

        MinecraftMappingCompat.invoke(compound, "nbt.setInteger",
                new Class<?>[]{String.class, int.class}, new Object[]{GRID_KEY, data.length},
                "func_74768_a", "setInteger");
        clearLegacyRuneStorage(compound);
        writeCompactPixels(compound, PIXELS_KEY, OCCUPIED_KEY, data, connections);
    }

    public static EnumActionResult onRuneDustUse(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                                float hitX, float hitY, float hitZ) {
        if (!shouldUseCustomPlacement() || facing != EnumFacing.UP) {
            return null;
        }

        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, hand);
        if (MinecraftMappingCompat.itemStackIsEmpty(stack) || !isRuneDust(MinecraftMappingCompat.itemStackItem(stack))) {
            return null;
        }

        int rune = RandomThingsRuneSettings.clampRune(MinecraftMappingCompat.itemStackMetadata(stack));
        if (MinecraftMappingCompat.playerIsSneaking(player)) {
            if (MinecraftMappingCompat.worldIsRemote(world)) {
                return openSettingsScreen(player, rune) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
            }
            return EnumActionResult.SUCCESS;
        }
        if (MinecraftMappingCompat.worldIsRemote(world)) {
            return EnumActionResult.SUCCESS;
        }

        UUID playerId = MinecraftMappingCompat.playerUniqueId(player);
        RandomThingsRuneSettings.RuneSettings settings = RandomThingsRuneSettings.forPlayer(playerId, rune);
        IBlockState targetState = MinecraftMappingCompat.worldBlockState(world, pos);
        if (targetState == null) {
            return null;
        }
        TileEntity runeTile = null;
        BlockPos runePos = pos;
        boolean created = false;
        if (isRuneBase(MinecraftMappingCompat.blockStateBlock(targetState))) {
            runeTile = MinecraftMappingCompat.worldTileEntity(world, pos);
        } else if (MinecraftMappingCompat.blockStateSideSolid(targetState, world, pos, EnumFacing.UP)) {
            BlockPos replacePos = MinecraftMappingCompat.blockPosOffset(pos, facing);
            IBlockState replaceState = MinecraftMappingCompat.worldBlockState(world, replacePos);
            Block block = MinecraftMappingCompat.blockStateBlock(replaceState);
            if (block != null && (MinecraftMappingCompat.blockIsAir(block, replaceState, world, replacePos)
                    || MinecraftMappingCompat.blockIsReplaceable(block, world, replacePos))) {
                Block runeBlock = runeBaseBlock();
                if (runeBlock == null) {
                    return null;
                }
                if (!MinecraftMappingCompat.worldIsRemote(world)) {
                    IBlockState runeState = MinecraftMappingCompat.blockDefaultState(runeBlock);
                    if (runeState == null || !MinecraftMappingCompat.worldSetBlockState(world, replacePos, runeState)) {
                        return EnumActionResult.FAIL;
                    }
                }
                runePos = replacePos;
                runeTile = MinecraftMappingCompat.worldTileEntity(world, replacePos);
                created = true;
            }
        }

        GpomRuneDataAccess access = asAccess(runeTile);
        if (access == null) {
            return null;
        }

        int fallbackSize = created ? settings.resolution : 4;
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), fallbackSize);
        if (created && data.length != settings.resolution) {
            data = emptyGrid(settings.resolution);
        }
        int[] connections = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), data.length);
        access.gpom$setRuneDataRaw(data);
        access.gpom$setRuneDisconnectedEdges(connections);
        access.gpom$setRuneConnectionMetadata(true);

        CellHit cell = cellFromHit(data.length, hitX, hitZ);
        int current = data[cell.x][cell.z];
        if (current == rune) {
            return EnumActionResult.FAIL;
        }

        int changed = paintBrush(data, connections, cell.x, cell.z, rune, settings.brush, settings.replaceOccupied, settings.autoConnect,
                MinecraftMappingCompat.playerIsCreative(player) ? Integer.MAX_VALUE : MinecraftMappingCompat.itemStackCount(stack));
        if (changed <= 0) {
            return EnumActionResult.FAIL;
        }
        syncRuneTile(runeTile);
        if (!MinecraftMappingCompat.playerIsCreative(player)) {
            MinecraftMappingCompat.itemStackShrink(stack, changed);
        }
        return EnumActionResult.SUCCESS;
    }

    public static Boolean breakSingleRunePiece(World world, BlockPos pos, EntityPlayer player) {
        if (MinecraftMappingCompat.worldIsRemote(world)) {
            return Boolean.FALSE;
        }
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
        GpomRuneDataAccess access = asAccess(tile);
        if (access == null) {
            return null;
        }
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        if (!enabled() && isDefaultGrid(data) && !hasAnySide(access.gpom$getRuneDisconnectedEdges())) {
            return null;
        }

        Vec3d start = MinecraftMappingCompat.playerPositionEyes(player, 0.0F);
        Vec3d look = MinecraftMappingCompat.playerLookVec(player);
        RayTraceResult result = start == null || look == null ? null
                : MinecraftMappingCompat.worldRayTraceBlocks(world, start, MinecraftMappingCompat.vecAdd(start, MinecraftMappingCompat.vecScale(look, 6.0D)), false, true, false);
        if (result == null || !MinecraftMappingCompat.rayTraceIsBlock(result) || !pos.equals(MinecraftMappingCompat.rayTraceBlockPos(result))) {
            return Boolean.FALSE;
        }

        Vec3d hit = MinecraftMappingCompat.vecSubtract(MinecraftMappingCompat.rayTraceHitVec(result),
                new Vec3d(MinecraftMappingCompat.blockPosX(pos), MinecraftMappingCompat.blockPosY(pos), MinecraftMappingCompat.blockPosZ(pos)));
        CellHit cell = cellFromHit(data.length, (float) MinecraftMappingCompat.vecX(hit), (float) MinecraftMappingCompat.vecZ(hit));
        int rune = data[cell.x][cell.z];
        if (rune == -1) {
            return Boolean.FALSE;
        }

        if (!MinecraftMappingCompat.playerIsCreative(player)) {
            spawnRuneDust(world, MinecraftMappingCompat.blockPosX(pos) + MinecraftMappingCompat.vecX(hit),
                    MinecraftMappingCompat.blockPosY(pos) + 0.1D, MinecraftMappingCompat.blockPosZ(pos) + MinecraftMappingCompat.vecZ(hit), rune);
        }

        data[cell.x][cell.z] = -1;
        int[] connections = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), data.length);
        clearConnectionsForRune(connections, data.length, cell.x, cell.z);
        access.gpom$setRuneDataRaw(data);
        access.gpom$setRuneDisconnectedEdges(connections);
        access.gpom$setRuneConnectionMetadata(true);
        syncRuneTile(tile);
        if (!MinecraftMappingCompat.playerIsCreative(player)) {
            MinecraftMappingCompat.worldPlaySound(world, null, pos,
                    ForgeRegistries.SOUND_EVENTS.getValue(new net.minecraft.util.ResourceLocation("minecraft", "block.stone.break")),
                    SoundCategory.BLOCKS, 1.0F, 0.8F);
        }
        if (isEmpty(data)) {
            MinecraftMappingCompat.worldSetBlockToAir(world, pos);
        }
        return Boolean.TRUE;
    }

    public static void dropCustomRunesBeforeStockBreak(World world, BlockPos pos) {
        if (MinecraftMappingCompat.worldIsRemote(world)) {
            return;
        }
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
        GpomRuneDataAccess access = asAccess(tile);
        if (access == null) {
            return;
        }
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        if (isDefaultGrid(data) && !hasAnySide(access.gpom$getRuneDisconnectedEdges())) {
            return;
        }
        if (runeDustItem() == null) {
            return;
        }
        int size = data.length;
        boolean droppedAny = false;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int rune = data[x][z];
                if (rune != -1) {
                    spawnRuneDust(world, MinecraftMappingCompat.blockPosX(pos) + (x + 0.5D) / size,
                            MinecraftMappingCompat.blockPosY(pos) + 0.1D, MinecraftMappingCompat.blockPosZ(pos) + (z + 0.5D) / size, rune);
                    data[x][z] = -1;
                    droppedAny = true;
                }
            }
        }
        if (!droppedAny) {
            return;
        }
        access.gpom$setRuneDataRaw(data);
        access.gpom$setRuneDisconnectedEdges(new int[size * size]);
        access.gpom$setRuneConnectionMetadata(true);
        syncRuneTile(tile);
    }

    @SuppressWarnings("unchecked")
    public static IBlockState getExtendedRuneState(IBlockState state, IBlockAccess world, BlockPos pos) {
        IUnlistedProperty<int[][]> runeDataProperty;
        IUnlistedProperty<boolean[]> connectionDataProperty;
        try {
            Class<?> blockRuneBase = Class.forName("lumien.randomthings.block.BlockRuneBase", false, RandomThingsRuneCompat.class.getClassLoader());
            runeDataProperty = (IUnlistedProperty<int[][]>) blockRuneBase.getField("RUNE_DATA").get(null);
            connectionDataProperty = (IUnlistedProperty<boolean[]>) blockRuneBase.getField("CONNECTION_DATA").get(null);
        } catch (Throwable ignored) {
            return state;
        }
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, pos);
        GpomRuneDataAccess access = asAccess(tile);
        IExtendedBlockState extended = (IExtendedBlockState) state;
        if (access == null) {
            int[][] empty = emptyGrid(4);
            return extended.withProperty(runeDataProperty, empty).withProperty(connectionDataProperty, new boolean[16]);
        }

        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        int[] connections = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), data.length);
        access.gpom$setRuneDataRaw(data);
        access.gpom$setRuneDisconnectedEdges(connections);
        boolean[] edgeConnections = buildEdgeConnections(world, pos, data, connections);
        int[][] renderData = encodeRenderData(data, connections, access.gpom$hasRuneConnectionMetadata() || data.length != 4 || hasAnySide(connections));
        RENDER_CONNECTIONS.put(renderData, new RenderConnectionState(connections, data.length));
        return extended.withProperty(runeDataProperty, renderData).withProperty(connectionDataProperty, edgeConnections);
    }

    public static List<BakedQuad> renderRuneModel(IBlockState state, EnumFacing side, long rand,
                                                  TextureAtlasSprite runeBaseSprite, TextureAtlasSprite runeBaseFlatSprite) {
        if (side != EnumFacing.UP || !(state instanceof IExtendedBlockState)) {
            return null;
        }
        int[][] data = findRuneData((IExtendedBlockState) state);
        boolean[] edgeConnections = findConnectionData((IExtendedBlockState) state);
        if (data == null || data.length == 0 || data[0].length == 0 || edgeConnections == null) {
            return Collections.emptyList();
        }
        if (!shouldUseCustomRendering(data)) {
            return null;
        }

        TextureAtlasSprite sprite = flatRunesEnabled() ? runeBaseFlatSprite : runeBaseSprite;
        if (sprite == null) {
            return Collections.emptyList();
        }

        int size = data.length;
        RenderConnectionState renderState = RENDER_CONNECTIONS.get(data);
        int[] disconnected = renderState != null && renderState.size == size
                ? renderState.disconnectedEdges
                : connectionsFromRenderData(data);
        List<BakedQuad> quads = new ArrayList<>();
        float cell = 1.0F / size;
        Random random = new Random(rand);
        RandomThingsRuneSettings.RuneSettings[] settingCache = new RandomThingsRuneSettings.RuneSettings[RandomThingsRuneSettings.RUNE_COUNT];

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int rune = runeColor(data[x][z]);
                if (rune == -1) {
                    continue;
                }
                float textureU = random.nextInt(8) * 2.0F;
                float textureV = random.nextInt(8) * 2.0F;
                RandomThingsRuneSettings.RuneSettings setting = settingCache[rune];
                if (setting == null) {
                    setting = RandomThingsRuneSettings.client(rune);
                    settingCache[rune] = setting;
                }
                float scale = setting.visualScale / 100.0F;
                float padding = setting.visualPadding / 100.0F;
                float piece = cell * Math.max(0.08F, Math.min(0.8F, scale));
                float inset = Math.max(0.0F, Math.min(cell * 0.46F, cell * padding));
                piece = Math.min(piece, cell - inset * 2.0F);
                if (piece <= 0.0F) {
                    piece = cell * 0.5F;
                    inset = (cell - piece) * 0.5F;
                }
                float x1 = x * cell + inset;
                float z1 = z * cell + inset;
                float x2 = x1 + piece;
                float z2 = z1 + piece;
                addQuad(quads, createQuad(x1, x2, z1, z2, RUNE_QUAD_Y, rune, sprite, textureU, textureV, 2.0F, 2.0F));
                float connectionHalf = piece * 0.5F;
                float cx = (x1 + x2) * 0.5F;
                float cz = (z1 + z2) * 0.5F;

                if (z == 0 && connects(data, disconnected, x, z, NORTH, edgeConnections)) {
                    addConnectionQuads(quads, random, cx - connectionHalf, cx + connectionHalf, z * cell - cell * 0.5F, z1, rune, rune, sprite, 1.0F, 2.0F);
                }
                if (connects(data, disconnected, x, z, EAST, edgeConnections)) {
                    int otherRune = x + 1 < size ? runeColor(data[x + 1][z]) : rune;
                    addConnectionQuads(quads, random, x2, (x + 1) * cell + cell * 0.5F, cz - connectionHalf, cz + connectionHalf, rune, otherRune, sprite, 2.0F, 1.0F);
                }
                if (connects(data, disconnected, x, z, SOUTH, edgeConnections)) {
                    int otherRune = z + 1 < size ? runeColor(data[x][z + 1]) : rune;
                    addConnectionQuads(quads, random, cx - connectionHalf, cx + connectionHalf, z2, (z + 1) * cell + cell * 0.5F, rune, otherRune, sprite, 1.0F, 2.0F);
                }
                if (x == 0 && connects(data, disconnected, x, z, WEST, edgeConnections)) {
                    addConnectionQuads(quads, random, x * cell - cell * 0.5F, x1, cz - connectionHalf, cz + connectionHalf, rune, rune, sprite, 2.0F, 1.0F);
                }
            }
        }
        return quads;
    }

    public static boolean copyRunePattern(PlayerInteractEventAccess event) {
        if (!enabled()) {
            return false;
        }
        World world = event.gpom$getWorld();
        if (world == null || MinecraftMappingCompat.worldIsRemote(world)) {
            return false;
        }
        ItemStack held = event.gpom$getHeldItem();
        Object paperValue = MinecraftMappingCompat.staticFieldValue(Items.class, "items.paper", "field_151121_aF", "PAPER");
        Item paper = paperValue instanceof Item
                ? (Item) paperValue
                : ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("minecraft", "paper"));
        if (paper == null || MinecraftMappingCompat.itemStackIsEmpty(held) || MinecraftMappingCompat.itemStackItem(held) != paper) {
            return false;
        }
        BlockPos pos = event.gpom$getPos();
        if (pos == null || !isRuneBase(MinecraftMappingCompat.blockStateBlock(MinecraftMappingCompat.worldBlockState(world, pos)))) {
            return false;
        }
        GpomRuneDataAccess access = asAccess(MinecraftMappingCompat.worldTileEntity(world, pos));
        if (access == null) {
            return false;
        }
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        int[] connections = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), data.length);
        if (isDefaultGrid(data) && !hasAnySide(connections)) {
            return false;
        }
        Item pattern = runePatternItem();
        if (pattern == null) {
            return false;
        }
        ItemStack patternStack = new ItemStack(pattern);
        NBTTagCompound patternTag = new NBTTagCompound();
        MinecraftMappingCompat.itemStackSetTagCompound(patternStack, patternTag);
        writePattern(patternTag, data, connections);
        event.gpom$allowUseItem();
        EntityPlayer player = event.gpom$getEntityPlayer();
        if (MinecraftMappingCompat.itemStackCount(held) == 1) {
            MinecraftMappingCompat.inventorySetStack(MinecraftMappingCompat.playerInventory(player),
                    event.gpom$getHand() == EnumHand.MAIN_HAND ? MinecraftMappingCompat.playerCurrentItemSlot(player) : 40,
                    patternStack);
        } else {
            MinecraftMappingCompat.itemStackShrink(held, 1);
            MinecraftMappingCompat.addToPlayerInventory(player, patternStack);
        }
        return true;
    }

    public static EnumActionResult placeRunePattern(EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing,
                                                    float hitX, float hitY, float hitZ) {
        if (!enabled()) {
            return null;
        }
        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, hand);
        NBTTagCompound tag = MinecraftMappingCompat.itemStackTagCompound(stack);
        if (MinecraftMappingCompat.itemStackIsEmpty(stack) || tag == null || !MinecraftMappingCompat.nbtHasKey(tag, GRID_KEY)) {
            return null;
        }
        IBlockState state = MinecraftMappingCompat.worldBlockState(world, pos);
        BlockPos runePos = MinecraftMappingCompat.blockPosOffset(pos, EnumFacing.UP);
        if (state == null || !MinecraftMappingCompat.worldIsAirBlock(world, runePos)
                || !MinecraftMappingCompat.blockStateSideSolid(state, world, pos, EnumFacing.UP)) {
            return EnumActionResult.FAIL;
        }
        PatternData pattern = readPattern(tag);
        if (pattern == null || isEmpty(pattern.runeData)) {
            return EnumActionResult.FAIL;
        }
        if (MinecraftMappingCompat.worldIsRemote(world)) {
            return EnumActionResult.SUCCESS;
        }
        int[][] actual = emptyGrid(pattern.runeData.length);
        boolean creative = MinecraftMappingCompat.playerIsCreative(player);
        boolean any = creative;
        for (int x = 0; x < pattern.runeData.length; x++) {
            for (int z = 0; z < pattern.runeData.length; z++) {
                int rune = pattern.runeData[x][z];
                if (rune == -1) {
                    actual[x][z] = -1;
                    continue;
                }
                if (!creative && !consumeRuneDust(player, rune, MinecraftMappingCompat.worldIsRemote(world))) {
                    actual[x][z] = -1;
                } else {
                    actual[x][z] = rune;
                    any = true;
                }
            }
        }
        if (!any) {
            return EnumActionResult.FAIL;
        }
        if (!MinecraftMappingCompat.worldIsRemote(world)) {
            Block block = runeBaseBlock();
            if (block == null) {
                return EnumActionResult.FAIL;
            }
            IBlockState runeState = MinecraftMappingCompat.blockDefaultState(block);
            if (runeState == null || !MinecraftMappingCompat.worldSetBlockState(world, runePos, runeState)) {
                return EnumActionResult.FAIL;
            }
            GpomRuneDataAccess access = asAccess(MinecraftMappingCompat.worldTileEntity(world, runePos));
            if (access != null) {
                access.gpom$setRuneDataRaw(actual);
                access.gpom$setRuneDisconnectedEdges(normalizeSideArray(pattern.connectionEdges, actual.length));
                syncRuneTile((TileEntity) access);
            }
        }
        return EnumActionResult.SUCCESS;
    }



    public static int runeAt(IBlockAccess world, BlockPos pos, float hitX, float hitZ, int fallback) {
        GpomRuneDataAccess access = asAccess(MinecraftMappingCompat.worldTileEntity(world, pos));
        if (access == null) {
            return RandomThingsRuneSettings.clampRune(fallback);
        }
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        CellHit cell = cellFromHit(data.length, hitX, hitZ);
        int rune = data[cell.x][cell.z];
        return rune == -1 ? RandomThingsRuneSettings.clampRune(fallback) : RandomThingsRuneSettings.clampRune(rune);
    }

    public static boolean isRuneBaseAt(IBlockAccess world, BlockPos pos) {
        return isRuneBase(MinecraftMappingCompat.blockStateBlock(MinecraftMappingCompat.blockAccessState(world, pos)));
    }

    public static boolean openSettingsScreen(EntityPlayer player, int rune) {
        World world = MinecraftMappingCompat.playerWorld(player);
        if (world == null || !MinecraftMappingCompat.worldIsRemote(world)) {
            return false;
        }
        try {
            Class<?> gui = Class.forName("com.l.gpom.compat.randomthings.client.RandomThingsRuneSettingsGui", true, RandomThingsRuneCompat.class.getClassLoader());
            Object opened = gui.getMethod("open", int.class).invoke(null, RandomThingsRuneSettings.clampRune(rune));
            return !(opened instanceof Boolean) || (Boolean) opened;
        } catch (Throwable throwable) {
            if (!openSettingsFailureLogged) {
                openSettingsFailureLogged = true;
                GPOM.LOGGER.warn("[GPOM RandomThings Runes] Failed to open improved runic dust screen", throwable);
            }
            return false;
        }
    }

    public static int[][] emptyGrid(int size) {
        size = clampGridSize(size);
        int[][] data = new int[size][size];
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                data[x][z] = -1;
            }
        }
        return data;
    }

    public static int[][] normalizeData(int[][] data, int fallbackSize) {
        int size = data == null || data.length == 0 ? fallbackSize : data.length;
        size = clampGridSize(size);
        if (isNormalizedGrid(data, size)) {
            return data;
        }
        int[][] normalized = emptyGrid(size);
        if (data == null) {
            return normalized;
        }
        for (int x = 0; x < Math.min(size, data.length); x++) {
            int[] row = data[x];
            if (row == null) {
                continue;
            }
            for (int z = 0; z < Math.min(size, row.length); z++) {
                normalized[x][z] = normalizeRune(row[z]);
            }
        }
        return normalized;
    }

    public interface PlayerInteractEventAccess {
        World gpom$getWorld();

        BlockPos gpom$getPos();

        ItemStack gpom$getHeldItem();

        EntityPlayer gpom$getEntityPlayer();

        EnumHand gpom$getHand();

        void gpom$allowUseItem();
    }

    private static GpomRuneDataAccess asAccess(Object tile) {
        return tile instanceof GpomRuneDataAccess ? (GpomRuneDataAccess) tile : null;
    }

    private static int clampGridSize(int size) {
        return RandomThingsRuneSettings.normalizeResolution(size);
    }

    private static int normalizeRune(int rune) {
        return rune < 0 ? -1 : Math.min(15, rune);
    }

    private static int[] normalizeSideArray(int[] raw, int gridSize) {
        if (isNormalizedSideArray(raw, gridSize)) {
            return raw;
        }
        int[] normalized = new int[gridSize * gridSize];
        if (raw == null) {
            return normalized;
        }
        for (int i = 0; i < Math.min(raw.length, normalized.length); i++) {
            normalized[i] = raw[i] & 15;
        }
        return normalized;
    }

    private static boolean isNormalizedGrid(int[][] data, int size) {
        if (data == null || data.length != size) {
            return false;
        }
        for (int x = 0; x < size; x++) {
            int[] row = data[x];
            if (row == null || row.length != size) {
                return false;
            }
            for (int z = 0; z < size; z++) {
                int rune = row[z];
                if (rune < -1 || rune > 15) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNormalizedSideArray(int[] raw, int gridSize) {
        if (raw == null || raw.length != gridSize * gridSize) {
            return false;
        }
        for (int value : raw) {
            if ((value & ~15) != 0) {
                return false;
            }
        }
        return true;
    }

    private static int nbtInteger(NBTTagCompound tag, String key) {
        Object value = MinecraftMappingCompat.invoke(tag, "nbt.getInteger",
                new Class<?>[]{String.class}, new Object[]{key}, "func_74762_e", "getInteger");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int[] readCompactPixels(NBTTagCompound tag, String pixelsKey, String occupiedKey, int[][] data, int size) {
        Object pixelsValue = MinecraftMappingCompat.invoke(tag, "nbt.getByteArray",
                new Class<?>[]{String.class}, new Object[]{pixelsKey}, "func_74770_j", "getByteArray");
        Object occupiedValue = MinecraftMappingCompat.invoke(tag, "nbt.getByteArray",
                new Class<?>[]{String.class}, new Object[]{occupiedKey}, "func_74770_j", "getByteArray");
        byte[] pixels = pixelsValue instanceof byte[] ? (byte[]) pixelsValue : new byte[0];
        byte[] occupied = occupiedValue instanceof byte[] ? (byte[]) occupiedValue : new byte[0];
        boolean hasOccupancy = occupied.length > 0;
        int[] sides = new int[size * size];
        int limit = Math.min(pixels.length, sides.length);
        for (int index = 0; index < limit; index++) {
            int value = pixels[index] & 255;
            if (hasOccupancy ? !bit(occupied, index) : value == 0) {
                continue;
            }
            int x = index % size;
            int z = index / size;
            data[x][z] = value & 15;
            sides[index] = value >>> 4 & 15;
        }
        return sides;
    }

    private static void writeCompactPixels(NBTTagCompound tag, String pixelsKey, String occupiedKey, int[][] data, int[] sides) {
        int size = data.length;
        byte[] pixels = new byte[size * size];
        byte[] occupied = new byte[(pixels.length + 7) / 8];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int rune = normalizeRune(data[x][z]);
                if (rune == -1) {
                    continue;
                }
                int index = z * size + x;
                setBit(occupied, index);
                pixels[index] = (byte) ((sides[index] & 15) << 4 | rune);
            }
        }
        MinecraftMappingCompat.invoke(tag, "nbt.setByteArray",
                new Class<?>[]{String.class, byte[].class}, new Object[]{pixelsKey, pixels},
                "func_74773_a", "setByteArray");
        MinecraftMappingCompat.invoke(tag, "nbt.setByteArray",
                new Class<?>[]{String.class, byte[].class}, new Object[]{occupiedKey, occupied},
                "func_74773_a", "setByteArray");
    }

    private static void clearLegacyRuneStorage(NBTTagCompound tag) {
        MinecraftMappingCompat.invoke(tag, "nbt.removeTag", new Class<?>[]{String.class}, new Object[]{RUNE_DATA_KEY}, "func_82580_o", "removeTag");
        MinecraftMappingCompat.invoke(tag, "nbt.removeTag", new Class<?>[]{String.class}, new Object[]{DISCONNECTED_EDGES_KEY}, "func_82580_o", "removeTag");
        MinecraftMappingCompat.invoke(tag, "nbt.removeTag", new Class<?>[]{String.class}, new Object[]{CONNECTION_EDGES_KEY}, "func_82580_o", "removeTag");
    }

    private static int[] legacyConnections(int[][] data, int[] legacyDisconnected) {
        int size = data.length;
        int[] connections = new int[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int rune = data[x][z];
                if (rune == -1) {
                    continue;
                }
                if (z > 0 && data[x][z - 1] == rune && !sideConnected(legacyDisconnected, size, x, z, NORTH)
                        && !sideConnected(legacyDisconnected, size, x, z - 1, SOUTH)) {
                    setSide(connections, size, x, z, NORTH, true);
                    setSide(connections, size, x, z - 1, SOUTH, true);
                }
                if (x < size - 1 && data[x + 1][z] == rune && !sideConnected(legacyDisconnected, size, x, z, EAST)
                        && !sideConnected(legacyDisconnected, size, x + 1, z, WEST)) {
                    setSide(connections, size, x, z, EAST, true);
                    setSide(connections, size, x + 1, z, WEST, true);
                }
                if (z < size - 1 && data[x][z + 1] == rune && !sideConnected(legacyDisconnected, size, x, z, SOUTH)
                        && !sideConnected(legacyDisconnected, size, x, z + 1, NORTH)) {
                    setSide(connections, size, x, z, SOUTH, true);
                    setSide(connections, size, x, z + 1, NORTH, true);
                }
                if (x > 0 && data[x - 1][z] == rune && !sideConnected(legacyDisconnected, size, x, z, WEST)
                        && !sideConnected(legacyDisconnected, size, x - 1, z, EAST)) {
                    setSide(connections, size, x, z, WEST, true);
                    setSide(connections, size, x - 1, z, EAST, true);
                }
            }
        }
        return connections;
    }

    private static boolean bit(byte[] bytes, int index) {
        int byteIndex = index >> 3;
        return byteIndex >= 0 && byteIndex < bytes.length && (bytes[byteIndex] & 1 << (index & 7)) != 0;
    }

    private static void setBit(byte[] bytes, int index) {
        int byteIndex = index >> 3;
        if (byteIndex >= 0 && byteIndex < bytes.length) {
            bytes[byteIndex] = (byte) (bytes[byteIndex] | 1 << (index & 7));
        }
    }

    private static boolean isDefaultGrid(int[][] data) {
        return data != null && data.length == 4 && data[0] != null && data[0].length == 4;
    }

    private static boolean hasCustomGrid(int[][] data) {
        return data != null && data.length != 4;
    }

    private static int paintBrush(int[][] data, int[] connections, int centerX, int centerZ, int rune, int brush,
                                  boolean replaceOccupied, boolean autoConnect, int available) {
        int size = data.length;
        int radiusBefore = (brush - 1) / 2;
        int radiusAfter = brush / 2;
        int changed = 0;
        for (int x = Math.max(0, centerX - radiusBefore); x <= Math.min(size - 1, centerX + radiusAfter); x++) {
            for (int z = Math.max(0, centerZ - radiusBefore); z <= Math.min(size - 1, centerZ + radiusAfter); z++) {
                if (changed >= available) {
                    return changed;
                }
                int current = data[x][z];
                if (current == -1 || replaceOccupied && current != rune) {
                    data[x][z] = rune;
                    clearConnectionsForRune(connections, size, x, z);
                    if (autoConnect) {
                        connectSameColorNeighbors(data, connections, size, x, z, rune);
                    }
                    changed++;
                }
            }
        }
        return changed;
    }

    private static boolean hasNonDefaultRenderedRuneSetting(int[][] data) {
        if (!enabled() || data == null) {
            return false;
        }
        boolean[] checked = new boolean[RandomThingsRuneSettings.RUNE_COUNT];
        for (int[] row : data) {
            if (row == null) {
                continue;
            }
            for (int rawRune : row) {
                int rune = runeColor(rawRune);
                if (rune < 0 || rune >= checked.length || checked[rune]) {
                    continue;
                }
                checked[rune] = true;
                if (!RandomThingsRuneSettings.isDefault(RandomThingsRuneSettings.client(rune))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasRenderableCustomMetadata(int[][] data) {
        RenderConnectionState state = data == null ? null : RENDER_CONNECTIONS.get(data);
        return state != null && hasAnySide(state.disconnectedEdges);
    }

    private static boolean hasEncodedConnectionBits(int[][] data) {
        if (data == null) {
            return false;
        }
        for (int[] row : data) {
            if (row == null) {
                continue;
            }
            for (int value : row) {
                if (value >= 0 && (value & ~15) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAnySide(int[] sides) {
        if (sides == null) {
            return false;
        }
        for (int value : sides) {
            if ((value & 15) != 0) {
                return true;
            }
        }
        return false;
    }

    private static CellHit cellFromHit(int size, float hitX, float hitZ) {
        float xHit = Math.max(0.0F, Math.min(0.999999F, hitX));
        float zHit = Math.max(0.0F, Math.min(0.999999F, hitZ));
        int x = Math.max(0, Math.min(size - 1, (int) Math.floor(xHit * size)));
        int z = Math.max(0, Math.min(size - 1, (int) Math.floor(zHit * size)));
        float localX = xHit * size - x;
        float localZ = zHit * size - z;
        return new CellHit(x, z, localX, localZ);
    }

    public static EnumActionResult toggleConnectionWithEmptyHand(EntityPlayer player, World world, BlockPos pos, EnumHand hand,
                                                                 float hitX, float hitZ) {
        if (!enabled() || player == null || pos == null) {
            return null;
        }
        ItemStack held = MinecraftMappingCompat.playerHeldItem(player, hand);
        if (!MinecraftMappingCompat.itemStackIsEmpty(held)) {
            return null;
        }
        BlockPos runePos = pos;
        Block blockAtPos = MinecraftMappingCompat.blockStateBlock(MinecraftMappingCompat.worldBlockState(world, runePos));
        if (!isRuneBase(blockAtPos)) {
            BlockPos above = MinecraftMappingCompat.blockPosOffset(pos, EnumFacing.UP);
            Block blockAbove = above == null ? null : MinecraftMappingCompat.blockStateBlock(MinecraftMappingCompat.worldBlockState(world, above));
            if (above == null || !isRuneBase(blockAbove)) {
                return null;
            }
            runePos = above;
        }
        if (MinecraftMappingCompat.worldIsRemote(world)) {
            return EnumActionResult.SUCCESS;
        }
        TileEntity tile = MinecraftMappingCompat.worldTileEntity(world, runePos);
        GpomRuneDataAccess access = asAccess(tile);
        if (access == null) {
            return null;
        }
        int[][] data = normalizeData(access.gpom$getRuneDataRaw(), 4);
        int[] connections = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), data.length);
        access.gpom$setRuneDataRaw(data);
        access.gpom$setRuneDisconnectedEdges(connections);
        CellHit cell = cellFromHit(data.length, hitX, hitZ);
        boolean toggled = toggleNearestConnection(player, world, runePos, data, connections, cell, tile);
        return toggled ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    private static boolean toggleNearestConnection(EntityPlayer player, World world, BlockPos pos, int[][] data, int[] connections,
                                                   CellHit cell, TileEntity tile) {
        int rune = data[cell.x][cell.z];
        if (rune == -1) {
            return false;
        }
        int[] order = edgeOrder(cell.localX, cell.localZ);
        for (int direction : order) {
            Neighbor neighbor = neighbor(world, pos, data.length, cell.x, cell.z, direction);
            if (neighbor == null || neighbor.data[neighbor.x][neighbor.z] == -1) {
                continue;
            }
            boolean localBefore = sideConnected(connections, data.length, cell.x, cell.z, direction);
            boolean neighborBefore = sideConnected(neighbor.disconnected, neighbor.data.length, neighbor.x, neighbor.z, OPPOSITE[direction]);
            boolean currentlyConnected = localBefore && neighborBefore;
            boolean connect = !currentlyConnected;
            if (isRepeatedManualToggle(player, pos, cell.x, cell.z, MinecraftMappingCompat.tileEntityPos(neighbor.tile), neighbor.x, neighbor.z)) {
                return true;
            }
            setSide(connections, data.length, cell.x, cell.z, direction, connect);
            setSide(neighbor.disconnected, neighbor.data.length, neighbor.x, neighbor.z, OPPOSITE[direction], connect);
            GpomRuneDataAccess localAccess = asAccess(tile);
            if (localAccess != null) {
                localAccess.gpom$setRuneConnectionMetadata(true);
            }
            GpomRuneDataAccess neighborAccess = asAccess(neighbor.tile);
            if (neighborAccess != null) {
                neighborAccess.gpom$setRuneConnectionMetadata(true);
            }
            syncRuneTile(tile);
            if (neighbor.tile != tile) {
                syncRuneTile(neighbor.tile);
            }
            return true;
        }
        return false;
    }

    private static boolean isRepeatedManualToggle(EntityPlayer player, BlockPos pos, int x, int z, BlockPos neighborPos, int neighborX, int neighborZ) {
        String key = manualToggleKey(player, pos, x, z, neighborPos, neighborX, neighborZ);
        long now = System.currentTimeMillis();
        synchronized (RECENT_MANUAL_TOGGLES) {
            if (RECENT_MANUAL_TOGGLES.size() > 128) {
                RECENT_MANUAL_TOGGLES.entrySet().removeIf(entry -> now - entry.getValue() > MANUAL_TOGGLE_REPEAT_MS);
            }
            Long previous = RECENT_MANUAL_TOGGLES.get(key);
            if (previous != null && now - previous < MANUAL_TOGGLE_REPEAT_MS) {
                RECENT_MANUAL_TOGGLES.put(key, now);
                return true;
            }
            RECENT_MANUAL_TOGGLES.put(key, now);
            return false;
        }
    }

    private static String manualToggleKey(EntityPlayer player, BlockPos pos, int x, int z, BlockPos neighborPos, int neighborX, int neighborZ) {
        UUID id = MinecraftMappingCompat.playerUniqueId(player);
        String playerKey = id == null ? playerName(player) : id.toString();
        String a = cellKey(pos, x, z);
        String b = cellKey(neighborPos, neighborX, neighborZ);
        return playerKey + '|' + (a.compareTo(b) <= 0 ? a + '|' + b : b + '|' + a);
    }

    private static String cellKey(BlockPos pos, int x, int z) {
        if (pos == null) {
            return "null:" + x + ',' + z;
        }
        return MinecraftMappingCompat.blockPosX(pos) + "," + MinecraftMappingCompat.blockPosY(pos) + "," + MinecraftMappingCompat.blockPosZ(pos) + ':' + x + ',' + z;
    }

    private static int[] edgeOrder(float localX, float localZ) {
        float north = localZ;
        float east = 1.0F - localX;
        float south = 1.0F - localZ;
        float west = localX;
        int[] directions = {NORTH, EAST, SOUTH, WEST};
        float[] distances = {north, east, south, west};
        for (int i = 0; i < directions.length - 1; i++) {
            for (int j = i + 1; j < directions.length; j++) {
                if (distances[j] < distances[i]) {
                    float df = distances[i];
                    distances[i] = distances[j];
                    distances[j] = df;
                    int di = directions[i];
                    directions[i] = directions[j];
                    directions[j] = di;
                }
            }
        }
        return directions;
    }

    private static Neighbor neighbor(World world, BlockPos pos, int size, int x, int z, int direction) {
        int nx = x;
        int nz = z;
        BlockPos npos = pos;
        if (direction == NORTH) {
            nz--;
            if (nz < 0) {
                npos = MinecraftMappingCompat.blockPosOffset(pos, EnumFacing.NORTH);
                nz = size - 1;
            }
        } else if (direction == EAST) {
            nx++;
            if (nx >= size) {
                npos = MinecraftMappingCompat.blockPosOffset(pos, EnumFacing.EAST);
                nx = 0;
            }
        } else if (direction == SOUTH) {
            nz++;
            if (nz >= size) {
                npos = MinecraftMappingCompat.blockPosOffset(pos, EnumFacing.SOUTH);
                nz = 0;
            }
        } else if (direction == WEST) {
            nx--;
            if (nx < 0) {
                npos = MinecraftMappingCompat.blockPosOffset(pos, EnumFacing.WEST);
                nx = size - 1;
            }
        }
        TileEntity neighborTile = MinecraftMappingCompat.worldTileEntity(world, npos);
        GpomRuneDataAccess access = asAccess(neighborTile);
        if (access == null) {
            return null;
        }
        int[][] neighborData = normalizeData(access.gpom$getRuneDataRaw(), size);
        int[] neighborDisconnected = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), neighborData.length);
        access.gpom$setRuneDataRaw(neighborData);
        access.gpom$setRuneDisconnectedEdges(neighborDisconnected);
        if (nx >= neighborData.length || nz >= neighborData.length) {
            return null;
        }
        return new Neighbor(neighborTile, neighborData, neighborDisconnected, nx, nz);
    }

    private static void setSide(int[] sides, int size, int x, int z, int direction, boolean connected) {
        int index = z * size + x;
        if (index < 0 || index >= sides.length) {
            return;
        }
        if (connected) {
            sides[index] |= 1 << direction;
        } else {
            sides[index] &= ~(1 << direction);
        }
    }

    private static boolean sideConnected(int[] disconnected, int size, int x, int z, int direction) {
        int index = z * size + x;
        return index >= 0 && index < disconnected.length && (disconnected[index] & (1 << direction)) != 0;
    }

    private static int[][] encodeRenderData(int[][] data, int[] connections, boolean hasMetadata) {
        int size = data.length;
        int[][] encoded = emptyGrid(size);
        int[] normalizedConnections = normalizeSideArray(connections, size);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int rune = normalizeRune(data[x][z]);
                if (rune == -1) {
                    encoded[x][z] = -1;
                } else {
                    encoded[x][z] = (hasMetadata ? RENDER_METADATA_MARKER : 0) | (normalizedConnections[z * size + x] & 15) << 4 | rune;
                }
            }
        }
        return encoded;
    }

    private static int[] connectionsFromRenderData(int[][] data) {
        int size = data.length;
        int[] connections = new int[size * size];
        for (int x = 0; x < size; x++) {
            int[] row = data[x];
            if (row == null) {
                continue;
            }
            for (int z = 0; z < Math.min(size, row.length); z++) {
                int value = row[z];
                if (value >= 0) {
                    connections[z * size + x] = value >>> 4 & 15;
                }
            }
        }
        return connections;
    }

    private static int runeColor(int encodedRune) {
        return encodedRune < 0 ? -1 : encodedRune & 15;
    }

    private static Method findNoArgMethod(Class<?> owner, String... names) throws NoSuchMethodException {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
                try {
                    Method method = current.getDeclaredMethod(name);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(owner.getName() + "." + names[0]);
    }

    private static void clearConnectionsForRune(int[] connections, int size, int x, int z) {
        if (connections == null || connections.length != size * size) {
            return;
        }
        connections[z * size + x] = 0;
        for (int direction = 0; direction < 4; direction++) {
            int nx = x + (direction == EAST ? 1 : (direction == WEST ? -1 : 0));
            int nz = z + (direction == SOUTH ? 1 : (direction == NORTH ? -1 : 0));
            if (nx >= 0 && nx < size && nz >= 0 && nz < size) {
                connections[nz * size + nx] &= ~(1 << OPPOSITE[direction]);
            }
        }
    }

    private static void connectSameColorNeighbors(int[][] data, int[] connections, int size, int x, int z, int rune) {
        for (int direction = 0; direction < 4; direction++) {
            int nx = x + (direction == EAST ? 1 : (direction == WEST ? -1 : 0));
            int nz = z + (direction == SOUTH ? 1 : (direction == NORTH ? -1 : 0));
            if (nx >= 0 && nx < size && nz >= 0 && nz < size && data[nx][nz] == rune) {
                setSide(connections, size, x, z, direction, true);
                setSide(connections, size, nx, nz, OPPOSITE[direction], true);
            }
        }
    }

    private static void spawnRuneDust(World world, double x, double y, double z, int rune) {
        Item dust = runeDustItem();
        if (dust == null) {
            return;
        }
        EntityItem entity = new EntityItem(world, x, y, z, new ItemStack(dust, 1, rune));
        MinecraftMappingCompat.entityItemSetDefaultPickupDelay(entity);
        MinecraftMappingCompat.worldSpawnEntity(world, entity);
    }

    private static boolean[] buildEdgeConnections(IBlockAccess world, BlockPos pos, int[][] data, int[] sides) {
        boolean[] edgeConnections = new boolean[data.length * 4];
        int size = data.length;
        for (int direction = 0; direction < HORIZONTAL.length; direction++) {
            BlockPos neighborPos = MinecraftMappingCompat.blockPosOffset(pos, HORIZONTAL[direction]);
            GpomRuneDataAccess access = asAccess(MinecraftMappingCompat.worldTileEntity(world, neighborPos));
            if (access == null) {
                continue;
            }
            int[][] other = normalizeData(access.gpom$getRuneDataRaw(), size);
            int[] otherSides = normalizeSideArray(access.gpom$getRuneDisconnectedEdges(), other.length);
            for (int i = 0; i < size; i++) {
                boolean connected = false;
                if (other.length == size) {
                    if (direction == NORTH) {
                        connected = data[i][0] != -1 && other[i][size - 1] != -1
                                && sideConnected(sides, size, i, 0, NORTH)
                                && sideConnected(otherSides, size, i, size - 1, SOUTH);
                    } else if (direction == EAST) {
                        connected = data[size - 1][i] != -1 && other[0][i] != -1
                                && sideConnected(sides, size, size - 1, i, EAST)
                                && sideConnected(otherSides, size, 0, i, WEST);
                    } else if (direction == SOUTH) {
                        connected = data[i][size - 1] != -1 && other[i][0] != -1
                                && sideConnected(sides, size, i, size - 1, SOUTH)
                                && sideConnected(otherSides, size, i, 0, NORTH);
                    } else if (direction == WEST) {
                        connected = data[0][i] != -1 && other[size - 1][i] != -1
                                && sideConnected(sides, size, 0, i, WEST)
                                && sideConnected(otherSides, size, size - 1, i, EAST);
                    }
                }
                edgeConnections[direction * size + i] = connected;
            }
        }
        return edgeConnections;
    }

    private static boolean connects(int[][] data, int[] sides, int x, int z, int direction, boolean[] edgeConnections) {
        int size = data.length;
        int rune = runeColor(data[x][z]);
        if (rune == -1 || !sideConnected(sides, size, x, z, direction)) {
            return false;
        }
        if (direction == NORTH) {
            if (z == 0) {
                return x < edgeConnections.length && edgeConnections[x];
            }
            return runeColor(data[x][z - 1]) != -1 && sideConnected(sides, size, x, z - 1, SOUTH);
        }
        if (direction == EAST) {
            if (x == size - 1) {
                int index = size + z;
                return index < edgeConnections.length && edgeConnections[index];
            }
            return runeColor(data[x + 1][z]) != -1 && sideConnected(sides, size, x + 1, z, WEST);
        }
        if (direction == SOUTH) {
            if (z == size - 1) {
                int index = size * 2 + x;
                return index < edgeConnections.length && edgeConnections[index];
            }
            return runeColor(data[x][z + 1]) != -1 && sideConnected(sides, size, x, z + 1, NORTH);
        }
        if (direction == WEST) {
            if (x == 0) {
                int index = size * 3 + z;
                return index < edgeConnections.length && edgeConnections[index];
            }
            return runeColor(data[x - 1][z]) != -1 && sideConnected(sides, size, x - 1, z, EAST);
        }
        return false;
    }

    private static void addConnectionQuads(List<BakedQuad> quads, Random random, float x1, float x2, float z1, float z2,
                                           int tintIndex, int otherTintIndex, TextureAtlasSprite sprite, float uSize, float vSize) {
        if (otherTintIndex < 0 || otherTintIndex == tintIndex) {
            addQuad(quads, createRandomizedConnectionQuad(random, x1, x2, z1, z2, tintIndex, sprite, uSize, vSize));
            return;
        }
        float u = random.nextInt(8) * 2.0F;
        float v = random.nextInt(8) * 2.0F;
        if (Math.abs(x2 - x1) >= Math.abs(z2 - z1)) {
            float middle = (x1 + x2) * 0.5F;
            addQuad(quads, createQuad(x1, middle, z1, z2, CONNECTION_QUAD_Y, tintIndex, sprite, u, v, uSize * 0.5F, vSize));
            addQuad(quads, createQuad(middle, x2, z1, z2, CONNECTION_QUAD_Y, otherTintIndex, sprite, u + uSize * 0.5F, v, uSize * 0.5F, vSize));
        } else {
            float middle = (z1 + z2) * 0.5F;
            addQuad(quads, createQuad(x1, x2, z1, middle, CONNECTION_QUAD_Y, tintIndex, sprite, u, v, uSize, vSize * 0.5F));
            addQuad(quads, createQuad(x1, x2, middle, z2, CONNECTION_QUAD_Y, otherTintIndex, sprite, u, v + vSize * 0.5F, uSize, vSize * 0.5F));
        }
    }

    private static BakedQuad createRandomizedConnectionQuad(Random random, float x1, float x2, float z1, float z2,
                                                            int tintIndex, TextureAtlasSprite sprite, float uSize, float vSize) {
        float u = random.nextInt(8) * 2.0F;
        float v = random.nextInt(8) * 2.0F;
        return createQuad(x1, x2, z1, z2, CONNECTION_QUAD_Y, tintIndex, sprite, u, v, uSize, vSize);
    }

    private static void addQuad(List<BakedQuad> quads, BakedQuad quad) {
        if (quad != null) {
            quads.add(quad);
        }
    }

    private static BakedQuad createQuad(float x1, float x2, float z1, float z2, float y, int tintIndex,
                                        TextureAtlasSprite sprite, float u, float v, float uSize, float vSize) {
        VertexFormat format = itemVertexFormat();
        if (format == null) {
            return null;
        }
        float u1 = u;
        float v1 = v;
        float u2 = u + uSize;
        float v2 = v + vSize;
        return new BakedQuad(Ints.concat(
                vertexToInts(x1, y, z1, -1, sprite, u1, v1),
                vertexToInts(x1, y, z2, -1, sprite, u1, v2),
                vertexToInts(x2, y, z2, -1, sprite, u2, v2),
                vertexToInts(x2, y, z1, -1, sprite, u2, v1)
        ), tintIndex, EnumFacing.UP, sprite, false, format);
    }

    private static VertexFormat itemVertexFormat() {
        VertexFormat cached = itemVertexFormat;
        if (cached != null) {
            return cached;
        }
        try {
            Class<?> formats = Class.forName("net.minecraft.client.renderer.vertex.DefaultVertexFormats", false,
                    RandomThingsRuneCompat.class.getClassLoader());
            for (String name : new String[]{"field_176599_b", "ITEM"}) {
                try {
                    Field field = formats.getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(null);
                    if (value instanceof VertexFormat) {
                        itemVertexFormat = (VertexFormat) value;
                        return itemVertexFormat;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try the other runtime mapping name.
                }
            }
        } catch (Throwable throwable) {
            if (!openSettingsFailureLogged) {
                openSettingsFailureLogged = true;
                GPOM.LOGGER.warn("[GPOM RandomThings] Could not resolve DefaultVertexFormats.ITEM for rune rendering", throwable);
            }
        }
        return null;
    }

    private static int[] vertexToInts(float x, float y, float z, int color, TextureAtlasSprite sprite, float u, float v) {
        int normal = 127 << 8;
        return new int[]{
                Float.floatToRawIntBits(x),
                Float.floatToRawIntBits(y),
                Float.floatToRawIntBits(z),
                color,
                Float.floatToRawIntBits(MinecraftMappingCompat.textureInterpolatedU(sprite, u)),
                Float.floatToRawIntBits(MinecraftMappingCompat.textureInterpolatedV(sprite, v)),
                normal
        };
    }

    private static boolean flatRunesEnabled() {
        try {
            if (randomThingsFlatRunes == null) {
                Class<?> visual = Class.forName("lumien.randomthings.config.Visual", false, RandomThingsRuneCompat.class.getClassLoader());
                randomThingsFlatRunes = visual.getField("FLAT_RUNES");
            }
            return randomThingsFlatRunes.getBoolean(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static int[][] findRuneData(IExtendedBlockState state) {
        for (Map.Entry<IUnlistedProperty<?>, java.util.Optional<?>> entry : state.getUnlistedProperties().entrySet()) {
            if (entry.getValue().isPresent() && entry.getValue().get() instanceof int[][]) {
                return (int[][]) entry.getValue().get();
            }
        }
        return null;
    }

    private static boolean[] findConnectionData(IExtendedBlockState state) {
        for (Map.Entry<IUnlistedProperty<?>, java.util.Optional<?>> entry : state.getUnlistedProperties().entrySet()) {
            if (entry.getValue().isPresent() && entry.getValue().get() instanceof boolean[]) {
                return (boolean[]) entry.getValue().get();
            }
        }
        return null;
    }

    private static boolean consumeRuneDust(EntityPlayer player, int rune, boolean simulate) {
        NonNullList<ItemStack> inventory = MinecraftMappingCompat.playerMainInventory(player);
        if (inventory == null) {
            return false;
        }
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (!MinecraftMappingCompat.itemStackIsEmpty(stack)
                    && isRuneDust(MinecraftMappingCompat.itemStackItem(stack))
                    && MinecraftMappingCompat.itemStackMetadata(stack) == rune) {
                if (!simulate) {
                    MinecraftMappingCompat.itemStackShrink(stack, 1);
                }
                return true;
            }
        }
        return false;
    }

    private static void writePattern(NBTTagCompound tag, int[][] data, int[] connections) {
        int size = data.length;
        MinecraftMappingCompat.invoke(tag, "nbt.setInteger",
                new Class<?>[]{String.class, int.class}, new Object[]{GRID_KEY, size},
                "func_74768_a", "setInteger");
        writeCompactPixels(tag, PATTERN_PIXELS_KEY, PATTERN_OCCUPIED_KEY, data, normalizeSideArray(connections, size));
    }

    private static PatternData readPattern(NBTTagCompound tag) {
        int size = MinecraftMappingCompat.nbtHasKey(tag, GRID_KEY) ? clampGridSize(nbtInteger(tag, GRID_KEY)) : 4;
        int[][] data = emptyGrid(size);
        int[] connections;
        if (MinecraftMappingCompat.nbtHasKey(tag, PATTERN_PIXELS_KEY)) {
            connections = readCompactPixels(tag, PATTERN_PIXELS_KEY, PATTERN_OCCUPIED_KEY, data, size);
        } else {
            int[] flattened = MinecraftMappingCompat.nbtGetIntArray(tag, RUNE_DATA_KEY);
            if (flattened.length == 0) {
                return null;
            }
            for (int i = 0; i < Math.min(flattened.length, size * size); i++) {
                int x = i % size;
                int z = i / size;
                data[x][z] = normalizeRune(flattened[i]);
            }
            if (MinecraftMappingCompat.nbtHasKey(tag, PATTERN_CONNECTION_EDGES_KEY)) {
                connections = normalizeSideArray(MinecraftMappingCompat.nbtGetIntArray(tag, PATTERN_CONNECTION_EDGES_KEY), size);
            } else {
                connections = legacyConnections(data, normalizeSideArray(MinecraftMappingCompat.nbtGetIntArray(tag, PATTERN_DISCONNECTED_EDGES_KEY), size));
            }
        }
        return new PatternData(data, connections);
    }

    private static boolean isEmpty(int[][] data) {
        for (int[] row : data) {
            for (int rune : row) {
                if (rune != -1) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isRuneBase(Block block) {
        Block target = runeBaseBlock();
        return block != null && block == target;
    }

    public static boolean isRuneDust(Item item) {
        Item target = runeDustItem();
        return item != null && item == target;
    }

    private static Block runeBaseBlock() {
        Block cached = runeBase.get();
        if (cached != null) {
            return cached;
        }
        Block block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation("randomthings", "runeBase"));
        if (block == null) {
            block = ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation("randomthings", "runebase"));
        }
        runeBase = new WeakReference<>(block);
        return block;
    }

    private static Item runeDustItem() {
        Item cached = runeDust.get();
        if (cached != null) {
            return cached;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("randomthings", "runeDust"));
        if (item == null) {
            item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("randomthings", "runedust"));
        }
        runeDust = new WeakReference<>(item);
        return item;
    }

    private static Item runePatternItem() {
        Item cached = runePattern.get();
        if (cached != null) {
            return cached;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("randomthings", "runePattern"));
        if (item == null) {
            item = ForgeRegistries.ITEMS.getValue(new net.minecraft.util.ResourceLocation("randomthings", "runepattern"));
        }
        runePattern = new WeakReference<>(item);
        return item;
    }

    private static void syncRuneTile(Object tile) {
        if (tile == null) {
            return;
        }
        try {
            if (syncTileEntity == null) {
                syncTileEntity = tile.getClass().getMethod("syncTE");
            }
            syncTileEntity.invoke(tile);
        } catch (Throwable ignored) {
        }
        if (tile instanceof TileEntity) {
            TileEntity te = (TileEntity) tile;
            MinecraftMappingCompat.tileEntityMarkDirty(te);
            World world = MinecraftMappingCompat.tileEntityWorld(te);
            BlockPos pos = MinecraftMappingCompat.tileEntityPos(te);
            if (world != null && pos != null) {
                IBlockState state = MinecraftMappingCompat.worldBlockState(world, pos);
                MinecraftMappingCompat.worldNotifyBlockUpdate(world, pos, state, state, 3);
            }
        }
    }

    private static final class CellHit {
        final int x;
        final int z;
        final float localX;
        final float localZ;

        CellHit(int x, int z, float localX, float localZ) {
            this.x = x;
            this.z = z;
            this.localX = localX;
            this.localZ = localZ;
        }
    }

    private static final class Neighbor {
        final TileEntity tile;
        final int[][] data;
        final int[] disconnected;
        final int x;
        final int z;

        Neighbor(TileEntity tile, int[][] data, int[] disconnected, int x, int z) {
            this.tile = tile;
            this.data = data;
            this.disconnected = disconnected;
            this.x = x;
            this.z = z;
        }
    }

    private static final class PatternData {
        final int[][] runeData;
        final int[] connectionEdges;

        PatternData(int[][] runeData, int[] connectionEdges) {
            this.runeData = runeData;
            this.connectionEdges = connectionEdges;
        }
    }

    private static final class RenderConnectionState {
        final int[] disconnectedEdges;
        final int size;

        RenderConnectionState(int[] disconnectedEdges, int size) {
            this.disconnectedEdges = disconnectedEdges.clone();
            this.size = size;
        }
    }
}
