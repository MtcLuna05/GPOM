package com.l.gpom.mixin.client;

import com.l.gpom.client.MouseFocusDeltaGuard;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Minecraft.class, remap = false)
public abstract class MixinMinecraftMouseFocus {
    @Inject(
            method = {
                    "runGameLoop()V",
                    "func_71411_J()V"
            },
            at = @At("HEAD"),
            require = 0
    )
    private void gpom$recaptureFirstPersonMouseAtFrameStart(CallbackInfo ci) {
        MouseFocusDeltaGuard.ensureFirstPersonMouseGrabbed(this);
    }

    @Inject(
            method = {
                    "setIngameFocus()V",
                    "func_71381_h()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MouseHelper;grabMouseCursor()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$drainCursorAfterGrabMcp(CallbackInfo ci) {
        MouseFocusDeltaGuard.afterGrab();
    }

    @Inject(
            method = {
                    "setIngameFocus()V",
                    "func_71381_h()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/util/MouseHelper;func_74372_a()V",
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void gpom$drainCursorAfterGrabSrg(CallbackInfo ci) {
        MouseFocusDeltaGuard.afterGrab();
    }

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
        MouseFocusDeltaGuard.afterUngrab();
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
        MouseFocusDeltaGuard.afterUngrab();
    }
}
