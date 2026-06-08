package com.l.gpom.compat.multipart.ae2;

import appeng.api.networking.IGridHost;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.tileentity.TileEntity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Ae2MultipartGridHostBridge {
    private static final String TILE_MULTIPART_CLASS = "codechicken.multipart.TileMultipart";
    private static final AtomicInteger DEBUG_LOGS_REMAINING = new AtomicInteger(8);
    private static volatile Method multipartPartsMethod;

    private Ae2MultipartGridHostBridge() {
    }

    public static IGridHost findGridHost(TileEntity tile) {
        if (!GpomEarlyConfig.multipartCompatAe2RegisterPartEnabled() || tile == null || !isMultipartTile(tile)) {
            return null;
        }
        List<?> parts = multipartParts(tile);
        if (parts == null) {
            return null;
        }
        for (Object part : parts) {
            if (part instanceof Ae2CableBusMultipart) {
                debugBridge(tile, parts.size(), true);
                return (Ae2CableBusMultipart) part;
            }
        }
        debugBridge(tile, parts.size(), false);
        return null;
    }

    private static boolean isMultipartTile(TileEntity tile) {
        Class<?> current = tile.getClass();
        while (current != null) {
            if (TILE_MULTIPART_CLASS.equals(current.getName())) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static List<?> multipartParts(TileEntity tile) {
        Method method = multipartPartsMethod;
        if (method == null) {
            method = findMultipartPartsMethod(tile);
            multipartPartsMethod = method;
        }
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(tile);
            return value instanceof List ? (List<?>) value : null;
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
                GPOM.LOGGER.warn("[GPOM Multipart] Failed to inspect ForgeMultipart parts for AE2 grid host bridge", throwable);
            }
            return null;
        }
    }

    private static Method findMultipartPartsMethod(TileEntity tile) {
        try {
            Method method = tile.getClass().getMethod("jPartList");
            method.setAccessible(true);
            return method;
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled()) {
                GPOM.LOGGER.warn("[GPOM Multipart] ForgeMultipart TileMultipart.jPartList bridge was not available", throwable);
            }
            return null;
        }
    }

    private static void debugBridge(TileEntity tile, int partCount, boolean found) {
        if (!GpomEarlyConfig.multipartCompatAe2DebugLogsEnabled() || DEBUG_LOGS_REMAINING.getAndDecrement() <= 0) {
            return;
        }
        GPOM.LOGGER.info(
                "[GPOM Multipart] AE2 grid host bridge scanned {} partCount={} foundHostedAe2Cable={}",
                tile.getClass().getName(),
                partCount,
                found
        );
    }
}
