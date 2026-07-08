package com.l.gpom.compat.minecraft;

import com.l.gpom.GPOM;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MinecraftMappingCompat {
    private static final ConcurrentMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();
    public static final Class<?>[] NO_TYPES = new Class<?>[0];
    public static final Object[] NO_ARGS = new Object[0];
    private static volatile Constructor<SPacketSetSlot> setSlotPacketConstructor;
    private static volatile Class<?> glStateManagerClass;

    private MinecraftMappingCompat() {
    }

    public static boolean worldIsRemote(World world) {
        Object value = fieldValue(world, "world.isRemote", "field_72995_K", "isRemote");
        return value instanceof Boolean && (Boolean) value;
    }

    public static ItemStack emptyStack() {
        Object value = staticFieldValue(ItemStack.class, "itemStack.empty", "field_190927_a", "EMPTY");
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    public static boolean itemStackIsEmpty(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        Object value = invoke(stack, "itemStack.isEmpty", noTypes(), noArgs(), "func_190926_b", "isEmpty");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        ItemStack empty = emptyStack();
        return empty != null && stack == empty;
    }

    public static ItemStack itemStackCopy(ItemStack stack) {
        Object value = invoke(stack, "itemStack.copy", noTypes(), noArgs(), "func_77946_l", "copy");
        return value instanceof ItemStack ? (ItemStack) value : emptyStack();
    }

    public static Item itemStackItem(ItemStack stack) {
        Object value = invoke(stack, "itemStack.getItem", noTypes(), noArgs(), "func_77973_b", "getItem");
        return value instanceof Item ? (Item) value : null;
    }

    public static int itemStackMetadata(ItemStack stack) {
        Object value = invoke(stack, "itemStack.getMetadata", noTypes(), noArgs(),
                "func_77960_j", "func_77952_i", "getMetadata", "getItemDamage");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static NBTTagCompound itemStackTagCompound(ItemStack stack) {
        Object value = invoke(stack, "itemStack.getTagCompound", noTypes(), noArgs(), "func_77978_p", "getTagCompound");
        return value instanceof NBTTagCompound ? (NBTTagCompound) value : null;
    }

    public static boolean nbtHasKey(NBTTagCompound tag, String key) {
        if (tag == null) {
            return false;
        }
        Object value = invoke(tag, "nbt.hasKey", new Class<?>[]{String.class}, new Object[]{key}, "func_74764_b", "hasKey");
        return value instanceof Boolean && (Boolean) value;
    }

    public static int[] nbtGetIntArray(NBTTagCompound tag, String key) {
        if (tag == null) {
            return new int[0];
        }
        Object value = invoke(tag, "nbt.getIntArray", new Class<?>[]{String.class}, new Object[]{key}, "func_74759_k", "getIntArray");
        return value instanceof int[] ? (int[]) value : new int[0];
    }

    public static boolean itemStackHasTagCompound(ItemStack stack) {
        Object value = invoke(stack, "itemStack.hasTagCompound", noTypes(), noArgs(), "func_77942_o", "hasTagCompound");
        return value instanceof Boolean && (Boolean) value;
    }

    public static void itemStackSetTagCompound(ItemStack stack, NBTTagCompound tag) {
        if (stack != null) {
            invoke(stack, "itemStack.setTagCompound", new Class<?>[]{NBTTagCompound.class}, new Object[]{tag},
                    "func_77982_d", "setTagCompound");
        }
    }

    public static int itemStackCount(ItemStack stack) {
        Object value = invoke(stack, "itemStack.getCount", noTypes(), noArgs(), "func_190916_E", "getCount");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static void itemStackSetCount(ItemStack stack, int count) {
        if (stack != null) {
            invoke(stack, "itemStack.setCount", new Class<?>[]{int.class}, new Object[]{count},
                    "func_190920_e", "setCount");
        }
    }

    public static void itemStackShrink(ItemStack stack, int amount) {
        if (stack != null && amount != 0) {
            invoke(stack, "itemStack.shrink", new Class<?>[]{int.class}, new Object[]{amount}, "func_190918_g", "shrink");
        }
    }

    public static boolean itemStacksSameItemAndTags(ItemStack left, ItemStack right) {
        Object sameItem = invokeStatic(ItemStack.class, "itemStack.areItemsEqual",
                new Class<?>[]{ItemStack.class, ItemStack.class}, new Object[]{left, right},
                "func_179545_c", "areItemsEqual");
        if (!Boolean.TRUE.equals(sameItem)) {
            return false;
        }
        Object sameTags = invokeStatic(ItemStack.class, "itemStack.areItemStackTagsEqual",
                new Class<?>[]{ItemStack.class, ItemStack.class}, new Object[]{left, right},
                "func_77970_a", "areItemStackTagsEqual");
        return Boolean.TRUE.equals(sameTags);
    }

    public static int itemIdFromItem(Item item) {
        Object value = invokeStatic(Item.class, "item.getIdFromItem",
                new Class<?>[]{Item.class}, new Object[]{item}, "func_150891_b", "getIdFromItem");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static ItemStack findMatchingResult(InventoryCrafting crafting, World world) {
        Object value = invokeStatic(CraftingManager.class, "crafting.findMatchingResult",
                new Class<?>[]{InventoryCrafting.class, World.class}, new Object[]{crafting, world},
                "func_82787_a", "findMatchingResult");
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    @SuppressWarnings("unchecked")
    public static ItemStack recipeCraftingResult(IRecipe recipe, InventoryCrafting crafting) {
        Object value = invoke(recipe, "recipe.getCraftingResult",
                new Class<?>[]{InventoryCrafting.class}, new Object[]{crafting},
                "func_77572_b", "getCraftingResult");
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    public static ItemStack recipeOutput(IRecipe recipe) {
        Object value = invoke(recipe, "recipe.getRecipeOutput", noTypes(), noArgs(), "func_77571_b", "getRecipeOutput");
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    @SuppressWarnings("unchecked")
    public static NonNullList<Ingredient> recipeIngredients(IRecipe recipe) {
        Object value = invoke(recipe, "recipe.getIngredients", noTypes(), noArgs(), "func_192400_c", "getIngredients");
        return value instanceof NonNullList ? (NonNullList<Ingredient>) value : null;
    }

    public static ItemStack[] ingredientMatchingStacks(Ingredient ingredient) {
        Object value = invoke(ingredient, "ingredient.getMatchingStacks", noTypes(), noArgs(),
                "func_193365_a", "getMatchingStacks");
        return value instanceof ItemStack[] ? (ItemStack[]) value : new ItemStack[0];
    }

    public static boolean ingredientApply(Ingredient ingredient, ItemStack stack) {
        Object value = invoke(ingredient, "ingredient.apply", new Class<?>[]{ItemStack.class}, new Object[]{stack}, "apply");
        return value instanceof Boolean && (Boolean) value;
    }

    public static void inventorySetStack(Object inventory, int slot, ItemStack stack) {
        invoke(inventory, "inventory.setInventorySlotContents",
                new Class<?>[]{int.class, ItemStack.class}, new Object[]{slot, stack},
                "func_70299_a", "setInventorySlotContents");
    }

    public static int inventorySize(Object inventory) {
        Object value = invoke(inventory, "inventory.getSizeInventory", noTypes(), noArgs(),
                "func_70302_i_", "getSizeInventory");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static ItemStack inventoryStackInSlot(Object inventory, int slot) {
        Object value = invoke(inventory, "inventory.getStackInSlot", new Class<?>[]{int.class}, new Object[]{slot},
                "func_70301_a", "getStackInSlot");
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    @SuppressWarnings("unchecked")
    public static World playerWorld(EntityPlayer player) {
        Object value = fieldValue(player, "entity.world", "field_70170_p", "world");
        return value instanceof World ? (World) value : null;
    }

    public static double entityPosX(Entity entity) {
        Object value = fieldValue(entity, "entity.posX", "field_70165_t", "posX");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static double entityPosY(Entity entity) {
        Object value = fieldValue(entity, "entity.posY", "field_70163_u", "posY");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static double entityPosZ(Entity entity) {
        Object value = fieldValue(entity, "entity.posZ", "field_70161_v", "posZ");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static double entityPrevPosX(Entity entity) {
        Object value = fieldValue(entity, "entity.prevPosX", "field_70169_q", "prevPosX");
        return value instanceof Number ? ((Number) value).doubleValue() : entityPosX(entity);
    }

    public static double entityPrevPosY(Entity entity) {
        Object value = fieldValue(entity, "entity.prevPosY", "field_70167_r", "prevPosY");
        return value instanceof Number ? ((Number) value).doubleValue() : entityPosY(entity);
    }

    public static double entityPrevPosZ(Entity entity) {
        Object value = fieldValue(entity, "entity.prevPosZ", "field_70166_s", "prevPosZ");
        return value instanceof Number ? ((Number) value).doubleValue() : entityPosZ(entity);
    }

    public static UUID playerUniqueId(EntityPlayer player) {
        Object value = invoke(player, "player.getUniqueID", noTypes(), noArgs(), "func_110124_au", "getUniqueID");
        return value instanceof UUID ? (UUID) value : null;
    }

    public static InventoryPlayer playerInventory(EntityPlayer player) {
        Object value = fieldValue(player, "player.inventory", "field_71071_by", "inventory");
        return value instanceof InventoryPlayer ? (InventoryPlayer) value : null;
    }

    @SuppressWarnings("unchecked")
    public static NonNullList<ItemStack> playerMainInventory(EntityPlayer player) {
        InventoryPlayer inventory = playerInventory(player);
        Object value = fieldValue(inventory, "playerInventory.mainInventory", "field_70462_a", "mainInventory");
        return value instanceof NonNullList ? (NonNullList<ItemStack>) value : null;
    }

    public static int playerCurrentItemSlot(EntityPlayer player) {
        InventoryPlayer inventory = playerInventory(player);
        Object value = fieldValue(inventory, "playerInventory.currentItem", "field_70461_c", "currentItem");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static ItemStack playerHeldItem(EntityPlayer player, EnumHand hand) {
        Object value = invoke(player, "player.getHeldItem", new Class<?>[]{EnumHand.class}, new Object[]{hand},
                "func_184586_b", "getHeldItem");
        if (value instanceof ItemStack) {
            return (ItemStack) value;
        }
        ItemStack empty = emptyStack();
        return empty == null ? null : empty;
    }

    public static boolean playerIsSneaking(EntityPlayer player) {
        Object value = invoke(player, "player.isSneaking", noTypes(), noArgs(), "func_70093_af", "isSneaking");
        return value instanceof Boolean && (Boolean) value;
    }

    public static boolean playerIsCreative(EntityPlayer player) {
        Object capabilities = fieldValue(player, "player.capabilities", "field_71075_bZ", "capabilities");
        if (!(capabilities instanceof PlayerCapabilities)) {
            return false;
        }
        Object value = fieldValue(capabilities, "playerCapabilities.isCreativeMode", "field_75098_d", "isCreativeMode");
        return value instanceof Boolean && (Boolean) value;
    }

    public static IBlockState worldBlockState(World world, BlockPos pos) {
        Object value = invoke(world, "world.getBlockState", new Class<?>[]{BlockPos.class}, new Object[]{pos},
                "func_180495_p", "getBlockState");
        return value instanceof IBlockState ? (IBlockState) value : null;
    }

    public static IBlockState blockAccessState(IBlockAccess world, BlockPos pos) {
        Object value = invoke(world, "blockAccess.getBlockState", new Class<?>[]{BlockPos.class}, new Object[]{pos},
                "func_180495_p", "getBlockState");
        return value instanceof IBlockState ? (IBlockState) value : null;
    }

    public static TileEntity worldTileEntity(IBlockAccess world, BlockPos pos) {
        Object value = invoke(world, "world.getTileEntity", new Class<?>[]{BlockPos.class}, new Object[]{pos},
                "func_175625_s", "getTileEntity");
        return value instanceof TileEntity ? (TileEntity) value : null;
    }

    public static World tileEntityWorld(TileEntity tile) {
        Object value = invoke(tile, "tileEntity.getWorld", noTypes(), noArgs(), "func_145831_w", "getWorld");
        return value instanceof World ? (World) value : null;
    }

    public static BlockPos tileEntityPos(TileEntity tile) {
        Object value = invoke(tile, "tileEntity.getPos", noTypes(), noArgs(), "func_174877_v", "getPos");
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static void tileEntityMarkDirty(TileEntity tile) {
        invoke(tile, "tileEntity.markDirty", noTypes(), noArgs(), "func_70296_d", "markDirty");
    }

    @SuppressWarnings("unchecked")
    public static Map<BlockPos, TileEntity> chunkTileEntityMap(Chunk chunk) {
        Object value = invoke(chunk, "chunk.getTileEntityMap", noTypes(), noArgs(), "func_177434_r", "getTileEntityMap");
        return value instanceof Map ? (Map<BlockPos, TileEntity>) value : null;
    }

    public static NBTTagCompound tileEntityWriteToNbt(TileEntity tile, NBTTagCompound tag) {
        Object value = invoke(tile, "tileEntity.writeToNBT",
                new Class<?>[]{NBTTagCompound.class},
                new Object[]{tag},
                "func_189515_b", "writeToNBT");
        return value instanceof NBTTagCompound ? (NBTTagCompound) value : tag;
    }

    public static void tileEntityReadFromNbt(TileEntity tile, NBTTagCompound tag) {
        invoke(tile, "tileEntity.readFromNBT",
                new Class<?>[]{NBTTagCompound.class},
                new Object[]{tag},
                "func_145839_a", "readFromNBT");
    }

    public static void worldNotifyBlockUpdate(World world, BlockPos pos, IBlockState oldState, IBlockState newState, int flags) {
        invoke(world, "world.notifyBlockUpdate",
                new Class<?>[]{BlockPos.class, IBlockState.class, IBlockState.class, int.class},
                new Object[]{pos, oldState, newState, flags},
                "func_184138_a", "notifyBlockUpdate");
    }

    public static float textureInterpolatedU(Object sprite, double u) {
        Object value = invoke(sprite, "textureAtlasSprite.getInterpolatedU",
                new Class<?>[]{double.class}, new Object[]{u},
                "func_94214_a", "getInterpolatedU");
        return value instanceof Number ? ((Number) value).floatValue() : 0.0F;
    }

    public static float textureInterpolatedV(Object sprite, double v) {
        Object value = invoke(sprite, "textureAtlasSprite.getInterpolatedV",
                new Class<?>[]{double.class}, new Object[]{v},
                "func_94207_b", "getInterpolatedV");
        return value instanceof Number ? ((Number) value).floatValue() : 0.0F;
    }

    public static boolean worldSetBlockState(World world, BlockPos pos, IBlockState state) {
        Object value = invoke(world, "world.setBlockState", new Class<?>[]{BlockPos.class, IBlockState.class}, new Object[]{pos, state},
                "func_175656_a", "setBlockState");
        return value instanceof Boolean && (Boolean) value;
    }

    public static boolean worldSetBlockState(World world, BlockPos pos, IBlockState state, int flags) {
        Object value = invoke(world, "world.setBlockStateFlags",
                new Class<?>[]{BlockPos.class, IBlockState.class, int.class},
                new Object[]{pos, state, flags},
                "func_180501_a", "setBlockState");
        return value instanceof Boolean && (Boolean) value;
    }

    public static void worldSetTileEntity(World world, BlockPos pos, TileEntity tile) {
        invoke(world, "world.setTileEntity",
                new Class<?>[]{BlockPos.class, TileEntity.class},
                new Object[]{pos, tile},
                "func_175690_a", "setTileEntity");
    }

    public static boolean worldSetBlockToAir(World world, BlockPos pos) {
        Object value = invoke(world, "world.setBlockToAir", new Class<?>[]{BlockPos.class}, new Object[]{pos},
                "func_175698_g", "setBlockToAir");
        return value instanceof Boolean && (Boolean) value;
    }

    public static boolean worldIsAirBlock(World world, BlockPos pos) {
        Object value = invoke(world, "world.isAirBlock", new Class<?>[]{BlockPos.class}, new Object[]{pos},
                "func_175623_d", "isAirBlock");
        return value instanceof Boolean && (Boolean) value;
    }

    public static RayTraceResult worldRayTraceBlocks(World world, Vec3d start, Vec3d end, boolean stopOnLiquid,
                                                     boolean ignoreBlockWithoutBoundingBox, boolean returnLastUncollidableBlock) {
        Object value = invoke(world, "world.rayTraceBlocks",
                new Class<?>[]{Vec3d.class, Vec3d.class, boolean.class, boolean.class, boolean.class},
                new Object[]{start, end, stopOnLiquid, ignoreBlockWithoutBoundingBox, returnLastUncollidableBlock},
                "func_147447_a", "rayTraceBlocks");
        return value instanceof RayTraceResult ? (RayTraceResult) value : null;
    }

    public static boolean worldSpawnEntity(World world, Entity entity) {
        Object value = invoke(world, "world.spawnEntity", new Class<?>[]{Entity.class}, new Object[]{entity},
                "func_72838_d", "spawnEntity");
        return value instanceof Boolean && (Boolean) value;
    }

    public static void worldPlaySound(World world, EntityPlayer player, BlockPos pos, SoundEvent sound, SoundCategory category,
                                      float volume, float pitch) {
        if (world == null || sound == null || category == null) {
            return;
        }
        invoke(world, "world.playSound",
                new Class<?>[]{EntityPlayer.class, BlockPos.class, SoundEvent.class, SoundCategory.class, float.class, float.class},
                new Object[]{player, pos, sound, category, volume, pitch},
                "func_184133_a", "playSound");
    }

    public static Block blockStateBlock(IBlockState state) {
        Object value = invoke(state, "blockState.getBlock", noTypes(), noArgs(), "func_177230_c", "getBlock");
        return value instanceof Block ? (Block) value : null;
    }

    public static ResourceLocation blockRegistryName(Block block) {
        Object value = invoke(block, "block.getRegistryName", noTypes(), noArgs(), "getRegistryName");
        if (value instanceof ResourceLocation) {
            return (ResourceLocation) value;
        }
        return block == null ? null : ForgeRegistries.BLOCKS.getKey(block);
    }

    public static boolean blockHasTileEntity(Block block) {
        Object value = invoke(block, "block.hasTileEntity", noTypes(), noArgs(), "func_149716_u", "hasTileEntity");
        return value instanceof Boolean && (Boolean) value;
    }

    public static BlockRenderLayer blockRenderLayer(Block block) {
        Object value = invoke(block, "block.getRenderLayer", noTypes(), noArgs(), "func_180664_k", "getRenderLayer");
        return value instanceof BlockRenderLayer ? (BlockRenderLayer) value : null;
    }

    public static boolean blockCanRenderInLayer(Block block, IBlockState state, BlockRenderLayer layer) {
        Object value = invoke(block, "block.canRenderInLayer",
                new Class<?>[]{IBlockState.class, BlockRenderLayer.class}, new Object[]{state, layer},
                "canRenderInLayer");
        return value instanceof Boolean && (Boolean) value;
    }

    public static int blockMetaFromState(Block block, IBlockState state) {
        Object value = invoke(block, "block.getMetaFromState",
                new Class<?>[]{IBlockState.class}, new Object[]{state},
                "func_176201_c", "getMetaFromState");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static boolean blockStateSideSolid(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        Object value = invoke(state, "blockState.isSideSolid",
                new Class<?>[]{IBlockAccess.class, BlockPos.class, EnumFacing.class}, new Object[]{world, pos, side},
                "isSideSolid");
        return value instanceof Boolean && (Boolean) value;
    }

    public static BlockPos blockPosOffset(BlockPos pos, EnumFacing facing) {
        Object value = invoke(pos, "blockPos.offset", new Class<?>[]{EnumFacing.class}, new Object[]{facing},
                "func_177972_a", "offset");
        return value instanceof BlockPos ? (BlockPos) value : pos;
    }

    public static int blockPosX(BlockPos pos) {
        Object value = invoke(pos, "blockPos.getX", noTypes(), noArgs(), "func_177958_n", "getX");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static int blockPosY(BlockPos pos) {
        Object value = invoke(pos, "blockPos.getY", noTypes(), noArgs(), "func_177956_o", "getY");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static int blockPosZ(BlockPos pos) {
        Object value = invoke(pos, "blockPos.getZ", noTypes(), noArgs(), "func_177952_p", "getZ");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    public static Vec3d playerPositionEyes(EntityPlayer player, float partialTicks) {
        Object value = invoke(player, "player.getPositionEyes", new Class<?>[]{float.class}, new Object[]{partialTicks},
                "func_174824_e", "getPositionEyes");
        return value instanceof Vec3d ? (Vec3d) value : null;
    }

    public static Vec3d playerLookVec(EntityPlayer player) {
        Object value = invoke(player, "player.getLookVec", noTypes(), noArgs(), "func_70040_Z", "getLookVec");
        return value instanceof Vec3d ? (Vec3d) value : null;
    }

    public static Vec3d vecScale(Vec3d vec, double factor) {
        if (vec == null) {
            return null;
        }
        Object value = invoke(vec, "vec3d.scale", new Class<?>[]{double.class}, new Object[]{factor},
                "func_186678_a", "scale");
        return value instanceof Vec3d ? (Vec3d) value : new Vec3d(vecX(vec) * factor, vecY(vec) * factor, vecZ(vec) * factor);
    }

    public static Vec3d vecAdd(Vec3d vec, Vec3d other) {
        if (vec == null) {
            return other;
        }
        if (other == null) {
            return vec;
        }
        Object value = invoke(vec, "vec3d.add", new Class<?>[]{Vec3d.class}, new Object[]{other},
                "func_178787_e", "add");
        return value instanceof Vec3d ? (Vec3d) value : new Vec3d(vecX(vec) + vecX(other), vecY(vec) + vecY(other), vecZ(vec) + vecZ(other));
    }

    public static Vec3d vecSubtract(Vec3d vec, Vec3d other) {
        if (vec == null) {
            return null;
        }
        if (other == null) {
            return vec;
        }
        Object value = invoke(vec, "vec3d.subtract", new Class<?>[]{Vec3d.class}, new Object[]{other},
                "func_178788_d", "subtract");
        return value instanceof Vec3d ? (Vec3d) value : new Vec3d(vecX(vec) - vecX(other), vecY(vec) - vecY(other), vecZ(vec) - vecZ(other));
    }

    public static double vecX(Vec3d vec) {
        Object value = fieldValue(vec, "vec3d.x", "field_72450_a", "x", "xCoord");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static double vecY(Vec3d vec) {
        Object value = fieldValue(vec, "vec3d.y", "field_72448_b", "y", "yCoord");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static double vecZ(Vec3d vec) {
        Object value = fieldValue(vec, "vec3d.z", "field_72449_c", "z", "zCoord");
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static BlockPos rayTraceBlockPos(RayTraceResult result) {
        Object value = invoke(result, "rayTraceResult.getBlockPos", noTypes(), noArgs(), "func_178782_a", "getBlockPos");
        return value instanceof BlockPos ? (BlockPos) value : null;
    }

    public static Vec3d rayTraceHitVec(RayTraceResult result) {
        Object value = fieldValue(result, "rayTraceResult.hitVec", "field_72307_f", "hitVec");
        return value instanceof Vec3d ? (Vec3d) value : null;
    }

    public static EnumFacing rayTraceSideHit(RayTraceResult result) {
        Object value = fieldValue(result, "rayTraceResult.sideHit", "field_178784_b", "sideHit");
        return value instanceof EnumFacing ? (EnumFacing) value : null;
    }

    public static void glPushMatrix() {
        invokeStatic(glStateManagerClass(), "glStateManager.pushMatrix", noTypes(), noArgs(), "func_179094_E", "pushMatrix");
    }

    public static void glPopMatrix() {
        invokeStatic(glStateManagerClass(), "glStateManager.popMatrix", noTypes(), noArgs(), "func_179121_F", "popMatrix");
    }

    public static void glDisableTexture2D() {
        invokeStatic(glStateManagerClass(), "glStateManager.disableTexture2D", noTypes(), noArgs(), "func_179090_x", "disableTexture2D");
    }

    public static void glEnableTexture2D() {
        invokeStatic(glStateManagerClass(), "glStateManager.enableTexture2D", noTypes(), noArgs(), "func_179098_w", "enableTexture2D");
    }

    public static void glDisableLighting() {
        invokeStatic(glStateManagerClass(), "glStateManager.disableLighting", noTypes(), noArgs(), "func_179140_f", "disableLighting");
    }

    public static void glEnableLighting() {
        invokeStatic(glStateManagerClass(), "glStateManager.enableLighting", noTypes(), noArgs(), "func_179145_e", "enableLighting");
    }

    public static void glEnableBlend() {
        invokeStatic(glStateManagerClass(), "glStateManager.enableBlend", noTypes(), noArgs(), "func_179147_l", "enableBlend");
    }

    public static void glDisableBlend() {
        invokeStatic(glStateManagerClass(), "glStateManager.disableBlend", noTypes(), noArgs(), "func_179084_k", "disableBlend");
    }

    public static void glTryBlendFuncSeparate(int srcFactor, int dstFactor, int srcFactorAlpha, int dstFactorAlpha) {
        invokeStatic(glStateManagerClass(), "glStateManager.tryBlendFuncSeparate",
                new Class<?>[]{int.class, int.class, int.class, int.class},
                new Object[]{srcFactor, dstFactor, srcFactorAlpha, dstFactorAlpha},
                "func_179120_a", "tryBlendFuncSeparate");
    }

    public static void glDepthMask(boolean flag) {
        invokeStatic(glStateManagerClass(), "glStateManager.depthMask",
                new Class<?>[]{boolean.class}, new Object[]{flag},
                "func_179132_a", "depthMask");
    }

    public static boolean rayTraceIsBlock(RayTraceResult result) {
        Object value = fieldValue(result, "rayTraceResult.typeOfHit", "field_72313_a", "typeOfHit");
        return value instanceof RayTraceResult.Type && value == RayTraceResult.Type.BLOCK;
    }

    public static void entityItemSetDefaultPickupDelay(EntityItem entity) {
        invoke(entity, "entityItem.setDefaultPickupDelay", noTypes(), noArgs(), "func_174869_p", "setDefaultPickupDelay");
    }

    public static boolean blockIsAir(Block block, IBlockState state, IBlockAccess world, BlockPos pos) {
        Object value = invoke(block, "block.isAir",
                new Class<?>[]{IBlockState.class, IBlockAccess.class, BlockPos.class}, new Object[]{state, world, pos},
                "isAir");
        return value instanceof Boolean && (Boolean) value;
    }

    public static boolean blockIsReplaceable(Block block, IBlockAccess world, BlockPos pos) {
        Object value = invoke(block, "block.isReplaceable", new Class<?>[]{IBlockAccess.class, BlockPos.class}, new Object[]{world, pos},
                "func_176200_f", "isReplaceable");
        return value instanceof Boolean && (Boolean) value;
    }

    public static IBlockState blockDefaultState(Block block) {
        Object value = invoke(block, "block.getDefaultState", noTypes(), noArgs(), "func_176223_P", "getDefaultState");
        return value instanceof IBlockState ? (IBlockState) value : null;
    }

    public static boolean blockStateIsOpaqueCube(IBlockState state) {
        Object value = invoke(state, "blockState.isOpaqueCube", noTypes(), noArgs(), "func_185914_p", "isOpaqueCube");
        return value instanceof Boolean && (Boolean) value;
    }

    public static double aabbMinX(AxisAlignedBB box) {
        return doubleField(box, "axisAlignedBB.minX", "field_72340_a", "minX");
    }

    public static double aabbMinY(AxisAlignedBB box) {
        return doubleField(box, "axisAlignedBB.minY", "field_72338_b", "minY");
    }

    public static double aabbMinZ(AxisAlignedBB box) {
        return doubleField(box, "axisAlignedBB.minZ", "field_72339_c", "minZ");
    }

    public static double aabbMaxX(AxisAlignedBB box) {
        return doubleField(box, "axisAlignedBB.maxX", "field_72336_d", "maxX");
    }

    public static double aabbMaxY(AxisAlignedBB box) {
        return doubleField(box, "axisAlignedBB.maxY", "field_72337_e", "maxY");
    }

    public static double aabbMaxZ(AxisAlignedBB box) {
        return doubleField(box, "axisAlignedBB.maxZ", "field_72334_f", "maxZ");
    }

    public static Integer worldDimension(World world) {
        Object provider = fieldValue(world, "world.provider", "field_73011_w", "provider");
        if (!(provider instanceof WorldProvider)) {
            return null;
        }
        Object value = invoke(provider, "worldProvider.getDimension", noTypes(), noArgs(),
                "func_186058_p", "getDimension");
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    public static boolean addToPlayerInventory(EntityPlayer player, ItemStack stack) {
        InventoryPlayer inventory = playerInventory(player);
        if (inventory == null) {
            return false;
        }
        Object value = invoke(inventory, "inventoryPlayer.addItemStackToInventory",
                new Class<?>[]{ItemStack.class}, new Object[]{stack},
                "func_70441_a", "addItemStackToInventory");
        return value instanceof Boolean && (Boolean) value;
    }

    public static void sendCraftingResult(EntityPlayer player, int windowId, ItemStack output) {
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }
        Object connection = fieldValue(player, "player.connection", "field_71135_a", "connection");
        if (connection == null) {
            return;
        }
        Packet<?> packet = newSetSlotPacket(windowId, 0, output);
        if (packet != null) {
            invoke(connection, "netHandlerPlayServer.sendPacket", new Class<?>[]{Packet.class}, new Object[]{packet},
                    "func_147359_a", "sendPacket");
        }
    }

    private static Packet<?> newSetSlotPacket(int windowId, int slot, ItemStack stack) {
        try {
            Constructor<SPacketSetSlot> constructor = setSlotPacketConstructor;
            if (constructor == null) {
                constructor = SPacketSetSlot.class.getConstructor(int.class, int.class, ItemStack.class);
                constructor.setAccessible(true);
                setSlotPacketConstructor = constructor;
            }
            return constructor.newInstance(windowId, slot, stack);
        } catch (Throwable throwable) {
            logOnce(SPacketSetSlot.class, "packet.setSlot", "could not create", throwable);
            return null;
        }
    }

    private static Class<?> glStateManagerClass() {
        Class<?> cached = glStateManagerClass;
        if (cached != null) {
            return cached;
        }
        try {
            cached = Class.forName("net.minecraft.client.renderer.GlStateManager", false,
                    MinecraftMappingCompat.class.getClassLoader());
            glStateManagerClass = cached;
            return cached;
        } catch (Throwable throwable) {
            logOnce(MinecraftMappingCompat.class, "glStateManager.class", "could not resolve", throwable);
            return null;
        }
    }

    public static Object staticFieldValue(Class<?> ownerClass, String purpose, String... names) {
        Field field = findField(ownerClass, purpose, names);
        if (field == null) {
            return null;
        }
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            logOnce(ownerClass, purpose, "could not read", e);
            return null;
        }
    }

    public static Object fieldValue(Object owner, String purpose, String... names) {
        if (owner == null) {
            return null;
        }
        Field field = findField(owner.getClass(), purpose, names);
        if (field == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            logOnce(owner.getClass(), purpose, "could not read", e);
            return null;
        }
    }

    private static double doubleField(Object owner, String purpose, String... names) {
        Object value = fieldValue(owner, purpose, names);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    public static Object invokeStatic(Class<?> ownerClass, String purpose, Class<?>[] parameterTypes, Object[] args, String... names) {
        if (ownerClass == null) {
            return null;
        }
        Method method = findMethod(ownerClass, purpose, parameterTypes, names);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logOnce(ownerClass, purpose, "threw", cause);
            return null;
        } catch (Throwable throwable) {
            logOnce(ownerClass, purpose, "could not invoke", throwable);
            return null;
        }
    }

    public static Object invoke(Object owner, String purpose, Class<?>[] parameterTypes, Object[] args, String... names) {
        if (owner == null) {
            return null;
        }
        Method method = findMethod(owner.getClass(), purpose, parameterTypes, names);
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(owner, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            logOnce(owner.getClass(), purpose, "threw", cause);
            return null;
        } catch (Throwable throwable) {
            logOnce(owner.getClass(), purpose, "could not invoke", throwable);
            return null;
        }
    }

    private static Field findField(Class<?> ownerClass, String purpose, String... names) {
        String key = ownerClass.getName() + '#' + purpose;
        Field cached = FIELDS.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> current = ownerClass;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    Field existing = FIELDS.putIfAbsent(key, field);
                    return existing == null ? field : existing;
                } catch (NoSuchFieldException ignored) {
                    // Try every known runtime name before logging once.
                } catch (Throwable throwable) {
                    logOnce(ownerClass, purpose, "could not inspect field", throwable);
                }
            }
            current = current.getSuperclass();
        }
        logOnce(ownerClass, purpose, "could not find field", null);
        return null;
    }

    private static Method findMethod(Class<?> ownerClass, String purpose, Class<?>[] parameterTypes, String... names) {
        String key = ownerClass.getName() + '#' + purpose;
        Method cached = METHODS.get(key);
        if (cached != null) {
            return cached;
        }
        for (String name : names) {
            try {
                Method method = ownerClass.getMethod(name, parameterTypes);
                method.setAccessible(true);
                Method existing = METHODS.putIfAbsent(key, method);
                return existing == null ? method : existing;
            } catch (NoSuchMethodException ignored) {
                // Try declared methods and interface defaults below before logging once.
            } catch (Throwable throwable) {
                logOnce(ownerClass, purpose, "could not inspect public method", throwable);
            }
        }
        Class<?> current = ownerClass;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    Method existing = METHODS.putIfAbsent(key, method);
                    return existing == null ? method : existing;
                } catch (NoSuchMethodException ignored) {
                    // Try every known runtime name before logging once.
                } catch (Throwable throwable) {
                    logOnce(ownerClass, purpose, "could not inspect method", throwable);
                }
            }
            current = current.getSuperclass();
        }
        logOnce(ownerClass, purpose, "could not find method", null);
        return null;
    }

    public static Class<?>[] noTypes() {
        return NO_TYPES;
    }

    public static Object[] noArgs() {
        return NO_ARGS;
    }

    private static void logOnce(Class<?> ownerClass, String purpose, String action, Throwable throwable) {
        String key = ownerClass.getName() + '#' + purpose + '#' + action + '#'
                + (throwable == null ? "" : throwable.getClass().getName() + ':' + String.valueOf(throwable.getMessage()));
        if (!LOGGED_FAILURES.add(key)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[GPOM Mapping Compat] {}.{} {}", ownerClass.getName(), purpose, action);
        } else {
            GPOM.LOGGER.warn("[GPOM Mapping Compat] {}.{} {}", ownerClass.getName(), purpose, action, throwable);
        }
    }
}
