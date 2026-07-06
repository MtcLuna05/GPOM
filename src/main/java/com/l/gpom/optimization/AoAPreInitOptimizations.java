package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.client.ClientNullPlayerStateGuard;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import com.l.gpom.util.ReflectionFields;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AoAPreInitOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.aoa3FastClientEventRegistration", "true"));
    private static final boolean LAZY_CLIENT_EVENT_HANDLERS = Boolean.parseBoolean(System.getProperty("gpom.aoa3LazyClientEventHandlers", "true"));
    private static final Method EVENT_BUS_REGISTER_METHOD = findEventBusRegisterMethod();
    private static final ConcurrentHashMap<String, Boolean> FALLBACK_LOGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Object> LAZY_TARGETS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> LAZY_METHODS = new ConcurrentHashMap<>();

    private static final HandlerSpec[] CLIENT_HANDLERS = {
            handler("net.tslat.aoa3.client.event.KeyBinder", "onKeyDown", "net.minecraftforge.fml.common.gameevent.InputEvent$KeyInputEvent"),
            handler("net.tslat.aoa3.client.gui.render.EntityPropertiesRenderer", "renderIcons", "net.minecraftforge.client.event.RenderLivingEvent$Specials$Pre"),
            handler("net.tslat.aoa3.client.gui.render.SniperGuiRenderer", "fovEvent", "net.minecraftforge.client.event.FOVUpdateEvent"),
            handler("net.tslat.aoa3.client.gui.render.SniperGuiRenderer", "renderScopeScreenPre", "net.minecraftforge.client.event.RenderGameOverlayEvent$Pre"),
            handler("net.tslat.aoa3.client.gui.render.SniperGuiRenderer", "renderScopeScreen", "net.minecraftforge.client.event.RenderGameOverlayEvent$Post"),
            handler("net.tslat.aoa3.client.gui.render.SniperGuiRenderer", "renderHand", "net.minecraftforge.client.event.RenderSpecificHandEvent"),
            handler("net.tslat.aoa3.client.gui.render.HelmetScreenRenderer", "renderHelmetScreen", "net.minecraftforge.client.event.RenderGameOverlayEvent$Post"),
            handler("net.tslat.aoa3.client.gui.render.ScreenOverlayRenderer", "renderOverlay", "net.minecraftforge.client.event.RenderGameOverlayEvent$Post"),
            handler("net.tslat.aoa3.client.gui.render.ResourcesRenderer", "onRenderTick", "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent"),
            handler("net.tslat.aoa3.client.gui.render.SkillsRenderer", "onRenderTick", "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent"),
            handler("net.tslat.aoa3.client.gui.render.XpParticlesRenderer", "onRenderTick", "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent"),
            handler("net.tslat.aoa3.client.gui.render.BossBarRenderer", "onRender", "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent"),
            handler("net.tslat.aoa3.client.event.ClientEventHandler", "clientTick", "net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent"),
            handler("net.tslat.aoa3.client.event.ClientEventHandler", "configChanged", "net.minecraftforge.fml.client.event.ConfigChangedEvent$OnConfigChangedEvent"),
            handler("net.tslat.aoa3.client.event.ClientEventHandler", "onLongReachSwing", "net.minecraftforge.client.event.MouseEvent"),
            handler("net.tslat.aoa3.client.event.ClientEventHandler", "onPlayerJoin", "net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedInEvent"),
            handler("net.tslat.aoa3.client.event.ClientEventHandler", "onPlayerDeath", "net.minecraftforge.event.entity.living.LivingDeathEvent"),
            handler("net.tslat.aoa3.client.event.ClientEventHandler", "onMusicPlay", "net.minecraftforge.client.event.sound.PlaySoundEvent"),
            handler("net.tslat.aoa3.client.render.entities.projectiles.ProjectileRenders", "registerEntityRenders", "net.minecraftforge.client.event.ModelRegistryEvent"),
            handler("net.tslat.aoa3.common.registration.ParticleRegister", "stitchEvent", "net.minecraftforge.client.event.TextureStitchEvent$Pre")
    };

    private AoAPreInitOptimizations() {
    }

    public static void registerClientEventsFastOrFallback() {
        if (!tryRegisterClientEventsLazy() && !tryRegisterClientEventsFast()) {
            registerClientEventsFallback();
        }
    }

    private static boolean tryRegisterClientEventsLazy() {
        if (!ENABLED || !LAZY_CLIENT_EVENT_HANDLERS || EVENT_BUS_REGISTER_METHOD == null
                || !TargetedModVersions.isAdventOfAscensionClass("net.tslat.aoa3.common.ClientProxy")) {
            return false;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            EventBus eventBus = MinecraftForge.EVENT_BUS;
            ModContainer owner = Loader.instance().activeModContainer();
            ClassLoader classLoader = Loader.instance().getModClassLoader();
            Map<Object, ModContainer> listenerOwners = listenerOwners(eventBus);

            for (HandlerSpec spec : CLIENT_HANDLERS) {
                Class<? extends Event> eventType = Class.forName(spec.eventClassName, false, classLoader).asSubclass(Event.class);
                LazyEventForwarder forwarder = new LazyEventForwarder(spec.className, spec.methodName, spec.eventClassName);
                if (listenerOwners != null) {
                    listenerOwners.put(forwarder, owner);
                }
                Method method = LazyEventForwarder.class.getDeclaredMethod(forwarderMethodName(spec.eventClassName), eventType);
                EVENT_BUS_REGISTER_METHOD.invoke(eventBus, eventType, forwarder, method, owner);
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                 InvocationTargetException | LinkageError | RuntimeException e) {
            logFallbackOnce("aoa-client-events-lazy", "AoA3 lazy client event registration failed; falling back to eager exact registration", e);
            return false;
        } finally {
            StartupProfiler.endProbeAlways("AOA ClientProxy.registerClientEvents lazy manifest", startedAt);
        }
    }

    private static boolean tryRegisterClientEventsFast() {
        if (!ENABLED || EVENT_BUS_REGISTER_METHOD == null
                || !TargetedModVersions.isAdventOfAscensionClass("net.tslat.aoa3.common.ClientProxy")) {
            return false;
        }

        try {
            EventBus eventBus = MinecraftForge.EVENT_BUS;
            ModContainer owner = Loader.instance().activeModContainer();
            ClassLoader classLoader = Loader.instance().getModClassLoader();
            Map<Object, ModContainer> listenerOwners = listenerOwners(eventBus);
            Map<Object, ?> listeners = listeners(eventBus);

            String currentClassName = null;
            Object currentTarget = null;
            for (HandlerSpec spec : CLIENT_HANDLERS) {
                if (!spec.className.equals(currentClassName)) {
                    currentClassName = spec.className;
                    currentTarget = instantiate(classLoader, spec.className);
                    if (listenerOwners != null) {
                        listenerOwners.put(currentTarget, owner);
                    }
                    if (listeners != null && listeners.containsKey(currentTarget)) {
                        continue;
                    }
                }
                Class<? extends Event> eventType = Class.forName(spec.eventClassName, false, classLoader).asSubclass(Event.class);
                Method method = currentTarget.getClass().getDeclaredMethod(spec.methodName, eventType);
                if (method.getAnnotation(SubscribeEvent.class) == null) {
                    logFallbackOnce(spec.className + '#' + spec.methodName, "AoA3 client event fast path found handler without @SubscribeEvent: " + spec.className + '#' + spec.methodName);
                    return false;
                }
                EVENT_BUS_REGISTER_METHOD.invoke(eventBus, eventType, currentTarget, method, owner);
            }
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException | LinkageError | RuntimeException e) {
            logFallbackOnce("aoa-client-events", "AoA3 client event fast registration failed; falling back to Forge EventBus.register", e);
            return false;
        }
    }

    private static Object instantiate(ClassLoader classLoader, String className)
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Class<?> type = Class.forName(className, true, classLoader);
        if (!TargetedModVersions.isAdventOfAscensionClass(type)) {
            throw new ClassNotFoundException("Unsupported AoA3 class source: " + className);
        }
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object lazyTarget(String className) {
        Object existing = LAZY_TARGETS.get(className);
        if (existing != null) {
            return existing;
        }

        try {
            ClassLoader classLoader = Loader.instance().getModClassLoader();
            Object created = instantiate(classLoader, className);
            Object previous = LAZY_TARGETS.putIfAbsent(className, created);
            return previous != null ? previous : created;
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException |
                 InstantiationException | IllegalAccessException | LinkageError | RuntimeException e) {
            throw new RuntimeException("Unable to lazily create AoA3 client event handler " + className, e);
        }
    }

    private static Method lazyMethod(Object target, String className, String methodName, String eventClassName) {
        String key = className + '#' + methodName + '(' + eventClassName + ')';
        Method existing = LAZY_METHODS.get(key);
        if (existing != null) {
            return existing;
        }

        try {
            ClassLoader classLoader = Loader.instance().getModClassLoader();
            Class<? extends Event> eventType = Class.forName(eventClassName, false, classLoader).asSubclass(Event.class);
            Method method = target.getClass().getDeclaredMethod(methodName, eventType);
            method.setAccessible(true);
            Method previous = LAZY_METHODS.putIfAbsent(key, method);
            return previous != null ? previous : method;
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError | RuntimeException e) {
            throw new RuntimeException("Unable to find AoA3 client event handler method " + key, e);
        }
    }

    private static void dispatchLazy(String className, String methodName, String eventClassName, Event event) {
        if (shouldSkipWithoutClientPlayer(eventClassName)) {
            return;
        }
        Object target = lazyTarget(className);
        Method method = lazyMethod(target, className, methodName, eventClassName);
        try {
            method.invoke(target, event);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Unable to invoke AoA3 client event handler " + className + '#' + methodName, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (shouldSuppressMissingClientPlayerCrash(className, eventClassName, cause)) {
                return;
            }
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new RuntimeException("AoA3 client event handler failed " + className + '#' + methodName, cause);
        }
    }

    private static boolean shouldSkipWithoutClientPlayer(String eventClassName) {
        if (!isClientPlayerDependentEvent(eventClassName)) {
            return false;
        }
        return ClientNullPlayerStateGuard.hasNoCurrentClientWorldOrPlayer();
    }

    private static boolean shouldSuppressMissingClientPlayerCrash(String className, String eventClassName, Throwable cause) {
        if (!(cause instanceof NullPointerException)
                || !isClientPlayerDependentEvent(eventClassName)
                || !className.startsWith("net.tslat.aoa3.client.gui.render.")) {
            return false;
        }
        String message = cause.getMessage();
        return message != null
                && (message.contains("field_71439_g") || message.contains(".player") || message.contains(" player"));
    }

    private static boolean isClientPlayerDependentEvent(String eventClassName) {
        return "net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent".equals(eventClassName)
                || "net.minecraftforge.client.event.RenderGameOverlayEvent$Pre".equals(eventClassName)
                || "net.minecraftforge.client.event.RenderGameOverlayEvent$Post".equals(eventClassName)
                || "net.minecraftforge.client.event.RenderSpecificHandEvent".equals(eventClassName)
                || "net.minecraftforge.client.event.FOVUpdateEvent".equals(eventClassName);
    }

    private static void registerClientEventsFallback() {
        EventBus bus = MinecraftForge.EVENT_BUS;
        bus.register(new net.tslat.aoa3.client.event.KeyBinder());
        bus.register(new net.tslat.aoa3.client.gui.render.EntityPropertiesRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.SniperGuiRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.HelmetScreenRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.ScreenOverlayRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.ResourcesRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.SkillsRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.XpParticlesRenderer());
        bus.register(new net.tslat.aoa3.client.gui.render.BossBarRenderer());
        bus.register(new net.tslat.aoa3.client.event.ClientEventHandler());
        bus.register(new net.tslat.aoa3.client.render.entities.projectiles.ProjectileRenders());
        bus.register(new net.tslat.aoa3.common.registration.ParticleRegister());
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, ModContainer> listenerOwners(EventBus eventBus) {
        return (Map<Object, ModContainer>) ReflectionFields.get(eventBus, "listenerOwners", "listenerOwners");
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, ?> listeners(EventBus eventBus) {
        return (Map<Object, ?>) ReflectionFields.get(eventBus, "listeners", "listeners");
    }

    private static Method findEventBusRegisterMethod() {
        try {
            Method method = EventBus.class.getDeclaredMethod("register", Class.class, Object.class, Method.class, ModContainer.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static HandlerSpec handler(String className, String methodName, String eventClassName) {
        return new HandlerSpec(className, methodName, eventClassName);
    }

    private static String forwarderMethodName(String eventClassName) {
        if ("net.minecraftforge.fml.common.gameevent.InputEvent$KeyInputEvent".equals(eventClassName)) {
            return "onKeyInput";
        }
        if ("net.minecraftforge.client.event.RenderLivingEvent$Specials$Pre".equals(eventClassName)) {
            return "onRenderLivingSpecialsPre";
        }
        if ("net.minecraftforge.client.event.FOVUpdateEvent".equals(eventClassName)) {
            return "onFovUpdate";
        }
        if ("net.minecraftforge.client.event.RenderGameOverlayEvent$Pre".equals(eventClassName)) {
            return "onRenderGameOverlayPre";
        }
        if ("net.minecraftforge.client.event.RenderGameOverlayEvent$Post".equals(eventClassName)) {
            return "onRenderGameOverlayPost";
        }
        if ("net.minecraftforge.client.event.RenderSpecificHandEvent".equals(eventClassName)) {
            return "onRenderSpecificHand";
        }
        if ("net.minecraftforge.fml.common.gameevent.TickEvent$RenderTickEvent".equals(eventClassName)) {
            return "onRenderTick";
        }
        if ("net.minecraftforge.fml.common.gameevent.TickEvent$ClientTickEvent".equals(eventClassName)) {
            return "onClientTick";
        }
        if ("net.minecraftforge.fml.client.event.ConfigChangedEvent$OnConfigChangedEvent".equals(eventClassName)) {
            return "onConfigChanged";
        }
        if ("net.minecraftforge.client.event.MouseEvent".equals(eventClassName)) {
            return "onMouse";
        }
        if ("net.minecraftforge.fml.common.gameevent.PlayerEvent$PlayerLoggedInEvent".equals(eventClassName)) {
            return "onPlayerLoggedIn";
        }
        if ("net.minecraftforge.event.entity.living.LivingDeathEvent".equals(eventClassName)) {
            return "onLivingDeath";
        }
        if ("net.minecraftforge.client.event.sound.PlaySoundEvent".equals(eventClassName)) {
            return "onPlaySound";
        }
        if ("net.minecraftforge.client.event.ModelRegistryEvent".equals(eventClassName)) {
            return "onModelRegistry";
        }
        if ("net.minecraftforge.client.event.TextureStitchEvent$Pre".equals(eventClassName)) {
            return "onTextureStitchPre";
        }
        throw new IllegalArgumentException("Unsupported AoA3 lazy client event type: " + eventClassName);
    }

    private static void logFallbackOnce(String key, String message) {
        if (FALLBACK_LOGS.putIfAbsent(key, Boolean.TRUE) == null) {
            GPOM.LOGGER.warn(message);
        }
    }

    private static void logFallbackOnce(String key, String message, Throwable throwable) {
        if (FALLBACK_LOGS.putIfAbsent(key, Boolean.TRUE) == null) {
            GPOM.LOGGER.warn(message, throwable);
        }
    }

    private static final class HandlerSpec {
        private final String className;
        private final String methodName;
        private final String eventClassName;

        private HandlerSpec(String className, String methodName, String eventClassName) {
            this.className = className;
            this.methodName = methodName;
            this.eventClassName = eventClassName;
        }
    }

    public static final class LazyEventForwarder {
        private final String className;
        private final String methodName;
        private final String eventClassName;

        private LazyEventForwarder(String className, String methodName, String eventClassName) {
            this.className = className;
            this.methodName = methodName;
            this.eventClassName = eventClassName;
        }

        @SubscribeEvent
        public void onKeyInput(InputEvent.KeyInputEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onRenderLivingSpecialsPre(RenderLivingEvent.Specials.Pre event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onFovUpdate(FOVUpdateEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onRenderGameOverlayPre(RenderGameOverlayEvent.Pre event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onRenderGameOverlayPost(RenderGameOverlayEvent.Post event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onRenderSpecificHand(RenderSpecificHandEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onRenderTick(TickEvent.RenderTickEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onMouse(MouseEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onLivingDeath(LivingDeathEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onPlaySound(PlaySoundEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onModelRegistry(ModelRegistryEvent event) {
            dispatch(event);
        }

        @SubscribeEvent
        public void onTextureStitchPre(TextureStitchEvent.Pre event) {
            dispatch(event);
        }

        private void dispatch(Event event) {
            dispatchLazy(className, methodName, eventClassName, event);
        }
    }
}
