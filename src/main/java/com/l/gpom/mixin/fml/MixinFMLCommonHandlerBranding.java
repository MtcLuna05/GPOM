package com.l.gpom.mixin.fml;

import com.google.common.collect.ImmutableList;
import com.l.gpom.Reference;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = FMLCommonHandler.class, remap = false)
public abstract class MixinFMLCommonHandlerBranding {
    @Inject(
            method = "computeBranding",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/google/common/collect/ImmutableList$Builder;add(Ljava/lang/Object;)Lcom/google/common/collect/ImmutableList$Builder;",
                    ordinal = 2,
                    remap = false
            ),
            locals = LocalCapture.CAPTURE_FAILHARD,
            remap = false
    )
    private void gpom$addBranding(CallbackInfo ci, ImmutableList.Builder<String> builder) {
        builder.add(Reference.MOD_ID.toUpperCase() + " " + Reference.VERSION);
    }
}
