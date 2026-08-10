package com.l.gpom.optimization;

import java.util.LinkedHashMap;
import java.util.Map;

/** Caches vanilla-compatible GameRules parse outcomes without retaining unbounded command input. */
public final class GameRuleValueParsingOptimizations {
    private static final int MAX_ENTRIES = 64;
    private static final Map<String, ParsedValue> VALUES = new LinkedHashMap<String, ParsedValue>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ParsedValue> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private GameRuleValueParsingOptimizations() {
    }

    public static synchronized ParsedValue parse(String value) {
        ParsedValue cached = VALUES.get(value);
        if (cached != null) {
            return cached;
        }
        boolean booleanValue = Boolean.parseBoolean(value);
        int integerValue = booleanValue ? 1 : 0;
        try {
            integerValue = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        boolean hasDouble = true;
        double doubleValue = 0.0D;
        try {
            doubleValue = Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            hasDouble = false;
        }
        ParsedValue parsed = new ParsedValue(booleanValue, integerValue, hasDouble, doubleValue);
        VALUES.put(value, parsed);
        return parsed;
    }

    public static final class ParsedValue {
        public final boolean booleanValue;
        public final int integerValue;
        public final boolean hasDouble;
        public final double doubleValue;

        private ParsedValue(boolean booleanValue, int integerValue, boolean hasDouble, double doubleValue) {
            this.booleanValue = booleanValue;
            this.integerValue = integerValue;
            this.hasDouble = hasDouble;
            this.doubleValue = doubleValue;
        }
    }
}
