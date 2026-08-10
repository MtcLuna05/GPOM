package com.l.gpom.mixin.entity;

import com.l.gpom.optimization.EntityActivationController;
import net.minecraft.entity.EntityLiving;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityLiving.class, remap = false)
public abstract class MixinEntityLivingActivationRange {
    @Inject(method = "func_70626_be", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/profiler/Profiler;func_76319_b()V",
            ordinal = 0, shift = At.Shift.AFTER), cancellable = true, remap = false)
    private void gpom$skipDistantAiAfterDespawnCheck(CallbackInfo callback) {
        if (EntityActivationController.shouldSkipAi((EntityLiving) (Object) this)) {
            callback.cancel();
        }
    }
}
