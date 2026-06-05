package com.l.gpom.mixin.fml;

import com.l.gpom.profiling.StartupProfiler;
import com.l.gpom.optimization.AoAConfigSyncOptimizations;
import com.l.gpom.optimization.FmlConstructionSafety;
import com.l.gpom.optimization.ForgeNetworkConstructionOptimizations;
import com.google.common.eventbus.EventBus;
import net.minecraftforge.fml.common.FMLModContainer;
import net.minecraftforge.fml.common.ILanguageAdapter;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ProxyInjector;
import net.minecraftforge.fml.common.AutomaticEventSubscriber;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Set;

@Mixin(value = FMLModContainer.class, remap = false)
public abstract class MixinFMLModContainerStartupProfiler implements ModContainer {
    @Unique
    private long gpom$constructStartedAt;

    @Unique
    private long gpom$eventStartedAt;

    @Unique
    private StartupProfiler.StackSampler gpom$constructStackSampler;

    @Unique
    private StartupProfiler.StackSampler gpom$eventStackSampler;

    @Shadow
    private Method gatherAnnotations(Class<?> modClass) {
        throw new AssertionError();
    }

    @Shadow
    private void processFieldAnnotations(ASMDataTable asmData) throws IllegalAccessException {
        throw new AssertionError();
    }

    @Inject(method = "constructMod", at = @At("HEAD"))
    private void gpom$beginConstruct(FMLConstructionEvent event, CallbackInfo ci) {
        gpom$constructStartedAt = StartupProfiler.beginMod(this, event);
        gpom$constructStackSampler = StartupProfiler.beginModStackSampler(this, event, gpom$constructStartedAt);
    }

    @Inject(method = "constructMod", at = @At("RETURN"))
    private void gpom$endConstruct(FMLConstructionEvent event, CallbackInfo ci) {
        StartupProfiler.endModStackSampler(gpom$constructStackSampler);
        StartupProfiler.endMod(this, event, gpom$constructStartedAt);
        gpom$constructStackSampler = null;
        gpom$constructStartedAt = 0L;
    }

    @Inject(method = "handleModStateEvent", at = @At("HEAD"))
    private void gpom$beginStateEvent(FMLEvent event, CallbackInfo ci) {
        gpom$eventStartedAt = StartupProfiler.beginMod(this, event);
        gpom$eventStackSampler = StartupProfiler.beginModStackSampler(this, event, gpom$eventStartedAt);
    }

    @Inject(method = "handleModStateEvent", at = @At("RETURN"))
    private void gpom$endStateEvent(FMLEvent event, CallbackInfo ci) {
        StartupProfiler.endModStackSampler(gpom$eventStackSampler);
        StartupProfiler.endMod(this, event, gpom$eventStartedAt);
        gpom$eventStackSampler = null;
        gpom$eventStartedAt = 0L;
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/ModClassLoader;addFile(Ljava/io/File;)V"
            )
    )
    private void gpom$timeConstructionAddFile(ModClassLoader loader, File file) throws MalformedURLException {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.classloaderMutation(gpom$constructionStage("addFile"), () -> loader.addFile(file));
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("addFile"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/ModClassLoader;clearNegativeCacheFor(Ljava/util/Set;)V"
            )
    )
    private void gpom$timeConstructionClearNegativeCache(ModClassLoader loader, Set<String> classes) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.classloaderMutation(
                    gpom$constructionStage("clearNegativeCacheFor"),
                    () -> loader.clearNegativeCacheFor(classes)
            );
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("clearNegativeCacheFor"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/MinecraftForge;preloadCrashClasses(Lnet/minecraftforge/fml/common/discovery/ASMDataTable;Ljava/lang/String;Ljava/util/Set;)V"
            )
    )
    private void gpom$timeConstructionPreloadCrashClasses(ASMDataTable asmData, String modId, Set<String> classes) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.forgeSharedMutation(
                    gpom$constructionStage("preloadCrashClasses"),
                    () -> MinecraftForge.preloadCrashClasses(asmData, modId, classes)
            );
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("preloadCrashClasses"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"
            )
    )
    private Class<?> gpom$timeConstructionLoadModClass(String className, boolean initialize, ClassLoader loader) throws ClassNotFoundException {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return Class.forName(className, initialize, loader);
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("loadModClass " + gpom$safeLabel(className)), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/FMLModContainer;gatherAnnotations(Ljava/lang/Class;)Ljava/lang/reflect/Method;"
            )
    )
    private Method gpom$timeConstructionGatherAnnotations(FMLModContainer container, Class<?> modClass) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return gatherAnnotations(modClass);
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("gatherAnnotations"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/ILanguageAdapter;getNewInstance(Lnet/minecraftforge/fml/common/FMLModContainer;Ljava/lang/Class;Ljava/lang/ClassLoader;Ljava/lang/reflect/Method;)Ljava/lang/Object;"
            )
    )
    private Object gpom$timeConstructionNewInstance(ILanguageAdapter adapter, FMLModContainer container, Class<?> modClass, ClassLoader loader, Method factoryMethod) throws Exception {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return adapter.getNewInstance(container, modClass, loader, factoryMethod);
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("getNewInstance " + gpom$safeLabel(modClass == null ? null : modClass.getName())), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/network/NetworkRegistry;register(Lnet/minecraftforge/fml/common/ModContainer;Ljava/lang/Class;Ljava/lang/String;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)V"
            )
    )
    private void gpom$timeConstructionNetworkRegister(NetworkRegistry registry, ModContainer container, Class<?> modClass, String acceptableRemoteVersions, ASMDataTable asmData) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.networkRegistration(
                    gpom$constructionStage("networkRegister"),
                    () -> {
                        if (!ForgeNetworkConstructionOptimizations.tryFastRegisterKnownNoNetworkChecker(
                                registry,
                                container,
                                modClass,
                                acceptableRemoteVersions,
                                asmData
                        )) {
                            registry.register(container, modClass, acceptableRemoteVersions, asmData);
                        }
                    }
            );
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("networkRegister"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/eventbus/EventBus;post(Ljava/lang/Object;)V"
            )
    )
    private void gpom$timeConstructionEventBusPost(EventBus eventBus, Object event) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            eventBus.post(event);
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("eventBusPost " + gpom$safeLabel(event == null ? null : event.getClass().getName())), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/ProxyInjector;inject(Lnet/minecraftforge/fml/common/ModContainer;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;Lnet/minecraftforge/fml/relauncher/Side;Lnet/minecraftforge/fml/common/ILanguageAdapter;)V"
            )
    )
    private void gpom$timeConstructionProxyInject(ModContainer container, ASMDataTable asmData, Side side, ILanguageAdapter adapter) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.proxyInjection(
                    gpom$constructionStage("proxyInject"),
                    () -> {
                        if (!ForgeNetworkConstructionOptimizations.tryFastInjectKnownProxy(
                                container,
                                side,
                                adapter
                        )) {
                            ProxyInjector.inject(container, asmData, side, adapter);
                        }
                    }
            );
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("proxyInject"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/AutomaticEventSubscriber;inject(Lnet/minecraftforge/fml/common/ModContainer;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;Lnet/minecraftforge/fml/relauncher/Side;)V"
            )
    )
    private void gpom$timeConstructionAutomaticSubscribers(ModContainer container, ASMDataTable asmData, Side side) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            AutomaticEventSubscriber.inject(container, asmData, side);
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("automaticSubscribers"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/config/ConfigManager;sync(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V"
            )
    )
    private void gpom$timeConstructionConfigSync(String modId, Config.Type type) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.configSync(gpom$constructionStage("configSync"), () -> {
                if (!AoAConfigSyncOptimizations.tryFastSync(modId, type)) {
                    ConfigManager.sync(modId, type);
                }
            });
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("configSync"), startedAt);
        }
    }

    @Redirect(
            method = "constructMod",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/FMLModContainer;processFieldAnnotations(Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)V"
            )
    )
    private void gpom$timeConstructionProcessFieldAnnotations(FMLModContainer container, ASMDataTable asmData) throws IllegalAccessException {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FmlConstructionSafety.annotationProcessing(
                    gpom$constructionStage("processFieldAnnotations"),
                    () -> processFieldAnnotations(asmData)
            );
        } finally {
            StartupProfiler.endProbe(gpom$constructionStage("processFieldAnnotations"), startedAt);
        }
    }

    @Unique
    private String gpom$constructionStage(String stage) {
        return "FML construct " + gpom$safeLabel(getModId()) + " " + stage;
    }

    @Unique
    private static String gpom$safeLabel(String value) {
        return value == null ? "<null>" : value;
    }
}
