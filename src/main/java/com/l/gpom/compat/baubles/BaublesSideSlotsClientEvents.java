package com.l.gpom.compat.baubles;

import baubles.api.BaubleType;
import baubles.api.IBauble;
import baubles.api.cap.BaublesCapabilities;
import baubles.client.ClientProxy;
import baubles.common.items.ItemRing;
import com.l.gpom.client.ClientAccess;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class BaublesSideSlotsClientEvents {
    private static volatile Method keyIsPressedMethod;
    private static volatile Method displayGuiScreenMethod;
    private static volatile Method stackHasCapabilityMethod;
    private static volatile Method stackGetCapabilityMethod;
    private static volatile Method i18nFormatMethod;
    private static volatile Method tooltipStackMethod;
    private static volatile Method tooltipListMethod;
    private static volatile Field currentScreenField;
    private static volatile Field inGameHasFocusField;

    @SubscribeEvent
    public void registerItemModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                ItemRing.RING,
                0,
                new ModelResourceLocation("baubles:ring", "inventory")
        );
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled()
                || event.side != Side.CLIENT
                || event.phase != TickEvent.Phase.START
                || !keyPressed(ClientProxy.KEY_BAUBLES)) {
            return;
        }

        openVanillaInventoryWithSideSlots();
    }

    @SubscribeEvent
    public void tooltipEvent(ItemTooltipEvent event) {
        ItemStack stack = tooltipStack(event);
        if (BaublesSideSlotsCommon.isEmptyStack(stack)
                || !hasBaubleCapability(stack)) {
            return;
        }

        IBauble bauble = baubleCapability(stack);
        if (bauble == null) {
            return;
        }

        BaubleType type = bauble.getBaubleType(stack);
        List<String> tooltip = tooltipList(event);
        if (tooltip != null) {
            tooltip.add(TextFormatting.GOLD + i18nFormat("name." + type));
        }
    }

    public static void openVanillaInventoryWithSideSlots() {
        Minecraft minecraft = ClientAccess.minecraft();
        Object player = ClientAccess.player(minecraft);
        if (minecraft == null || !(player instanceof EntityPlayer) || !inGameHasFocus(minecraft)) {
            return;
        }
        Object currentScreen = currentScreen(minecraft);
        if (isCreativePlayer((EntityPlayer) player)) {
            if (!(currentScreen instanceof GuiContainerCreative)) {
                displayGuiScreen(minecraft, new GuiContainerCreative((EntityPlayer) player));
            }
            return;
        }
        if (currentScreen instanceof GuiInventory) {
            BaublesSideSlotsClient.setPanelVisible((GuiInventory) currentScreen, true);
            return;
        }
        GuiInventory inventory = new GuiInventory((EntityPlayer) player);
        BaublesSideSlotsClient.setPanelVisible(inventory, true);
        displayGuiScreen(minecraft, inventory);
    }

    private static boolean isCreativePlayer(EntityPlayer player) {
        if (player == null) {
            return false;
        }
        try {
            return player.isCreative()
                    || (player.capabilities != null && player.capabilities.isCreativeMode);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean keyPressed(Object keyBinding) {
        if (keyBinding == null) {
            return false;
        }
        try {
            Method method = keyIsPressedMethod;
            if (method == null) {
                method = findMethod(keyBinding.getClass(), new Class<?>[0], "func_151468_f", "isPressed");
                keyIsPressedMethod = method;
            }
            Object value = method == null ? null : method.invoke(keyBinding);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static ItemStack tooltipStack(ItemTooltipEvent event) {
        Object value = invokeCached(event, true);
        return value instanceof ItemStack ? (ItemStack) value : BaublesSideSlotsCommon.emptyStack();
    }

    @SuppressWarnings("unchecked")
    private static List<String> tooltipList(ItemTooltipEvent event) {
        Object value = invokeCached(event, false);
        return value instanceof List ? (List<String>) value : null;
    }

    private static Object invokeCached(ItemTooltipEvent event, boolean stack) {
        if (event == null) {
            return null;
        }
        try {
            Method method = stack ? tooltipStackMethod : tooltipListMethod;
            if (method == null) {
                method = findMethod(event.getClass(), new Class<?>[0],
                        stack ? "getItemStack" : "getToolTip");
                if (stack) {
                    tooltipStackMethod = method;
                } else {
                    tooltipListMethod = method;
                }
            }
            return method == null ? null : method.invoke(event);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean hasBaubleCapability(ItemStack stack) {
        Object value = invokeStackCapability(stack, true);
        return value instanceof Boolean && (Boolean) value;
    }

    private static IBauble baubleCapability(ItemStack stack) {
        Object value = invokeStackCapability(stack, false);
        return value instanceof IBauble ? (IBauble) value : null;
    }

    private static Object invokeStackCapability(ItemStack stack, boolean has) {
        if (stack == null) {
            return null;
        }
        try {
            Method method = has ? stackHasCapabilityMethod : stackGetCapabilityMethod;
            if (method == null) {
                method = findMethod(
                        ItemStack.class,
                        new Class<?>[] {Capability.class, EnumFacing.class},
                        has ? "hasCapability" : "getCapability"
                );
                if (has) {
                    stackHasCapabilityMethod = method;
                } else {
                    stackGetCapabilityMethod = method;
                }
            }
            return method == null ? null : method.invoke(stack, BaublesCapabilities.CAPABILITY_ITEM_BAUBLE, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static String i18nFormat(String key) {
        try {
            Method method = i18nFormatMethod;
            if (method == null) {
                method = findMethod(I18n.class, new Class<?>[] {String.class, Object[].class}, "func_135052_a", "format");
                i18nFormatMethod = method;
            }
            Object value = method == null ? null : method.invoke(null, key, new Object[0]);
            return value instanceof String ? (String) value : key;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return key;
        }
    }

    private static boolean inGameHasFocus(Minecraft minecraft) {
        Object value = fieldValue(minecraft, inGameHasFocusField, true, "field_71415_G", "inGameHasFocus");
        return !(value instanceof Boolean) || (Boolean) value;
    }

    private static Object currentScreen(Minecraft minecraft) {
        return fieldValue(minecraft, currentScreenField, false, "field_71462_r", "currentScreen");
    }

    private static void displayGuiScreen(Minecraft minecraft, GuiScreen screen) {
        try {
            Method method = displayGuiScreenMethod;
            if (method == null) {
                method = findMethod(Minecraft.class, new Class<?>[] {GuiScreen.class}, "func_147108_a", "displayGuiScreen");
                displayGuiScreenMethod = method;
            }
            if (method != null) {
                method.invoke(minecraft, screen);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Object fieldValue(Minecraft minecraft, Field cached, boolean focus, String... names) {
        if (minecraft == null) {
            return null;
        }
        try {
            Field field = cached != null ? cached : findField(Minecraft.class, names);
            if (focus) {
                inGameHasFocusField = field;
            } else {
                currentScreenField = field;
            }
            return field == null ? null : field.get(minecraft);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String... names) {
        for (String name : names) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }
}
