package com.l.gpom.compat.baubles;

import baubles.common.container.SlotBauble;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class BaublesSideSlotsClient {
    private static final int PANEL_MARGIN = 0;
    private static final int PANEL_BORDER = 4;
    private static final int PANEL_PADDING = 3;
    private static final int PANEL_HEADER_HEIGHT = 12;
    private static final int SLOT_SIZE = 16;
    private static final int SLOT_BACKGROUND_SIZE = 18;
    private static final int SLOT_GAP = 0;
    private static final int SLOT_STEP = SLOT_BACKGROUND_SIZE + SLOT_GAP;
    private static final int BAUBLES_BUTTON_TEXTURE_Y = 48;
    private static final ResourceLocation BAUBLES_GUI_TEXTURE = new ResourceLocation("baubles", "textures/gui/expanded_inventory.png");
    private static final ResourceLocation SIDE_SLOTS_TEXTURE = new ResourceLocation("gpom", "textures/gui/baubles_side_slots.png");
    private static final int SIDE_SLOT_U = 48;
    private static final int SIDE_SLOT_V = 0;
    private static final int HOVER_OVERLAY_COLOR = 0x40FFFFFF;
    private static final float POST_HEI_TOOLTIP_Z = 1000.0F;
    private static final int CREATIVE_SURVIVAL_INVENTORY_TAB_INDEX = 11;
    private static final int PANEL_CORNER = 4;
    private static final int PANEL_TILE = 8;
    private static final int PANEL_TL_U = 0;
    private static final int PANEL_TL_V = 0;
    private static final int PANEL_EDGE_U = 4;
    private static final int PANEL_EDGE_V = 0;
    private static final int PANEL_TR_U = 12;
    private static final int PANEL_TR_V = 0;
    private static final int PANEL_LEFT_U = 0;
    private static final int PANEL_LEFT_V = 4;
    private static final int PANEL_CENTER_U = 4;
    private static final int PANEL_CENTER_V = 4;
    private static final int PANEL_RIGHT_U = 12;
    private static final int PANEL_RIGHT_V = 4;
    private static final int PANEL_BL_U = 0;
    private static final int PANEL_BL_V = 12;
    private static final int PANEL_BOTTOM_U = 4;
    private static final int PANEL_BOTTOM_V = 12;
    private static final int PANEL_BR_U = 12;
    private static final int PANEL_BR_V = 12;
    private static final int COSMETIC_ARMOR_TOGGLE_ID_BASE = 80;
    private static final int COSMETIC_ARMOR_BAUBLE_COSMETIC_SLOT_OFFSET = 4;
    private static final String COSMETIC_ARMOR_MAIN_CLASS = "lain.mods.cos.CosmeticArmorReworked";
    private static final String COSMETIC_ARMOR_TOGGLE_BUTTON_CLASS = "lain.mods.cos.client.GuiCosArmorToggleButton";
    private static final String COSMETIC_ARMOR_PACKET_SET_SKIN_ARMOR_CLASS = "lain.mods.cos.network.packet.PacketSetSkinArmor";
    private static final int[][] BAUBLE_ICON_UV = {
            {78, 9},   // amulet
            {78, 27},  // ring
            {78, 63},  // belt
            {97, 9},   // head
            {97, 27},  // body
            {97, 45},  // charm
            {78, 45},  // trinket/generic
            {78, 45}
    };
    private static final Map<GuiContainer, Integer> PAGE_OFFSETS = new WeakHashMap<>();
    private static final Map<GuiContainer, Boolean> PANEL_VISIBILITY = new WeakHashMap<>();
    private static final Map<GuiContainer, Set<Slot>> GUI_INVENTORY_SIDE_SLOTS = new WeakHashMap<>();
    private static Field inventorySlotsField;
    private static Field widthField;
    private static Field heightField;
    private static Field xSizeField;
    private static Field ySizeField;
    private static Field guiLeftField;
    private static Field guiTopField;
    private static Field buttonXField;
    private static Field buttonYField;
    private static Field buttonWidthField;
    private static Field buttonHeightField;
    private static Field buttonVisibleField;
    private static Field buttonEnabledField;
    private static Field baublesButtonParentField;
    private static Field buttonHoveredField;
    private static Field buttonIdField;
    private static Field buttonListField;
    private static Field hoveredSlotField;
    private static Field creativeSlotTargetField;
    private static Field creativeSelectedTabIndexField;
    private static Field cosmeticArmorToggleStateField;
    private static Field cosmeticArmorInventoryManagerField;
    private static Field cosmeticArmorNetworkField;
    private static Constructor<?> cosmeticArmorToggleButtonConstructor;
    private static Constructor<?> cosmeticArmorPacketSetSkinArmorConstructor;
    private static Method entityUniqueIdMethod;
    private static Method cosmeticArmorGetClientInventoryMethod;
    private static Method cosmeticArmorInventorySizeMethod;
    private static Method cosmeticArmorIsSkinArmorMethod;
    private static Method cosmeticArmorSetSkinArmorMethod;
    private static Method cosmeticArmorNetworkSendToServerMethod;
    private static Method activePotionEffectsMethod;
    private static Method renderHoveredToolTipMethod;

    private BaublesSideSlotsClient() {
    }

    public static void arrangeSlots(GuiContainer gui, int screenWidth, int xSize, int ySize, int guiLeft) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        if (isSideRailGui(gui)) {
            arrange(gui, screenWidth, 0, xSize, ySize, guiLeft, 0);
        }
        syncCosmeticArmorBaubleToggleButtons(gui);
    }

    public static void arrangeSlots(GuiContainer gui) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        Dimensions dimensions = dimensions(gui);
        if (dimensions != null) {
            arrange(gui, dimensions.screenWidth, dimensions.screenHeight, dimensions.xSize, dimensions.ySize,
                    dimensions.guiLeft, dimensions.guiTop);
        }
        syncCosmeticArmorBaubleToggleButtons(gui);
    }

    public static void drawPanel(GuiContainer gui, int screenWidth, int xSize, int ySize, int guiLeft, int guiTop) {
        drawPanel(gui, screenWidth, 0, xSize, ySize, guiLeft, guiTop);
    }

    private static void drawPanel(GuiContainer gui,
                                  int screenWidth,
                                  int screenHeight,
                                  int xSize,
                                  int ySize,
                                  int guiLeft,
                                  int guiTop) {
        if (!isSideRailOpen(gui) || !isSideRailGui(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        Layout layout = arrange(gui, screenWidth, screenHeight, xSize, ySize, guiLeft, guiTop);
        if (layout.totalSlots <= 0) {
            return;
        }

        int left = guiLeft + layout.panelX;
        int top = guiTop + layout.panelY;
        int right = left + layout.panelWidth;
        int bottom = top + layout.panelHeight;

        Minecraft minecraft = ClientAccess.minecraft();
        drawTexturedSidePanel(gui, minecraft, left, top, right, bottom, layout.useRight);

        FontRenderer font = ClientAccess.fontRenderer(minecraft);
        if (layout.maxPage > 0) {
            int textColor = 0xE0E0E0;
            String page = (layout.page + 1) + "/" + (layout.maxPage + 1);
            int headerTop = top + PANEL_BORDER;
            ClientAccess.drawStringWithShadow(font, layout.page > 0 ? "<" : "<", left + 6, headerTop + 1, layout.page > 0 ? textColor : 0x666666);
            ClientAccess.drawStringWithShadow(font, page, left + (layout.panelWidth - ClientAccess.stringWidth(font, page)) / 2, headerTop + 1, textColor);
            ClientAccess.drawStringWithShadow(font, ">", right - 11, headerTop + 1, layout.page < layout.maxPage ? textColor : 0x666666);
        }

        List<Slot> slots = orderedSideRailSlots(inventorySlots(gui));
        for (Slot slot : slots) {
            if (BaublesSideSlotsCommon.slotX(slot) <= BaublesSideSlotsCommon.HIDDEN_SLOT_POS / 2) {
                continue;
            }
            int slotLeft = guiLeft + BaublesSideSlotsCommon.slotX(slot) - 1;
            int slotTop = guiTop + BaublesSideSlotsCommon.slotY(slot) - 1;
            ClientAccess.bindTexture(minecraft, SIDE_SLOTS_TEXTURE);
            ClientAccess.drawTexturedModalRect(gui, slotLeft, slotTop, SIDE_SLOT_U, SIDE_SLOT_V, SLOT_BACKGROUND_SIZE, SLOT_BACKGROUND_SIZE);
            drawSlotTypeIcon(gui, minecraft, slot, slotLeft, slotTop);
        }
    }

    public static void drawPanel(GuiContainer gui) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        Dimensions dimensions = dimensions(gui);
        if (dimensions != null) {
            drawPanel(gui, dimensions.screenWidth, dimensions.screenHeight, dimensions.xSize, dimensions.ySize,
                    dimensions.guiLeft, dimensions.guiTop);
        }
    }

    public static boolean handlePanelClick(GuiContainer gui, int mouseX, int mouseY, int mouseButton) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return false;
        }
        if (mouseButton != 0) {
            return false;
        }

        Dimensions dimensions = dimensions(gui);
        if (dimensions == null) {
            return false;
        }

        Layout layout = arrange(gui, dimensions.screenWidth, dimensions.screenHeight, dimensions.xSize, dimensions.ySize,
                dimensions.guiLeft, dimensions.guiTop);
        if (layout.maxPage <= 0) {
            return false;
        }

        int left = dimensions.guiLeft + layout.panelX;
        int top = dimensions.guiTop + layout.panelY;
        int right = left + layout.panelWidth;
        int headerTop = top + PANEL_BORDER;
        if (mouseY < headerTop || mouseY >= headerTop + layout.headerHeight || mouseX < left || mouseX >= right) {
            return false;
        }

        int nextPage = layout.page;
        if (mouseX < left + 16) {
            nextPage--;
        } else if (mouseX >= right - 16) {
            nextPage++;
        } else {
            return false;
        }

        nextPage = clamp(nextPage, 0, layout.maxPage);
        if (nextPage == layout.page) {
            return true;
        }
        PAGE_OFFSETS.put(gui, nextPage);
        arrange(gui, dimensions.screenWidth, dimensions.screenHeight, dimensions.xSize, dimensions.ySize,
                dimensions.guiLeft, dimensions.guiTop);
        return true;
    }

    public static boolean handleSideRailSlotClick(GuiContainer gui, int mouseX, int mouseY, int mouseButton) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return false;
        }
        if (mouseButton != 0) {
            return false;
        }

        Container container = inventorySlots(gui);
        Slot slot = findSlotAt(container, guiLeft(gui), guiTop(gui), mouseX, mouseY);
        if (!BaublesSideSlotsCommon.isSideRailSlot(slot)
                || !BaublesSideSlotsCommon.isSlotEnabled(slot)) {
            return false;
        }

        ClickType clickType = isShiftKeyDown() && BaublesSideSlotsCommon.slotHasStack(slot)
                ? ClickType.QUICK_MOVE
                : ClickType.PICKUP;
        int windowId = BaublesSideSlotsCommon.windowId(container);
        int slotNumber = BaublesSideSlotsCommon.slotNumber(slot);
        if (windowId < 0 || slotNumber < 0) {
            return false;
        }

        boolean emptyCursorPickup = clickType == ClickType.PICKUP
                && BaublesSideSlotsCommon.isEmptyStack(ClientAccess.carriedStack(ClientAccess.minecraft()))
                && BaublesSideSlotsCommon.slotHasStack(slot);
        int railType = sideRailType(slot);
        int railIndex = sideRailIndex(slot);
        // Side-rail slots are server-authoritative. A local vanilla windowClick races
        // the GPOM packet and makes normal pickups snap back into the slot.
        BaublesSideSlotsNetwork.sendSideRailSlotClick(windowId, slotNumber, railType, railIndex, mouseButton, clickType, emptyCursorPickup);
        return true;
    }

    private static int sideRailType(Slot slot) {
        if (slot instanceof SlotBauble) {
            return BaublesSideSlotsNetwork.RAIL_TYPE_BAUBLE;
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return BaublesSideSlotsNetwork.RAIL_TYPE_AETHER;
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return BaublesSideSlotsNetwork.RAIL_TYPE_COSMETIC_ARMOR;
        }
        return BaublesSideSlotsNetwork.RAIL_TYPE_UNKNOWN;
    }

    private static int sideRailIndex(Slot slot) {
        if (slot instanceof SlotBauble) {
            return BaublesSideSlotsCommon.baubleSlotIndex(slot);
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return AetherSideSlotsBridge.accessorySlotIndex(slot);
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot);
        }
        return -1;
    }

    public static boolean handleCosmeticArmorBaubleToggleClick(GuiContainer gui, int mouseX, int mouseY, int mouseButton) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return false;
        }
        if (mouseButton != 0) {
            return false;
        }

        Object value = objectFieldValue(gui, buttonListField, "field_146292_n", "buttonList");
        if (!(value instanceof List)) {
            return false;
        }

        for (Object button : (List<?>) value) {
            if (!isCosmeticArmorBaubleToggleButton(button) || !buttonContains(button, mouseX, mouseY)) {
                continue;
            }

            int cosmeticSlot = cosmeticArmorToggleCosmeticSlot(button);
            if (cosmeticSlot < 0) {
                continue;
            }

            if (!hasCosmeticArmorSlot(gui, cosmeticSlot)) {
                return false;
            }

            boolean updated = !cosmeticArmorSlotState(gui, cosmeticSlot);
            if (!setCosmeticArmorSlotState(gui, cosmeticSlot, updated)) {
                return false;
            }
            setCosmeticArmorToggleButtonState(button, updated);
            sendCosmeticArmorSlotStateToServer(gui, cosmeticSlot);
            return true;
        }
        return false;
    }

    public static boolean handleScroll(GuiContainer gui,
                                       int screenWidth,
                                       int xSize,
                                       int ySize,
                                       int guiLeft,
                                       int guiTop,
                                       int mouseX,
                                       int mouseY,
                                       int wheelDelta) {
        if (!isSideRailOpen(gui) || wheelDelta == 0) {
            closeSideRailRendering(gui);
            return false;
        }

        Layout layout = arrange(gui, screenWidth, 0, xSize, ySize, guiLeft, guiTop);
        if (layout.maxPage <= 0) {
            return false;
        }

        int left = guiLeft + layout.panelX;
        int top = guiTop + layout.panelY;
        int right = left + layout.panelWidth;
        int bottom = top + layout.panelHeight;
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) {
            return false;
        }

        int direction = wheelDelta > 0 ? -1 : 1;
        int updated = clamp(layout.page + direction, 0, layout.maxPage);
        PAGE_OFFSETS.put(gui, updated);
        arrange(gui, screenWidth, 0, xSize, ySize, guiLeft, guiTop);
        return true;
    }

    public static boolean handleScroll(GuiContainer gui, int wheelDelta) {
        Dimensions dimensions = dimensions(gui);
        if (dimensions == null) {
            return false;
        }

        Minecraft minecraft = ClientAccess.minecraft();
        int displayWidth = ClientAccess.displayWidth(minecraft);
        int displayHeight = ClientAccess.displayHeight(minecraft);
        if (displayWidth <= 0 || displayHeight <= 0) {
            return false;
        }
        int mouseX = MouseInput.mouseX(dimensions.screenWidth, displayWidth);
        int mouseY = MouseInput.mouseY(dimensions.screenHeight, displayHeight);
        return handleScroll(gui, dimensions.screenWidth, dimensions.xSize, dimensions.ySize,
                dimensions.guiLeft, dimensions.guiTop, mouseX, mouseY, wheelDelta);
    }

    public static void syncHoveredSlotAndDrawFallback(GuiContainer gui, int mouseX, int mouseY) {
        syncHoveredSlot(gui, mouseX, mouseY, true);
    }

    public static void syncHoveredSlotForTooltip(GuiContainer gui, int mouseX, int mouseY) {
        syncHoveredSlot(gui, mouseX, mouseY, false);
    }

    private static void syncHoveredSlot(GuiContainer gui, int mouseX, int mouseY, boolean drawFallbackOverlay) {
        if (!isSideRailOpen(gui) || !isSideRailGui(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        Slot slot = findSlotAt(inventorySlots(gui), guiLeft(gui), guiTop(gui), mouseX, mouseY);
        if (slot == null || !BaublesSideSlotsCommon.isSlotEnabled(slot)) {
            clearHoveredBaubleSlot(gui);
            return;
        }

        boolean sideRailSlot = BaublesSideSlotsCommon.isSideRailSlot(slot);
        boolean occupiedPlayerSlot = BaublesSideSlotsCommon.slotInventory(slot) instanceof InventoryPlayer
                && BaublesSideSlotsCommon.slotHasStack(slot);
        if (!sideRailSlot && !occupiedPlayerSlot) {
            clearHoveredBaubleSlot(gui);
            return;
        }

        Slot vanillaHovered = hoveredSlot(gui);
        setHoveredSlot(gui, slot);
        if (drawFallbackOverlay && vanillaHovered != slot) {
            drawHoverOverlay(gui, slot);
        }
    }

    public static void drawEmptySlotTooltip(GuiContainer gui, int mouseX, int mouseY) {
        if (!isSideRailOpen(gui) || !isSideRailGui(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        Slot slot = findSlotAt(inventorySlots(gui), guiLeft(gui), guiTop(gui), mouseX, mouseY);
        if (slot == null
                || !BaublesSideSlotsCommon.isSideRailSlot(slot)
                || !BaublesSideSlotsCommon.isSlotEnabled(slot)
                || BaublesSideSlotsCommon.slotHasStack(slot)) {
            return;
        }

        String tooltip = sideRailSlotTooltip(slot);
        if (tooltip == null || tooltip.isEmpty()) {
            return;
        }
        drawSlotTypeTooltip(gui, tooltip, mouseX, mouseY);
    }

    public static boolean drawLateSideSlotTooltip(GuiContainer gui, int mouseX, int mouseY) {
        if (!isSideRailOpen(gui) || !isSideRailGui(gui)) {
            return false;
        }

        Slot slot = findSlotAt(inventorySlots(gui), guiLeft(gui), guiTop(gui), mouseX, mouseY);
        if (slot == null
                || !BaublesSideSlotsCommon.isSideRailSlot(slot)
                || !BaublesSideSlotsCommon.isSlotEnabled(slot)) {
            return false;
        }

        setHoveredSlot(gui, slot);
        if (BaublesSideSlotsCommon.slotHasStack(slot)) {
            drawHoveredSlotTooltip(gui, mouseX, mouseY);
            return true;
        }
        drawEmptySlotTooltip(gui, mouseX, mouseY);
        return true;
    }

    private static void drawSlotTypeTooltip(GuiContainer gui, String tooltip, int mouseX, int mouseY) {
        FontRenderer font = ClientAccess.fontRenderer(ClientAccess.minecraft());
        if (gui == null || font == null || tooltip == null || tooltip.isEmpty()) {
            return;
        }

        int tooltipWidth = ClientAccess.stringWidth(font, tooltip);
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 12;
        int tooltipHeight = 8;
        int screenWidth = intField(gui, "width", "field_146294_l");
        int screenHeight = intField(gui, "height", "field_146295_m");
        if (screenWidth > 0 && tooltipX + tooltipWidth + 6 > screenWidth) {
            tooltipX = mouseX - 28 - tooltipWidth;
        }
        if (screenHeight > 0 && tooltipY + tooltipHeight + 6 > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - 6;
        }
        tooltipY = Math.max(4, tooltipY);

        int background = 0xF0100010;
        int border = 0x505000FF;
        int borderEnd = 0x5028007F;
        int right = tooltipX + tooltipWidth;
        int bottom = tooltipY + tooltipHeight;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(0.0F, 0.0F, POST_HEI_TOOLTIP_Z);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

            ClientAccess.drawRect(tooltipX - 3, tooltipY - 4, right + 3, tooltipY - 3, background);
            ClientAccess.drawRect(tooltipX - 3, bottom + 3, right + 3, bottom + 4, background);
            ClientAccess.drawRect(tooltipX - 3, tooltipY - 3, right + 3, bottom + 3, background);
            ClientAccess.drawRect(tooltipX - 4, tooltipY - 3, tooltipX - 3, bottom + 3, background);
            ClientAccess.drawRect(right + 3, tooltipY - 3, right + 4, bottom + 3, background);
            ClientAccess.drawRect(tooltipX - 3, tooltipY - 3, tooltipX - 2, bottom + 3, border);
            ClientAccess.drawRect(right + 2, tooltipY - 3, right + 3, bottom + 3, borderEnd);
            ClientAccess.drawRect(tooltipX - 3, tooltipY - 3, right + 3, tooltipY - 2, border);
            ClientAccess.drawRect(tooltipX - 3, bottom + 2, right + 3, bottom + 3, borderEnd);
            ClientAccess.drawStringWithShadow(font, tooltip, tooltipX, tooltipY, 0xFFFFFFFF);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    private static void drawHoveredSlotTooltip(GuiContainer gui, int mouseX, int mouseY) {
        if (gui == null) {
            return;
        }
        try {
            Method method = renderHoveredToolTipMethod;
            if (method == null) {
                method = findMethod(gui.getClass(), new Class<?>[] {int.class, int.class},
                        "func_191948_b",
                        "renderHoveredToolTip");
                renderHoveredToolTipMethod = method;
            }
            if (method != null) {
                GL11.glPushMatrix();
                try {
                    GL11.glTranslatef(0.0F, 0.0F, POST_HEI_TOOLTIP_Z);
                    method.invoke(gui, mouseX, mouseY);
                } finally {
                    GL11.glPopMatrix();
                    GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void drawHoverOverlay(GuiContainer gui, Slot slot) {
        int x = BaublesSideSlotsCommon.slotX(slot);
        int y = BaublesSideSlotsCommon.slotY(slot);
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.colorMask(true, true, true, false);
        ClientAccess.drawGradientRect(gui, x, y, x + SLOT_SIZE, y + SLOT_SIZE, HOVER_OVERLAY_COLOR, HOVER_OVERLAY_COLOR);
        GlStateManager.colorMask(true, true, true, true);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
    }

    public static boolean tryQuickEquip(GuiContainer gui,
                                        int mouseX,
                                        int mouseY,
                                        int mouseButton) {
        Container container = inventorySlots(gui);
        if (container == null) {
            return false;
        }
        return tryQuickEquip(gui, container, intField(gui, "guiLeft", "field_147003_i"),
                intField(gui, "guiTop", "field_147009_r"), mouseX, mouseY, mouseButton);
    }

    public static boolean tryQuickEquip(GuiContainer gui,
                                        Container container,
                                        int guiLeft,
                                        int guiTop,
                                        int mouseX,
                                        int mouseY,
                                        int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }

        if (!GpomEarlyConfig.baublesSideSlotsShiftRightClickEquipEnabled()
                || !isShiftKeyDown()
                || !isSideRailOpen(gui)
                || container == null) {
            closeSideRailRendering(gui);
            return false;
        }

        Slot slot = findSlotAt(container, guiLeft, guiTop, mouseX, mouseY);
        if (slot == null
                || BaublesSideSlotsCommon.isSideRailSlot(slot)
                || !BaublesSideSlotsCommon.isPlayerMainInventorySlot(slot)
                || !BaublesSideSlotsCommon.slotHasStack(slot)) {
            return false;
        }

        if (quickEquipWithContainerClicks(container, slot)) {
            return true;
        }

        BaublesSideSlotsNetwork.sendQuickEquip(BaublesSideSlotsCommon.windowId(container),
                BaublesSideSlotsCommon.slotNumber(slot));
        return true;
    }

    public static boolean handleToggleButtonClick(Object button, GuiContainer parent, Minecraft minecraft, int mouseX, int mouseY) {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled()
                || !(parent instanceof GuiInventory)
                || !isActiveScreen(minecraft, parent)) {
            return false;
        }
        positionToggleButton(button, parent);
        if (!buttonContains(button, mouseX, mouseY)) {
            return false;
        }

        setPanelVisible(parent, !isPanelVisible(parent));
        return true;
    }

    public static void setPanelVisible(GuiContainer gui, boolean visible) {
        if (gui == null) {
            return;
        }
        if (visible) {
            PANEL_VISIBILITY.put(gui, true);
            prepareGuiInventorySideSlots(gui);
            arrangeSlots(gui);
        } else {
            removeGuiInventorySideSlots(gui);
        }
    }

    public static void prepareGuiInventorySideSlots(GuiContainer gui) {
        if (!isSideRailOpen(gui)) {
            closeSideRailRendering(gui);
            return;
        }

        Container container = inventorySlots(gui);
        Minecraft minecraft = ClientAccess.minecraft();
        EntityLivingBase player = ClientAccess.player(minecraft);
        if (container == null || !(player instanceof EntityPlayer)) {
            return;
        }

        Set<Slot> existingSlots = new HashSet<>(BaublesSideSlotsCommon.sideRailSlots(container));
        BaublesSideSlotsVanillaBridge.prepare(container, (EntityPlayer) player);
        AetherSideSlotsBridge.prepare(container, (EntityPlayer) player);
        CosmeticArmorSideSlotsBridge.prepare(container, (EntityPlayer) player);

        Set<Slot> managedSlots = GUI_INVENTORY_SIDE_SLOTS.get(gui);
        for (Slot slot : BaublesSideSlotsCommon.sideRailSlots(container)) {
            if (existingSlots.contains(slot)) {
                continue;
            }
            if (managedSlots == null) {
                managedSlots = new HashSet<>();
                GUI_INVENTORY_SIDE_SLOTS.put(gui, managedSlots);
            }
            managedSlots.add(slot);
        }
        arrangeSlots(gui);
    }

    public static void removeGuiInventorySideSlots(GuiContainer gui) {
        if (!(gui instanceof GuiInventory)) {
            closeSideRailRendering(gui);
            return;
        }

        Container container = inventorySlots(gui);
        Set<Slot> managedSlots = GUI_INVENTORY_SIDE_SLOTS.remove(gui);
        if (container != null && managedSlots != null && !managedSlots.isEmpty()) {
            List<Slot> slots = BaublesSideSlotsCommon.slots(container);
            for (int index = slots.size() - 1; index >= 0; index--) {
                if (managedSlots.contains(slots.get(index))) {
                    BaublesSideSlotsCommon.removeSlotAt(container, index);
                }
            }
        }

        hideBaubleSlots(gui);
        clearHoveredBaubleSlot(gui);
        PAGE_OFFSETS.remove(gui);
        PANEL_VISIBILITY.remove(gui);
        syncCosmeticArmorBaubleToggleButtons(gui);
    }

    public static void resetClientState() {
        Minecraft minecraft = ClientAccess.minecraft();
        Object currentScreen = ClientAccess.currentScreen(minecraft);
        if (currentScreen instanceof GuiContainer) {
            GuiContainer gui = (GuiContainer) currentScreen;
            if (gui instanceof GuiInventory) {
                removeGuiInventorySideSlots(gui);
            } else {
                closeSideRailRendering(gui);
            }
        }

        PAGE_OFFSETS.clear();
        PANEL_VISIBILITY.clear();
        GUI_INVENTORY_SIDE_SLOTS.clear();
        CosmeticArmorSideSlotsBridge.resetClientState();
    }

    private static void closeSideRailRendering(GuiContainer gui) {
        if (gui == null) {
            return;
        }
        hideBaubleSlots(gui);
        clearHoveredBaubleSlot(gui);
        PAGE_OFFSETS.remove(gui);
        syncCosmeticArmorBaubleToggleButtons(gui, false);
    }

    private static boolean isSideRailSlotMirror(Slot slot) {
        if (BaublesSideSlotsCommon.isSideRailSlot(slot)) {
            return true;
        }

        Slot target = creativeSlotTarget(slot);
        return BaublesSideSlotsCommon.isSideRailSlot(target);
    }

    public static boolean shouldSkipSlotRender(GuiContainer gui, Slot slot) {
        if (slot == null) {
            return false;
        }

        Slot target = creativeSlotTarget(slot);
        boolean sideRailSlot = BaublesSideSlotsCommon.isSideRailSlot(slot);
        boolean sideRailMirror = target != null && BaublesSideSlotsCommon.isSideRailSlot(target);
        if (!sideRailSlot && !sideRailMirror) {
            return false;
        }

        if (!isSideRailOpen(gui)) {
            hideSlot(slot);
            hideSlot(target);
            return true;
        }

        Slot renderedSlot = sideRailMirror ? target : slot;
        boolean hidden = BaublesSideSlotsCommon.slotX(renderedSlot) <= BaublesSideSlotsCommon.HIDDEN_SLOT_POS / 2
                || BaublesSideSlotsCommon.slotY(renderedSlot) <= BaublesSideSlotsCommon.HIDDEN_SLOT_POS / 2;
        if (hidden) {
            hideSlot(slot);
            hideSlot(target);
        }
        return hidden;
    }

    public static int hoverOverlayColor(GuiContainer gui, int originalColor) {
        return shouldUseReducedHoverOverlay(gui) ? HOVER_OVERLAY_COLOR : originalColor;
    }

    private static Slot creativeSlotTarget(Slot slot) {
        if (slot == null) {
            return null;
        }

        try {
            Field field = creativeSlotTargetField;
            if (field == null || field.getDeclaringClass() != slot.getClass()) {
                field = findField(slot.getClass(), "slot", "field_148332_b");
                creativeSlotTargetField = field;
            }
            Object value = field == null ? null : field.get(slot);
            return value instanceof Slot ? (Slot) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public static void syncCosmeticArmorBaubleToggleButtons(GuiContainer gui, List<?> buttons) {
        if (buttons == null) {
            return;
        }
        ensureCosmeticArmorBaubleToggleButtons(gui, buttons);
        syncCosmeticArmorBaubleToggleButtons(gui, buttons, isSideRailOpen(gui));
    }

    public static boolean drawToggleButton(Object button, GuiContainer parent, Minecraft minecraft, int mouseX, int mouseY) {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled() || !(parent instanceof GuiInventory) || !(button instanceof GuiButton)) {
            return false;
        }
        if (!isActiveScreen(minecraft, parent)) {
            setButtonInteractive(button, false);
            return true;
        }
        if (!isSideRailOpen(parent)) {
            closeSideRailRendering(parent);
        }
        setButtonInteractive(button, true);
        if (!booleanField(button, buttonVisibleField, true, "field_146125_m", "visible")) {
            return true;
        }

        positionToggleButton(button, parent);
        int left = intField(button, buttonXField, 0, "field_146128_h", "x");
        int top = intField(button, buttonYField, 0, "field_146129_i", "y");
        int width = intField(button, buttonWidthField, 10, "field_146120_f", "width");
        int height = intField(button, buttonHeightField, 10, "field_146121_g", "height");
        boolean hovered = buttonContains(button, mouseX, mouseY);
        setBooleanField(button, buttonHoveredField, hovered, "field_146123_n", "hovered");

        ClientAccess.bindTexture(minecraft, BAUBLES_GUI_TEXTURE);
        ClientAccess.drawTexturedModalRect((GuiButton) button, left, top, hovered ? 210 : 200, BAUBLES_BUTTON_TEXTURE_Y, width, height);
        return true;
    }

    private static boolean quickEquipWithContainerClicks(Container container, Slot source) {
        ItemStack sourceStack = BaublesSideSlotsCommon.slotStack(source);
        Slot target = findEmptyQuickEquipTarget(container, sourceStack);
        if (target == null) {
            return false;
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(target)) {
            return false;
        }

        Minecraft minecraft = ClientAccess.minecraft();
        if (!BaublesSideSlotsCommon.isEmptyStack(ClientAccess.carriedStack(minecraft))) {
            return false;
        }

        int windowId = BaublesSideSlotsCommon.windowId(container);
        int sourceSlot = BaublesSideSlotsCommon.slotNumber(source);
        int targetSlot = BaublesSideSlotsCommon.slotNumber(target);
        if (windowId < 0 || sourceSlot < 0 || targetSlot < 0) {
            return false;
        }

        if (!ClientAccess.windowClick(minecraft, windowId, sourceSlot, 0, ClickType.PICKUP)) {
            return false;
        }
        if (!ClientAccess.windowClick(minecraft, windowId, targetSlot, 0, ClickType.PICKUP)) {
            ClientAccess.windowClick(minecraft, windowId, sourceSlot, 0, ClickType.PICKUP);
            return true;
        }
        if (!BaublesSideSlotsCommon.isEmptyStack(ClientAccess.carriedStack(minecraft))) {
            ClientAccess.windowClick(minecraft, windowId, sourceSlot, 0, ClickType.PICKUP);
        }
        return true;
    }

    private static Slot findEmptyBaubleTarget(Container container, ItemStack stack) {
        if (BaublesSideSlotsCommon.isEmptyStack(stack)) {
            return null;
        }

        for (Slot slot : orderedBaubleSlots(container)) {
            if (!BaublesSideSlotsCommon.slotHasStack(slot)
                    && BaublesSideSlotsCommon.isSlotItemValid(slot, stack)) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findEmptyAetherTarget(Container container, ItemStack stack) {
        if (BaublesSideSlotsCommon.isEmptyStack(stack)) {
            return null;
        }

        List<Slot> slots = new ArrayList<>(AetherSideSlotsBridge.accessorySlots(container));
        slots.sort(Comparator
                .comparingInt(BaublesSideSlotsClient::sideRailVerticalOrder)
                .thenComparing(BaublesSideSlotsClient::sideRailSlotTooltip, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(AetherSideSlotsBridge::accessorySlotIndex)
                .thenComparingInt(BaublesSideSlotsCommon::slotNumber));
        for (Slot slot : slots) {
            if (!BaublesSideSlotsCommon.slotHasStack(slot)
                    && BaublesSideSlotsCommon.isSlotItemValid(slot, stack)) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findEmptyCosmeticArmorTarget(Container container, ItemStack stack) {
        if (BaublesSideSlotsCommon.isEmptyStack(stack)) {
            return null;
        }

        List<Slot> slots = new ArrayList<>(CosmeticArmorSideSlotsBridge.cosmeticArmorSlots(container));
        slots.sort(Comparator
                .comparingInt(BaublesSideSlotsClient::sideRailVerticalOrder)
                .thenComparing(BaublesSideSlotsClient::sideRailSlotTooltip, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(CosmeticArmorSideSlotsBridge::cosmeticArmorSlotIndex)
                .thenComparingInt(BaublesSideSlotsCommon::slotNumber));
        for (Slot slot : slots) {
            if (!BaublesSideSlotsCommon.slotHasStack(slot)
                    && BaublesSideSlotsCommon.isSlotItemValid(slot, stack)) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findEmptyQuickEquipTarget(Container container, ItemStack stack) {
        if (BaublesSideSlotsCommon.isValidForVanillaArmorSlot(container, stack)) {
            Slot armor = BaublesSideSlotsCommon.findEmptyVanillaArmorTarget(container, stack);
            if (armor != null) {
                return armor;
            }
            Slot cosmeticArmor = findEmptyCosmeticArmorTarget(container, stack);
            if (cosmeticArmor != null) {
                return cosmeticArmor;
            }
        }

        Slot bauble = findEmptyBaubleTarget(container, stack);
        if (bauble != null) {
            return bauble;
        }
        Slot aether = findEmptyAetherTarget(container, stack);
        return aether != null ? aether : findEmptyCosmeticArmorTarget(container, stack);
    }

    private static void syncCosmeticArmorBaubleToggleButtons(GuiContainer gui) {
        syncCosmeticArmorBaubleToggleButtons(gui, isSideRailOpen(gui));
    }

    private static void syncCosmeticArmorBaubleToggleButtons(GuiContainer gui, boolean panelVisible) {
        Object value = objectFieldValue(gui, buttonListField, "field_146292_n", "buttonList");
        if (value instanceof List) {
            List<?> buttons = (List<?>) value;
            ensureCosmeticArmorBaubleToggleButtons(gui, buttons);
            syncCosmeticArmorBaubleToggleButtons(gui, buttons, panelVisible);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void ensureCosmeticArmorBaubleToggleButtons(GuiContainer gui, List<?> buttons) {
        if (gui == null || buttons == null || !isSideRailGui(gui)) {
            return;
        }

        Set<Integer> existingIds = new HashSet<>();
        for (Object button : buttons) {
            if (isCosmeticArmorBaubleToggleButton(button)) {
                existingIds.add(intField(button, buttonIdField, -1, "field_146127_k", "id"));
            }
        }

        Container container = inventorySlots(gui);
        for (Slot slot : CosmeticArmorSideSlotsBridge.cosmeticArmorSlots(container)) {
            int cosmeticSlot = CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot);
            if (cosmeticSlot < 0) {
                continue;
            }

            int buttonId = COSMETIC_ARMOR_TOGGLE_ID_BASE + cosmeticSlot;
            if (existingIds.contains(buttonId) || !hasCosmeticArmorSlot(gui, cosmeticSlot)) {
                continue;
            }

            Object button = createCosmeticArmorToggleButton(gui, buttonId, cosmeticSlot);
            if (button instanceof GuiButton) {
                ((List) buttons).add(button);
                existingIds.add(buttonId);
            }
        }

        for (Slot slot : BaublesSideSlotsCommon.baubleSlots(container)) {
            int baubleSlot = BaublesSideSlotsCommon.baubleSlotIndex(slot);
            if (baubleSlot < 0) {
                continue;
            }

            int cosmeticSlot = COSMETIC_ARMOR_BAUBLE_COSMETIC_SLOT_OFFSET + baubleSlot;
            int buttonId = COSMETIC_ARMOR_TOGGLE_ID_BASE + cosmeticSlot;
            if (existingIds.contains(buttonId) || !hasCosmeticArmorSlot(gui, cosmeticSlot)) {
                continue;
            }

            Object button = createCosmeticArmorToggleButton(gui, buttonId, cosmeticSlot);
            if (button instanceof GuiButton) {
                ((List) buttons).add(button);
                existingIds.add(buttonId);
            }
        }
    }

    private static void syncCosmeticArmorBaubleToggleButtons(GuiContainer gui, List<?> buttons, boolean panelVisible) {
        if (gui == null || buttons == null) {
            return;
        }
        Container container = inventorySlots(gui);
        int guiLeft = guiLeft(gui);
        int guiTop = guiTop(gui);
        for (Object button : buttons) {
            if (!isCosmeticArmorBaubleToggleButton(button)) {
                continue;
            }
            int cosmeticSlot = cosmeticArmorToggleCosmeticSlot(button);
            Slot slot = cosmeticSlot < 0 ? null : cosmeticArmorToggleSlot(container, cosmeticSlot);
            boolean show = panelVisible
                    && slot != null
                    && BaublesSideSlotsCommon.slotX(slot) > BaublesSideSlotsCommon.HIDDEN_SLOT_POS / 2
                    && BaublesSideSlotsCommon.slotY(slot) > BaublesSideSlotsCommon.HIDDEN_SLOT_POS / 2;
            setButtonInteractive(button, show);
            if (show) {
                setIntField(button, buttonXField, guiLeft + BaublesSideSlotsCommon.slotX(slot) - 1, "field_146128_h", "x");
                setIntField(button, buttonYField, guiTop + BaublesSideSlotsCommon.slotY(slot) - 1, "field_146129_i", "y");
                setIntField(button, buttonWidthField, 5, "field_146120_f", "width");
                setIntField(button, buttonHeightField, 5, "field_146121_g", "height");
                setCosmeticArmorToggleButtonState(button, cosmeticArmorSlotState(gui, cosmeticSlot));
            }
        }
    }

    private static boolean isCosmeticArmorBaubleToggleButton(Object button) {
        return button != null
                && COSMETIC_ARMOR_TOGGLE_BUTTON_CLASS.equals(button.getClass().getName())
                && cosmeticArmorToggleCosmeticSlot(button) >= 0;
    }

    private static int cosmeticArmorToggleCosmeticSlot(Object button) {
        int id = intField(button, buttonIdField, -1, "field_146127_k", "id");
        return id >= COSMETIC_ARMOR_TOGGLE_ID_BASE ? id - COSMETIC_ARMOR_TOGGLE_ID_BASE : -1;
    }

    private static Slot cosmeticArmorToggleSlot(Container container, int cosmeticSlot) {
        if (cosmeticSlot < COSMETIC_ARMOR_BAUBLE_COSMETIC_SLOT_OFFSET) {
            return cosmeticArmorSlot(container, cosmeticSlot);
        }
        return baubleSlot(container, cosmeticSlot - COSMETIC_ARMOR_BAUBLE_COSMETIC_SLOT_OFFSET);
    }

    private static Slot cosmeticArmorSlot(Container container, int cosmeticSlotIndex) {
        for (Slot slot : CosmeticArmorSideSlotsBridge.cosmeticArmorSlots(container)) {
            if (CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot) == cosmeticSlotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static Slot baubleSlot(Container container, int baubleSlotIndex) {
        for (Slot slot : BaublesSideSlotsCommon.baubleSlots(container)) {
            if (BaublesSideSlotsCommon.baubleSlotIndex(slot) == baubleSlotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static Object createCosmeticArmorToggleButton(GuiContainer gui, int buttonId, int cosmeticSlot) {
        try {
            Constructor<?> constructor = cosmeticArmorToggleButtonConstructor;
            if (constructor == null) {
                Class<?> type = Class.forName(COSMETIC_ARMOR_TOGGLE_BUTTON_CLASS, false,
                        BaublesSideSlotsClient.class.getClassLoader());
                constructor = type.getConstructor(int.class, int.class, int.class, int.class, int.class, String.class);
                constructor.setAccessible(true);
                cosmeticArmorToggleButtonConstructor = constructor;
            }

            Object button = constructor.newInstance(buttonId, BaublesSideSlotsCommon.HIDDEN_SLOT_POS,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS, 5, 5, "");
            setCosmeticArmorToggleButtonState(button, cosmeticArmorSlotState(gui, cosmeticSlot));
            return button;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean hasCosmeticArmorSlot(GuiContainer gui, int cosmeticSlot) {
        return cosmeticSlot >= 0 && cosmeticSlot < cosmeticArmorInventorySize(gui);
    }

    private static int cosmeticArmorInventorySize(GuiContainer gui) {
        Object inventory = cosmeticArmorInventory(gui);
        if (inventory == null) {
            return 0;
        }
        try {
            Method method = cosmeticArmorInventorySizeMethod;
            if (method == null) {
                method = findMethod(inventory.getClass(), new Class<?>[0], "func_70302_i_", "getSizeInventory");
                cosmeticArmorInventorySizeMethod = method;
            }
            Object value = method == null ? null : method.invoke(inventory);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean cosmeticArmorSlotState(GuiContainer gui, int cosmeticSlot) {
        Object inventory = cosmeticArmorInventory(gui);
        if (inventory == null) {
            return false;
        }
        try {
            Method method = cosmeticArmorIsSkinArmorMethod;
            if (method == null) {
                method = findMethod(inventory.getClass(), new Class<?>[] {int.class}, "isSkinArmor");
                cosmeticArmorIsSkinArmorMethod = method;
            }
            Object value = method == null ? null : method.invoke(inventory, cosmeticSlot);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean setCosmeticArmorSlotState(GuiContainer gui, int cosmeticSlot, boolean state) {
        Object inventory = cosmeticArmorInventory(gui);
        if (inventory == null) {
            return false;
        }
        try {
            Method method = cosmeticArmorSetSkinArmorMethod;
            if (method == null) {
                method = findMethod(inventory.getClass(), new Class<?>[] {int.class, boolean.class}, "setSkinArmor");
                cosmeticArmorSetSkinArmorMethod = method;
            }
            if (method == null) {
                return false;
            }
            method.invoke(inventory, cosmeticSlot, state);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object cosmeticArmorInventory(GuiContainer gui) {
        if (gui == null) {
            return null;
        }
        Minecraft minecraft = ClientAccess.minecraft();
        EntityLivingBase player = ClientAccess.player(minecraft);
        UUID uuid = playerUniqueId(player);
        if (uuid == null) {
            return null;
        }

        Object manager = cosmeticArmorInventoryManager();
        if (manager == null) {
            return null;
        }

        try {
            Method method = cosmeticArmorGetClientInventoryMethod;
            if (method == null) {
                method = findMethod(manager.getClass(), new Class<?>[] {UUID.class}, "getCosArmorInventoryClient");
                cosmeticArmorGetClientInventoryMethod = method;
            }
            return method == null ? null : method.invoke(manager, uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object cosmeticArmorInventoryManager() {
        try {
            Field field = cosmeticArmorInventoryManagerField;
            if (field == null) {
                Class<?> type = Class.forName(COSMETIC_ARMOR_MAIN_CLASS, false,
                        BaublesSideSlotsClient.class.getClassLoader());
                field = findField(type, "invMan");
                cosmeticArmorInventoryManagerField = field;
            }
            return field == null ? null : field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setCosmeticArmorToggleButtonState(Object button, boolean state) {
        if (button == null) {
            return;
        }
        try {
            Field field = cosmeticArmorToggleStateField;
            if (field == null) {
                field = findField(button.getClass(), "state");
                cosmeticArmorToggleStateField = field;
            }
            if (field != null) {
                field.setInt(button, state ? 1 : 0);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void sendCosmeticArmorSlotStateToServer(GuiContainer gui, int cosmeticSlot) {
        try {
            Minecraft minecraft = ClientAccess.minecraft();
            EntityLivingBase player = ClientAccess.player(minecraft);
            if (!(player instanceof net.minecraft.entity.player.EntityPlayer)) {
                return;
            }

            Constructor<?> packetConstructor = cosmeticArmorPacketSetSkinArmorConstructor;
            if (packetConstructor == null) {
                Class<?> packetType = Class.forName(COSMETIC_ARMOR_PACKET_SET_SKIN_ARMOR_CLASS, false,
                        BaublesSideSlotsClient.class.getClassLoader());
                packetConstructor = packetType.getConstructor(net.minecraft.entity.player.EntityPlayer.class, int.class);
                packetConstructor.setAccessible(true);
                cosmeticArmorPacketSetSkinArmorConstructor = packetConstructor;
            }
            Object packet = packetConstructor.newInstance(player, cosmeticSlot);
            Object network = cosmeticArmorNetwork();
            if (network == null || packet == null) {
                return;
            }

            Method method = cosmeticArmorNetworkSendToServerMethod;
            if (method == null) {
                method = findCompatibleMethod(network.getClass(), "sendToServer", packet);
                cosmeticArmorNetworkSendToServerMethod = method;
            }
            if (method != null) {
                method.invoke(network, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object cosmeticArmorNetwork() {
        try {
            Field field = cosmeticArmorNetworkField;
            if (field == null) {
                Class<?> type = Class.forName(COSMETIC_ARMOR_MAIN_CLASS, false,
                        BaublesSideSlotsClient.class.getClassLoader());
                field = findField(type, "network");
                cosmeticArmorNetworkField = field;
            }
            return field == null ? null : field.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID playerUniqueId(EntityLivingBase player) {
        if (player == null) {
            return null;
        }
        try {
            Method method = entityUniqueIdMethod;
            if (method == null) {
                method = findMethod(player.getClass(), new Class<?>[0], "func_110124_au", "getUniqueID");
                entityUniqueIdMethod = method;
            }
            Object value = method == null ? null : method.invoke(player);
            return value instanceof UUID ? (UUID) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Slot hoveredSlot(GuiContainer gui) {
        Object value = objectFieldValue(gui, hoveredSlotField, "hoveredSlot", "field_147006_u");
        return value instanceof Slot ? (Slot) value : null;
    }

    private static void clearHoveredBaubleSlot(GuiContainer gui) {
        Slot slot = hoveredSlot(gui);
        if (BaublesSideSlotsCommon.isSideRailSlot(slot)) {
            setHoveredSlot(gui, null);
        }
    }

    private static void setHoveredSlot(GuiContainer gui, Slot slot) {
        if (gui == null) {
            return;
        }
        try {
            Field field = hoveredSlotField != null ? hoveredSlotField : findField(gui.getClass(), "hoveredSlot", "field_147006_u");
            if (field != null) {
                hoveredSlotField = field;
                field.set(gui, slot);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static boolean isPanelVisible(GuiContainer gui) {
        return gui != null && Boolean.TRUE.equals(PANEL_VISIBILITY.get(gui));
    }

    private static boolean isSideRailOpen(GuiContainer gui) {
        return GpomEarlyConfig.baublesSideSlotsEnabled()
                && isSupportedGui(gui)
                && isPanelVisible(gui);
    }

    private static boolean isActiveScreen(Minecraft minecraft, GuiContainer parent) {
        return minecraft != null && ClientAccess.currentScreen(minecraft) == parent;
    }

    private static void setButtonInteractive(Object button, boolean interactive) {
        setBooleanField(button, buttonVisibleField, interactive, "field_146125_m", "visible");
        setBooleanField(button, buttonEnabledField, interactive, "field_146124_l", "enabled");
        if (!interactive) {
            setBooleanField(button, buttonHoveredField, false, "field_146123_n", "hovered");
        }
    }

    private static void positionToggleButton(Object button, GuiContainer parent) {
        if (button == null || parent == null) {
            return;
        }
        setIntField(button, buttonXField, guiLeft(parent) + 64, "field_146128_h", "x");
        setIntField(button, buttonYField, guiTop(parent) + 9, "field_146129_i", "y");
        setIntField(button, buttonWidthField, 10, "field_146120_f", "width");
        setIntField(button, buttonHeightField, 10, "field_146121_g", "height");
    }

    private static boolean buttonContains(Object button, int mouseX, int mouseY) {
        if (button == null
                || !booleanField(button, buttonEnabledField, true, "field_146124_l", "enabled")
                || !booleanField(button, buttonVisibleField, true, "field_146125_m", "visible")) {
            return false;
        }

        int left = intField(button, buttonXField, 0, "field_146128_h", "x");
        int top = intField(button, buttonYField, 0, "field_146129_i", "y");
        int width = intField(button, buttonWidthField, 10, "field_146120_f", "width");
        int height = intField(button, buttonHeightField, 10, "field_146121_g", "height");
        return mouseX >= left && mouseY >= top && mouseX < left + width && mouseY < top + height;
    }

    private static int guiLeft(GuiContainer gui) {
        return intField(gui, "guiLeft", "field_147003_i");
    }

    private static int guiTop(GuiContainer gui) {
        return intField(gui, "guiTop", "field_147009_r");
    }

    private static boolean isSupportedGui(GuiContainer gui) {
        return gui instanceof GuiInventory;
    }

    private static boolean shouldUseReducedHoverOverlay(GuiContainer gui) {
        return GpomEarlyConfig.baublesSideSlotsEnabled()
                && (isSideRailOpen(gui) || isCreativeSurvivalInventoryTab(gui));
    }

    private static boolean isCreativeSurvivalInventoryTab(GuiContainer gui) {
        return gui instanceof GuiContainerCreative
                && creativeSelectedTabIndex((GuiContainerCreative) gui) == CREATIVE_SURVIVAL_INVENTORY_TAB_INDEX;
    }

    private static int creativeSelectedTabIndex(GuiContainerCreative gui) {
        try {
            Field field = creativeSelectedTabIndexField;
            if (field == null) {
                field = findField(gui.getClass(), "selectedTabIndex", "field_147058_w");
                creativeSelectedTabIndexField = field;
            }
            return field == null ? -1 : field.getInt(null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static boolean isShiftKeyDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    private static boolean isSideRailGui(GuiContainer gui) {
        return gui != null && isSupportedGui(gui) && !BaublesSideSlotsCommon.sideRailSlots(inventorySlots(gui)).isEmpty();
    }

    private static Slot findSlotAt(Container container, int guiLeft, int guiTop, int mouseX, int mouseY) {
        for (Object rawSlot : BaublesSideSlotsCommon.slots(container)) {
            Slot slot = (Slot) rawSlot;
            int slotX = BaublesSideSlotsCommon.slotX(slot);
            int slotY = BaublesSideSlotsCommon.slotY(slot);
            if (slotX <= BaublesSideSlotsCommon.HIDDEN_SLOT_POS / 2) {
                continue;
            }
            int x = guiLeft + slotX;
            int y = guiTop + slotY;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private static Layout arrange(GuiContainer gui,
                                  int screenWidth,
                                  int screenHeight,
                                  int xSize,
                                  int ySize,
                                  int guiLeft,
                                  int guiTop) {
        Container container = inventorySlots(gui);
        List<Slot> slots = orderedSideRailSlots(container);
        int total = slots.size();
        if (total <= 0) {
            return new Layout(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
        }

        int columns = Math.min(total, Math.max(1, GpomEarlyConfig.baublesSideSlotsColumns()));
        int visibleRows = Math.min(Math.max(1, GpomEarlyConfig.baublesSideSlotsVisibleRows()), Math.max(1, (total + columns - 1) / columns));
        int pageSize = Math.max(1, visibleRows * columns);
        int maxPage = Math.max(0, (total - 1) / pageSize);
        int page = clamp(PAGE_OFFSETS.getOrDefault(gui, 0), 0, maxPage);
        PAGE_OFFSETS.put(gui, page);

        int firstIndex = page * pageSize;
        int lastIndex = Math.min(total, firstIndex + pageSize);
        int visibleCount = Math.max(0, lastIndex - firstIndex);
        int pageColumns = Math.max(1, Math.min(columns, Math.max(1, visibleCount)));
        int pageRows = Math.min(visibleRows, Math.max(1, (visibleCount + pageColumns - 1) / pageColumns));
        int headerHeight = maxPage > 0 ? PANEL_HEADER_HEIGHT : 0;
        int panelWidth = PANEL_BORDER * 2
                + PANEL_PADDING * 2
                + pageColumns * SLOT_BACKGROUND_SIZE
                + Math.max(0, pageColumns - 1) * SLOT_GAP;
        int panelHeight = PANEL_BORDER * 2
                + PANEL_PADDING * 2
                + headerHeight
                + pageRows * SLOT_BACKGROUND_SIZE
                + Math.max(0, pageRows - 1) * SLOT_GAP;
        boolean canUseLeft = guiLeft - PANEL_MARGIN - panelWidth >= 0;
        boolean canUseRight = guiLeft + xSize + PANEL_MARGIN + panelWidth <= screenWidth;
        boolean useRight = GpomEarlyConfig.baublesSideSlotsPreferRight() ? (canUseRight || !canUseLeft) : (!canUseLeft && canUseRight);
        int panelX = useRight ? xSize + PANEL_MARGIN : -panelWidth - PANEL_MARGIN;
        int maxPanelY = maxPanelY(screenHeight, ySize, guiTop, panelHeight);
        int panelY = clamp(ySize - panelHeight, 4, maxPanelY);
        if (!useRight) {
            panelY = avoidPotionColumn(gui, guiLeft, guiTop, panelX, panelWidth, panelHeight, panelY, maxPanelY);
        }
        for (int index = 0; index < total; index++) {
            Slot slot = slots.get(index);
            if (index < firstIndex || index >= lastIndex) {
                BaublesSideSlotsCommon.setSlotPos(slot,
                        BaublesSideSlotsCommon.HIDDEN_SLOT_POS,
                        BaublesSideSlotsCommon.HIDDEN_SLOT_POS);
            } else {
                int pageIndex = index - firstIndex;
                int row = pageIndex / pageColumns;
                int column = pageIndex % pageColumns;
                BaublesSideSlotsCommon.setSlotPos(slot,
                        panelX + PANEL_BORDER + PANEL_PADDING + column * SLOT_STEP + 1,
                        panelY + PANEL_BORDER + PANEL_PADDING + headerHeight + row * SLOT_STEP + 1);
            }
        }
        return new Layout(panelX, panelY, panelWidth, panelHeight, pageRows, pageColumns, total, page, maxPage, headerHeight, useRight);
    }

    private static void drawSlotTypeIcon(GuiContainer gui, Minecraft minecraft, Slot slot, int slotLeft, int slotTop) {
        if (BaublesSideSlotsCommon.slotHasStack(slot)) {
            return;
        }

        if (AetherSideSlotsBridge.isAccessorySlot(slot)
                || CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            // Native-textured slots expose their own empty-slot sprite through Slot#getSlotTexture.
            return;
        }

        BaublesSideSlotsCommon.SlotType type = BaublesSideSlotsCommon.slotType(slot);
        int icon = Math.max(0, Math.min(BAUBLE_ICON_UV.length - 1, type.ordinal()));
        int[] uv = BAUBLE_ICON_UV[icon];
        ClientAccess.bindTexture(minecraft, BAUBLES_GUI_TEXTURE);
        ClientAccess.drawTexturedModalRect(gui, slotLeft + 2, slotTop + 2, uv[0], uv[1], 16, 16);
    }

    private static List<Slot> orderedBaubleSlots(Container container) {
        List<Slot> slots = new ArrayList<>(BaublesSideSlotsCommon.baubleSlots(container));
        slots.sort(Comparator
                .comparingInt(BaublesSideSlotsClient::sideRailVerticalOrder)
                .thenComparing(BaublesSideSlotsClient::sideRailSlotTooltip, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt((Slot slot) -> slotTypeTieOrder(BaublesSideSlotsCommon.slotType(slot)))
                .thenComparingInt(BaublesSideSlotsCommon::baubleSlotIndex)
                .thenComparingInt(BaublesSideSlotsCommon::slotNumber));
        return slots;
    }

    private static List<Slot> orderedSideRailSlots(Container container) {
        List<Slot> slots = new ArrayList<>(BaublesSideSlotsCommon.sideRailSlots(container));
        slots.sort(Comparator
                .comparingInt(BaublesSideSlotsClient::sideRailVerticalOrder)
                .thenComparing(BaublesSideSlotsClient::sideRailSlotTooltip, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(BaublesSideSlotsClient::sideRailGroupOrder)
                .thenComparingInt(BaublesSideSlotsClient::sideRailSlotOrder)
                .thenComparingInt(BaublesSideSlotsCommon::slotNumber));
        return slots;
    }

    private static int sideRailGroupOrder(Slot slot) {
        if (slot instanceof SlotBauble) {
            return 0;
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return 1;
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return 2;
        }
        return 99;
    }

    private static String sideRailSlotTooltip(Slot slot) {
        if (slot instanceof SlotBauble) {
            return slotTypeLabel(BaublesSideSlotsCommon.slotType(slot));
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return aetherSlotTypeLabel(slot);
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return CosmeticArmorSideSlotsBridge.cosmeticArmorSlotName(slot);
        }
        return "";
    }

    private static int sideRailVerticalOrder(Slot slot) {
        if (slot instanceof SlotBauble) {
            return baubleVerticalOrder(BaublesSideSlotsCommon.slotType(slot));
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return aetherVerticalOrder(AetherSideSlotsBridge.accessorySlotIndex(slot));
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return cosmeticArmorVerticalOrder(CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot));
        }
        return 99;
    }

    private static int sideRailSlotOrder(Slot slot) {
        if (slot instanceof SlotBauble) {
            return slotTypeTieOrder(BaublesSideSlotsCommon.slotType(slot)) * 100
                    + Math.max(0, BaublesSideSlotsCommon.baubleSlotIndex(slot));
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return Math.max(0, AetherSideSlotsBridge.accessorySlotIndex(slot));
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return Math.max(0, CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot));
        }
        return 9999;
    }

    private static int baubleVerticalOrder(BaublesSideSlotsCommon.SlotType type) {
        if (type == null) {
            return 99;
        }
        switch (type) {
            case HEAD:
                return 0;
            case AMULET:
                return 1;
            case BODY:
                return 3;
            case RING:
                return 5;
            case BELT:
                return 6;
            case CHARM:
                return 9;
            case TRINKET:
                return 10;
            case GENERIC:
            default:
                return 11;
        }
    }

    private static int aetherVerticalOrder(int index) {
        switch (index) {
            case 0: // pendant
                return 1;
            case 1: // cape
                return 2;
            case 2: // shield
                return 3;
            case 6: // gloves
                return 4;
            case 4:
            case 5:
                return 5;
            case 3:
            case 7:
                return 11;
            default:
                return 99;
        }
    }

    private static int cosmeticArmorVerticalOrder(int index) {
        switch (index) {
            case 3: // helmet
                return 0;
            case 2: // chestplate
                return 3;
            case 1: // leggings
                return 7;
            case 0: // boots
                return 8;
            default:
                return 99;
        }
    }

    private static int slotTypeTieOrder(BaublesSideSlotsCommon.SlotType type) {
        if (type == null) {
            return 99;
        }
        switch (type) {
            case HEAD:
                return 0;
            case AMULET:
                return 1;
            case BODY:
                return 2;
            case RING:
                return 3;
            case BELT:
                return 4;
            case CHARM:
                return 5;
            case TRINKET:
                return 6;
            case GENERIC:
            default:
                return 7;
        }
    }

    private static String slotTypeLabel(BaublesSideSlotsCommon.SlotType type) {
        if (type == null) {
            return "Accessory";
        }
        switch (type) {
            case AMULET:
                return "Necklace";
            case RING:
                return "Ring";
            case BELT:
                return "Belt";
            case HEAD:
                return "Head";
            case BODY:
                return "Body";
            case CHARM:
                return "Charm";
            case TRINKET:
                return "Trinket";
            case GENERIC:
            default:
                return "Accessory";
        }
    }

    private static String aetherSlotTypeLabel(Slot slot) {
        switch (AetherSideSlotsBridge.accessorySlotIndex(slot)) {
            case 0:
                return "Necklace";
            case 1:
                return "Cape";
            case 2:
                return "Shield";
            case 3:
            case 7:
                return "Misc";
            case 4:
            case 5:
                return "Ring";
            case 6:
                return "Gloves";
            default:
                return "Aether Accessory";
        }
    }

    private static void drawTexturedSidePanel(GuiContainer gui, Minecraft minecraft, int left, int top, int right, int bottom, boolean attachedOnRight) {
        int width = right - left;
        int height = bottom - top;
        if (gui == null || width <= PANEL_CORNER * 2 || height <= PANEL_CORNER * 2) {
            return;
        }

        ClientAccess.bindTexture(minecraft, SIDE_SLOTS_TEXTURE);

        boolean skipLeftBorder = attachedOnRight;
        boolean skipRightBorder = !attachedOnRight;
        int innerLeft = left + (skipLeftBorder ? 0 : PANEL_CORNER);
        int innerRight = right - (skipRightBorder ? 0 : PANEL_CORNER);
        int innerWidth = innerRight - innerLeft;
        int innerHeight = height - PANEL_CORNER * 2;
        if (innerWidth > 0 && innerHeight > 0) {
            drawTiled(gui, innerLeft, top + PANEL_CORNER, innerWidth, innerHeight,
                    PANEL_CENTER_U, PANEL_CENTER_V, PANEL_TILE, PANEL_TILE);
            drawTiled(gui, innerLeft, top, innerWidth, PANEL_CORNER,
                    PANEL_EDGE_U, PANEL_EDGE_V, PANEL_TILE, PANEL_CORNER);
            drawTiled(gui, innerLeft, bottom - PANEL_CORNER, innerWidth, PANEL_CORNER,
                    PANEL_BOTTOM_U, PANEL_BOTTOM_V, PANEL_TILE, PANEL_CORNER);
        }

        if (!skipLeftBorder) {
            drawTiled(gui, left, top + PANEL_CORNER, PANEL_CORNER, innerHeight,
                    PANEL_LEFT_U, PANEL_LEFT_V, PANEL_CORNER, PANEL_TILE);
            ClientAccess.drawTexturedModalRect(gui, left, top, PANEL_TL_U, PANEL_TL_V, PANEL_CORNER, PANEL_CORNER);
            ClientAccess.drawTexturedModalRect(gui, left, bottom - PANEL_CORNER, PANEL_BL_U, PANEL_BL_V, PANEL_CORNER, PANEL_CORNER);
        }
        if (!skipRightBorder) {
            drawTiled(gui, right - PANEL_CORNER, top + PANEL_CORNER, PANEL_CORNER, innerHeight,
                    PANEL_RIGHT_U, PANEL_RIGHT_V, PANEL_CORNER, PANEL_TILE);
            ClientAccess.drawTexturedModalRect(gui, right - PANEL_CORNER, top, PANEL_TR_U, PANEL_TR_V, PANEL_CORNER, PANEL_CORNER);
            ClientAccess.drawTexturedModalRect(gui, right - PANEL_CORNER, bottom - PANEL_CORNER, PANEL_BR_U, PANEL_BR_V, PANEL_CORNER, PANEL_CORNER);
        }
    }

    private static void drawTiled(GuiContainer gui,
                                  int x,
                                  int y,
                                  int width,
                                  int height,
                                  int textureX,
                                  int textureY,
                                  int tileWidth,
                                  int tileHeight) {
        if (width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }

        for (int yOffset = 0; yOffset < height; yOffset += tileHeight) {
            int drawHeight = Math.min(tileHeight, height - yOffset);
            for (int xOffset = 0; xOffset < width; xOffset += tileWidth) {
                int drawWidth = Math.min(tileWidth, width - xOffset);
                ClientAccess.drawTexturedModalRect(gui, x + xOffset, y + yOffset,
                        textureX, textureY, drawWidth, drawHeight);
            }
        }
    }

    private static void hideBaubleSlots(GuiContainer gui) {
        for (Slot slot : BaublesSideSlotsCommon.sideRailSlots(inventorySlots(gui))) {
            hideSlot(slot);
        }
    }

    private static void hideSlot(Slot slot) {
        if (slot != null) {
            BaublesSideSlotsCommon.setSlotPos(slot,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS);
        }
    }

    private static int maxPanelY(int screenHeight, int ySize, int guiTop, int panelHeight) {
        if (screenHeight > 0) {
            return Math.max(4, screenHeight - guiTop - panelHeight - 4);
        }
        return Math.max(4, ySize - panelHeight - 4);
    }

    private static int avoidPotionColumn(GuiContainer gui,
                                         int guiLeft,
                                         int guiTop,
                                         int panelX,
                                         int panelWidth,
                                         int panelHeight,
                                         int panelY,
                                         int maxPanelY) {
        int effectCount = activePotionEffectCount();
        if (effectCount <= 0) {
            return panelY;
        }

        int panelLeft = guiLeft + panelX;
        int panelRight = panelLeft + panelWidth;
        int potionLeft = guiLeft - 124;
        int potionRight = guiLeft - 4;
        if (panelRight <= potionLeft || panelLeft >= potionRight) {
            return panelY;
        }

        int spacing = effectCount > 5 ? 132 / (effectCount - 1) : 33;
        int potionBottom = guiTop + 32 + spacing * (effectCount - 1);
        int shifted = potionBottom + 4 - guiTop;
        return clamp(Math.max(panelY, shifted), 4, maxPanelY);
    }

    @SuppressWarnings("rawtypes")
    private static int activePotionEffectCount() {
        Minecraft minecraft = ClientAccess.minecraft();
        EntityLivingBase player = ClientAccess.player(minecraft);
        if (player == null) {
            return 0;
        }

        try {
            Method method = activePotionEffectsMethod;
            if (method == null) {
                method = findMethod(player.getClass(), new Class<?>[0], "func_70651_bq", "getActivePotionEffects");
                activePotionEffectsMethod = method;
            }
            Object value = method == null ? null : method.invoke(player);
            return value instanceof Collection ? ((Collection) value).size() : 0;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean booleanField(Object owner, Field cached, boolean fallback, String... names) {
        Object value = objectFieldValue(owner, cached, names);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }

    private static int intField(Object owner, Field cached, int fallback, String... names) {
        Object value = objectFieldValue(owner, cached, names);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static void setBooleanField(Object owner, Field cached, boolean value, String... names) {
        if (owner == null) {
            return;
        }
        try {
            Field field = cached != null ? cached : findField(owner.getClass(), names);
            cacheButtonField(field, names[0]);
            if (field != null) {
                field.setBoolean(owner, value);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void setIntField(Object owner, Field cached, int value, String... names) {
        if (owner == null) {
            return;
        }
        try {
            Field field = cached != null ? cached : findField(owner.getClass(), names);
            cacheButtonField(field, names[0]);
            if (field != null) {
                field.setInt(owner, value);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Object objectFieldValue(Object owner, Field cached, String... names) {
        if (owner == null) {
            return null;
        }
        try {
            Field field = cached != null ? cached : findField(owner.getClass(), names);
            cacheButtonField(field, names[0]);
            return field == null ? null : field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static void cacheButtonField(Field field, String firstName) {
        if (field == null) {
            return;
        }
        if ("field_146128_h".equals(firstName)) {
            buttonXField = field;
        } else if ("field_146129_i".equals(firstName)) {
            buttonYField = field;
        } else if ("field_146120_f".equals(firstName)) {
            buttonWidthField = field;
        } else if ("field_146121_g".equals(firstName)) {
            buttonHeightField = field;
        } else if ("field_146125_m".equals(firstName)) {
            buttonVisibleField = field;
        } else if ("field_146124_l".equals(firstName)) {
            buttonEnabledField = field;
        } else if ("field_146123_n".equals(firstName)) {
            buttonHoveredField = field;
        } else if ("parentGui".equals(firstName)) {
            baublesButtonParentField = field;
        } else if ("field_146127_k".equals(firstName)) {
            buttonIdField = field;
        } else if ("field_146292_n".equals(firstName)) {
            buttonListField = field;
        } else if ("hoveredSlot".equals(firstName) || "field_147006_u".equals(firstName)) {
            hoveredSlotField = field;
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        Class<?> type = owner;
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) {
        Class<?> type = owner;
        while (type != null) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> owner, String name, Object singleArgument) {
        Class<?> type = owner;
        Class<?> argumentType = singleArgument == null ? null : singleArgument.getClass();
        while (type != null) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!name.equals(method.getName()) || parameters.length != 1) {
                    continue;
                }
                if (argumentType == null || parameters[0].isAssignableFrom(argumentType)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static final class Layout {
        private final int panelX;
        private final int panelY;
        private final int panelWidth;
        private final int panelHeight;
        private final int visibleRows;
        private final int columns;
        private final int totalSlots;
        private final int page;
        private final int maxPage;
        private final int headerHeight;
        private final boolean useRight;

        private Layout(int panelX, int panelY, int panelWidth, int panelHeight, int visibleRows, int columns, int totalSlots, int page, int maxPage, int headerHeight, boolean useRight) {
            this.panelX = panelX;
            this.panelY = panelY;
            this.panelWidth = panelWidth;
            this.panelHeight = panelHeight;
            this.visibleRows = visibleRows;
            this.columns = columns;
            this.totalSlots = totalSlots;
            this.page = page;
            this.maxPage = maxPage;
            this.headerHeight = headerHeight;
            this.useRight = useRight;
        }
    }

    private static Container inventorySlots(GuiContainer gui) {
        Object value = fieldValue(gui, inventorySlotsField, "inventorySlots", "field_147002_h");
        if (value instanceof Container) {
            return (Container) value;
        }
        return null;
    }

    private static Dimensions dimensions(GuiContainer gui) {
        if (!isSideRailOpen(gui) || !isSideRailGui(gui)) {
            return null;
        }

        int screenWidth = intField(gui, "width", "field_146294_l");
        int screenHeight = intField(gui, "height", "field_146295_m");
        int xSize = intField(gui, "xSize", "field_146999_f");
        int ySize = intField(gui, "ySize", "field_147000_g");
        int guiLeft = intField(gui, "guiLeft", "field_147003_i");
        int guiTop = intField(gui, "guiTop", "field_147009_r");
        if (screenWidth <= 0 || screenHeight <= 0 || xSize <= 0 || ySize <= 0) {
            return null;
        }
        return new Dimensions(screenWidth, screenHeight, xSize, ySize, guiLeft, guiTop);
    }

    private static int intField(GuiContainer gui, String mcpName, String srgName) {
        Object value;
        if ("width".equals(mcpName)) {
            value = fieldValue(gui, widthField, mcpName, srgName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } else if ("height".equals(mcpName)) {
            value = fieldValue(gui, heightField, mcpName, srgName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } else if ("xSize".equals(mcpName)) {
            value = fieldValue(gui, xSizeField, mcpName, srgName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } else if ("ySize".equals(mcpName)) {
            value = fieldValue(gui, ySizeField, mcpName, srgName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } else if ("guiLeft".equals(mcpName)) {
            value = fieldValue(gui, guiLeftField, mcpName, srgName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        } else if ("guiTop".equals(mcpName)) {
            value = fieldValue(gui, guiTopField, mcpName, srgName);
            if (value instanceof Integer) {
                return (Integer) value;
            }
        }
        return 0;
    }

    private static Object fieldValue(GuiContainer gui, Field cached, String mcpName, String srgName) {
        try {
            Field field = cached != null ? cached : findField(mcpName, srgName);
            if ("inventorySlots".equals(mcpName)) {
                inventorySlotsField = field;
            } else if ("width".equals(mcpName)) {
                widthField = field;
            } else if ("height".equals(mcpName)) {
                heightField = field;
            } else if ("xSize".equals(mcpName)) {
                xSizeField = field;
            } else if ("ySize".equals(mcpName)) {
                ySizeField = field;
            } else if ("guiLeft".equals(mcpName)) {
                guiLeftField = field;
            } else if ("guiTop".equals(mcpName)) {
                guiTopField = field;
            }
            return field.get(gui);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findField(String mcpName, String srgName) throws NoSuchFieldException {
        Class<?> type = GuiContainer.class;
        while (type != null) {
            try {
                Field field = type.getDeclaredField(mcpName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            try {
                Field field = type.getDeclaredField(srgName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException(mcpName + "/" + srgName);
    }

    private static final class Dimensions {
        private final int screenWidth;
        private final int screenHeight;
        private final int xSize;
        private final int ySize;
        private final int guiLeft;
        private final int guiTop;

        private Dimensions(int screenWidth, int screenHeight, int xSize, int ySize, int guiLeft, int guiTop) {
            this.screenWidth = screenWidth;
            this.screenHeight = screenHeight;
            this.xSize = xSize;
            this.ySize = ySize;
            this.guiLeft = guiLeft;
            this.guiTop = guiTop;
        }
    }

    private static final class MouseInput {
        private MouseInput() {
        }

        private static int mouseX(int screenWidth, int displayWidth) {
            return org.lwjgl.input.Mouse.getEventX() * screenWidth / displayWidth;
        }

        private static int mouseY(int screenHeight, int displayHeight) {
            return screenHeight - org.lwjgl.input.Mouse.getEventY() * screenHeight / displayHeight - 1;
        }
    }
}
