package com.l.gpom.mixin.fml;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.EventBus;
import com.l.gpom.client.EarlySplashWindow;
import com.l.gpom.optimization.FmlParallelLoadingContext;
import com.l.gpom.optimization.FmlParallelLoadingScheduler;
import com.l.gpom.optimization.AoAConstructionOptimizations;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Shadow
    private BiMap<ModContainer, Object> modObjectList;

    @Shadow
    public abstract ImmutableBiMap<ModContainer, Object> buildModObjectList();

    private long gpom$stateDispatchStartedAt;
    private String gpom$stateDispatchName;
    @Unique
    private ProgressManager.ProgressBar gpom$phaseTransitionProgress;

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("HEAD"))
    private void gpom$beginStatePhase(LoaderState state, Object[] eventData, CallbackInfo ci) {
        if (state != null && state.hasEvent()) {
            gpom$stateDispatchStartedAt = StartupProfiler.beginProbe();
            gpom$stateDispatchName = state.name();
            EarlySplashWindow.setStatus("Forge " + state.name());
            EarlySplashWindow.setPhaseProgress("Forge " + state.name() + " preparing", 0, activeModList == null ? 0 : activeModList.size());
            gpom$openPhaseTransitionProgress(state);
            StartupProfiler.beginPhase(state.name());
        }
    }

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("RETURN"))
    private void gpom$endStatePhase(LoaderState state, Object[] eventData, CallbackInfo ci) {
        if (state != null && state.hasEvent()) {
            StartupProfiler.endPhase(state.name());
            gpom$closePhaseTransitionProgress();
            gpom$stateDispatchStartedAt = 0L;
            gpom$stateDispatchName = null;
        }
    }

    @Inject(method = "propogateStateMessage", at = @At("HEAD"), cancellable = true)
    private void gpom$beginPropagatedPhase(FMLEvent event, CallbackInfo ci) {
        if (event != null) {
            if (gpom$stateDispatchStartedAt != 0L) {
                StartupProfiler.endProbeAlways(
                        "FML " + event.getEventType() + " before propogateStateMessage"
                                + (gpom$stateDispatchName == null ? "" : " from " + gpom$stateDispatchName),
                        gpom$stateDispatchStartedAt
                );
                gpom$stateDispatchStartedAt = 0L;
                gpom$stateDispatchName = null;
            }
            StartupProfiler.beginPhase(event.getEventType());
            gpom$closePhaseTransitionProgress();
            EarlySplashWindow.close("Minecraft Forge splash active");
            EarlySplashWindow.setPhaseProgress("Forge " + event.getEventType(), 0, activeModList == null ? 0 : activeModList.size());
            if (FmlParallelLoadingScheduler.shouldParallelize(event)) {
                try {
                    if (event instanceof FMLPreInitializationEvent) {
                        modObjectList = buildModObjectList();
                    }
                    FmlParallelLoadingScheduler.propagate(event, activeModList, eventChannels, modStates);
                    if (event instanceof FMLLoadCompleteEvent) {
                        AoAConstructionOptimizations.logFinalGrassRegistryState("after LoadComplete");
                        EarlySplashWindow.close("Forge LoadComplete finished");
                    }
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
            if (event instanceof FMLLoadCompleteEvent) {
                AoAConstructionOptimizations.logFinalGrassRegistryState("after LoadComplete");
                EarlySplashWindow.close("Forge LoadComplete finished");
            }
            StartupProfiler.endPhase(event.getEventType());
        }
    }

    @Unique
    private void gpom$openPhaseTransitionProgress(LoaderState state) {
        gpom$closePhaseTransitionProgress();
        try {
            ProgressManager.ProgressBar progress = ProgressManager.push("GPOM Phase Transition", 1, true);
            progress.step("Preparing " + state.name());
            gpom$phaseTransitionProgress = progress;
        } catch (Throwable ignored) {
            gpom$phaseTransitionProgress = null;
        }
    }

    @Unique
    private void gpom$closePhaseTransitionProgress() {
        ProgressManager.ProgressBar progress = gpom$phaseTransitionProgress;
        if (progress == null) {
            return;
        }
        gpom$phaseTransitionProgress = null;
        try {
            while (progress.getStep() < progress.getSteps()) {
                progress.step("Starting phase");
            }
            ProgressManager.pop(progress);
        } catch (Throwable ignored) {
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
