package com.l.gpom.mixin.fml;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mixin(value = ASMDataTable.class, remap = false)
public abstract class MixinASMDataTableAnnotationCache {
    @Shadow
    @Final
    private SetMultimap<String, ASMDataTable.ASMData> globalAnnotationData;

    @Shadow
    private Map<ModContainer, SetMultimap<String, ASMDataTable.ASMData>> containerAnnotationData;

    @Shadow
    private List<ModContainer> containers;

    @Inject(method = "getAnnotationsFor", at = @At("HEAD"), cancellable = true)
    private void gpom$getAnnotationsForFast(ModContainer container,
                                            CallbackInfoReturnable<SetMultimap<String, ASMDataTable.ASMData>> cir) {
        Map<ModContainer, SetMultimap<String, ASMDataTable.ASMData>> cache = containerAnnotationData;
        if (cache == null) {
            synchronized (this) {
                cache = containerAnnotationData;
                if (cache == null) {
                    long startedAt = StartupProfiler.beginProbe();
                    try {
                        cache = gpom$buildContainerAnnotationData();
                        containerAnnotationData = cache;
                    } finally {
                        StartupProfiler.endProbe("FML ASMDataTable getAnnotationsFor buildFast", startedAt);
                    }
                }
            }
        }
        cir.setReturnValue(cache.get(container));
    }

    @Unique
    private Map<ModContainer, SetMultimap<String, ASMDataTable.ASMData>> gpom$buildContainerAnnotationData() {
        Map<File, SetMultimap<String, ASMDataTable.ASMData>> bySource = new HashMap<>();
        for (ASMDataTable.ASMData data : globalAnnotationData.values()) {
            ModCandidate candidate = data.getCandidate();
            if (candidate == null) {
                continue;
            }
            File source = candidate.getModContainer();
            if (source == null) {
                continue;
            }
            bySource.computeIfAbsent(source, ignored -> HashMultimap.create()).put(data.getAnnotationName(), data);
        }

        ImmutableMap.Builder<ModContainer, SetMultimap<String, ASMDataTable.ASMData>> result = ImmutableMap.builder();
        for (ModContainer mod : containers) {
            File source = mod.getSource();
            SetMultimap<String, ASMDataTable.ASMData> sourceData = source == null ? null : bySource.get(source);
            result.put(mod, sourceData == null ? ImmutableSetMultimap.of() : ImmutableSetMultimap.copyOf(sourceData));
        }
        return result.build();
    }
}
