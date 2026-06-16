package com.elytradev.architecture.common.shape;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Compatibility shim for addons compiled against older ArchitectureCraft builds.
 * ArchitectureCraft 3.108 exposes EnumShape instead of the old Shape class.
 */
public final class Shape {
    public final String title;

    private Shape(String title) {
        this.title = title == null || title.isEmpty() ? "Unknown Shape" : title;
    }

    public static Shape forId(int id) {
        try {
            Class<?> enumShapeClass = Class.forName("com.elytradev.architecture.common.shape.EnumShape");
            Method forId = enumShapeClass.getMethod("forId", int.class);
            Object enumShape = forId.invoke(null, id);
            if (enumShape == null) {
                return new Shape("Unknown Shape " + id);
            }
            return new Shape(shapeName(enumShape));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return new Shape("Shape " + id);
        }
    }

    private static String shapeName(Object enumShape) {
        try {
            Method localizedName = enumShape.getClass().getMethod("getLocalizedShapeName");
            Object value = localizedName.invoke(enumShape);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        try {
            Field translationKey = enumShape.getClass().getField("translationKey");
            Object value = translationKey.get(enumShape);
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }

        return humanize(enumShape.toString());
    }

    private static String humanize(String value) {
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder(value.length());
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.length() == 0 ? value : builder.toString();
    }
}
