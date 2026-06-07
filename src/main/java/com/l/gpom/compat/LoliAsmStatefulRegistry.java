package com.l.gpom.compat;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoliAsmStatefulRegistry {
    private static final int PRUNE_INTERVAL = Math.max(64, Integer.getInteger("gpom.loliasm.statefulPruneInterval", 1024));
    private static final int PRUNE_SIZE = Math.max(PRUNE_INTERVAL, Integer.getInteger("gpom.loliasm.statefulPruneSize", 4096));
    private static final AtomicInteger REGISTRATIONS = new AtomicInteger();

    private LoliAsmStatefulRegistry() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register(Set instances, Object stateful) {
        if (instances == null || stateful == null) {
            return;
        }
        instances.add(new WeakReference<>(stateful));
        int count = REGISTRATIONS.incrementAndGet();
        int size = instances.size();
        if (size >= PRUNE_SIZE || count % PRUNE_INTERVAL == 0) {
            pruneClearedReferences(instances);
        }
    }

    @SuppressWarnings("rawtypes")
    public static int pruneClearedReferences(Set instances) {
        if (instances == null || instances.isEmpty()) {
            return 0;
        }
        int removed = 0;
        Iterator iterator = instances.iterator();
        while (iterator.hasNext()) {
            Object value = iterator.next();
            if (value instanceof WeakReference && ((WeakReference) value).get() == null) {
                try {
                    iterator.remove();
                } catch (UnsupportedOperationException exception) {
                    instances.remove(value);
                }
                removed++;
            }
        }
        return removed;
    }
}
