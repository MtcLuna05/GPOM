package com.l.gpom.mixin.fml;

import com.google.common.collect.Multimap;
import com.l.gpom.optimization.FmlConstructionSafety;
import com.l.gpom.optimization.ForgeConstructionAnnotationOptimizations;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.common.network.ForgeNetworkHandler;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.json.JsonAnnotationLoader;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Mixin(value = ForgeModContainer.class, remap = false)
public abstract class MixinForgeModContainerConstructionProfiler {
    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/discovery/json/JsonAnnotationLoader;loadJson(Ljava/io/InputStream;Lnet/minecraftforge/fml/common/discovery/ModCandidate;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)Lcom/google/common/collect/Multimap;"
            )
    )
    private Multimap<?, ?> gpom$timeVanillaAnnotationJson(InputStream inputStream, ModCandidate candidate, ASMDataTable asmData) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return JsonAnnotationLoader.loadJson(inputStream, candidate, asmData);
        } finally {
            StartupProfiler.endProbeAlways("ForgeModContainer.modConstruction vanilla annotation json", startedAt);
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/discovery/ASMDataTable;getAll(Ljava/lang/Class;)Ljava/util/Set;"
            )
    )
    private Set<ASMDataTable.ASMData> gpom$timeAsmGetAll(ASMDataTable asmData, Class<?> annotationClass) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return asmData.getAll(annotationClass);
        } finally {
            StartupProfiler.endProbeAlways(
                    "ForgeModContainer.modConstruction ASM getAll " + gpom$safeClassName(annotationClass),
                    startedAt
            );
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;removeIf(Ljava/util/function/Predicate;)Z"
            )
    )
    private boolean gpom$timeCrashClassFilter(List<String> list, java.util.function.Predicate<? super String> filter) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return list.removeIf(filter);
        } finally {
            StartupProfiler.endProbeAlways(
                    "ForgeModContainer.modConstruction crash preload filter candidates=" + list.size(),
                    startedAt
            );
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Collections;sort(Ljava/util/List;)V"
            )
    )
    private void gpom$timeCrashClassSort(List<String> list) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            Collections.sort(list);
        } finally {
            StartupProfiler.endProbeAlways(
                    "ForgeModContainer.modConstruction crash preload sort candidates=" + list.size(),
                    startedAt
            );
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Class;forName(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"
            )
    )
    private Class<?> gpom$timeCrashClassLoad(String className, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
        long startedAt = StartupProfiler.beginProbe();
        try {
            return Class.forName(className, initialize, classLoader);
        } finally {
            StartupProfiler.endProbeAlways(
                    "ForgeModContainer.modConstruction crash preload class " + className,
                    startedAt
            );
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/network/NetworkRegistry;register(Lnet/minecraftforge/fml/common/ModContainer;Ljava/lang/Class;Ljava/lang/String;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)V"
            )
    )
    private void gpom$timeNetworkRegister(NetworkRegistry registry, net.minecraftforge.fml.common.ModContainer container, Class<?> modClass, String acceptableRemoteVersions, ASMDataTable asmData) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            if (!ForgeConstructionAnnotationOptimizations.tryRegisterNetwork(
                    registry,
                    container,
                    modClass,
                    acceptableRemoteVersions,
                    asmData
            )) {
                FmlConstructionSafety.networkRegistration(
                        "ForgeModContainer.modConstruction network register",
                        () -> registry.register(container, modClass, acceptableRemoteVersions, asmData)
                );
            }
        } finally {
            StartupProfiler.endProbeAlways("ForgeModContainer.modConstruction network register", startedAt);
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/network/ForgeNetworkHandler;registerChannel(Lnet/minecraftforge/common/ForgeModContainer;Lnet/minecraftforge/fml/relauncher/Side;)V"
            )
    )
    private void gpom$timeForgeChannelRegister(ForgeModContainer container, Side side) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            ForgeNetworkHandler.registerChannel(container, side);
        } finally {
            StartupProfiler.endProbeAlways("ForgeModContainer.modConstruction forge channel register", startedAt);
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/config/ConfigManager;sync(Ljava/lang/String;Lnet/minecraftforge/common/config/Config$Type;)V"
            )
    )
    private void gpom$timeConfigSync(String modId, Config.Type type) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            ConfigManager.sync(modId, type);
        } finally {
            StartupProfiler.endProbeAlways("ForgeModContainer.modConstruction config sync", startedAt);
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;register(Ljava/lang/Object;)V"
            )
    )
    private void gpom$timeForgeEventBusRegister(EventBus eventBus, Object target) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            eventBus.register(target);
        } finally {
            StartupProfiler.endProbeAlways(
                    "ForgeModContainer.modConstruction MinecraftForge.EVENT_BUS register "
                            + (target == null ? "<null>" : target.getClass().getName()),
                    startedAt
            );
        }
    }

    @Unique
    private static String gpom$safeClassName(Class<?> type) {
        return type == null ? "<null>" : type.getName();
    }
}
