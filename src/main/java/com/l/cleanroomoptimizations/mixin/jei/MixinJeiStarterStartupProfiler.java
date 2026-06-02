package com.l.cleanroomoptimizations.mixin.jei;

import com.l.cleanroomoptimizations.profiling.StartupProfiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.startup.JeiStarter", remap = false)
public abstract class MixinJeiStarterStartupProfiler {
    @Unique private long cleanroomoptimizations$startStartedAt;
    @Unique private long cleanroomoptimizations$loadStartedAt;
    @Unique private static long cleanroomoptimizations$registerItemSubtypesStartedAt;
    @Unique private static long cleanroomoptimizations$registerIngredientsStartedAt;
    @Unique private static long cleanroomoptimizations$registerCategoriesStartedAt;
    @Unique private static long cleanroomoptimizations$registerPluginsStartedAt;
    @Unique private static long cleanroomoptimizations$sendRuntimeStartedAt;

    @Inject(method = "start", at = @At("HEAD"))
    private void cleanroomoptimizations$beginStart(CallbackInfo ci) {
        cleanroomoptimizations$startStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void cleanroomoptimizations$endStart(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("HEI JeiStarter.start", cleanroomoptimizations$startStartedAt);
        cleanroomoptimizations$startStartedAt = 0L;
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void cleanroomoptimizations$beginLoad(CallbackInfo ci) {
        cleanroomoptimizations$loadStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void cleanroomoptimizations$endLoad(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("HEI JeiStarter.load", cleanroomoptimizations$loadStartedAt);
        cleanroomoptimizations$loadStartedAt = 0L;
    }

    @Inject(method = "registerItemSubtypes", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginRegisterItemSubtypes(CallbackInfo ci) {
        cleanroomoptimizations$registerItemSubtypesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerItemSubtypes", at = @At("RETURN"))
    private static void cleanroomoptimizations$endRegisterItemSubtypes(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI registerItemSubtypes", cleanroomoptimizations$registerItemSubtypesStartedAt);
        cleanroomoptimizations$registerItemSubtypesStartedAt = 0L;
    }

    @Inject(method = "registerIngredients", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginRegisterIngredients(CallbackInfoReturnable<Object> cir) {
        cleanroomoptimizations$registerIngredientsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerIngredients", at = @At("RETURN"))
    private static void cleanroomoptimizations$endRegisterIngredients(CallbackInfoReturnable<Object> cir) {
        StartupProfiler.endProbe("HEI registerIngredients", cleanroomoptimizations$registerIngredientsStartedAt);
        cleanroomoptimizations$registerIngredientsStartedAt = 0L;
    }

    @Inject(method = "registerCategories", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginRegisterCategories(CallbackInfo ci) {
        cleanroomoptimizations$registerCategoriesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerCategories", at = @At("RETURN"))
    private static void cleanroomoptimizations$endRegisterCategories(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI registerCategories", cleanroomoptimizations$registerCategoriesStartedAt);
        cleanroomoptimizations$registerCategoriesStartedAt = 0L;
    }

    @Inject(method = "registerPlugins", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginRegisterPlugins(CallbackInfo ci) {
        cleanroomoptimizations$registerPluginsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerPlugins", at = @At("RETURN"))
    private static void cleanroomoptimizations$endRegisterPlugins(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI registerPlugins", cleanroomoptimizations$registerPluginsStartedAt);
        cleanroomoptimizations$registerPluginsStartedAt = 0L;
    }

    @Inject(method = "sendRuntime", at = @At("HEAD"))
    private static void cleanroomoptimizations$beginSendRuntime(CallbackInfo ci) {
        cleanroomoptimizations$sendRuntimeStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "sendRuntime", at = @At("RETURN"))
    private static void cleanroomoptimizations$endSendRuntime(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI sendRuntimeToPlugins", cleanroomoptimizations$sendRuntimeStartedAt);
        cleanroomoptimizations$sendRuntimeStartedAt = 0L;
    }
}
