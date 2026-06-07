package com.l.gpom.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class SynchronizedHashMap<K, V> extends HashMap<K, V> {
    @Override
    public synchronized V get(Object key) {
        return super.get(key);
    }

    @Override
    public synchronized boolean containsKey(Object key) {
        return super.containsKey(key);
    }

    @Override
    public synchronized V put(K key, V value) {
        return super.put(key, value);
    }

    @Override
    public synchronized V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        return super.computeIfAbsent(key, mappingFunction);
    }

    @Override
    public synchronized void putAll(Map<? extends K, ? extends V> map) {
        super.putAll(map);
    }

    @Override
    public synchronized int size() {
        return super.size();
    }
}
