package com.l.gpom.mixin.client;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftMouseFocus {
    @Inject(
            method = {
                    "setIngameNotInFocus()V",
                    "func_71364_i()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MouseHelper;ungrabMouseCursor()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$recenterCursorAfterUngrabMcp(CallbackInfo ci) {
        gpom$recenterCursor();
    }

    @Inject(
            method = {
                    "setIngameNotInFocus()V",
                    "func_71364_i()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MouseHelper;func_74373_b()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$recenterCursorAfterUngrabSrg(CallbackInfo ci) {
        gpom$recenterCursor();
    }

    private static void gpom$recenterCursor() {
        try {
            if (!Display.isCreated() || !Mouse.isCreated() || Mouse.isGrabbed()) {
                return;
            }
            int width = Display.getWidth();
            int height = Display.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }

            Mouse.setCursorPosition(width / 2, height / 2);
        } catch (Throwable ignored) {
        }
    }
}
