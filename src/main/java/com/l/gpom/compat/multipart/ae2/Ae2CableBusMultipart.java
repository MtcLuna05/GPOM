package com.l.gpom.compat.multipart.ae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridHost;
import appeng.api.AEApi;
import appeng.api.parts.IFacadeContainer;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.api.parts.SelectedPart;
import appeng.api.util.AEColor;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.parts.CableBusContainer;
import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Vector3;
import codechicken.microblock.TMicroOcclusion;
import codechicken.multipart.NormalOcclusionTest;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.TNormalOcclusionPart;
import codechicken.multipart.TPartialOcclusionPart;
import codechicken.multipart.TileMultipart;
import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import com.l.gpom.config.GpomEarlyConfig;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

public final class Ae2CableBusMultipart extends TMultiPart implements appeng.api.parts.IPartHost, IGridHost, ICapabilityProvider, TNormalOcclusionPart {
    public static final ResourceLocation TYPE = new ResourceLocation(Reference.MOD_ID, "ae2_cable_bus");
    private static final String TAG_CABLE_BUS = "cableBus";
    private static final String TAG_CABLE_BUS_STREAM = "cableBusStream";
    private static final String TAG_PENDING_PLACEMENT = "pendingPlacement";
    private static final String TAG_PENDING_STACK = "stack";
    private static final String TAG_PENDING_LOCATION = "location";
    private static final Cuboid6 FALLBACK_BOUNDS = new Cuboid6(0.3125D, 0.3125D, 0.3125D, 0.6875D, 0.6875D, 0.6875D);
    private static final double SIDE_CHANNEL_MIN = 0.375D;
    private static final double SIDE_CHANNEL_MAX = 0.625D;
    private static final double SIDE_ACTIVATION_EPSILON = 0.1875D;
    private static final int POST_LOAD_REFRESH_PASSES = 12;

    private final CableBusContainer cableBus = new CableBusContainer(this);
    private NBTTagCompound pendingCableBusNbt;
    private PendingPlacement pendingPlacement;
    private int postLoadRefreshesRemaining;
    private int deferredDescSyncTicks = -1;
    private boolean postLoadRefreshScheduled;
    private boolean suppressDescUpdates;

    public static Ae2CableBusMultipart forPlacement(ItemStack stack, AEPartLocation location, EntityPlayer player, EnumHand hand) {
        Ae2CableBusMultipart part = new Ae2CableBusMultipart();
        part.pendingPlacement = new PendingPlacement(copyOne(stack), location, player, hand == null ? EnumHand.MAIN_HAND : hand);
        return part;
    }

    public static Ae2CableBusMultipart fromCableBus(appeng.tile.networking.TileCableBus tile) {
        Ae2CableBusMultipart part = new Ae2CableBusMultipart();
        NBTTagCompound tag = new NBTTagCompound();
        tile.writeToNBT(tag);
        part.pendingCableBusNbt = tag;
        return part;
    }

    @Override
    public ResourceLocation getType() {
        return TYPE;
    }

    @Override
    public void bind(TileMultipart tile) {
        super.bind(tile);
        applyPendingCableBusNbt();
    }

    @Override
    public void onAdded() {
        runWithoutDescUpdates(new Runnable() {
            @Override
            public void run() {
                applyPendingCableBusNbt();
                applyPendingPlacement();
                joinWorldIfPossible();
                schedulePostLoadRefreshes();
            }
        });
        markForSave();
    }

    @Override
    public void onWorldJoin() {
        runWithoutDescUpdates(new Runnable() {
            @Override
            public void run() {
                restorePendingState();
                joinWorldIfPossible();
                schedulePostLoadRefreshes();
            }
        });
    }

    @Override
    public void onChunkLoad() {
        runWithoutDescUpdates(new Runnable() {
            @Override
            public void run() {
                restorePendingState();
                joinWorldIfPossible();
                schedulePostLoadRefreshes();
            }
        });
    }

    @Override
    public void onWorldSeparate() {
        leaveWorldIfNeeded();
        resetPostLoadRefreshes();
    }

    @Override
    public void onChunkUnload() {
        leaveWorldIfNeeded();
        resetPostLoadRefreshes();
    }

    @Override
    public void preRemove() {
        leaveWorldIfNeeded();
        resetPostLoadRefreshes();
    }

    @Override
    public void onRemoved() {
        cableBus.cleanup();
    }

    @Override
    public void save(NBTTagCompound tag) {
        NBTTagCompound cableTag = pendingCableBusNbt != null ? pendingCableBusNbt : new NBTTagCompound();
        if (pendingCableBusNbt == null) {
            cableBus.writeToNBT(cableTag);
        }
        Ae2MinecraftCompat.setTag(tag, TAG_CABLE_BUS, cableTag);
        writePendingPlacement(tag);
    }

    @Override
    public void load(NBTTagCompound tag) {
        pendingCableBusNbt = Ae2MinecraftCompat.getCompoundTag(tag, TAG_CABLE_BUS);
        pendingPlacement = readPendingPlacement(tag);
        applyPendingCableBusNbt();
    }

    @Override
    public void writeDesc(MCDataOutput output) {
        NBTTagCompound tag = new NBTTagCompound();
        save(tag);
        writeCableBusStream(tag);
        output.writeNBTTagCompound(tag);
    }

    @Override
    public void readDesc(MCDataInput input) {
        NBTTagCompound tag = input.readNBTTagCompound();
        load(tag);
        readCableBusStream(tag);
        markRenderOnly();
    }

    @Override
    public Iterable<Cuboid6> getCollisionBoxes() {
        return currentCuboids(false);
    }

    @Override
    public Iterable<IndexedCuboid6> getSubParts() {
        List<IndexedCuboid6> indexed = new ArrayList<>();
        int index = 0;
        for (Cuboid6 cuboid : currentCuboids(true)) {
            indexed.add(new IndexedCuboid6(index++, cuboid));
        }
        return indexed;
    }

    @Override
    public Iterable<Cuboid6> getOcclusionBoxes() {
        return currentCuboids(true);
    }

    @Override
    public boolean occlusionTest(TMultiPart other) {
        return NormalOcclusionTest.apply(this, other);
    }

    @Override
    public boolean renderStatic(Vector3 position, BlockRenderLayer layer, CCRenderState renderState) {
        return Ae2CableBusMultipartRenderer.render(this, cableBus, world(), pos(), position, renderState);
    }

    @Override
    public Iterable<ItemStack> getDrops() {
        List<ItemStack> drops = new ArrayList<>();
        cableBus.getDrops(drops);
        if (drops.isEmpty() && pendingPlacement != null && !Ae2ItemStackCompat.isEmpty(pendingPlacement.stack)) {
            drops.add(Ae2ItemStackCompat.copy(pendingPlacement.stack));
        }
        return drops;
    }

    @Override
    public SoundType getPlacementSound(ItemStack stack) {
        Block cableBlock = ae2CableBusBlock();
        if (cableBlock == null) {
            return null;
        }
        IBlockState state = Ae2MinecraftCompat.getDefaultState(cableBlock);
        return Ae2MinecraftCompat.getSoundType(cableBlock, state, world(), tile() == null ? null : pos(), null);
    }

    @Override
    public ItemStack pickItem(CuboidRayTraceResult hit) {
        Vec3d localHit = localHit(hit);
        SelectedPart selected = cableBus.selectPart(localHit);
        if (selected.part != null) {
            return selected.part.getItemStack(PartItemStack.PICK);
        }
        if (selected.facade != null) {
            return selected.facade.getItemStack();
        }
        for (ItemStack stack : getDrops()) {
            return Ae2ItemStackCompat.copy(stack);
        }
        return Ae2ItemStackCompat.emptyStack();
    }

    @Override
    public boolean activate(EntityPlayer player, CuboidRayTraceResult hit, ItemStack held, EnumHand hand) {
        Vec3d localHit = localHit(hit);
        if (cableBus.activate(player, hand, localHit)) {
            return true;
        }
        return activateSidePartFallback(player, hand, hit, localHit);
    }

    @Override
    public void click(EntityPlayer player, CuboidRayTraceResult hit, ItemStack held) {
        cableBus.clicked(player, EnumHand.MAIN_HAND, localHit(hit));
    }

    @Override
    public void onEntityCollision(Entity entity) {
        cableBus.onEntityCollision(entity);
    }

    @Override
    public void onNeighborChanged() {
        if (world() == null) {
            return;
        }
        cableBus.onNeighborChanged(world(), pos(), pos());
        refreshConnectionsAndShape();
    }

    @Override
    public void onNeighborBlockChanged(BlockPos neighbor) {
        if (world() == null) {
            return;
        }
        cableBus.onNeighborChanged(world(), pos(), neighbor);
        refreshConnectionsAndShape();
    }

    @Override
    public void onPartChanged(TMultiPart part) {
        refreshConnectionsAndShape(false);
        schedulePostLoadRefreshes();
    }

    @Override
    public void scheduledTick() {
        postLoadRefreshScheduled = false;
        if (tile() == null || world() == null || Ae2MinecraftCompat.isRemote(world())) {
            postLoadRefreshesRemaining = 0;
            return;
        }

        joinWorldIfPossible();
        refreshConnectionsAndShape(false);
        refreshAdjacentHostedCables(false);
        notifyNeighbors();

        postLoadRefreshesRemaining--;
        tickDeferredDescSync();
        if (postLoadRefreshesRemaining > 0) {
            schedulePostLoadRefreshTick(postLoadRefreshDelay());
        } else if (deferredDescSyncTicks >= 0) {
            schedulePostLoadRefreshTick(1);
        }
    }

    @Override
    public int getLightValue() {
        return cableBus.getLightValue();
    }

    @Override
    public boolean canPlaceTorchOnTop() {
        return cableBus.isSolidOnSide(EnumFacing.UP);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        IPart part = partForCapabilitySide(facing);
        return part != null && part.hasCapability(capability);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        IPart part = partForCapabilitySide(facing);
        return part == null ? null : part.getCapability(capability);
    }

    @Override
    public IFacadeContainer getFacadeContainer() {
        return cableBus.getFacadeContainer();
    }

    @Override
    public boolean canAddPart(ItemStack stack, AEPartLocation location) {
        return cableBus.canAddPart(stack, location);
    }

    @Override
    public AEPartLocation addPart(ItemStack stack, AEPartLocation location, EntityPlayer player, EnumHand hand) {
        return cableBus.addPart(stack, location, player, hand);
    }

    @Override
    public IPart getPart(AEPartLocation location) {
        return cableBus.getPart(location);
    }

    @Override
    public IPart getPart(EnumFacing side) {
        return cableBus.getPart(side);
    }

    @Override
    public void removePart(AEPartLocation location, boolean suppressUpdate) {
        cableBus.removePart(location, suppressUpdate);
    }

    @Override
    public void markForUpdate() {
        if (tile() == null || world() == null) {
            return;
        }
        tile().markRender();
        if (!suppressDescUpdates && !Ae2MinecraftCompat.isRemote(world())) {
            sendDescUpdate();
        }
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(world(), pos());
    }

    @Override
    public TileEntity getTile() {
        return tile();
    }

    @Override
    public AEColor getColor() {
        return cableBus.getColor();
    }

    @Override
    public void clearContainer() {
        // CableBusContainer.setHost calls the old host's clearContainer().
        // This multipart owns a final container, so delegating back into AE2 would throw.
    }

    @Override
    public boolean isBlocked(EnumFacing side) {
        if (side == null || tile() == null) {
            return false;
        }

        Cuboid6 channel = sideChannel(side);
        for (TMultiPart other : tile().jPartList()) {
            if (other == this || other == null) {
                continue;
            }
            if (intersectsAny(channel, occlusionBoxesFor(other))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public SelectedPart selectPart(Vec3d hit) {
        return cableBus.selectPart(hit);
    }

    @Override
    public void markForSave() {
        if (tile() == null || world() == null || Ae2MinecraftCompat.isRemote(world())) {
            return;
        }
        Ae2MinecraftCompat.markDirty(tile());
    }

    @Override
    public void partChanged() {
        markForUpdate();
        markForSave();
        notifyNeighbors();
    }

    @Override
    public boolean hasRedstone(AEPartLocation location) {
        return cableBus.hasRedstone(location);
    }

    @Override
    public boolean isEmpty() {
        return cableBus.isEmpty();
    }

    @Override
    public void cleanup() {
        leaveWorldIfNeeded();
    }

    @Override
    public void notifyNeighbors() {
        if (tile() == null || world() == null) {
            return;
        }
        World world = world();
        BlockPos pos = pos();
        Block block = Ae2MinecraftCompat.getBlock(Ae2MinecraftCompat.getBlockState(world, pos));
        Ae2MinecraftCompat.notifyNeighborsOfStateChange(world, pos, block, false);
    }

    @Override
    public boolean isInWorld() {
        return cableBus.isInWorld();
    }

    @Override
    public float getCableConnectionLength(AECableType cableType) {
        return cableBus.getCableConnectionLength(cableType);
    }

    @Override
    public IGridNode getGridNode(AEPartLocation location) {
        return cableBus.getGridNode(location);
    }

    @Override
    public AECableType getCableConnectionType(AEPartLocation location) {
        return cableBus.getCableConnectionType(location);
    }

    @Override
    public void securityBreak() {
        cableBus.securityBreak();
    }

    private void applyPendingCableBusNbt() {
        if (pendingCableBusNbt == null || tile() == null || world() == null) {
            return;
        }
        cableBus.readFromNBT(pendingCableBusNbt);
        pendingCableBusNbt = null;
    }

    private void applyPendingPlacement() {
        if (pendingPlacement == null || tile() == null || world() == null) {
            return;
        }
        PendingPlacement placement = pendingPlacement;
        pendingPlacement = null;
        AEPartLocation addedAt = cableBus.addPart(Ae2ItemStackCompat.copy(placement.stack), placement.location, placement.player, placement.hand);
        if (addedAt == null) {
            GPOM.LOGGER.warn("[GPOM Multipart] Failed to add staged AE2 part {} at {} inside hosted cable bus", placement.stack, placement.location);
        }
    }

    private void restorePendingState() {
        applyPendingCableBusNbt();
        applyPendingPlacement();
    }

    private void joinWorldIfPossible() {
        if (tile() == null || world() == null || cableBus.isInWorld()) {
            return;
        }
        cableBus.addToWorld();
        refreshConnectionsAndShape(false);
    }

    private void schedulePostLoadRefreshes() {
        if (tile() == null || world() == null || Ae2MinecraftCompat.isRemote(world())) {
            return;
        }
        postLoadRefreshesRemaining = Math.max(postLoadRefreshesRemaining, POST_LOAD_REFRESH_PASSES);
        schedulePostLoadRefreshTick(1);
    }

    private void schedulePostLoadRefreshTick(int ticks) {
        if (!postLoadRefreshScheduled) {
            postLoadRefreshScheduled = true;
            scheduleTick(ticks);
        }
    }

    private int postLoadRefreshDelay() {
        if (postLoadRefreshesRemaining > 8) {
            return 2;
        }
        return postLoadRefreshesRemaining > 4 ? 10 : 20;
    }

    private void resetPostLoadRefreshes() {
        postLoadRefreshesRemaining = 0;
        deferredDescSyncTicks = -1;
        postLoadRefreshScheduled = false;
    }

    private void leaveWorldIfNeeded() {
        if (!cableBus.isInWorld()) {
            return;
        }
        cableBus.removeFromWorld();
    }

    private void refreshConnectionsAndShape() {
        refreshConnectionsAndShape(true);
    }

    private void refreshConnectionsAndShape(boolean networkUpdate) {
        if (tile() == null || world() == null) {
            return;
        }
        cableBus.updateConnections();
        if (networkUpdate) {
            markForUpdate();
        } else {
            markRenderOnly();
        }
        markForSave();
    }

    private void refreshAdjacentHostedCables() {
        refreshAdjacentHostedCables(true);
    }

    private void refreshAdjacentHostedCables(boolean networkUpdate) {
        if (world() == null || pos() == null) {
            return;
        }
        for (EnumFacing side : EnumFacing.values()) {
            Ae2CableBusMultipart adjacent = adjacentHostedCable(side);
            if (adjacent != null) {
                adjacent.refreshConnectionsAndShape(networkUpdate);
            }
        }
    }

    void afterManualPlacementRefresh() {
        refreshConnectionsAndShape(false);
        refreshAdjacentHostedCables(false);
        notifyNeighbors();
        requestDeferredDescSync(1);
    }

    Ae2CableBusMultipart adjacentHostedCable(EnumFacing side) {
        BlockPos adjacentPos = adjacentPos(side);
        if (adjacentPos == null) {
            return null;
        }
        Object host = Ae2MultipartGridHostBridge.findGridHost(Ae2MinecraftCompat.getTileEntity(world(), adjacentPos));
        return host instanceof Ae2CableBusMultipart ? (Ae2CableBusMultipart) host : null;
    }

    boolean allowsExternalRenderConnection(EnumFacing side) {
        return side != null && getPart(side) == null && !isBlocked(side);
    }

    AECableType internalCableConnectionType() {
        return cableBus.getCableConnectionType(AEPartLocation.INTERNAL);
    }

    boolean selectsExistingAttachment(double hitX, double hitY, double hitZ) {
        SelectedPart selected = cableBus.selectPart(new Vec3d(hitX, hitY, hitZ));
        return selected.facade != null || (selected.part != null && selected.side != AEPartLocation.INTERNAL);
    }

    private boolean activateSidePartFallback(EntityPlayer player, EnumHand hand, CuboidRayTraceResult hit, Vec3d localHit) {
        SelectedPart selected = cableBus.selectPart(localHit);
        if (selected != null && selected.side != null && selected.side != AEPartLocation.INTERNAL
                && activatePartAt(selected.side, player, hand, localHit)) {
            return true;
        }

        EnumFacing hitSide = Ae2MinecraftCompat.hitSide(hit);
        if (hitSide != null && activatePartAt(AEPartLocation.fromFacing(hitSide), player, hand, localHit)) {
            return true;
        }

        AEPartLocation nearest = sideFromLocalHit(localHit);
        if (nearest != null && activatePartAt(nearest, player, hand, localHit)) {
            return true;
        }

        for (AEPartLocation location : AEPartLocation.SIDE_LOCATIONS) {
            if (location != nearest && activatePartAt(location, player, hand, localHit)) {
                return true;
            }
        }
        return false;
    }

    private boolean activatePartAt(AEPartLocation location, EntityPlayer player, EnumHand hand, Vec3d localHit) {
        if (location == null || location == AEPartLocation.INTERNAL) {
            return false;
        }
        IPart part = cableBus.getPart(location);
        if (part == null) {
            return false;
        }
        if (player != null && player.isSneaking() && part.onShiftActivate(player, hand, localHit)) {
            return true;
        }
        return part.onActivate(player, hand, localHit);
    }

    private static AEPartLocation sideFromLocalHit(Vec3d hit) {
        if (hit == null) {
            return null;
        }

        double x = Ae2MinecraftCompat.x(hit);
        double y = Ae2MinecraftCompat.y(hit);
        double z = Ae2MinecraftCompat.z(hit);
        double best = SIDE_ACTIVATION_EPSILON;
        AEPartLocation side = null;
        double distance = x;
        if (distance <= best) {
            best = distance;
            side = AEPartLocation.WEST;
        }
        distance = 1.0D - x;
        if (distance <= best) {
            best = distance;
            side = AEPartLocation.EAST;
        }
        distance = y;
        if (distance <= best) {
            best = distance;
            side = AEPartLocation.DOWN;
        }
        distance = 1.0D - y;
        if (distance <= best) {
            best = distance;
            side = AEPartLocation.UP;
        }
        distance = z;
        if (distance <= best) {
            best = distance;
            side = AEPartLocation.NORTH;
        }
        distance = 1.0D - z;
        if (distance <= best) {
            side = AEPartLocation.SOUTH;
        }
        return side;
    }

    private BlockPos adjacentPos(EnumFacing side) {
        BlockPos base = pos();
        if (base == null || side == null) {
            return null;
        }
        int x = Ae2MinecraftCompat.x(base);
        int y = Ae2MinecraftCompat.y(base);
        int z = Ae2MinecraftCompat.z(base);
        switch (side) {
            case DOWN:
                return new BlockPos(x, y - 1, z);
            case UP:
                return new BlockPos(x, y + 1, z);
            case NORTH:
                return new BlockPos(x, y, z - 1);
            case SOUTH:
                return new BlockPos(x, y, z + 1);
            case WEST:
                return new BlockPos(x - 1, y, z);
            case EAST:
            default:
                return new BlockPos(x + 1, y, z);
        }
    }

    private void markRenderOnly() {
        if (tile() != null && world() != null) {
            tile().markRender();
        }
    }

    private void requestDeferredDescSync(int ticks) {
        if (tile() == null || world() == null || Ae2MinecraftCompat.isRemote(world())) {
            return;
        }
        int delay = Math.max(0, ticks);
        if (deferredDescSyncTicks < 0 || delay < deferredDescSyncTicks) {
            deferredDescSyncTicks = delay;
        }
        schedulePostLoadRefreshTick(1);
    }

    private void tickDeferredDescSync() {
        if (deferredDescSyncTicks < 0) {
            return;
        }
        if (deferredDescSyncTicks > 0) {
            deferredDescSyncTicks--;
            return;
        }
        deferredDescSyncTicks = -1;
        markForUpdate();
    }

    private void runWithoutDescUpdates(Runnable runnable) {
        boolean previous = suppressDescUpdates;
        suppressDescUpdates = true;
        try {
            runnable.run();
        } finally {
            suppressDescUpdates = previous;
        }
    }

    private void writeCableBusStream(NBTTagCompound tag) {
        ByteBuf buffer = Unpooled.buffer();
        try {
            cableBus.writeToStream(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            Ae2MinecraftCompat.setByteArray(tag, TAG_CABLE_BUS_STREAM, bytes);
        } catch (IOException | RuntimeException throwable) {
            if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
                GPOM.LOGGER.warn("[GPOM Multipart] Failed to write AE2 cable bus stream for hosted multipart", throwable);
            }
        } finally {
            buffer.release();
        }
    }

    private void readCableBusStream(NBTTagCompound tag) {
        byte[] bytes = Ae2MinecraftCompat.getByteArray(tag, TAG_CABLE_BUS_STREAM);
        if (bytes.length == 0 || tile() == null || world() == null) {
            return;
        }
        ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
        try {
            if (cableBus.readFromStream(buffer)) {
                markRenderOnly();
            }
        } catch (IOException | RuntimeException throwable) {
            if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
                GPOM.LOGGER.warn("[GPOM Multipart] Failed to read AE2 cable bus stream for hosted multipart", throwable);
            }
        } finally {
            buffer.release();
        }
    }

    private List<Cuboid6> currentCuboids(boolean visual) {
        if (tile() == null) {
            return Collections.singletonList(new Cuboid6(FALLBACK_BOUNDS));
        }

        List<Cuboid6> cuboids = new ArrayList<>();
        for (AxisAlignedBB box : cableBus.getSelectedBoundingBoxesFromPool(false, true, null, visual)) {
            cuboids.add(new Cuboid6(box));
        }
        if (cuboids.isEmpty()) {
            cuboids.add(new Cuboid6(FALLBACK_BOUNDS));
        }
        return cuboids;
    }

    private IPart partForCapabilitySide(EnumFacing side) {
        if (side != null) {
            IPart sidePart = cableBus.getPart(side);
            if (sidePart != null) {
                return sidePart;
            }
        }
        return cableBus.getPart(AEPartLocation.INTERNAL);
    }

    private Vec3d localHit(CuboidRayTraceResult hit) {
        Vec3d vec = Ae2MinecraftCompat.hitVec(hit);
        if (vec == null) {
            return new Vec3d(0.5D, 0.5D, 0.5D);
        }
        BlockPos pos = pos();
        double x = Ae2MinecraftCompat.x(vec);
        double y = Ae2MinecraftCompat.y(vec);
        double z = Ae2MinecraftCompat.z(vec);
        if (x >= 0.0D && x <= 1.0D && y >= 0.0D && y <= 1.0D && z >= 0.0D && z <= 1.0D) {
            return vec;
        }
        return new Vec3d(
                x - Ae2MinecraftCompat.x(pos),
                y - Ae2MinecraftCompat.y(pos),
                z - Ae2MinecraftCompat.z(pos)
        );
    }

    private static ItemStack copyOne(ItemStack stack) {
        if (Ae2ItemStackCompat.isEmpty(stack)) {
            return Ae2ItemStackCompat.emptyStack();
        }
        ItemStack copy = Ae2ItemStackCompat.copy(stack);
        Ae2ItemStackCompat.setCount(copy, 1);
        return copy;
    }

    private void writePendingPlacement(NBTTagCompound tag) {
        if (pendingPlacement == null || Ae2ItemStackCompat.isEmpty(pendingPlacement.stack)) {
            return;
        }
        NBTTagCompound stackTag = Ae2ItemStackCompat.writeToNbt(pendingPlacement.stack);
        if (stackTag == null) {
            return;
        }
        NBTTagCompound placementTag = new NBTTagCompound();
        Ae2MinecraftCompat.setTag(placementTag, TAG_PENDING_STACK, stackTag);
        Ae2MinecraftCompat.setInteger(placementTag, TAG_PENDING_LOCATION, locationOrInternal(pendingPlacement.location).ordinal());
        Ae2MinecraftCompat.setTag(tag, TAG_PENDING_PLACEMENT, placementTag);
    }

    private static PendingPlacement readPendingPlacement(NBTTagCompound tag) {
        if (!Ae2MinecraftCompat.hasKey(tag, TAG_PENDING_PLACEMENT)) {
            return null;
        }
        NBTTagCompound placementTag = Ae2MinecraftCompat.getCompoundTag(tag, TAG_PENDING_PLACEMENT);
        if (!Ae2MinecraftCompat.hasKey(placementTag, TAG_PENDING_STACK)) {
            return null;
        }
        ItemStack stack = Ae2ItemStackCompat.readFromNbt(Ae2MinecraftCompat.getCompoundTag(placementTag, TAG_PENDING_STACK));
        if (Ae2ItemStackCompat.isEmpty(stack)) {
            return null;
        }
        int locationOrdinal = Ae2MinecraftCompat.getInteger(placementTag, TAG_PENDING_LOCATION, AEPartLocation.INTERNAL.ordinal());
        return new PendingPlacement(copyOne(stack), locationFromOrdinal(locationOrdinal), null, EnumHand.MAIN_HAND);
    }

    private static AEPartLocation locationFromOrdinal(int ordinal) {
        AEPartLocation[] values = AEPartLocation.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : AEPartLocation.INTERNAL;
    }

    private static AEPartLocation locationOrInternal(AEPartLocation location) {
        return location == null ? AEPartLocation.INTERNAL : location;
    }

    private static Cuboid6 sideChannel(EnumFacing side) {
        switch (side) {
            case DOWN:
                return new Cuboid6(SIDE_CHANNEL_MIN, 0.0D, SIDE_CHANNEL_MIN, SIDE_CHANNEL_MAX, 0.5D, SIDE_CHANNEL_MAX);
            case UP:
                return new Cuboid6(SIDE_CHANNEL_MIN, 0.5D, SIDE_CHANNEL_MIN, SIDE_CHANNEL_MAX, 1.0D, SIDE_CHANNEL_MAX);
            case NORTH:
                return new Cuboid6(SIDE_CHANNEL_MIN, SIDE_CHANNEL_MIN, 0.0D, SIDE_CHANNEL_MAX, SIDE_CHANNEL_MAX, 0.5D);
            case SOUTH:
                return new Cuboid6(SIDE_CHANNEL_MIN, SIDE_CHANNEL_MIN, 0.5D, SIDE_CHANNEL_MAX, SIDE_CHANNEL_MAX, 1.0D);
            case WEST:
                return new Cuboid6(0.0D, SIDE_CHANNEL_MIN, SIDE_CHANNEL_MIN, 0.5D, SIDE_CHANNEL_MAX, SIDE_CHANNEL_MAX);
            case EAST:
            default:
                return new Cuboid6(0.5D, SIDE_CHANNEL_MIN, SIDE_CHANNEL_MIN, 1.0D, SIDE_CHANNEL_MAX, SIDE_CHANNEL_MAX);
        }
    }

    private static Iterable<Cuboid6> occlusionBoxesFor(TMultiPart part) {
        if (part instanceof TMicroOcclusion) {
            return Collections.singletonList(((TMicroOcclusion) part).getBounds());
        }
        if (part instanceof TNormalOcclusionPart) {
            return ((TNormalOcclusionPart) part).getOcclusionBoxes();
        }
        if (part instanceof TPartialOcclusionPart) {
            return ((TPartialOcclusionPart) part).getPartialOcclusionBoxes();
        }
        return part.getCollisionBoxes();
    }

    private static boolean intersectsAny(Cuboid6 channel, Iterable<Cuboid6> boxes) {
        if (channel == null || boxes == null) {
            return false;
        }
        for (Cuboid6 box : boxes) {
            if (box != null && channel.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private static Block ae2CableBusBlock() {
        Optional<Block> block = AEApi.instance().definitions().blocks().multiPart().maybeBlock();
        return block.orElse(null);
    }

    private static final class PendingPlacement {
        private final ItemStack stack;
        private final AEPartLocation location;
        private final EntityPlayer player;
        private final EnumHand hand;

        private PendingPlacement(ItemStack stack, AEPartLocation location, EntityPlayer player, EnumHand hand) {
            this.stack = stack;
            this.location = location;
            this.player = player;
            this.hand = hand;
        }
    }
}
