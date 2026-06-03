package com.l.gpom.mixin.fml;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.l.gpom.optimization.FmlParallelLoadingContext;
import com.l.gpom.optimization.FmlParallelLoadingScheduler;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = LoadController.class, remap = false)
public abstract class MixinLoadControllerStartupProfiler {
    @Shadow
    private List<ModContainer> activeModList;

    @Shadow
    private ImmutableMap<String, EventBus> eventChannels;

    @Shadow
    private Multimap<String, LoaderState.ModState> modStates;

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("HEAD"))
    private void gpom$beginStatePhase(LoaderState state, Object[] eventData, CallbackInfo ci) {
        if (state != null && state.hasEvent()) {
            StartupProfiler.beginPhase(state.name());
        }
    }

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("RETURN"))
    private void gpom$endStatePhase(LoaderState state, Object[] eventData, CallbackInfo ci) {
        if (state != null && state.hasEvent()) {
            StartupProfiler.endPhase(state.name());
        }
    }

    @Inject(method = "propogateStateMessage", at = @At("HEAD"), cancellable = true)
    private void gpom$beginPropagatedPhase(FMLEvent event, CallbackInfo ci) {
        if (event != null) {
            StartupProfiler.beginPhase(event.getEventType());
            if (FmlParallelLoadingScheduler.shouldParallelize(event)) {
                try {
                    FmlParallelLoadingScheduler.propagate(event, activeModList, eventChannels, modStates);
                } finally {
                    StartupProfiler.endPhase(event.getEventType());
                }
                ci.cancel();
            }
        }
    }

    @Inject(method = "propogateStateMessage", at = @At("RETURN"))
    private void gpom$endPropagatedPhase(FMLEvent event, CallbackInfo ci) {
        if (event != null) {
            StartupProfiler.endPhase(event.getEventType());
        }
    }

    @Inject(method = "activeContainer", at = @At("HEAD"), cancellable = true)
    private void gpom$threadLocalActiveContainer(CallbackInfoReturnable<ModContainer> cir) {
        ModContainer activeContainer = FmlParallelLoadingContext.getActiveContainer();
        if (activeContainer != null) {
            cir.setReturnValue(activeContainer);
        }
    }
}
