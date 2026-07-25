package com.l.gpom.compat.tileentity;

import com.l.gpom.GPOM;
import com.l.gpom.compat.actuallyadditions.ActuallyAdditionsTileEntityMappingFix;
import com.l.gpom.compat.architecturecraft.ArchitectureCraftTileEntityMappingFix;
import com.l.gpom.compat.enderio.EnderIOTileEntityMappingFix;
import com.l.gpom.compat.forestry.ForestryTileEntityMappingFix;
import com.l.gpom.compat.industrialforegoing.IndustrialForegoingTileEntityMappingFix;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class TileEntityMissingMappingSaveGuard {
    private static final String ACTUALLY_ADDITIONS_TILE_BASE = "de.ellpeck.actuallyadditions.mod.tile.TileEntityBase";
    private static final String ARCHITECTURECRAFT_TILE_SHAPE = "com.elytradev.architecture.common.tile.TileShape";
    private static final String ARCHITECTURECRAFT_TILE_SAWBENCH = "com.elytradev.architecture.common.tile.TileSawbench";
    private static final String ASTRAL_TILE_PREFIX = "hellfirepvp.astralsorcery.common.tile.";
    private static final String FORESTRY_APIARIST_CHEST = "forestry.apiculture.tiles.TileApiaristChest";
    private static final String TINKER_IO_SMART_OUTPUT = "tinker_io.tileentity.TileEntitySmartOutput";
    private static final Set<String> LOGGED_FALLBACKS = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOGGED_FAILURES = ConcurrentHashMap.newKeySet();

    private TileEntityMissingMappingSaveGuard() {
    }

    public static boolean writeFallbackIdIfNeeded(TileEntity tile, NBTTagCompound compound) {
        if (!GpomEarlyConfig.tileEntityMissingMappingSaveGuardEnabled()
                || !GpomRemoteEnvironment.serverFeaturesAllowed()
                || tile == null
                || compound == null) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Class<? extends TileEntity> tileClass = (Class<? extends TileEntity>) tile.getClass();
        try {
            if (TileEntityMappingRegistry.registeredName(tileClass) != null) {
                return false;
            }
        } catch (ReflectiveOperationException exception) {
            logFailureOnce(tileClass, "inspect vanilla TileEntity registry", exception);
            return false;
        }

        runKnownMappingRepairs();

        try {
            if (TileEntityMappingRegistry.registeredName(tileClass) != null) {
                return false;
            }
        } catch (ReflectiveOperationException exception) {
            logFailureOnce(tileClass, "inspect repaired vanilla TileEntity registry", exception);
            return false;
        }

        ResourceLocation fallbackId = knownFallbackId(tile);
        if (fallbackId == null) {
            logFailureOnce(tileClass, "derive a safe fallback id", null);
            return false;
        }

        if (!GpomEarlyConfig.tileEntityMissingMappingSaveGuardWriteKnownFallbackIds()) {
            logFailureOnce(tileClass, "write fallback id because the fallback writer is disabled", null);
            return false;
        }

        try {
            Class<? extends TileEntity> existingClass = TileEntityMappingRegistry.registeredClass(fallbackId);
            if (existingClass != null && existingClass != tileClass) {
                logFailureOnce(tileClass, "write fallback id " + fallbackId + " because it already maps to " + existingClass.getName(), null);
                return false;
            }
            if (existingClass == null) {
                try {
                    TileEntityMappingRegistry.registerTileEntity(tileClass, fallbackId);
                    if (TileEntityMappingRegistry.registeredName(tileClass) != null) {
                        return false;
                    }
                } catch (RuntimeException exception) {
                    logFailureOnce(tileClass, "register fallback id " + fallbackId, exception);
                }
            }
        } catch (ReflectiveOperationException exception) {
            logFailureOnce(tileClass, "inspect fallback id " + fallbackId, exception);
            return false;
        }

        compound.setString("id", fallbackId.toString());
        if (LOGGED_FALLBACKS.add(tileClass.getName())) {
            GPOM.LOGGER.warn(
                    "[GPOM TileEntity SaveGuard] Wrote fallback id {} for missing TileEntity mapping {}; save data is preserved but the mapping source should still be fixed",
                    fallbackId,
                    tileClass.getName()
            );
        }
        return true;
    }

    private static void runKnownMappingRepairs() {
        ActuallyAdditionsTileEntityMappingFix.repairIfEnabled();
        ArchitectureCraftTileEntityMappingFix.repairIfEnabled();
        EnderIOTileEntityMappingFix.repairIfEnabled();
        ForestryTileEntityMappingFix.repairIfEnabled();
        IndustrialForegoingTileEntityMappingFix.repairIfEnabled();
    }

    private static ResourceLocation knownFallbackId(TileEntity tile) {
        ResourceLocation actuallyAdditionsId = actuallyAdditionsFallbackId(tile);
        if (actuallyAdditionsId != null) {
            return actuallyAdditionsId;
        }
        ResourceLocation architectureCraftId = architectureCraftFallbackId(tile);
        if (architectureCraftId != null) {
            return architectureCraftId;
        }
        ResourceLocation astralSorceryId = astralSorceryFallbackId(tile);
        if (astralSorceryId != null) {
            return astralSorceryId;
        }
        ResourceLocation forestryId = forestryFallbackId(tile);
        if (forestryId != null) {
            return forestryId;
        }
        ResourceLocation tinkerIoId = tinkerIoFallbackId(tile);
        if (tinkerIoId != null) {
            return tinkerIoId;
        }
        return null;
    }

    private static ResourceLocation astralSorceryFallbackId(TileEntity tile) {
        Class<?> tileClass = tile.getClass();
        String className = tileClass.getName();
        if (!className.startsWith(ASTRAL_TILE_PREFIX)) {
            return null;
        }
        String simpleName = tileClass.getSimpleName();
        if (simpleName.isEmpty() || simpleName.indexOf('$') >= 0) {
            return null;
        }
        return new ResourceLocation("astralsorcery", simpleName.toLowerCase(Locale.ROOT));
    }

    private static ResourceLocation forestryFallbackId(TileEntity tile) {
        if (FORESTRY_APIARIST_CHEST.equals(tile.getClass().getName())) {
            return new ResourceLocation("forestry", "api_chest");
        }
        return null;
    }

    private static ResourceLocation tinkerIoFallbackId(TileEntity tile) {
        String className = tile.getClass().getName();
        if (TINKER_IO_SMART_OUTPUT.equals(className)) {
            return new ResourceLocation("tinker_io", "smart_output");
        }
        return null;
    }

    private static ResourceLocation architectureCraftFallbackId(TileEntity tile) {
        String className = tile.getClass().getName();
        if (ARCHITECTURECRAFT_TILE_SHAPE.equals(className)) {
            return new ResourceLocation("architecturecraft", "shape");
        }
        if (ARCHITECTURECRAFT_TILE_SAWBENCH.equals(className)) {
            return new ResourceLocation("architecturecraft", "sawbench");
        }
        return null;
    }

    private static ResourceLocation actuallyAdditionsFallbackId(TileEntity tile) {
        try {
            Class<?> tileClass = tile.getClass();
            if (!isInstanceOf(tileClass, ACTUALLY_ADDITIONS_TILE_BASE)) {
                return null;
            }
            Field nameField = TileEntityMappingRegistry.findField(tileClass, "name");
            Object rawName = nameField.get(tile);
            if (!(rawName instanceof String) || ((String) rawName).isEmpty()) {
                return null;
            }
            return new ResourceLocation("actuallyadditions", (String) rawName);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isInstanceOf(Class<?> type, String expectedName) {
        Class<?> current = type;
        while (current != null) {
            if (expectedName.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static void logFailureOnce(Class<? extends TileEntity> tileClass, String action, Throwable throwable) {
        if (!LOGGED_FAILURES.add(tileClass.getName() + ":" + action)) {
            return;
        }
        if (throwable == null) {
            GPOM.LOGGER.warn("[GPOM TileEntity SaveGuard] Could not {} for missing TileEntity mapping {}", action, tileClass.getName());
        } else {
            GPOM.LOGGER.warn("[GPOM TileEntity SaveGuard] Could not {} for missing TileEntity mapping {}", action, tileClass.getName(), throwable);
        }
    }
}
