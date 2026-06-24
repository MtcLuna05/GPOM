package com.l.gpom.mixin.teslacorelib;

import com.l.gpom.compat.industrialforegoing.IndustrialForegoingMobCrusherLoadGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.ndrei.teslacorelib.tileentities.ElectricMachine", remap = false)
public abstract class MixinElectricMachineMobCrusherLoadGuard {
    @Inject(method = "updateWorkEnergyRate()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$deferWorkEnergyRateRefreshDuringMobCrusherLoad(CallbackInfo ci) {
        if (IndustrialForegoingMobCrusherLoadGuard.suppressUpgradeRefresh(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "updateWorkEnergyCapacity()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$deferWorkEnergyCapacityRefreshDuringMobCrusherLoad(CallbackInfo ci) {
        if (IndustrialForegoingMobCrusherLoadGuard.suppressUpgradeRefresh(this)) {
            ci.cancel();
        }
    }
}
