package com.l.cleanroomoptimizations.mixin.fml;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(value = EventBus.class, remap = false)
public abstract class MixinEventBusStartupProfiler {
    @Unique
    private final ThreadLocal<Deque<HandlerProbe>> cleanroomoptimizations$handlerProbes = ThreadLocal.withInitial(ArrayDeque::new);

    @Redirect(
            method = "register(Ljava/lang/Object;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;getMethods()[Ljava/lang/reflect/Method;"
            )
    )
    private Method[] cleanroomoptimizations$timeGetMethods(Class<?> targetClass, Object target) {
        long startedAt = StartupProfiler.beginProbe();
        Method[] methods = targetClass.getMethods();
        StartupProfiler.endEventBusGetMethods(cleanroomoptimizations$targetName(target), startedAt, methods.length);
        return methods;
    }

    @Inject(
            method = "register(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/reflect/Method;Lnet/minecraftforge/fml/common/ModContainer;)V",
            at = @At("HEAD")
    )
    private void cleanroomoptimizations$beginHandlerRegister(Class<?> eventType, Object target, Method method, ModContainer owner, CallbackInfo ci) {
        cleanroomoptimizations$handlerProbes.get().push(new HandlerProbe(
                cleanroomoptimizations$targetName(target),
                eventType == null ? null : eventType.getName(),
                method == null ? null : method.getName(),
                StartupProfiler.beginProbe()
        ));
    }

    @Inject(
            method = "register(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/reflect/Method;Lnet/minecraftforge/fml/common/ModContainer;)V",
            at = @At("RETURN")
    )
    private void cleanroomoptimizations$endHandlerRegister(Class<?> eventType, Object target, Method method, ModContainer owner, CallbackInfo ci) {
        Deque<HandlerProbe> probes = cleanroomoptimizations$handlerProbes.get();
        if (probes.isEmpty()) {
            return;
        }

        HandlerProbe probe = probes.pop();
        if (probes.isEmpty()) {
            cleanroomoptimizations$handlerProbes.remove();
        }
        StartupProfiler.endEventBusHandlerRegister(probe.targetName, probe.eventName, probe.methodName, probe.startedAt);
    }

    @Unique
    private static String cleanroomoptimizations$targetName(Object target) {
        if (target instanceof Class) {
            return ((Class<?>) target).getName();
        }
        return target == null ? null : target.getClass().getName();
    }

    @Unique
    private static final class HandlerProbe {
        private final String targetName;
        private final String eventName;
        private final String methodName;
        private final long startedAt;

        private HandlerProbe(String targetName, String eventName, String methodName, long startedAt) {
            this.targetName = targetName;
            this.eventName = eventName;
            this.methodName = methodName;
            this.startedAt = startedAt;
        }
    }
}
