package com.l.gpom.mixin.betterportals;

import com.l.gpom.compat.betterportals.BetterPortalsWaypointCrashGuard;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.management.PlayerChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PlayerChunkMap.class, priority = 2000, remap = false)
public abstract class MixinPlayerChunkMapMissingWorldManagerGuard {
    @Inject(method = "func_72685_d", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$skipBetterPortalsMissingWorldManagerUpdate(EntityPlayerMP player, CallbackInfo ci) {
        if (BetterPortalsWaypointCrashGuard.shouldCancelMissingWorldManagerUpdate(this, player)) {
            ci.cancel();
        }
    }
}
