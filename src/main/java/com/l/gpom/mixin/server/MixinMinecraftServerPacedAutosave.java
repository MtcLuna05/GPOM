package com.l.gpom.mixin.server;

import com.l.gpom.optimization.PacedChunkSaveController;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MixinMinecraftServerPacedAutosave {
    @Inject(method = "func_71217_p", at = @At("HEAD"), remap = false, require = 1)
    private void gpom$continuePacedSaves(CallbackInfo callback) {
        PacedChunkSaveController.tick();
    }

    @Redirect(method = "func_71217_p", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;func_71267_a(Z)V"), remap = false, require = 1)
    private void gpom$beginPacedAutosave(MinecraftServer server, boolean dontLog) {
        PacedChunkSaveController.beginAutosave(server);
    }
}
