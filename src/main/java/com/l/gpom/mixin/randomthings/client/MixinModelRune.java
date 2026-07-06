package com.l.gpom.mixin.randomthings.client;

import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(targets = "lumien.randomthings.client.models.blocks.ModelRune", remap = false)
public abstract class MixinModelRune {
    @Shadow
    public static TextureAtlasSprite runeBase;

    @Shadow
    public static TextureAtlasSprite runeBaseFlat;

    @Inject(method = "func_188616_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void gpom$renderCustomRunes(IBlockState state, EnumFacing side, long rand, CallbackInfoReturnable<List<BakedQuad>> cir) {
        List<BakedQuad> quads = RandomThingsRuneCompat.renderRuneModel(state, side, rand, runeBase, runeBaseFlat);
        if (quads != null) {
            cir.setReturnValue(quads);
        }
    }
}
