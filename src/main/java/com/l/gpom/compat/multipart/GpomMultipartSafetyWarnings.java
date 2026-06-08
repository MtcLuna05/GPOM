package com.l.gpom.compat.multipart;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.lang.reflect.Method;

public final class GpomMultipartSafetyWarnings {
    private static final GpomMultipartSafetyWarnings INSTANCE = new GpomMultipartSafetyWarnings();
    private static final Method PLAYER_SEND_MESSAGE = findMethod(EntityPlayer.class, "sendMessage", "func_145747_a", ITextComponent.class);
    private static boolean registered;

    private GpomMultipartSafetyWarnings() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!shouldWarnAboutDisabledAe2MultipartBridge()) {
            return;
        }
        sendMessage(event.player, "[GPOM] WARNING: AE2 ForgeMultipart load support is disabled.");
        sendMessage(event.player, "[GPOM] Existing GPOM-hosted AE2 multipart cables need gpom.multipartCompat.enabled=true, gpom.multipartCompat.ae2.enabled=true, and gpom.multipartCompat.ae2.registerPart=true before opening affected worlds.");
    }

    private static boolean shouldWarnAboutDisabledAe2MultipartBridge() {
        return GpomEarlyConfig.multipartCompatAe2DisabledWarningEnabled()
                && !GpomEarlyConfig.multipartCompatAe2RegisterPartEnabled()
                && Loader.isModLoaded("forgemultipartcbe")
                && Loader.isModLoaded("appliedenergistics2");
    }

    private static void sendMessage(EntityPlayer player, String message) {
        if (player == null || PLAYER_SEND_MESSAGE == null) {
            return;
        }
        try {
            PLAYER_SEND_MESSAGE.invoke(player, new TextComponentString(message));
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Multipart] Failed to send AE2 multipart safety warning to player", throwable);
        }
    }

    private static Method findMethod(Class<?> type, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(type, mcpName, parameterTypes);
        return method != null ? method : findMethod(type, srgName, parameterTypes);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
