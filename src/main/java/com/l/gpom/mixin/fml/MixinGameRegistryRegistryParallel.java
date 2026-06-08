package com.l.gpom.mixin.fml;

import com.l.gpom.optimization.RegistryEventParallelDispatcher;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GameRegistry.class, remap = false)
public abstract class MixinGameRegistryRegistryParallel {
    @Inject(
            method = "register(Lnet/minecraftforge/registries/IForgeRegistryEntry;)Lnet/minecraftforge/registries/IForgeRegistryEntry;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void gpom$queueRegistryWorkerRegistration(IForgeRegistryEntry<?> entry,
                                                             CallbackInfoReturnable<IForgeRegistryEntry<?>> cir) {
        if (RegistryEventParallelDispatcher.queueWorkerRegistration(entry)) {
            cir.setReturnValue(entry);
        }
    }
}
