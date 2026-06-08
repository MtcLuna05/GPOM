package com.l.gpom.compat.multipart.ae2;

import appeng.api.util.AEPartLocation;
import appeng.items.parts.ItemPart;
import appeng.items.parts.PartType;
import codechicken.multipart.TMultiPart;
import codechicken.multipart.api.IPlacementConverter;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public final class Ae2PlacementConverter implements IPlacementConverter {
    @Override
    public boolean canConvert(ItemStack stack) {
        return GpomEarlyConfig.multipartCompatAe2PlacementConverterEnabled() && isCablePartStack(stack);
    }

    @Override
    public TMultiPart convert(ItemStack stack, World world, BlockPos pos, EnumFacing side, Vec3d hit, EntityLivingBase placer, EnumHand hand) {
        if (!isCablePartStack(stack)) {
            return null;
        }

        EntityPlayer player = placer instanceof EntityPlayer ? (EntityPlayer) placer : null;
        Ae2CableBusMultipart part = Ae2CableBusMultipart.forPlacement(stack, AEPartLocation.INTERNAL, player, hand);
        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.info("[GPOM Multipart] Converting AE2 cable item {} into hosted multipart at {}", stack, pos);
        }
        return part;
    }

    static boolean isAe2PartStack(ItemStack stack) {
        return !Ae2ItemStackCompat.isEmpty(stack) && Ae2ItemStackCompat.item(stack) instanceof ItemPart;
    }

    static boolean isCablePartStack(ItemStack stack) {
        if (!isAe2PartStack(stack)) {
            return false;
        }
        PartType type = ((ItemPart) Ae2ItemStackCompat.item(stack)).getTypeByStack(stack);
        return type != null && type.isCable();
    }
}
