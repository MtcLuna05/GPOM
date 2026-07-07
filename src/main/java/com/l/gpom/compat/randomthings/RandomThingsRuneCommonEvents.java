package com.l.gpom.compat.randomthings;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class RandomThingsRuneCommonEvents {
    private static boolean registered;

    private RandomThingsRuneCommonEvents() {
    }

    public static void registerIfNeeded() {
        if (registered || !GpomEarlyConfig.randomThingsImprovedRunicDustEnabled() || !Loader.isModLoaded("randomthings")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new RandomThingsRuneCommonEvents());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        World world = event.getWorld();
        EntityPlayer player = event.getEntityPlayer();
        BlockPos pos = event.getPos();
        Vec3d hit = event.getHitVec();
        boolean remote = world != null && MinecraftMappingCompat.worldIsRemote(world);
        if (event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        if (world == null || remote) {
            return;
        }
        if (player == null || MinecraftMappingCompat.playerIsSneaking(player)) {
            return;
        }
        float hitX = 0.5F;
        float hitY = 0.5F;
        float hitZ = 0.5F;
        if (hit != null && pos != null) {
            hitX = (float) (MinecraftMappingCompat.vecX(hit) - MinecraftMappingCompat.blockPosX(pos));
            hitY = (float) (MinecraftMappingCompat.vecY(hit) - MinecraftMappingCompat.blockPosY(pos));
            hitZ = (float) (MinecraftMappingCompat.vecZ(hit) - MinecraftMappingCompat.blockPosZ(pos));
        }
        EnumActionResult result = RandomThingsRuneCompat.toggleConnectionWithEmptyHand(player, world, pos, event.getHand(), event.getFace(), hitX, hitY, hitZ);
        if (result != null) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCancellationResult(result);
            event.setCanceled(true);
        }
    }
}
