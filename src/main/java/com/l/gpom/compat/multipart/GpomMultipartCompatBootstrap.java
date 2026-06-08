package com.l.gpom.compat.multipart;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;

public final class GpomMultipartCompatBootstrap {
    private static boolean attempted;

    private GpomMultipartCompatBootstrap() {
    }

    public static void preInit() {
        if (attempted || !GpomEarlyConfig.multipartCompatEnabled()) {
            return;
        }
        attempted = true;

        if (GpomEarlyConfig.multipartCompatAe2Enabled()) {
            installAe2Bridge();
        }
    }

    private static void installAe2Bridge() {
        if (!Loader.isModLoaded("forgemultipartcbe")
                || !Loader.isModLoaded("microblockcbe")
                || !Loader.isModLoaded("appliedenergistics2")) {
            if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
                GPOM.LOGGER.info("[GPOM Multipart] AE2 bridge skipped because ForgeMultipart, Microblocks, or AE2 is not loaded");
            }
            return;
        }

        try {
            Class<?> bridgeClass = Class.forName("com.l.gpom.compat.multipart.ae2.Ae2MultipartCompat");
            Method register = bridgeClass.getMethod("register");
            register.invoke(null);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Multipart] Failed to install AE2 multipart bridge; bridge disabled for this launch", throwable);
        }
    }
}
