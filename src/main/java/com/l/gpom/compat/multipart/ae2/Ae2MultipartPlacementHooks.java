package com.l.gpom.compat.multipart.ae2;

import appeng.api.util.AEPartLocation;
import codechicken.multipart.TileMultipart;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class Ae2MultipartPlacementHooks {
    private static final Method PLAYER_GET_HELD_ITEM = findMethod(EntityPlayer.class, "getHeldItem", "func_184586_b", EnumHand.class);
    private static final Method PLAYER_IS_SNEAKING = findMethod(EntityPlayer.class, "isSneaking", "func_70093_af");
    private static final Method BLOCK_POS_OFFSET = findMethod(BlockPos.class, "offset", "func_177972_a", EnumFacing.class);
    private static final Method FACING_GET_OPPOSITE = findMethod(EnumFacing.class, "getOpposite", "func_176734_d");
    private static final Field WORLD_IS_REMOTE = findField(World.class, "isRemote", "field_72995_K");
    private static final Field PLAYER_CAPABILITIES = findField(EntityPlayer.class, "capabilities", "field_71075_bZ");
    private static final Field CAPABILITIES_CREATIVE = findField("net.minecraft.entity.player.PlayerCapabilities", "isCreativeMode", "field_75098_d");

    private Ae2MultipartPlacementHooks() {
    }

    public static EnumActionResult tryPlaceHeldCable(World world,
                                                     BlockPos clickedPos,
                                                     EnumFacing side,
                                                     EntityPlayer player,
                                                     EnumHand hand) {
        return tryPlaceCable(heldItem(player, hand), world, clickedPos, side, player, hand);
    }

    public static EnumActionResult tryPlaceHeldAe2Part(World world,
                                                       BlockPos clickedPos,
                                                       EnumFacing side,
                                                       EntityPlayer player,
                                                       EnumHand hand,
                                                       float hitX,
                                                       float hitY,
                                                       float hitZ) {
        ItemStack stack = heldItem(player, hand);
        if (isSneaking(player) && isMultipartAt(world, clickedPos)) {
            return tryPlaceCableOffsetOnly(stack, world, clickedPos, side, player, hand);
        }
        if (shouldLetSelectedHostedPartActivate(stack, world, clickedPos, player, hitX, hitY, hitZ)) {
            return EnumActionResult.PASS;
        }
        EnumActionResult sidePartResult = tryPlaceSidePart(stack, world, clickedPos, side, player, hand);
        return sidePartResult != null ? sidePartResult : tryPlaceCable(stack, world, clickedPos, side, player, hand);
    }

    public static EnumActionResult tryPlaceCable(ItemStack stack,
                                                World world,
                                                BlockPos clickedPos,
                                                EnumFacing side,
                                                EntityPlayer player,
                                                EnumHand hand) {
        if (!GpomEarlyConfig.multipartCompatAe2PlacementConverterEnabled()
                || world == null
                || clickedPos == null
                || side == null
                || stack == null
                || Ae2ItemStackCompat.isEmpty(stack)
                || !Ae2PlacementConverter.isCablePartStack(stack)) {
            return null;
        }

        boolean clickedMultipart = isMultipartAt(world, clickedPos);
        BlockPos primaryTarget = clickedMultipart ? clickedPos : offset(clickedPos, side);
        BlockPos fallbackTarget = clickedMultipart ? offset(clickedPos, side) : clickedPos;
        if (primaryTarget == null || fallbackTarget == null) {
            return null;
        }

        PlacementCandidate candidate = findCandidate(stack, world, primaryTarget, fallbackTarget, player, hand, false);
        if (candidate == null) {
            return clickedMultipart ? EnumActionResult.FAIL : null;
        }

        return placeCandidate(stack, world, player, hand, candidate);
    }

    private static EnumActionResult tryPlaceCableOffsetOnly(ItemStack stack,
                                                           World world,
                                                           BlockPos clickedPos,
                                                           EnumFacing side,
                                                           EntityPlayer player,
                                                           EnumHand hand) {
        if (!GpomEarlyConfig.multipartCompatAe2PlacementConverterEnabled()
                || world == null
                || clickedPos == null
                || side == null
                || stack == null
                || Ae2ItemStackCompat.isEmpty(stack)
                || !Ae2PlacementConverter.isCablePartStack(stack)) {
            return null;
        }

        BlockPos target = offset(clickedPos, side);
        if (target == null) {
            return EnumActionResult.FAIL;
        }
        PlacementCandidate candidate = candidateAt(stack, world, target, player, hand);
        if (candidate == null) {
            return EnumActionResult.FAIL;
        }
        return placeCandidate(stack, world, player, hand, candidate);
    }

    private static EnumActionResult placeCandidate(ItemStack stack,
                                                   World world,
                                                   EntityPlayer player,
                                                   EnumHand hand,
                                                   PlacementCandidate candidate) {
        if (isRemote(world)) {
            return EnumActionResult.SUCCESS;
        }

        TileMultipart.addPart(world, candidate.pos, candidate.part);
        candidate.part.afterManualPlacementRefresh();
        consumePlacedStack(stack, player, hand);

        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.info("[GPOM Multipart] Placed AE2 cable item {} as hosted multipart at {}", stack, candidate.pos);
        }
        return EnumActionResult.SUCCESS;
    }

    private static boolean shouldLetSelectedHostedPartActivate(ItemStack stack,
                                                               World world,
                                                               BlockPos clickedPos,
                                                               EntityPlayer player,
                                                               float hitX,
                                                               float hitY,
                                                               float hitZ) {
        if (!Ae2PlacementConverter.isAe2PartStack(stack) || isSneaking(player)) {
            return false;
        }
        Ae2CableBusMultipart host = hostedCableAt(world, clickedPos);
        return host != null && host.selectsExistingAttachment(hitX, hitY, hitZ);
    }

    private static EnumActionResult tryPlaceSidePart(ItemStack stack,
                                                     World world,
                                                     BlockPos clickedPos,
                                                     EnumFacing side,
                                                     EntityPlayer player,
                                                     EnumHand hand) {
        if (!GpomEarlyConfig.multipartCompatAe2SidePartPlacementEnabled()
                || world == null
                || clickedPos == null
                || side == null
                || stack == null
                || Ae2ItemStackCompat.isEmpty(stack)
                || !Ae2PlacementConverter.isAe2PartStack(stack)
                || Ae2PlacementConverter.isCablePartStack(stack)) {
            return null;
        }

        SidePartCandidate candidate = findSidePartCandidate(stack, world, clickedPos, side);
        if (candidate == null) {
            return null;
        }

        if (isRemote(world)) {
            return EnumActionResult.SUCCESS;
        }

        AEPartLocation addedAt = candidate.host.addPart(stack, candidate.location, player, hand);
        if (addedAt == null || candidate.host.getPart(addedAt) == null) {
            return null;
        }

        consumePlacedStack(stack, player, hand);
        candidate.host.partChanged();

        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[GPOM Multipart] Placed AE2 side part item {} into hosted multipart at {} side {}",
                    stack,
                    candidate.pos,
                    candidate.location
            );
        }
        return EnumActionResult.SUCCESS;
    }

    private static SidePartCandidate findSidePartCandidate(ItemStack stack,
                                                           World world,
                                                           BlockPos clickedPos,
                                                           EnumFacing side) {
        SidePartCandidate clicked = sidePartCandidateAt(stack, world, clickedPos, side);
        if (clicked != null) {
            return clicked;
        }

        BlockPos offsetPos = offset(clickedPos, side);
        EnumFacing opposite = opposite(side);
        if (offsetPos == null || opposite == null) {
            return null;
        }
        return sidePartCandidateAt(stack, world, offsetPos, opposite);
    }

    private static SidePartCandidate sidePartCandidateAt(ItemStack stack,
                                                         World world,
                                                         BlockPos pos,
                                                         EnumFacing side) {
        Ae2CableBusMultipart host = hostedCableAt(world, pos);
        if (host == null) {
            return null;
        }

        AEPartLocation location = AEPartLocation.fromFacing(side);
        if (!host.canAddPart(stack, location)) {
            return null;
        }
        return new SidePartCandidate(pos, host, location);
    }

    private static Ae2CableBusMultipart hostedCableAt(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        Object host = Ae2MultipartGridHostBridge.findGridHost(Ae2MinecraftCompat.getTileEntity(world, pos));
        return host instanceof Ae2CableBusMultipart ? (Ae2CableBusMultipart) host : null;
    }

    private static PlacementCandidate findCandidate(ItemStack stack,
                                                    World world,
                                                    BlockPos primaryTarget,
                                                    BlockPos fallbackTarget,
                                                    EntityPlayer player,
                                                    EnumHand hand,
                                                    boolean strictPrimary) {
        PlacementCandidate primary = candidateAt(stack, world, primaryTarget, player, hand);
        if (primary != null) {
            return primary;
        }
        if (strictPrimary) {
            return null;
        }
        return primaryTarget.equals(fallbackTarget) ? null : candidateAt(stack, world, fallbackTarget, player, hand);
    }

    private static PlacementCandidate candidateAt(ItemStack stack,
                                                  World world,
                                                  BlockPos pos,
                                                  EntityPlayer player,
                                                  EnumHand hand) {
        Ae2CableBusMultipart part = Ae2CableBusMultipart.forPlacement(stack, AEPartLocation.INTERNAL, player, hand);
        return TileMultipart.canPlacePart(world, pos, part) ? new PlacementCandidate(pos, part) : null;
    }

    private static boolean isMultipartAt(World world, BlockPos pos) {
        return Ae2MinecraftCompat.getTileEntity(world, pos) instanceof TileMultipart;
    }

    private static void consumePlacedStack(ItemStack stack, EntityPlayer player, EnumHand hand) {
        if (player == null || isCreative(player)) {
            return;
        }
        Ae2ItemStackCompat.shrink(stack, 1);
        if (Ae2ItemStackCompat.isEmpty(stack)) {
            ForgeEventFactory.onPlayerDestroyItem(player, stack, hand);
        }
    }

    private static ItemStack heldItem(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null || PLAYER_GET_HELD_ITEM == null) {
            return null;
        }
        try {
            Object value = PLAYER_GET_HELD_ITEM.invoke(player, hand);
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("EntityPlayer.getHeldItem", throwable);
            return null;
        }
    }

    private static BlockPos offset(BlockPos pos, EnumFacing side) {
        if (pos == null || side == null || BLOCK_POS_OFFSET == null) {
            return null;
        }
        try {
            Object value = BLOCK_POS_OFFSET.invoke(pos, side);
            return value instanceof BlockPos ? (BlockPos) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("BlockPos.offset", throwable);
            return null;
        }
    }

    private static EnumFacing opposite(EnumFacing side) {
        if (side == null || FACING_GET_OPPOSITE == null) {
            return null;
        }
        try {
            Object value = FACING_GET_OPPOSITE.invoke(side);
            return value instanceof EnumFacing ? (EnumFacing) value : null;
        } catch (Throwable throwable) {
            logBridgeFailure("EnumFacing.getOpposite", throwable);
            return null;
        }
    }

    private static boolean isRemote(World world) {
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

    private static boolean isCreative(EntityPlayer player) {
        if (player == null || PLAYER_CAPABILITIES == null || CAPABILITIES_CREATIVE == null) {
            return false;
        }
        try {
            Object capabilities = PLAYER_CAPABILITIES.get(player);
            return capabilities != null && CAPABILITIES_CREATIVE.getBoolean(capabilities);
        } catch (Throwable throwable) {
            logBridgeFailure("EntityPlayer.capabilities.isCreativeMode", throwable);
            return false;
        }
    }

    private static boolean isSneaking(EntityPlayer player) {
        if (player == null || PLAYER_IS_SNEAKING == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(PLAYER_IS_SNEAKING.invoke(player));
        } catch (Throwable throwable) {
            logBridgeFailure("EntityPlayer.isSneaking", throwable);
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(type, mcpName, parameterTypes);
        return method != null ? method : findMethod(type, srgName, parameterTypes);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
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

    private static Field findField(String className, String mcpName, String srgName) {
        try {
            return findField(Class.forName(className), mcpName, srgName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void logBridgeFailure(String operation, Throwable throwable) {
        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.warn("[GPOM Multipart] {} bridge failed", operation, throwable);
        }
    }

    private static final class PlacementCandidate {
        private final BlockPos pos;
        private final Ae2CableBusMultipart part;

        private PlacementCandidate(BlockPos pos, Ae2CableBusMultipart part) {
            this.pos = pos;
            this.part = part;
        }
    }

    private static final class SidePartCandidate {
        private final BlockPos pos;
        private final Ae2CableBusMultipart host;
        private final AEPartLocation location;

        private SidePartCandidate(BlockPos pos, Ae2CableBusMultipart host, AEPartLocation location) {
            this.pos = pos;
            this.host = host;
            this.location = location;
        }
    }
}
