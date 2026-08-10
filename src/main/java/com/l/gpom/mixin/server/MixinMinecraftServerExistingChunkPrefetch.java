package com.l.gpom.mixin.server;

import com.l.gpom.optimization.ExistingChunkPrefetchController;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MinecraftServer.class, remap = false)
public abstract class MixinMinecraftServerExistingChunkPrefetch {
    @Inject(method = "func_71217_p", at = @At("HEAD"), remap = false)
    private void gpom$prefetchExistingChunksAheadOfPlayers(CallbackInfo callback) {
        ExistingChunkPrefetchController.tick((MinecraftServer) (Object) this);
    }
}
