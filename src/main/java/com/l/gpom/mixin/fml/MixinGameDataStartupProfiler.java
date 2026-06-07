package com.l.gpom.mixin.fml;

import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.registries.GameData;
import net.minecraftforge.registries.ObjectHolderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value = GameData.class, remap = false)
public abstract class MixinGameDataStartupProfiler {
    @Unique
    private static final ThreadLocal<Deque<Long>> gpom$gameDataStarts = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "fireCreateRegistryEvents()V", at = @At("HEAD"))
    private static void gpom$beginFireCreateRegistryEvents(CallbackInfo ci) {
        gpom$beginGameDataProbe();
    }

    @Inject(method = "fireCreateRegistryEvents()V", at = @At("RETURN"))
    private static void gpom$endFireCreateRegistryEvents(CallbackInfo ci) {
        gpom$endGameDataProbe("GameData.fireCreateRegistryEvents");
    }

    @Inject(method = "fireRegistryEvents()V", at = @At("HEAD"))
    private static void gpom$beginFireRegistryEvents(CallbackInfo ci) {
        gpom$beginGameDataProbe();
    }

    @Inject(method = "fireRegistryEvents()V", at = @At("RETURN"))
    private static void gpom$endFireRegistryEvents(CallbackInfo ci) {
        gpom$endGameDataProbe("GameData.fireRegistryEvents");
    }

    @Inject(method = "fireRegistryEvents(Ljava/util/function/Predicate;)V", at = @At("HEAD"))
    private static void gpom$beginFireRegistryEventsFiltered(Predicate<?> predicate, CallbackInfo ci) {
        gpom$beginGameDataProbe();
    }

    @Inject(method = "fireRegistryEvents(Ljava/util/function/Predicate;)V", at = @At("RETURN"))
    private static void gpom$endFireRegistryEventsFiltered(Predicate<?> predicate, CallbackInfo ci) {
        gpom$endGameDataProbe("GameData.fireRegistryEvents filtered");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(
            method = "fireRegistryEvents(Ljava/util/function/Predicate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collections;sort(Ljava/util/List;Ljava/util/Comparator;)V"
            )
    )
    private static void gpom$timeRegistrySort(List list, Comparator comparator) {
        long startedAt = gpom$beginPostPreProbe();
        try {
            Collections.sort(list, comparator);
        } finally {
            gpom$endPostPreProbe("GameData.fireRegistryEvents registry sort", startedAt);
        }
    }

    @Redirect(
            method = "fireRegistryEvents(Ljava/util/function/Predicate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z"
            )
    )
    private static boolean gpom$timeRegistryEventPost(EventBus eventBus, Event event) {
        long startedAt = gpom$beginPostPreProbe();
        try {
            return eventBus.post(event);
        } finally {
            gpom$endPostPreProbe("GameData.fireRegistryEvents EventBus.post " + gpom$eventName(event), startedAt);
        }
    }

    @Redirect(
            method = "fireRegistryEvents(Ljava/util/function/Predicate;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/registries/ObjectHolderRegistry;applyObjectHolders()V"
            )
    )
    private static void gpom$timeApplyObjectHolders(ObjectHolderRegistry registry) {
        long startedAt = gpom$beginPostPreProbe();
        try {
            registry.applyObjectHolders();
        } finally {
            gpom$endPostPreProbe("GameData.fireRegistryEvents ObjectHolderRegistry.applyObjectHolders", startedAt);
        }
    }

    @Inject(method = "freezeData()V", at = @At("HEAD"))
    private static void gpom$beginFreezeData(CallbackInfo ci) {
        gpom$beginGameDataProbe();
    }

    @Inject(method = "freezeData()V", at = @At("RETURN"))
    private static void gpom$endFreezeData(CallbackInfo ci) {
        gpom$endGameDataProbe("GameData.freezeData");
    }

    @Inject(method = "vanillaSnapshot()V", at = @At("HEAD"))
    private static void gpom$beginVanillaSnapshot(CallbackInfo ci) {
        gpom$beginGameDataProbe();
    }

    @Inject(method = "vanillaSnapshot()V", at = @At("RETURN"))
    private static void gpom$endVanillaSnapshot(CallbackInfo ci) {
        gpom$endGameDataProbe("GameData.vanillaSnapshot");
    }

    @Unique
    private static void gpom$beginGameDataProbe() {
        gpom$gameDataStarts.get().push(gpom$beginPostPreProbe());
    }

    @Unique
    private static void gpom$endGameDataProbe(String name) {
        Deque<Long> starts = gpom$gameDataStarts.get();
        long startedAt = starts.isEmpty() ? 0L : starts.pop();
        if (startedAt != 0L) {
            StartupProfiler.endProbeAlways(name, startedAt);
        }
    }

    @Unique
    private static long gpom$beginPostPreProbe() {
        return StartupProfiler.isPostPreInitTransitionActive() ? StartupProfiler.beginProbe() : 0L;
    }

    @Unique
    private static void gpom$endPostPreProbe(String name, long startedAt) {
        if (startedAt != 0L) {
            StartupProfiler.endProbeAlways(name, startedAt);
        }
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
}
