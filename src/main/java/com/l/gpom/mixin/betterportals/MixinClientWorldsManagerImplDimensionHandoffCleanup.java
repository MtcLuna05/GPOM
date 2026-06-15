package com.l.gpom.mixin.betterportals;

import com.l.gpom.client.ClientDimensionHandoffCleanup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.johni0702.minecraft.view.impl.client.ClientWorldsManagerImpl", remap = false)
public abstract class MixinClientWorldsManagerImplDimensionHandoffCleanup {
    @Inject(method = "makeMainView", at = @At("RETURN"), require = 0)
    private void gpom$cleanupTransientEffectsAfterMainViewSwap(
            @Coerce Object newMainView,
            CallbackInfo ci
    ) {
        ClientDimensionHandoffCleanup.cleanup("BetterPortals main view swap");
    }
}
