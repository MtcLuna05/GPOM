package com.l.gpom.mixin.thaumcraftfix;

import com.l.gpom.GPOM;
import net.minecraftforge.event.world.WorldEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "thecodex6824.thaumcraftfix.client.ClientEventHandler", remap = false)
public abstract class MixinThaumcraftFixClientEventHandler {
    @Unique private static boolean gpom$loggedMissingParticleEngine;

    @Inject(method = "onClientWorldUnload", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gpom$skipParticleCleanupIfParticleEngineMissing(WorldEvent.Unload event, CallbackInfo ci) {
        try {
            Class.forName("thaumcraft.client.fx.ParticleEngine", false, MixinThaumcraftFixClientEventHandler.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError throwable) {
            if (!gpom$loggedMissingParticleEngine) {
                gpom$loggedMissingParticleEngine = true;
                GPOM.LOGGER.warn("[ThaumcraftFix Compat] Skipping particle cleanup because thaumcraft.client.fx.ParticleEngine is unavailable", throwable);
            }
            ci.cancel();
        }
    }
}
