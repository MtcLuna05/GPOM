package com.l.gpom.client;

import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.GPOM;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenWorking;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

public final class WorldLoadingProgress {
    private static final ResourceLocation OPTIONS_BACKGROUND = new ResourceLocation("textures/gui/options_background.png");
    private static volatile boolean active;
    private static volatile String title = "Loading world";
    private static volatile String worldName = "";
    private static volatile String stage = "Preparing";
    private static volatile String detail = "";
    private static volatile int progress = -1;
    private static volatile long startedAt;
    private static volatile boolean suppressTerrainStartUntilScreenClears;
    private static volatile boolean waitingForFirstWorldRender;
    private static volatile long waitingForFirstWorldRenderAt;
    private static volatile boolean firstFrameLogged;
    private static volatile boolean disabledAfterFailure;
    private static volatile Field fontRendererField;
    private static volatile Method guiDrawRectMethod;
    private static volatile Method guiTexturedRectMethod;
    private static volatile Method fontDrawStringMethod;
    private static volatile Method fontGetStringWidthMethod;
    private static volatile Method minecraftGetMinecraftMethod;
    private static volatile Method minecraftGetTextureManagerMethod;
    private static volatile Method textureManagerBindTextureMethod;
    private static volatile Method minecraftUpdateDisplayMethod;
    private static volatile Method scaledResolutionGetWidthMethod;
    private static volatile Method scaledResolutionGetHeightMethod;
    private static volatile Field minecraftCurrentScreenField;
    private static final DirtBackgroundScreen DIRT_BACKGROUND_SCREEN = new DirtBackgroundScreen();

    private WorldLoadingProgress() {
    }

    public static boolean enabled() {
        return GpomEarlyConfig.worldLoadingScreenEnabled() && !disabledAfterFailure;
    }

    public static boolean isActive() {
        return enabled() && active;
    }

    public static boolean canRenderOverCurrentScreen() {
        if (!enabled()) {
            return false;
        }
        try {
            Minecraft minecraft = currentMinecraft();
            return canRenderOverCurrentScreen(minecraft);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void beginIntegrated(String folderName, String displayName) {
        if (!enabled()) {
            return;
        }
        active = true;
        suppressTerrainStartUntilScreenClears = false;
        waitingForFirstWorldRender = false;
        waitingForFirstWorldRenderAt = 0L;
        title = "Loading world";
        worldName = clean(displayName);
        if (worldName.isEmpty()) {
            worldName = clean(folderName);
        }
        stage = "Preparing save";
        detail = "Opening local world data";
        progress = 8;
        startedAt = System.nanoTime();
        firstFrameLogged = false;
        GPOM.LOGGER.info("[WorldLoadingScreen] Started integrated world loading for {}", worldName.isEmpty() ? "<unnamed>" : worldName);
    }

    public static void beginTerrainIfNeeded() {
        if (!enabled()) {
            return;
        }
        if (!active) {
            if (!shouldStartTerrainOverlay()) {
                return;
            }
            active = true;
            suppressTerrainStartUntilScreenClears = false;
            waitingForFirstWorldRender = false;
            waitingForFirstWorldRenderAt = 0L;
            title = "Loading terrain";
            worldName = "";
            startedAt = System.nanoTime();
            firstFrameLogged = false;
            GPOM.LOGGER.info("[WorldLoadingScreen] Started terrain loading");
        }
        update("Receiving terrain", "Waiting for initial chunks", 88);
    }

    public static void beginDimensionSwitch(String fromWorld, String toWorld) {
        if (!enabled()) {
            return;
        }
        active = true;
        suppressTerrainStartUntilScreenClears = false;
        waitingForFirstWorldRender = false;
        waitingForFirstWorldRenderAt = 0L;
        title = "Changing dimension";
        worldName = dimensionSwitchLabel(fromWorld, toWorld);
        stage = "Preparing client world";
        detail = "Waiting for dimension handoff";
        progress = 48;
        startedAt = System.nanoTime();
        firstFrameLogged = false;
        GPOM.LOGGER.info("[WorldLoadingScreen] Started dimension switch overlay ({})", worldName.isEmpty() ? "unknown target" : worldName);
    }

    public static void beginLeaving(String message) {
        if (!enabled()) {
            return;
        }
        active = true;
        suppressTerrainStartUntilScreenClears = false;
        waitingForFirstWorldRender = false;
        waitingForFirstWorldRenderAt = 0L;
        title = "Leaving world";
        worldName = "";
        stage = "Saving and disconnecting";
        detail = clean(message);
        if (detail.isEmpty()) {
            detail = "Returning to main menu";
        }
        progress = -1;
        startedAt = System.nanoTime();
        firstFrameLogged = false;
        GPOM.LOGGER.info("[WorldLoadingScreen] Started world leave overlay ({})", detail);
    }

    public static void update(String newStage, String newDetail, int newProgress) {
        if (!enabled() || !active) {
            return;
        }
        if (newStage != null && !newStage.trim().isEmpty()) {
            stage = newStage.trim();
        }
        if (newDetail != null) {
            detail = newDetail.trim();
        }
        progress = mergeProgress(progress, newProgress);
    }

    public static void updateFromVanilla(String titleText, String messageText, int vanillaProgress) {
        if (!enabled() || !active) {
            return;
        }
        if (titleText != null && !titleText.trim().isEmpty()) {
            title = titleText.trim();
        }
        if (messageText != null && !messageText.trim().isEmpty()) {
            detail = messageText.trim();
        }
        if (vanillaProgress >= 0) {
            progress = mergeProgress(progress, vanillaProgress);
        }
    }

    public static void finish() {
        finish("client world ready");
    }

    public static void finish(String reason) {
        if (!active) {
            return;
        }
        long elapsedMs = startedAt == 0L ? 0L : (System.nanoTime() - startedAt) / 1_000_000L;
        stage = "Entering world";
        detail = "Handing control to the client";
        progress = 100;
        active = false;
        waitingForFirstWorldRender = false;
        waitingForFirstWorldRenderAt = 0L;
        suppressTerrainStartUntilScreenClears = true;
        GPOM.LOGGER.info("[WorldLoadingScreen] World loading finished after {} ms ({})", elapsedMs, reason == null ? "unknown" : reason);
    }

    public static void markWaitingForFirstWorldRender() {
        if (!enabled() || !active) {
            return;
        }
        if (!waitingForFirstWorldRender) {
            waitingForFirstWorldRender = true;
            waitingForFirstWorldRenderAt = System.nanoTime();
            GPOM.LOGGER.info("[WorldLoadingScreen] Waiting for first rendered world frame");
        }
        update("Rendering terrain", "Waiting for first terrain frame", 99);
    }

    public static void finishAfterFirstWorldRender(String reason) {
        if (!enabled() || !active || !waitingForFirstWorldRender) {
            return;
        }
        finish(reason);
    }

    public static boolean finishIfFirstWorldRenderTimedOut(long timeoutMs) {
        if (!enabled() || !active || !waitingForFirstWorldRender || timeoutMs <= 0L) {
            return false;
        }
        long waitingStartedAt = waitingForFirstWorldRenderAt;
        if (waitingStartedAt == 0L) {
            return false;
        }
        long elapsedMs = Math.max(0L, (System.nanoTime() - waitingStartedAt) / 1_000_000L);
        if (elapsedMs < timeoutMs) {
            return false;
        }
        finish("first world render timeout after " + elapsedMs + " ms");
        return true;
    }

    public static boolean shouldStartTerrainOverlay() {
        if (!enabled()) {
            return false;
        }
        if (!suppressTerrainStartUntilScreenClears) {
            return true;
        }
        Minecraft minecraft;
        try {
            minecraft = currentMinecraft();
        } catch (Throwable ignored) {
            return false;
        }
        GuiScreen screen = currentScreen(minecraft);
        if (screen instanceof GuiDownloadTerrain || screen instanceof GuiScreenWorking) {
            return false;
        }
        suppressTerrainStartUntilScreenClears = false;
        return true;
    }

    public static boolean render(Minecraft minecraft, int width, int height, int progressOverride) {
        if (minecraft == null) {
            return false;
        }
        if (!canRenderOverCurrentScreen(minecraft)) {
            return false;
        }
        Object font = fontRenderer(minecraft);
        if (font == null) {
            return false;
        }
        int[] dimensions = normalizedDimensions(minecraft, width, height);
        width = dimensions[0];
        height = dimensions[1];
        int effectiveProgress = effectiveProgress(progressOverride);
        setupFullScreenProjection(minecraft, width, height);
        drawFullWindow(minecraft, font, width, height, effectiveProgress);
        return true;
    }

    public static boolean safeRender(Minecraft minecraft, int width, int height, int progressOverride) {
        if (!enabled() || !canRenderOverCurrentScreen(minecraft)) {
            return false;
        }
        try {
            boolean rendered = render(minecraft, width, height, progressOverride);
            if (rendered) {
                markFirstFrame(false);
            }
            return rendered;
        } catch (Throwable throwable) {
            disabledAfterFailure = true;
            active = false;
            GPOM.LOGGER.warn("[WorldLoadingScreen] Disabled GPOM world loading overlay after render failure", throwable);
            return false;
        }
    }

    public static boolean safeRenderAndUpdate(Minecraft minecraft, int width, int height, int progressOverride) {
        if (!enabled() || !canRenderOverCurrentScreen(minecraft)) {
            return false;
        }
        try {
            if (!render(minecraft, width, height, progressOverride)) {
                return false;
            }
            updateDisplay(minecraft);
            markFirstFrame(true);
            return true;
        } catch (Throwable throwable) {
            disabledAfterFailure = true;
            active = false;
            GPOM.LOGGER.warn("[WorldLoadingScreen] Disabled GPOM world loading overlay after display update failure", throwable);
            return false;
        }
    }

    public static boolean safeRenderCurrentMinecraft(int progressOverride, boolean updateDisplay) {
        if (!enabled()) {
            return false;
        }
        try {
            Minecraft minecraft = currentMinecraft();
            if (minecraft == null) {
                return false;
            }
            if (!canRenderOverCurrentScreen(minecraft)) {
                return false;
            }
            int[] dimensions = currentScaledDimensions(minecraft);
            if (updateDisplay) {
                return safeRenderAndUpdate(minecraft, dimensions[0], dimensions[1], progressOverride);
            }
            return safeRender(minecraft, dimensions[0], dimensions[1], progressOverride);
        } catch (Throwable throwable) {
            disabledAfterFailure = true;
            active = false;
            GPOM.LOGGER.warn("[WorldLoadingScreen] Disabled GPOM world loading overlay after current-screen render failure", throwable);
            return false;
        }
    }

    private static void markFirstFrame(boolean displayUpdated) {
        if (firstFrameLogged || !active || startedAt == 0L) {
            return;
        }
        firstFrameLogged = true;
        long elapsedMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        GPOM.LOGGER.info(
                "[WorldLoadingScreen] First overlay frame after {} ms (stage={}, displayUpdated={})",
                elapsedMs,
                compact(displayStage(), 80),
                displayUpdated
        );
    }

    private static void drawFullWindow(Minecraft minecraft, Object font, int width, int height, int effectiveProgress) {
        drawDirtBackground(minecraft, width, height);
        drawRect(0, 0, width, height, 0x22000000);

        int contentWidth = Math.min(560, Math.max(260, width - 80));
        int x = (width - contentWidth) / 2;
        int centerY = Math.max(86, height / 2 - 36);
        drawRect(x - 18, centerY - 86, x + contentWidth + 18, centerY + 104, 0xAA000000);
        drawRect(x - 18, centerY - 86, x + contentWidth + 18, centerY - 85, 0xFF707070);
        drawRect(x - 18, centerY + 103, x + contentWidth + 18, centerY + 104, 0xFF1A1A1A);
        drawRect(x - 18, centerY - 86, x - 17, centerY + 104, 0xFF707070);
        drawRect(x + contentWidth + 17, centerY - 86, x + contentWidth + 18, centerY + 104, 0xFF1A1A1A);

        drawCentered(font, "GPOM World Loading", width / 2, centerY - 70, 0xE0E0E0);
        drawCentered(font, title, width / 2, centerY - 48, 0xFFFFFF);
        String world = worldName == null || worldName.isEmpty() ? "Local world" : worldName;
        drawCentered(font, compact(world, 70), width / 2, centerY - 32, 0xBDBDBD);

        drawBar(x, centerY, contentWidth, 14, effectiveProgress);
        drawCentered(font, progressText(effectiveProgress), width / 2, centerY + 22, 0xE8E8E8);

        drawCentered(font, compact(displayStage(), 72), width / 2, centerY + 46, 0xFFFFFF);
        drawCentered(font, compact(displayDetail(), 82), width / 2, centerY + 61, 0xBDBDBD);

        long elapsedSeconds = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000_000L);
        String elapsed = String.format(Locale.ROOT, "%ds elapsed", elapsedSeconds);
        drawCentered(font, elapsed, width / 2, centerY + 84, 0xA0A0A0);
    }

    private static void drawDirtBackground(Minecraft minecraft, int width, int height) {
        resetGuiColor();
        if (bindOptionsBackground(minecraft)) {
            drawTiledDirt(width, height);
            return;
        }
        if (drawGuiScreenDirtBackground(minecraft, width, height)) {
            return;
        }
        drawRect(0, 0, width, height, 0xFF202020);
    }

    private static void drawTiledDirt(int width, int height) {
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            float scale = 32.0F;
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, height / scale);
            GL11.glVertex3f(0.0F, height, 0.0F);
            GL11.glTexCoord2f(width / scale, height / scale);
            GL11.glVertex3f(width, height, 0.0F);
            GL11.glTexCoord2f(width / scale, 0.0F);
            GL11.glVertex3f(width, 0.0F, 0.0F);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex3f(0.0F, 0.0F, 0.0F);
            GL11.glEnd();
        } catch (Throwable ignored) {
            int tile = 32;
            for (int y = 0; y < height; y += tile) {
                for (int x = 0; x < width; x += tile) {
                    drawTexturedRect(x, y, Math.min(tile, width - x), Math.min(tile, height - y));
                }
            }
        }
    }

    private static void setupFullScreenProjection(Minecraft minecraft, int width, int height) {
        try {
            if (minecraft != null && minecraft.displayWidth > 0 && minecraft.displayHeight > 0) {
                GlStateManager.viewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
                GL11.glViewport(0, 0, minecraft.displayWidth, minecraft.displayHeight);
            }
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, width, height, 0.0D, 100.0D, 300.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();
            GL11.glTranslatef(0.0F, 0.0F, -200.0F);
            resetGuiColor();
        } catch (Throwable ignored) {
        }
    }

    private static boolean drawGuiScreenDirtBackground(Minecraft minecraft, int width, int height) {
        try {
            resetGuiColor();
            DIRT_BACKGROUND_SCREEN.draw(minecraft, width, height);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void resetGuiColor() {
        try {
            GlStateManager.enableTexture2D();
            GlStateManager.disableDepth();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } catch (Throwable ignored) {
        }
    }

    private static void drawBar(int x, int y, int width, int height, int effectiveProgress) {
        drawRect(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF777777);
        drawRect(x, y, x + width, y + height, 0xFF242424);
        if (effectiveProgress >= 0) {
            int filled = Math.min(width, Math.max(0, width * effectiveProgress / 100));
            drawRect(x, y, x + filled, y + height, 0xFF8BC34A);
            return;
        }

        long time = System.currentTimeMillis();
        int segment = Math.max(42, width / 4);
        int span = width + segment;
        int offset = (int) ((time / 4L) % span) - segment;
        int left = Math.max(x, x + offset);
        int right = Math.min(x + width, x + offset + segment);
        if (right > left) {
            drawRect(left, y, right, y + height, 0xFF8BC34A);
        }
    }

    private static String progressText(int effectiveProgress) {
        if (effectiveProgress < 0) {
            return "Working";
        }
        return effectiveProgress + "%";
    }

    private static void drawCentered(Object font, String text, int x, int y, int color) {
        drawString(font, text, x - stringWidth(font, text) / 2, y, color);
    }

    private static Object fontRenderer(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = fontRendererField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71466_p", "fontRenderer");
                fontRendererField = field;
            }
            return field.get(minecraft);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Method method = guiDrawRectMethod;
            if (method == null) {
                method = findMethod(Gui.class, new Class<?>[]{int.class, int.class, int.class, int.class, int.class}, "func_73734_a", "drawRect");
                guiDrawRectMethod = method;
            }
            method.invoke(null, left, top, right, bottom, color);
        } catch (Throwable ignored) {
        }
    }

    private static void drawTexturedRect(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        try {
            Method method = guiTexturedRectMethod;
            if (method == null) {
                method = findMethod(
                        Gui.class,
                        new Class<?>[]{int.class, int.class, float.class, float.class, int.class, int.class, float.class, float.class},
                        "func_152125_a",
                        "drawModalRectWithCustomSizedTexture"
                );
                guiTexturedRectMethod = method;
            }
            method.invoke(null, x, y, 0.0F, 0.0F, width, height, 32.0F, 32.0F);
        } catch (Throwable ignored) {
            drawRect(x, y, x + width, y + height, 0xFF202020);
        }
    }

    private static boolean bindOptionsBackground(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            resetGuiColor();
            Method textureManagerMethod = minecraftGetTextureManagerMethod;
            if (textureManagerMethod == null) {
                textureManagerMethod = findMethod(Minecraft.class, new Class<?>[0], "func_110434_K", "getTextureManager");
                minecraftGetTextureManagerMethod = textureManagerMethod;
            }
            Object textureManager = textureManagerMethod.invoke(minecraft);
            if (textureManager == null) {
                return false;
            }
            Method bindMethod = textureManagerBindTextureMethod;
            if (bindMethod == null) {
                bindMethod = findMethod(textureManager.getClass(), new Class<?>[]{ResourceLocation.class}, "func_110577_a", "bindTexture");
                textureManagerBindTextureMethod = bindMethod;
            }
            bindMethod.invoke(textureManager, OPTIONS_BACKGROUND);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void updateDisplay(Minecraft minecraft) throws ReflectiveOperationException {
        if (minecraft == null) {
            return;
        }
        Method method = minecraftUpdateDisplayMethod;
        if (method == null) {
            method = findMethod(Minecraft.class, new Class<?>[0], "func_175601_h", "updateDisplay");
            minecraftUpdateDisplayMethod = method;
        }
        method.invoke(minecraft);
    }

    private static void drawString(Object font, String text, int x, int y, int color) {
        if (font == null || text == null) {
            return;
        }
        try {
            Method method = fontDrawStringMethod;
            if (method == null) {
                method = findMethod(font.getClass(), new Class<?>[]{String.class, int.class, int.class, int.class}, "func_78276_b", "drawString");
                fontDrawStringMethod = method;
            }
            method.invoke(font, text, x, y, color);
        } catch (Throwable ignored) {
        }
    }

    private static int stringWidth(Object font, String text) {
        if (font == null || text == null) {
            return 0;
        }
        try {
            Method method = fontGetStringWidthMethod;
            if (method == null) {
                method = findMethod(font.getClass(), new Class<?>[]{String.class}, "func_78256_a", "getStringWidth");
                fontGetStringWidthMethod = method;
            }
            Object result = method.invoke(font, text);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(String.join("/", names));
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = owner.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
            try {
                Method method = owner.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + "#" + String.join("/", names));
    }

    private static int[] normalizedDimensions(Minecraft minecraft, int width, int height) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft);
            int scaledWidth = resolution.getScaledWidth();
            int scaledHeight = resolution.getScaledHeight();
            if (scaledWidth > 0 && scaledHeight > 0) {
                return new int[]{scaledWidth, scaledHeight};
            }
        } catch (Throwable ignored) {
        }
        if (width > 0 && height > 0) {
            return new int[]{width, height};
        }
        return new int[]{Math.max(1, width), Math.max(1, height)};
    }

    private static Minecraft currentMinecraft() throws ReflectiveOperationException {
        Method method = minecraftGetMinecraftMethod;
        if (method == null) {
            method = findMethod(Minecraft.class, new Class<?>[0], "func_71410_x", "getMinecraft");
            minecraftGetMinecraftMethod = method;
        }
        Object value = method.invoke(null);
        return value instanceof Minecraft ? (Minecraft) value : null;
    }

    private static boolean canRenderOverCurrentScreen(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        GuiScreen screen = currentScreen(minecraft);
        return active || screen == null || screen instanceof GuiScreenWorking || screen instanceof GuiDownloadTerrain;
    }

    private static GuiScreen currentScreen(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = minecraftCurrentScreenField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71462_r", "currentScreen");
                minecraftCurrentScreenField = field;
            }
            Object value = field.get(minecraft);
            return value instanceof GuiScreen ? (GuiScreen) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] currentScaledDimensions(Minecraft minecraft) {
        try {
            ScaledResolution resolution = new ScaledResolution(minecraft);
            int width = invokeScaledDimension(resolution, true);
            int height = invokeScaledDimension(resolution, false);
            if (width > 0 && height > 0) {
                return new int[]{width, height};
            }
        } catch (Throwable ignored) {
        }
        return new int[]{1, 1};
    }

    private static int invokeScaledDimension(ScaledResolution resolution, boolean width) throws ReflectiveOperationException {
        Method method = width ? scaledResolutionGetWidthMethod : scaledResolutionGetHeightMethod;
        if (method == null) {
            method = findMethod(
                    ScaledResolution.class,
                    new Class<?>[0],
                    width ? "func_78326_a" : "func_78328_b",
                    width ? "getScaledWidth" : "getScaledHeight"
            );
            if (width) {
                scaledResolutionGetWidthMethod = method;
            } else {
                scaledResolutionGetHeightMethod = method;
            }
        }
        Object value = method.invoke(resolution);
        return value instanceof Number ? Math.max(1, ((Number) value).intValue()) : 1;
    }

    private static int clampProgress(int value) {
        if (value < 0) {
            return -1;
        }
        return Math.min(100, value);
    }

    private static int mergeProgress(int oldProgress, int newProgress) {
        int clamped = clampProgress(newProgress);
        if (clamped < 0) {
            return oldProgress;
        }
        return Math.max(clampProgress(oldProgress), clamped);
    }

    private static int effectiveProgress(int progressOverride) {
        int base = progress;
        if (progressOverride >= 0) {
            base = Math.max(base, clampProgress(progressOverride));
        }
        if (!active || startedAt == 0L || base >= 96) {
            return clampProgress(base);
        }
        long elapsedMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        int timed = Math.min(94, 8 + (int) (elapsedMs / 360L));
        return Math.max(clampProgress(base), timed);
    }

    private static String displayStage() {
        long elapsedMs = startedAt == 0L ? 0L : Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        int currentProgress = progress;
        if (currentProgress <= 18) {
            if (elapsedMs >= 22_000L) {
                return "Joining local server";
            }
            if (elapsedMs >= 14_000L) {
                return "Preparing world data";
            }
            if (elapsedMs >= 7_000L) {
                return "Loading dimensions";
            }
            if (elapsedMs >= 3_000L) {
                return "Starting integrated server";
            }
        }
        return stage;
    }

    private static String displayDetail() {
        long elapsedMs = startedAt == 0L ? 0L : Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        int currentProgress = progress;
        if (currentProgress <= 18) {
            if (elapsedMs >= 22_000L) {
                return "Waiting for the client handoff and first world frame";
            }
            if (elapsedMs >= 14_000L) {
                return "Running server-started hooks and preparing save data";
            }
            if (elapsedMs >= 7_000L) {
                return "Loading dimensions, spawn chunks, and world providers";
            }
            if (elapsedMs >= 3_000L) {
                return "Server thread is initializing the local world";
            }
        }
        return detail;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String dimensionSwitchLabel(String fromWorld, String toWorld) {
        String from = compact(clean(fromWorld), 42);
        String to = compact(clean(toWorld), 42);
        if (from.isEmpty() && to.isEmpty()) {
            return "";
        }
        if (from.isEmpty()) {
            return to;
        }
        if (to.isEmpty()) {
            return from;
        }
        if (from.equals(to)) {
            return to;
        }
        return from + " -> " + to;
    }

    private static String compact(String value, int max) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static final class DirtBackgroundScreen extends GuiScreen {
        private void draw(Minecraft minecraft, int screenWidth, int screenHeight) {
            this.mc = minecraft;
            this.width = Math.max(1, screenWidth);
            this.height = Math.max(1, screenHeight);
            drawBackground(0);
        }
    }
}
