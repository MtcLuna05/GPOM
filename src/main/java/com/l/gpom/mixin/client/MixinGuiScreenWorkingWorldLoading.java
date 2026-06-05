package com.l.gpom.mixin.client;

import com.l.gpom.client.WorldLoadingProgress;
import net.minecraft.client.gui.GuiScreenWorking;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiScreenWorking.class, remap = false)
public abstract class MixinGuiScreenWorkingWorldLoading {
    @Inject(
            method = {
                    "drawScreen(IIF)V",
                    "func_73863_a(IIF)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$drawWorldLoadingWorkingScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.safeRenderCurrentMinecraft(-1, false);
        ci.cancel();
    }

    @Inject(
            method = {
                    "displaySavingString(Ljava/lang/String;)V",
                    "func_73720_a(Ljava/lang/String;)V",
                    "resetProgressAndMessage(Ljava/lang/String;)V",
                    "func_73721_b(Ljava/lang/String;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$setWorldLoadingWorkingTitle(String title, CallbackInfo ci) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.updateFromVanilla(title, null, -1);
        ci.cancel();
    }

    @Inject(
            method = {
                    "displayLoadingString(Ljava/lang/String;)V",
                    "func_73719_c(Ljava/lang/String;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$setWorldLoadingWorkingDetail(String detail, CallbackInfo ci) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.updateFromVanilla(null, detail, -1);
        ci.cancel();
    }

    @Inject(
            method = {
                    "setLoadingProgress(I)V",
                    "func_73718_a(I)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$setWorldLoadingWorkingProgress(int progress, CallbackInfo ci) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.updateFromVanilla(null, null, progress);
        ci.cancel();
    }
}
