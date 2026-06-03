package com.l.gpom.optimization;

import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

public final class RebornCoreTileRegistrationOptimizations {
    private static final boolean FAST_TECH_REBORN_TILES = Boolean.parseBoolean(System.getProperty("gpom.reborncore.fastTechRebornTileRegistration", "true"));
    private static final String TECH_REBORN_DOMAIN = "techreborn";
    private static final String[] TECH_REBORN_TILE_CLASSES = {
            "techreborn.tiles.generator.fluid.TilePlasmaGenerator",
            "techreborn.tiles.generator.fluid.lv.TileDieselGenerator",
            "techreborn.tiles.generator.fluid.lv.TileGasTurbine",
            "techreborn.tiles.generator.fluid.lv.TileSemiFluidGenerator",
            "techreborn.tiles.generator.fluid.lv.TileThermalGenerator",
            "techreborn.tiles.processing.TileAlloySmelter",
            "techreborn.tiles.processing.TileAssemblingMachine",
            "techreborn.tiles.processing.TileChemicalReactor",
            "techreborn.tiles.processing.TileCompressor",
            "techreborn.tiles.processing.TileExtractor",
            "techreborn.tiles.processing.TileGrinder",
            "techreborn.tiles.processing.TileIndustrialCentrifuge",
            "techreborn.tiles.processing.TilePlateBendingMachine",
            "techreborn.tiles.processing.TileRecycler",
            "techreborn.tiles.processing.TileSolidCanningMachine",
            "techreborn.tiles.processing.TileWireMill"
    };
    private static final String[] TECH_REBORN_TILE_NAMES = {
            "plasma_generator",
            "diesel_generator",
            "gas_turbine",
            "semi_fluid_generator",
            "thermal_generator",
            "alloy_smelter",
            "assembling_machine",
            "chemical_reactor",
            "compressor",
            "extractor",
            "grinder",
            "industrial_centrifuge",
            "plate_bending_machine",
            "recycler",
            "solid_canning_machine",
            "wire_mill"
    };

    private static volatile Field domainField;
    private static volatile Field loggerField;
    private static volatile Field registeredTilesField;

    private RebornCoreTileRegistrationOptimizations() {
    }

    public static boolean tryRegisterTechRebornTiles(Object manager) {
        if (!FAST_TECH_REBORN_TILES || manager == null || !TargetedModVersions.isTechRebornSuiteAvailable()) {
            return false;
        }

        String domain = readDomain(manager);
        if (!TECH_REBORN_DOMAIN.equals(domain)) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        Class<? extends TileEntity>[] tileClasses = loadTechRebornTileClasses();
        ConcurrentHashMap<ResourceLocation, Class<? extends TileEntity>> registeredTiles = readRegisteredTiles(manager);
        Logger logger = readLogger(manager);

        for (int i = 0; i < TECH_REBORN_TILE_CLASSES.length; i++) {
            ResourceLocation resourceLocation = new ResourceLocation(domain, TECH_REBORN_TILE_NAMES[i]);
            Class<? extends TileEntity> tileClass = tileClasses[i];
            GameRegistry.registerTileEntity(tileClass, resourceLocation);
            if (registeredTiles.put(resourceLocation, tileClass) != null && logger != null) {
                logger.error(String.format("The tile with ResourceLocation %s was already registered before.", resourceLocation.toString()));
            }
        }

        StartupProfiler.endProbeAlways("TR TileRegistrationManager.fastTechRebornDirectList", startedAt);
        return true;
    }

    private static String readDomain(Object manager) {
        try {
            return (String) field(manager.getClass(), "domain", domainField).get(manager);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read RebornCore tile registration domain", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMap<ResourceLocation, Class<? extends TileEntity>> readRegisteredTiles(Object manager) {
        try {
            return (ConcurrentHashMap<ResourceLocation, Class<? extends TileEntity>>) field(manager.getClass(), "registeredTiles", registeredTilesField).get(manager);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to read RebornCore registered tile map", exception);
        }
    }

    private static Logger readLogger(Object manager) {
        try {
            return (Logger) field(manager.getClass(), "logger", loggerField).get(manager);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Field field(Class<?> owner, String name, Field cached) throws NoSuchFieldException {
        if (cached != null) {
            return cached;
        }

        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        if ("domain".equals(name)) {
            domainField = field;
        } else if ("logger".equals(name)) {
            loggerField = field;
        } else if ("registeredTiles".equals(name)) {
            registeredTilesField = field;
        }
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends TileEntity>[] loadTechRebornTileClasses() {
        Class<?>[] classes = new Class<?>[TECH_REBORN_TILE_CLASSES.length];
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : RebornCoreTileRegistrationOptimizations.class.getClassLoader();
        try {
            for (int i = 0; i < TECH_REBORN_TILE_CLASSES.length; i++) {
                classes[i] = Class.forName(TECH_REBORN_TILE_CLASSES[i], false, loader).asSubclass(TileEntity.class);
            }
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to load targeted TechReborn tile class", exception);
        }
        return (Class<? extends TileEntity>[]) classes;
    }
}
