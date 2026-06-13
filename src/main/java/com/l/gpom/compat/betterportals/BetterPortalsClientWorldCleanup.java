package com.l.gpom.compat.betterportals;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class BetterPortalsClientWorldCleanup {
    private static final String CLIENT_VIEW_API_IMPL = "de.johni0702.minecraft.view.impl.ClientViewAPIImpl";
    private static final String CLIENT_WORLDS_MANAGER_IMPL = "de.johni0702.minecraft.view.impl.client.ClientWorldsManagerImpl";

    private static boolean registered;

    private BetterPortalsClientWorldCleanup() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.betterPortalsCleanupClientWorldsEnabled()) {
            return;
        }
        if (loadClass(CLIENT_WORLDS_MANAGER_IMPL) == null) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(new BetterPortalsClientWorldCleanup());
    }

    public static void cleanup(String reason) {
        if (!GpomEarlyConfig.betterPortalsCleanupClientWorldsEnabled()) {
            return;
        }
        try {
            Object manager = getWorldsManager();
            if (manager != null) {
                resetWorldsManager(manager);
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void worldUnload(WorldEvent.Unload event) {
        try {
            World world = event.getWorld();
            if (world == null) {
                cleanup("client world unload");
                return;
            }
            Minecraft minecraft = Minecraft.getMinecraft();
            if (world.isRemote && world == minecraft.world) {
                cleanup("client world unload");
            }
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void clientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        cleanup("client disconnect");
    }

    private static Object getWorldsManager() {
        try {
            Class<?> apiClass = loadClass(CLIENT_VIEW_API_IMPL);
            if (apiClass == null) {
                return null;
            }

            Object api = getStaticFieldValue(apiClass, "INSTANCE");
            if (api == null) {
                return null;
            }

            Method getter = apiClass.getDeclaredMethod("getViewManagerImpl");
            getter.setAccessible(true);
            return getter.invoke(api);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void resetWorldsManager(Object manager) throws Exception {
        Method reset = manager.getClass().getDeclaredMethod("access$reset", manager.getClass());
        reset.setAccessible(true);
        reset.invoke(null, manager);
    }

    private static Object getStaticFieldValue(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            if (!Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> loadClass(String className) {
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null) {
                return Class.forName(className, false, contextLoader);
            }
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(className, false, BetterPortalsClientWorldCleanup.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

}
