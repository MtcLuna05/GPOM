package com.l.gpom.compat.randomthings.client;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.compat.randomthings.RandomThingsRuneCompat;
import com.l.gpom.compat.randomthings.RandomThingsRuneNetwork;
import com.l.gpom.compat.randomthings.RandomThingsRuneSettings;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class RandomThingsRuneClientEvents {
    private static boolean registered;

    private RandomThingsRuneClientEvents() {
    }

    public static void register(FMLPreInitializationEvent event) {
        if (registered || !GpomEarlyConfig.randomThingsImprovedRunicDustEnabled() || !Loader.isModLoaded("randomthings")) {
            return;
        }
        registered = true;
        RandomThingsRuneSettings.setSettingsFile(event.getModConfigurationDirectory());
        RandomThingsRuneSettings.ensureClientLoaded();
        MinecraftForge.EVENT_BUS.register(new RandomThingsRuneClientEvents());
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        RandomThingsRuneNetwork.sendClientSettingsToServer();
    }

    @SubscribeEvent
    public void onRightClickRuneDust(PlayerInteractEvent.RightClickItem event) {
        if (!RandomThingsRuneCompat.enabled()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !MinecraftMappingCompat.playerIsSneaking(player)) {
            return;
        }
        if (event.getWorld() == null || !MinecraftMappingCompat.worldIsRemote(event.getWorld())) {
            return;
        }
        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, event.getHand());
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)
                || !RandomThingsRuneCompat.isRuneDust(MinecraftMappingCompat.itemStackItem(stack))) {
            return;
        }
        if (RandomThingsRuneCompat.openSettingsScreen(player, MinecraftMappingCompat.itemStackMetadata(stack))) {
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onRightClickRuneTarget(PlayerInteractEvent.RightClickBlock event) {
        if (!RandomThingsRuneCompat.enabled()) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !MinecraftMappingCompat.playerIsSneaking(player)) {
            return;
        }
        if (event.getWorld() == null || !MinecraftMappingCompat.worldIsRemote(event.getWorld())) {
            return;
        }
        int rune = -1;
        ItemStack stack = MinecraftMappingCompat.playerHeldItem(player, event.getHand());
        if (!MinecraftMappingCompat.itemStackIsEmpty(stack)
                && RandomThingsRuneCompat.isRuneDust(MinecraftMappingCompat.itemStackItem(stack))) {
            rune = MinecraftMappingCompat.itemStackMetadata(stack);
        } else if (RandomThingsRuneCompat.isRuneBaseAt(event.getWorld(), event.getPos())) {
            float hitX = 0.5F;
            float hitZ = 0.5F;
            Vec3d hit = event.getHitVec();
            BlockPos pos = event.getPos();
            if (hit != null && pos != null) {
                hitX = (float) (MinecraftMappingCompat.vecX(hit) - MinecraftMappingCompat.blockPosX(pos));
                hitZ = (float) (MinecraftMappingCompat.vecZ(hit) - MinecraftMappingCompat.blockPosZ(pos));
            }
            rune = RandomThingsRuneCompat.runeAt(event.getWorld(), event.getPos(), hitX, hitZ, 0);
        }
        if (rune >= 0 && RandomThingsRuneCompat.openSettingsScreen(player, rune)) {
            event.setCancellationResult(EnumActionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
}
