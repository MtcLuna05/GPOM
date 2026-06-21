package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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
    private static final Set<String> IGNORED_MISSING_SOUND_NAMESPACES =
            GpomEarlyConfig.registryIgnoredMissingSoundEventNamespaces();
    private static final Set<String> IGNORED_MISSING_BLOCK_ITEM_NAMESPACES =
            GpomEarlyConfig.registryIgnoredMissingBlockItemNamespaces();
    private static final Set<String> IGNORED_MISSING_AETHER_ENCHANTMENT_NAMESPACES =
            GpomEarlyConfig.registryIgnoredMissingAetherEnchantmentNamespaces();
    private static final Set<String> FAIL_MISSING_BLOCK_ITEM_NAMESPACES =
            GpomEarlyConfig.registryFailMissingBlockItemNamespaces();

    private MissingMappingRepairs() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(MissingMappingRepairs.class);
    }

    @SubscribeEvent
    public static void onMissingBlockMappings(RegistryEvent.MissingMappings<Block> event) {
        IgnoreSummary ignored = new IgnoreSummary("block", IGNORED_MISSING_BLOCK_ITEM_NAMESPACES);
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            ResourceLocation key = mapping.key;
            if (isAdvancedRocketryWirelessTransciever(key)) {
                remap(mapping, ForgeRegistries.BLOCKS, new ResourceLocation("advancedrocketry", "wirelesstransceiver"), "block");
            } else if (GpomEarlyConfig.registryRepairThaumicWondersMissingMappingsEnabled() && isThaumicWondersSameKeyBlock(key)) {
                remapSameKey(mapping, ForgeRegistries.BLOCKS, "block");
            } else if (GpomEarlyConfig.registryRepairThaumicWondersMissingMappingsEnabled() && isThaumicWondersPrimordialAccelerator(key)) {
                remap(mapping, ForgeRegistries.BLOCKS, new ResourceLocation("thaumicwonders", "primordial_siphon"), "block");
            } else if (isModularMachineryFactoryController(key)) {
                remap(mapping, ForgeRegistries.BLOCKS, new ResourceLocation("modularmachinery", "blockfactorycontroller"), "block");
            } else if (isMysticalLibAoAGrass(key)) {
                remap(mapping, ForgeRegistries.BLOCKS, new ResourceLocation("aoa3", path(key)), "block");
            } else if (isAoAGrass(key) || isBlockcrafteryEditableBlock(key)) {
                remapSameKey(mapping, ForgeRegistries.BLOCKS, "block");
            } else if (isIgnoredMissingBlockItemNamespace(key)) {
                mapping.ignore();
                ignored.add(key);
            } else if (shouldFailMissingBlockItemMapping(key)) {
                failMissing(mapping, "block");
            }
        }
        ignored.log();
    }

    @SubscribeEvent
    public static void onMissingItemMappings(RegistryEvent.MissingMappings<Item> event) {
        IgnoreSummary ignored = new IgnoreSummary("item", IGNORED_MISSING_BLOCK_ITEM_NAMESPACES);
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            ResourceLocation key = mapping.key;
            if (isAdvancedRocketryWirelessTransciever(key)) {
                remap(mapping, ForgeRegistries.ITEMS, new ResourceLocation("advancedrocketry", "wirelesstransceiver"), "item");
            } else if (GpomEarlyConfig.registryRepairThaumicWondersMissingMappingsEnabled() && isThaumicWondersSameKeyItem(key)) {
                remapSameKey(mapping, ForgeRegistries.ITEMS, "item");
            } else if (GpomEarlyConfig.registryRepairThaumicWondersMissingMappingsEnabled() && isThaumicWondersCropItem(key)) {
                remap(mapping, ForgeRegistries.ITEMS, thaumicWondersCropReplacement(key), "item");
            } else if (GpomEarlyConfig.registryRepairThaumicWondersMissingMappingsEnabled() && isThaumicWondersPrimordialAccelerator(key)) {
                remap(mapping, ForgeRegistries.ITEMS, new ResourceLocation("thaumicwonders", "primordial_siphon"), "item");
            } else if (isModularMachineryFactoryController(key)) {
                remap(mapping, ForgeRegistries.ITEMS, new ResourceLocation("modularmachinery", "blockfactorycontroller"), "item");
            } else if (isMysticalLibAoAGrass(key)) {
                remap(mapping, ForgeRegistries.ITEMS, new ResourceLocation("aoa3", path(key)), "item");
            } else if (isAoAGrass(key) || isBlockcrafteryEditableBlock(key)) {
                remapSameKey(mapping, ForgeRegistries.ITEMS, "item");
            } else if (isIgnoredMissingBlockItemNamespace(key)) {
                mapping.ignore();
                ignored.add(key);
            } else if (shouldFailMissingBlockItemMapping(key)) {
                failMissing(mapping, "item");
            }
        }
        ignored.log();
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
        IgnoreSummary ignored = new IgnoreSummary("sound event", IGNORED_MISSING_SOUND_NAMESPACES);
        for (RegistryEvent.MissingMappings.Mapping<SoundEvent> mapping : event.getAllMappings()) {
            if (isAromaCritSound(mapping.key)) {
                if (!remapSameKey(mapping, ForgeRegistries.SOUND_EVENTS, "sound")) {
                    mapping.ignore();
                    GPOM.LOGGER.warn("[MissingMappingRepairs] Ignored legacy Aroma1997Core sound mapping {}; no live target exists", mapping.key);
                }
            } else if (isIgnoredMissingSoundNamespace(mapping.key)) {
                mapping.ignore();
                ignored.add(mapping.key);
            }
        }
        ignored.log();
    }

    @SubscribeEvent
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void onMissingAetherEnchantmentMappings(RegistryEvent.MissingMappings event) {
        if (!isAetherEnchantmentRegistry(event)) {
            return;
        }

        IgnoreSummary ignored = new IgnoreSummary("Aether enchantment", IGNORED_MISSING_AETHER_ENCHANTMENT_NAMESPACES);
        for (Object raw : event.getAllMappings()) {
            if (!(raw instanceof RegistryEvent.MissingMappings.Mapping)) {
                continue;
            }
            RegistryEvent.MissingMappings.Mapping mapping = (RegistryEvent.MissingMappings.Mapping) raw;
            ResourceLocation key = mapping.key;
            if (isIgnoredMissingAetherEnchantmentNamespace(key)) {
                mapping.ignore();
                ignored.add(key);
            }
        }
        ignored.log();
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

    private static boolean isAdvancedRocketryWirelessTransciever(ResourceLocation key) {
        return key != null
                && "advancedrocketry".equals(namespace(key))
                && "wirelesstransciever".equals(path(key));
    }

    private static boolean isModularMachineryFactoryController(ResourceLocation key) {
        return key != null
                && "modularmachinery".equals(namespace(key))
                && path(key).endsWith("_factory_controller");
    }

    private static boolean isThaumicWondersSameKeyBlock(ResourceLocation key) {
        if (key == null || !"thaumicwonders".equals(namespace(key))) {
            return false;
        }
        String path = path(key);
        return "placeholder_adv_alch_construct".equals(path)
                || "placeholder_thaumium_block".equals(path)
                || "placeholder_void_metal_block".equals(path)
                || "cinderpearl_crop".equals(path)
                || "shimmerleaf_crop".equals(path)
                || "vishroom_crop".equals(path);
    }

    private static boolean isThaumicWondersSameKeyItem(ResourceLocation key) {
        if (key == null || !"thaumicwonders".equals(namespace(key))) {
            return false;
        }
        String path = path(key);
        return "placeholder_adv_alch_construct".equals(path)
                || "placeholder_thaumium_block".equals(path)
                || "placeholder_void_metal_block".equals(path);
    }

    private static boolean isThaumicWondersCropItem(ResourceLocation key) {
        if (key == null || !"thaumicwonders".equals(namespace(key))) {
            return false;
        }
        String path = path(key);
        return "cinderpearl_crop".equals(path)
                || "shimmerleaf_crop".equals(path)
                || "vishroom_crop".equals(path);
    }

    private static ResourceLocation thaumicWondersCropReplacement(ResourceLocation key) {
        String path = path(key);
        if ("cinderpearl_crop".equals(path)) {
            return new ResourceLocation("thaumicwonders", "cinderpearl_seed");
        }
        if ("shimmerleaf_crop".equals(path)) {
            return new ResourceLocation("thaumicwonders", "shimmerleaf_seed");
        }
        return new ResourceLocation("thaumicwonders", "vishroom_spore");
    }

    private static boolean isThaumicWondersPrimordialAccelerator(ResourceLocation key) {
        if (key == null || !"thaumicwonders".equals(namespace(key))) {
            return false;
        }
        String path = path(key);
        return "primordial_accelerator".equals(path)
                || "primordial_accelerator_tunnel".equals(path)
                || "primordial_accelerator_terminus".equals(path)
                || "primordial_accretion_chamber".equals(path);
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

    private static boolean isIgnoredMissingSoundNamespace(ResourceLocation key) {
        return key != null && IGNORED_MISSING_SOUND_NAMESPACES.contains(namespace(key));
    }

    private static boolean isIgnoredMissingBlockItemNamespace(ResourceLocation key) {
        return key != null && IGNORED_MISSING_BLOCK_ITEM_NAMESPACES.contains(namespace(key));
    }

    private static boolean isIgnoredMissingAetherEnchantmentNamespace(ResourceLocation key) {
        return key != null && IGNORED_MISSING_AETHER_ENCHANTMENT_NAMESPACES.contains(namespace(key));
    }

    private static boolean isAetherEnchantmentRegistry(RegistryEvent.MissingMappings<?> event) {
        return event != null && "aetherapi:enchantments".equals(String.valueOf(event.getName()));
    }

    private static boolean shouldFailMissingBlockItemMapping(ResourceLocation key) {
        return key != null && FAIL_MISSING_BLOCK_ITEM_NAMESPACES.contains(namespace(key));
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

    private static <T extends IForgeRegistryEntry<T>> void failMissing(RegistryEvent.MissingMappings.Mapping<T> mapping,
                                                                       String typeName) {
        mapping.fail();
        GPOM.LOGGER.error(
                "[MissingMappingRepairs] Failing world load because missing {} mapping {} belongs to protected namespace(s) {}",
                typeName,
                mapping.key,
                FAIL_MISSING_BLOCK_ITEM_NAMESPACES
        );
    }

    private static final class IgnoreSummary {
        private final String typeName;
        private final Set<String> namespaces;
        private final StringBuilder examples = new StringBuilder();
        private int count;

        private IgnoreSummary(String typeName, Set<String> namespaces) {
            this.typeName = typeName;
            this.namespaces = namespaces;
        }

        private void add(ResourceLocation key) {
            count++;
            if (key == null || examples.length() >= 160) {
                return;
            }
            if (examples.length() > 0) {
                examples.append(", ");
            }
            examples.append(key);
        }

        private void log() {
            if (count <= 0) {
                return;
            }
            GPOM.LOGGER.warn(
                    "[MissingMappingRepairs] Ignored {} stale missing {} mapping(s) from configured namespace(s) {}; examples: {}",
                    count,
                    typeName,
                    namespaces,
                    examples
            );
        }
    }
}
