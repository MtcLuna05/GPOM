package com.l.gpom.mixin.server;

import com.l.gpom.optimization.PacedChunkSaveController;
import net.minecraft.world.WorldServer;
import net.minecraft.world.gen.ChunkProviderServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = WorldServer.class, remap = false)
public abstract class MixinWorldServerPacedAutosave {
    @Redirect(method = "func_73044_a(ZLnet/minecraft/util/IProgressUpdate;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/gen/ChunkProviderServer;func_186027_a(Z)Z"),
            remap = false,
            require = 1)
    private boolean gpom$deferPeriodicChunkSave(ChunkProviderServer provider, boolean all) {
        return PacedChunkSaveController.saveChunksOrDefer(provider, all);
    }
}
