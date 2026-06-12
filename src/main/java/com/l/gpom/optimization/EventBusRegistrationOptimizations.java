package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomSide;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.IContextSetter;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.IGenericEvent;
import net.minecraftforge.fml.common.eventhandler.ListenerList;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.apache.logging.log4j.ThreadContext;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

public final class EventBusRegistrationOptimizations {
    private static final String SUBSCRIBE_EVENT_DESCRIPTOR = Type.getDescriptor(SubscribeEvent.class);
    private static final boolean GET_LOG_CONTEXT = Boolean.parseBoolean(System.getProperty("fml.LogContext", "false"));

    private static final ClassValue<Method[]> PUBLIC_DECLARED_METHODS = new ClassValue<Method[]>() {
        @Override
        protected Method[] computeValue(Class<?> type) {
            Method[] declaredMethods = type.getDeclaredMethods();
            List<Method> publicMethods = new ArrayList<>(declaredMethods.length);
            for (Method method : declaredMethods) {
                if (Modifier.isPublic(method.getModifiers())) {
                    publicMethods.add(method);
                }
            }
            return publicMethods.toArray(new Method[publicMethods.size()]);
        }
    };

    private static final ClassValue<SubscriberScan> STATIC_SUBSCRIBERS = new ClassValue<SubscriberScan>() {
        @Override
        protected SubscriberScan computeValue(Class<?> type) {
            return scanStaticSubscribers(type);
        }
    };

    private static volatile Field eventBusIdField;
    private static volatile Field eventBusListenersField;
    private static volatile Field eventBusListenerOwnersField;
    private static volatile Field listenerListInstancesField;
    private static volatile Field listenerListInstListenersField;
    private static volatile Field listenerListInstPrioritiesField;
    private static volatile Field listenerListInstRebuildField;
    private static volatile boolean listenerCacheSanitizerWarningLogged;

    private EventBusRegistrationOptimizations() {
    }

    public static boolean tryReplaceFragileInstanceRegistration(EventBus eventBus, Object target) {
        if (target == null) {
            return false;
        }

        String targetClass = target.getClass().getName();
        if (!GpomEarlyConfig.baublesSideSlotsEnabled() || !GpomSide.isClientLaunch()) {
            return false;
        }

        if ("baubles.client.ClientEventHandler".equals(targetClass)) {
            registerReplacement(
                    eventBus,
                    "com.l.gpom.compat.baubles.BaublesSideSlotsClientEvents",
                    "[GPOM Baubles] Replaced Baubles ClientEventHandler with vanilla-inventory side-slot handler",
                    "[GPOM Baubles] Could not register vanilla-inventory side-slot client handler; dropping Baubles ClientEventHandler to avoid expanded inventory screen"
            );
            return true;
        }

        if (!"baubles.client.gui.GuiEvents".equals(targetClass)) {
            return false;
        }
        registerReplacement(
                eventBus,
                "com.l.gpom.compat.baubles.BaublesSideSlotsGuiEvents",
                "[GPOM Baubles] Replaced Baubles GuiEvents with vanilla-inventory side-slot GUI handler",
                "[GPOM Baubles] Could not register vanilla-inventory side-slot GUI handler; dropping Baubles GuiEvents to avoid expanded inventory screen"
        );
        return true;
    }

    private static void registerReplacement(EventBus eventBus, String className, String successMessage, String failureMessage) {
        try {
            Class<?> replacementClass = Class.forName(
                    className,
                    true,
                    EventBusRegistrationOptimizations.class.getClassLoader()
            );
            Object replacement = replacementClass.getConstructor().newInstance();
            eventBus.register(replacement);
            GPOM.LOGGER.info(successMessage);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn(failureMessage, throwable);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean tryRegisterLazyStaticSubscribers(EventBus eventBus, Object target) {
        if (!(target instanceof Class)) {
            return false;
        }

        Class<?> subscriberClass = (Class<?>) target;
        ModContainer owner = activeOwner();
        if (isLazyStaticSubscriberDenied(subscriberClass, owner)) {
            return false;
        }

        SubscriberScan scan = STATIC_SUBSCRIBERS.get(subscriberClass);
        if (!scan.supported || scan.handlers.isEmpty()) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            Map<Object, ArrayList<IEventListener>> listeners =
                    (Map<Object, ArrayList<IEventListener>>) eventBusListenersField().get(eventBus);
            if (listeners.containsKey(subscriberClass)) {
                return true;
            }

            List<ListenerRegistration> registrations = new ArrayList<>(scan.handlers.size());
            for (SubscriberHandlerSpec spec : scan.handlers) {
                ListenerList listenerList = listenerListFor(spec);
                if (listenerList == null) {
                    return false;
                }
                registrations.add(new ListenerRegistration(spec, listenerList));
            }

            Map<Object, ModContainer> listenerOwners =
                    (Map<Object, ModContainer>) eventBusListenerOwnersField().get(eventBus);
            int busId = eventBusIdField().getInt(eventBus);

            synchronized (ListenerList.class) {
                if (listeners.containsKey(subscriberClass)) {
                    return true;
                }

                ArrayList<IEventListener> registeredListeners = new ArrayList<>(registrations.size());
                listenerOwners.put(subscriberClass, owner);
                try {
                    for (ListenerRegistration registration : registrations) {
                        IEventListener listener = new LazyStaticEventBusListener(owner, subscriberClass, registration.spec);
                        registration.listenerList.register(busId, registration.spec.priority, listener);
                        registeredListeners.add(listener);
                    }

                    for (ListenerRegistration registration : registrations) {
                        sanitizeCachedListenersLocked(
                                registration.listenerList,
                                busId,
                                "lazy EventBus registration " + subscriberClass.getName()
                        );
                    }

                    listeners.put(subscriberClass, registeredListeners);
                    return true;
                } catch (Throwable throwable) {
                    for (IEventListener listener : registeredListeners) {
                        ListenerList.unregisterAll(busId, listener);
                    }
                    listenerOwners.remove(subscriberClass);
                    throw throwable;
                }
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn(
                    "[EventBusRegistrationOptimizations] Lazy static EventBus registration failed for {}; falling back to Forge",
                    subscriberClass.getName(),
                    throwable
            );
            return false;
        } finally {
            StartupProfiler.endProbe(
                    "FML EventBus lazyStaticRegister "
                            + subscriberClass.getName()
                            + " handlers="
                            + scan.handlers.size(),
                    startedAt
            );
        }
    }

    public static Method[] methodsForRegistration(Class<?> targetClass, Object target) {
        if (target instanceof Class) {
            return PUBLIC_DECLARED_METHODS.get(targetClass);
        }
        return targetClass.getMethods();
    }

    private static SubscriberScan scanStaticSubscribers(Class<?> subscriberClass) {
        String className = subscriberClass.getName();
        String resourceName = className.replace('.', '/') + ".class";
        ClassLoader loader = subscriberClass.getClassLoader();
        InputStream input = loader == null
                ? ClassLoader.getSystemResourceAsStream(resourceName)
                : loader.getResourceAsStream(resourceName);
        if (input == null) {
            return SubscriberScan.unsupported();
        }

        try {
            try {
                StaticSubscriberVisitor visitor = new StaticSubscriberVisitor(className, loader);
                new ClassReader(input).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return visitor.toScan();
            } finally {
                input.close();
            }
        } catch (IOException | RuntimeException exception) {
            GPOM.LOGGER.warn(
                    "[EventBusRegistrationOptimizations] Could not scan @SubscribeEvent bytecode for {}; falling back to Forge",
                    className,
                    exception
            );
            return SubscriberScan.unsupported();
        }
    }

    private static ListenerList listenerListFor(SubscriberHandlerSpec spec) {
        try {
            Event event = dummyEventFor(spec);
            return event == null ? null : event.getListenerList();
        } catch (Throwable throwable) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Event dummyEventFor(SubscriberHandlerSpec spec) throws Exception {
        if (RegistryEvent.Register.class.equals(spec.eventType)) {
            if (!(spec.genericType instanceof Class)) {
                return null;
            }
            Class<?> genericClass = (Class<?>) spec.genericType;
            Class<? extends IForgeRegistryEntry> entryType =
                    genericClass.asSubclass(IForgeRegistryEntry.class);
            IForgeRegistry registry = GameRegistry.findRegistry(entryType);
            if (registry == null) {
                return null;
            }
            return new RegistryEvent.Register(new ResourceLocation("gpom", "listener_probe"), registry);
        }

        return spec.eventType.getConstructor().newInstance();
    }

    public static void sanitizePostedEventListeners(EventBus eventBus, Event event) {
        if (eventBus == null || event == null || !shouldSanitizePostedEventListeners()) {
            return;
        }
        try {
            int busId = eventBusIdField().getInt(eventBus);
            synchronized (ListenerList.class) {
                sanitizeCachedListenersLocked(
                        event.getListenerList(),
                        busId,
                        "worker EventBus.post " + event.getClass().getName()
                );
            }
        } catch (Throwable throwable) {
            warnListenerCacheSanitizerOnce(
                    "[EventBusRegistrationOptimizations] Could not inspect Forge listener cache before worker EventBus.post",
                    throwable
            );
        }
    }

    static void sanitizeCachedListeners(ListenerList listenerList, int busId, String context) {
        if (listenerList == null) {
            return;
        }
        synchronized (ListenerList.class) {
            sanitizeCachedListenersLocked(listenerList, busId, context);
        }
    }

    @SuppressWarnings("unchecked")
    private static void sanitizeCachedListenersLocked(ListenerList listenerList, int busId, String context) {
        IEventListener[] cached = listenerList.getListeners(busId);
        int nullCount = countNullListeners(cached);
        if (nullCount == 0) {
            return;
        }

        try {
            Object instance = listenerListInstance(listenerList, busId);
            int removedFromPriorities = removeNullPriorityListeners(instance);
            listenerListInstRebuildField().setBoolean(instance, true);
            IEventListener[] rebuilt = listenerList.getListeners(busId);
            int remainingNulls = countNullListeners(rebuilt);
            if (remainingNulls > 0) {
                listenerListInstListenersField().set(instance, compactListeners(rebuilt));
            }
            GPOM.LOGGER.warn(
                    "[EventBusRegistrationOptimizations] Removed {} null cached Forge listener(s) during {}; priorityNulls={}",
                    nullCount,
                    context,
                    removedFromPriorities
            );
        } catch (Throwable throwable) {
            warnListenerCacheSanitizerOnce(
                    "[EventBusRegistrationOptimizations] Could not compact Forge listener cache after null listener detection",
                    throwable
            );
        }
    }

    private static boolean shouldSanitizePostedEventListeners() {
        String threadName = Thread.currentThread().getName();
        return FmlParallelLoadingContext.getActiveContainer() != null
                || StartupProfiler.isPostPreInitTransitionActive()
                || (threadName != null
                && (threadName.startsWith("ForkJoinPool.")
                || threadName.startsWith("GPOM-FML-")));
    }

    private static int countNullListeners(IEventListener[] listeners) {
        int count = 0;
        if (listeners == null) {
            return 0;
        }
        for (IEventListener listener : listeners) {
            if (listener == null) {
                count++;
            }
        }
        return count;
    }

    private static IEventListener[] compactListeners(IEventListener[] listeners) {
        if (listeners == null || listeners.length == 0) {
            return new IEventListener[0];
        }
        int size = 0;
        for (IEventListener listener : listeners) {
            if (listener != null) {
                size++;
            }
        }
        IEventListener[] compacted = new IEventListener[size];
        int index = 0;
        for (IEventListener listener : listeners) {
            if (listener != null) {
                compacted[index++] = listener;
            }
        }
        return compacted;
    }

    private static Object listenerListInstance(ListenerList listenerList, int busId) throws IllegalAccessException, NoSuchFieldException {
        Object[] instances = (Object[]) listenerListInstancesField().get(listenerList);
        if (busId < 0 || busId >= instances.length || instances[busId] == null) {
            throw new IllegalStateException("No ListenerList instance for bus " + busId);
        }
        return instances[busId];
    }

    @SuppressWarnings("unchecked")
    private static int removeNullPriorityListeners(Object listenerListInstance) throws IllegalAccessException, NoSuchFieldException {
        Object[] priorities = (Object[]) listenerListInstPrioritiesField().get(listenerListInstance);
        int removed = 0;
        for (Object priority : priorities) {
            if (!(priority instanceof ArrayList)) {
                continue;
            }
            ArrayList<IEventListener> listeners = (ArrayList<IEventListener>) priority;
            while (listeners.remove(null)) {
                removed++;
            }
        }
        return removed;
    }

    private static void warnListenerCacheSanitizerOnce(String message, Throwable throwable) {
        if (listenerCacheSanitizerWarningLogged) {
            return;
        }
        listenerCacheSanitizerWarningLogged = true;
        GPOM.LOGGER.warn(message, throwable);
    }

    private static boolean isLazyStaticSubscriberDenied(Class<?> subscriberClass, ModContainer owner) {
        Set<String> denylist = GpomEarlyConfig.constructionGenericAutomaticSubscribersDenylist();
        if (denylist.isEmpty()) {
            return false;
        }
        if (owner != null && denylist.contains(safeModId(owner).toLowerCase(Locale.ROOT))) {
            return true;
        }
        String inferredModId = inferModId(subscriberClass.getName());
        return inferredModId != null && denylist.contains(inferredModId);
    }

    private static String inferModId(String className) {
        if (className == null) {
            return null;
        }
        if (className.startsWith("team.chisel.ctm.")) {
            return "ctm";
        }
        if (className.startsWith("team.chisel.")) {
            return "chisel";
        }
        if (className.startsWith("pl.asie.ucw.")) {
            return "unlimitedchiselworks";
        }
        if (className.startsWith("thebetweenlands.")) {
            return "thebetweenlands";
        }
        if (className.startsWith("twilightforest.")) {
            return "twilightforest";
        }
        if (className.startsWith("erebus.")) {
            return "erebus";
        }
        if (className.startsWith("landmaster.plustic.")) {
            return "plustic";
        }
        if (className.startsWith("thecodex6824.thaumcraftfix.")) {
            return "thaumcraftfix";
        }
        return null;
    }

    private static ModContainer activeOwner() {
        Loader loader = Loader.instance();
        ModContainer owner = loader.activeModContainer();
        return owner == null ? loader.getMinecraftModContainer() : owner;
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

    private static Field listenerListInstancesField() throws NoSuchFieldException {
        Field field = listenerListInstancesField;
        if (field == null) {
            field = ListenerList.class.getDeclaredField("lists");
            field.setAccessible(true);
            listenerListInstancesField = field;
        }
        return field;
    }

    private static Field listenerListInstListenersField() throws NoSuchFieldException {
        Field field = listenerListInstListenersField;
        if (field == null) {
            field = listenerListInstField("listeners");
            listenerListInstListenersField = field;
        }
        return field;
    }

    private static Field listenerListInstPrioritiesField() throws NoSuchFieldException {
        Field field = listenerListInstPrioritiesField;
        if (field == null) {
            field = listenerListInstField("priorities");
            listenerListInstPrioritiesField = field;
        }
        return field;
    }

    private static Field listenerListInstRebuildField() throws NoSuchFieldException {
        Field field = listenerListInstRebuildField;
        if (field == null) {
            field = listenerListInstField("rebuild");
            listenerListInstRebuildField = field;
        }
        return field;
    }

    private static Field listenerListInstField(String fieldName) throws NoSuchFieldException {
        Class<?>[] declaredClasses = ListenerList.class.getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses) {
            if ("ListenerListInst".equals(declaredClass.getSimpleName())) {
                Field field = declaredClass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            }
        }
        throw new NoSuchFieldException("ListenerListInst." + fieldName);
    }

    private static String safeModId(ModContainer owner) {
        return owner == null ? "<null>" : owner.getModId();
    }

    private static final class StaticSubscriberVisitor extends ClassVisitor {
        private final String className;
        private final ClassLoader loader;
        private final List<SubscriberHandlerSpec> handlers = new ArrayList<>();
        private boolean supported = true;

        private StaticSubscriberVisitor(String className, ClassLoader loader) {
            super(Opcodes.ASM9);
            this.className = className;
            this.loader = loader;
        }

        @Override
        public MethodVisitor visitMethod(int access,
                                         String name,
                                         String descriptor,
                                         String signature,
                                         String[] exceptions) {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public AnnotationVisitor visitAnnotation(String annotationDescriptor, boolean visible) {
                    if (!SUBSCRIBE_EVENT_DESCRIPTOR.equals(annotationDescriptor)) {
                        return null;
                    }
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        private EventPriority priority = EventPriority.NORMAL;
                        private boolean receiveCanceled;

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

                        @Override
                        public void visitEnd() {
                            SubscriberHandlerSpec spec = SubscriberHandlerSpec.fromBytecode(
                                    className,
                                    name,
                                    descriptor,
                                    signature,
                                    access,
                                    priority,
                                    receiveCanceled,
                                    loader
                            );
                            if (spec == null) {
                                supported = false;
                            } else {
                                handlers.add(spec);
                            }
                        }
                    };
                }
            };
        }

        private SubscriberScan toScan() {
            if (!supported) {
                return SubscriberScan.unsupported();
            }
            if (handlers.isEmpty()) {
                return SubscriberScan.empty();
            }
            Collections.sort(handlers, Comparator.comparing(SubscriberHandlerSpec::sortKey));
            return new SubscriberScan(true, handlers);
        }
    }

    private static final class SubscriberScan {
        private static final SubscriberScan EMPTY = new SubscriberScan(true, Collections.emptyList());
        private static final SubscriberScan UNSUPPORTED = new SubscriberScan(false, Collections.emptyList());

        private final boolean supported;
        private final List<SubscriberHandlerSpec> handlers;

        private SubscriberScan(boolean supported, List<SubscriberHandlerSpec> handlers) {
            this.supported = supported;
            this.handlers = handlers;
        }

        private static SubscriberScan empty() {
            return EMPTY;
        }

        private static SubscriberScan unsupported() {
            return UNSUPPORTED;
        }
    }

    private static final class ListenerRegistration {
        private final SubscriberHandlerSpec spec;
        private final ListenerList listenerList;

        private ListenerRegistration(SubscriberHandlerSpec spec, ListenerList listenerList) {
            this.spec = spec;
            this.listenerList = listenerList;
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
                return null;
            }

            try {
                Class<? extends Event> eventType = (Class<? extends Event>) Class
                        .forName(arguments[0].getClassName(), false, loader)
                        .asSubclass(Event.class);
                java.lang.reflect.Type genericType = null;
                if (IGenericEvent.class.isAssignableFrom(eventType)) {
                    genericType = genericTypeFromSignature(signature, loader);
                    if (genericType == null) {
                        return null;
                    }
                }
                return new SubscriberHandlerSpec(methodName, eventType, genericType, priority, receiveCanceled);
            } catch (Throwable throwable) {
                GPOM.LOGGER.warn(
                        "[EventBusRegistrationOptimizations] Could not resolve @SubscribeEvent parameter for {}.{}; falling back to Forge",
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

    private static final class LazyStaticEventBusListener implements IEventListener {
        private final ModContainer owner;
        private final Class<?> subscriberClass;
        private final SubscriberHandlerSpec spec;
        private static final MethodHandle NOOP_HANDLER = noopHandler();
        private volatile MethodHandle handler;

        private LazyStaticEventBusListener(ModContainer owner,
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

            if (GET_LOG_CONTEXT) {
                ThreadContext.put("mod", owner == null ? "" : owner.getName());
            }
            try {
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
            } finally {
                if (GET_LOG_CONTEXT) {
                    ThreadContext.remove("mod");
                }
            }
        }

        private void invokeHandler(Event event) {
            try {
                handler().invokeExact(event);
            } catch (RuntimeException | Error throwable) {
                throw throwable;
            } catch (Throwable throwable) {
                throw new RuntimeException(
                        "Lazy static EventBus subscriber failed "
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
                return MethodHandles.publicLookup()
                        .findStatic(
                                subscriberClass,
                                spec.methodName,
                                MethodType.methodType(Void.TYPE, spec.eventType)
                        )
                        .asType(MethodType.methodType(Void.TYPE, Event.class));
            } catch (Throwable publicLookupFailure) {
                try {
                    Method method = subscriberClass.getDeclaredMethod(spec.methodName, spec.eventType);
                    method.setAccessible(true);
                    return MethodHandles.lookup()
                            .unreflect(method)
                            .asType(MethodType.methodType(Void.TYPE, Event.class));
                } catch (Throwable reflectiveFailure) {
                    GPOM.LOGGER.error(
                            "[EventBusRegistrationOptimizations] Disabling broken lazy static EventBus subscriber {}#{}({}) for {}; handler could not be initialized",
                            subscriberClass.getName(),
                            spec.methodName,
                            spec.eventType.getName(),
                            safeModId(owner),
                            reflectiveFailure
                    );
                    return NOOP_HANDLER;
                }
            }
        }

        private static MethodHandle noopHandler() {
            try {
                return MethodHandles.lookup().findStatic(
                        LazyStaticEventBusListener.class,
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

        @Override
        public String toString() {
            return "LazyStaticEventBusSubscriber[mod="
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
