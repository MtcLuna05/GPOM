package com.l.cleanroomoptimizations.mixin.fml;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.event.FMLEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LoadController.class, remap = false)
public abstract class MixinLoadControllerStartupProfiler {
    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("HEAD"))
    private void cleanroomoptimizations$beginStatePhase(LoaderState state, Object[] eventData, CallbackInfo ci) {
        if (state != null && state.hasEvent()) {
            StartupProfiler.beginPhase(state.name());
        }
    }

    @Inject(method = "distributeStateMessage(Lnet/minecraftforge/fml/common/LoaderState;[Ljava/lang/Object;)V", at = @At("RETURN"))
    private void cleanroomoptimizations$endStatePhase(LoaderState state, Object[] eventData, CallbackInfo ci) {
        if (state != null && state.hasEvent()) {
            StartupProfiler.endPhase(state.name());
        }
    }

    @Inject(method = "propogateStateMessage", at = @At("HEAD"))
    private void cleanroomoptimizations$beginPropagatedPhase(FMLEvent event, CallbackInfo ci) {
        if (event != null) {
            StartupProfiler.beginPhase(event.getEventType());
        }
    }

    @Inject(method = "propogateStateMessage", at = @At("RETURN"))
    private void cleanroomoptimizations$endPropagatedPhase(FMLEvent event, CallbackInfo ci) {
        if (event != null) {
            StartupProfiler.endPhase(event.getEventType());
        }
    }
}
