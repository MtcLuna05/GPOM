package com.l.gpom.mixin.fml;

import com.l.gpom.profiling.StartupProfiler;
import com.l.gpom.optimization.EventBusRegistrationOptimizations;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = EventBus.class, remap = false)
public abstract class MixinEventBusStartupProfiler {
    @Inject(method = "register(Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true)
    private void gpom$tryLazyStaticRegistration(Object target, CallbackInfo ci) {
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

    @Unique
    private static String gpom$targetName(Object target) {
        if (target instanceof Class) {
            return ((Class<?>) target).getName();
        }
        return target == null ? null : target.getClass().getName();
    }
}
