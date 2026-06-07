package com.l.gpom.optimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ForgeRegistrySnapshotOptimizations {
    private ForgeRegistrySnapshotOptimizations() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Set immutableSetSnapshot(Set source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet(source));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection immutableCollectionSnapshot(Collection source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        if (source instanceof Set) {
            return Collections.unmodifiableSet(new LinkedHashSet(source));
        }
        return Collections.unmodifiableList(new ArrayList(source));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator iteratorSnapshot(Iterator source) {
        if (source == null) {
            return Collections.emptyList().iterator();
        }
        ArrayList snapshot = new ArrayList();
        while (source.hasNext()) {
            snapshot.add(source.next());
        }
        return snapshot.iterator();
    }
}
