package com.l.gpom.proxy;

import com.l.gpom.GPOM;
import com.l.gpom.compat.blockcraftery.BlockcrafteryOrphanFrameRecoveryEvents;
import com.l.gpom.compat.bloodmagic.BloodMagicWorldMapRecoveryEvents;
import com.l.gpom.compat.hei.HeiQuickCraftNetwork;
import com.l.gpom.compat.randomthings.RandomThingsRuneCommonEvents;
import com.l.gpom.compat.randomthings.RandomThingsRuneNetwork;
import com.l.gpom.compat.thaumcraft.ThaumcraftResearchRecoveryEvents;
import com.l.gpom.compat.torohealth.ToroHealthCombatHealthSync;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.optimization.MissingMappingRepairs;
import com.l.gpom.profiling.RuntimeSinkProfilerEvents;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.lang.reflect.Method;

public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        registerBaublesSideSlotsNetwork();
        registerDankStoragePickupSync();
        HeiQuickCraftNetwork.registerIfNeeded();
        RandomThingsRuneCommonEvents.registerIfNeeded();
        RandomThingsRuneNetwork.registerIfNeeded();
        ToroHealthCombatHealthSync.registerIfNeeded();
        registerSfmMagicalCapabilityIntegration();
        BlockcrafteryOrphanFrameRecoveryEvents.registerIfNeeded();
        MissingMappingRepairs.register();
        BloodMagicWorldMapRecoveryEvents.register();
        ThaumcraftResearchRecoveryEvents.register();
        registerAe2PatternDiagnostics();
        RuntimeSinkProfilerEvents.register();
    }

    public boolean isClient() {
        return false;
    }

    private static void registerBaublesSideSlotsNetwork() {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled() || !Loader.isModLoaded("baubles")) {
            return;
        }
        invokeOptional("baubles", "com.l.gpom.compat.baubles.BaublesSideSlotsNetwork", "registerIfEnabled");
    }

    private static void registerDankStoragePickupSync() {
        if (!GpomEarlyConfig.dankStoragePortablePickupSyncEnabled() || !Loader.isModLoaded("dankstorage")) {
            return;
        }
        invokeOptional("dankstorage", "com.l.gpom.compat.dankstorage.DankStoragePortablePickupSync", "registerIfEnabled");
    }

    private static void registerAe2PatternDiagnostics() {
        if (!GpomEarlyConfig.ae2PatternDiagnosticsEnabled() || !Loader.isModLoaded("appliedenergistics2")) {
            return;
        }
        invokeOptional("appliedenergistics2", "com.l.gpom.diagnostics.Ae2PatternDiagnostics", "register");
    }

    private static void registerSfmMagicalCapabilityIntegration() {
        if (!GpomEarlyConfig.sfmCustomResourcesEnabled() || !Loader.isModLoaded("superfactorymanager")) {
            return;
        }
        invokeOptional("superfactorymanager", "com.l.gpom.compat.sfm.integration.SfmMagicalCapabilityIntegration", "registerIfNeeded");
    }

    private static void invokeOptional(String modId, String className, String methodName) {
        try {
            Class<?> type = Class.forName(className, true, CommonProxy.class.getClassLoader());
            Method method = type.getMethod(methodName);
            method.invoke(null);
        } catch (ClassNotFoundException | NoClassDefFoundError ignored) {
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Optional] Skipping {} integration because {} is unavailable", modId, className);
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException throwable) {
            GPOM.LOGGER.warn("[GPOM Optional] Failed to initialize {} integration {}", modId, className, throwable);
        }
    }
}
