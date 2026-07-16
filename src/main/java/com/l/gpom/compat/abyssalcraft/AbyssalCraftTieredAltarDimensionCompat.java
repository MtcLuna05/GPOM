package com.l.gpom.compat.abyssalcraft;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.shinoow.abyssalcraft.api.APIUtils;
import com.shinoow.abyssalcraft.api.necronomicon.condition.DimensionCondition;
import com.shinoow.abyssalcraft.api.necronomicon.condition.IUnlockCondition;
import com.shinoow.abyssalcraft.api.ritual.NecronomiconRitual;
import com.shinoow.abyssalcraft.lib.ACLib;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class AbyssalCraftTieredAltarDimensionCompat {
    private static final Logger PROBE_LOGGER = LogManager.getLogger("GPOM AbyssalCraft Ritual Probe");
    private static final BlockPos[] RITUAL_PEDESTAL_OFFSETS = new BlockPos[] {
            new BlockPos(-3, 0, 0),
            new BlockPos(0, 0, -3),
            new BlockPos(3, 0, 0),
            new BlockPos(0, 0, 3),
            new BlockPos(-2, 0, 2),
            new BlockPos(-2, 0, -2),
            new BlockPos(2, 0, 2),
            new BlockPos(2, 0, -2)
    };

    private AbyssalCraftTieredAltarDimensionCompat() {
    }

    public static IUnlockCondition unlockConditionForTier(int tier, IUnlockCondition originalFallback) {
        if (!GpomEarlyConfig.abyssalCraftTieredAltarDimensionsEnabled()) {
            return originalConditionForTier(tier, originalFallback);
        }
        if (tier < 0 || tier > 3) {
            return originalFallback;
        }
        return new TierDimensionCondition(tier, defaultDimensionForTier(tier));
    }

    public static boolean canPerformRitualAction(int dimension, int bookType) {
        if (!GpomEarlyConfig.abyssalCraftTieredAltarDimensionsEnabled()) {
            return false;
        }
        int maxTier = Math.min(3, bookType);
        for (int tier = 0; tier <= maxTier; tier++) {
            if (isAllowedDimension(tier, dimension)) {
                probe("canPerformAction override dimension={} bookType={} matchedTier={} allowlist={}",
                        dimension, bookType, tier, GpomEarlyConfig.abyssalCraftTieredAltarDimensionAllowlist(tier));
                return true;
            }
        }
        probe("canPerformAction no override dimension={} bookType={} checkedTiers=0..{}", dimension, bookType, maxTier);
        return false;
    }

    public static boolean sameRitualBookType(int dimension, int bookType) {
        if (!GpomEarlyConfig.abyssalCraftTieredAltarDimensionsEnabled() || bookType < 0 || bookType > 3) {
            return false;
        }
        boolean allowed = isAllowedDimension(bookType, dimension);
        probe("sameBookType override check dimension={} bookType={} allowed={} allowlist={}",
                dimension, bookType, allowed, GpomEarlyConfig.abyssalCraftTieredAltarDimensionAllowlist(bookType));
        return allowed;
    }

    public static boolean sameChunkOrLoadedRitualStructure(World world, BlockPos altarPos, EntityPlayer player) {
        if (world == null || altarPos == null) {
            return false;
        }
        if (allPedestalsInAltarChunk(altarPos)) {
            return true;
        }
        boolean loaded = MinecraftMappingCompat.worldIsBlockLoaded(world, altarPos);
        for (BlockPos offset : RITUAL_PEDESTAL_OFFSETS) {
            BlockPos pedestalPos = add(altarPos, offset);
            loaded &= MinecraftMappingCompat.worldIsBlockLoaded(world, pedestalPos);
            if (!loaded) {
                break;
            }
        }
        probe("sameChunk bypass check pos={} loaded={}", describePos(altarPos), loaded);
        if (loaded) {
            return true;
        }
        if (MinecraftMappingCompat.worldIsRemote(world)) {
            showCrossChunkFailure(world, altarPos, player);
        }
        return false;
    }

    public static boolean ritualDimensionMatches(int ritualDimension, int currentDimension) {
        if (ritualDimension == -1 || ritualDimension == currentDimension) {
            probe("ritualDimension match vanilla ritualDimension={} currentDimension={}", ritualDimension, currentDimension);
            return true;
        }
        if (!GpomEarlyConfig.abyssalCraftTieredAltarDimensionsEnabled()) {
            return false;
        }
        int tier = defaultTierForDimension(ritualDimension);
        boolean matches = tier >= 0 && isAllowedDimension(tier, currentDimension);
        probe("ritualDimension custom ritualDimension={} currentDimension={} inferredTier={} matches={} allowlist={}",
                ritualDimension,
                currentDimension,
                tier,
                matches,
                tier >= 0 ? GpomEarlyConfig.abyssalCraftTieredAltarDimensionAllowlist(tier) : "n/a");
        return matches;
    }

    public static void probePerformRitual(int dimension, Object altar, Object heldStack) {
        if (!shouldProbeDimension(dimension)) {
            return;
        }
        Object altarItem = invokeNoArg(altar, "getItem");
        Object pedestals = invokeNoArg(altar, "getPedestals");
        int pedestalCount = pedestals instanceof List ? ((List<?>) pedestals).size() : -1;
        PROBE_LOGGER.info("[GPOM AbyssalCraft Ritual Probe] performRitual dimension={} held={} altarItem={} pedestalCount={} pedestals={}",
                dimension,
                describe(heldStack),
                describe(altarItem),
                pedestalCount,
                describe(pedestals));
    }

    public static void probeRitualCandidate(NecronomiconRitual ritual, int dimension, int bookType, ItemStack[] offerings, ItemStack sacrifice) {
        if (ritual == null || !shouldProbeDimension(dimension)) {
            return;
        }
        boolean dimensionMatches = ritualDimensionMatches(ritual.getDimension(), dimension);
        boolean bookMatches = ritual.getBookType() <= bookType;
        boolean offeringsMatch = ritual.getOfferings() != null
                && offerings != null
                && APIUtils.areItemStackArraysEqual(ritual.getOfferings(), offerings, ritual.isNBTSensitive());
        boolean sacrificeMatch = false;
        if (ritual.requiresItemSacrifice()) {
            sacrificeMatch = true;
        } else if (ritual.getSacrifice() == null) {
            sacrificeMatch = isEmptyItemStack(sacrifice);
        } else {
            sacrificeMatch = APIUtils.areObjectsEqual(sacrifice, ritual.getSacrifice(), ritual.isSacrificeNBTSensitive());
        }
        PROBE_LOGGER.info("[GPOM AbyssalCraft Ritual Probe] candidate name={} dimension={} ritualDimension={} dimensionMatch={} bookType={} ritualBookType={} bookMatch={} offeringsMatch={} sacrificeMatch={} offerings={} ritualOfferings={} sacrifice={} ritualSacrifice={}",
                ritual.getUnlocalizedName(),
                dimension,
                ritual.getDimension(),
                dimensionMatches,
                bookType,
                ritual.getBookType(),
                bookMatches,
                offeringsMatch,
                sacrificeMatch,
                describeArray(offerings),
                describeArray(ritual.getOfferings()),
                describe(sacrifice),
                describe(ritual.getSacrifice()));
    }

    private static IUnlockCondition originalConditionForTier(int tier, IUnlockCondition originalFallback) {
        if (tier <= 0) {
            return originalFallback;
        }
        return new DimensionCondition(defaultDimensionForTier(tier));
    }

    private static int defaultDimensionForTier(int tier) {
        switch (tier) {
            case 1:
                return ACLib.abyssal_wasteland_id;
            case 2:
                return ACLib.dreadlands_id;
            case 3:
                return ACLib.omothol_id;
            case 0:
            default:
                return 0;
        }
    }

    private static int defaultTierForDimension(int dimension) {
        if (dimension == 0) {
            return 0;
        }
        if (dimension == ACLib.abyssal_wasteland_id) {
            return 1;
        }
        if (dimension == ACLib.dreadlands_id) {
            return 2;
        }
        if (dimension == ACLib.omothol_id) {
            return 3;
        }
        return -1;
    }

    private static boolean isAllowedDimension(int tier, int dimension) {
        Set<String> allowlist = GpomEarlyConfig.abyssalCraftTieredAltarDimensionAllowlist(tier);
        if (allowlist.isEmpty()) {
            return dimension == defaultDimensionForTier(tier);
        }
        String numeric = Integer.toString(dimension);
        for (String entry : allowlist) {
            if ("*".equals(entry) || numeric.equals(entry) || dimension == dimensionAlias(entry)) {
                return true;
            }
        }
        return false;
    }

    private static int dimensionAlias(String entry) {
        switch (entry) {
            case "overworld":
            case "minecraft:overworld":
                return 0;
            case "abyssal_wasteland":
            case "abyssal-wasteland":
            case "abyssalcraft:abyssal_wasteland":
                return ACLib.abyssal_wasteland_id;
            case "dreadlands":
            case "abyssalcraft:dreadlands":
                return ACLib.dreadlands_id;
            case "omothol":
            case "abyssalcraft:omothol":
                return ACLib.omothol_id;
            default:
                return Integer.MIN_VALUE;
        }
    }

    private static boolean shouldProbeDimension(int dimension) {
        return GpomEarlyConfig.abyssalCraftTieredAltarProbeEnabled() && isConfiguredDimension(dimension);
    }

    private static boolean isConfiguredDimension(int dimension) {
        for (int tier = 0; tier <= 3; tier++) {
            if (isAllowedDimension(tier, dimension)) {
                return true;
            }
        }
        return false;
    }

    private static void probe(String message, Object... args) {
        if (GpomEarlyConfig.abyssalCraftTieredAltarProbeEnabled()) {
            PROBE_LOGGER.info("[GPOM AbyssalCraft Ritual Probe] " + message, args);
        }
    }

    private static boolean allPedestalsInAltarChunk(BlockPos altarPos) {
        int chunkX = MinecraftMappingCompat.blockPosX(altarPos) >> 4;
        int chunkZ = MinecraftMappingCompat.blockPosZ(altarPos) >> 4;
        for (BlockPos offset : RITUAL_PEDESTAL_OFFSETS) {
            BlockPos pedestalPos = add(altarPos, offset);
            if ((MinecraftMappingCompat.blockPosX(pedestalPos) >> 4) != chunkX
                    || (MinecraftMappingCompat.blockPosZ(pedestalPos) >> 4) != chunkZ) {
                return false;
            }
        }
        return true;
    }

    private static void showCrossChunkFailure(World world, BlockPos altarPos, EntityPlayer player) {
        for (BlockPos offset : RITUAL_PEDESTAL_OFFSETS) {
            BlockPos pedestalPos = add(altarPos, offset);
            if (!MinecraftMappingCompat.worldIsBlockLoaded(world, pedestalPos)) {
                MinecraftMappingCompat.worldSpawnParticle(
                        world,
                        EnumParticleTypes.BARRIER,
                        MinecraftMappingCompat.blockPosX(pedestalPos) + 0.5D,
                        MinecraftMappingCompat.blockPosY(pedestalPos) + 1.5D,
                        MinecraftMappingCompat.blockPosZ(pedestalPos) + 0.5D,
                        0.0D,
                        0.0D,
                        0.0D
                );
            }
        }
        if (player != null) {
            MinecraftMappingCompat.playerSendStatusMessage(player, new TextComponentTranslation("message.ritual.notsamechunk"), true);
        }
    }

    private static BlockPos add(BlockPos base, BlockPos offset) {
        return new BlockPos(
                MinecraftMappingCompat.blockPosX(base) + MinecraftMappingCompat.blockPosX(offset),
                MinecraftMappingCompat.blockPosY(base) + MinecraftMappingCompat.blockPosY(offset),
                MinecraftMappingCompat.blockPosZ(base) + MinecraftMappingCompat.blockPosZ(offset)
        );
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable throwable) {
            return "<" + methodName + " failed: " + throwable.getClass().getSimpleName() + ">";
        }
    }

    private static boolean isEmptyItemStack(Object stack) {
        try {
            return stack != null && (Boolean) stack.getClass().getMethod("func_190926_b").invoke(stack);
        } catch (Throwable throwable) {
            return stack == null;
        }
    }

    private static String describe(Object value) {
        if (value instanceof Object[]) {
            return describeArray((Object[]) value);
        }
        return String.valueOf(value);
    }

    private static String describeArray(Object[] values) {
        return values == null ? "null" : Arrays.toString(values);
    }

    private static String describePos(BlockPos pos) {
        return pos == null
                ? "null"
                : MinecraftMappingCompat.blockPosX(pos) + ","
                + MinecraftMappingCompat.blockPosY(pos) + ","
                + MinecraftMappingCompat.blockPosZ(pos);
    }

    private static final class TierDimensionCondition implements IUnlockCondition {
        private final int tier;
        private final int defaultDimension;

        private TierDimensionCondition(int tier, int defaultDimension) {
            this.tier = tier;
            this.defaultDimension = defaultDimension;
        }

        @Override
        public boolean areConditionObjectsEqual(Object object) {
            return object instanceof Integer && isAllowedDimension(tier, (Integer) object);
        }

        @Override
        public Object getConditionObject() {
            return defaultDimension;
        }

        @Override
        public int getType() {
            return 2;
        }
    }
}
