package com.l.gpom.mixin.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mixin(value = IntegratedServer.class, remap = false)
public abstract class MixinIntegratedServerShutdownNullPlayerTaskGuard {
    @Redirect(
            method = {
                    "initiateShutdown()V",
                    "func_71260_j()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/integrated/IntegratedServer;addScheduledTask(Ljava/lang/Runnable;)Lcom/google/common/util/concurrent/ListenableFuture;"
            ),
            require = 0
    )
    private ListenableFuture<Object> gpom$skipUnsafeLogoutTaskWhenClientPlayerCleared(IntegratedServer server, Runnable task) {
        if (ClientAccess.player(ClientAccess.minecraft()) != null) {
            Object value = MinecraftMappingCompat.invoke(server, "integratedServer.addScheduledTask",
                    new Class<?>[]{Runnable.class}, new Object[]{task},
                    "func_152344_a", "addScheduledTask");
            if (value instanceof ListenableFuture) {
                return (ListenableFuture<Object>) value;
            }
            task.run();
            return Futures.immediateFuture(null);
        }

        Object playerList = MinecraftMappingCompat.invoke(server, "integratedServer.getPlayerList",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_184103_al", "getPlayerList");
        if (playerList != null) {
            for (EntityPlayerMP player : new ArrayList<EntityPlayerMP>(gpom$players(playerList))) {
                MinecraftMappingCompat.invoke(playerList, "playerList.playerLoggedOut",
                        new Class<?>[]{EntityPlayerMP.class}, new Object[]{player},
                        "func_72367_e", "playerLoggedOut");
            }
        }
        return Futures.immediateFuture(null);
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> gpom$players(Object playerList) {
        Object value = MinecraftMappingCompat.invoke(playerList, "playerList.getPlayers",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS,
                "func_181057_v", "getPlayers");
        return value instanceof List ? (List<EntityPlayerMP>) value : Collections.<EntityPlayerMP>emptyList();
    }
}
