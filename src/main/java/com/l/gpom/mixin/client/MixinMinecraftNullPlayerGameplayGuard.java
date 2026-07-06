package com.l.gpom.mixin.client;

import com.l.gpom.client.ClientNullPlayerStateGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftNullPlayerGameplayGuard {
    @Inject(
            method = {
                    "runTickKeyboard()V",
                    "func_184118_az()V",
                    "runTickMouse()V",
                    "func_184124_aB()V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$skipInputTickWithoutClientPlayer(CallbackInfo ci) {
        Minecraft minecraft = (Minecraft) (Object) this;
        if (!ClientNullPlayerStateGuard.hasNoWorldOrPlayer(minecraft)) {
            return;
        }
        ClientNullPlayerStateGuard.clearInGameFocusIfNoPlayer(minecraft);
        ci.cancel();
    }
}
