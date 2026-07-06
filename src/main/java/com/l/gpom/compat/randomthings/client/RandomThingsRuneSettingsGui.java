package com.l.gpom.compat.randomthings.client;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.GPOM;
import com.l.gpom.compat.randomthings.RandomThingsRuneNetwork;
import com.l.gpom.compat.randomthings.RandomThingsRuneSettings;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public final class RandomThingsRuneSettingsGui extends GuiScreen {
    private static volatile boolean displayFailureLogged;
    private static volatile int nextScreenId;
    private static volatile Field mcField;
    private static volatile Field widthField;
    private static volatile Field heightField;
    private static volatile Field buttonListField;
    private static volatile Field fontRendererField;
    private static volatile Field buttonIdField;
    private static volatile Field buttonXField;
    private static volatile Field buttonYField;
    private static volatile Field buttonWidthField;
    private static volatile Field buttonHeightField;
    private static volatile Field buttonTextField;
    private static volatile Field buttonEnabledField;
    private static volatile Field buttonVisibleField;
    private final List<GuiButton> fallbackButtons = new ArrayList<>();
    private int rune;
    private boolean autoConnect;
    private int resolution;
    private int brush;
    private int visualScale;
    private int visualPadding;
    private boolean replaceOccupied;
    private final int screenId;
    private int drawCalls;
    private boolean initLogged;

    public static boolean open(int rune) {
        Minecraft minecraft = ClientAccess.minecraft();
        probeStatic("open requested rune={} minecraft={} thread={}", RandomThingsRuneSettings.clampRune(rune),
                minecraft == null ? "null" : minecraft.getClass().getName(), Thread.currentThread().getName());
        if (minecraft == null) {
            return false;
        }
        if (ClientAccess.isMinecraftThread(minecraft)) {
            return display(minecraft, rune);
        }
        return ClientAccess.schedule(minecraft, () -> display(minecraft, rune));
    }

    private RandomThingsRuneSettingsGui(int rune) {
        this.screenId = ++nextScreenId;
        this.rune = RandomThingsRuneSettings.clampRune(rune);
        loadRune();
        probe("constructed rune={} settings autoConnect={} resolution={} brush={} visualScale={} visualPadding={} replaceOccupied={}",
                this.rune, autoConnect, resolution, brush, visualScale, visualPadding, replaceOccupied);
    }

    private static boolean display(Minecraft minecraft, int rune) {
        RandomThingsRuneSettingsGui screen = new RandomThingsRuneSettingsGui(rune);
        probeStatic("display start screenId={} currentBefore={}", screen.screenId, currentScreenName(minecraft));
        if (!ClientAccess.displayGuiScreen(minecraft, screen)) {
            logDisplayFailure("displayGuiScreen returned false", null);
            return false;
        }
        Object current = ClientAccess.currentScreen(minecraft);
        boolean opened = current == screen;
        probeStatic("display result screenId={} opened={} currentAfter={}", screen.screenId, opened, currentName(current));
        if (!opened) {
            logDisplayFailure("current screen after display was " + (current == null ? "null" : current.getClass().getName()), null);
        }
        return opened;
    }

    private void loadRune() {
        RandomThingsRuneSettings.RuneSettings settings = RandomThingsRuneSettings.client(this.rune);
        this.autoConnect = settings.autoConnect;
        this.resolution = settings.resolution;
        this.brush = settings.brush;
        this.visualScale = settings.visualScale;
        this.visualPadding = settings.visualPadding;
        this.replaceOccupied = settings.replaceOccupied;
    }

    @Override
    public void initGui() {
        initGuiBody();
    }

    public void func_73866_w_() {
        initGuiBody();
    }

    public void func_146280_a(Minecraft minecraft, int width, int height) {
        setScreenField(mcField, "field_146297_k", "mc", minecraft);
        setScreenField(widthField, "field_146294_l", "width", width);
        setScreenField(heightField, "field_146295_m", "height", height);
        setScreenField(fontRendererField, "field_146289_q", "fontRenderer", ClientAccess.fontRenderer(minecraft));
        func_73866_w_();
    }

    private void initGuiBody() {
        List<GuiButton> buttons = buttons();
        buttons.clear();
        Layout layout = layout();
        int y = layout.firstRowY;
        buttons.add(new GuiButton(0, layout.contentLeft, y, layout.halfWidth, layout.buttonHeight, "< Rune"));
        buttons.add(new GuiButton(1, layout.secondColumnX, y, layout.halfWidth, layout.buttonHeight, "Rune >"));
        y += layout.rowStep;
        buttons.add(new GuiButton(2, layout.contentLeft, y, layout.contentWidth, layout.buttonHeight, ""));
        y += layout.sectionStep;
        buttons.add(new GuiButton(3, layout.minusX, y, layout.adjustButtonWidth, layout.buttonHeight, "-"));
        buttons.add(new GuiButton(4, layout.plusX, y, layout.adjustButtonWidth, layout.buttonHeight, "+"));
        y += layout.rowStep;
        buttons.add(new GuiButton(5, layout.minusX, y, layout.adjustButtonWidth, layout.buttonHeight, "-"));
        buttons.add(new GuiButton(6, layout.plusX, y, layout.adjustButtonWidth, layout.buttonHeight, "+"));
        y += layout.rowStep;
        buttons.add(new GuiButton(7, layout.minusX, y, layout.adjustButtonWidth, layout.buttonHeight, "-"));
        buttons.add(new GuiButton(8, layout.plusX, y, layout.adjustButtonWidth, layout.buttonHeight, "+"));
        y += layout.rowStep;
        buttons.add(new GuiButton(9, layout.minusX, y, layout.adjustButtonWidth, layout.buttonHeight, "-"));
        buttons.add(new GuiButton(10, layout.plusX, y, layout.adjustButtonWidth, layout.buttonHeight, "+"));
        y += layout.sectionStep;
        buttons.add(new GuiButton(11, layout.contentLeft, y, layout.halfWidth, layout.buttonHeight, ""));
        buttons.add(new GuiButton(12, layout.secondColumnX, y, layout.halfWidth, layout.buttonHeight, ""));
        y += layout.footerStep;
        buttons.add(new GuiButton(13, layout.contentLeft, y, layout.halfWidth, layout.buttonHeight, "Save"));
        buttons.add(new GuiButton(14, layout.secondColumnX, y, layout.halfWidth, layout.buttonHeight, "Cancel"));
        updateLabels();
        logInitProbe(layout.center);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (buttonId(button)) {
            case 0:
                saveCurrent();
                this.rune = this.rune == 0 ? RandomThingsRuneSettings.RUNE_COUNT - 1 : this.rune - 1;
                loadRune();
                break;
            case 1:
                saveCurrent();
                this.rune = (this.rune + 1) % RandomThingsRuneSettings.RUNE_COUNT;
                loadRune();
                break;
            case 2:
                this.autoConnect = !this.autoConnect;
                break;
            case 3:
                this.resolution = RandomThingsRuneSettings.previousResolution(this.resolution);
                break;
            case 4:
                this.resolution = RandomThingsRuneSettings.nextResolution(this.resolution);
                break;
            case 5:
                this.brush = RandomThingsRuneSettings.clamp(this.brush - 1, 1, 9);
                break;
            case 6:
                this.brush = RandomThingsRuneSettings.clamp(this.brush + 1, 1, 9);
                break;
            case 7:
                this.visualScale = RandomThingsRuneSettings.clamp(this.visualScale - 5, 10, 100);
                break;
            case 8:
                this.visualScale = RandomThingsRuneSettings.clamp(this.visualScale + 5, 10, 100);
                break;
            case 9:
                this.visualPadding = RandomThingsRuneSettings.clamp(this.visualPadding - 5, 0, 45);
                break;
            case 10:
                this.visualPadding = RandomThingsRuneSettings.clamp(this.visualPadding + 5, 0, 45);
                break;
            case 11:
                this.replaceOccupied = !this.replaceOccupied;
                break;
            case 12:
                resetCurrent();
                break;
            case 13:
                saveCurrent();
                ClientAccess.displayGuiScreen(screenMinecraft(), null);
                return;
            case 14:
                ClientAccess.displayGuiScreen(screenMinecraft(), null);
                return;
            default:
                break;
        }
        updateLabels();
    }

    protected void func_146284_a(GuiButton button) throws IOException {
        actionPerformed(button);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawScreenBody(mouseX, mouseY, partialTicks);
    }

    public void func_73863_a(int mouseX, int mouseY, float partialTicks) {
        drawScreenBody(mouseX, mouseY, partialTicks);
    }

    private void drawScreenBody(int mouseX, int mouseY, float partialTicks) {
        this.drawCalls++;
        logDrawProbe(mouseX, mouseY, partialTicks);
        drawFallbackPanel();
        drawFallbackText();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    public boolean func_73868_f() {
        return false;
    }

    private void updateLabels() {
        boolean compact = layout().compact;
        for (GuiButton button : buttons()) {
            int id = buttonId(button);
            if (id == 2) {
                setButtonText(button, (compact ? "Auto: " : "Auto-connect: ") + (autoConnect ? "ON" : "OFF"));
            } else if (id == 11) {
                setButtonText(button, "Replace: " + (replaceOccupied ? "ON" : "OFF"));
            } else if (id == 12) {
                setButtonText(button, compact ? "Reset" : "Reset Rune");
            }
        }
    }

    private void resetCurrent() {
        RandomThingsRuneSettings.RuneSettings defaults = RandomThingsRuneSettings.defaultSetting();
        this.autoConnect = defaults.autoConnect;
        this.resolution = defaults.resolution;
        this.brush = defaults.brush;
        this.visualScale = defaults.visualScale;
        this.visualPadding = defaults.visualPadding;
        this.replaceOccupied = defaults.replaceOccupied;
    }

    private void saveCurrent() {
        RandomThingsRuneSettings.RuneSettings settings = new RandomThingsRuneSettings.RuneSettings(autoConnect, resolution, brush, visualScale, visualPadding, replaceOccupied);
        RandomThingsRuneSettings.updateClient(this.rune, settings);
        RandomThingsRuneNetwork.sendClientSettingToServer(this.rune, settings);
        ClientAccess.reloadRenderers(screenMinecraft());
    }

    private static String runeName(int rune) {
        String key = "item.runeDust." + runeNameKey(rune) + ".name";
        String localized = ClientAccess.i18nFormat(key);
        return key.equals(localized) ? Integer.toString(rune) : localized;
    }

    private static String runeNameKey(int rune) {
        String[] names = {"white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        return names[RandomThingsRuneSettings.clampRune(rune)];
    }

    private void drawFallbackPanel() {
        Layout layout = layout();
        ClientAccess.drawRect(layout.panelLeft, layout.panelTop, layout.panelRight, layout.panelBottom, 0xCC080A0D);
        ClientAccess.drawRect(layout.panelLeft + 1, layout.panelTop + 1, layout.panelRight - 1, layout.panelBottom - 1, 0xDD12171C);
        for (GuiButton button : buttons()) {
            int x = buttonX(button);
            int y = buttonY(button);
            int buttonWidth = buttonWidth(button);
            int buttonHeight = buttonHeight(button);
            int outline = buttonEnabled(button) ? 0xAAFFFFFF : 0x66555555;
            int fill = buttonEnabled(button) ? 0x66000000 : 0x44000000;
            ClientAccess.drawRect(x, y, x + buttonWidth, y + buttonHeight, outline);
            ClientAccess.drawRect(x + 1, y + 1, x + buttonWidth - 1, y + buttonHeight - 1, fill);
        }
    }

    private void drawFallbackText() {
        FontRenderer font = screenFontRenderer();
        Layout layout = layout();
        drawCenteredFallback(font, "Improved Runic Dust", layout.center, layout.panelTop + layout.titleY, 0xFFFFFF, layout.contentWidth);
        drawCenteredFallback(font, "Rune " + this.rune + " - " + runeName(this.rune), layout.center, layout.panelTop + layout.subtitleY, 0xB0E0FF, layout.contentWidth);
        int y = layout.firstRowY + layout.rowStep + layout.sectionStep;
        drawSettingRow(font, "Resolution", resolution + "x" + resolution, layout, y);
        y += layout.rowStep;
        drawSettingRow(font, "Brush", brush + "x" + brush, layout, y);
        y += layout.rowStep;
        drawSettingRow(font, "Visual", visualScale + "%", layout, y);
        y += layout.rowStep;
        drawSettingRow(font, "Padding", visualPadding + "%", layout, y);
        for (GuiButton button : buttons()) {
            int maxWidth = Math.max(8, buttonWidth(button) - 6);
            drawCenteredFallback(font, buttonText(button), buttonX(button) + buttonWidth(button) / 2,
                    buttonY(button) + Math.max(2, (buttonHeight(button) - 8) / 2), 0xFFFFFF, maxWidth);
        }
    }

    private void drawSettingRow(FontRenderer font, String label, String value, Layout layout, int y) {
        if (font == null) {
            return;
        }
        int textY = y + Math.max(2, (layout.buttonHeight - 8) / 2);
        int labelMax = Math.max(32, layout.minusX - layout.contentLeft - 54);
        String labelText = trimToWidth(font, label, labelMax);
        String valueText = trimToWidth(font, value, 42);
        ClientAccess.drawStringWithShadow(font, labelText, layout.contentLeft + 4, textY, 0xE0E8F0);
        ClientAccess.drawStringWithShadow(font, valueText, layout.minusX - 50, textY, 0xB0E0FF);
    }

    private int layoutTop() {
        return layout().panelTop;
    }

    private int layoutBottom() {
        return layout().panelBottom;
    }

    private static void drawCenteredFallback(FontRenderer font, String text, int x, int y, int color) {
        drawCenteredFallback(font, text, x, y, color, Integer.MAX_VALUE);
    }

    private static void drawCenteredFallback(FontRenderer font, String text, int x, int y, int color, int maxWidth) {
        if (font == null || text == null) {
            return;
        }
        text = trimToWidth(font, text, maxWidth);
        ClientAccess.drawStringWithShadow(font, text, x - ClientAccess.stringWidth(font, text) / 2.0F, y, color);
    }

    private static String trimToWidth(FontRenderer font, String text, int maxWidth) {
        if (font == null || text == null || maxWidth == Integer.MAX_VALUE || ClientAccess.stringWidth(font, text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int suffixWidth = ClientAccess.stringWidth(font, suffix);
        int limit = Math.max(0, maxWidth - suffixWidth);
        String value = text;
        while (!value.isEmpty() && ClientAccess.stringWidth(font, value) > limit) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? suffix : value + suffix;
    }

    private Layout layout() {
        return new Layout(screenWidth(), screenHeight());
    }

    private static final class Layout {
        final boolean compact;
        final int center;
        final int panelLeft;
        final int panelRight;
        final int panelTop;
        final int panelBottom;
        final int contentLeft;
        final int contentWidth;
        final int secondColumnX;
        final int halfWidth;
        final int buttonHeight;
        final int rowStep;
        final int sectionStep;
        final int footerStep;
        final int firstRowY;
        final int adjustButtonWidth;
        final int minusX;
        final int plusX;
        final int titleY;
        final int subtitleY;

        Layout(int screenWidth, int screenHeight) {
            int safeWidth = Math.max(160, screenWidth);
            int safeHeight = Math.max(120, screenHeight);
            this.compact = safeHeight < 250 || safeWidth < 285;
            this.center = safeWidth / 2;
            int margin = compact ? 4 : 8;
            int panelWidth = Math.min(compact ? 260 : 296, Math.max(180, safeWidth - margin * 2));
            this.buttonHeight = compact ? 16 : 20;
            this.rowStep = buttonHeight + (compact ? 3 : 6);
            this.sectionStep = buttonHeight + (compact ? 6 : 8);
            this.footerStep = buttonHeight + (compact ? 5 : 10);
            int headerHeight = compact ? 28 : 36;
            int desiredHeight = headerHeight + rowStep + sectionStep + rowStep * 3 + sectionStep + footerStep + buttonHeight + (compact ? 8 : 12);
            int panelHeight = Math.min(desiredHeight, Math.max(112, safeHeight - margin * 2));
            this.panelLeft = Math.max(margin, center - panelWidth / 2);
            this.panelRight = Math.min(safeWidth - margin, panelLeft + panelWidth);
            this.panelTop = Math.max(margin, (safeHeight - panelHeight) / 2);
            this.panelBottom = Math.min(safeHeight - margin, panelTop + panelHeight);
            this.contentLeft = panelLeft + (compact ? 8 : 12);
            this.contentWidth = Math.max(120, panelRight - panelLeft - (compact ? 16 : 24));
            int columnGap = compact ? 4 : 6;
            this.halfWidth = Math.max(48, (contentWidth - columnGap) / 2);
            this.secondColumnX = contentLeft + halfWidth + columnGap;
            this.adjustButtonWidth = compact ? 32 : 42;
            this.plusX = panelRight - (compact ? 8 : 12) - adjustButtonWidth;
            this.minusX = plusX - (compact ? 4 : 6) - adjustButtonWidth;
            this.titleY = compact ? 5 : 8;
            this.subtitleY = compact ? 16 : 22;
            this.firstRowY = panelTop + headerHeight;
        }
    }

    private static void logDisplayFailure(String message, Throwable throwable) {
        if (displayFailureLogged) {
            return;
        }
        displayFailureLogged = true;
        if (throwable == null) {
            GPOM.LOGGER.warn("[GPOM RandomThings Runes] Failed to display improved runic dust screen: {}", message);
        } else {
            GPOM.LOGGER.warn("[GPOM RandomThings Runes] Failed to display improved runic dust screen: {}", message, throwable);
        }
    }

    private void logInitProbe(int center) {
        if (!probeEnabled() || initLogged) {
            return;
        }
        initLogged = true;
        GPOM.LOGGER.warn("[GPOM RandomThings GUI Probe] init screenId={} rune={} size={}x{} center={} buttons={}",
                screenId, rune, screenWidth(), screenHeight(), center, buttons().size());
        for (GuiButton button : buttons()) {
            GPOM.LOGGER.warn("[GPOM RandomThings GUI Probe] button screenId={} id={} rect={}x{}+{}+{} text='{}' enabled={} visible={}",
                    screenId, buttonId(button), buttonWidth(button), buttonHeight(button), buttonX(button), buttonY(button),
                    buttonText(button), buttonEnabled(button), buttonVisible(button));
        }
    }

    private void logDrawProbe(int mouseX, int mouseY, float partialTicks) {
        if (!probeEnabled()) {
            return;
        }
        if (drawCalls <= 10 || drawCalls % 40 == 0) {
            GPOM.LOGGER.warn("[GPOM RandomThings GUI Probe] draw screenId={} call={} size={}x{} mouse={}x{} partial={} buttons={} current={} mcThread={}",
                    screenId, drawCalls, screenWidth(), screenHeight(), mouseX, mouseY, partialTicks, buttons().size(),
                    currentScreenName(screenMinecraft()), ClientAccess.isMinecraftThread(screenMinecraft()));
        }
    }

    private void probe(String message, Object... args) {
        if (probeEnabled()) {
            GPOM.LOGGER.warn("[GPOM RandomThings GUI Probe] screenId={} " + message, prepend(screenId, args));
        }
    }

    private static void probeStatic(String message, Object... args) {
        if (probeEnabled()) {
            GPOM.LOGGER.warn("[GPOM RandomThings GUI Probe] " + message, args);
        }
    }

    private static Object[] prepend(Object first, Object[] rest) {
        Object[] values = new Object[rest.length + 1];
        values[0] = first;
        System.arraycopy(rest, 0, values, 1, rest.length);
        return values;
    }

    private static boolean probeEnabled() {
        return GpomEarlyConfig.randomThingsImprovedRunicDustGuiProbeEnabled();
    }

    private static String currentScreenName(Minecraft minecraft) {
        return currentName(ClientAccess.currentScreen(minecraft));
    }

    private static String currentName(Object screen) {
        return screen == null ? "null" : screen.getClass().getName();
    }

    @SuppressWarnings("unchecked")
    private List<GuiButton> buttons() {
        Object value = screenField(buttonListField, "field_146292_n", "buttonList");
        if (value instanceof List<?>) {
            return (List<GuiButton>) value;
        }
        return fallbackButtons;
    }

    private Minecraft screenMinecraft() {
        Object value = screenField(mcField, "field_146297_k", "mc");
        return value instanceof Minecraft ? (Minecraft) value : ClientAccess.minecraft();
    }

    private int screenWidth() {
        return intValue(screenField(widthField, "field_146294_l", "width"), 320);
    }

    private int screenHeight() {
        return intValue(screenField(heightField, "field_146295_m", "height"), 240);
    }

    private FontRenderer screenFontRenderer() {
        Object value = screenField(fontRendererField, "field_146289_q", "fontRenderer");
        if (value instanceof FontRenderer) {
            return (FontRenderer) value;
        }
        return ClientAccess.fontRenderer(screenMinecraft());
    }

    private Object screenField(Field cached, String srgName, String mcpName) {
        Field field = cached != null ? cached : findField(GuiScreen.class, srgName, mcpName);
        cacheScreenField(srgName, field);
        return getFieldValue(field, this);
    }

    private void setScreenField(Field cached, String srgName, String mcpName, Object value) {
        Field field = cached != null ? cached : findField(GuiScreen.class, srgName, mcpName);
        cacheScreenField(srgName, field);
        setFieldValue(field, this, value);
    }

    private static void cacheScreenField(String srgName, Field field) {
        if (field == null) {
            return;
        }
        if ("field_146297_k".equals(srgName)) {
            mcField = field;
        } else if ("field_146294_l".equals(srgName)) {
            widthField = field;
        } else if ("field_146295_m".equals(srgName)) {
            heightField = field;
        } else if ("field_146292_n".equals(srgName)) {
            buttonListField = field;
        } else if ("field_146289_q".equals(srgName)) {
            fontRendererField = field;
        }
    }

    private static int buttonId(GuiButton button) {
        return intValue(buttonField(button, buttonIdField, "field_146127_k", "id"), 0);
    }

    private static int buttonX(GuiButton button) {
        return intValue(buttonField(button, buttonXField, "field_146128_h", "x"), 0);
    }

    private static int buttonY(GuiButton button) {
        return intValue(buttonField(button, buttonYField, "field_146129_i", "y"), 0);
    }

    private static int buttonWidth(GuiButton button) {
        return intValue(buttonField(button, buttonWidthField, "field_146120_f", "width"), 0);
    }

    private static int buttonHeight(GuiButton button) {
        return intValue(buttonField(button, buttonHeightField, "field_146121_g", "height"), 0);
    }

    private static String buttonText(GuiButton button) {
        Object value = buttonField(button, buttonTextField, "field_146126_j", "displayString");
        return value instanceof String ? (String) value : "";
    }

    private static boolean buttonEnabled(GuiButton button) {
        Object value = buttonField(button, buttonEnabledField, "field_146124_l", "enabled");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    private static boolean buttonVisible(GuiButton button) {
        Object value = buttonField(button, buttonVisibleField, "field_146125_m", "visible");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    private static void setButtonText(GuiButton button, String text) {
        setButtonField(button, buttonTextField, "field_146126_j", "displayString", text);
    }

    private static Object buttonField(GuiButton button, Field cached, String srgName, String mcpName) {
        Field field = cached != null ? cached : findField(GuiButton.class, srgName, mcpName);
        cacheButtonField(srgName, field);
        return getFieldValue(field, button);
    }

    private static void setButtonField(GuiButton button, Field cached, String srgName, String mcpName, Object value) {
        Field field = cached != null ? cached : findField(GuiButton.class, srgName, mcpName);
        cacheButtonField(srgName, field);
        setFieldValue(field, button, value);
    }

    private static void cacheButtonField(String srgName, Field field) {
        if (field == null) {
            return;
        }
        if ("field_146127_k".equals(srgName)) {
            buttonIdField = field;
        } else if ("field_146128_h".equals(srgName)) {
            buttonXField = field;
        } else if ("field_146129_i".equals(srgName)) {
            buttonYField = field;
        } else if ("field_146120_f".equals(srgName)) {
            buttonWidthField = field;
        } else if ("field_146121_g".equals(srgName)) {
            buttonHeightField = field;
        } else if ("field_146126_j".equals(srgName)) {
            buttonTextField = field;
        } else if ("field_146124_l".equals(srgName)) {
            buttonEnabledField = field;
        } else if ("field_146125_m".equals(srgName)) {
            buttonVisibleField = field;
        }
    }

    private static Field findField(Class<?> owner, String srgName, String mcpName) {
        for (String name : new String[] {srgName, mcpName}) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }

    private static Object getFieldValue(Field field, Object target) {
        if (field == null || target == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setFieldValue(Field field, Object target, Object value) {
        if (field == null || target == null) {
            return;
        }
        try {
            field.set(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }
}
