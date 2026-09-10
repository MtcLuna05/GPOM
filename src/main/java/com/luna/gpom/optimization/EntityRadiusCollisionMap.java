package com.luna.gpom.optimization;

import it.unimi.dsi.fastutil.objects.Reference2DoubleOpenHashMap;
import net.minecraft.entity.item.EntityItem;

/** Keeps UniversalTweaks' configured identity map while avoiding its dominant vanilla item lookup. */
public final class EntityRadiusCollisionMap extends Reference2DoubleOpenHashMap<Class<?>> {
    @Override
    public double getDouble(Object key) {
        return key == EntityItem.class ? 2.0D : super.getDouble(key);
    }
}
