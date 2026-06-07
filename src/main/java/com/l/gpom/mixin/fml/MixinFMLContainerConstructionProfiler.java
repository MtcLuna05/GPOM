package com.l.gpom.mixin.fml;

import com.l.gpom.optimization.FmlConstructionSafety;
import com.l.gpom.optimization.ForgeConstructionAnnotationOptimizations;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraftforge.fml.common.FMLContainer;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = FMLContainer.class, remap = false)
public abstract class MixinFMLContainerConstructionProfiler {
    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/network/NetworkRegistry;register(Lnet/minecraftforge/fml/common/ModContainer;Ljava/lang/Class;Ljava/lang/String;Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)V"
            )
    )
    private void gpom$timeNetworkRegister(NetworkRegistry registry, ModContainer container, Class<?> modClass, String acceptableRemoteVersions, ASMDataTable asmData) {
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
                        "FMLContainer.modConstruction network register",
                        () -> registry.register(container, modClass, acceptableRemoteVersions, asmData)
                );
            }
        } finally {
            StartupProfiler.endProbeAlways("FMLContainer.modConstruction network register", startedAt);
        }
    }

    @Redirect(
            method = "modConstruction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/fml/common/network/internal/FMLNetworkHandler;registerChannel(Lnet/minecraftforge/fml/common/FMLContainer;Lnet/minecraftforge/fml/relauncher/Side;)V"
            )
    )
    private void gpom$timeFmlChannelRegister(FMLContainer container, Side side) {
        long startedAt = StartupProfiler.beginProbe();
        try {
            FMLNetworkHandler.registerChannel(container, side);
        } finally {
            StartupProfiler.endProbeAlways("FMLContainer.modConstruction FML channel register", startedAt);
        }
    }
}
