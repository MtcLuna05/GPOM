package com.l.gpom.mixin.astralsorcery;

import com.l.gpom.client.ClientAccess;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "hellfirepvp.astralsorcery.client.event.ClientConnectionEventHandler", remap = false)
public abstract class MixinAstralSorceryClientConnectionEventHandler {
    @Shadow
    public abstract void onDc(FMLNetworkEvent.ClientDisconnectionFromServerEvent event);

    @Inject(method = "onDc", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$runDisconnectCleanupOnClientThread(FMLNetworkEvent.ClientDisconnectionFromServerEvent event, CallbackInfo ci) {
        Minecraft minecraft = ClientAccess.minecraft();
        if (minecraft == null || ClientAccess.isMinecraftThread(minecraft)) {
            return;
        }

        ci.cancel();
        ClientAccess.schedule(minecraft, () -> onDc(event));
    }
}
