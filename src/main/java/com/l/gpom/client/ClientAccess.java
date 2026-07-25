package com.l.gpom.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.Display;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ClientAccess {
    private static volatile Method getMinecraftMethod;
    private static volatile Method isMinecraftThreadMethod;
    private static volatile Method addScheduledTaskMethod;
    private static volatile Method displayGuiScreenMethod;
    private static volatile Method fontWidthMethod;
    private static volatile Method drawStringWithShadowMethod;
    private static volatile Method drawRectMethod;
    private static volatile Method drawGradientRectMethod;
    private static volatile Method drawTexturedModalRectMethod;
    private static volatile Method drawModalRectWithCustomSizedTextureMethod;
    private static volatile Method drawScaledCustomSizeModalRectMethod;
    private static volatile Method getTextureManagerMethod;
    private static volatile Method getBlockRendererDispatcherMethod;
    private static volatile Method getTextureMapBlocksMethod;
    private static volatile Method getModelForStateMethod;
    private static volatile Method getMissingSpriteMethod;
    private static volatile Method getAtlasSpriteMethod;
    private static volatile Method bindTextureMethod;
    private static volatile Method drawInventoryEntityMethod;
    private static volatile Method getRenderItemMethod;
    private static volatile Method renderItemOverlayMethod;
    private static volatile Method renderWorldMethod;
    private static volatile Method loadRenderersMethod;
    private static volatile Method i18nFormatMethod;
    private static volatile Method windowClickMethod;
    private static volatile Method resizeMethod;
    private static volatile Method inventoryCarriedStackMethod;
    private static volatile Field fontRendererField;
    private static volatile Field displayWidthField;
    private static volatile Field displayHeightField;
    private static volatile Field playerField;
    private static volatile Field currentScreenField;
    private static volatile Field playerControllerField;
    private static volatile Field playerInventoryField;
    private static volatile Field entityRendererField;
    private static volatile Field renderGlobalField;
    private static volatile Field worldField;

    private ClientAccess() {
    }

    public static Minecraft minecraft() {
        try {
            Method method = getMinecraftMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[0], "func_71410_x", "getMinecraft");
                getMinecraftMethod = method;
            }
            Object value = method == null ? null : method.invoke(null);
            return value instanceof Minecraft ? (Minecraft) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isMinecraftThread(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            Method method = isMinecraftThreadMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[0], "func_152345_ab", "isCallingFromMinecraftThread");
                isMinecraftThreadMethod = method;
            }
            Object value = method == null ? null : method.invoke(minecraft);
            return value instanceof Boolean ? (Boolean) value : "Client thread".equals(Thread.currentThread().getName());
        } catch (Throwable ignored) {
            return "Client thread".equals(Thread.currentThread().getName());
        }
    }

    public static boolean schedule(Minecraft minecraft, Runnable task) {
        if (minecraft == null || task == null) {
            return false;
        }
        try {
            Method method = addScheduledTaskMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[] {Runnable.class}, "func_152344_a", "addScheduledTask");
                addScheduledTaskMethod = method;
            }
            if (method == null) {
                return false;
            }
            method.invoke(minecraft, task);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean displayGuiScreen(Minecraft minecraft, GuiScreen screen) {
        if (minecraft == null) {
            return false;
        }
        try {
            Method method = displayGuiScreenMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[] {GuiScreen.class}, "func_147108_a", "displayGuiScreen");
                displayGuiScreenMethod = method;
            }
            if (method != null) {
                method.invoke(minecraft, screen);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static FontRenderer fontRenderer(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = fontRendererField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71466_p", "fontRenderer");
                fontRendererField = field;
            }
            Object value = field == null ? null : field.get(minecraft);
            return value instanceof FontRenderer ? (FontRenderer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static Object currentScreen(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = currentScreenField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71462_r", "currentScreen");
                currentScreenField = field;
            }
            return field == null ? null : field.get(minecraft);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static IBlockAccess world(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = worldField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71441_e", "world");
                worldField = field;
            }
            Object value = field == null ? null : field.get(minecraft);
            return value instanceof IBlockAccess ? (IBlockAccess) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void reloadRenderers(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        try {
            Field field = renderGlobalField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71438_f", "renderGlobal");
                renderGlobalField = field;
            }
            Object value = field == null ? null : field.get(minecraft);
            if (!(value instanceof RenderGlobal)) {
                return;
            }
            Method method = loadRenderersMethod;
            if (method == null) {
                method = findMethod(RenderGlobal.class, new Class<?>[0], "func_72712_a", "loadRenderers");
                loadRenderersMethod = method;
            }
            if (method != null) {
                method.invoke(value);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int displayWidth(Minecraft minecraft) {
        return intField(minecraft, true);
    }

    public static int displayHeight(Minecraft minecraft) {
        return intField(minecraft, false);
    }

    public static void syncDisplayResize(Minecraft minecraft) {
        if (minecraft == null || !isMinecraftThread(minecraft)) {
            return;
        }
        try {
            if (!Display.isCreated()) {
                return;
            }
            int width = Math.max(1, Display.getWidth());
            int height = Math.max(1, Display.getHeight());
            if (width == displayWidth(minecraft) && height == displayHeight(minecraft)) {
                return;
            }
            Method method = resizeMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[] {int.class, int.class}, "func_71370_a", "resize");
                resizeMethod = method;
            }
            if (method != null) {
                method.invoke(minecraft, width, height);
            }
        } catch (Throwable ignored) {
        }
    }

    public static int stringWidth(FontRenderer font, String text) {
        if (font == null || text == null) {
            return 0;
        }
        try {
            Method method = fontWidthMethod;
            if (method == null) {
                method = findMethod(FontRenderer.class, new Class<?>[] {String.class}, "func_78256_a", "getStringWidth");
                fontWidthMethod = method;
            }
            Object value = method == null ? null : method.invoke(font, text);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void drawStringWithShadow(FontRenderer font, String text, float x, float y, int color) {
        if (font == null || text == null) {
            return;
        }
        try {
            Method method = drawStringWithShadowMethod;
            if (method == null) {
                method = findMethod(FontRenderer.class, new Class<?>[] {String.class, float.class, float.class, int.class}, "func_175063_a", "drawStringWithShadow");
                drawStringWithShadowMethod = method;
            }
            if (method != null) {
                method.invoke(font, text, x, y, color);
            }
        } catch (Throwable ignored) {
        }
    }

    public static String i18nFormat(String key, Object... args) {
        if (key == null) {
            return "";
        }
        try {
            Method method = i18nFormatMethod;
            if (method == null) {
                method = findMethod(I18n.class, new Class<?>[] {String.class, Object[].class}, "func_135052_a", "format");
                i18nFormatMethod = method;
            }
            Object value = method == null ? null : method.invoke(null, new Object[] {key, args == null ? new Object[0] : args});
            return value instanceof String ? (String) value : key;
        } catch (Throwable ignored) {
            return key;
        }
    }

    public static void drawRect(int left, int top, int right, int bottom, int color) {
        try {
            Method method = drawRectMethod;
            if (method == null) {
                method = findMethod(Gui.class, new Class<?>[] {int.class, int.class, int.class, int.class, int.class}, "func_73734_a", "drawRect");
                drawRectMethod = method;
            }
            if (method != null) {
                method.invoke(null, left, top, right, bottom, color);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void drawGradientRect(Gui gui, int left, int top, int right, int bottom, int startColor, int endColor) {
        if (gui == null) {
            return;
        }
        try {
            Method method = drawGradientRectMethod;
            if (method == null) {
                method = findMethod(Gui.class, new Class<?>[] {int.class, int.class, int.class, int.class, int.class, int.class}, "func_73733_a", "drawGradientRect");
                drawGradientRectMethod = method;
            }
            if (method != null) {
                method.invoke(gui, left, top, right, bottom, startColor, endColor);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void drawTexturedModalRect(Gui gui, int x, int y, int textureX, int textureY, int width, int height) {
        if (gui == null) {
            return;
        }
        try {
            Method method = drawTexturedModalRectMethod;
            if (method == null) {
                method = findMethod(Gui.class, new Class<?>[] {int.class, int.class, int.class, int.class, int.class, int.class}, "func_73729_b", "drawTexturedModalRect");
                drawTexturedModalRectMethod = method;
            }
            if (method != null) {
                method.invoke(gui, x, y, textureX, textureY, width, height);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void drawModalRectWithCustomSizedTexture(Gui gui,
                                                           int x,
                                                           int y,
                                                           float textureX,
                                                           float textureY,
                                                           int width,
                                                           int height,
                                                           float textureWidth,
                                                           float textureHeight) {
        if (gui == null) {
            return;
        }
        try {
            Method method = drawModalRectWithCustomSizedTextureMethod;
            if (method == null) {
                method = findMethod(Gui.class,
                        new Class<?>[] {int.class, int.class, float.class, float.class, int.class, int.class, float.class, float.class},
                        "func_146110_a",
                        "drawModalRectWithCustomSizedTexture");
                drawModalRectWithCustomSizedTextureMethod = method;
            }
            if (method != null) {
                method.invoke(null, x, y, textureX, textureY, width, height, textureWidth, textureHeight);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void drawScaledCustomSizeModalRect(Gui gui,
                                                     int x,
                                                     int y,
                                                     float textureX,
                                                     float textureY,
                                                     int sourceWidth,
                                                     int sourceHeight,
                                                     int width,
                                                     int height,
                                                     float textureWidth,
                                                     float textureHeight) {
        if (gui == null) {
            return;
        }
        try {
            Method method = drawScaledCustomSizeModalRectMethod;
            if (method == null) {
                method = findMethod(Gui.class,
                        new Class<?>[] {
                                int.class,
                                int.class,
                                float.class,
                                float.class,
                                int.class,
                                int.class,
                                int.class,
                                int.class,
                                float.class,
                                float.class
                        },
                        "func_152125_a",
                        "drawScaledCustomSizeModalRect");
                drawScaledCustomSizeModalRectMethod = method;
            }
            if (method != null) {
                method.invoke(null, x, y, textureX, textureY, sourceWidth, sourceHeight, width, height, textureWidth, textureHeight);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void bindTexture(Minecraft minecraft, ResourceLocation texture) {
        if (minecraft == null || texture == null) {
            return;
        }
        try {
            Method textureManager = getTextureManagerMethod;
            if (textureManager == null) {
                textureManager = findMethod(Minecraft.class, new Class<?>[0], "func_110434_K", "getTextureManager");
                getTextureManagerMethod = textureManager;
            }
            Object manager = textureManager == null ? null : textureManager.invoke(minecraft);
            if (manager == null) {
                return;
            }

            Method bind = bindTextureMethod;
            if (bind == null) {
                bind = findMethod(manager.getClass(), new Class<?>[] {ResourceLocation.class}, "func_110577_a", "bindTexture");
                bindTextureMethod = bind;
            }
            if (bind != null) {
                bind.invoke(manager, texture);
            }
        } catch (Throwable ignored) {
        }
    }

    public static IBakedModel modelForState(Minecraft minecraft, IBlockState state) {
        if (minecraft == null || state == null) {
            return null;
        }
        try {
            Method dispatcherMethod = getBlockRendererDispatcherMethod;
            if (dispatcherMethod == null) {
                dispatcherMethod = findMethod(Minecraft.class, new Class<?>[0], "func_175602_ab", "getBlockRendererDispatcher");
                getBlockRendererDispatcherMethod = dispatcherMethod;
            }
            Object dispatcher = dispatcherMethod == null ? null : dispatcherMethod.invoke(minecraft);
            if (!(dispatcher instanceof BlockRendererDispatcher)) {
                return null;
            }
            Method modelMethod = getModelForStateMethod;
            if (modelMethod == null) {
                modelMethod = findMethod(BlockRendererDispatcher.class, new Class<?>[] {IBlockState.class}, "func_175023_a", "getModelForState");
                getModelForStateMethod = modelMethod;
            }
            Object model = modelMethod == null ? null : modelMethod.invoke(dispatcher, state);
            return model instanceof IBakedModel ? (IBakedModel) model : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static TextureAtlasSprite missingSprite(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Method mapMethod = getTextureMapBlocksMethod;
            if (mapMethod == null) {
                mapMethod = findMethod(Minecraft.class, new Class<?>[0], "func_147117_R", "getTextureMapBlocks");
                getTextureMapBlocksMethod = mapMethod;
            }
            Object textureMap = mapMethod == null ? null : mapMethod.invoke(minecraft);
            if (!(textureMap instanceof TextureMap)) {
                return null;
            }
            Method missingMethod = getMissingSpriteMethod;
            if (missingMethod == null) {
                missingMethod = findMethod(TextureMap.class, new Class<?>[0], "func_174944_f", "getMissingSprite");
                getMissingSpriteMethod = missingMethod;
            }
            Object sprite = missingMethod == null ? null : missingMethod.invoke(textureMap);
            return sprite instanceof TextureAtlasSprite ? (TextureAtlasSprite) sprite : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static TextureAtlasSprite atlasSprite(Minecraft minecraft, String name) {
        if (minecraft == null || name == null || name.isEmpty()) {
            return null;
        }
        try {
            Method mapMethod = getTextureMapBlocksMethod;
            if (mapMethod == null) {
                mapMethod = findMethod(Minecraft.class, new Class<?>[0], "func_147117_R", "getTextureMapBlocks");
                getTextureMapBlocksMethod = mapMethod;
            }
            Object textureMap = mapMethod == null ? null : mapMethod.invoke(minecraft);
            if (!(textureMap instanceof TextureMap)) {
                return null;
            }
            Method spriteMethod = getAtlasSpriteMethod;
            if (spriteMethod == null) {
                spriteMethod = findMethod(TextureMap.class, new Class<?>[] {String.class},
                        "func_110572_b", "getAtlasSprite");
                getAtlasSpriteMethod = spriteMethod;
            }
            Object sprite = spriteMethod == null ? null : spriteMethod.invoke(textureMap, name);
            return sprite instanceof TextureAtlasSprite ? (TextureAtlasSprite) sprite : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static EntityLivingBase player(Minecraft minecraft) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = playerField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71439_g", "player");
                playerField = field;
            }
            Object value = field == null ? null : field.get(minecraft);
            return value instanceof EntityLivingBase ? (EntityLivingBase) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void drawInventoryEntity(int x, int y, int scale, float mouseX, float mouseY, EntityLivingBase entity) {
        if (entity == null) {
            return;
        }
        try {
            Method method = drawInventoryEntityMethod;
            if (method == null) {
                method = findMethod(GuiInventory.class,
                        new Class<?>[] {int.class, int.class, int.class, float.class, float.class, EntityLivingBase.class},
                        "func_147046_a",
                        "drawEntityOnScreen");
                drawInventoryEntityMethod = method;
            }
            if (method != null) {
                method.invoke(null, x, y, scale, mouseX, mouseY, entity);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void renderItemOverlayIntoGui(Minecraft minecraft,
                                                FontRenderer font,
                                                ItemStack stack,
                                                int x,
                                                int y,
                                                String text) {
        if (minecraft == null || font == null || stack == null) {
            return;
        }

        try {
            Method getRenderItem = getRenderItemMethod;
            if (getRenderItem == null) {
                getRenderItem = findMethod(Minecraft.class, new Class<?>[0], "func_175599_af", "getRenderItem");
                getRenderItemMethod = getRenderItem;
            }
            Object renderer = getRenderItem == null ? null : getRenderItem.invoke(minecraft);
            if (!(renderer instanceof RenderItem)) {
                return;
            }

            Method overlay = renderItemOverlayMethod;
            if (overlay == null) {
                overlay = findMethod(RenderItem.class,
                        new Class<?>[] {FontRenderer.class, ItemStack.class, int.class, int.class, String.class},
                        "func_180453_a",
                        "renderItemOverlayIntoGUI");
                renderItemOverlayMethod = overlay;
            }
            if (overlay != null) {
                overlay.invoke(renderer, font, stack, x, y, text);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean renderWorldOnly(Minecraft minecraft) {
        if (minecraft == null) {
            return false;
        }
        try {
            Field field = entityRendererField;
            if (field == null) {
                field = findField(Minecraft.class, "field_71460_t", "entityRenderer");
                entityRendererField = field;
            }
            Object renderer = field == null ? null : field.get(minecraft);
            if (!(renderer instanceof EntityRenderer)) {
                return false;
            }
            Method method = renderWorldMethod;
            if (method == null) {
                method = findMethod(EntityRenderer.class, new Class<?>[] {float.class, long.class},
                        "func_78471_a",
                        "renderWorld");
                renderWorldMethod = method;
            }
            if (method == null) {
                return false;
            }
            method.invoke(renderer, 1.0F, System.nanoTime());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean windowClick(Minecraft minecraft, int windowId, int slotNumber, int mouseButton, ClickType clickType) {
        if (minecraft == null || clickType == null) {
            return false;
        }

        EntityLivingBase living = player(minecraft);
        if (!(living instanceof EntityPlayer)) {
            return false;
        }

        try {
            Object controller = playerController(minecraft);
            if (controller == null) {
                return false;
            }

            Method method = windowClickMethod;
            if (method == null) {
                method = findMethod(
                        controller.getClass(),
                        new Class<?>[] {int.class, int.class, int.class, ClickType.class, EntityPlayer.class},
                        "func_187098_a",
                        "windowClick"
                );
                windowClickMethod = method;
            }
            if (method == null) {
                return false;
            }

            method.invoke(controller, windowId, slotNumber, mouseButton, clickType, living);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static ItemStack carriedStack(Minecraft minecraft) {
        EntityLivingBase living = player(minecraft);
        if (!(living instanceof EntityPlayer)) {
            return null;
        }

        try {
            Field inventoryField = playerInventoryField;
            if (inventoryField == null) {
                inventoryField = findField(EntityPlayer.class, "field_71071_by", "inventory");
                playerInventoryField = inventoryField;
            }
            Object inventory = inventoryField == null ? null : inventoryField.get(living);
            if (!(inventory instanceof InventoryPlayer)) {
                return null;
            }

            Method method = inventoryCarriedStackMethod;
            if (method == null) {
                method = findMethod(InventoryPlayer.class, new Class<?>[0], "func_70445_o", "getItemStack");
                inventoryCarriedStackMethod = method;
            }
            Object value = method == null ? null : method.invoke(inventory);
            return value instanceof ItemStack ? (ItemStack) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object playerController(Minecraft minecraft) throws IllegalAccessException {
        Field field = playerControllerField;
        if (field == null) {
            field = findField(Minecraft.class, "field_71442_b", "playerController");
            playerControllerField = field;
        }
        return field == null ? null : field.get(minecraft);
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameters, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameters);
                method.setAccessible(true);
                return method;
            } catch (Throwable ignored) {
            }
        }
        return null;
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

    private static int intField(Minecraft minecraft, boolean width) {
        if (minecraft == null) {
            return 0;
        }
        try {
            Field field = width ? displayWidthField : displayHeightField;
            if (field == null) {
                field = findField(
                        Minecraft.class,
                        width ? "field_71443_c" : "field_71440_d",
                        width ? "displayWidth" : "displayHeight"
                );
                if (width) {
                    displayWidthField = field;
                } else {
                    displayHeightField = field;
                }
            }
            Object value = field == null ? null : field.get(minecraft);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
