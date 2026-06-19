package com.l.gpom.mixin.betterportals;

import com.l.gpom.compat.betterportals.BetterPortalsWaypointCrashGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "de.johni0702.minecraft.view.impl.client.ClientWorldsManagerImpl", remap = false, priority = 2000)
public abstract class MixinClientWorldsManagerImplDestroyMainViewGuard {
    @Inject(method = "destroyState", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$skipDestroyingBetterPortalsMainView(@Coerce Object state, CallbackInfo ci) {
        if (BetterPortalsWaypointCrashGuard.shouldSkipDestroyState(this, state)) {
            ci.cancel();
        }
    }
}
