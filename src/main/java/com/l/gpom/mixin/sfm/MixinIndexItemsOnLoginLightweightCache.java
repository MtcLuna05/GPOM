package com.l.gpom.mixin.sfm;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(targets = "vswe.superfactory.client.IndexItemsOnLogin", remap = false)
public abstract class MixinIndexItemsOnLoginLightweightCache {
    @Unique
    private static final AtomicBoolean gpom$scheduled = new AtomicBoolean();

    @Inject(method = "onPlayerLogin", at = @At("HEAD"), cancellable = true, require = 0)
    private static void gpom$deferSearchCacheBuild(FMLNetworkEvent.ClientConnectedToServerEvent event, CallbackInfo ci) {
        if (!GpomEarlyConfig.sfmLightweightSearchCacheEnabled()) {
            return;
        }
        ci.cancel();
        if (!gpom$scheduled.compareAndSet(false, true)) {
            return;
        }

        Runnable task = () -> {
            try {
                ClassLoader loader = Launch.classLoader != null
                        ? Launch.classLoader
                        : MixinIndexItemsOnLoginLightweightCache.class.getClassLoader();
                Class<?> searchUtil = Class.forName("vswe.superfactory.util.SearchUtil", true, loader);
                Method buildCache = searchUtil.getDeclaredMethod("buildCache");
                buildCache.setAccessible(true);
                buildCache.invoke(null);
                GPOM.LOGGER.info("[GPOM SFM] Deferred SFM item search cache build onto client thread");
            } catch (Throwable throwable) {
                gpom$scheduled.set(false);
                GPOM.LOGGER.warn("[GPOM SFM] Failed to defer SFM item search cache build; leaving SFM login handler cancelled", throwable);
            }
        };

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null) {
            minecraft.addScheduledTask(task);
        } else {
            task.run();
        }
    }
}
