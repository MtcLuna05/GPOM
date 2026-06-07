package com.l.gpom.mixin.fml;

import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.ASMEventHandler;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ASMEventHandler.class, remap = false)
public abstract class MixinASMEventHandlerStartupProfiler {
    @Shadow
    @Final
    private ModContainer owner;

    @Shadow
    @Final
    private String readable;

    @Unique
    private long gpom$invokeStartedAt;

    @Unique
    private String gpom$invokeEventName;

    @Inject(method = "invoke", at = @At("HEAD"))
    private void gpom$beginInvoke(Event event, CallbackInfo ci) {
        if (!StartupProfiler.isPostPreInitTransitionActive()) {
            gpom$invokeStartedAt = 0L;
            gpom$invokeEventName = null;
            return;
        }
        gpom$invokeStartedAt = StartupProfiler.beginProbe();
        gpom$invokeEventName = gpom$eventName(event);
    }

    @Inject(method = "invoke", at = @At("RETURN"))
    private void gpom$endInvoke(Event event, CallbackInfo ci) {
        long startedAt = gpom$invokeStartedAt;
        if (startedAt == 0L) {
            return;
        }
        StartupProfiler.endProbe(
                "Forge Event handler " + gpom$invokeEventName + ' ' + gpom$ownerName() + ' ' + gpom$readableName(),
                startedAt
        );
        gpom$invokeStartedAt = 0L;
        gpom$invokeEventName = null;
    }

    @Unique
    private String gpom$ownerName() {
        return owner == null ? "<unknown>" : owner.getModId();
    }

    @Unique
    private String gpom$readableName() {
        return readable == null ? "<unknown>" : readable;
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
