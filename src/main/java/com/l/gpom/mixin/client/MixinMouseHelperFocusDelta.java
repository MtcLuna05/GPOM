package com.l.gpom.mixin.client;

import com.l.gpom.client.MouseFocusDeltaGuard;
import net.minecraft.util.MouseHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MouseHelper.class, remap = false)
public abstract class MixinMouseHelperFocusDelta {
    @Redirect(
            method = {
                    "mouseXYChange()V",
                    "func_74374_c()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/input/Mouse;getDX()I"
            ),
            require = 0
    )
    private int gpom$getDxWithoutFocusSnap() {
        return MouseFocusDeltaGuard.mouseDxForCamera();
    }

    @Redirect(
            method = {
                    "mouseXYChange()V",
                    "func_74374_c()V"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/lwjgl/input/Mouse;getDY()I"
            ),
            require = 0
    )
    private int gpom$getDyWithoutFocusSnap() {
        return MouseFocusDeltaGuard.mouseDyForCamera();
    }
}
