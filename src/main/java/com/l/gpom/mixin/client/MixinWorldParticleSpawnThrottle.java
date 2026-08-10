package com.l.gpom.mixin.client;

import com.l.gpom.optimization.ParticleSpawnThrottler;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = World.class, remap = false)
public abstract class MixinWorldParticleSpawnThrottle {
    @Inject(method = "func_175688_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void gpom$throttleParticleBurst(EnumParticleTypes type, double x, double y, double z,
                                            double velocityX, double velocityY, double velocityZ,
                                            int[] parameters, CallbackInfo callback) {
        if (ParticleSpawnThrottler.shouldSuppress(type)) {
            callback.cancel();
        }
    }
}
