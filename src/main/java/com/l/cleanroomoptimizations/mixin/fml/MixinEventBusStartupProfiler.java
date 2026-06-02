package com.l.cleanroomoptimizations.mixin.fml;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

@Mixin(value = EventBus.class, remap = false)
public abstract class MixinEventBusStartupProfiler {
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

    @Unique
    private static String cleanroomoptimizations$targetName(Object target) {
        if (target instanceof Class) {
            return ((Class<?>) target).getName();
        }
        return target == null ? null : target.getClass().getName();
    }
}
