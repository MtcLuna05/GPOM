package com.l.gpom.compat.jecalculation;

import com.l.gpom.GPOM;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class JecPinnedCraftOverlay {
    private static final String JECA_GUI = "me.towdium.jecalculation.gui.JecaGui";
    private static final String GUI_CRAFT = "me.towdium.jecalculation.gui.guis.GuiCraft";
    private static final String I_GUI = "me.towdium.jecalculation.gui.guis.IGui";
    private static final String JEI_RECIPES_GUI = "mezz.jei.gui.recipes.RecipesGui";

    private static final int CRAFT_ROOT_WIDTH = 176;
    private static final int CRAFT_ROOT_HEIGHT = 166;
    private static final int MINI_WIDTH = 108;
    private static final int MINI_HEIGHT = 113;
    private static final int MINI_CONTENT_TOP = 10;
    private static final float MINI_SCALE = (float) MINI_WIDTH / (float) CRAFT_ROOT_WIDTH;
    private static final int MINI_ROOT_HEIGHT = Math.round(CRAFT_ROOT_HEIGHT * MINI_SCALE);
    private static final int DRAG_X = 4;
    private static final int DRAG_Y = 4;
    private static final int DRAG_WIDTH = 95;
    private static final int DRAG_HEIGHT = 5;
    private static final int CLOSE_X = 99;
    private static final int CLOSE_Y = 4;
    private static final int CLOSE_SIZE = 5;
    private static final int AMOUNT_FIELD_X = 60;
    private static final int AMOUNT_FIELD_Y = 7;
    private static final int AMOUNT_FIELD_WIDTH = 65;
    private static final int AMOUNT_FIELD_HEIGHT = 20;
    private static final int MINI_ATLAS_WIDTH = 128;
    private static final int MINI_ATLAS_HEIGHT = 32;
    private static final int OFFSCREEN_MOUSE = -10000;
    private static final int UV_BUTTON_NORMAL_X = 0;
    private static final int UV_BUTTON_HOVER_X = 20;
    private static final int UV_PANEL_X = 40;
    private static final int UV_PIN_ON_X = 60;
    private static final int UV_PIN_OFF_X = 74;
    private static final int UV_DRAG_NORMAL_X = 0;
    private static final int UV_DRAG_HOVER_X = 8;
    private static final int UV_CLOSE_NORMAL_X = 16;
    private static final int UV_CLOSE_HOVER_X = 21;
    private static final int UV_TOP_ROW_Y = 0;
    private static final int UV_CONTROL_ROW_Y = 20;
    private static final String STATE_FILE_NAME = "gpom-jecalculation-overlay.properties";
    private static final ResourceLocation MINI_RESOURCES = new ResourceLocation("gpom", "textures/gui/jec_mini_overlay.png");
    private static final Gui TEXTURE_GUI = new Gui();
    private static final long REFRESH_INTERVAL_NANOS = 500_000_000L;
    private static final Field GUI_LEFT_FIELD = findField(GuiContainer.class, "field_147003_i", "guiLeft");
    private static final Field GUI_TOP_FIELD = findField(GuiContainer.class, "field_147009_r", "guiTop");
    private static final Field GUI_WIDTH_FIELD = findField(GuiContainer.class, "field_146999_f", "xSize");
    private static final Field GUI_HEIGHT_FIELD = findField(GuiContainer.class, "field_147000_g", "ySize");
    private static final Field SCREEN_WIDTH_FIELD = findField(GuiScreen.class, "field_146294_l", "width");
    private static final Field SCREEN_HEIGHT_FIELD = findField(GuiScreen.class, "field_146295_m", "height");

    private static final JecPinnedCraftOverlay INSTANCE = new JecPinnedCraftOverlay();

    private static boolean registered;
    private static boolean pinned = true;
    private static boolean positionInitialized;
    private static boolean userPositioned;
    private static int overlayX;
    private static int overlayY;
    private static boolean dragging;
    private static boolean overlayKeyboardFocused;
    private static boolean stateLoaded;
    private static boolean loggedStateFailure;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static Object lastHostScreen;

    private static JecAccess access;
    private static HiddenCraft hiddenCraft;
    private static boolean accessFailed;
    private static boolean loggedAccessFailure;
    private static boolean loggedRenderFailure;
    private static long nextRefreshAt;

    private JecPinnedCraftOverlay() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.jecalculationPinnedCraftOverlayEnabled() || !classPresent(JECA_GUI)) {
            return;
        }
        registered = true;
        loadStateIfNeeded();
        MinecraftForge.EVENT_BUS.register(INSTANCE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!GpomEarlyConfig.jecalculationPinnedCraftOverlayEnabled()) {
            return;
        }
        loadStateIfNeeded();

        GuiScreen screen = event.getGui();
        if (isJecCraftScreen(screen)) {
            drawPinButton(screen, event.getMouseX(), event.getMouseY());
            return;
        }

        if (isJecGui(screen)) {
            dragging = false;
            clearOverlayKeyboardFocus();
            return;
        }

        if (!pinned || !(screen instanceof GuiContainer)) {
            dragging = false;
            clearOverlayKeyboardFocus();
            return;
        }

        Minecraft minecraft = ClientAccess.minecraft();
        if (minecraft == null) {
            return;
        }

        ensurePosition(screen, event.getMouseX(), event.getMouseY());
        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                overlayX = event.getMouseX() - dragOffsetX;
                overlayY = event.getMouseY() - dragOffsetY;
                userPositioned = true;
                clampToScreen(screen);
            } else {
                dragging = false;
                saveState();
            }
        }

        HiddenCraft craft = hiddenCraft(minecraft, screen);
        if (craft == null) {
            return;
        }

        try {
            craft.position(rootX(), rootY(), screen);
            refreshIfDue(craft);
            if (!overlayKeyboardFocused) {
                craft.blurTextField();
            }
            drawMiniFrame(minecraft, event.getMouseX(), event.getMouseY());
            drawCraftRoot(craft, screen, event.getMouseX(), event.getMouseY(), event.getRenderPartialTicks());
            drawMiniControls(minecraft, event.getMouseX(), event.getMouseY());
            drawCraftTooltip(craft, screen, event.getMouseX(), event.getMouseY());
        } catch (Throwable throwable) {
            resetHiddenCraft();
            logRenderFailure(throwable);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!GpomEarlyConfig.jecalculationPinnedCraftOverlayEnabled()) {
            return;
        }
        loadStateIfNeeded();

        GuiScreen screen = event.getGui();
        int mouseX = mouseX(screen);
        int mouseY = mouseY(screen);
        int button = Mouse.getEventButton();

        if (isJecCraftScreen(screen)) {
            if (button == 0 && Mouse.getEventButtonState() && isInsidePinButton(screen, mouseX, mouseY)) {
                setPinned(!pinned);
                resetHiddenCraft();
                cancel(event);
            }
            return;
        }

        if (isJecGui(screen)) {
            clearOverlayKeyboardFocus();
            return;
        }

        if (!pinned || !(screen instanceof GuiContainer)) {
            clearOverlayKeyboardFocus();
            return;
        }

        ensurePosition(screen, mouseX, mouseY);
        int wheel = Mouse.getEventDWheel();

        if (button == 0 && !Mouse.getEventButtonState() && dragging) {
            dragging = false;
            saveState();
            cancel(event);
            return;
        }

        if (button >= 0 && Mouse.getEventButtonState() && !isInsideOverlay(mouseX, mouseY)) {
            clearOverlayKeyboardFocus();
            return;
        }

        if (button == 0 && Mouse.getEventButtonState()) {
            if (isInsideCloseButton(mouseX, mouseY)) {
                setPinned(false);
                dragging = false;
                clearOverlayKeyboardFocus();
                resetHiddenCraft();
                cancel(event);
                return;
            }
            if (isInsideDragHandle(mouseX, mouseY)) {
                dragging = true;
                clearOverlayKeyboardFocus();
                dragOffsetX = mouseX - overlayX;
                dragOffsetY = mouseY - overlayY;
                cancel(event);
                return;
            }
        }

        if (!isInsideRoot(mouseX, mouseY) && !(wheel != 0 && isInsideOverlay(mouseX, mouseY))) {
            return;
        }

        HiddenCraft craft = hiddenCraft(ClientAccess.minecraft(), screen);
        if (craft == null) {
            return;
        }

        try {
            craft.position(rootX(), rootY(), screen);
            int localX = rootLocalX(mouseX);
            int localY = rootLocalY(mouseY);
            boolean consumed = false;
            if (button == -1 && wheel != 0) {
                consumed = craft.scroll(localX, localY, wheel / 120);
            } else if (button >= 0 && Mouse.getEventButtonState()) {
                boolean textFocusClick = button == 0 && isInsideAmountField(localX, localY);
                consumed = craft.click(screen, localX, localY, button);
                overlayKeyboardFocused = textFocusClick && craft.isTextFieldFocused();
                if (!overlayKeyboardFocused) {
                    craft.blurTextField();
                }
            }
            if (consumed || isInsideOverlay(mouseX, mouseY)) {
                nextRefreshAt = 0L;
                cancel(event);
            }
        } catch (Throwable throwable) {
            resetHiddenCraft();
            logRenderFailure(throwable);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onKeyboard(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!GpomEarlyConfig.jecalculationPinnedCraftOverlayEnabled()) {
            return;
        }
        loadStateIfNeeded();
        if (!pinned || !(event.getGui() instanceof GuiContainer) || isJecGui(event.getGui())) {
            return;
        }
        if (!overlayKeyboardFocused) {
            return;
        }
        if (!Keyboard.getEventKeyState()) {
            return;
        }

        if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
            clearOverlayKeyboardFocus();
            return;
        }

        HiddenCraft craft = hiddenCraft(ClientAccess.minecraft(), event.getGui());
        if (craft == null) {
            clearOverlayKeyboardFocus();
            return;
        }

        try {
            if (!craft.isTextFieldFocused()) {
                clearOverlayKeyboardFocus();
                return;
            }
            if (craft.key(Keyboard.getEventCharacter(), Keyboard.getEventKey())) {
                nextRefreshAt = 0L;
                cancel(event);
            }
        } catch (Throwable throwable) {
            resetHiddenCraft();
            logRenderFailure(throwable);
        }
    }

    private static HiddenCraft hiddenCraft(Minecraft minecraft, GuiScreen host) {
        if (minecraft == null || accessFailed) {
            return null;
        }
        try {
            JecAccess currentAccess = access();
            if (currentAccess == null) {
                return null;
            }
            if (hiddenCraft == null) {
                hiddenCraft = currentAccess.createHiddenCraft(minecraft, screenWidth(host), screenHeight(host));
                nextRefreshAt = 0L;
            }
            return hiddenCraft;
        } catch (Throwable throwable) {
            accessFailed = true;
            logAccessFailure(throwable);
            return null;
        }
    }

    private static void refreshIfDue(HiddenCraft craft) throws Exception {
        long now = System.nanoTime();
        if (now >= nextRefreshAt) {
            craft.refresh();
            nextRefreshAt = now + REFRESH_INTERVAL_NANOS;
        }
    }

    private static void loadStateIfNeeded() {
        if (stateLoaded) {
            return;
        }
        File file = stateFile();
        if (file == null) {
            return;
        }
        stateLoaded = true;
        if (!file.isFile()) {
            return;
        }

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
            pinned = parseBoolean(properties.getProperty("pinned"), pinned);
            userPositioned = parseBoolean(properties.getProperty("userPositioned"), false);
            if (userPositioned) {
                overlayX = parseInt(properties.getProperty("overlayX"), overlayX);
                overlayY = parseInt(properties.getProperty("overlayY"), overlayY);
                positionInitialized = true;
            }
        } catch (Throwable throwable) {
            logStateFailure("load", file, throwable);
        }
    }

    private static void setPinned(boolean value) {
        if (pinned == value) {
            return;
        }
        pinned = value;
        saveState();
    }

    private static void saveState() {
        if (!stateLoaded) {
            return;
        }
        File file = stateFile();
        if (file == null) {
            return;
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            logStateFailure("create parent directory for", file, null);
            return;
        }

        Properties properties = new Properties();
        properties.setProperty("pinned", Boolean.toString(pinned));
        properties.setProperty("userPositioned", Boolean.toString(userPositioned));
        properties.setProperty("overlayX", Integer.toString(overlayX));
        properties.setProperty("overlayY", Integer.toString(overlayY));

        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            properties.store(writer, "GPOM Just Enough Calculation pinned craft overlay state");
        } catch (Throwable throwable) {
            logStateFailure("save", file, throwable);
        }
    }

    private static File stateFile() {
        File gameDir = new File(System.getProperty("user.dir", "."));
        return new File(new File(gameDir, "config"), STATE_FILE_NAME);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        return fallback;
    }

    private static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void logStateFailure(String action, File file, Throwable throwable) {
        if (loggedStateFailure) {
            return;
        }
        loggedStateFailure = true;
        if (throwable == null) {
            GPOM.LOGGER.warn("Could not {} GPOM JEC pinned craft overlay state file {}", action, file);
        } else {
            GPOM.LOGGER.warn("Could not {} GPOM JEC pinned craft overlay state file {}", action, file, throwable);
        }
    }

    private static void drawCraftRoot(HiddenCraft craft, GuiScreen screen, int mouseX, int mouseY, float partialTicks) throws Exception {
        GlBridge.pushMatrix();
        try {
            enableScissor(screen, rootX(), rootY(), MINI_WIDTH, MINI_ROOT_HEIGHT);
            GlBridge.translate(rootX(), rootY(), 0.0F);
            GlBridge.scale(MINI_SCALE, MINI_SCALE, 1.0F);
            GlBridge.enableTexture2D();
            GlBridge.enableBlend();
            GlBridge.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlBridge.disableLighting();
            GlBridge.disableDepth();
            GlBridge.color(1.0F, 1.0F, 1.0F, 1.0F);
            int localMouseX = isInsideRoot(mouseX, mouseY) ? rootLocalX(mouseX) : OFFSCREEN_MOUSE;
            int localMouseY = isInsideRoot(mouseX, mouseY) ? rootLocalY(mouseY) : OFFSCREEN_MOUSE;
            craft.draw(localMouseX, localMouseY, partialTicks);
        } finally {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlBridge.popMatrix();
            GlBridge.normalizeGuiState();
        }
    }

    private static void drawCraftTooltip(HiddenCraft craft, GuiScreen screen, int mouseX, int mouseY) throws Exception {
        if (!isInsideRoot(mouseX, mouseY)) {
            return;
        }
        List<String> tooltip = craft.tooltip(rootLocalX(mouseX), rootLocalY(mouseY));
        if (tooltip.isEmpty()) {
            return;
        }
        FontRenderer font = ClientAccess.fontRenderer(ClientAccess.minecraft());
        GlBridge.pushMatrix();
        try {
            drawTooltipText(tooltip, mouseX, mouseY, font, screenWidth(screen), screenHeight(screen));
        } finally {
            GlBridge.popMatrix();
            GlBridge.normalizeGuiState();
        }
    }

    private static void drawTooltipText(List<String> tooltip, int mouseX, int mouseY, FontRenderer font, int screenWidth, int screenHeight) {
        if (font == null || tooltip == null || tooltip.isEmpty()) {
            return;
        }

        int tooltipWidth = 0;
        for (String line : tooltip) {
            tooltipWidth = Math.max(tooltipWidth, ClientAccess.stringWidth(font, line));
        }

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        int tooltipHeight = 8 + Math.max(0, tooltip.size() - 1) * 10;

        if (screenWidth > 0 && tooltipX + tooltipWidth + 6 > screenWidth) {
            tooltipX = mouseX - 28 - tooltipWidth;
        }
        if (screenHeight > 0 && tooltipY + tooltipHeight + 6 > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 6;
        }
        tooltipY = Math.max(4, tooltipY);

        GlBridge.disableLighting();
        GlBridge.disableDepth();
        GlBridge.enableBlend();
        GlBridge.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlBridge.color(1.0F, 1.0F, 1.0F, 1.0F);

        int background = 0xF0100010;
        int border = 0x505000FF;
        int borderEnd = 0x5028007F;
        int right = tooltipX + tooltipWidth;
        int bottom = tooltipY + tooltipHeight;

        ClientAccess.drawRect(tooltipX - 3, tooltipY - 4, right + 3, tooltipY - 3, background);
        ClientAccess.drawRect(tooltipX - 3, bottom + 3, right + 3, bottom + 4, background);
        ClientAccess.drawRect(tooltipX - 3, tooltipY - 3, right + 3, bottom + 3, background);
        ClientAccess.drawRect(tooltipX - 4, tooltipY - 3, tooltipX - 3, bottom + 3, background);
        ClientAccess.drawRect(right + 3, tooltipY - 3, right + 4, bottom + 3, background);
        ClientAccess.drawRect(tooltipX - 3, tooltipY - 3, tooltipX - 2, bottom + 3, border);
        ClientAccess.drawRect(right + 2, tooltipY - 3, right + 3, bottom + 3, borderEnd);
        ClientAccess.drawRect(tooltipX - 3, tooltipY - 3, right + 3, tooltipY - 2, border);
        ClientAccess.drawRect(tooltipX - 3, bottom + 2, right + 3, bottom + 3, borderEnd);

        GlBridge.enableTexture2D();
        GlBridge.color(1.0F, 1.0F, 1.0F, 1.0F);
        int y = tooltipY;
        for (String line : tooltip) {
            ClientAccess.drawStringWithShadow(font, line, tooltipX, y, 0xFFFFFFFF);
            y += 10;
        }
    }

    private static void drawMiniFrame(Minecraft minecraft, int mouseX, int mouseY) {
        try {
            GlBridge.disableLighting();
            GlBridge.disableDepth();
            prepareMiniTextureDraw();
            drawContinuousTexture(minecraft, overlayX, overlayY, MINI_WIDTH, MINI_HEIGHT,
                    UV_PANEL_X, UV_TOP_ROW_Y, 20, 20, 5);
        } finally {
            GlBridge.normalizeGuiState();
        }
    }

    private static void drawMiniControls(Minecraft minecraft, int mouseX, int mouseY) {
        try {
            GlBridge.disableLighting();
            GlBridge.disableDepth();
            prepareMiniTextureDraw();

            int dragU = isInsideDragHandle(mouseX, mouseY) || dragging ? UV_DRAG_HOVER_X : UV_DRAG_NORMAL_X;
            drawContinuousTexture(minecraft, overlayX + DRAG_X, overlayY + DRAG_Y, DRAG_WIDTH, DRAG_HEIGHT,
                    dragU, UV_CONTROL_ROW_Y, 8, 5, 1);

            int closeU = isInsideCloseButton(mouseX, mouseY) ? UV_CLOSE_HOVER_X : UV_CLOSE_NORMAL_X;
            drawTexture(minecraft, overlayX + CLOSE_X, overlayY + CLOSE_Y,
                    closeU, UV_CONTROL_ROW_Y, CLOSE_SIZE, CLOSE_SIZE);
        } finally {
            GlBridge.normalizeGuiState();
        }
    }

    private static void drawPinButton(GuiScreen screen, int mouseX, int mouseY) {
        int left = guiLeft(screen) + 130;
        int top = guiTop(screen) + 62;
        FontRenderer font = ClientAccess.fontRenderer(ClientAccess.minecraft());
        boolean hovered = mouseX >= left && mouseY >= top && mouseX < left + 20 && mouseY < top + 20;
        Minecraft minecraft = ClientAccess.minecraft();

        try {
            GlBridge.disableLighting();
            GlBridge.disableDepth();
            prepareMiniTextureDraw();
            drawTexture(minecraft, left, top, hovered ? UV_BUTTON_HOVER_X : UV_BUTTON_NORMAL_X, UV_TOP_ROW_Y, 20, 20);
            drawTexture(minecraft, left + 3, top + 3,
                    pinned ? UV_PIN_ON_X : UV_PIN_OFF_X, UV_TOP_ROW_Y, 14, 14);
            GlBridge.color(1.0F, 1.0F, 1.0F, 1.0F);
            if (hovered) {
                GlBridge.color(1.0F, 1.0F, 1.0F, 1.0F);
                ClientAccess.drawStringWithShadow(font, pinned ? "Pinned overlay on" : "Pinned overlay off", left - 23, top - 10, 0xFFFFFFFF);
            }
        } finally {
            GlBridge.normalizeGuiState();
        }
    }

    private static void prepareMiniTextureDraw() {
        GlBridge.enableTexture2D();
        GlBridge.enableBlend();
        GlBridge.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlBridge.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawContinuousTexture(Minecraft minecraft,
                                              int x,
                                              int y,
                                              int width,
                                              int height,
                                              int u,
                                              int v,
                                              int textureWidth,
                                              int textureHeight,
                                              int border) {
        if (width <= 0 || height <= 0 || textureWidth <= 0 || textureHeight <= 0) {
            return;
        }
        int centerWidth = textureWidth - border * 2;
        int centerHeight = textureHeight - border * 2;
        if (border <= 0 || centerWidth <= 0 || centerHeight <= 0 || width <= border * 2 || height <= border * 2) {
            drawTiledTexture(minecraft, x, y, width, height, u, v, textureWidth, textureHeight);
            return;
        }

        int right = x + width - border;
        int bottom = y + height - border;
        drawTexture(minecraft, x, y, u, v, border, border);
        drawTexture(minecraft, right, y, u + textureWidth - border, v, border, border);
        drawTexture(minecraft, x, bottom, u, v + textureHeight - border, border, border);
        drawTexture(minecraft, right, bottom, u + textureWidth - border, v + textureHeight - border, border, border);

        drawTiledTexture(minecraft, x + border, y, width - border * 2, border, u + border, v, centerWidth, border);
        drawTiledTexture(minecraft, x + border, bottom, width - border * 2, border,
                u + border, v + textureHeight - border, centerWidth, border);
        drawTiledTexture(minecraft, x, y + border, border, height - border * 2, u, v + border, border, centerHeight);
        drawTiledTexture(minecraft, right, y + border, border, height - border * 2,
                u + textureWidth - border, v + border, border, centerHeight);
        drawTiledTexture(minecraft, x + border, y + border, width - border * 2, height - border * 2,
                u + border, v + border, centerWidth, centerHeight);
    }

    private static void drawTiledTexture(Minecraft minecraft,
                                         int x,
                                         int y,
                                         int width,
                                         int height,
                                         int u,
                                         int v,
                                         int tileWidth,
                                         int tileHeight) {
        if (width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }
        int drawnY = 0;
        while (drawnY < height) {
            int drawHeight = Math.min(tileHeight, height - drawnY);
            int drawnX = 0;
            while (drawnX < width) {
                int drawWidth = Math.min(tileWidth, width - drawnX);
                drawTexture(minecraft, x + drawnX, y + drawnY, u, v, drawWidth, drawHeight);
                drawnX += drawWidth;
            }
            drawnY += drawHeight;
        }
    }

    private static void drawTexture(Minecraft minecraft, int x, int y, int u, int v, int width, int height) {
        ClientAccess.bindTexture(minecraft, MINI_RESOURCES);
        ClientAccess.drawModalRectWithCustomSizedTexture(TEXTURE_GUI, x, y, u, v, width, height,
                MINI_ATLAS_WIDTH, MINI_ATLAS_HEIGHT);
    }

    private static void enableScissor(GuiScreen screen, int x, int y, int width, int height) {
        Minecraft minecraft = ClientAccess.minecraft();
        int displayWidth = ClientAccess.displayWidth(minecraft);
        int displayHeight = ClientAccess.displayHeight(minecraft);
        int screenWidth = screenWidth(screen);
        int screenHeight = screenHeight(screen);
        if (displayWidth <= 0 || displayHeight <= 0 || screenWidth <= 0 || screenHeight <= 0 || width <= 0 || height <= 0) {
            return;
        }

        double scaleX = (double) displayWidth / (double) screenWidth;
        double scaleY = (double) displayHeight / (double) screenHeight;
        int scissorX = (int) Math.floor(x * scaleX);
        int scissorY = (int) Math.floor(displayHeight - (y + height) * scaleY);
        int scissorWidth = Math.max(1, (int) Math.ceil(width * scaleX));
        int scissorHeight = Math.max(1, (int) Math.ceil(height * scaleY));

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(scissorX, scissorY, scissorWidth, scissorHeight);
    }

    private static void ensurePosition(GuiScreen screen, int mouseX, int mouseY) {
        boolean hostChanged = screen != lastHostScreen;
        if (hostChanged) {
            clearOverlayKeyboardFocus();
        }
        if (!positionInitialized || (!userPositioned && screen != lastHostScreen)) {
            int hostLeft = guiLeft(screen);
            int hostTop = guiTop(screen);
            int hostWidth = guiWidth(screen);
            int hostHeight = guiHeight(screen);
            int width = screenWidth(screen);
            int x = hostLeft - MINI_WIDTH - 10;
            if (x < 4) {
                x = hostLeft + hostWidth + 10;
            }
            if (x + MINI_WIDTH > width - 4) {
                x = Math.max(4, width - MINI_WIDTH - 4);
            }
            int y = hostTop + (hostHeight - MINI_HEIGHT) / 2;
            overlayX = x;
            overlayY = y;
            positionInitialized = true;
        }
        lastHostScreen = screen;
        clampToScreen(screen);
        if (dragging && !Mouse.isButtonDown(0)) {
            dragging = false;
            saveState();
        }
    }

    private static void clampToScreen(GuiScreen screen) {
        int width = screenWidth(screen);
        int height = screenHeight(screen);
        if (width <= 0 || height <= 0) {
            return;
        }
        overlayX = Math.max(0, Math.min(overlayX, Math.max(0, width - MINI_WIDTH)));
        overlayY = Math.max(0, Math.min(overlayY, Math.max(0, height - MINI_HEIGHT)));
    }

    private static boolean isInsidePinButton(GuiScreen screen, int mouseX, int mouseY) {
        int left = guiLeft(screen) + 130;
        int top = guiTop(screen) + 62;
        return mouseX >= left && mouseY >= top && mouseX < left + 20 && mouseY < top + 20;
    }

    private static boolean isInsideDragHandle(int mouseX, int mouseY) {
        int left = overlayX + DRAG_X;
        int top = overlayY + DRAG_Y;
        return mouseX >= left && mouseY >= top && mouseX < left + DRAG_WIDTH && mouseY < top + DRAG_HEIGHT;
    }

    private static boolean isInsideCloseButton(int mouseX, int mouseY) {
        int left = overlayX + CLOSE_X;
        int top = overlayY + CLOSE_Y;
        return mouseX >= left && mouseY >= top && mouseX < left + CLOSE_SIZE && mouseY < top + CLOSE_SIZE;
    }

    private static boolean isInsideRoot(int mouseX, int mouseY) {
        int x = rootX();
        int y = rootY();
        return mouseX >= x && mouseY >= y && mouseX < x + MINI_WIDTH && mouseY < y + MINI_ROOT_HEIGHT;
    }

    private static boolean isInsideAmountField(int localX, int localY) {
        return localX >= AMOUNT_FIELD_X
                && localY >= AMOUNT_FIELD_Y
                && localX < AMOUNT_FIELD_X + AMOUNT_FIELD_WIDTH
                && localY < AMOUNT_FIELD_Y + AMOUNT_FIELD_HEIGHT;
    }

    private static boolean isInsideOverlay(int mouseX, int mouseY) {
        return mouseX >= overlayX && mouseY >= overlayY && mouseX < overlayX + MINI_WIDTH && mouseY < overlayY + MINI_HEIGHT;
    }

    private static int rootX() {
        return overlayX;
    }

    private static int rootY() {
        return overlayY + MINI_CONTENT_TOP;
    }

    private static int rootLocalX(int mouseX) {
        return Math.round((mouseX - rootX()) / MINI_SCALE);
    }

    private static int rootLocalY(int mouseY) {
        return Math.round((mouseY - rootY()) / MINI_SCALE);
    }

    private static boolean isJecCraftScreen(Object screen) {
        if (!isJecGui(screen)) {
            return false;
        }
        Object root = jecRoot(screen);
        return root != null && GUI_CRAFT.equals(root.getClass().getName());
    }

    private static boolean isJecGui(Object screen) {
        return screen != null && JECA_GUI.equals(screen.getClass().getName());
    }

    private static Object jecRoot(Object screen) {
        try {
            JecAccess currentAccess = access();
            return currentAccess == null ? null : currentAccess.rootField.get(screen);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int mouseX(GuiScreen screen) {
        Minecraft minecraft = ClientAccess.minecraft();
        int displayWidth = ClientAccess.displayWidth(minecraft);
        int width = screenWidth(screen);
        if (displayWidth <= 0 || width <= 0) {
            return 0;
        }
        return Mouse.getX() * width / displayWidth;
    }

    private static int mouseY(GuiScreen screen) {
        Minecraft minecraft = ClientAccess.minecraft();
        int displayHeight = ClientAccess.displayHeight(minecraft);
        int height = screenHeight(screen);
        if (displayHeight <= 0 || height <= 0) {
            return 0;
        }
        return height - Mouse.getY() * height / displayHeight - 1;
    }

    private static int guiLeft(Object screen) {
        return intField(screen, GUI_LEFT_FIELD, 0);
    }

    private static int guiTop(Object screen) {
        return intField(screen, GUI_TOP_FIELD, 0);
    }

    private static int guiWidth(Object screen) {
        return Math.max(CRAFT_ROOT_WIDTH, intField(screen, GUI_WIDTH_FIELD, CRAFT_ROOT_WIDTH));
    }

    private static int guiHeight(Object screen) {
        return Math.max(CRAFT_ROOT_HEIGHT, intField(screen, GUI_HEIGHT_FIELD, CRAFT_ROOT_HEIGHT));
    }

    private static int screenWidth(Object screen) {
        return intField(screen, SCREEN_WIDTH_FIELD, 0);
    }

    private static int screenHeight(Object screen) {
        return intField(screen, SCREEN_HEIGHT_FIELD, 0);
    }

    private static int intField(Object owner, Field field, int fallback) {
        if (owner == null || field == null) {
            return fallback;
        }
        try {
            return field.getInt(owner);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void resetHiddenCraft() {
        clearOverlayKeyboardFocus();
        hiddenCraft = null;
        nextRefreshAt = 0L;
    }

    private static void clearOverlayKeyboardFocus() {
        overlayKeyboardFocused = false;
        HiddenCraft craft = hiddenCraft;
        if (craft != null) {
            craft.blurTextField();
        }
    }

    private static void cancel(Event event) {
        try {
            if (event.isCancelable()) {
                event.setCanceled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static JecAccess access() throws Exception {
        if (access != null || accessFailed) {
            return access;
        }
        try {
            access = JecAccess.create();
            return access;
        } catch (ClassNotFoundException ignored) {
            accessFailed = true;
            return null;
        }
    }

    private static void logAccessFailure(Throwable throwable) {
        if (!loggedAccessFailure) {
            loggedAccessFailure = true;
            GPOM.LOGGER.warn("Disabling GPOM JEC pinned craft overlay because Just Enough Calculation internals were unavailable", throwable);
        }
    }

    private static void logRenderFailure(Throwable throwable) {
        if (!loggedRenderFailure) {
            loggedRenderFailure = true;
            GPOM.LOGGER.warn("Disabling one GPOM JEC pinned craft overlay render after an unexpected Just Enough Calculation error", throwable);
        }
    }

    private static Field findField(Class<?> type, String... names) {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }

        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName());
    }

    private static Method findOptionalMethod(Class<?> type, Class<?>[] parameterTypes, String... names) {
        try {
            return findMethod(type, parameterTypes, names);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> loadClass(String name) throws ClassNotFoundException {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            try {
                return Class.forName(name, false, context);
            } catch (ClassNotFoundException ignored) {
            }
        }
        return Class.forName(name, false, JecPinnedCraftOverlay.class.getClassLoader());
    }

    private static boolean classPresent(String name) {
        try {
            loadClass(name);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static final class JecAccess {
        private final Class<?> jecaGuiClass;
        private final Constructor<?> craftConstructor;
        private final Constructor<?> guiConstructor;
        private final Method setWorldAndResolution;
        private final Method onVisible;
        private final Method onDraw;
        private final Method onTooltip;
        private final Method onClicked;
        private final Method onScroll;
        private final Method onKey;
        private final Method drawHoveringText;
        private final Method refreshRecent;
        private final Method refreshCalculator;
        private final Field rootField;
        private final Field craftAmountField;
        private final Field textFieldWidgetField;
        private final Method textFieldSetFocused;
        private final Method textFieldIsFocused;
        private final Field currentScreenField;
        private final Field recipesGuiParentScreenField;
        private final Method recipesGuiOnStateChange;
        private final Field guiZLevelField;
        private final Field guiRenderItemField;
        private final Field renderItemZLevelField;

        private JecAccess(Class<?> jecaGuiClass,
                          Constructor<?> craftConstructor,
                          Constructor<?> guiConstructor,
                          Method setWorldAndResolution,
                          Method onVisible,
                          Method onDraw,
                          Method onTooltip,
                          Method onClicked,
                          Method onScroll,
                          Method onKey,
                          Method drawHoveringText,
                          Method refreshRecent,
                          Method refreshCalculator,
                          Field rootField,
                          Field craftAmountField,
                          Field textFieldWidgetField,
                          Method textFieldSetFocused,
                          Method textFieldIsFocused,
                          Field currentScreenField,
                          Field recipesGuiParentScreenField,
                          Method recipesGuiOnStateChange,
                          Field guiZLevelField,
                          Field guiRenderItemField,
                          Field renderItemZLevelField) {
            this.jecaGuiClass = jecaGuiClass;
            this.craftConstructor = craftConstructor;
            this.guiConstructor = guiConstructor;
            this.setWorldAndResolution = setWorldAndResolution;
            this.onVisible = onVisible;
            this.onDraw = onDraw;
            this.onTooltip = onTooltip;
            this.onClicked = onClicked;
            this.onScroll = onScroll;
            this.onKey = onKey;
            this.drawHoveringText = drawHoveringText;
            this.refreshRecent = refreshRecent;
            this.refreshCalculator = refreshCalculator;
            this.rootField = rootField;
            this.craftAmountField = craftAmountField;
            this.textFieldWidgetField = textFieldWidgetField;
            this.textFieldSetFocused = textFieldSetFocused;
            this.textFieldIsFocused = textFieldIsFocused;
            this.currentScreenField = currentScreenField;
            this.recipesGuiParentScreenField = recipesGuiParentScreenField;
            this.recipesGuiOnStateChange = recipesGuiOnStateChange;
            this.guiZLevelField = guiZLevelField;
            this.guiRenderItemField = guiRenderItemField;
            this.renderItemZLevelField = renderItemZLevelField;
        }

        private static JecAccess create() throws Exception {
            Class<?> jecaGuiClass = loadClass(JECA_GUI);
            Class<?> guiCraftClass = loadClass(GUI_CRAFT);
            Class<?> iGuiClass = loadClass(I_GUI);
            Constructor<?> craftConstructor = guiCraftClass.getConstructor();
            craftConstructor.setAccessible(true);
            Constructor<?> guiConstructor = jecaGuiClass.getConstructor(jecaGuiClass, boolean.class, iGuiClass);
            guiConstructor.setAccessible(true);
            Method setWorldAndResolution = findMethod(GuiScreen.class,
                    new Class<?>[] {Minecraft.class, int.class, int.class},
                    "func_146280_a",
                    "setWorldAndResolution");
            Method onVisible = findMethod(iGuiClass, new Class<?>[] {jecaGuiClass}, "onVisible");
            Method onDraw = findMethod(iGuiClass, new Class<?>[] {jecaGuiClass, int.class, int.class}, "onDraw");
            Method onTooltip = findMethod(iGuiClass, new Class<?>[] {jecaGuiClass, int.class, int.class, List.class}, "onTooltip");
            Method onClicked = findMethod(iGuiClass, new Class<?>[] {jecaGuiClass, int.class, int.class, int.class}, "onClicked");
            Method onScroll = findMethod(iGuiClass, new Class<?>[] {jecaGuiClass, int.class, int.class, int.class}, "onScroll");
            Method onKey = findMethod(iGuiClass, new Class<?>[] {jecaGuiClass, char.class, int.class}, "onKey");
            Method drawHoveringText = findOptionalMethod(jecaGuiClass,
                    new Class<?>[] {List.class, int.class, int.class, FontRenderer.class},
                    "drawHoveringText");
            Method refreshRecent = findOptionalMethod(guiCraftClass, new Class<?>[0], "refreshRecent");
            Method refreshCalculator = findOptionalMethod(guiCraftClass, new Class<?>[0], "refreshCalculator");
            Field rootField = findField(jecaGuiClass, "root");
            if (rootField == null) {
                throw new NoSuchFieldException("JecaGui.root");
            }
            Class<?> wTextFieldClass = loadClass("me.towdium.jecalculation.gui.widgets.WTextField");
            Field craftAmountField = findField(guiCraftClass, "amount");
            Field textFieldWidgetField = findField(wTextFieldClass, "textField");
            Method textFieldSetFocused = findOptionalMethod(GuiTextField.class, new Class<?>[] {boolean.class},
                    "func_146195_b",
                    "setFocused");
            Method textFieldIsFocused = findOptionalMethod(GuiTextField.class, new Class<?>[0],
                    "func_146206_l",
                    "isFocused");
            Field currentScreenField = findField(Minecraft.class, "field_71462_r", "currentScreen");
            if (currentScreenField == null) {
                throw new NoSuchFieldException("Minecraft.currentScreen");
            }
            Field recipesGuiParentScreenField = null;
            Method recipesGuiOnStateChange = null;
            try {
                Class<?> recipesGuiClass = loadClass(JEI_RECIPES_GUI);
                recipesGuiParentScreenField = findField(recipesGuiClass, "parentScreen");
                recipesGuiOnStateChange = findOptionalMethod(recipesGuiClass, new Class<?>[0], "onStateChange");
            } catch (ClassNotFoundException ignored) {
            }
            Field guiZLevelField = findField(Gui.class, "field_73735_i", "zLevel");
            Field guiRenderItemField = findField(GuiScreen.class, "field_146296_j", "itemRender");
            Field renderItemZLevelField = findField(RenderItem.class, "field_77023_b", "zLevel");
            return new JecAccess(jecaGuiClass, craftConstructor, guiConstructor, setWorldAndResolution, onVisible,
                    onDraw, onTooltip, onClicked, onScroll, onKey, drawHoveringText, refreshRecent, refreshCalculator,
                    rootField, craftAmountField, textFieldWidgetField, textFieldSetFocused, textFieldIsFocused,
                    currentScreenField, recipesGuiParentScreenField, recipesGuiOnStateChange, guiZLevelField, guiRenderItemField, renderItemZLevelField);
        }

        private HiddenCraft createHiddenCraft(Minecraft minecraft, int width, int height) throws Exception {
            Object root = craftConstructor.newInstance();
            Object gui = guiConstructor.newInstance(null, false, root);
            setWorldAndResolution.invoke(gui, minecraft, Math.max(width, CRAFT_ROOT_WIDTH), Math.max(height, CRAFT_ROOT_HEIGHT));
            onVisible.invoke(root, gui);
            HiddenCraft craft = new HiddenCraft(this, gui, root);
            craft.blurTextField();
            return craft;
        }

        private Object withCurrentScreen(Object temporaryScreen, ScreenCallable callable) throws Exception {
            return withCurrentScreen(temporaryScreen, null, callable);
        }

        private Object withCurrentScreen(Object temporaryScreen, Object preferredParentScreen, ScreenCallable callable) throws Exception {
            Minecraft minecraft = ClientAccess.minecraft();
            if (minecraft == null) {
                return callable.call();
            }
            Object previous = currentScreenField.get(minecraft);
            currentScreenField.set(minecraft, temporaryScreen);
            try {
                return callable.call();
            } finally {
                Object current = currentScreenField.get(minecraft);
                if (current == temporaryScreen) {
                    currentScreenField.set(minecraft, previous);
                } else {
                    patchJeiRecipeParent(current, preferredParentScreen == null ? previous : preferredParentScreen, temporaryScreen);
                }
            }
        }

        private void patchJeiRecipeParent(Object screen, Object parentScreen, Object hiddenScreen) {
            if (screen == null || parentScreen == null || recipesGuiParentScreenField == null || !JEI_RECIPES_GUI.equals(screen.getClass().getName())) {
                return;
            }
            try {
                Object currentParent = recipesGuiParentScreenField.get(screen);
                if (currentParent == hiddenScreen || currentParent == null || isJecGui(currentParent)) {
                    recipesGuiParentScreenField.set(screen, parentScreen);
                    if (recipesGuiOnStateChange != null) {
                        recipesGuiOnStateChange.invoke(screen);
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        private RenderStateSnapshot captureRenderState(Object gui) {
            return RenderStateSnapshot.capture(gui, guiZLevelField, guiRenderItemField, renderItemZLevelField);
        }
    }

    private interface ScreenCallable {
        Object call() throws Exception;
    }

    private static final class GlBridge {
        private static final Class<?> GL_STATE_MANAGER = optionalClass("net.minecraft.client.renderer.GlStateManager");
        private static final Method PUSH_MATRIX = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179094_E", "pushMatrix");
        private static final Method POP_MATRIX = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179121_F", "popMatrix");
        private static final Method TRANSLATE = optionalMethod(GL_STATE_MANAGER,
                new Class<?>[] {float.class, float.class, float.class},
                "func_179109_b",
                "translate");
        private static final Method SCALE = optionalMethod(GL_STATE_MANAGER,
                new Class<?>[] {float.class, float.class, float.class},
                "func_179152_a",
                "scale");
        private static final Method ENABLE_TEXTURE_2D = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179098_w", "enableTexture2D");
        private static final Method ENABLE_BLEND = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179147_l", "enableBlend");
        private static final Method DISABLE_BLEND = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179084_k", "disableBlend");
        private static final Method BLEND_FUNC = optionalMethod(GL_STATE_MANAGER,
                new Class<?>[] {int.class, int.class},
                "func_179112_b",
                "blendFunc");
        private static final Method DISABLE_LIGHTING = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179140_f", "disableLighting");
        private static final Method DISABLE_DEPTH = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179097_i", "disableDepth");
        private static final Method ENABLE_DEPTH = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179126_j", "enableDepth");
        private static final Method ENABLE_ALPHA = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179141_d", "enableAlpha");
        private static final Method DEPTH_MASK = optionalMethod(GL_STATE_MANAGER,
                new Class<?>[] {boolean.class},
                "func_179132_a",
                "depthMask");
        private static final Method COLOR_MASK = optionalMethod(GL_STATE_MANAGER,
                new Class<?>[] {boolean.class, boolean.class, boolean.class, boolean.class},
                "func_179135_a",
                "colorMask");
        private static final Method COLOR = optionalMethod(GL_STATE_MANAGER,
                new Class<?>[] {float.class, float.class, float.class, float.class},
                "func_179131_c",
                "color");
        private static final Method RESET_COLOR = optionalMethod(GL_STATE_MANAGER, new Class<?>[0], "func_179117_G", "resetColor");

        private GlBridge() {
        }

        private static void pushMatrix() {
            invoke(PUSH_MATRIX);
        }

        private static void popMatrix() {
            invoke(POP_MATRIX);
        }

        private static void translate(float x, float y, float z) {
            invoke(TRANSLATE, x, y, z);
        }

        private static void scale(float x, float y, float z) {
            invoke(SCALE, x, y, z);
        }

        private static void enableTexture2D() {
            invoke(ENABLE_TEXTURE_2D);
        }

        private static void enableBlend() {
            invoke(ENABLE_BLEND);
        }

        private static void disableBlend() {
            invoke(DISABLE_BLEND);
        }

        private static void blendFunc(int sourceFactor, int destinationFactor) {
            invoke(BLEND_FUNC, sourceFactor, destinationFactor);
        }

        private static void disableLighting() {
            invoke(DISABLE_LIGHTING);
        }

        private static void disableDepth() {
            invoke(DISABLE_DEPTH);
        }

        private static void enableDepth() {
            invoke(ENABLE_DEPTH);
        }

        private static void enableAlpha() {
            invoke(ENABLE_ALPHA);
        }

        private static void depthMask(boolean enabled) {
            invoke(DEPTH_MASK, enabled);
        }

        private static void colorMask(boolean red, boolean green, boolean blue, boolean alpha) {
            invoke(COLOR_MASK, red, green, blue, alpha);
        }

        private static void color(float red, float green, float blue, float alpha) {
            invoke(COLOR, red, green, blue, alpha);
        }

        private static void resetColor() {
            if (RESET_COLOR != null) {
                invoke(RESET_COLOR);
            } else {
                color(1.0F, 1.0F, 1.0F, 1.0F);
            }
        }

        private static void normalizeGuiState() {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            enableTexture2D();
            enableAlpha();
            disableLighting();
            enableDepth();
            depthMask(true);
            colorMask(true, true, true, true);
            disableBlend();
            resetColor();
        }

        private static void invoke(Method method, Object... args) {
            if (method == null) {
                return;
            }
            try {
                method.invoke(null, args);
            } catch (Throwable ignored) {
            }
        }

        private static Class<?> optionalClass(String name) {
            try {
                return loadClass(name);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Method optionalMethod(Class<?> type, Class<?>[] parameterTypes, String... names) {
            if (type == null) {
                return null;
            }
            return findOptionalMethod(type, parameterTypes, names);
        }
    }

    private static final class HiddenCraft {
        private final JecAccess access;
        private final Object gui;
        private final Object root;
        private int lastWidth = -1;
        private int lastHeight = -1;

        private HiddenCraft(JecAccess access, Object gui, Object root) {
            this.access = access;
            this.gui = gui;
            this.root = root;
        }

        private void position(int left, int top, GuiScreen host) throws Exception {
            int width = Math.max(screenWidth(host), CRAFT_ROOT_WIDTH);
            int height = Math.max(screenHeight(host), CRAFT_ROOT_HEIGHT);
            if (width != lastWidth || height != lastHeight) {
                access.setWorldAndResolution.invoke(gui, ClientAccess.minecraft(), width, height);
                lastWidth = width;
                lastHeight = height;
            }
            if (GUI_LEFT_FIELD != null) {
                GUI_LEFT_FIELD.setInt(gui, left);
            }
            if (GUI_TOP_FIELD != null) {
                GUI_TOP_FIELD.setInt(gui, top);
            }
        }

        private void refresh() throws Exception {
            if (access.refreshRecent != null) {
                access.refreshRecent.invoke(root);
            }
            if (access.refreshCalculator != null) {
                access.refreshCalculator.invoke(root);
            } else {
                access.onVisible.invoke(root, gui);
            }
        }

        private void draw(int localMouseX, int localMouseY, float partialTicks) throws Exception {
            RenderStateSnapshot renderState = access.captureRenderState(gui);
            try {
                access.withCurrentScreen(gui, () -> {
                    access.onDraw.invoke(root, gui, localMouseX, localMouseY);
                    return null;
                });
            } finally {
                renderState.restore();
            }
        }

        private List<String> tooltip(int localMouseX, int localMouseY) throws Exception {
            List<String> tooltip = new ArrayList<>();
            access.withCurrentScreen(gui, () -> {
                access.onTooltip.invoke(root, gui, localMouseX, localMouseY, tooltip);
                return null;
            });
            return tooltip;
        }

        private boolean click(GuiScreen host, int localMouseX, int localMouseY, int button) throws Exception {
            Object value = access.withCurrentScreen(gui, host, () -> access.onClicked.invoke(root, gui, localMouseX, localMouseY, button));
            return value instanceof Boolean && (Boolean) value;
        }

        private boolean scroll(int localMouseX, int localMouseY, int amount) throws Exception {
            Object value = access.withCurrentScreen(gui, () -> access.onScroll.invoke(root, gui, localMouseX, localMouseY, amount));
            return value instanceof Boolean && (Boolean) value;
        }

        private boolean key(char character, int keyCode) throws Exception {
            Object value = access.withCurrentScreen(gui, () -> access.onKey.invoke(root, gui, character, keyCode));
            return value instanceof Boolean && (Boolean) value;
        }

        private void blurTextField() {
            try {
                Object textField = textField();
                if (textField != null && access.textFieldSetFocused != null) {
                    access.textFieldSetFocused.invoke(textField, false);
                }
            } catch (Throwable ignored) {
            }
        }

        private boolean isTextFieldFocused() {
            try {
                Object textField = textField();
                if (textField != null && access.textFieldIsFocused != null) {
                    Object value = access.textFieldIsFocused.invoke(textField);
                    return value instanceof Boolean && (Boolean) value;
                }
            } catch (Throwable ignored) {
            }
            return false;
        }

        private Object textField() throws IllegalAccessException {
            if (access.craftAmountField == null || access.textFieldWidgetField == null) {
                return null;
            }
            Object amount = access.craftAmountField.get(root);
            return amount == null ? null : access.textFieldWidgetField.get(amount);
        }

        private void drawTooltip(List<String> tooltip, int mouseX, int mouseY, FontRenderer font) throws Exception {
            if (font == null || access.drawHoveringText == null) {
                return;
            }
            access.withCurrentScreen(gui, () -> {
                access.drawHoveringText.invoke(gui, tooltip, mouseX, mouseY, font);
                return null;
            });
        }
    }

    private static final class RenderStateSnapshot {
        private final Object gui;
        private final Field guiZLevelField;
        private final boolean hasGuiZLevel;
        private final float guiZLevel;
        private final Object renderItem;
        private final Field renderItemZLevelField;
        private final boolean hasRenderItemZLevel;
        private final float renderItemZLevel;

        private RenderStateSnapshot(Object gui,
                                    Field guiZLevelField,
                                    boolean hasGuiZLevel,
                                    float guiZLevel,
                                    Object renderItem,
                                    Field renderItemZLevelField,
                                    boolean hasRenderItemZLevel,
                                    float renderItemZLevel) {
            this.gui = gui;
            this.guiZLevelField = guiZLevelField;
            this.hasGuiZLevel = hasGuiZLevel;
            this.guiZLevel = guiZLevel;
            this.renderItem = renderItem;
            this.renderItemZLevelField = renderItemZLevelField;
            this.hasRenderItemZLevel = hasRenderItemZLevel;
            this.renderItemZLevel = renderItemZLevel;
        }

        private static RenderStateSnapshot capture(Object gui,
                                                   Field guiZLevelField,
                                                   Field guiRenderItemField,
                                                   Field renderItemZLevelField) {
            boolean hasGuiZLevel = false;
            float guiZLevel = 0.0F;
            Object renderItem = null;
            boolean hasRenderItemZLevel = false;
            float renderItemZLevel = 0.0F;
            try {
                if (gui != null && guiZLevelField != null) {
                    guiZLevel = guiZLevelField.getFloat(gui);
                    hasGuiZLevel = true;
                }
            } catch (Throwable ignored) {
            }
            try {
                if (gui != null && guiRenderItemField != null) {
                    renderItem = guiRenderItemField.get(gui);
                }
                if (renderItem != null && renderItemZLevelField != null) {
                    renderItemZLevel = renderItemZLevelField.getFloat(renderItem);
                    hasRenderItemZLevel = true;
                }
            } catch (Throwable ignored) {
            }
            return new RenderStateSnapshot(gui, guiZLevelField, hasGuiZLevel, guiZLevel,
                    renderItem, renderItemZLevelField, hasRenderItemZLevel, renderItemZLevel);
        }

        private void restore() {
            try {
                if (hasGuiZLevel) {
                    guiZLevelField.setFloat(gui, guiZLevel);
                }
            } catch (Throwable ignored) {
            }
            try {
                if (hasRenderItemZLevel) {
                    renderItemZLevelField.setFloat(renderItem, renderItemZLevel);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
