package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import com.l.gpom.core.TargetedModVersions;
import com.google.common.collect.SetMultimap;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.tslat.aoa3.client.render.entities.EntityRenders;
import net.tslat.aoa3.common.registration.ArmourRegister;
import net.tslat.aoa3.common.registration.BiomeRegister;
import net.tslat.aoa3.common.registration.BlockRegister;
import net.tslat.aoa3.common.registration.EntityRegister;
import net.tslat.aoa3.common.registration.EnchantmentsRegister;
import net.tslat.aoa3.common.registration.ItemRegister;
import net.tslat.aoa3.common.registration.SoundsRegister;
import net.tslat.aoa3.common.registration.ToolRegister;
import net.tslat.aoa3.common.registration.WeaponRegister;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation.EnumHolder;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.relauncher.Side;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AoAConstructionOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(
            System.getProperty("gpom.aoa3ConstructionOptimizations", "true")
    );
    private static final String ENTITY_RENDERS = "net.tslat.aoa3.client.render.entities.EntityRenders";
    private static final String BLOCK_REGISTER = "net.tslat.aoa3.common.registration.BlockRegister";
    private static final String ITEM_REGISTER = "net.tslat.aoa3.common.registration.ItemRegister";
    private static final String WEAPON_REGISTER = "net.tslat.aoa3.common.registration.WeaponRegister";
    private static final String ENCHANTMENTS_REGISTER = "net.tslat.aoa3.common.registration.EnchantmentsRegister";
    private static final String TOOL_REGISTER = "net.tslat.aoa3.common.registration.ToolRegister";
    private static final String OVERWORLD_ORE = "net.tslat.aoa3.block.generation.ores.OverworldOre";
    private static final String ARMOUR_REGISTER = "net.tslat.aoa3.common.registration.ArmourRegister";
    private static final String BIOME_REGISTER = "net.tslat.aoa3.common.registration.BiomeRegister";
    private static final String DIMENSION_REGISTER = "net.tslat.aoa3.common.registration.DimensionRegister";
    private static final String ENTITY_REGISTER = "net.tslat.aoa3.common.registration.EntityRegister";
    private static final String SOUNDS_REGISTER = "net.tslat.aoa3.common.registration.SoundsRegister";
    private static final EnumSet<Side> DEFAULT_SIDES = EnumSet.allOf(Side.class);
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

    public static boolean tryInjectAutomaticSubscribers(ModContainer mod, ASMDataTable asmData, Side side) {
        if (!ENABLED || mod == null || asmData == null || !"aoa3".equals(mod.getModId())
                || !TargetedModVersions.isAdventOfAscensionClass("net.tslat.aoa3.advent.AdventOfAscension")) {
            return false;
        }

        Set<String> classNames = selectedSubscriberClassNames(mod, asmData, side);
        if (classNames.isEmpty()) {
            return false;
        }

        for (String className : classNames) {
            if (!isKnownSubscriber(className)) {
                return false;
            }
        }

        for (String className : classNames) {
            if (!initializeSubscriberClass(className)) {
                return false;
            }
            registerByName(className, mod);
        }
        return true;
    }

    public static boolean tryRegisterAutomaticSubscriber(String modId, Object target, ModContainer owner) {
        if (!ENABLED || !"aoa3".equals(modId) || !(target instanceof Class)) {
            return false;
        }

        Class<?> targetClass = (Class<?>) target;
        if (!TargetedModVersions.isAdventOfAscensionClass(targetClass)) {
            return false;
        }
        if (!initializeSubscriberClass(targetClass)) {
            return false;
        }
        if (ENTITY_RENDERS.equals(targetClass.getName())) {
            registerEntityRenders(owner);
            return true;
        }
        if (BLOCK_REGISTER.equals(targetClass.getName())) {
            registerBlockRegister(owner);
            return true;
        }
        if (ITEM_REGISTER.equals(targetClass.getName())) {
            registerItemRegister(owner);
            return true;
        }
        if (WEAPON_REGISTER.equals(targetClass.getName())) {
            registerWeaponRegister(owner);
            return true;
        }
        if (ENCHANTMENTS_REGISTER.equals(targetClass.getName())) {
            registerEnchantmentsRegister(owner);
            return true;
        }
        if (TOOL_REGISTER.equals(targetClass.getName())) {
            registerToolRegister(owner);
            return true;
        }
        if (ARMOUR_REGISTER.equals(targetClass.getName())) {
            registerArmourRegister(owner);
            return true;
        }
        if (BIOME_REGISTER.equals(targetClass.getName())) {
            registerBiomeRegister(owner);
            return true;
        }
        if (ENTITY_REGISTER.equals(targetClass.getName())) {
            registerEntityRegister(owner);
            return true;
        }
        if (SOUNDS_REGISTER.equals(targetClass.getName())) {
            registerSoundsRegister(owner);
            return true;
        }
        if (OVERWORLD_ORE.equals(targetClass.getName()) || DIMENSION_REGISTER.equals(targetClass.getName())) {
            return true;
        }

        return false;
    }

    private static boolean isKnownSubscriber(String className) {
        return ENTITY_RENDERS.equals(className)
                || BLOCK_REGISTER.equals(className)
                || ITEM_REGISTER.equals(className)
                || WEAPON_REGISTER.equals(className)
                || ENCHANTMENTS_REGISTER.equals(className)
                || TOOL_REGISTER.equals(className)
                || OVERWORLD_ORE.equals(className)
                || ARMOUR_REGISTER.equals(className)
                || BIOME_REGISTER.equals(className)
                || DIMENSION_REGISTER.equals(className)
                || ENTITY_REGISTER.equals(className)
                || SOUNDS_REGISTER.equals(className);
    }

    private static void registerByName(String className, ModContainer owner) {
        if (ENTITY_RENDERS.equals(className)) {
            registerEntityRenders(owner);
        } else if (BLOCK_REGISTER.equals(className)) {
            registerBlockRegister(owner);
        } else if (ITEM_REGISTER.equals(className)) {
            registerItemRegister(owner);
        } else if (WEAPON_REGISTER.equals(className)) {
            registerWeaponRegister(owner);
        } else if (ENCHANTMENTS_REGISTER.equals(className)) {
            registerEnchantmentsRegister(owner);
        } else if (TOOL_REGISTER.equals(className)) {
            registerToolRegister(owner);
        } else if (ARMOUR_REGISTER.equals(className)) {
            registerArmourRegister(owner);
        } else if (BIOME_REGISTER.equals(className)) {
            registerBiomeRegister(owner);
        } else if (ENTITY_REGISTER.equals(className)) {
            registerEntityRegister(owner);
        } else if (SOUNDS_REGISTER.equals(className)) {
            registerSoundsRegister(owner);
        }
    }

    private static void registerEntityRenders(ModContainer owner) {
        FmlConstructionSafety.subscriberRegistration("AoA subscriber register " + ENTITY_RENDERS, () -> {
            ModelRegistryEvent event = new ModelRegistryEvent();
            event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
                @Override
                public void invoke(Event event) {
                    if (event instanceof IContextSetter) {
                        ((IContextSetter) event).setModContainer(owner);
                    }
                    EntityRenders.registerEntityRenders((ModelRegistryEvent) event);
                }
            });
        });
    }

    private static void registerBlockRegister(ModContainer owner) {
        registerModelRegistry(owner, new ModelRegistryInvoker() {
            @Override
            public void invoke(ModelRegistryEvent event) {
                BlockRegister.registerItemBlockRenders(event);
            }
        });
        registerRegistry(owner, Block.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                BlockRegister.registerBlocks(event);
                logGrassRegistryState("block", event.getRegistry());
            }
        });
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                BlockRegister.registerItemBlocks(event);
                recoverMissingGrassItemBlocks(event.getRegistry());
                logGrassRegistryState("item", event.getRegistry());
            }
        });
        registerMissingMappings(owner, Block.class, new MissingMappingsInvoker() {
            @Override
            public void invoke(RegistryEvent.MissingMappings event) {
                BlockRegister.remapMissing(event);
            }
        });
    }

    private static void registerItemRegister(ModContainer owner) {
        registerModelRegistry(owner, new ModelRegistryInvoker() {
            @Override
            public void invoke(ModelRegistryEvent event) {
                ItemRegister.registerItemRenders(event);
            }
        });
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                ItemRegister.registerItems(event);
            }
        });
        registerMissingMappings(owner, Item.class, new MissingMappingsInvoker() {
            @Override
            public void invoke(RegistryEvent.MissingMappings event) {
                ItemRegister.remapMissing(event);
            }
        });
    }

    private static void registerWeaponRegister(ModContainer owner) {
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                WeaponRegister.registerWeapon(event);
            }
        });
        registerMissingMappings(owner, Item.class, new MissingMappingsInvoker() {
            @Override
            public void invoke(RegistryEvent.MissingMappings event) {
                WeaponRegister.remapMissing(event);
            }
        });
    }

    private static void registerEnchantmentsRegister(ModContainer owner) {
        registerRegistry(owner, Enchantment.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                EnchantmentsRegister.registerEnchantments(event);
            }
        });
    }

    private static void registerToolRegister(ModContainer owner) {
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                ToolRegister.registerTools(event);
            }
        });
    }

    private static void registerArmourRegister(ModContainer owner) {
        registerRegistry(owner, Item.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                ArmourRegister.registerArmours(event);
            }
        });
    }

    private static void registerBiomeRegister(ModContainer owner) {
        registerRegistry(owner, Biome.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                BiomeRegister.registerBiomes(event);
            }
        });
    }

    private static void registerEntityRegister(ModContainer owner) {
        registerRegistry(owner, EntityEntry.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                EntityRegister.registerEntities(event);
            }
        });
    }

    private static void registerSoundsRegister(ModContainer owner) {
        registerRegistry(owner, SoundEvent.class, new RegisterInvoker() {
            @Override
            public void invoke(RegistryEvent.Register event) {
                SoundsRegister.registerSounds(event);
            }
        });
    }

    private static void registerModelRegistry(final ModContainer owner, final ModelRegistryInvoker invoker) {
        FmlConstructionSafety.subscriberRegistration("AoA subscriber register ModelRegistryEvent", () -> {
            ModelRegistryEvent event = new ModelRegistryEvent();
            event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
                @Override
                public void invoke(Event event) {
                    setOwner(event, owner);
                    invoker.invoke((ModelRegistryEvent) event);
                }
            });
        });
    }

    private static void registerRegistry(final ModContainer owner, final Class<?> registryType, final RegisterInvoker invoker) {
        FmlConstructionSafety.subscriberRegistration("AoA subscriber register RegistryEvent.Register " + registryType.getName(), () -> {
            RegistryEvent.Register event = new RegistryEvent.Register(null, registryFor(registryType));
            event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
                @Override
                public void invoke(Event event) {
                    RegistryEvent.Register registryEvent = (RegistryEvent.Register) event;
                    if (registryEvent.getRegistry() == null || registryEvent.getRegistry().getRegistrySuperType() != registryType) {
                        return;
                    }
                    setOwner(event, owner);
                    invoker.invoke(registryEvent);
                }
            });
        });
    }

    private static void registerMissingMappings(final ModContainer owner, final Class<?> registryType, final MissingMappingsInvoker invoker) {
        FmlConstructionSafety.subscriberRegistration("AoA subscriber register RegistryEvent.MissingMappings " + registryType.getName(), () -> {
            RegistryEvent.MissingMappings event = new RegistryEvent.MissingMappings(null, registryFor(registryType), java.util.Collections.emptyList());
            event.getListenerList().register(0, EventPriority.NORMAL, new IEventListener() {
                @Override
                public void invoke(Event event) {
                    RegistryEvent.MissingMappings mappingEvent = (RegistryEvent.MissingMappings) event;
                    if (mappingEvent.getRegistry() == null || mappingEvent.getRegistry().getRegistrySuperType() != registryType) {
                        return;
                    }
                    setOwner(event, owner);
                    invoker.invoke(mappingEvent);
                }
            });
        });
    }

    private static void setOwner(Event event, ModContainer owner) {
        if (event instanceof IContextSetter) {
            ((IContextSetter) event).setModContainer(owner);
        }
    }

    private static boolean initializeSubscriberClass(String className) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = AoAConstructionOptimizations.class.getClassLoader();
            }
            Class.forName(className, true, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            GPOM.LOGGER.warn("[AoA Construction] Falling back to Forge subscriber injection; could not initialize {}", className, exception);
            return false;
        }
    }

    private static boolean initializeSubscriberClass(Class<?> targetClass) {
        try {
            Class.forName(targetClass.getName(), true, targetClass.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            GPOM.LOGGER.warn("[AoA Construction] Falling back to Forge subscriber registration; could not initialize {}", targetClass.getName(), exception);
            return false;
        }
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
            GPOM.LOGGER.warn("[AoA Construction] AoA grass {} registry missing {}/{} entries: {}",
                    registryType,
                    missingCount,
                    GRASS_REGISTRY_IDS.length,
                    missing);
        }
    }

    public static void logFinalGrassRegistryState(String stage) {
        logGrassRegistryState(stage + " block", ForgeRegistries.BLOCKS);
        logGrassRegistryState(stage + " item", ForgeRegistries.ITEMS);
    }

    private static void recoverMissingGrassItemBlocks(IForgeRegistry<Item> itemRegistry) {
        if (itemRegistry == null) {
            return;
        }

        int recovered = 0;
        for (String id : GRASS_REGISTRY_IDS) {
            ResourceLocation registryName = new ResourceLocation("aoa3", id);
            if (itemRegistry.getValue(registryName) != null) {
                continue;
            }

            Block block = ForgeRegistries.BLOCKS.getValue(registryName);
            if (block == null) {
                GPOM.LOGGER.warn("[AoA Construction] Could not recover missing AoA grass item {}; block was also missing during item registration", registryName);
                continue;
            }

            ItemBlock itemBlock = new ItemBlock(block);
            itemBlock.setRegistryName(registryName);
            itemRegistry.register(itemBlock);
            recovered++;
        }

        if (recovered > 0) {
            GPOM.LOGGER.warn("[AoA Construction] Recovered {} missing AoA grass item-block registrations from existing grass blocks", recovered);
        }
    }

    private static IForgeRegistry<?> registryFor(Class<?> registryType) {
        if (registryType == Block.class) {
            return ForgeRegistries.BLOCKS;
        }
        if (registryType == Item.class) {
            return ForgeRegistries.ITEMS;
        }
        if (registryType == Enchantment.class) {
            return ForgeRegistries.ENCHANTMENTS;
        }
        if (registryType == Biome.class) {
            return ForgeRegistries.BIOMES;
        }
        if (registryType == EntityEntry.class) {
            return ForgeRegistries.ENTITIES;
        }
        if (registryType == SoundEvent.class) {
            return ForgeRegistries.SOUND_EVENTS;
        }
        throw new IllegalArgumentException("Unsupported AoA registry type: " + registryType.getName());
    }

    private static Set<String> selectedSubscriberClassNames(ModContainer mod, ASMDataTable asmData, Side side) {
        SetMultimap<String, ASMDataTable.ASMData> annotations = asmData.getAnnotationsFor(mod);
        Set<ASMDataTable.ASMData> modAnnotations = annotations.get(net.minecraftforge.fml.common.Mod.class.getName());
        Set<ASMDataTable.ASMData> subscriberAnnotations = annotations.get(net.minecraftforge.fml.common.Mod.EventBusSubscriber.class.getName());
        Set<String> classNames = new LinkedHashSet<>(subscriberAnnotations.size());

        for (ASMDataTable.ASMData data : subscriberAnnotations) {
            if (!subscriberMatchesCurrentSide(data, side)) {
                continue;
            }

            String ownerModId = annotationModId(data);
            if (ownerModId == null || ownerModId.isEmpty()) {
                ownerModId = ASMDataTable.getOwnerModID(modAnnotations, data);
            }
            if (mod.getModId().equals(ownerModId)) {
                classNames.add(data.getClassName());
            }
        }
        return classNames;
    }

    private static boolean subscriberMatchesCurrentSide(ASMDataTable.ASMData data, Side side) {
        Object value = data.getAnnotationInfo().get("value");
        if (!(value instanceof List)) {
            return true;
        }

        @SuppressWarnings("unchecked")
        List<Object> configuredSides = (List<Object>) value;
        if (configuredSides.isEmpty()) {
            return DEFAULT_SIDES.contains(side);
        }

        for (Object configuredSide : configuredSides) {
            Side selectedSide = sideFromAnnotationValue(configuredSide);
            if (selectedSide == side) {
                return true;
            }
        }
        return false;
    }

    private static Side sideFromAnnotationValue(Object value) {
        if (value instanceof EnumHolder) {
            return Side.valueOf(((EnumHolder) value).getValue());
        }
        return Side.valueOf(String.valueOf(value));
    }

    private static String annotationModId(ASMDataTable.ASMData data) {
        Object modId = data.getAnnotationInfo().get("modid");
        return modId instanceof String ? (String) modId : null;
    }

    private interface ModelRegistryInvoker {
        void invoke(ModelRegistryEvent event);
    }

    private interface RegisterInvoker {
        void invoke(RegistryEvent.Register event);
    }

    private interface MissingMappingsInvoker {
        void invoke(RegistryEvent.MissingMappings event);
    }
}
