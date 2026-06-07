package com.l.gpom.optimization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class ForestryRecipeManagerOptimizations {
    private ForestryRecipeManagerOptimizations() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean addRecipeCollection(Collection collection, Object value) {
        if (collection == null) {
            return false;
        }
        synchronized (collection) {
            return collection.add(value);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static boolean removeRecipeCollection(Collection collection, Object value) {
        if (collection == null) {
            return false;
        }
        synchronized (collection) {
            return collection.remove(value);
        }
    }

    @SuppressWarnings("rawtypes")
    public static void clearRecipeCollection(Collection collection) {
        if (collection == null) {
            return;
        }
        synchronized (collection) {
            collection.clear();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Set immutableSetSnapshot(Set source) {
        if (source == null) {
            return Collections.emptySet();
        }
        synchronized (source) {
            if (source.isEmpty()) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(new LinkedHashSet(source));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection immutableCollectionSnapshot(Collection source) {
        if (source == null) {
            return Collections.emptyList();
        }
        synchronized (source) {
            if (source.isEmpty()) {
                return Collections.emptyList();
            }
            if (source instanceof Set) {
                return Collections.unmodifiableSet(new LinkedHashSet(source));
            }
            return Collections.unmodifiableList(new ArrayList(source));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator iteratorSnapshot(Collection source) {
        if (source == null) {
            return Collections.emptyList().iterator();
        }
        synchronized (source) {
            if (source.isEmpty()) {
                return Collections.emptyList().iterator();
            }
            return new ArrayList(source).iterator();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Stream streamSnapshot(Collection source) {
        if (source == null) {
            return Stream.empty();
        }
        synchronized (source) {
            if (source.isEmpty()) {
                return Stream.empty();
            }
            return new ArrayList(source).stream();
        }
    }
}
