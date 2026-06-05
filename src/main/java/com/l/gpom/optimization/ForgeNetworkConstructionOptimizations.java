package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.ILanguageAdapter;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.versioning.VersionRange;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ForgeNetworkConstructionOptimizations {
    private static final String CONFIG_CHECKER_MOD_ID = "concheckrmd";
    private static final String CONFIG_CHECKER_MOD_CLASS = "com.matt_r__.ConfigChecker.Main";
    private static final String ITEM_BLACKLIST_MOD_ID = "itemblacklist";
    private static final String ITEM_BLACKLIST_MOD_CLASS = "net.doubledoordev.itemblacklist.ItemBlacklist";
    private static final String ITEM_BLACKLIST_PROXY_FIELD = "proxy";
    private static final String ITEM_BLACKLIST_CLIENT_PROXY = "net.doubledoordev.itemblacklist.client.Proxy";
    private static final String ITEM_BLACKLIST_SERVER_PROXY = "net.doubledoordev.itemblacklist.ItemBlacklist";
    private static final String REDCORE_MOD_ID = "redcore";
    private static final String REDCORE_MOD_CLASS = "dev.redstudio.redcore.asm.RedCorePlugin";
    private static final String SMOOTHFONT_MOD_ID = "smoothfont";
    private static final String SMOOTHFONT_MOD_CLASS = "bre.smoothfont.mod_SmoothFont";
    private static final String SMOOTHFONT_PROXY_FIELD = "proxy";
    private static final String SMOOTHFONT_CLIENT_PROXY = "bre.smoothfont.proxy.ClientProxy";
    private static final String SMOOTHFONT_SERVER_PROXY = "bre.smoothfont.proxy.CommonProxy";
    private static final String HOLDER_CLASS = "net.minecraftforge.fml.common.network.internal.NetworkModHolder";
    private static final String DEFAULT_CHECKER_CLASS = HOLDER_CLASS + "$DefaultNetworkChecker";
    private static final String IGNORED_CHECKER_CLASS = HOLDER_CLASS + "$IgnoredChecker";
    private static final Set<String> WARNED_FAILURES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_PROXY_FAILURES = ConcurrentHashMap.newKeySet();
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

    public static boolean tryFastInjectKnownProxy(ModContainer container, Side side, ILanguageAdapter adapter) {
        ProxySpec spec = proxySpec(container);
        if (spec == null || side == null || adapter == null) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            ClassLoader loader = Loader.instance().getModClassLoader();
            Class<?> ownerClass = Class.forName(spec.ownerClass, true, loader);
            if (!isSupportedProxyTarget(container, ownerClass, spec)) {
                return false;
            }
            if (spec.hasSidedProxy()) {
                injectExactSidedProxy(spec, side, adapter, loader, ownerClass);
                GPOM.LOGGER.info(
                        "[ForgeNetworkConstructionOptimizations] Fast-injected sided proxy for {} ({}.{})",
                        container.getModId(),
                        spec.ownerClass,
                        spec.fieldName
                );
            } else {
                GPOM.LOGGER.info(
                        "[ForgeNetworkConstructionOptimizations] Fast-skipped sided proxy scan for {} ({})",
                        container.getModId(),
                        spec.ownerClass
                );
            }
            adapter.setInternalProxies(container, side, loader);
            return true;
        } catch (Throwable throwable) {
            if (WARNED_PROXY_FAILURES.add(container.getModId())) {
                GPOM.LOGGER.warn(
                        "[ForgeNetworkConstructionOptimizations] Fast proxy injection failed for {}; falling back to Forge",
                        container.getModId(),
                        throwable
                );
            }
            return false;
        } finally {
            StartupProfiler.endProbeAlways("FML construct " + container.getModId() + " fastProxyInject", startedAt);
        }
    }

    private static boolean isSupportedNoCheckerTarget(ModContainer container, Class<?> modClass, ASMDataTable asmData) {
        if (container == null || modClass == null || asmData == null) {
            return false;
        }
        // The slow Forge path is ASMDataTable.getAnnotationsFor(container), so exact no-checker
        // targets must be proven by jar/class gates rather than re-reading ASM data here.
        return isConfigCheckerTarget(container, modClass)
                || isItemBlacklistTarget(container, modClass)
                || isRedCoreTarget(container, modClass)
                || isSmoothFontTarget(container, modClass);
    }

    private static boolean isConfigCheckerTarget(ModContainer container, Class<?> modClass) {
        return CONFIG_CHECKER_MOD_ID.equals(container.getModId())
                && CONFIG_CHECKER_MOD_CLASS.equals(modClass.getName())
                && TargetedModVersions.isModpackConfigCheckerClass(modClass);
    }

    private static boolean isItemBlacklistTarget(ModContainer container, Class<?> modClass) {
        return ITEM_BLACKLIST_MOD_ID.equals(container.getModId())
                && ITEM_BLACKLIST_MOD_CLASS.equals(modClass.getName())
                && TargetedModVersions.isItemBlacklistClass(modClass);
    }

    private static boolean isRedCoreTarget(ModContainer container, Class<?> modClass) {
        return REDCORE_MOD_ID.equals(container.getModId())
                && REDCORE_MOD_CLASS.equals(modClass.getName())
                && TargetedModVersions.isRedCoreClass(modClass);
    }

    private static boolean isSmoothFontTarget(ModContainer container, Class<?> modClass) {
        return SMOOTHFONT_MOD_ID.equals(container.getModId())
                && SMOOTHFONT_MOD_CLASS.equals(modClass.getName())
                && TargetedModVersions.isSmoothFontClass(modClass);
    }

    private static boolean isSupportedProxyTarget(ModContainer container, Class<?> ownerClass, ProxySpec spec) {
        if (container == null || ownerClass == null || spec == null) {
            return false;
        }
        return (CONFIG_CHECKER_MOD_ID.equals(spec.modId) && isConfigCheckerTarget(container, ownerClass))
                || (ITEM_BLACKLIST_MOD_ID.equals(spec.modId) && isItemBlacklistTarget(container, ownerClass))
                || (REDCORE_MOD_ID.equals(spec.modId) && isRedCoreTarget(container, ownerClass))
                || (SMOOTHFONT_MOD_ID.equals(spec.modId) && isSmoothFontTarget(container, ownerClass));
    }

    private static ProxySpec proxySpec(ModContainer container) {
        if (container == null) {
            return null;
        }
        String modId = container.getModId();
        if (CONFIG_CHECKER_MOD_ID.equals(modId)) {
            return ProxySpec.noProxy(CONFIG_CHECKER_MOD_ID, CONFIG_CHECKER_MOD_CLASS);
        }
        if (ITEM_BLACKLIST_MOD_ID.equals(modId)) {
            return new ProxySpec(
                    ITEM_BLACKLIST_MOD_ID,
                    ITEM_BLACKLIST_MOD_CLASS,
                    ITEM_BLACKLIST_PROXY_FIELD,
                    ITEM_BLACKLIST_CLIENT_PROXY,
                    ITEM_BLACKLIST_SERVER_PROXY
            );
        }
        if (REDCORE_MOD_ID.equals(modId)) {
            return ProxySpec.noProxy(REDCORE_MOD_ID, REDCORE_MOD_CLASS);
        }
        if (SMOOTHFONT_MOD_ID.equals(modId)) {
            return new ProxySpec(
                    SMOOTHFONT_MOD_ID,
                    SMOOTHFONT_MOD_CLASS,
                    SMOOTHFONT_PROXY_FIELD,
                    SMOOTHFONT_CLIENT_PROXY,
                    SMOOTHFONT_SERVER_PROXY
            );
        }
        return null;
    }

    private static void injectExactSidedProxy(ProxySpec spec, Side side, ILanguageAdapter adapter,
                                              ClassLoader loader, Class<?> ownerClass) throws Exception {
        Field field = ownerClass.getDeclaredField(spec.fieldName);
        field.setAccessible(true);
        SidedProxy annotation = field.getAnnotation(SidedProxy.class);
        if (annotation == null
                || !spec.clientProxy.equals(annotation.clientSide())
                || !spec.serverProxy.equals(annotation.serverSide())) {
            throw new LoaderException(String.format(
                    "Unexpected @SidedProxy annotation on %s.%s",
                    spec.ownerClass,
                    spec.fieldName
            ));
        }

        String proxyClassName = side.isClient() ? spec.clientProxy : spec.serverProxy;
        Object proxy = Class.forName(proxyClassName, true, loader).newInstance();
        if (adapter.supportsStatics() && !Modifier.isStatic(field.getModifiers())) {
            throw new LoaderException(String.format(
                    "Attempted to load a proxy type %s into %s.%s, but the field is not static",
                    proxyClassName,
                    spec.ownerClass,
                    spec.fieldName
            ));
        }
        if (!field.getType().isAssignableFrom(proxy.getClass())) {
            throw new LoaderException(String.format(
                    "Attempted to load a proxy type %s into %s.%s, but the types don't match",
                    proxyClassName,
                    spec.ownerClass,
                    spec.fieldName
            ));
        }
        adapter.setProxy(field, ownerClass, proxy);
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

    private static final class ProxySpec {
        private final String modId;
        private final String ownerClass;
        private final String fieldName;
        private final String clientProxy;
        private final String serverProxy;

        private ProxySpec(String modId, String ownerClass, String fieldName, String clientProxy, String serverProxy) {
            this.modId = modId;
            this.ownerClass = ownerClass;
            this.fieldName = fieldName;
            this.clientProxy = clientProxy;
            this.serverProxy = serverProxy;
        }

        private static ProxySpec noProxy(String modId, String ownerClass) {
            return new ProxySpec(modId, ownerClass, null, null, null);
        }

        private boolean hasSidedProxy() {
            return fieldName != null;
        }
    }
}
