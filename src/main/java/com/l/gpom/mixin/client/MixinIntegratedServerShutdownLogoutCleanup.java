package com.l.gpom.mixin.client;

import com.l.gpom.client.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.management.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(targets = "net.minecraft.server.integrated.IntegratedServer$3")
public class MixinIntegratedServerShutdownLogoutCleanup {
    @Shadow
    @Final
    private IntegratedServer this$0;

    @Inject(method = "run", at = @At("HEAD"), cancellable = true)
    private void gpom$logoutPlayersWhenClientPlayerAlreadyCleared(CallbackInfo ci) {
        Minecraft minecraft = ClientAccess.minecraft();
        EntityLivingBase clientPlayer = ClientAccess.player(minecraft);
        if (clientPlayer != null) {
            return;
        }

        PlayerList playerList = this$0.getPlayerList();
        if (playerList != null) {
            for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(playerList.getPlayers())) {
                playerList.playerLoggedOut(player);
            }
        }
        ci.cancel();
    }
}
