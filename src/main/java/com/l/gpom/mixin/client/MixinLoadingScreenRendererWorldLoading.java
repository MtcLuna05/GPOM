package com.l.gpom.mixin.client;

import com.l.gpom.client.WorldLoadingProgress;
import net.minecraft.client.LoadingScreenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = LoadingScreenRenderer.class, remap = false)
public abstract class MixinLoadingScreenRendererWorldLoading {
    @Unique
    private static Method gpom$getMinecraftMethod;
    @Unique
    private static Method gpom$getScaledWidthMethod;
    @Unique
    private static Method gpom$getScaledHeightMethod;

    @Inject(
            method = {
                    "resetProgressAndMessage(Ljava/lang/String;)V",
                    "func_73721_b(Ljava/lang/String;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$resetWorldLoadTitle(String title, CallbackInfo ci) {
        WorldLoadingProgress.updateFromVanilla(title, null, -1);
        if (gpom$renderWorldLoadingOverlay(-1, true)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                    "displaySavingString(Ljava/lang/String;)V",
                    "func_73720_a(Ljava/lang/String;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$setWorldLoadTitle(String title, CallbackInfo ci) {
        WorldLoadingProgress.updateFromVanilla(title, null, -1);
        if (gpom$renderWorldLoadingOverlay(-1, true)) {
            ci.cancel();
        }
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
    private void gpom$setWorldLoadDetail(String detail, CallbackInfo ci) {
        WorldLoadingProgress.updateFromVanilla(null, detail, -1);
        if (gpom$renderWorldLoadingOverlay(-1, true)) {
            ci.cancel();
        }
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
    private void gpom$drawWorldLoadingScreen(int progress, CallbackInfo ci) {
        if (!WorldLoadingProgress.isActive()) {
            return;
        }
        WorldLoadingProgress.updateFromVanilla(null, null, progress);
        if (gpom$renderWorldLoadingOverlay(progress, true)) {
            ci.cancel();
        }
    }

    @Inject(
            method = {
                    "setLoadingProgress(I)V",
                    "func_73718_a(I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;updateDisplay()V"),
            require = 0
    )
    private void gpom$renderWorldLoadingOverlayMcp(int progress, CallbackInfo ci) {
        gpom$renderWorldLoadingOverlay(progress, false);
    }

    @Inject(
            method = {
                    "setLoadingProgress(I)V",
                    "func_73718_a(I)V"
            },
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;func_175601_h()V"),
            require = 0
    )
    private void gpom$renderWorldLoadingOverlaySrg(int progress, CallbackInfo ci) {
        gpom$renderWorldLoadingOverlay(progress, false);
    }

    @Unique
    private boolean gpom$renderWorldLoadingOverlay(int progress, boolean updateDisplay) {
        if (!WorldLoadingProgress.isActive()) {
            return false;
        }

        try {
            Minecraft minecraft = gpom$getMinecraft();
            if (minecraft == null) {
                return false;
            }

            ScaledResolution resolution = new ScaledResolution(minecraft);
            int width = gpom$getScaledWidth(resolution);
            int height = gpom$getScaledHeight(resolution);
            if (updateDisplay) {
                return WorldLoadingProgress.safeRenderAndUpdate(minecraft, width, height, progress);
            }
            WorldLoadingProgress.safeRender(minecraft, width, height, progress);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Unique
    private static Minecraft gpom$getMinecraft() {
        try {
            Method method = gpom$getMinecraftMethod;
            if (method == null) {
                method = gpom$findMethod(Minecraft.class, new Class<?>[0], "func_71410_x", "getMinecraft");
                gpom$getMinecraftMethod = method;
            }
            Object value = method.invoke(null);
            return value instanceof Minecraft ? (Minecraft) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Unique
    private static int gpom$getScaledWidth(ScaledResolution resolution) {
        try {
            Method method = gpom$getScaledWidthMethod;
            if (method == null) {
                method = gpom$findMethod(ScaledResolution.class, new Class<?>[0], "func_78326_a", "getScaledWidth");
                gpom$getScaledWidthMethod = method;
            }
            Object value = method.invoke(resolution);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Unique
    private static int gpom$getScaledHeight(ScaledResolution resolution) {
        try {
            Method method = gpom$getScaledHeightMethod;
            if (method == null) {
                method = gpom$findMethod(ScaledResolution.class, new Class<?>[0], "func_78328_b", "getScaledHeight");
                gpom$getScaledHeightMethod = method;
            }
            Object value = method.invoke(resolution);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Unique
    private static Method gpom$findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + String.join("/", names));
    }
}
