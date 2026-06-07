package com.l.gpom.optimization;

import com.l.gpom.GPOM;
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

    private EventBusRegistrationOptimizations() {
    }

    @SuppressWarnings("unchecked")
    public static boolean tryRegisterLazyStaticSubscribers(EventBus eventBus, Object target) {
        if (!(target instanceof Class)) {
            return false;
        }

        Class<?> subscriberClass = (Class<?>) target;
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

            ModContainer owner = activeOwner();
            Map<Object, ModContainer> listenerOwners =
                    (Map<Object, ModContainer>) eventBusListenerOwnersField().get(eventBus);
            listenerOwners.put(subscriberClass, owner);

            int busId = eventBusIdField().getInt(eventBus);
            ArrayList<IEventListener> registeredListeners = new ArrayList<>(scan.handlers.size());
            for (SubscriberHandlerSpec spec : scan.handlers) {
                ListenerList listenerList = listenerListFor(spec);
                if (listenerList == null) {
                    return false;
                }

                IEventListener listener = new LazyStaticEventBusListener(owner, subscriberClass, spec);
                listenerList.register(busId, spec.priority, listener);
                registeredListeners.add(listener);
            }

            listeners.put(subscriberClass, registeredListeners);
            return true;
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
                    throw new RuntimeException(
                            "Unable to initialize lazy static EventBus subscriber "
                                    + subscriberClass.getName()
                                    + '#'
                                    + spec.methodName
                                    + '('
                                    + spec.eventType.getName()
                                    + ')',
                            reflectiveFailure
                    );
                }
            }
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
