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
    @Unique
    private static final int gpom$phaseTransitionSteps = 24;
    @Unique
    private static final long gpom$phaseTransitionHeartbeatMillis = 250L;

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
    @Unique
    private int gpom$phaseTransitionStep;

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
        String stateName = state == null ? "FML" : state.name();
        try {
            ProgressManager.ProgressBar progress = ProgressManager.push("GPOM Phase Transition", gpom$phaseTransitionSteps, true);
            synchronized (this) {
                gpom$phaseTransitionProgress = progress;
                gpom$phaseTransitionStep = 0;
            }
            gpom$stepPhaseTransitionProgress(progress, stateName, "Preparing " + stateName);
            gpom$startPhaseTransitionHeartbeat(progress, stateName);
        } catch (Throwable ignored) {
            synchronized (this) {
                gpom$phaseTransitionProgress = null;
                gpom$phaseTransitionStep = 0;
            }
        }
    }

    @Unique
    private void gpom$closePhaseTransitionProgress() {
        ProgressManager.ProgressBar progress;
        synchronized (this) {
            progress = gpom$phaseTransitionProgress;
            gpom$phaseTransitionProgress = null;
            gpom$phaseTransitionStep = 0;
        }
        if (progress == null) {
            return;
        }
        try {
            while (progress.getStep() < progress.getSteps()) {
                progress.step("Starting phase");
            }
            ProgressManager.pop(progress);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private void gpom$startPhaseTransitionHeartbeat(ProgressManager.ProgressBar progress, String stateName) {
        Thread heartbeat = new Thread(() -> {
            int frame = 0;
            while (true) {
                try {
                    Thread.sleep(gpom$phaseTransitionHeartbeatMillis);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }

                synchronized (this) {
                    if (gpom$phaseTransitionProgress != progress) {
                        return;
                    }
                }

                frame = (frame + 1) & 3;
                gpom$stepPhaseTransitionProgress(progress, stateName, "Preparing " + stateName + gpom$ellipsis(frame));
            }
        }, "GPOM phase transition heartbeat");
        heartbeat.setDaemon(true);
        heartbeat.start();
    }

    @Unique
    private void gpom$stepPhaseTransitionProgress(ProgressManager.ProgressBar progress, String stateName, String message) {
        synchronized (this) {
            if (gpom$phaseTransitionProgress != progress || progress == null) {
                return;
            }
            if (gpom$phaseTransitionStep < gpom$phaseTransitionSteps) {
                try {
                    progress.step(message);
                    gpom$phaseTransitionStep++;
                } catch (Throwable ignored) {
                    return;
                }
            }
            EarlySplashWindow.setPhaseProgress(
                    "Forge " + stateName + " preparing" + gpom$ellipsis(gpom$phaseTransitionStep & 3),
                    gpom$phaseTransitionStep,
                    gpom$phaseTransitionSteps
            );
        }
    }

    @Unique
    private static String gpom$ellipsis(int frame) {
        switch (frame & 3) {
            case 1:
                return ".";
            case 2:
                return "..";
            case 3:
                return "...";
            default:
                return "";
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
