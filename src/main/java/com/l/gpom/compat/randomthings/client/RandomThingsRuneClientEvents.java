package com.l.gpom.compat.randomthings.client;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import com.l.gpom.compat.randomthings.RandomThingsRuneNetwork;
import com.l.gpom.compat.randomthings.RandomThingsRuneSettings;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public final class RandomThingsRuneClientEvents {
    private static boolean registered;
    private static final double PREVIEW_OFFSET = 0.002D;
    private static final int MAX_EXACT_PREVIEW_GRID = 64;
    private static final int SETTINGS_SYNC_RETRY_TICKS = 400;
    private static final int SETTINGS_SYNC_INTERVAL_TICKS = 20;
    private static int pendingSettingsSyncTicks;
    private static int settingsSyncCooldownTicks;

    private RandomThingsRuneClientEvents() {
    }

    public static void register(FMLPreInitializationEvent event) {
        if (registered || !GpomEarlyConfig.randomThingsImprovedRunicDustEnabled() || !Loader.isModLoaded("randomthings")) {
            return;
        }
        registered = true;
        RandomThingsRuneSettings.setSettingsFile(event.getModConfigurationDirectory());
        RandomThingsRuneSettings.ensureClientLoaded();
        MinecraftForge.EVENT_BUS.register(new RandomThingsRuneClientEvents());
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        pendingSettingsSyncTicks = SETTINGS_SYNC_RETRY_TICKS;
        settingsSyncCooldownTicks = 0;
        RandomThingsRuneSettings.ensureClientLoaded();
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        pendingSettingsSyncTicks = 0;
        settingsSyncCooldownTicks = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingSettingsSyncTicks <= 0) {
            return;
        }
        pendingSettingsSyncTicks--;
        Minecraft minecraft = ClientAccess.minecraft();
        Object playerValue = ClientAccess.player(minecraft);
        if (!(playerValue instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer player = (EntityPlayer) playerValue;
        if (MinecraftMappingCompat.playerWorld(player) == null) {
            return;
        }
        if (settingsSyncCooldownTicks > 0) {
            settingsSyncCooldownTicks--;
            return;
        }
        if (RandomThingsRuneNetwork.sendClientSettingsToServer()) {
            settingsSyncCooldownTicks = SETTINGS_SYNC_INTERVAL_TICKS;
        }
    }

    @SubscribeEvent
    public void onRightClickRuneDust(PlayerInteractEvent.RightClickItem event) {
        if (!RandomThingsRuneCompat.enabled()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !MinecraftMappingCompat.playerIsSneaking(player)) {
            return;
        }
        if (event.getWorld() == null || !MinecraftMappingCompat.worldIsRemote(event.getWorld())) {
            return;
        }
        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, event.getHand());
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)
                || !RandomThingsRuneCompat.isRuneDust(MinecraftMappingCompat.itemStackItem(stack))) {
            return;
        }
        if (RandomThingsRuneCompat.openSettingsScreen(player, MinecraftMappingCompat.itemStackMetadata(stack))) {
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickRuneTarget(PlayerInteractEvent.RightClickBlock event) {
        if (!RandomThingsRuneCompat.enabled()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !MinecraftMappingCompat.playerIsSneaking(player)) {
            return;
        }
        if (event.getWorld() == null || !MinecraftMappingCompat.worldIsRemote(event.getWorld())) {
            return;
        }
        int rune = -1;
        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, event.getHand());
        if (!MinecraftMappingCompat.itemStackIsEmpty(stack)
                && RandomThingsRuneCompat.isRuneDust(MinecraftMappingCompat.itemStackItem(stack))) {
            rune = MinecraftMappingCompat.itemStackMetadata(stack);
        } else if (RandomThingsRuneCompat.isRuneBaseAt(event.getWorld(), event.getPos())) {
            float hitX = 0.5F;
            float hitZ = 0.5F;
            Vec3d hit = event.getHitVec();
            BlockPos pos = event.getPos();
            if (hit != null && pos != null) {
                hitX = (float) (MinecraftMappingCompat.vecX(hit) - MinecraftMappingCompat.blockPosX(pos));
                hitZ = (float) (MinecraftMappingCompat.vecZ(hit) - MinecraftMappingCompat.blockPosZ(pos));
            }
            rune = RandomThingsRuneCompat.runeAt(event.getWorld(), event.getPos(), hitX, hitZ, 0);
        }
        if (rune >= 0 && RandomThingsRuneCompat.openSettingsScreen(player, rune)) {
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onDrawRunePlacementPreview(DrawBlockHighlightEvent event) {
        if (!RandomThingsRuneCompat.enabled()) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        RayTraceResult target = event.getTarget();
        if (player == null || target == null || !MinecraftMappingCompat.rayTraceIsBlock(target)) {
            return;
        }
        ItemStack stack = runeDustInHand(player);
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return;
        }
        World world = MinecraftMappingCompat.playerWorld(player);
        BlockPos pos = MinecraftMappingCompat.rayTraceBlockPos(target);
        EnumFacing face = MinecraftMappingCompat.rayTraceSideHit(target);
        Vec3d hit = MinecraftMappingCompat.rayTraceHitVec(target);
        PreviewTarget preview = previewTarget(world, pos, face, hit);
        if (preview == null) {
            return;
        }
        drawPreview(player, preview.supportPos, preview.face, preview.hitX, preview.hitY, preview.hitZ, stack, event.getPartialTicks());
    }

    private static ItemStack runeDustInHand(EntityPlayer player) {
        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, EnumHand.MAIN_HAND);
        if (!MinecraftMappingCompat.itemStackIsEmpty(stack)
                && RandomThingsRuneCompat.isRuneDust(MinecraftMappingCompat.itemStackItem(stack))) {
            return stack;
        }
        stack = MinecraftMappingCompat.playerHeldItem(player, EnumHand.OFF_HAND);
        return !MinecraftMappingCompat.itemStackIsEmpty(stack)
                && RandomThingsRuneCompat.isRuneDust(MinecraftMappingCompat.itemStackItem(stack)) ? stack : null;
    }

    private static PreviewTarget previewTarget(World world, BlockPos pos, EnumFacing face, Vec3d hit) {
        if (world == null || pos == null || face == null || hit == null) {
            return null;
        }
        if (RandomThingsRuneCompat.isRuneBaseAt(world, pos)) {
            EnumFacing runeFace = RandomThingsRuneCompat.runeFaceAt(world, pos);
            BlockPos supportPos = MinecraftMappingCompat.blockPosOffset(pos, opposite(runeFace));
            IBlockState support = MinecraftMappingCompat.worldBlockState(world, supportPos);
            if (support == null || !MinecraftMappingCompat.blockStateSideSolid(support, world, supportPos, runeFace)) {
                return null;
            }
            return new PreviewTarget(supportPos, runeFace, hitX(hit, supportPos), hitY(hit, supportPos), hitZ(hit, supportPos));
        }
        if (!canPreviewPlacement(world, pos, face)) {
            return null;
        }
        return new PreviewTarget(pos, face, hitX(hit, pos), hitY(hit, pos), hitZ(hit, pos));
    }

    private static boolean canPreviewPlacement(World world, BlockPos pos, EnumFacing face) {
        IBlockState support = MinecraftMappingCompat.worldBlockState(world, pos);
        if (support == null || !MinecraftMappingCompat.blockStateSideSolid(support, world, pos, face)) {
            return false;
        }
        BlockPos runePos = MinecraftMappingCompat.blockPosOffset(pos, face);
        if (RandomThingsRuneCompat.isRuneBaseAt(world, runePos)) {
            return true;
        }
        IBlockState replace = MinecraftMappingCompat.worldBlockState(world, runePos);
        Block block = MinecraftMappingCompat.blockStateBlock(replace);
        return block != null && (MinecraftMappingCompat.blockIsAir(block, replace, world, runePos)
                || MinecraftMappingCompat.blockIsReplaceable(block, world, runePos));
    }

    private static float hitX(Vec3d hit, BlockPos pos) {
        return (float) (MinecraftMappingCompat.vecX(hit) - MinecraftMappingCompat.blockPosX(pos));
    }

    private static float hitY(Vec3d hit, BlockPos pos) {
        return (float) (MinecraftMappingCompat.vecY(hit) - MinecraftMappingCompat.blockPosY(pos));
    }

    private static float hitZ(Vec3d hit, BlockPos pos) {
        return (float) (MinecraftMappingCompat.vecZ(hit) - MinecraftMappingCompat.blockPosZ(pos));
    }

    private static void drawPreview(EntityPlayer player, BlockPos pos, EnumFacing face, float hitX, float hitY, float hitZ,
                                    ItemStack stack, float partialTicks) {
        int rune = RandomThingsRuneSettings.clampRune(MinecraftMappingCompat.itemStackMetadata(stack));
        RandomThingsRuneSettings.RuneSettings settings = RandomThingsRuneSettings.client(rune);
        int size = settings.resolution;
        double prevX = MinecraftMappingCompat.entityPrevPosX(player);
        double prevY = MinecraftMappingCompat.entityPrevPosY(player);
        double prevZ = MinecraftMappingCompat.entityPrevPosZ(player);
        double cameraX = prevX + (MinecraftMappingCompat.entityPosX(player) - prevX) * partialTicks;
        double cameraY = prevY + (MinecraftMappingCompat.entityPosY(player) - prevY) * partialTicks;
        double cameraZ = prevZ + (MinecraftMappingCompat.entityPosZ(player) - prevZ) * partialTicks;

        boolean drawing = false;
        MinecraftMappingCompat.glPushMatrix();
        try {
            MinecraftMappingCompat.glDisableTexture2D();
            MinecraftMappingCompat.glDisableLighting();
            MinecraftMappingCompat.glEnableBlend();
            MinecraftMappingCompat.glTryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0);
            MinecraftMappingCompat.glDepthMask(false);
            GL11.glLineWidth(1.0F);

            GL11.glBegin(GL11.GL_LINES);
            drawing = true;
            try {
                int[] cellHit = RandomThingsRuneCompat.cellCoordinatesFromHit(size, face, hitX, hitY, hitZ);
                int centerX = clamp(cellHit[0], 0, size - 1);
                int centerZ = clamp(cellHit[1], 0, size - 1);
                int radiusBefore = (settings.brush - 1) / 2;
                int radiusAfter = settings.brush / 2;
                double cell = 1.0D / size;
                if (size <= MAX_EXACT_PREVIEW_GRID) {
                    for (int x = 0; x < size; x++) {
                        for (int z = 0; z < size; z++) {
                            double[] bounds = previewPixelBounds(x, z, cell, settings);
                            addPreviewRect(pos, face, bounds[0], bounds[1], bounds[2], bounds[3], cameraX, cameraY, cameraZ,
                                    0.35F, 0.95F, 1.0F, 0.58F);
                        }
                    }
                } else {
                    for (int i = 0; i <= size; i++) {
                        double t = (double) i / (double) size;
                        addPreviewLine(pos, face, t, 0.0D, t, 1.0D, cameraX, cameraY, cameraZ, 0.35F, 0.95F, 1.0F, 0.45F);
                        addPreviewLine(pos, face, 0.0D, t, 1.0D, t, cameraX, cameraY, cameraZ, 0.35F, 0.95F, 1.0F, 0.45F);
                    }
                }
                for (int x = Math.max(0, centerX - radiusBefore); x <= Math.min(size - 1, centerX + radiusAfter); x++) {
                    for (int z = Math.max(0, centerZ - radiusBefore); z <= Math.min(size - 1, centerZ + radiusAfter); z++) {
                        double[] bounds = previewPixelBounds(x, z, cell, settings);
                        addPreviewRect(pos, face, bounds[0], bounds[1], bounds[2], bounds[3], cameraX, cameraY, cameraZ,
                                1.0F, 0.85F, 0.20F, 0.95F);
                    }
                }
            } finally {
                GL11.glEnd();
                drawing = false;
            }
        } finally {
            if (drawing) {
                GL11.glEnd();
            }
            MinecraftMappingCompat.glDepthMask(true);
            MinecraftMappingCompat.glDisableBlend();
            MinecraftMappingCompat.glEnableLighting();
            MinecraftMappingCompat.glEnableTexture2D();
            MinecraftMappingCompat.glPopMatrix();
        }
    }

    private static double[] previewPixelBounds(int x, int z, double cell, RandomThingsRuneSettings.RuneSettings settings) {
        double scale = settings.visualScale / 100.0D;
        double padding = settings.visualPadding / 100.0D;
        double piece = cell * Math.max(0.08D, Math.min(0.8D, scale));
        double inset = Math.max(0.0D, Math.min(cell * 0.46D, cell * padding));
        piece = Math.min(piece, cell - inset * 2.0D);
        if (piece <= 0.0D) {
            piece = cell * 0.5D;
            inset = (cell - piece) * 0.5D;
        }
        double x1 = x * cell + inset;
        double z1 = z * cell + inset;
        return new double[]{x1, z1, x1 + piece, z1 + piece};
    }

    private static void addPreviewRect(BlockPos pos, EnumFacing face, double x1, double z1, double x2, double z2,
                                       double cameraX, double cameraY, double cameraZ, float r, float g, float b, float a) {
        addPreviewLine(pos, face, x1, z1, x2, z1, cameraX, cameraY, cameraZ, r, g, b, a);
        addPreviewLine(pos, face, x2, z1, x2, z2, cameraX, cameraY, cameraZ, r, g, b, a);
        addPreviewLine(pos, face, x2, z2, x1, z2, cameraX, cameraY, cameraZ, r, g, b, a);
        addPreviewLine(pos, face, x1, z2, x1, z1, cameraX, cameraY, cameraZ, r, g, b, a);
    }

    private static void addPreviewLine(BlockPos pos, EnumFacing face, double u1, double v1, double u2, double v2,
                                       double cameraX, double cameraY, double cameraZ, float r, float g, float b, float a) {
        double[] aPos = previewPoint(pos, face, u1, v1, cameraX, cameraY, cameraZ);
        double[] bPos = previewPoint(pos, face, u2, v2, cameraX, cameraY, cameraZ);
        GL11.glColor4f(r, g, b, a);
        GL11.glVertex3d(aPos[0], aPos[1], aPos[2]);
        GL11.glVertex3d(bPos[0], bPos[1], bPos[2]);
    }

    private static double[] previewPoint(BlockPos pos, EnumFacing face, double u, double v, double cameraX, double cameraY, double cameraZ) {
        double x = MinecraftMappingCompat.blockPosX(pos);
        double y = MinecraftMappingCompat.blockPosY(pos);
        double z = MinecraftMappingCompat.blockPosZ(pos);
        switch (face) {
            case DOWN:
                return new double[]{x + u - cameraX, y - PREVIEW_OFFSET - cameraY, z + v - cameraZ};
            case NORTH:
                return new double[]{x + u - cameraX, y + 1.0D - v - cameraY, z - PREVIEW_OFFSET - cameraZ};
            case SOUTH:
                return new double[]{x + u - cameraX, y + 1.0D - v - cameraY, z + 1.0D + PREVIEW_OFFSET - cameraZ};
            case WEST:
                return new double[]{x - PREVIEW_OFFSET - cameraX, y + 1.0D - v - cameraY, z + u - cameraZ};
            case EAST:
                return new double[]{x + 1.0D + PREVIEW_OFFSET - cameraX, y + 1.0D - v - cameraY, z + u - cameraZ};
            case UP:
            default:
                return new double[]{x + u - cameraX, y + 1.0D + PREVIEW_OFFSET - cameraY, z + v - cameraZ};
        }
    }

    private static EnumFacing opposite(EnumFacing face) {
        EnumFacing renderFace = face == null ? EnumFacing.UP : face;
        switch (renderFace) {
            case DOWN:
                return EnumFacing.UP;
            case NORTH:
                return EnumFacing.SOUTH;
            case SOUTH:
                return EnumFacing.NORTH;
            case WEST:
                return EnumFacing.EAST;
            case EAST:
                return EnumFacing.WEST;
            case UP:
            default:
                return EnumFacing.DOWN;
        }
    }

    private static final class PreviewTarget {
        private final BlockPos supportPos;
        private final EnumFacing face;
        private final float hitX;
        private final float hitY;
        private final float hitZ;

        private PreviewTarget(BlockPos supportPos, EnumFacing face, float hitX, float hitY, float hitZ) {
            this.supportPos = supportPos;
            this.face = face;
            this.hitX = hitX;
            this.hitY = hitY;
            this.hitZ = hitZ;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
