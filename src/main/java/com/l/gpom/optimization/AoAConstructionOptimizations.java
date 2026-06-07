package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;

public final class AoAConstructionOptimizations {
    private static final String[] GRASS_REGISTRY_IDS = {
            "abyss_grass",
            "borean_grass",
            "candyland_grass",
            "celeve_grass",
            "creeponia_grass",
            "dustopia_grass",
            "gardencia_grass",
            "greckon_grass",
            "haven_grass",
            "iromine_grass",
            "lelyetia_grass",
            "lelyetia_down_grass",
            "lunalyte_grass",
            "lunasole_grass",
            "mysterium_grass",
            "precasia_grass",
            "runic_grass",
            "shyrelands_grass",
            "toxic_grass"
    };

    private AoAConstructionOptimizations() {
    }

    public static void logFinalGrassRegistryState(String stage) {
        logGrassRegistryState(stage + " block", ForgeRegistries.BLOCKS);
        logGrassRegistryState(stage + " item", ForgeRegistries.ITEMS);
    }

    private static void logGrassRegistryState(String registryType, IForgeRegistry<?> registry) {
        if (registry == null) {
            GPOM.LOGGER.warn("[AoA Construction] Could not validate AoA grass {} registry: registry was null", registryType);
            return;
        }

        StringBuilder missing = new StringBuilder();
        int missingCount = 0;
        for (String id : GRASS_REGISTRY_IDS) {
            if (registry.getValue(new ResourceLocation("aoa3", id)) != null) {
                continue;
            }
            if (missing.length() > 0) {
                missing.append(',');
            }
            missing.append("aoa3:").append(id);
            missingCount++;
        }

        if (missingCount == 0) {
            GPOM.LOGGER.info("[AoA Construction] AoA grass {} registry validation passed for {} entries", registryType, GRASS_REGISTRY_IDS.length);
        } else {
            GPOM.LOGGER.warn(
                    "[AoA Construction] AoA grass {} registry missing {}/{} entries: {}",
                    registryType,
                    missingCount,
                    GRASS_REGISTRY_IDS.length,
                    missing
            );
        }
    }
}
