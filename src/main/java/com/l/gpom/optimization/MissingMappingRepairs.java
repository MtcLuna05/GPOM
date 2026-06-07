package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class MissingMappingRepairs {
    private static final Set<String> AOA_GRASS_BLOCKS = new HashSet<>(Arrays.asList(
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
    ));

    private static final Set<String> BLOCKCRAFTERY_EDITABLE_BLOCKS = new HashSet<>(Arrays.asList(
            "editable_slab",
            "editable_double_slab",
            "editable_wall",
            "editable_fence",
            "editable_stairs",
            "editable_door",
            "editable_slab_reinforced",
            "editable_double_slab_reinforced",
            "editable_wall_reinforced",
            "editable_fence_reinforced",
            "editable_stairs_reinforced",
            "editable_door_reinforced"
    ));

    private MissingMappingRepairs() {
    }

    @SubscribeEvent
    public static void onMissingBlockMappings(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            ResourceLocation key = mapping.key;
            if (isMysticalLibAoAGrass(key)) {
                remap(mapping, ForgeRegistries.BLOCKS, new ResourceLocation("aoa3", path(key)), "block");
            } else if (isAoAGrass(key) || isBlockcrafteryEditableBlock(key)) {
                remapSameKey(mapping, ForgeRegistries.BLOCKS, "block");
            }
        }
    }

    @SubscribeEvent
    public static void onMissingItemMappings(RegistryEvent.MissingMappings<Item> event) {
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            ResourceLocation key = mapping.key;
            if (isMysticalLibAoAGrass(key)) {
                remap(mapping, ForgeRegistries.ITEMS, new ResourceLocation("aoa3", path(key)), "item");
            } else if (isAoAGrass(key) || isBlockcrafteryEditableBlock(key)) {
                remapSameKey(mapping, ForgeRegistries.ITEMS, "item");
            }
        }
    }

    @SubscribeEvent
    public static void onMissingPotionTypeMappings(RegistryEvent.MissingMappings<PotionType> event) {
        for (RegistryEvent.MissingMappings.Mapping<PotionType> mapping : event.getAllMappings()) {
            if (isCoFHCore(mapping.key)) {
                if (!remapSameKey(mapping, ForgeRegistries.POTION_TYPES, "potion type")) {
                    mapping.ignore();
                    GPOM.LOGGER.warn("[MissingMappingRepairs] Ignored legacy CoFHCore potion type mapping {}; no live target exists", mapping.key);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMissingPotionMappings(RegistryEvent.MissingMappings<Potion> event) {
        for (RegistryEvent.MissingMappings.Mapping<Potion> mapping : event.getAllMappings()) {
            if (isAromaSanityChecker(mapping.key)) {
                if (!remapSameKey(mapping, ForgeRegistries.POTIONS, "potion")) {
                    mapping.ignore();
                    GPOM.LOGGER.warn("[MissingMappingRepairs] Ignored legacy Aroma1997Core potion mapping {}; no live target exists", mapping.key);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onMissingSoundMappings(RegistryEvent.MissingMappings<SoundEvent> event) {
        for (RegistryEvent.MissingMappings.Mapping<SoundEvent> mapping : event.getAllMappings()) {
            if (isAromaCritSound(mapping.key)) {
                if (!remapSameKey(mapping, ForgeRegistries.SOUND_EVENTS, "sound")) {
                    mapping.ignore();
                    GPOM.LOGGER.warn("[MissingMappingRepairs] Ignored legacy Aroma1997Core sound mapping {}; no live target exists", mapping.key);
                }
            }
        }
    }

    private static boolean isAoAGrass(ResourceLocation key) {
        return key != null && "aoa3".equals(namespace(key)) && AOA_GRASS_BLOCKS.contains(path(key));
    }

    private static boolean isMysticalLibAoAGrass(ResourceLocation key) {
        return key != null && "mysticallib".equals(namespace(key)) && AOA_GRASS_BLOCKS.contains(path(key));
    }

    private static boolean isBlockcrafteryEditableBlock(ResourceLocation key) {
        return key != null && "blockcraftery".equals(namespace(key)) && BLOCKCRAFTERY_EDITABLE_BLOCKS.contains(path(key));
    }

    private static boolean isCoFHCore(ResourceLocation key) {
        return key != null && "cofhcore".equals(namespace(key));
    }

    private static boolean isAromaSanityChecker(ResourceLocation key) {
        return key != null && "aroma1997core".equals(namespace(key)) && "sanity_checker".equals(path(key));
    }

    private static boolean isAromaCritSound(ResourceLocation key) {
        return key != null && "aroma1997core".equals(namespace(key)) && "crit".equals(path(key));
    }

    private static String namespace(ResourceLocation key) {
        String serialized = key.toString();
        int separator = serialized.indexOf(':');
        return separator >= 0 ? serialized.substring(0, separator) : "minecraft";
    }

    private static String path(ResourceLocation key) {
        String serialized = key.toString();
        int separator = serialized.indexOf(':');
        return separator >= 0 ? serialized.substring(separator + 1) : serialized;
    }

    private static <T extends IForgeRegistryEntry<T>> boolean remapSameKey(RegistryEvent.MissingMappings.Mapping<T> mapping,
                                                                           IForgeRegistry<T> registry,
                                                                           String typeName) {
        return remap(mapping, registry, mapping.key, typeName);
    }

    private static <T extends IForgeRegistryEntry<T>> boolean remap(RegistryEvent.MissingMappings.Mapping<T> mapping,
                                                                    IForgeRegistry<T> registry,
                                                                    ResourceLocation targetKey,
                                                                    String typeName) {
        T target = registry.getValue(targetKey);
        if (target == null) {
            GPOM.LOGGER.warn("[MissingMappingRepairs] Could not remap missing {} {} to {}; no live target exists",
                    typeName,
                    mapping.key,
                    targetKey);
            return false;
        }

        mapping.remap(target);
        if (mapping.key.equals(targetKey)) {
            GPOM.LOGGER.warn("[MissingMappingRepairs] Remapped missing {} {} to live same-key registry entry", typeName, mapping.key);
        } else {
            GPOM.LOGGER.warn("[MissingMappingRepairs] Remapped missing {} {} to {}", typeName, mapping.key, targetKey);
        }
        return true;
    }
}
