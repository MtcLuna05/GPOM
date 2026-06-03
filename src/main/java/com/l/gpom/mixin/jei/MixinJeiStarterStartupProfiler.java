package com.l.gpom.mixin.jei;

import com.l.gpom.profiling.StartupProfiler;
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
    @Unique private long gpom$startStartedAt;
    @Unique private long gpom$loadStartedAt;
    @Unique private static long gpom$registerItemSubtypesStartedAt;
    @Unique private static long gpom$registerIngredientsStartedAt;
    @Unique private static long gpom$registerCategoriesStartedAt;
    @Unique private static long gpom$registerPluginsStartedAt;
    @Unique private static long gpom$sendRuntimeStartedAt;

    @Inject(method = "start", at = @At("HEAD"))
    private void gpom$beginStart(CallbackInfo ci) {
        gpom$startStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "start", at = @At("RETURN"))
    private void gpom$endStart(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("HEI JeiStarter.start", gpom$startStartedAt);
        gpom$startStartedAt = 0L;
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void gpom$beginLoad(CallbackInfo ci) {
        gpom$loadStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void gpom$endLoad(CallbackInfo ci) {
        StartupProfiler.endProbeAlways("HEI JeiStarter.load", gpom$loadStartedAt);
        gpom$loadStartedAt = 0L;
    }

    @Inject(method = "registerItemSubtypes", at = @At("HEAD"))
    private static void gpom$beginRegisterItemSubtypes(CallbackInfo ci) {
        gpom$registerItemSubtypesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerItemSubtypes", at = @At("RETURN"))
    private static void gpom$endRegisterItemSubtypes(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI registerItemSubtypes", gpom$registerItemSubtypesStartedAt);
        gpom$registerItemSubtypesStartedAt = 0L;
    }

    @Inject(method = "registerIngredients", at = @At("HEAD"))
    private static void gpom$beginRegisterIngredients(CallbackInfoReturnable<Object> cir) {
        gpom$registerIngredientsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerIngredients", at = @At("RETURN"))
    private static void gpom$endRegisterIngredients(CallbackInfoReturnable<Object> cir) {
        StartupProfiler.endProbe("HEI registerIngredients", gpom$registerIngredientsStartedAt);
        gpom$registerIngredientsStartedAt = 0L;
    }

    @Inject(method = "registerCategories", at = @At("HEAD"))
    private static void gpom$beginRegisterCategories(CallbackInfo ci) {
        gpom$registerCategoriesStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerCategories", at = @At("RETURN"))
    private static void gpom$endRegisterCategories(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI registerCategories", gpom$registerCategoriesStartedAt);
        gpom$registerCategoriesStartedAt = 0L;
    }

    @Inject(method = "registerPlugins", at = @At("HEAD"))
    private static void gpom$beginRegisterPlugins(CallbackInfo ci) {
        gpom$registerPluginsStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "registerPlugins", at = @At("RETURN"))
    private static void gpom$endRegisterPlugins(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI registerPlugins", gpom$registerPluginsStartedAt);
        gpom$registerPluginsStartedAt = 0L;
    }

    @Inject(method = "sendRuntime", at = @At("HEAD"))
    private static void gpom$beginSendRuntime(CallbackInfo ci) {
        gpom$sendRuntimeStartedAt = StartupProfiler.beginProbe();
    }

    @Inject(method = "sendRuntime", at = @At("RETURN"))
    private static void gpom$endSendRuntime(CallbackInfo ci) {
        StartupProfiler.endProbe("HEI sendRuntimeToPlugins", gpom$sendRuntimeStartedAt);
        gpom$sendRuntimeStartedAt = 0L;
    }
}
