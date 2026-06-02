package com.l.cleanroomoptimizations.mixin.fml;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FMLModContainer.class, remap = false)
public abstract class MixinFMLModContainerStartupProfiler implements ModContainer {
    @Unique
    private long cleanroomoptimizations$constructStartedAt;

    @Unique
    private long cleanroomoptimizations$eventStartedAt;

    @Unique
    private StartupProfiler.StackSampler cleanroomoptimizations$constructStackSampler;

    @Unique
    private StartupProfiler.StackSampler cleanroomoptimizations$eventStackSampler;

    @Inject(method = "constructMod", at = @At("HEAD"))
    private void cleanroomoptimizations$beginConstruct(FMLConstructionEvent event, CallbackInfo ci) {
        cleanroomoptimizations$constructStartedAt = StartupProfiler.beginMod(this, event);
        cleanroomoptimizations$constructStackSampler = StartupProfiler.beginModStackSampler(this, event, cleanroomoptimizations$constructStartedAt);
    }

    @Inject(method = "constructMod", at = @At("RETURN"))
    private void cleanroomoptimizations$endConstruct(FMLConstructionEvent event, CallbackInfo ci) {
        StartupProfiler.endModStackSampler(cleanroomoptimizations$constructStackSampler);
        StartupProfiler.endMod(this, event, cleanroomoptimizations$constructStartedAt);
        cleanroomoptimizations$constructStackSampler = null;
        cleanroomoptimizations$constructStartedAt = 0L;
    }

    @Inject(method = "handleModStateEvent", at = @At("HEAD"))
    private void cleanroomoptimizations$beginStateEvent(FMLEvent event, CallbackInfo ci) {
        cleanroomoptimizations$eventStartedAt = StartupProfiler.beginMod(this, event);
        cleanroomoptimizations$eventStackSampler = StartupProfiler.beginModStackSampler(this, event, cleanroomoptimizations$eventStartedAt);
    }

    @Inject(method = "handleModStateEvent", at = @At("RETURN"))
    private void cleanroomoptimizations$endStateEvent(FMLEvent event, CallbackInfo ci) {
        StartupProfiler.endModStackSampler(cleanroomoptimizations$eventStackSampler);
        StartupProfiler.endMod(this, event, cleanroomoptimizations$eventStartedAt);
        cleanroomoptimizations$eventStackSampler = null;
        cleanroomoptimizations$eventStartedAt = 0L;
    }
}
