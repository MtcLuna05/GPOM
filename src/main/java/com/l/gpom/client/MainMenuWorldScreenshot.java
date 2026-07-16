package com.l.gpom.client;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainMenuWorldScreenshot {
    private static final Object LOCK = new Object();
    private static final AtomicInteger WRITER_ID = new AtomicInteger();
    private static final String DIRECTORY_NAME = "gpom";
    private static final String FILE_NAME = "main-menu-world.png";

    private static volatile BufferedImage capturedImage;
    private static volatile DynamicTexture texture;
    private static volatile ResourceLocation textureLocation;
    private static volatile int textureWidth;
    private static volatile int textureHeight;
    private static volatile long lastCaptureMillis;
    private static volatile Method getFramebufferMethod;
    private static volatile Method createScreenshotMethod;
    private static volatile Method getTextureManagerMethod;
    private static volatile Method getDynamicTextureLocationMethod;
    private static volatile Method deleteTextureMethod;
    private static volatile Field mcDataDirField;

    private MainMenuWorldScreenshot() {
    }

    public static void captureBeforeWorldLeaves(String reason) {
        if (!GpomEarlyConfig.mainMenuWorldScreenshotEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastCaptureMillis < 1000L) {
            return;
        }
        lastCaptureMillis = now;

        Minecraft minecraft = ClientAccess.minecraft();
        if (minecraft == null || !ClientAccess.isMinecraftThread(minecraft)) {
            return;
        }
        int width = ClientAccess.displayWidth(minecraft);
        int height = ClientAccess.displayHeight(minecraft);
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            Framebuffer framebuffer = framebuffer(minecraft);
            if (framebuffer == null) {
                return;
            }
            ClientAccess.renderWorldOnly(minecraft);
            BufferedImage image = createScreenshot(width, height, framebuffer);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                return;
            }
            image = scaledForStorage(image, GpomEarlyConfig.mainMenuWorldScreenshotMaxWidth());
            synchronized (LOCK) {
                capturedImage = image;
                clearTexture(minecraft);
            }
            writeAsync(minecraftDataDir(minecraft), image, reason);
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[MainMenuWorldScreenshot] Failed to capture world screenshot before {}", reason, throwable);
            }
        }
    }

    public static boolean renderBackground(Gui gui) {
        if (!GpomEarlyConfig.mainMenuWorldScreenshotEnabled() || gui == null) {
            return false;
        }
        int width = screenIntField(gui, "field_146294_l", "width");
        int height = screenIntField(gui, "field_146295_m", "height");
        if (width <= 0 || height <= 0) {
            return false;
        }
        Minecraft minecraft = ClientAccess.minecraft();
        if (minecraft == null) {
            return false;
        }
        ResourceLocation texture = textureLocation(minecraft);
        if (texture == null || textureWidth <= 0 || textureHeight <= 0) {
            return false;
        }

        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            ClientAccess.bindTexture(minecraft, texture);
            drawCover(gui, width, height, textureWidth, textureHeight);
            ClientAccess.drawRect(0, 0, width, height, 0x66000000);
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try {
                GL11.glPopAttrib();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void drawCover(Gui gui, int width, int height, int imageWidth, int imageHeight) {
        float screenAspect = (float) width / (float) height;
        float imageAspect = (float) imageWidth / (float) imageHeight;
        float sourceX = 0.0F;
        float sourceY = 0.0F;
        int sourceWidth = imageWidth;
        int sourceHeight = imageHeight;
        if (imageAspect > screenAspect) {
            sourceWidth = Math.max(1, Math.round(imageHeight * screenAspect));
            sourceX = (imageWidth - sourceWidth) / 2.0F;
        } else if (imageAspect < screenAspect) {
            sourceHeight = Math.max(1, Math.round(imageWidth / screenAspect));
            sourceY = (imageHeight - sourceHeight) / 2.0F;
        }
        ClientAccess.drawScaledCustomSizeModalRect(gui, 0, 0, sourceX, sourceY, sourceWidth, sourceHeight,
                width, height, imageWidth, imageHeight);
    }

    private static BufferedImage scaledForStorage(BufferedImage image, int maxWidth) {
        if (maxWidth <= 0 || image.getWidth() <= maxWidth) {
            return image;
        }
        int width = maxWidth;
        int height = Math.max(1, Math.round(image.getHeight() * (maxWidth / (float) image.getWidth())));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static ResourceLocation textureLocation(Minecraft minecraft) {
        synchronized (LOCK) {
            if (textureLocation != null) {
                return textureLocation;
            }
            BufferedImage image = capturedImage;
            if (image == null) {
                image = readSavedImage(minecraftDataDir(minecraft));
                capturedImage = image;
            }
            if (image == null) {
                return null;
            }
            Object textureManager = textureManager(minecraft);
            if (textureManager == null) {
                return null;
            }
            try {
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                Method method = getDynamicTextureLocationMethod;
                if (method == null) {
                    method = findMethod(textureManager.getClass(), new Class<?>[]{String.class, DynamicTexture.class},
                            "func_110578_a", "getDynamicTextureLocation");
                    getDynamicTextureLocationMethod = method;
                }
                Object value = method == null ? null : method.invoke(textureManager, "gpom_world_menu", dynamicTexture);
                if (!(value instanceof ResourceLocation)) {
                    return null;
                }
                texture = dynamicTexture;
                textureLocation = (ResourceLocation) value;
                textureWidth = image.getWidth();
                textureHeight = image.getHeight();
                return textureLocation;
            } catch (Throwable throwable) {
                return null;
            }
        }
    }

    private static void clearTexture(Minecraft minecraft) {
        ResourceLocation oldLocation = textureLocation;
        texture = null;
        textureLocation = null;
        textureWidth = 0;
        textureHeight = 0;
        if (oldLocation == null) {
            return;
        }
        Object textureManager = textureManager(minecraft);
        if (textureManager == null) {
            return;
        }
        try {
            Method method = deleteTextureMethod;
            if (method == null) {
                method = findMethod(textureManager.getClass(), new Class<?>[]{ResourceLocation.class},
                        "func_147645_c", "deleteTexture");
                deleteTextureMethod = method;
            }
            if (method != null) {
                method.invoke(textureManager, oldLocation);
            }
        } catch (Throwable ignored) {
        }
    }

    private static BufferedImage readSavedImage(File dataDir) {
        if (dataDir == null) {
            return null;
        }
        File file = imageFile(dataDir);
        if (!file.isFile()) {
            return null;
        }
        try {
            return ImageIO.read(file);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void writeAsync(File dataDir, BufferedImage image, String reason) {
        if (dataDir == null || image == null) {
            return;
        }
        BufferedImage copy = copyImage(image);
        Thread writer = new Thread(() -> {
            try {
                File file = imageFile(dataDir);
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    return;
                }
                ImageIO.write(copy, "png", file);
            } catch (Throwable throwable) {
                if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[MainMenuWorldScreenshot] Failed to write captured world screenshot after {}", reason, throwable);
                }
            }
        }, "GPOM-MainMenuWorldScreenshot-Writer-" + WRITER_ID.incrementAndGet());
        writer.setDaemon(true);
        writer.setContextClassLoader(null);
        writer.start();
    }

    private static BufferedImage copyImage(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static File imageFile(File dataDir) {
        return new File(new File(dataDir, DIRECTORY_NAME), FILE_NAME);
    }

    private static File minecraftDataDir(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = mcDataDirField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71412_D", "mcDataDir");
                mcDataDirField = field;
            }
            Object value = field == null ? null : field.get(minecraft);
            return value instanceof File ? (File) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int screenIntField(Object screen, String srgName, String mcpName) {
        try {
            Field field = findField(screen.getClass(), srgName, mcpName);
            Object value = field == null ? null : field.get(screen);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Framebuffer framebuffer(Minecraft minecraft) throws ReflectiveOperationException {
        Method method = getFramebufferMethod;
        if (method == null) {
            method = findMethod(Minecraft.class, new Class<?>[0], "func_147110_a", "getFramebuffer");
            getFramebufferMethod = method;
        }
        Object value = method == null ? null : method.invoke(minecraft);
        return value instanceof Framebuffer ? (Framebuffer) value : null;
    }

    private static BufferedImage createScreenshot(int width, int height, Framebuffer framebuffer) throws ReflectiveOperationException {
        Method method = createScreenshotMethod;
        if (method == null) {
            method = findMethod(ScreenShotHelper.class, new Class<?>[]{int.class, int.class, Framebuffer.class},
                    "func_186719_a", "createScreenshot");
            createScreenshotMethod = method;
        }
        Object value = method == null ? null : method.invoke(null, width, height, framebuffer);
        return value instanceof BufferedImage ? (BufferedImage) value : null;
    }

    private static Object textureManager(Minecraft minecraft) {
        try {
            Method method = getTextureManagerMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[0], "func_110434_K", "getTextureManager");
                getTextureManagerMethod = method;
            }
            return method == null ? null : method.invoke(minecraft);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String... names) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameters, String... names) {
        for (String name : names) {
            try {
                Method method = type.getDeclaredMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
