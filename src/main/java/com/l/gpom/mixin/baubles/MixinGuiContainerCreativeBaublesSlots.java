package com.l.gpom.mixin.baubles;

import com.l.gpom.compat.baubles.BaublesSideSlotsClient;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.creativetab.CreativeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;

@Mixin(value = GuiContainerCreative.class, remap = false)
public abstract class MixinGuiContainerCreativeBaublesSlots {
    private static final String[] TAB_LABEL_METHODS = {"getTabLabel", "func_78013_b"};

    @Inject(
            method = {
                    "setCurrentCreativeTab(Lnet/minecraft/creativetab/CreativeTabs;)V",
                    "func_147050_b(Lnet/minecraft/creativetab/CreativeTabs;)V"
            },
            at = @At("RETURN"),
            require = 0
    )
    private void gpom$removeBaubleMirrorsFromSurvivalTab(CreativeTabs tab, CallbackInfo ci) {
        if (isSurvivalInventoryTab(tab)) {
            BaublesSideSlotsClient.removeCreativeSurvivalBaubleMirrors((GuiContainer) (Object) this);
        }
    }

    private static boolean isSurvivalInventoryTab(CreativeTabs tab) {
        if (tab == null) {
            return false;
        }
        for (String methodName : TAB_LABEL_METHODS) {
            String label = getTabLabel(tab, methodName);
            if (label != null) {
                return "inventory".equals(label);
            }
        }
        return false;
    }

    private static String getTabLabel(CreativeTabs tab, String methodName) {
        try {
            Method method = tab.getClass().getMethod(methodName);
            Object value = method.invoke(tab);
            return value instanceof String ? (String) value : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
