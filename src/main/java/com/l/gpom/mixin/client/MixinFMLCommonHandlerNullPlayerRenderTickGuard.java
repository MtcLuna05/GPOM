package com.l.gpom.mixin.client;

import com.l.gpom.client.ClientNullPlayerStateGuard;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = FMLCommonHandler.class, remap = false)
public abstract class MixinFMLCommonHandlerNullPlayerRenderTickGuard {
    @Inject(method = "onRenderTickStart(F)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$skipRenderTickStartDuringNullPlayerGameplayFrame(float timer, CallbackInfo ci) {
        if (ClientNullPlayerStateGuard.refreshAndIsUnsafeCurrentClientGameplayFrame()) {
            ci.cancel();
        }
    }

    @Inject(method = "onRenderTickEnd(F)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$skipRenderTickEndDuringNullPlayerGameplayFrame(float timer, CallbackInfo ci) {
        if (ClientNullPlayerStateGuard.refreshAndIsUnsafeCurrentClientGameplayFrame()) {
            ci.cancel();
        }
    }
}
