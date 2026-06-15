package com.l.gpom.compat.journeymap;

import com.l.gpom.GPOM;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public final class JourneyMapWaypointDimensionDropup {
    private static final int ROW_HEIGHT = 14;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int BORDER = 2;
    private static final int PANEL_BG = 0xF0181B22;
    private static final int PANEL_BORDER = 0xFF5A6474;
    private static final int ROW_BG = 0xE0272C36;
    private static final int ROW_HOVER = 0xF03B4658;
    private static final int ROW_SELECTED = 0xF0566C8E;
    private static final int TEXT = 0xFFE8EDF5;
    private static final int TEXT_DIM = 0xFFB8C0CC;
    private static final int SCROLL_TRACK = 0x803A4352;
    private static final int SCROLL_THUMB = 0xFFB5C0D1;
    private static final Set<String> FAILURE_LOG_KEYS = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final ConcurrentHashMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();
    private static final ConcurrentHashMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    private static final Map<Object, DropupState> STATES = Collections.synchronizedMap(new WeakHashMap<Object, DropupState>());

    private JourneyMapWaypointDimensionDropup() {
    }

    public static void draw(Object screen, int mouseX, int mouseY) {
        if (!enabled() || !isWaypointManager(screen)) {
            return;
        }
        DropupState state = state(screen);
        if (!state.open) {
            return;
        }
        try {
            Object button = dimensionButton(screen);
            if (button == null) {
                close(state);
                return;
            }
            Object fontRenderer = minecraftFontRenderer();
            if (fontRenderer == null) {
                return;
            }
            Layout layout = layout(screen, button, fontRenderer, state.scroll);
            state.scroll = layout.scroll;
            drawLayout(layout, fontRenderer, mouseX, mouseY);
        } catch (Throwable throwable) {
            close(state);
            logFailure("draw", throwable);
        }
    }

    public static boolean mouseInput(Object screen) {
        if (!enabled() || !isWaypointManager(screen)) {
            return false;
        }
        DropupState state = state(screen);
        int mouseX = mouseX(screen);
        int mouseY = mouseY(screen);
        int wheel = Mouse.getEventDWheel();
        int mouseButton = Mouse.getEventButton();
        try {
            Object button = dimensionButton(screen);
            if (button == null) {
                close(state);
                return false;
            }
            if (wheel != 0 && state.open) {
                Object fontRenderer = minecraftFontRenderer();
                Layout layout = layout(screen, button, fontRenderer, state.scroll);
                if (!layout.contains(mouseX, mouseY)) {
                    return false;
                }
                state.scroll = clamp(layout.scroll + (wheel < 0 ? 1 : -1), 0, layout.maxScroll());
                return true;
            }
            if (mouseButton != 0 || !Mouse.getEventButtonState()) {
                return false;
            }
            if (state.open) {
                Object fontRenderer = minecraftFontRenderer();
                Layout layout = layout(screen, button, fontRenderer, state.scroll);
                state.scroll = layout.scroll;
                if (layout.contains(mouseX, mouseY)) {
                    int index = layout.entryIndexAt(mouseY);
                    if (index >= 0 && index < layout.entries.size()) {
                        selectEntry(screen, button, state, layout.entries.get(index));
                    }
                    return true;
                }
                if (buttonContains(button, mouseX, mouseY)) {
                    close(state);
                    return true;
                }
                close(state);
                return false;
            }
            if (buttonContains(button, mouseX, mouseY)) {
                open(screen, button, state);
                return true;
            }
        } catch (Throwable throwable) {
            close(state);
            logFailure("mouseInput", throwable);
        }
        return false;
    }

    public static boolean actionPerformed(Object screen, GuiButton pressedButton) {
        if (!enabled() || !isWaypointManager(screen) || pressedButton == null) {
            return false;
        }
        DropupState state = state(screen);
        try {
            Object button = dimensionButton(screen);
            if (pressedButton == button) {
                if (state.open) {
                    close(state);
                } else {
                    open(screen, button, state);
                }
                return true;
            }
        } catch (Throwable throwable) {
            close(state);
            logFailure("actionPerformed", throwable);
        }
        return false;
    }

    public static boolean keyboardInput(Object screen) {
        if (!enabled() || !isWaypointManager(screen)) {
            return false;
        }
        DropupState state = state(screen);
        if (!state.open || !Keyboard.getEventKeyState() || Keyboard.getEventKey() != 1) {
            return false;
        }
        close(state);
        return true;
    }

    private static void open(Object waypointManager, Object button, DropupState state) throws ReflectiveOperationException {
        Object fontRenderer = minecraftFontRenderer();
        Layout layout = layout(waypointManager, button, fontRenderer, state.scroll);
        state.scroll = layout.scroll;
        state.open = true;
    }

    private static void close(DropupState state) {
        state.open = false;
        state.scroll = 0;
    }

    private static void selectEntry(Object waypointManager, Object button, DropupState state, Entry entry) throws ReflectiveOperationException {
        setStaticField(button.getClass(), entry.provider, "currentWorldProvider");
        invokeNoArg(button, "updateLabel");
        invokeNoArg(waypointManager, "updateItems");
        clearButtonList(waypointManager);
        close(state);
    }

    private static Layout layout(Object waypointManager, Object button, Object fontRenderer, int requestedScroll) throws ReflectiveOperationException {
        List<Entry> entries = entries(button);
        int visibleRows = Math.min(MAX_VISIBLE_ROWS, Math.max(1, entries.size()));
        int maxScroll = Math.max(0, entries.size() - visibleRows);
        int scroll = clamp(requestedScroll, 0, maxScroll);
        int buttonX = buttonInt(button, "field_146128_h", "x", "xPosition");
        int buttonY = buttonInt(button, "field_146129_i", "y", "yPosition");
        int buttonWidth = Math.max(40, buttonInt(button, "field_146120_f", "width"));
        int screenWidth = Math.max(buttonX + buttonWidth + 4, screenInt(waypointManager, "field_146294_l", "width"));
        int width = Math.max(buttonWidth, widestEntry(fontRenderer, entries) + 22);
        width = Math.min(width, Math.max(80, screenWidth - 8));
        int height = visibleRows * ROW_HEIGHT + BORDER * 2;
        int x = clamp(buttonX, 4, Math.max(4, screenWidth - width - 4));
        int y = Math.max(4, buttonY - height - 2);
        return new Layout(entries, x, y, width, height, visibleRows, scroll);
    }

    private static void drawLayout(Layout layout, Object fontRenderer, int mouseX, int mouseY) throws ReflectiveOperationException {
        drawRect(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, PANEL_BG);
        drawRect(layout.x, layout.y, layout.x + layout.width, layout.y + 1, PANEL_BORDER);
        drawRect(layout.x, layout.y + layout.height - 1, layout.x + layout.width, layout.y + layout.height, PANEL_BORDER);
        drawRect(layout.x, layout.y, layout.x + 1, layout.y + layout.height, PANEL_BORDER);
        drawRect(layout.x + layout.width - 1, layout.y, layout.x + layout.width, layout.y + layout.height, PANEL_BORDER);

        int rowRight = layout.x + layout.width - (layout.maxScroll() > 0 ? 7 : 1);
        for (int row = 0; row < layout.visibleRows; row++) {
            int entryIndex = layout.scroll + row;
            if (entryIndex >= layout.entries.size()) {
                break;
            }
            Entry entry = layout.entries.get(entryIndex);
            int rowY = layout.y + BORDER + row * ROW_HEIGHT;
            boolean hovered = mouseX >= layout.x + 1 && mouseX < rowRight && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            int color = entry.selected ? ROW_SELECTED : (hovered ? ROW_HOVER : ROW_BG);
            drawRect(layout.x + 1, rowY, rowRight, rowY + ROW_HEIGHT, color);
            drawString(fontRenderer, entry.label, layout.x + 6, rowY + 3, entry.selected ? TEXT : TEXT_DIM);
        }

        if (layout.maxScroll() > 0) {
            int trackX = layout.x + layout.width - 6;
            int trackY = layout.y + BORDER;
            int trackHeight = layout.visibleRows * ROW_HEIGHT;
            drawRect(trackX, trackY, trackX + 4, trackY + trackHeight, SCROLL_TRACK);
            int thumbHeight = Math.max(8, trackHeight * layout.visibleRows / layout.entries.size());
            int thumbY = trackY + (trackHeight - thumbHeight) * layout.scroll / layout.maxScroll();
            drawRect(trackX, thumbY, trackX + 4, thumbY + thumbHeight, SCROLL_THUMB);
        }
    }

    private static List<Entry> entries(Object button) throws ReflectiveOperationException {
        Object current = staticField(button.getClass(), "currentWorldProvider");
        List<Entry> entries = new ArrayList<Entry>();
        entries.add(new Entry(null, "All Dimensions", sameProvider(null, current)));
        Object providers = fieldValue(button, "dimensionProviders");
        if (providers instanceof Iterable) {
            for (Object provider : (Iterable<?>) providers) {
                if (provider != null) {
                    entries.add(new Entry(provider, providerLabel(provider), sameProvider(provider, current)));
                }
            }
        }
        return entries;
    }

    private static String providerLabel(Object provider) {
        String name = stringValue(invokeNullableStatic("journeymap.client.data.WorldData", "getSafeDimensionName", provider));
        if (name == null || name.trim().isEmpty()) {
            name = stringValue(invokeNullable(provider, "getName"));
        }
        if (name == null || name.trim().isEmpty()) {
            name = "Dimension";
        }
        Integer dimension = dimension(provider);
        if (dimension == null) {
            return name;
        }
        return name + " [" + dimension.intValue() + "]";
    }

    private static boolean sameProvider(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        Integer leftDimension = dimension(left);
        Integer rightDimension = dimension(right);
        return leftDimension != null && leftDimension.equals(rightDimension);
    }

    private static Integer dimension(Object provider) {
        Object value = invokeNullable(provider, "getDimension");
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private static Object dimensionButton(Object waypointManager) throws ReflectiveOperationException {
        return fieldValue(waypointManager, "buttonDimensions");
    }

    private static boolean buttonContains(Object button, int mouseX, int mouseY) throws ReflectiveOperationException {
        int x = buttonInt(button, "field_146128_h", "x", "xPosition");
        int y = buttonInt(button, "field_146129_i", "y", "yPosition");
        int width = buttonInt(button, "field_146120_f", "width");
        int height = buttonInt(button, "field_146121_g", "height");
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static int widestEntry(Object fontRenderer, List<Entry> entries) {
        int width = 0;
        for (Entry entry : entries) {
            width = Math.max(width, stringWidth(fontRenderer, entry.label));
        }
        return width;
    }

    private static int stringWidth(Object fontRenderer, String text) {
        Object value = invokeNullable(fontRenderer, "func_78256_a", "getStringWidth", text == null ? "" : text);
        return value instanceof Number ? ((Number) value).intValue() : (text == null ? 0 : text.length() * 6);
    }

    private static void drawString(Object fontRenderer, String text, int x, int y, int color) throws ReflectiveOperationException {
        Method method = findMethod(fontRenderer.getClass(), new Class<?>[]{String.class, float.class, float.class, int.class},
                "func_175063_a", "drawStringWithShadow");
        method.invoke(fontRenderer, text, Float.valueOf(x), Float.valueOf(y), Integer.valueOf(color));
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) throws ReflectiveOperationException {
        Class<?> gui = Class.forName("net.minecraft.client.gui.Gui", false, JourneyMapWaypointDimensionDropup.class.getClassLoader());
        Method method = findMethod(gui, new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                "func_73734_a", "drawRect");
        method.invoke(null, Integer.valueOf(left), Integer.valueOf(top), Integer.valueOf(right), Integer.valueOf(bottom), Integer.valueOf(color));
    }

    private static Object minecraftFontRenderer() throws ReflectiveOperationException {
        Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft", false, JourneyMapWaypointDimensionDropup.class.getClassLoader());
        Method getMinecraft = findMethod(minecraftClass, new Class<?>[0], "func_71410_x", "getMinecraft");
        Object minecraft = getMinecraft.invoke(null);
        return minecraft == null ? null : fieldValue(minecraft, "field_71466_p", "fontRenderer");
    }

    private static void clearButtonList(Object screen) throws ReflectiveOperationException {
        Object buttonList = fieldValue(screen, "field_146292_n", "buttonList");
        if (buttonList instanceof List) {
            ((List<?>) buttonList).clear();
        }
    }

    private static int buttonInt(Object target, String... fieldNames) throws ReflectiveOperationException {
        Object value = fieldValue(target, fieldNames);
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static int screenInt(Object target, String... fieldNames) {
        try {
            Object value = fieldValue(target, fieldNames);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private static int mouseX(Object screen) {
        int width = Math.max(1, screenInt(screen, "field_146294_l", "width"));
        int displayWidth = Math.max(1, ClientAccess.displayWidth(ClientAccess.minecraft()));
        return Mouse.getEventX() * width / displayWidth;
    }

    private static int mouseY(Object screen) {
        int height = Math.max(1, screenInt(screen, "field_146295_m", "height"));
        int displayHeight = Math.max(1, ClientAccess.displayHeight(ClientAccess.minecraft()));
        return height - Mouse.getEventY() * height / displayHeight - 1;
    }

    private static boolean isWaypointManager(Object screen) {
        return screen != null && "journeymap.client.ui.waypoint.WaypointManager".equals(screen.getClass().getName());
    }

    private static DropupState state(Object screen) {
        DropupState state = STATES.get(screen);
        if (state == null) {
            state = new DropupState();
            STATES.put(screen, state);
        }
        return state;
    }

    private static Object staticField(Class<?> type, String fieldName) throws ReflectiveOperationException {
        Field field = findField(type, fieldName);
        return field.get(null);
    }

    private static void setStaticField(Class<?> type, Object value, String fieldName) throws ReflectiveOperationException {
        Field field = findField(type, fieldName);
        field.set(null, value);
    }

    private static Object fieldValue(Object target, String... fieldNames) throws ReflectiveOperationException {
        Field field = findField(target.getClass(), fieldNames);
        return field.get(target);
    }

    private static void invokeNoArg(Object target, String... methodNames) throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), new Class<?>[0], methodNames);
        method.invoke(target);
    }

    private static Object invokeNullable(Object target, String... methodNamesAndMaybeArg) {
        if (target == null || methodNamesAndMaybeArg.length == 0) {
            return null;
        }
        try {
            String[] names = new String[methodNamesAndMaybeArg.length];
            System.arraycopy(methodNamesAndMaybeArg, 0, names, 0, methodNamesAndMaybeArg.length);
            Method method = findMethod(target.getClass(), new Class<?>[0], names);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeNullable(Object target, String firstName, String secondName, Object arg) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), new Class<?>[]{arg.getClass()}, firstName, secondName);
            return method.invoke(target, arg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeNullableStatic(String className, String methodName, Object arg) {
        if (arg == null) {
            return null;
        }
        try {
            Class<?> type = Class.forName(className, false, JourneyMapWaypointDimensionDropup.class.getClassLoader());
            Method method = findCompatibleMethod(type, methodName, arg);
            return method == null ? null : method.invoke(null, arg);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        String key = type.getName() + "#field#" + join(names);
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    FIELD_CACHE.putIfAbsent(key, field);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchFieldException(type.getName() + "#" + join(names));
    }

    private static Method findMethod(Class<?> type, Class<?>[] params, String... names) throws NoSuchMethodException {
        String key = type.getName() + "#method#" + join(names) + "#" + params.length;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, params);
                    method.setAccessible(true);
                    METHOD_CACHE.putIfAbsent(key, method);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "#" + join(names));
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Object arg) {
        Class<?> current = type;
        while (current != null) {
            Method[] methods = current.getDeclaredMethods();
            for (Method method : methods) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getName().equals(name)
                        && Modifier.isStatic(method.getModifiers())
                        && params.length == 1
                        && params[0].isAssignableFrom(arg.getClass())) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static String join(String... names) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) {
                builder.append('|');
            }
            builder.append(names[i]);
        }
        return builder.toString();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean enabled() {
        return GpomEarlyConfig.journeyMapWaypointDimensionDropupEnabled();
    }

    private static void logFailure(String key, Throwable throwable) {
        if (FAILURE_LOG_KEYS.add(key)) {
            GPOM.LOGGER.warn("[JourneyMapDimensionDropup] {} failed: {}", key, throwable.toString());
        }
    }

    private static final class DropupState {
        private boolean open;
        private int scroll;
    }

    private static final class Entry {
        private final Object provider;
        private final String label;
        private final boolean selected;

        private Entry(Object provider, String label, boolean selected) {
            this.provider = provider;
            this.label = label;
            this.selected = selected;
        }
    }

    private static final class Layout {
        private final List<Entry> entries;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final int visibleRows;
        private final int scroll;

        private Layout(List<Entry> entries, int x, int y, int width, int height, int visibleRows, int scroll) {
            this.entries = entries;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.visibleRows = visibleRows;
            this.scroll = scroll;
        }

        private boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        }

        private int entryIndexAt(int mouseY) {
            int row = (mouseY - y - BORDER) / ROW_HEIGHT;
            if (row < 0 || row >= visibleRows) {
                return -1;
            }
            return scroll + row;
        }

        private int maxScroll() {
            return Math.max(0, entries.size() - visibleRows);
        }
    }
}
