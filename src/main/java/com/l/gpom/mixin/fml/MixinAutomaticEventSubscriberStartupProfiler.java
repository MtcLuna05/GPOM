package com.l.gpom.mixin.fml;

import com.l.gpom.optimization.ForgeConstructionAnnotationOptimizations;
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
    private static final String GPOM_THAUMCRAFT_MISSING_PARTICLE_ENGINE = "thaumcraft.client.fx.ParticleEngine";

    @Unique
    private static final String GPOM_DANK_STORAGE_EVENT_HANDLER = "com.tfar.dankstorage.event.DankEventHandler";

    @Unique
    private static boolean gpom$loggedThaumcraftParticleEngineSkip;

    @Unique
    private static boolean gpom$loggedDankStorageEventHandlerSkip;

    @Unique
    private static final ThreadLocal<String> gpom$currentModId = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<ModContainer> gpom$currentMod = new ThreadLocal<>();

    @Inject(method = "inject", at = @At("HEAD"), cancellable = true)
    private static void gpom$beginInject(ModContainer mod, ASMDataTable asmData, Side side, CallbackInfo ci) {
        gpom$currentModId.set(mod == null ? null : mod.getModId());
        gpom$currentMod.set(mod);
        if (ForgeConstructionAnnotationOptimizations.tryInjectAutomaticSubscribers(mod, asmData, side)) {
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
            if (gpom$shouldSkipMissingSubscriber(className)) {
                gpom$logMissingSubscriberSkip(className);
                return GpomSkippedAutomaticSubscriber.class;
            }
            try {
                return Class.forName(className, initialize, loader);
            } catch (ClassNotFoundException exception) {
                throw exception;
            }
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
            FmlConstructionSafety.subscriberRegistration("automatic subscriber register " + modId + " " + subscriberName(target), () -> {
                eventBus.register(target);
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

    @Unique
    private static boolean gpom$shouldSkipMissingSubscriber(String className) {
        String modId = gpom$currentModId.get();
        return (GPOM_THAUMCRAFT_MISSING_PARTICLE_ENGINE.equals(className) && "thaumcraft".equals(modId))
                || (GPOM_DANK_STORAGE_EVENT_HANDLER.equals(className) && "dankstorage".equals(modId));
    }

    @Unique
    private static void gpom$logMissingSubscriberSkip(String className) {
        if (GPOM_THAUMCRAFT_MISSING_PARTICLE_ENGINE.equals(className)) {
            if (gpom$loggedThaumcraftParticleEngineSkip) {
                return;
            }
            gpom$loggedThaumcraftParticleEngineSkip = true;
        } else if (GPOM_DANK_STORAGE_EVENT_HANDLER.equals(className)) {
            if (gpom$loggedDankStorageEventHandlerSkip) {
                return;
            }
            gpom$loggedDankStorageEventHandlerSkip = true;
        }
        com.l.gpom.GPOM.LOGGER.warn(
                "[ForgeConstructionAnnotationOptimizations] Skipping missing automatic subscriber {} for {}",
                className,
                gpom$currentModId.get()
        );
    }

    private static final class GpomSkippedAutomaticSubscriber {
        private GpomSkippedAutomaticSubscriber() {
        }
    }
}
