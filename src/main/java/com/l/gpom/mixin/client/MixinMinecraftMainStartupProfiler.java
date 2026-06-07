package com.l.gpom.mixin.client;

import com.l.gpom.client.EarlySplashWindow;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public abstract class MixinMinecraftMainStartupProfiler {
    @Inject(method = "main", at = @At("HEAD"))
    private static void gpom$markMainEntered(String[] args, CallbackInfo ci) {
        EarlySplashWindow.setStatus("Minecraft client main");
        EarlySplashWindow.setBootProgress("Minecraft client main", 4, 4);
        StartupProfiler.markBoot("Minecraft client main entered");
    }
}
