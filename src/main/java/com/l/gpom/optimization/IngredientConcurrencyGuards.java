package com.l.gpom.optimization;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public final class IngredientConcurrencyGuards {
    private IngredientConcurrencyGuards() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Set synchronizedIngredientSet(Set instances) {
        return Collections.synchronizedSet(instances);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Iterator ingredientInstanceIterator(Set instances) {
        synchronized (instances) {
            return new ArrayList(instances).iterator();
        }
    }
}
