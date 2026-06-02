package com.l.cleanroomoptimizations.mixin.fml;

import com.google.common.collect.ListMultimap;
import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = FMLModContainer.class, remap = false)
public abstract class MixinFMLModContainerStartupProfiler implements ModContainer {
    @Shadow
    private Object modInstance;

    @Shadow
    private LoadController controller;

    @Shadow
    @Final
    private ListMultimap<Class<? extends FMLEvent>, Method> eventMethods;

    @Unique
    private long cleanroomoptimizations$constructStartedAt;

    @Unique
    private static final ThreadLocal<Object[]> cleanroomoptimizations$singleEventArgument =
            ThreadLocal.withInitial(() -> new Object[1]);

    @Unique
    private StartupProfiler.StackSampler cleanroomoptimizations$constructStackSampler;

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

    /**
     * @author Cleanroom Optimizations
     * @reason Avoids the original duplicate multimap lookup and per-handler Object[] allocation while keeping
     * lifecycle handler ordering and LoadController error reporting identical.
     */
    @Overwrite
    public void handleModStateEvent(FMLEvent event) {
        long eventStartedAt = StartupProfiler.beginMod(this, event);
        StartupProfiler.StackSampler eventStackSampler = StartupProfiler.beginModStackSampler(this, event, eventStartedAt);
        try {
            Class<? extends FMLEvent> eventClass = event.getClass();
            List<Method> methods = eventMethods.get(eventClass);
            if (methods.isEmpty()) {
                return;
            }

            Object[] argument = cleanroomoptimizations$singleEventArgument.get();
            argument[0] = event;
            try {
                for (Method method : methods) {
                    method.invoke(modInstance, argument);
                }
            } catch (Throwable throwable) {
                controller.errorOccurred(this, throwable);
            } finally {
                argument[0] = null;
            }
        } finally {
            StartupProfiler.endModStackSampler(eventStackSampler);
            StartupProfiler.endMod(this, event, eventStartedAt);
        }
    }
}
