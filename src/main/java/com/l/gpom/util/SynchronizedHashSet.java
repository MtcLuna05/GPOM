package com.l.gpom.util;

import java.util.Collection;
import java.util.HashSet;

public class SynchronizedHashSet<E> extends HashSet<E> {
    @Override
    public synchronized boolean add(E value) {
        return super.add(value);
    }

    @Override
    public synchronized boolean addAll(Collection<? extends E> values) {
        return super.addAll(values);
    }

    @Override
    public synchronized boolean contains(Object value) {
        return super.contains(value);
    }

    @Override
    public synchronized Object[] toArray() {
        return super.toArray();
    }

    @Override
    public synchronized <T> T[] toArray(T[] array) {
        return super.toArray(array);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}
