package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.registries.ForgeRegistry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BlockcrafteryRegistryMigration {
    private static final ResourceLocation BLOCK_REGISTRY = new ResourceLocation("minecraft", "blocks");
    private static final ResourceLocation ITEM_REGISTRY = new ResourceLocation("minecraft", "items");
    private static final ResourceLocation AIR = new ResourceLocation("minecraft", "air");
    private static final Set<String> EDITABLE_PATHS = new HashSet<>(Arrays.asList(
            "editable_block",
            "editable_slab",
            "editable_double_slab",
            "editable_slant",
            "editable_outer_corner",
            "editable_inner_corner",
            "editable_wall",
            "editable_fence",
            "editable_stairs",
            "editable_trap_door",
            "editable_door",
            "editable_pressure_plate_all",
            "editable_pressure_plate_mobs",
            "editable_pressure_plate_player",
            "editable_pressure_plate_items",
            "editable_block_reinforced",
            "editable_slab_reinforced",
            "editable_double_slab_reinforced",
            "editable_slant_reinforced",
            "editable_outer_corner_reinforced",
            "editable_inner_corner_reinforced",
            "editable_wall_reinforced",
            "editable_fence_reinforced",
            "editable_stairs_reinforced",
            "editable_trap_door_reinforced",
            "editable_door_reinforced",
            "editable_pressure_plate_all_reinforced",
            "editable_pressure_plate_items_reinforced",
            "editable_pressure_plate_mobs_reinforced",
            "editable_pressure_plate_player_reinforced"
    ));

    private BlockcrafteryRegistryMigration() {
    }

    public static void normalizeSnapshot(Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot) {
        if (snapshot == null || !Loader.isModLoaded("blockcraftery")) {
            return;
        }

        int migrated = 0;
        int removedAirAliases = 0;
        migrated += normalizeRegistry(snapshot.get(BLOCK_REGISTRY));
        migrated += normalizeRegistry(snapshot.get(ITEM_REGISTRY));
        removedAirAliases += removeBadBlockcrafteryAirAliases(snapshot.get(BLOCK_REGISTRY));
        removedAirAliases += removeBadBlockcrafteryAirAliases(snapshot.get(ITEM_REGISTRY));

        if (migrated > 0 || removedAirAliases > 0) {
            GPOM.LOGGER.warn(
                    "[BlockcrafteryRegistryMigration] Normalized legacy Blockcraftery registry snapshot: migrated {} mysticallib id(s), removed {} stale air alias(es)",
                    migrated,
                    removedAirAliases
            );
        }
    }

    private static int normalizeRegistry(ForgeRegistry.Snapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }

        int migrated = 0;
        for (String path : EDITABLE_PATHS) {
            ResourceLocation legacy = new ResourceLocation("mysticallib", path);
            ResourceLocation target = new ResourceLocation("blockcraftery", path);
            Integer legacyId = snapshot.ids.get(legacy);
            if (legacyId == null) {
                continue;
            }

            snapshot.dummied.remove(legacy);
            snapshot.dummied.remove(target);
            removeAliasToAir(snapshot.aliases, legacy);
            removeAliasToAir(snapshot.aliases, target);

            Integer targetId = snapshot.ids.get(target);
            if (targetId == null || targetId.equals(legacyId)) {
                snapshot.ids.put(target, legacyId);
                snapshot.ids.remove(legacy);
                moveOverride(snapshot, legacy, target);
                snapshot.aliases.put(legacy, target);
                migrated++;
            } else {
                snapshot.aliases.put(legacy, target);
                GPOM.LOGGER.warn(
                        "[BlockcrafteryRegistryMigration] Kept legacy id {} for {} because {} already has saved id {}; MissingMappings will remap if needed",
                        legacyId,
                        legacy,
                        target,
                        targetId
                );
            }
        }
        return migrated;
    }

    private static int removeBadBlockcrafteryAirAliases(ForgeRegistry.Snapshot snapshot) {
        if (snapshot == null) {
            return 0;
        }

        int removed = 0;
        for (String path : EDITABLE_PATHS) {
            ResourceLocation key = new ResourceLocation("blockcraftery", path);
            if (removeAliasToAir(snapshot.aliases, key)) {
                removed++;
            }
            snapshot.dummied.remove(key);
        }
        return removed;
    }

    private static boolean removeAliasToAir(Map<ResourceLocation, ResourceLocation> aliases, ResourceLocation key) {
        ResourceLocation value = aliases.get(key);
        if (!AIR.equals(value)) {
            return false;
        }
        aliases.remove(key);
        return true;
    }

    private static void moveOverride(ForgeRegistry.Snapshot snapshot, ResourceLocation legacy, ResourceLocation target) {
        String owner = snapshot.overrides.remove(legacy);
        if (owner != null && !snapshot.overrides.containsKey(target)) {
            snapshot.overrides.put(target, owner);
        }
    }
}
