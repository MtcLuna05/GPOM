package com.l.gpom.optimization;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.ILanguageAdapter;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.asm.ModAnnotation;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IGenericEvent;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.network.NetworkCheckHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.versioning.InvalidVersionSpecificationException;
import net.minecraftforge.fml.common.versioning.VersionRange;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

public final class ForgeConstructionAnnotationOptimizations {
    private static final String HOLDER_CLASS = "net.minecraftforge.fml.common.network.internal.NetworkModHolder";
    private static final String DEFAULT_CHECKER_CLASS = HOLDER_CLASS + "$DefaultNetworkChecker";
    private static final String IGNORED_CHECKER_CLASS = HOLDER_CLASS + "$IgnoredChecker";
    private static final String METHOD_CHECKER_CLASS = HOLDER_CLASS + "$MethodNetworkChecker";

    private static final SetMultimap<String, ASMDataTable.ASMData> EMPTY_ANNOTATIONS = ImmutableSetMultimap.of();
    private static final Map<ASMDataTable, AnnotationIndex> ANNOTATION_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile Constructor<?> holderConstructor;
    private static volatile Constructor<?> defaultCheckerConstructor;
    private static volatile Constructor<?> ignoredCheckerConstructor;
    private static volatile Constructor<?> methodCheckerConstructor;
    private static volatile Field networkRegistryMapField;
    private static volatile Field eventBusIdField;
    private static volatile Field eventBusListenersField;
    private static volatile Field eventBusListenerOwnersField;
    private static volatile Field checkerField;
    private static volatile Field checkHandlerField;
    private static volatile Field acceptableRangeField;
    private static volatile Method testVanillaAcceptanceMethod;
    private static volatile Method parseSimpleFieldAnnotationMethod;

    private ForgeConstructionAnnotationOptimizations() {
    }

    public static boolean tryRegisterNetwork(NetworkRegistry registry,
                                             ModContainer container,
                                             Class<?> modClass,
                                             String acceptableRemoteVersions,
                                             ASMDataTable asmData) {
        if (registry == null || container == null || modClass == null || asmData == null) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            Object holder = holderConstructor().newInstance(container);
            configureNetworkChecker(holder, container, modClass, acceptableRemoteVersions);
            FmlConstructionSafety.networkRegistration(
                    "generic network holder " + container.getModId(),
                    () -> {
                        registryMap(registry).put(container, holder);
                        testVanillaAcceptance().invoke(holder);
                    }
            );
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn(
                    "[ForgeConstructionAnnotationOptimizations] Generic network holder failed for {}; falling back to Forge",
                    container.getModId(),
                    throwable
            );
            return false;
        } finally {
            StartupProfiler.endProbe("FML construct " + safeModId(container) + " genericNetworkHolder", startedAt);
        }
    }

    public static boolean tryInjectSidedProxies(ModContainer container,
                                                ASMDataTable asmData,
                                                Side side,
                                                ILanguageAdapter adapter) {
        if (container == null || asmData == null || side == null || adapter == null) {
            return false;
        }
        String modId = safeModId(container);
        if (GpomEarlyConfig.constructionGenericSidedProxiesDenylist().contains(modId.toLowerCase())) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            final SetMultimap<String, ASMDataTable.ASMData> annotations = annotationsFor(asmData, container);
            final Set<ASMDataTable.ASMData> modAnnotations = annotations.get(Mod.class.getName());
            final Set<ASMDataTable.ASMData> sidedProxies = annotations.get(SidedProxy.class.getName());
            final ClassLoader loader = Loader.instance().getModClassLoader();
            final int[] injected = new int[1];

            FmlConstructionSafety.proxyInjection(
                    "generic proxy inject " + safeModId(container),
                    () -> {
                        for (ASMDataTable.ASMData data : sidedProxies) {
                            if (!ownsAnnotation(container, modAnnotations, data, "modId", "@SidedProxy")) {
                                continue;
                            }

                            Class<?> ownerClass = Class.forName(data.getClassName(), true, loader);
                            Field field = ownerClass.getDeclaredField(data.getObjectName());
                            field.setAccessible(true);
                            SidedProxy annotation = field.getAnnotation(SidedProxy.class);
                            if (annotation == null) {
                                throw new LoaderException(String.format(
                                        "Attempted to load a proxy type into %s.%s, but the field is not annotated",
                                        data.getClassName(),
                                        data.getObjectName()
                                ));
                            }

                            String proxyClassName = side.isClient() ? annotation.clientSide() : annotation.serverSide();
                            if (proxyClassName.isEmpty()) {
                                proxyClassName = data.getClassName() + (side.isClient() ? "$ClientProxy" : "$ServerProxy");
                            }

                            Object proxy = Class.forName(proxyClassName, true, loader).newInstance();
                            if (adapter.supportsStatics() && !Modifier.isStatic(field.getModifiers())) {
                                throw new LoaderException(String.format(
                                        "Attempted to load a proxy type %s into %s.%s, but the field is not static",
                                        proxyClassName,
                                        data.getClassName(),
                                        data.getObjectName()
                                ));
                            }
                            if (!field.getType().isAssignableFrom(proxy.getClass())) {
                                throw new LoaderException(String.format(
                                        "Attempted to load a proxy type %s into %s.%s, but the types don't match",
                                        proxyClassName,
                                        data.getClassName(),
                                        data.getObjectName()
                                ));
                            }
                            adapter.setProxy(field, ownerClass, proxy);
                            injected[0]++;
                        }

                        adapter.setInternalProxies(container, side, loader);
                    }
            );
            if (injected[0] > 0) {
                GPOM.LOGGER.info(
                        "[ForgeConstructionAnnotationOptimizations] Generic-injected {} sided proxies for {}",
                        injected[0],
                        container.getModId()
                );
            }
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.error(
                    "[ForgeConstructionAnnotationOptimizations] Generic sided proxy injection failed for {}",
                    safeModId(container),
                    throwable
            );
            throw throwable instanceof LoaderException ? (LoaderException) throwable : new LoaderException(throwable);
        } finally {
            StartupProfiler.endProbe("FML construct " + safeModId(container) + " genericProxyInject", startedAt);
        }
    }

    public static boolean tryInjectAutomaticSubscribers(ModContainer mod, ASMDataTable asmData, Side side) {
        if (mod == null || asmData == null || side == null) {
            return false;
        }
        String modId = safeModId(mod);
        if (GpomEarlyConfig.constructionGenericAutomaticSubscribersDenylist().contains(modId.toLowerCase())) {
            return false;
        }

        try {
            SetMultimap<String, ASMDataTable.ASMData> annotations = annotationsFor(asmData, mod);
            Set<ASMDataTable.ASMData> modAnnotations = annotations.get(Mod.class.getName());
            Set<ASMDataTable.ASMData> subscribers = annotations.get(Mod.EventBusSubscriber.class.getName());
            ClassLoader loader = Loader.instance().getModClassLoader();
            int injected = 0;
            int lazyHandlers = 0;

            for (ASMDataTable.ASMData data : subscribers) {
                if (!sideMatches(data, side)) {
                    continue;
                }
                if (!ownsAnnotation(mod, modAnnotations, data, "modid", "@EventBusSubscriber")) {
                    continue;
                }

                String className = data.getClassName();
                long loadStartedAt = StartupProfiler.beginAutomaticSubscriberProbe();
                Class<?> subscriberClass;
                try {
                    subscriberClass = Class.forName(className, false, loader);
                } finally {
                    StartupProfiler.endAutomaticSubscriberClassLoad(mod.getModId(), className, loadStartedAt);
                }

                long registerStartedAt = StartupProfiler.beginAutomaticSubscriberProbe();
                try {
                    EventBus eventBus = MinecraftForge.EVENT_BUS;
                    List<SubscriberHandlerSpec> handlerSpecs = handlerSpecsFor(className, subscriberClass, loader, side);
                    FmlConstructionSafety.subscriberRegistration(
                            "automatic subscriber register " + mod.getModId() + " " + className,
                            () -> {
                                if (handlerSpecs.isEmpty()
                                        || !tryRegisterLazyStaticSubscriber(eventBus, mod, subscriberClass, handlerSpecs)) {
                                    eventBus.register(subscriberClass);
                                }
                            }
                    );
                    lazyHandlers += handlerSpecs.size();
                } finally {
                    StartupProfiler.endAutomaticSubscriberRegister(mod.getModId(), className, registerStartedAt);
                }
                injected++;
            }

            if (injected > 0) {
                GPOM.LOGGER.info(
                        "[ForgeConstructionAnnotationOptimizations] Generic-injected {} automatic subscribers for {} with {} lazy handler(s)",
                        injected,
                        mod.getModId(),
                        lazyHandlers
                );
            }
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.error(
                    "[ForgeConstructionAnnotationOptimizations] Generic automatic subscriber injection failed for {}",
                    safeModId(mod),
                    throwable
            );
            throw throwable instanceof LoaderException ? (LoaderException) throwable : new LoaderException(throwable);
        }
    }

    public static boolean tryProcessFieldAnnotations(FMLModContainer container, ASMDataTable asmData) throws IllegalAccessException {
        if (container == null || asmData == null) {
            return false;
        }

        SetMultimap<String, ASMDataTable.ASMData> annotations = annotationsFor(asmData, container);
        if (annotations.get(Mod.Instance.class.getName()).isEmpty()
                && annotations.get(Mod.Metadata.class.getName()).isEmpty()) {
            return true;
        }

        try {
            Method method = parseSimpleFieldAnnotation();
            Function<ModContainer, Object> modFunction = ModContainer::getMod;
            Function<ModContainer, Object> metadataFunction = ModContainer::getMetadata;
            FmlConstructionSafety.annotationProcessing(
                    "generic field annotations " + container.getModId(),
                    () -> {
                        method.invoke(container, annotations, Mod.Instance.class.getName(), modFunction);
                        method.invoke(container, annotations, Mod.Metadata.class.getName(), metadataFunction);
                    }
            );
            return true;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IllegalAccessException) {
                throw (IllegalAccessException) cause;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new LoaderException(cause);
        } catch (ReflectiveOperationException exception) {
            GPOM.LOGGER.warn(
                    "[ForgeConstructionAnnotationOptimizations] Generic field annotation processing unavailable for {}; falling back to Forge",
                    container.getModId(),
                    exception
            );
            return false;
        }
    }

    private static SetMultimap<String, ASMDataTable.ASMData> annotationsFor(ASMDataTable asmData, ModContainer container) {
        AnnotationIndex index;
        synchronized (ANNOTATION_CACHE) {
            index = ANNOTATION_CACHE.get(asmData);
            if (index == null) {
                long startedAt = StartupProfiler.beginProbe();
                try {
                    index = AnnotationIndex.build(asmData);
                    ANNOTATION_CACHE.put(asmData, index);
                } finally {
                    StartupProfiler.endProbe("FML construct annotationIndex buildGlobal", startedAt);
                }
            }
        }
        return index.annotationsFor(container);
    }

    private static boolean ownsAnnotation(ModContainer container,
                                          Set<ASMDataTable.ASMData> modAnnotations,
                                          ASMDataTable.ASMData data,
                                          String explicitOwnerKey,
                                          String annotationName) {
        String ownerModId = (String) data.getAnnotationInfo().get(explicitOwnerKey);
        if (Strings.isNullOrEmpty(ownerModId)) {
            ownerModId = ASMDataTable.getOwnerModID(modAnnotations, data);
            if (Strings.isNullOrEmpty(ownerModId)) {
                FMLLog.bigWarning(
                        "Could not determine owning mod for %s on %s for mod %s",
                        annotationName,
                        data.getClassName(),
                        container.getModId()
                );
                return false;
            }
        }
        return container.getModId().equals(ownerModId);
    }

    @SuppressWarnings("unchecked")
    private static boolean sideMatches(ASMDataTable.ASMData data, Side side) {
        List<ModAnnotation.EnumHolder> sideValues = (List<ModAnnotation.EnumHolder>) data.getAnnotationInfo().get("value");
        if (sideValues == null) {
            return true;
        }
        EnumSet<Side> allowedSides = EnumSet.noneOf(Side.class);
        for (ModAnnotation.EnumHolder holder : sideValues) {
            allowedSides.add(Side.valueOf(holder.getValue()));
        }
        return allowedSides.contains(side);
    }

    private static List<SubscriberHandlerSpec> handlerSpecsFor(String subscriberClassName,
                                                               Class<?> subscriberClass,
                                                               ClassLoader loader,
                                                               Side side) {
        String resourceName = subscriberClassName.replace('.', '/') + ".class";
        InputStream classStream = loader.getResourceAsStream(resourceName);
        if (classStream == null) {
            classStream = subscriberClass.getResourceAsStream('/' + resourceName);
        }

        try (InputStream inputStream = classStream) {
            if (inputStream == null) {
                return Collections.emptyList();
            }

            List<SubscriberHandlerSpec> specs = new ArrayList<>();
            boolean[] needsForgeFallback = new boolean[1];
            ClassReader reader = new ClassReader(inputStream);
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access,
                                                 String name,
                                                 String descriptor,
                                                 String signature,
                                                 String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        private boolean subscribeEvent;
                        private EventPriority priority = EventPriority.NORMAL;
                        private boolean receiveCanceled;
                        private Side methodSide;

                        @Override
                        public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                            if ("Lnet/minecraftforge/fml/relauncher/SideOnly;".equals(annotationDescriptor)) {
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitEnum(String annotationName, String enumDescriptor, String value) {
                                        if ("value".equals(annotationName)) {
                                            methodSide = Side.valueOf(value);
                                        }
                                    }
                                };
                            }

                            if ("Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;".equals(annotationDescriptor)) {
                                subscribeEvent = true;
                                return new AnnotationVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visit(String annotationName, Object value) {
                                        if ("receiveCanceled".equals(annotationName) && value instanceof Boolean) {
                                            receiveCanceled = (Boolean) value;
                                        }
                                    }

                                    @Override
                                    public void visitEnum(String annotationName, String enumDescriptor, String value) {
                                        if ("priority".equals(annotationName)) {
                                            priority = EventPriority.valueOf(value);
                                        }
                                    }
                                };
                            }

                            return null;
                        }

                        @Override
                        public void visitEnd() {
                            if (!subscribeEvent) {
                                return;
                            }
                            if (methodSide != null && methodSide != side) {
                                return;
                            }
                            SubscriberHandlerSpec spec = SubscriberHandlerSpec.fromBytecode(
                                    subscriberClassName,
                                    name,
                                    descriptor,
                                    signature,
                                    access,
                                    priority,
                                    receiveCanceled,
                                    loader
                            );
                            if (spec == null) {
                                needsForgeFallback[0] = true;
                            } else {
                                specs.add(spec);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (needsForgeFallback[0]) {
                return Collections.emptyList();
            }
            specs.sort(Comparator.comparing(SubscriberHandlerSpec::sortKey));
            return specs;
        } catch (IOException | RuntimeException exception) {
            GPOM.LOGGER.warn(
                    "[ForgeConstructionAnnotationOptimizations] Could not scan @SubscribeEvent bytecode for {}; falling back if needed",
                    subscriberClassName,
                    exception
            );
            return Collections.emptyList();
        }
    }
    @SuppressWarnings("unchecked")
    private static boolean tryRegisterLazyStaticSubscriber(EventBus eventBus,
                                                           ModContainer owner,
                                                           Class<?> subscriberClass,
                                                           List<SubscriberHandlerSpec> handlerSpecs) {
        try {
            Map<Object, ArrayList<IEventListener>> listeners =
                    (Map<Object, ArrayList<IEventListener>>) eventBusListenersField().get(eventBus);
            if (listeners.containsKey(subscriberClass)) {
                return true;
            }

            Map<Object, ModContainer> listenerOwners =
                    (Map<Object, ModContainer>) eventBusListenerOwnersField().get(eventBus);
            listenerOwners.put(subscriberClass, owner);

            int busId = eventBusIdField().getInt(eventBus);
            ArrayList<IEventListener> registeredListeners = new ArrayList<>(handlerSpecs.size());
            for (SubscriberHandlerSpec spec : handlerSpecs) {
                Event event = spec.eventType.getConstructor().newInstance();
                IEventListener listener = new LazyStaticSubscriberListener(owner, subscriberClass, spec);
                event.getListenerList().register(busId, spec.priority, listener);
                registeredListeners.add(listener);
            }
            listeners.put(subscriberClass, registeredListeners);
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn(
                    "[ForgeConstructionAnnotationOptimizations] Lazy automatic subscriber registration failed for {}; falling back to Forge",
                    subscriberClass.getName(),
                    throwable
            );
            return false;
        }
    }

    private static void configureNetworkChecker(Object holder,
                                                ModContainer container,
                                                Class<?> modClass,
                                                String acceptableRemoteVersions) throws Exception {
        Method checkHandler = findNetworkCheckHandler(modClass);
        if (checkHandler != null) {
            checkHandlerField().set(holder, checkHandler);
            checkerField().set(holder, methodCheckerConstructor().newInstance(holder));
            return;
        }
        configureDefaultNetworkChecker(holder, container, acceptableRemoteVersions);
    }

    private static Method findNetworkCheckHandler(Class<?> modClass) {
        Method declared = findAnnotatedNetworkCheckHandler(modClass.getDeclaredMethods());
        if (declared != null) {
            return declared;
        }
        for (Method method : modClass.getMethods()) {
            if (method.getDeclaringClass().equals(modClass)) {
                continue;
            }
            if (isValidNetworkCheckHandler(method)) {
                return method;
            }
        }
        return null;
    }

    private static Method findAnnotatedNetworkCheckHandler(Method[] methods) {
        for (Method method : methods) {
            if (isValidNetworkCheckHandler(method)) {
                return method;
            }
        }
        return null;
    }

    private static boolean isValidNetworkCheckHandler(Method method) {
        if (!method.isAnnotationPresent(NetworkCheckHandler.class)) {
            return false;
        }
        Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 2
                && parameterTypes[0].equals(Map.class)
                && parameterTypes[1].equals(Side.class)
                && Boolean.TYPE.equals(method.getReturnType())) {
            return true;
        }
        FMLLog.log.fatal("Found unexpected method signature for annotation NetworkCheckHandler");
        return false;
    }

    private static void configureDefaultNetworkChecker(Object holder,
                                                       ModContainer container,
                                                       String acceptableRemoteVersions) throws Exception {
        if ("*".equals(acceptableRemoteVersions)) {
            checkerField().set(holder, ignoredCheckerConstructor().newInstance(holder));
            return;
        }
        if (!Strings.isNullOrEmpty(acceptableRemoteVersions)) {
            try {
                acceptableRangeField().set(holder, VersionRange.createFromVersionSpec(acceptableRemoteVersions));
            } catch (InvalidVersionSpecificationException exception) {
                FMLLog.log.warn(
                        "Invalid bounded range {} specified for network mod id {}",
                        acceptableRemoteVersions,
                        container.getModId(),
                        exception
                );
            }
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

    private static Constructor<?> methodCheckerConstructor() throws Exception {
        Constructor<?> constructor = methodCheckerConstructor;
        if (constructor == null) {
            constructor = Class.forName(METHOD_CHECKER_CLASS).getDeclaredConstructor(Class.forName(HOLDER_CLASS));
            constructor.setAccessible(true);
            methodCheckerConstructor = constructor;
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

    private static Field checkHandlerField() throws Exception {
        Field field = checkHandlerField;
        if (field == null) {
            field = Class.forName(HOLDER_CLASS).getDeclaredField("checkHandler");
            field.setAccessible(true);
            checkHandlerField = field;
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

    private static Field eventBusIdField() throws NoSuchFieldException {
        Field field = eventBusIdField;
        if (field == null) {
            field = EventBus.class.getDeclaredField("busID");
            field.setAccessible(true);
            eventBusIdField = field;
        }
        return field;
    }

    private static Field eventBusListenersField() throws NoSuchFieldException {
        Field field = eventBusListenersField;
        if (field == null) {
            field = EventBus.class.getDeclaredField("listeners");
            field.setAccessible(true);
            eventBusListenersField = field;
        }
        return field;
    }

    private static Field eventBusListenerOwnersField() throws NoSuchFieldException {
        Field field = eventBusListenerOwnersField;
        if (field == null) {
            field = EventBus.class.getDeclaredField("listenerOwners");
            field.setAccessible(true);
            eventBusListenerOwnersField = field;
        }
        return field;
    }

    private static Method parseSimpleFieldAnnotation() throws NoSuchMethodException {
        Method method = parseSimpleFieldAnnotationMethod;
        if (method == null) {
            method = FMLModContainer.class.getDeclaredMethod(
                    "parseSimpleFieldAnnotation",
                    SetMultimap.class,
                    String.class,
                    Function.class
            );
            method.setAccessible(true);
            parseSimpleFieldAnnotationMethod = method;
        }
        return method;
    }

    private static String safeModId(ModContainer container) {
        return container == null ? "<null>" : container.getModId();
    }

    private static final class AnnotationIndex {
        private final Map<File, SetMultimap<String, ASMDataTable.ASMData>> bySource;

        private AnnotationIndex(Map<File, SetMultimap<String, ASMDataTable.ASMData>> bySource) {
            this.bySource = bySource;
        }

        private static AnnotationIndex build(ASMDataTable asmData) {
            Map<File, SetMultimap<String, ASMDataTable.ASMData>> bySource = new HashMap<>();
            addAll(bySource, Mod.class.getName(), asmData.getAll(Mod.class.getName()));
            addAll(bySource, SidedProxy.class.getName(), asmData.getAll(SidedProxy.class.getName()));
            addAll(bySource, Mod.EventBusSubscriber.class.getName(), asmData.getAll(Mod.EventBusSubscriber.class.getName()));
            addAll(bySource, Mod.Instance.class.getName(), asmData.getAll(Mod.Instance.class.getName()));
            addAll(bySource, Mod.Metadata.class.getName(), asmData.getAll(Mod.Metadata.class.getName()));
            return new AnnotationIndex(bySource);
        }

        private static void addAll(Map<File, SetMultimap<String, ASMDataTable.ASMData>> bySource,
                                   String annotationName,
                                   Set<ASMDataTable.ASMData> annotations) {
            for (ASMDataTable.ASMData data : annotations) {
                if (data.getCandidate() == null) {
                    continue;
                }
                File source = data.getCandidate().getModContainer();
                if (source == null) {
                    continue;
                }
                bySource.computeIfAbsent(source, ignored -> HashMultimap.create()).put(annotationName, data);
            }
        }

        private SetMultimap<String, ASMDataTable.ASMData> annotationsFor(ModContainer container) {
            File source = container.getSource();
            if (source == null) {
                return EMPTY_ANNOTATIONS;
            }
            SetMultimap<String, ASMDataTable.ASMData> annotations = bySource.get(source);
            return annotations == null ? EMPTY_ANNOTATIONS : annotations;
        }
    }

    private static final class SubscriberHandlerSpec {
        private final String methodName;
        private final Class<? extends Event> eventType;
        private final java.lang.reflect.Type genericType;
        private final EventPriority priority;
        private final boolean receiveCanceled;
        private final boolean contextSetterEvent;

        private SubscriberHandlerSpec(String methodName,
                                      Class<? extends Event> eventType,
                                      java.lang.reflect.Type genericType,
                                      EventPriority priority,
                                      boolean receiveCanceled) {
            this.methodName = methodName;
            this.eventType = eventType;
            this.genericType = genericType;
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.contextSetterEvent = IContextSetter.class.isAssignableFrom(eventType);
        }

        @SuppressWarnings("unchecked")
        private static SubscriberHandlerSpec fromBytecode(String subscriberClassName,
                                                          String methodName,
                                                          String descriptor,
                                                          String signature,
                                                          int access,
                                                          EventPriority priority,
                                                          boolean receiveCanceled,
                                                          ClassLoader loader) {
            if ((access & Opcodes.ACC_PUBLIC) == 0 || (access & Opcodes.ACC_STATIC) == 0) {
                return null;
            }
            if (!Type.VOID_TYPE.equals(Type.getReturnType(descriptor))) {
                return null;
            }

            Type[] arguments = Type.getArgumentTypes(descriptor);
            if (arguments.length != 1) {
                GPOM.LOGGER.warn(
                        "[ForgeConstructionAnnotationOptimizations] Skipping malformed @SubscribeEvent method {} for {}: expected one event parameter",
                        methodName + descriptor,
                        subscriberClassName
                );
                return null;
            }

            try {
                String eventClassName = arguments[0].getClassName();
                Class<? extends Event> eventType = (Class<? extends Event>) Class.forName(eventClassName, false, loader)
                        .asSubclass(Event.class);
                java.lang.reflect.Type genericType = null;
                if (IGenericEvent.class.isAssignableFrom(eventType)) {
                    genericType = genericTypeFromSignature(signature, loader);
                    if (genericType == null) {
                        return null;
                    }
                }
                return new SubscriberHandlerSpec(
                        methodName,
                        eventType,
                        genericType,
                        priority,
                        receiveCanceled
                );
            } catch (Throwable throwable) {
                GPOM.LOGGER.warn(
                        "[ForgeConstructionAnnotationOptimizations] Could not resolve @SubscribeEvent parameter for {}.{}; falling back if needed",
                        subscriberClassName,
                        methodName + descriptor,
                        throwable
                );
                return null;
            }
        }

        private String sortKey() {
            return methodName + eventType.getName() + (genericType == null ? "" : genericType.getTypeName());
        }

        private static java.lang.reflect.Type genericTypeFromSignature(String signature, ClassLoader loader) {
            if (signature == null) {
                return null;
            }

            int parametersStart = signature.indexOf('(');
            int parametersEnd = signature.indexOf(')', parametersStart + 1);
            int genericStart = signature.indexOf('<', parametersStart + 1);
            if (parametersStart < 0 || parametersEnd < 0 || genericStart < 0 || genericStart > parametersEnd) {
                return null;
            }

            int typeStart = genericStart + 1;
            if (typeStart >= signature.length()) {
                return null;
            }

            char variance = signature.charAt(typeStart);
            if (variance == '+' || variance == '-') {
                typeStart++;
            }
            if (typeStart >= signature.length() || signature.charAt(typeStart) != 'L') {
                return null;
            }

            int typeEnd = objectTypeEnd(signature, typeStart);
            if (typeEnd <= typeStart) {
                return null;
            }

            String internalName = signature.substring(typeStart + 1, typeEnd);
            int nestedGeneric = internalName.indexOf('<');
            if (nestedGeneric >= 0) {
                internalName = internalName.substring(0, nestedGeneric);
            }
            try {
                return Class.forName(internalName.replace('/', '.'), false, loader);
            } catch (ClassNotFoundException exception) {
                return null;
            }
        }

        private static int objectTypeEnd(String signature, int typeStart) {
            int depth = 0;
            for (int i = typeStart + 1; i < signature.length(); i++) {
                char current = signature.charAt(i);
                if (current == '<') {
                    depth++;
                } else if (current == '>') {
                    depth--;
                } else if (current == ';' && depth == 0) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static final class LazyStaticSubscriberListener implements IEventListener {
        private final ModContainer owner;
        private final Class<?> subscriberClass;
        private final SubscriberHandlerSpec spec;
        private static final MethodHandle NOOP_HANDLER = noopHandler();
        private volatile MethodHandle handler;

        private LazyStaticSubscriberListener(ModContainer owner,
                                             Class<?> subscriberClass,
                                             SubscriberHandlerSpec spec) {
            this.owner = owner;
            this.subscriberClass = subscriberClass;
            this.spec = spec;
        }

        @Override
        public void invoke(Event event) {
            if (event.isCancelable() && event.isCanceled() && !spec.receiveCanceled) {
                return;
            }
            if (spec.genericType != null
                    && (!(event instanceof IGenericEvent)
                    || ((IGenericEvent<?>) event).getGenericType() != spec.genericType)) {
                return;
            }

            if (spec.contextSetterEvent) {
                if (FmlParallelLoadingContext.getActiveContainer() != null) {
                    ((IContextSetter) event).setModContainer(owner);
                    invokeHandler(event);
                    return;
                }
                Loader loader = Loader.instance();
                ModContainer previous = loader.activeModContainer();
                try {
                    loader.setActiveModContainer(owner);
                    ((IContextSetter) event).setModContainer(owner);
                    invokeHandler(event);
                } finally {
                    loader.setActiveModContainer(previous);
                }
                return;
            }

            invokeHandler(event);
        }

        private void invokeHandler(Event event) {
            try {
                handler().invokeExact(event);
            } catch (RuntimeException | Error throwable) {
                throw throwable;
            } catch (Throwable throwable) {
                throw new RuntimeException(
                        "Lazy automatic subscriber failed "
                                + subscriberClass.getName()
                                + '#'
                                + spec.methodName
                                + '('
                                + spec.eventType.getName()
                                + ')',
                        throwable
                );
            }
        }

        private MethodHandle handler() {
            MethodHandle current = handler;
            if (current != null) {
                return current;
            }
            synchronized (this) {
                current = handler;
                if (current == null) {
                    current = createHandler();
                    handler = current;
                }
                return current;
            }
        }

        private MethodHandle createHandler() {
            try {
                Method method = findSubscriberMethod();
                method.setAccessible(true);
                return MethodHandles.lookup()
                        .unreflect(method)
                        .asType(MethodType.methodType(Void.TYPE, Event.class));
            } catch (Throwable throwable) {
                GPOM.LOGGER.error(
                        "[ForgeConstructionAnnotationOptimizations] Disabling broken lazy automatic subscriber {}#{}({}) for {}; handler could not be initialized",
                        subscriberClass.getName(),
                        spec.methodName,
                        spec.eventType.getName(),
                        safeModId(owner),
                        throwable
                );
                return NOOP_HANDLER;
            }
        }

        private static MethodHandle noopHandler() {
            try {
                return MethodHandles.lookup().findStatic(
                        LazyStaticSubscriberListener.class,
                        "noop",
                        MethodType.methodType(Void.TYPE, Event.class)
                );
            } catch (Throwable throwable) {
                throw new ExceptionInInitializerError(throwable);
            }
        }

        @SuppressWarnings("unused")
        private static void noop(Event event) {
        }

        private Method findSubscriberMethod() throws NoSuchMethodException {
            try {
                return subscriberClass.getDeclaredMethod(spec.methodName, spec.eventType);
            } catch (NoSuchMethodException ignored) {
            }

            Method method = findCompatibleSubscriberMethod(subscriberClass.getDeclaredMethods());
            if (method != null) {
                return method;
            }

            method = findCompatibleSubscriberMethod(subscriberClass.getMethods());
            if (method != null) {
                return method;
            }

            throw new NoSuchMethodException(subscriberClass.getName() + '#' + spec.methodName + '(' + spec.eventType.getName() + ')');
        }

        private Method findCompatibleSubscriberMethod(Method[] methods) {
            Method sameNamedFallback = null;
            Method uniqueSameNamedFallback = null;
            int sameNamedCandidates = 0;
            for (Method method : methods) {
                if (!spec.methodName.equals(method.getName()) || !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                Class<?>[] parameterTypes;
                try {
                    parameterTypes = method.getParameterTypes();
                } catch (Throwable ignored) {
                    continue;
                }

                if (parameterTypes.length != 1) {
                    continue;
                }

                sameNamedCandidates++;
                if (uniqueSameNamedFallback == null) {
                    uniqueSameNamedFallback = method;
                }

                Class<?> parameterType = parameterTypes[0];
                if (parameterType == spec.eventType
                        || parameterType.isAssignableFrom(spec.eventType)
                        || spec.eventType.isAssignableFrom(parameterType)
                        || parameterType.getName().equals(spec.eventType.getName())) {
                    return method;
                }

                if (sameNamedFallback == null && Event.class.isAssignableFrom(parameterType)) {
                    sameNamedFallback = method;
                }
            }

            if (sameNamedFallback != null) {
                return sameNamedFallback;
            }

            // Some 1.12 coremod stacks expose classloader edge cases where Event assignability
            // fails even though the bytecode annotation points at the only same-name subscriber.
            return sameNamedCandidates == 1 ? uniqueSameNamedFallback : null;
        }

        @Override
        public String toString() {
            return "LazyStaticSubscriber[mod="
                    + safeModId(owner)
                    + ", listener="
                    + subscriberClass.getName()
                    + '#'
                    + spec.methodName
                    + '('
                    + spec.eventType.getName()
                    + ")]";
        }
    }

}
