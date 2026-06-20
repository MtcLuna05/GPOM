package com.l.gpom.compat.hei;

import com.l.gpom.GPOM;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class ThaumcraftHeiResearchClickBridge {
    private static final String MISSING_RESEARCH_HEADER = "Missing research:";

    private static volatile Field recipeLayoutsField;
    private static volatile Field recipeWrapperField;
    private static volatile Method getPosXMethod;
    private static volatile Method getPosYMethod;
    private static volatile Method getTooltipStringsMethod;
    private static volatile Method getResearchMethod;
    private static volatile Method getResearchEntryMethod;
    private static volatile Method getCategoryMethod;
    private static volatile Method getMinecraftMethod;
    private static volatile Method displayGuiScreenMethod;
    private static volatile Constructor<?> researchBrowserConstructor;
    private static volatile Field selectedCategoryField;

    private ThaumcraftHeiResearchClickBridge() {
    }

    public static boolean handleClick(Object recipesGui, int mouseX, int mouseY, int mouseButton) {
        if (recipesGui == null || mouseButton != 0) {
            return false;
        }
        try {
            List<?> layouts = recipeLayouts(recipesGui);
            if (layouts == null || layouts.isEmpty()) {
                return false;
            }
            for (Object layout : layouts) {
                if (layout == null) {
                    continue;
                }
                int localX = mouseX - recipePosX(layout);
                int localY = mouseY - recipePosY(layout);
                Object wrapper = recipeWrapper(layout);
                if (!isMissingResearchClick(wrapper, localX, localY)) {
                    continue;
                }
                String category = firstResearchCategory(wrapper);
                if (category == null || category.isEmpty()) {
                    return false;
                }
                openResearchBrowser(category);
                GPOM.LOGGER.info("[GPOM HEI QoL] Opened Thaumcraft research category '{}' from HEI required research click", category);
                return true;
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM HEI QoL] Could not handle Thaumcraft required research click", throwable);
        }
        return false;
    }

    private static List<?> recipeLayouts(Object recipesGui) throws ReflectiveOperationException {
        Field field = recipeLayoutsField;
        if (field == null) {
            field = findField(recipesGui.getClass(), "recipeLayouts");
            field.setAccessible(true);
            recipeLayoutsField = field;
        }
        Object value = field.get(recipesGui);
        return value instanceof List ? (List<?>) value : null;
    }

    private static Object recipeWrapper(Object layout) throws ReflectiveOperationException {
        Field field = recipeWrapperField;
        if (field == null) {
            field = findField(layout.getClass(), "recipeWrapper");
            field.setAccessible(true);
            recipeWrapperField = field;
        }
        return field.get(layout);
    }

    private static int recipePosX(Object layout) throws ReflectiveOperationException {
        Method method = getPosXMethod;
        if (method == null) {
            method = findMethod(layout.getClass(), new Class<?>[0], "getPosX");
            method.setAccessible(true);
            getPosXMethod = method;
        }
        Object value = method.invoke(layout);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int recipePosY(Object layout) throws ReflectiveOperationException {
        Method method = getPosYMethod;
        if (method == null) {
            method = findMethod(layout.getClass(), new Class<?>[0], "getPosY");
            method.setAccessible(true);
            getPosYMethod = method;
        }
        Object value = method.invoke(layout);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static boolean isMissingResearchClick(Object wrapper, int localX, int localY) throws ReflectiveOperationException {
        if (wrapper == null || !hasResearchMethod(wrapper)) {
            return false;
        }
        Method tooltipMethod = getTooltipStringsMethod;
        if (tooltipMethod == null || !tooltipMethod.getDeclaringClass().isAssignableFrom(wrapper.getClass())) {
            tooltipMethod = findMethod(wrapper.getClass(), new Class<?>[]{int.class, int.class}, "getTooltipStrings");
            tooltipMethod.setAccessible(true);
            getTooltipStringsMethod = tooltipMethod;
        }
        Object tooltips = tooltipMethod.invoke(wrapper, localX, localY);
        if (!(tooltips instanceof List)) {
            return false;
        }
        for (Object tooltip : (List<?>) tooltips) {
            if (tooltip != null && stripFormatting(String.valueOf(tooltip)).contains(MISSING_RESEARCH_HEADER)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasResearchMethod(Object wrapper) {
        try {
            Method method = getResearchMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(wrapper.getClass())) {
                method = findMethod(wrapper.getClass(), new Class<?>[0], "getResearch");
                method.setAccessible(true);
                getResearchMethod = method;
            }
            return method.getReturnType().isArray();
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static String firstResearchCategory(Object wrapper) throws ReflectiveOperationException {
        Method method = getResearchMethod;
        if (method == null || !method.getDeclaringClass().isAssignableFrom(wrapper.getClass())) {
            method = findMethod(wrapper.getClass(), new Class<?>[0], "getResearch");
            method.setAccessible(true);
            getResearchMethod = method;
        }
        Object value = method.invoke(wrapper);
        if (!(value instanceof String[])) {
            return null;
        }
        for (String expression : (String[]) value) {
            String category = firstResearchCategory(expression);
            if (category != null) {
                return category;
            }
        }
        return null;
    }

    private static String firstResearchCategory(String expression) throws ReflectiveOperationException {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        String[] parts = expression.split("&&");
        for (String part : parts) {
            String key = normalizeResearchKey(part);
            if (key.isEmpty()) {
                continue;
            }
            Object entry = researchEntry(key);
            if (entry == null) {
                continue;
            }
            Method method = getCategoryMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(entry.getClass())) {
                method = findMethod(entry.getClass(), new Class<?>[0], "getCategory");
                method.setAccessible(true);
                getCategoryMethod = method;
            }
            Object category = method.invoke(entry);
            if (category != null) {
                return String.valueOf(category);
            }
        }
        return null;
    }

    private static Object researchEntry(String key) throws ReflectiveOperationException {
        Method method = getResearchEntryMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.api.research.ResearchCategories")
                    .getMethod("getResearch", String.class);
            method.setAccessible(true);
            getResearchEntryMethod = method;
        }
        return method.invoke(null, key);
    }

    private static void openResearchBrowser(String category) throws ReflectiveOperationException {
        Class<?> browserClass = Class.forName("thaumcraft.client.gui.GuiResearchBrowser");
        Field selectedCategory = selectedCategoryField;
        if (selectedCategory == null) {
            selectedCategory = findField(browserClass, "selectedCategory");
            selectedCategory.setAccessible(true);
            selectedCategoryField = selectedCategory;
        }
        selectedCategory.set(null, category);

        Constructor<?> constructor = researchBrowserConstructor;
        if (constructor == null) {
            constructor = browserClass.getConstructor();
            constructor.setAccessible(true);
            researchBrowserConstructor = constructor;
        }
        Object screen = constructor.newInstance();
        Object minecraft = minecraft();
        Method display = displayGuiScreenMethod;
        if (display == null) {
            display = findMethod(minecraft.getClass(), new Class<?>[]{Class.forName("net.minecraft.client.gui.GuiScreen")},
                    "func_147108_a", "displayGuiScreen");
            display.setAccessible(true);
            displayGuiScreenMethod = display;
        }
        display.invoke(minecraft, screen);
    }

    private static Object minecraft() throws ReflectiveOperationException {
        Method method = getMinecraftMethod;
        if (method == null) {
            method = findMethod(Class.forName("net.minecraft.client.Minecraft"), new Class<?>[0],
                    "func_71410_x", "getMinecraft");
            method.setAccessible(true);
            getMinecraftMethod = method;
        }
        return method.invoke(null);
    }

    private static String normalizeResearchKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        int at = key.indexOf('@');
        if (at >= 0) {
            key = key.substring(0, at);
        }
        while (key.startsWith("!")) {
            key = key.substring(1);
        }
        return key.trim();
    }

    private static String stripFormatting(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        boolean formatting = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (formatting) {
                formatting = false;
                continue;
            }
            if (c == '\u00a7') {
                formatting = true;
                continue;
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameters, String... names) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredMethod(name, parameters);
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }
}
