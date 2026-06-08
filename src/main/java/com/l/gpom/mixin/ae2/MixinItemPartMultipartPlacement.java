package com.l.gpom.mixin.ae2;

import com.l.gpom.GPOM;
import com.l.gpom.compat.multipart.ae2.Ae2MultipartPlacementHooks;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.items.parts.ItemPart", remap = false)
public abstract class MixinItemPartMultipartPlacement {
    @Inject(
            method = "func_180614_a(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumHand;Lnet/minecraft/util/EnumFacing;FFF)Lnet/minecraft/util/EnumActionResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private void gpom$placeAe2PartAsMultipart(EntityPlayer player,
                                              World world,
                                              BlockPos pos,
                                              EnumHand hand,
                                              EnumFacing side,
                                              float hitX,
                                              float hitY,
                                              float hitZ,
                                              CallbackInfoReturnable<EnumActionResult> cir) {
        if (!GpomEarlyConfig.multipartCompatAe2PlacementConverterEnabled()) {
            return;
        }

        try {
            EnumActionResult result = Ae2MultipartPlacementHooks.tryPlaceHeldAe2Part(world, pos, side, player, hand, hitX, hitY, hitZ);
            if (result != null) {
                cir.setReturnValue(result);
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Multipart] AE2 multipart placement hook failed; falling back to AE2 placement", throwable);
        }
    }
}
