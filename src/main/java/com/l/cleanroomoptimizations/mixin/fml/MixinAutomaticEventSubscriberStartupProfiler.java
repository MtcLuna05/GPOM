package com.l.cleanroomoptimizations.mixin.fml;

import com.l.cleanroomoptimizations.profiling.AoAConstructionOptimizations;
import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.AutomaticEventSubscriber;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomaticEventSubscriber.class, remap = false)
public abstract class MixinAutomaticEventSubscriberStartupProfiler {
    @Unique
    private static final ThreadLocal<String> cleanroomoptimizations$currentModId = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ModContainer> cleanroomoptimizations$currentMod = new ThreadLocal<>();

    @Inject(method = "inject", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginInject(ModContainer mod, ASMDataTable asmData, Side side, CallbackInfo ci) {
        cleanroomoptimizations$currentModId.set(mod == null ? null : mod.getModId());
        cleanroomoptimizations$currentMod.set(mod);
    }

    @Inject(method = "inject", at = @At("RETURN"))
    private static void cleanroomoptimizations$endInject(ModContainer mod, ASMDataTable asmData, Side side, CallbackInfo ci) {
        cleanroomoptimizations$currentModId.remove();
        cleanroomoptimizations$currentMod.remove();
    }

    @Redirect(
            method = "inject",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"
            )
    )
    private static Class<?> cleanroomoptimizations$timeSubscriberClassLoad(String className, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        long startedAt = StartupProfiler.beginAutomaticSubscriberProbe();
        try {
            return Class.forName(className, initialize, loader);
        } finally {
            StartupProfiler.endAutomaticSubscriberClassLoad(cleanroomoptimizations$currentModId.get(), className, startedAt);
        }
    }

    @Redirect(
            method = "inject",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;register(Ljava/lang/Object;)V"
            )
    )
    private static void cleanroomoptimizations$timeSubscriberRegister(EventBus eventBus, Object target) {
        long startedAt = StartupProfiler.beginAutomaticSubscriberProbe();
        try {
            if (!AoAConstructionOptimizations.tryRegisterAutomaticSubscriber(cleanroomoptimizations$currentModId.get(), target, cleanroomoptimizations$currentMod.get())) {
                eventBus.register(target);
            }
        } finally {
            StartupProfiler.endAutomaticSubscriberRegister(cleanroomoptimizations$currentModId.get(), subscriberName(target), startedAt);
        }
    }

    @Unique
    private static String subscriberName(Object target) {
        if (target instanceof Class) {
            return ((Class<?>) target).getName();
        }
        return target == null ? null : target.getClass().getName();
    }
}
