package com.l.gpom.optimization;

import com.google.common.collect.SetMultimap;
import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.versioning.VersionRange;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ForgeNetworkConstructionOptimizations {
    private static final String SMOOTHFONT_MOD_ID = "smoothfont";
    private static final String SMOOTHFONT_MOD_CLASS = "bre.smoothfont.mod_SmoothFont";
    private static final String HOLDER_CLASS = "net.minecraftforge.fml.common.network.internal.NetworkModHolder";
    private static final String DEFAULT_CHECKER_CLASS = HOLDER_CLASS + "$DefaultNetworkChecker";
    private static final String IGNORED_CHECKER_CLASS = HOLDER_CLASS + "$IgnoredChecker";
    private static final Set<String> WARNED_FAILURES = ConcurrentHashMap.newKeySet();
    private static volatile Constructor<?> holderConstructor;
    private static volatile Constructor<?> defaultCheckerConstructor;
    private static volatile Constructor<?> ignoredCheckerConstructor;
    private static volatile Field networkRegistryMapField;
    private static volatile Field checkerField;
    private static volatile Field acceptableRangeField;
    private static volatile Method testVanillaAcceptanceMethod;

    private ForgeNetworkConstructionOptimizations() {
    }

    public static boolean tryFastRegisterKnownNoNetworkChecker(NetworkRegistry registry,
                                                               ModContainer container,
                                                               Class<?> modClass,
                                                               String acceptableRemoteVersions,
                                                               ASMDataTable asmData) {
        if (!isSupportedNoCheckerTarget(container, modClass, asmData)) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            Object holder = holderConstructor().newInstance(container);
            configureChecker(holder, acceptableRemoteVersions);
            registryMap(registry).put(container, holder);
            testVanillaAcceptance().invoke(holder);
            StartupProfiler.endProbeAlways("FML construct " + container.getModId() + " fastNetworkHolder", startedAt);
            GPOM.LOGGER.info(
                    "[ForgeNetworkConstructionOptimizations] Fast-registered default network holder for {} ({})",
                    container.getModId(),
                    modClass.getName()
            );
            return true;
        } catch (Throwable throwable) {
            if (WARNED_FAILURES.add(container.getModId())) {
                GPOM.LOGGER.warn(
                        "[ForgeNetworkConstructionOptimizations] Fast network holder failed for {}; falling back to Forge",
                        container.getModId(),
                        throwable
                );
            }
            return false;
        }
    }

    private static boolean isSupportedNoCheckerTarget(ModContainer container, Class<?> modClass, ASMDataTable asmData) {
        if (container == null || modClass == null || asmData == null) {
            return false;
        }
        if (!SMOOTHFONT_MOD_ID.equals(container.getModId())
                || !SMOOTHFONT_MOD_CLASS.equals(modClass.getName())
                || !TargetedModVersions.isSmoothFontClass(modClass)) {
            return false;
        }
        return !hasNetworkCheckHandler(asmData, container, modClass.getName());
    }

    private static boolean hasNetworkCheckHandler(ASMDataTable asmData, ModContainer container, String modClassName) {
        SetMultimap<String, ASMDataTable.ASMData> annotations = asmData.getAnnotationsFor(container);
        if (annotations == null) {
            return true;
        }
        Set<ASMDataTable.ASMData> networkHandlers = annotations.get(NetworkCheckHandler.class.getName());
        if (networkHandlers == null || networkHandlers.isEmpty()) {
            return false;
        }
        for (ASMDataTable.ASMData data : networkHandlers) {
            if (data != null && modClassName.equals(data.getClassName())) {
                return true;
            }
        }
        return true;
    }

    private static void configureChecker(Object holder, String acceptableRemoteVersions) throws Exception {
        if ("*".equals(acceptableRemoteVersions)) {
            checkerField().set(holder, ignoredCheckerConstructor().newInstance(holder));
            return;
        }
        if (acceptableRemoteVersions != null && !acceptableRemoteVersions.isEmpty()) {
            acceptableRangeField().set(holder, VersionRange.createFromVersionSpec(acceptableRemoteVersions));
        }
        checkerField().set(holder, defaultCheckerConstructor().newInstance(holder));
    }

    private static Constructor<?> holderConstructor() throws Exception {
        Constructor<?> constructor = holderConstructor;
        if (constructor == null) {
            constructor = Class.forName(HOLDER_CLASS).getConstructor(ModContainer.class);
            constructor.setAccessible(true);
            holderConstructor = constructor;
        }
        return constructor;
    }

    private static Constructor<?> defaultCheckerConstructor() throws Exception {
        Constructor<?> constructor = defaultCheckerConstructor;
        if (constructor == null) {
            constructor = Class.forName(DEFAULT_CHECKER_CLASS).getDeclaredConstructor(Class.forName(HOLDER_CLASS));
            constructor.setAccessible(true);
            defaultCheckerConstructor = constructor;
        }
        return constructor;
    }

    private static Constructor<?> ignoredCheckerConstructor() throws Exception {
        Constructor<?> constructor = ignoredCheckerConstructor;
        if (constructor == null) {
            constructor = Class.forName(IGNORED_CHECKER_CLASS).getDeclaredConstructor(Class.forName(HOLDER_CLASS));
            constructor.setAccessible(true);
            ignoredCheckerConstructor = constructor;
        }
        return constructor;
    }

    private static Field checkerField() throws Exception {
        Field field = checkerField;
        if (field == null) {
            field = Class.forName(HOLDER_CLASS).getDeclaredField("checker");
            field.setAccessible(true);
            checkerField = field;
        }
        return field;
    }

    private static Field acceptableRangeField() throws Exception {
        Field field = acceptableRangeField;
        if (field == null) {
            field = Class.forName(HOLDER_CLASS).getDeclaredField("acceptableRange");
            field.setAccessible(true);
            acceptableRangeField = field;
        }
        return field;
    }

    private static Method testVanillaAcceptance() throws Exception {
        Method method = testVanillaAcceptanceMethod;
        if (method == null) {
            method = Class.forName(HOLDER_CLASS).getMethod("testVanillaAcceptance");
            method.setAccessible(true);
            testVanillaAcceptanceMethod = method;
        }
        return method;
    }

    @SuppressWarnings("unchecked")
    private static Map<ModContainer, Object> registryMap(NetworkRegistry registry) throws Exception {
        Field field = networkRegistryMapField;
        if (field == null) {
            field = NetworkRegistry.class.getDeclaredField("registry");
            field.setAccessible(true);
            networkRegistryMapField = field;
        }
        return (Map<ModContainer, Object>) field.get(registry);
    }
}
