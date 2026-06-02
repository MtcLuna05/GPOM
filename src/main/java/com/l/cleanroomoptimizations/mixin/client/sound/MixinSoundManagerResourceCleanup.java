package com.l.cleanroomoptimizations.mixin.client.sound;

import com.l.cleanroomoptimizations.util.ReflectionFields;
import net.minecraft.client.audio.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(SoundManager.class)
public abstract class MixinSoundManagerResourceCleanup {
    @Inject(method = "reloadSoundSystem", at = @At("HEAD"))
    private void cleanroomoptimizations$clearUnableToPlayCacheOnReload(CallbackInfo ci) {
        Object unableToPlay = ReflectionFields.getStatic(SoundManager.class, "UNABLE_TO_PLAY", "UNABLE_TO_PLAY", "field_188775_c", "c");
        if (unableToPlay instanceof Set) {
            ((Set<?>) unableToPlay).clear();
        }
    }
}
