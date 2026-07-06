package com.l.gpom.compat.baubles;

import baubles.api.BaubleType;
import baubles.api.cap.IBaublesItemHandler;
import baubles.common.container.SlotBauble;
import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BaublesSideSlotsCommon {
    public static final int HIDDEN_SLOT_POS = -32000;
    private static final int FIRST_BAUBLE_SLOT_INDEX = 9;
    private static final String BRING_ME_THE_RINGS_CONTAINER = "zabi.minecraft.bmtr.components.ContainerBaubles";
    private static final Field CONTAINER_SLOTS = findField(Container.class, "inventorySlots", "field_75151_b");
    private static final Field CONTAINER_TRACKED_STACKS = findField(Container.class, "inventoryItemStacks", "field_75153_a");
    private static final Field CONTAINER_WINDOW_ID = findField(Container.class, "windowId", "field_75152_c");
    private static final Field SLOT_INVENTORY = findField(Slot.class, "inventory", "field_75224_c");
    private static final Field SLOT_INDEX = findField(Slot.class, "slotIndex", "field_75225_a");
    private static final Field SLOT_NUMBER = findField(Slot.class, "slotNumber", "field_75222_d");
    private static final Field SLOT_X = findField(Slot.class, "xPos", "field_75223_e");
    private static final Field SLOT_Y = findField(Slot.class, "yPos", "field_75221_f");
    private static final Field SLOT_BAUBLE_INDEX = findField(SlotBauble.class, "baubleSlot");
    private static final Method SLOT_HAS_STACK = findMethod(Slot.class, "getHasStack", "func_75216_d");
    private static final Method SLOT_GET_STACK = findMethod(Slot.class, "getStack", "func_75211_c");
    private static final Method SLOT_PUT_STACK = findMethod(Slot.class, "putStack", "func_75215_d", ItemStack.class);
    private static final Method SLOT_CHANGED = findMethod(Slot.class, "onSlotChanged", "func_75218_e");
    private static final Method SLOT_DECR_STACK_SIZE = findMethod(Slot.class, "decrStackSize", "func_75209_a", int.class);
    private static final Method SLOT_ON_TAKE = findMethod(Slot.class, "onTake", "func_190901_a", EntityPlayer.class, ItemStack.class);
    private static final Method SLOT_CAN_TAKE = findMethod(Slot.class, "canTakeStack", "func_82869_a", EntityPlayer.class);
    private static final Method SLOT_ITEM_VALID = findMethod(Slot.class, "isItemValid", "func_75214_a", ItemStack.class);
    private static final Method SLOT_ENABLED = findMethod(Slot.class, "isEnabled", "func_111238_b");
    private static Boolean bringMeTheRingsPresent;
    private static boolean loggedBmtrHook;
    private static boolean loggedBmtrSyntheticSlots;

    private BaublesSideSlotsCommon() {
    }

    public interface SlotAppender {
        Slot add(Slot slot);
    }

    public static void prepareContainer(Container container, IBaublesItemHandler handler, EntityPlayer player, SlotAppender appender) {
        prepareContainer(container, handler, player, appender, true);
    }

    public static void prepareContainer(Container container, IBaublesItemHandler handler, EntityPlayer player) {
        prepareContainer(container, handler, player, slot -> appendSlot(container, slot), true);
    }

    public static void prepareVanillaInventoryContainer(Container container, IBaublesItemHandler handler, EntityPlayer player, SlotAppender appender) {
        prepareContainer(container, handler, player, appender, false);
    }

    public static void prepareVanillaInventoryContainer(Container container, IBaublesItemHandler handler, EntityPlayer player) {
        prepareContainer(container, handler, player, slot -> appendSlot(container, slot), false);
    }

    public static void prepareVanillaInventoryContainerObject(Container container, Object handler, EntityPlayer player) {
        if (handler instanceof IBaublesItemHandler) {
            prepareVanillaInventoryContainer(container, (IBaublesItemHandler) handler, player);
        }
    }

    private static void prepareContainer(Container container,
                                         IBaublesItemHandler handler,
                                         EntityPlayer player,
                                         SlotAppender appender,
                                         boolean insertBeforePlayerInventory) {
        if (container == null || handler == null || player == null || appender == null) {
            return;
        }

        int existing = baubleSlots(container).size();
        int handlerSlots = handler.getSlots();
        if (isBringMeTheRingsPresent()) {
            if (!loggedBmtrHook && GpomEarlyConfig.baublesInfoLogsEnabled()) {
                loggedBmtrHook = true;
                GPOM.LOGGER.info("[GPOM Baubles] BringMeTheRings detected; reusing existing Baubles container slots and adding only missing handler slots");
            }
            if (existing < handlerSlots && !loggedBmtrSyntheticSlots && GpomEarlyConfig.baublesInfoLogsEnabled()) {
                loggedBmtrSyntheticSlots = true;
                GPOM.LOGGER.info("[GPOM Baubles] BringMeTheRings exposed {} existing Baubles slots for a {}-slot handler; adding {} missing GPOM side-rail slot(s)",
                        existing, handlerSlots, handlerSlots - existing);
            }
        }

        int total = Math.max(existing, handlerSlots);
        for (int baubleSlot = existing; baubleSlot < total; baubleSlot++) {
            Slot added = appender.add(new SlotBauble(player, handler, baubleSlot, HIDDEN_SLOT_POS, HIDDEN_SLOT_POS));
            if (insertBeforePlayerInventory) {
                moveSlot(container, added, FIRST_BAUBLE_SLOT_INDEX + baubleSlot);
            }
        }
        renumberSlots(container);
    }

    public static Slot appendSlot(Container container, Slot slot) {
        if (container == null || slot == null) {
            return null;
        }

        try {
            List<Slot> slots = slots(container);
            List<ItemStack> trackedStacks = trackedStacks(container);
            setIntField(SLOT_NUMBER, slot, slots.size());
            slots.add(slot);
            trackedStacks.add(emptyStack());
            return slot;
        } catch (RuntimeException exception) {
            GPOM.LOGGER.warn("[GPOM Baubles] Could not append side-rail slot to container {}", container.getClass().getName(), exception);
            return null;
        }
    }

    public static boolean removeSlotAt(Container container, int index) {
        if (container == null || index < 0) {
            return false;
        }

        List<Slot> slots = slots(container);
        if (index >= slots.size()) {
            return false;
        }

        slots.remove(index);
        List<ItemStack> trackedStacks = trackedStacks(container);
        if (index < trackedStacks.size()) {
            trackedStacks.remove(index);
        }
        renumberSlots(container);
        return true;
    }

    public static List<Slot> baubleSlots(Container container) {
        List<Slot> baubles = new ArrayList<>();
        if (container == null) {
            return baubles;
        }

        for (Object rawSlot : slots(container)) {
            if (rawSlot instanceof SlotBauble) {
                baubles.add((Slot) rawSlot);
            }
        }
        return baubles;
    }

    public static List<Slot> sideRailSlots(Container container) {
        List<Slot> sideSlots = baubleSlots(container);
        sideSlots.addAll(AetherSideSlotsBridge.accessorySlots(container));
        sideSlots.addAll(CosmeticArmorSideSlotsBridge.cosmeticArmorSlots(container));
        return sideSlots;
    }

    public static boolean isSideRailSlot(Slot slot) {
        return slot instanceof SlotBauble
                || AetherSideSlotsBridge.isAccessorySlot(slot)
                || CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot);
    }

    public static Slot findEmptyVanillaArmorTarget(Container container, ItemStack stack) {
        if (container == null || isEmptyStack(stack)) {
            return null;
        }

        for (Slot slot : slots(container)) {
            if (isVanillaArmorSlot(slot)
                    && !slotHasStack(slot)
                    && isSlotEnabled(slot)
                    && isSlotItemValid(slot, stack)) {
                return slot;
            }
        }
        return null;
    }

    public static boolean isValidForVanillaArmorSlot(Container container, ItemStack stack) {
        if (container == null || isEmptyStack(stack)) {
            return false;
        }

        for (Slot slot : slots(container)) {
            if (isVanillaArmorSlot(slot)
                    && isSlotEnabled(slot)
                    && isSlotItemValid(slot, stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isVanillaArmorSlot(Slot slot) {
        if (slot == null || isSideRailSlot(slot)) {
            return false;
        }
        IInventory inventory = slotInventory(slot);
        int index = slotIndex(slot);
        return inventory instanceof InventoryPlayer && index >= 36 && index <= 39;
    }

    public static boolean isPlayerMainInventorySlot(Slot slot) {
        if (slot == null || isSideRailSlot(slot)) {
            return false;
        }
        IInventory inventory = slotInventory(slot);
        int index = slotIndex(slot);
        return inventory instanceof InventoryPlayer && index >= 0 && index < 36;
    }

    @SuppressWarnings("unchecked")
    public static List<Slot> slots(Container container) {
        Object value = fieldValue(CONTAINER_SLOTS, container);
        return value instanceof List ? (List<Slot>) value : Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> trackedStacks(Container container) {
        Object value = fieldValue(CONTAINER_TRACKED_STACKS, container);
        return value instanceof List ? (List<ItemStack>) value : Collections.emptyList();
    }

    public static int windowId(Container container) {
        return intField(CONTAINER_WINDOW_ID, container, -1);
    }

    public static int slotNumber(Slot slot) {
        return intField(SLOT_NUMBER, slot, -1);
    }

    public static int slotIndex(Slot slot) {
        return intField(SLOT_INDEX, slot, -1);
    }

    public static int slotX(Slot slot) {
        return intField(SLOT_X, slot, HIDDEN_SLOT_POS);
    }

    public static int slotY(Slot slot) {
        return intField(SLOT_Y, slot, HIDDEN_SLOT_POS);
    }

    public static void setSlotPos(Slot slot, int x, int y) {
        setIntField(SLOT_X, slot, x);
        setIntField(SLOT_Y, slot, y);
    }

    public static IInventory slotInventory(Slot slot) {
        Object value = fieldValue(SLOT_INVENTORY, slot);
        return value instanceof IInventory ? (IInventory) value : null;
    }

    public static int baubleSlotIndex(Slot slot) {
        return slot instanceof SlotBauble ? intField(SLOT_BAUBLE_INDEX, slot, -1) : -1;
    }

    public static SlotType slotType(Slot slot) {
        int index = baubleSlotIndex(slot);
        if (index < 0) {
            return SlotType.GENERIC;
        }

        if (BaubleType.AMULET.hasSlot(index)) {
            return SlotType.AMULET;
        }
        if (BaubleType.RING.hasSlot(index)) {
            return SlotType.RING;
        }
        if (BaubleType.BELT.hasSlot(index)) {
            return SlotType.BELT;
        }
        if (BaubleType.HEAD.hasSlot(index)) {
            return SlotType.HEAD;
        }
        if (BaubleType.BODY.hasSlot(index)) {
            return SlotType.BODY;
        }
        if (BaubleType.CHARM.hasSlot(index)) {
            return SlotType.CHARM;
        }
        if (BaubleType.TRINKET.hasSlot(index)) {
            return SlotType.TRINKET;
        }
        return SlotType.GENERIC;
    }

    public static boolean slotHasStack(Slot slot) {
        Object value = invoke(SLOT_HAS_STACK, slot);
        return value instanceof Boolean && (Boolean) value;
    }

    public static ItemStack slotStack(Slot slot) {
        Object value = invoke(SLOT_GET_STACK, slot);
        return value instanceof ItemStack ? (ItemStack) value : emptyStack();
    }

    public static void putSlotStack(Slot slot, ItemStack stack) {
        invoke(SLOT_PUT_STACK, slot, stack == null ? emptyStack() : stack);
    }

    public static void slotChanged(Slot slot) {
        invoke(SLOT_CHANGED, slot);
    }

    public static ItemStack decrSlotStack(Slot slot, int amount) {
        Object value = invoke(SLOT_DECR_STACK_SIZE, slot, amount);
        return value instanceof ItemStack ? (ItemStack) value : emptyStack();
    }

    public static ItemStack onTakeSlotStack(Slot slot, EntityPlayer player, ItemStack stack) {
        Object value = invoke(SLOT_ON_TAKE, slot, player, stack);
        return value instanceof ItemStack ? (ItemStack) value : stack;
    }

    public static boolean canTakeSlotStack(Slot slot, EntityPlayer player) {
        Object value = invoke(SLOT_CAN_TAKE, slot, player);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static boolean isSlotItemValid(Slot slot, ItemStack stack) {
        Object value = invoke(SLOT_ITEM_VALID, slot, stack);
        return value instanceof Boolean && (Boolean) value;
    }

    public static boolean isSlotEnabled(Slot slot) {
        Object value = invoke(SLOT_ENABLED, slot);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    public static ItemStack emptyStack() {
        return MinecraftMappingCompat.emptyStack();
    }

    public static boolean isEmptyStack(ItemStack stack) {
        return MinecraftMappingCompat.itemStackIsEmpty(stack);
    }

    public static ItemStack copyStack(ItemStack stack) {
        if (isEmptyStack(stack)) {
            return emptyStack();
        }
        ItemStack copy = MinecraftMappingCompat.itemStackCopy(stack);
        return copy == null ? emptyStack() : copy;
    }

    public static int stackCount(ItemStack stack) {
        return MinecraftMappingCompat.itemStackCount(stack);
    }

    public static void setStackCount(ItemStack stack, int count) {
        MinecraftMappingCompat.itemStackSetCount(stack, count);
    }

    public static void shrinkStack(ItemStack stack, int amount) {
        if (stack == null || amount <= 0) {
            return;
        }
        int before = stackCount(stack);
        MinecraftMappingCompat.itemStackShrink(stack, amount);
        if (before > 0 && stackCount(stack) == before) {
            setStackCount(stack, Math.max(0, before - amount));
        }
    }

    public static boolean moveOneItemToSlot(Slot source, Slot target, ItemStack sourceStack) {
        if (source == null || target == null || isEmptyStack(sourceStack)) {
            return false;
        }

        ItemStack moved = copyStack(sourceStack);
        if (isEmptyStack(moved)) {
            return false;
        }

        setStackCount(moved, 1);
        shrinkStack(sourceStack, 1);
        if (isEmptyStack(sourceStack)) {
            putSlotStack(source, emptyStack());
        } else {
            slotChanged(source);
        }
        putSlotStack(target, moved);
        slotChanged(target);
        return true;
    }

    public static String slotDebug(Slot slot) {
        if (slot == null) {
            return "null";
        }
        IInventory inventory = slotInventory(slot);
        String inventoryName = inventory == null ? "null" : inventory.getClass().getName();
        return slot.getClass().getName()
                + "{slotNumber=" + slotNumber(slot)
                + ", slotIndex=" + slotIndex(slot)
                + ", baubleIndex=" + baubleSlotIndex(slot)
                + ", x=" + slotX(slot)
                + ", y=" + slotY(slot)
                + ", enabled=" + isSlotEnabled(slot)
                + ", sideRail=" + isSideRailSlot(slot)
                + ", hasStack=" + slotHasStack(slot)
                + ", inventory=" + inventoryName
                + ", stack=" + stackDebug(slotStack(slot))
                + "}";
    }

    public static String stackDebug(ItemStack stack) {
        if (isEmptyStack(stack)) {
            return "EMPTY";
        }
        return String.valueOf(stack) + " x" + stackCount(stack);
    }

    private static void moveSlot(Container container, Slot added, int targetIndex) {
        if (added == null || targetIndex < 0) {
            return;
        }

        List<Slot> slots = slots(container);
        List<ItemStack> trackedStacks = trackedStacks(container);
        int sourceIndex = slots.indexOf(added);
        if (sourceIndex < 0 || sourceIndex == targetIndex) {
            return;
        }

        Slot slot = slots.remove(sourceIndex);
        ItemStack tracked = trackedStacks.remove(sourceIndex);
        int boundedTarget = Math.min(targetIndex, slots.size());
        slots.add(boundedTarget, slot);
        trackedStacks.add(boundedTarget, tracked);
    }

    private static void renumberSlots(Container container) {
        List<Slot> slots = slots(container);
        for (int index = 0; index < slots.size(); index++) {
            setIntField(SLOT_NUMBER, slots.get(index), index);
        }
    }

    public static boolean isBringMeTheRingsPresent() {
        Boolean cached = bringMeTheRingsPresent;
        if (cached != null) {
            return cached;
        }

        boolean present = false;
        try {
            present = Loader.isModLoaded("bmtr");
        } catch (RuntimeException ignored) {
        }
        if (!present) {
            present = classPresent(BRING_ME_THE_RINGS_CONTAINER);
        }
        bringMeTheRingsPresent = present;
        return present;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, BaublesSideSlotsCommon.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static Object fieldValue(Field field, Object owner) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static int intField(Field field, Object owner, int fallback) {
        if (field == null) {
            return fallback;
        }
        try {
            return field.getInt(owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return fallback;
        }
    }

    private static void setIntField(Field field, Object owner, int value) {
        if (field == null) {
            return;
        }
        try {
            field.setInt(owner, value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Object invoke(Method method, Object owner, Object... args) {
        if (method == null || owner == null) {
            return null;
        }
        try {
            return method.invoke(owner, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        try {
            return ReflectionLookup.findField(owner, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, String mcpName, String srgName, Class<?>... parameterTypes) {
        try {
            return ReflectionLookup.findMethod(owner, mcpName, srgName, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    public enum SlotType {
        AMULET,
        RING,
        BELT,
        HEAD,
        BODY,
        CHARM,
        TRINKET,
        GENERIC
    }
}
