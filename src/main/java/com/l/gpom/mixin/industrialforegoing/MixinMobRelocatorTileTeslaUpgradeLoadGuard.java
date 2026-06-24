package com.l.gpom.mixin.industrialforegoing;

import com.l.gpom.compat.industrialforegoing.IndustrialForegoingMobCrusherLoadGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.buuz135.industrial.tile.mob.MobRelocatorTile", remap = false)
public abstract class MixinMobRelocatorTileTeslaUpgradeLoadGuard {
    @Inject(method = "protectedUpdate()V", at = @At("HEAD"), require = 0)
    private void gpom$refreshTeslaUpgradeStateAfterLoad(CallbackInfo ci) {
        IndustrialForegoingMobCrusherLoadGuard.refreshIfNeeded(this);
    }
}
