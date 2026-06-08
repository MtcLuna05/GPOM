package com.l.gpom.compat.multipart.ae2;

import codechicken.multipart.MultiPartRegistry;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.util.Collections;

public final class Ae2MultipartCompat {
    private static boolean registered;

    private Ae2MultipartCompat() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.multipartCompatAe2RegisterPartEnabled()) {
            return;
        }
        registered = true;

        MultiPartRegistry.registerParts(new Ae2PartFactory(), Collections.singletonList(Ae2CableBusMultipart.TYPE));

        if (GpomEarlyConfig.multipartCompatAe2PlacementConverterEnabled()) {
            MultiPartRegistry.registerPlacementConverter(new Ae2PlacementConverter());
        }
        if (GpomEarlyConfig.multipartCompatAe2BlockConverterEnabled()) {
            MultiPartRegistry.registerConverter(new Ae2BlockConverter());
        }

        if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
            GPOM.LOGGER.info(
                    "[GPOM Multipart] AE2 bridge registered: part={}, placementConverter={}, sidePartPlacement={}, blockConverter={}",
                    Ae2CableBusMultipart.TYPE,
                    GpomEarlyConfig.multipartCompatAe2PlacementConverterEnabled(),
                    GpomEarlyConfig.multipartCompatAe2SidePartPlacementEnabled(),
                    GpomEarlyConfig.multipartCompatAe2BlockConverterEnabled()
            );
        }
    }
}
