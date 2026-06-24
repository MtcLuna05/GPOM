package com.l.gpom.compat.scannable;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.util.BitSet;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ScannableOreCacheGuard {
    private static final Set<Integer> LOGGED_NEGATIVE_IDS = Collections.newSetFromMap(new ConcurrentHashMap<Integer, Boolean>());

    private ScannableOreCacheGuard() {
    }

    public static void safeSet(BitSet bitSet, int bitIndex) {
        if (!GpomEarlyConfig.scannableSkipNegativeOreCacheIdsEnabled()) {
            bitSet.set(bitIndex);
            return;
        }
        if (bitSet == null) {
            return;
        }
        if (bitIndex >= 0) {
            bitSet.set(bitIndex);
            return;
        }
        if (GpomEarlyConfig.optimizationInfoLogsEnabled() && LOGGED_NEGATIVE_IDS.add(Integer.valueOf(bitIndex))) {
            GPOM.LOGGER.warn("[ScannableOreCacheGuard] Skipped negative Scannable ore-cache block state id {}", Integer.valueOf(bitIndex));
        }
    }
}
