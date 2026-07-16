package com.l.gpom.compat.twilightforest;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TwilightForestPortalDimensionCompat {
    private static final Logger PROBE_LOGGER = LogManager.getLogger("GPOM Twilight Forest Portal Probe");
    private static final long PROBE_INTERVAL_NANOS = 5_000_000_000L;
    private static volatile Field twilightAllowPortalsInOtherDimensionsField;
    private static volatile Method canFormPortalMethod;
    private static final Map<String, Long> LAST_PROBES = new ConcurrentHashMap<>();

    private TwilightForestPortalDimensionCompat() {
    }

    public static boolean allowPortalCreationInCurrentDimension(EntityPlayer player, World world) {
        boolean twilightAllowsAll = twilightAllowsAllOtherDimensions();
        if (twilightAllowsAll) {
            probeDimensionDecision(player, world, true, true);
            return true;
        }
        if (!GpomEarlyConfig.twilightForestPortalCreationDimensionsEnabled()) {
            probeDimensionDecision(player, world, false, false);
            return false;
        }
        Integer dimension = currentDimension(player, world);
        boolean allowed = dimension != null && isAllowedDimension(dimension);
        probeDimensionDecision(player, world, allowed, false);
        return allowed;
    }

    public static void probePortalScan(EntityPlayer player, World world, float radius) {
        if (!GpomEarlyConfig.twilightForestPortalCreationProbeEnabled()) {
            return;
        }
        Integer dimension = currentDimension(player, world);
        Integer worldDimension = MinecraftMappingCompat.worldDimension(world);
        Integer playerDimension = MinecraftMappingCompat.entityDimension(player);
        boolean allowed = twilightAllowsAllOtherDimensions()
                || (dimension != null
                && GpomEarlyConfig.twilightForestPortalCreationDimensionsEnabled()
                && isAllowedDimension(dimension));
        probe("scan:" + dimension + ":" + allowed, "scan player={} dimension={} worldDimension={} playerDimension={} radius={} allowed={} gpomEnabled={} allowlist={} twilightOrigin={} twilightDim={} twilightAllowAll={}",
                playerName(player),
                dimension,
                worldDimension,
                playerDimension,
                radius,
                allowed,
                GpomEarlyConfig.twilightForestPortalCreationDimensionsEnabled(),
                GpomEarlyConfig.twilightForestPortalCreationDimensionAllowlist(),
                twilightOriginDimension(),
                twilightForestDimensionId(),
                twilightAllowsAllOtherDimensions());
    }

    public static boolean canFormPortalWithProbe(Object portalBlock, IBlockState state) {
        boolean result = invokeCanFormPortal(portalBlock, state);
        if (GpomEarlyConfig.twilightForestPortalCreationProbeEnabled()) {
            probe("canForm:" + describeState(state) + ":" + result,
                    "canFormPortal state={} result={}", describeState(state), result);
        }
        return result;
    }

    public static boolean logTryCreatePortalResult(boolean result, World world, BlockPos pos, EntityItem item, EntityPlayer player) {
        if (GpomEarlyConfig.twilightForestPortalCreationProbeEnabled()) {
            IBlockState state = world == null || pos == null ? null : MinecraftMappingCompat.worldBlockState(world, pos);
            BlockPos below = pos == null ? null : new BlockPos(
                    MinecraftMappingCompat.blockPosX(pos),
                    MinecraftMappingCompat.blockPosY(pos) - 1,
                    MinecraftMappingCompat.blockPosZ(pos));
            IBlockState belowState = world == null || below == null ? null : MinecraftMappingCompat.worldBlockState(world, below);
            probe("tryCreate:" + describePos(pos) + ":" + result,
                    "tryToCreatePortal result={} player={} dimension={} worldDimension={} playerDimension={} pos={} item={} state={} below={}",
                    result,
                    playerName(player),
                    currentDimension(player, world),
                    MinecraftMappingCompat.worldDimension(world),
                    MinecraftMappingCompat.entityDimension(player),
                    describePos(pos),
                    describeStack(MinecraftMappingCompat.entityItemStack(item)),
                    describeState(state),
                    describeState(belowState));
        }
        return result;
    }

    private static boolean twilightAllowsAllOtherDimensions() {
        try {
            Field field = twilightAllowPortalsInOtherDimensionsField;
            if (field == null) {
                Class<?> configClass = Class.forName("twilightforest.TFConfig", false,
                        TwilightForestPortalDimensionCompat.class.getClassLoader());
                field = configClass.getField("allowPortalsInOtherDimensions");
                twilightAllowPortalsInOtherDimensionsField = field;
            }
            return field.getBoolean(null);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Twilight Forest Portals] Could not read Twilight Forest allowPortalsInOtherDimensions; using GPOM allowlist only", throwable);
            return false;
        }
    }

    private static boolean isAllowedDimension(int dimension) {
        Set<String> allowlist = GpomEarlyConfig.twilightForestPortalCreationDimensionAllowlist();
        if (allowlist.isEmpty()) {
            return false;
        }
        String numeric = Integer.toString(dimension);
        for (String entry : allowlist) {
            if ("*".equals(entry) || numeric.equals(entry) || dimension == dimensionAlias(entry)) {
                return true;
            }
        }
        return false;
    }

    private static boolean invokeCanFormPortal(Object portalBlock, IBlockState state) {
        if (portalBlock == null || state == null) {
            return false;
        }
        try {
            Method method = canFormPortalMethod;
            if (method == null) {
                method = portalBlock.getClass().getMethod("canFormPortal", IBlockState.class);
                method.setAccessible(true);
                canFormPortalMethod = method;
            }
            Object value = method.invoke(portalBlock, state);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Twilight Forest Portals] Could not call BlockTFPortal.canFormPortal; denying this portal candidate", throwable);
            return false;
        }
    }

    private static void probeDimensionDecision(EntityPlayer player, World world, boolean allowed, boolean twilightAllowsAll) {
        if (!GpomEarlyConfig.twilightForestPortalCreationProbeEnabled()) {
            return;
        }
        Integer dimension = currentDimension(player, world);
        Integer worldDimension = MinecraftMappingCompat.worldDimension(world);
        Integer playerDimension = MinecraftMappingCompat.entityDimension(player);
        probe("dimension:" + dimension + ":" + allowed,
                "dimensionGate dimension={} worldDimension={} playerDimension={} allowed={} gpomEnabled={} allowlist={} twilightOrigin={} twilightDim={} twilightAllowAll={}",
                dimension,
                worldDimension,
                playerDimension,
                allowed,
                GpomEarlyConfig.twilightForestPortalCreationDimensionsEnabled(),
                GpomEarlyConfig.twilightForestPortalCreationDimensionAllowlist(),
                twilightOriginDimension(),
                twilightForestDimensionId(),
                twilightAllowsAll);
    }

    private static Integer currentDimension(EntityPlayer player, World world) {
        Integer playerDimension = MinecraftMappingCompat.entityDimension(player);
        if (playerDimension != null) {
            return playerDimension;
        }
        return MinecraftMappingCompat.worldDimension(world);
    }

    private static int dimensionAlias(String entry) {
        switch (entry) {
            case "overworld":
            case "minecraft:overworld":
                return 0;
            case "twilight":
            case "twilight_forest":
            case "twilightforest":
            case "twilightforest:twilight_forest":
                return twilightForestDimensionId();
            case "void":
            case "void_world":
            case "the_void":
            case "mbc:void_world":
                return 43;
            default:
                return Integer.MIN_VALUE;
        }
    }

    private static int twilightForestDimensionId() {
        try {
            Class<?> configClass = Class.forName("twilightforest.TFConfig", false,
                    TwilightForestPortalDimensionCompat.class.getClassLoader());
            Object dimensionConfig = configClass.getField("dimension").get(null);
            if (dimensionConfig == null) {
                return Integer.MIN_VALUE;
            }
            return dimensionConfig.getClass().getField("dimensionID").getInt(dimensionConfig);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int twilightOriginDimension() {
        try {
            Class<?> configClass = Class.forName("twilightforest.TFConfig", false,
                    TwilightForestPortalDimensionCompat.class.getClassLoader());
            return configClass.getField("originDimension").getInt(null);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static String playerName(EntityPlayer player) {
        if (player == null) {
            return "null";
        }
        return String.valueOf(MinecraftMappingCompat.playerUniqueId(player));
    }

    private static String describeStack(ItemStack stack) {
        if (stack == null || MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return "empty";
        }
        Item item = MinecraftMappingCompat.itemStackItem(stack);
        ResourceLocation name = MinecraftMappingCompat.itemRegistryName(item);
        return String.valueOf(name) + "@" + MinecraftMappingCompat.itemStackMetadata(stack)
                + "x" + MinecraftMappingCompat.itemStackCount(stack);
    }

    private static String describeState(IBlockState state) {
        if (state == null) {
            return "null";
        }
        Block block = MinecraftMappingCompat.blockStateBlock(state);
        ResourceLocation name = MinecraftMappingCompat.blockRegistryName(block);
        int meta = block == null ? 0 : MinecraftMappingCompat.blockMetaFromState(block, state);
        return String.valueOf(name) + "@" + meta;
    }

    private static String describePos(BlockPos pos) {
        if (pos == null) {
            return "null";
        }
        return MinecraftMappingCompat.blockPosX(pos) + "," + MinecraftMappingCompat.blockPosY(pos) + "," + MinecraftMappingCompat.blockPosZ(pos);
    }

    private static void probe(String key, String message, Object... args) {
        long now = System.nanoTime();
        Long previous = LAST_PROBES.get(key);
        if (previous != null && now - previous < PROBE_INTERVAL_NANOS) {
            return;
        }
        LAST_PROBES.put(key, now);
        PROBE_LOGGER.info("[GPOM Twilight Forest Portal Probe] " + message, args);
    }
}
