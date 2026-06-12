package com.l.gpom.mixin.fml;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.optimization.EventBusRegistrationOptimizations;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(value = EventBus.class, remap = false)
public abstract class MixinEventBusStartupProfiler {
    @Unique
    private static final ThreadLocal<Deque<Long>> gpom$postStarts = ThreadLocal.withInitial(ArrayDeque::new);

    @Unique
    private static final ThreadLocal<Deque<String>> gpom$postNames = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "register(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private void gpom$tryLazyStaticRegistration(Object target, CallbackInfo ci) {
        if (EventBusRegistrationOptimizations.tryReplaceFragileInstanceRegistration((EventBus) (Object) this, target)) {
            ci.cancel();
            return;
        }
        if (EventBusRegistrationOptimizations.tryRegisterLazyStaticSubscribers((EventBus) (Object) this, target)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "register(Ljava/lang/Object;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;getMethods()[Ljava/lang/reflect/Method;"
            )
    )
    private Method[] gpom$timeGetMethods(Class<?> targetClass, Object target) {
        long startedAt = StartupProfiler.beginProbe();
        Method[] methods = EventBusRegistrationOptimizations.methodsForRegistration(targetClass, target);
        StartupProfiler.endEventBusGetMethods(gpom$targetName(target), startedAt, methods.length);
        return methods;
    }

    @Inject(method = "post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", at = @At("HEAD"))
    private void gpom$beginPost(Event event, CallbackInfoReturnable<Boolean> cir) {
        EventBusRegistrationOptimizations.sanitizePostedEventListeners((EventBus) (Object) this, event);
        StartupProfiler.postPreInitProgressStage(gpom$postPreInitProgressStage(event));
        if (!StartupProfiler.isPostPreInitTransitionActive()) {
            gpom$postStarts.get().push(0L);
            gpom$postNames.get().push("");
            return;
        }
        if (gpom$isSuppressedHighVolumeProbe(event)) {
            gpom$postStarts.get().push(0L);
            gpom$postNames.get().push("");
            return;
        }
        gpom$postStarts.get().push(StartupProfiler.beginProbe());
        gpom$postNames.get().push(gpom$eventName(event));
    }

    @Inject(method = "post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", at = @At("RETURN"))
    private void gpom$endPost(Event event, CallbackInfoReturnable<Boolean> cir) {
        Deque<Long> starts = gpom$postStarts.get();
        Deque<String> names = gpom$postNames.get();
        long startedAt = starts.isEmpty() ? 0L : starts.pop();
        String eventName = names.isEmpty() ? gpom$eventName(event) : names.pop();
        if (startedAt == 0L) {
            return;
        }
        StartupProfiler.endProbeAlways("Forge EventBus.post " + eventName, startedAt);
    }

    @Unique
    private static String gpom$targetName(Object target) {
        if (target instanceof Class) {
            return ((Class<?>) target).getName();
        }
        return target == null ? null : target.getClass().getName();
    }

    @Unique
    private static String gpom$eventName(Event event) {
        if (event == null) {
            return "<null>";
        }
        String name = event.getClass().getName();
        if (event instanceof RegistryEvent.Register) {
            Object registryName = ((RegistryEvent.Register<?>) event).getName();
            if (registryName != null) {
                return name + " " + registryName;
            }
        }
        return name;
    }

    @Unique
    private static String gpom$postPreInitProgressStage(Event event) {
        if (event == null) {
            return null;
        }
        String name = event.getClass().getName();
        if ("net.minecraftforge.client.event.TextureStitchEvent$Pre".equals(name)) {
            return "Texture stitching";
        }
        if ("net.minecraftforge.client.event.ModelRegistryEvent".equals(name)) {
            return "Model registration";
        }
        if ("net.minecraftforge.client.event.ModelBakeEvent".equals(name)
                || "org.embeddedt.vintagefix.event.DynamicModelBakeEvent".equals(name)) {
            return "Model baking";
        }
        if ("team.chisel.ctm.api.event.TextureCollectedEvent".equals(name)) {
            return "CTM texture collection";
        }
        if (event instanceof RegistryEvent.Register) {
            Object registryName = ((RegistryEvent.Register<?>) event).getName();
            if (registryName != null) {
                return "Registry " + registryName;
            }
        }
        return null;
    }

    @Unique
    private static boolean gpom$isSuppressedHighVolumeProbe(Event event) {
        if (GpomEarlyConfig.startupProfilerHighVolumeEventBusPostProbesEnabled() || event == null) {
            return false;
        }
        String name = event.getClass().getName();
        return "org.embeddedt.vintagefix.event.DynamicModelBakeEvent".equals(name)
                || "team.chisel.ctm.api.event.TextureCollectedEvent".equals(name);
    }
}
