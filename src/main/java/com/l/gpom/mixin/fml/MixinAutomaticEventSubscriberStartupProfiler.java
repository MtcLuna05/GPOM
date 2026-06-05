package com.l.gpom.mixin.fml;

import com.l.gpom.optimization.AoAConstructionOptimizations;
import com.l.gpom.optimization.EnderIOConstructionOptimizations;
import com.l.gpom.optimization.FmlConstructionSafety;
import com.l.gpom.profiling.StartupProfiler;
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
    private static final ThreadLocal<String> gpom$currentModId = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ModContainer> gpom$currentMod = new ThreadLocal<>();

    @Inject(method = "inject", at = @At("HEAD"), cancellable = true)
    private static void gpom$beginInject(ModContainer mod, ASMDataTable asmData, Side side, CallbackInfo ci) {
        gpom$currentModId.set(mod == null ? null : mod.getModId());
        gpom$currentMod.set(mod);
        if (EnderIOConstructionOptimizations.tryInjectAutomaticSubscribers(mod, asmData, side)
                || AoAConstructionOptimizations.tryInjectAutomaticSubscribers(mod, asmData, side)) {
            gpom$currentModId.remove();
            gpom$currentMod.remove();
            ci.cancel();
        }
    }

    @Inject(method = "inject", at = @At("RETURN"))
    private static void gpom$endInject(ModContainer mod, ASMDataTable asmData, Side side, CallbackInfo ci) {
        gpom$currentModId.remove();
        gpom$currentMod.remove();
    }

    @Redirect(
            method = "inject",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"
            )
    )
    private static Class<?> gpom$timeSubscriberClassLoad(String className, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        long startedAt = StartupProfiler.beginAutomaticSubscriberProbe();
        try {
            return Class.forName(className, initialize, loader);
        } finally {
            StartupProfiler.endAutomaticSubscriberClassLoad(gpom$currentModId.get(), className, startedAt);
        }
    }

    @Redirect(
            method = "inject",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;register(Ljava/lang/Object;)V"
            )
    )
    private static void gpom$timeSubscriberRegister(EventBus eventBus, Object target) {
        long startedAt = StartupProfiler.beginAutomaticSubscriberProbe();
        try {
            String modId = gpom$currentModId.get();
            ModContainer owner = gpom$currentMod.get();
            FmlConstructionSafety.subscriberRegistration("automatic subscriber register " + modId + " " + subscriberName(target), () -> {
                if (!EnderIOConstructionOptimizations.tryRegisterAutomaticSubscriber(eventBus, modId, target, owner)
                        && !AoAConstructionOptimizations.tryRegisterAutomaticSubscriber(modId, target, owner)) {
                    eventBus.register(target);
                }
            });
        } finally {
            StartupProfiler.endAutomaticSubscriberRegister(gpom$currentModId.get(), subscriberName(target), startedAt);
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
