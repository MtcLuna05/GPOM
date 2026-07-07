package com.l.gpom.mixin.client;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        Object playerList = MinecraftMappingCompat.invoke(this$0, "integratedServer.getPlayerList",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_184103_al", "getPlayerList");
        if (playerList != null) {
            for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(gpom$players(playerList))) {
                MinecraftMappingCompat.invoke(playerList, "playerList.playerLoggedOut",
                        new Class<?>[]{EntityPlayerMP.class}, new Object[]{player},
                        "func_72367_e", "playerLoggedOut");
            }
        }
        ci.cancel();
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> gpom$players(Object playerList) {
        Object value = MinecraftMappingCompat.invoke(playerList, "playerList.getPlayers",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_181057_v", "getPlayers");
        return value instanceof List ? (List<EntityPlayerMP>) value : Collections.<EntityPlayerMP>emptyList();
    }
}
