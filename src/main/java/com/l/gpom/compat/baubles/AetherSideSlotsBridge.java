package com.l.gpom.compat.baubles;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AetherSideSlotsBridge {
    private static final String MOD_ID = "aether_legacy";
    private static final String AETHER_API_CLASS = "com.gildedgames.the_aether.api.AetherAPI";
    private static final String SLOT_ACCESSORY_CLASS = "com.gildedgames.the_aether.containers.slots.SlotAccessory";
    private static final String ACCESSORY_TYPE_CLASS = "com.gildedgames.the_aether.api.accessories.AccessoryType";
    private static final String[] ACCESSORY_TYPES = {
            "PENDANT",
            "CAPE",
            "SHIELD",
            "MISC",
            "RING",
            "RING",
            "GLOVE",
            "MISC"
    };
    private static final String[] ACCESSORY_NAMES = {
            "Pendant",
            "Cape",
            "Shield",
            "Misc 1",
            "Ring 1",
            "Ring 2",
            "Gloves",
            "Misc 2"
    };
    private static final int[] ACCESSORY_INDEXES_BY_VERTICAL_ORDER = {0, 1, 2, 6, 4, 5, 3, 7};
    private static final ResourceLocation[] ACCESSORY_ICONS = {
            new ResourceLocation(MOD_ID, "textures/items/slots/pendant.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/cape.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/shield.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/misc.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/ring.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/ring.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/gloves.png"),
            new ResourceLocation(MOD_ID, "textures/items/slots/misc.png")
    };
    private static volatile Class<?> slotAccessoryClass;
    private static volatile Class<?> accessoryTypeClass;
    private static volatile Constructor<?> slotAccessoryConstructor;
    private static volatile Method getAetherApiInstanceMethod;
    private static volatile Method getPlayerAetherMethod;
    private static volatile Method getAccessoryInventoryMethod;
    private static volatile Method inventorySizeMethod;
    private static volatile Method inventoryStackMethod;
    private static volatile Object[] accessoryTypes;
    private static Boolean present;
    private static boolean loggedFailure;

    private AetherSideSlotsBridge() {
    }

    public static void prepare(Container container, EntityPlayer player) {
        if (!enabledAndPresent()) {
            return;
        }

        IInventory inventory = accessoryInventory(player);
        if (container == null || player == null || inventory == null) {
            return;
        }

        prepareContainer(container, inventory, player, slot -> BaublesSideSlotsCommon.appendSlot(container, slot));
    }

    public static void prepareContainer(Container container,
                                        IInventory inventory,
                                        EntityPlayer player,
                                        BaublesSideSlotsCommon.SlotAppender appender) {
        if (!enabledAndPresent() || container == null || inventory == null || player == null || appender == null) {
            return;
        }

        Set<Integer> existingIndexes = new HashSet<>();
        for (Slot slot : accessorySlots(container)) {
            existingIndexes.add(BaublesSideSlotsCommon.slotIndex(slot));
        }

        int size = Math.min(inventorySize(inventory), ACCESSORY_TYPES.length);
        for (int index = 0; index < size; index++) {
            if (existingIndexes.contains(index)) {
                continue;
            }

            Slot slot = createAccessorySlot(inventory, index, player);
            if (slot != null) {
                appender.add(slot);
            }
        }
    }

    public static List<Slot> accessorySlots(Container container) {
        List<Slot> accessories = new ArrayList<>();
        if (!enabledAndPresent() || container == null) {
            return accessories;
        }

        Class<?> type = slotAccessoryClass();
        if (type == null) {
            return accessories;
        }

        for (Object rawSlot : BaublesSideSlotsCommon.slots(container)) {
            if (rawSlot instanceof Slot && type.isInstance(rawSlot)) {
                accessories.add((Slot) rawSlot);
            }
        }
        return accessories;
    }

    public static boolean isAccessorySlot(Slot slot) {
        if (!enabledAndPresent() || slot == null) {
            return false;
        }

        Class<?> type = slotAccessoryClass();
        return type != null && type.isInstance(slot);
    }

    public static int accessorySlotIndex(Slot slot) {
        return isAccessorySlot(slot) ? BaublesSideSlotsCommon.slotIndex(slot) : -1;
    }

    public static ResourceLocation accessoryIcon(Slot slot) {
        int index = accessorySlotIndex(slot);
        if (index < 0 || index >= ACCESSORY_ICONS.length) {
            return null;
        }
        return ACCESSORY_ICONS[index];
    }

    public static String accessorySlotName(Slot slot) {
        int index = accessorySlotIndex(slot);
        if (index < 0 || index >= ACCESSORY_NAMES.length) {
            return "";
        }
        return ACCESSORY_NAMES[index];
    }

    public static boolean quickEquip(EntityPlayer player, Slot source, ItemStack sourceStack) {
        if (!enabledAndPresent()
                || player == null
                || source == null
                || BaublesSideSlotsCommon.isEmptyStack(sourceStack)) {
            return false;
        }

        IInventory inventory = accessoryInventory(player);
        if (inventory == null) {
            return false;
        }

        int size = Math.min(inventorySize(inventory), ACCESSORY_TYPES.length);
        for (int index : ACCESSORY_INDEXES_BY_VERTICAL_ORDER) {
            if (index >= size) {
                continue;
            }
            if (!BaublesSideSlotsCommon.isEmptyStack(inventoryStack(inventory, index))) {
                continue;
            }

            Slot target = createAccessorySlot(inventory, index, player);
            if (target == null || !BaublesSideSlotsCommon.isSlotItemValid(target, sourceStack)) {
                continue;
            }

            return BaublesSideSlotsCommon.moveOneItemToSlot(source, target, sourceStack);
        }
        return false;
    }

    private static int inventorySize(IInventory inventory) {
        if (inventory == null) {
            return 0;
        }
        try {
            Method method = inventorySizeMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[0], "func_70302_i_", "getSizeInventory");
                inventorySizeMethod = method;
            }
            Object value = method == null ? null : method.invoke(inventory);
            return value instanceof Number ? ((Number) value).intValue() : 0;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not read Aether accessory inventory size", exception);
            return 0;
        }
    }

    private static ItemStack inventoryStack(IInventory inventory, int index) {
        if (inventory == null || index < 0) {
            return BaublesSideSlotsCommon.emptyStack();
        }
        try {
            Method method = inventoryStackMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[] {int.class}, "func_70301_a", "getStackInSlot");
                inventoryStackMethod = method;
            }
            Object value = method == null ? null : method.invoke(inventory, index);
            return value instanceof ItemStack ? (ItemStack) value : BaublesSideSlotsCommon.emptyStack();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not read Aether accessory inventory stack", exception);
            return BaublesSideSlotsCommon.emptyStack();
        }
    }

    private static IInventory accessoryInventory(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        try {
            Object api = aetherApi();
            if (api == null) {
                return null;
            }

            Method getPlayerAether = getPlayerAetherMethod;
            if (getPlayerAether == null) {
                getPlayerAether = api.getClass().getMethod("get", EntityPlayer.class);
                getPlayerAether.setAccessible(true);
                getPlayerAetherMethod = getPlayerAether;
            }

            Object playerAether = getPlayerAether.invoke(api, player);
            if (playerAether == null) {
                return null;
            }

            Method getAccessoryInventory = getAccessoryInventoryMethod;
            if (getAccessoryInventory == null || !getAccessoryInventory.getDeclaringClass().isAssignableFrom(playerAether.getClass())) {
                getAccessoryInventory = playerAether.getClass().getMethod("getAccessoryInventory");
                getAccessoryInventory.setAccessible(true);
                getAccessoryInventoryMethod = getAccessoryInventory;
            }

            Object value = getAccessoryInventory.invoke(playerAether);
            return value instanceof IInventory ? (IInventory) value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not resolve Aether accessory inventory for side slots", exception);
            return null;
        }
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) {
        if (type == null || names == null) {
            return null;
        }
        try {
            return ReflectionLookup.findMethod(type, names, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Slot createAccessorySlot(IInventory inventory, int index, EntityPlayer player) {
        Object type = accessoryType(index);
        if (type == null) {
            return null;
        }

        try {
            Constructor<?> constructor = slotAccessoryConstructor;
            if (constructor == null) {
                Class<?> slotClass = slotAccessoryClass();
                Class<?> typeClass = accessoryTypeClass();
                if (slotClass == null || typeClass == null) {
                    return null;
                }
                constructor = slotClass.getConstructor(IInventory.class, int.class, typeClass, int.class, int.class, EntityPlayer.class);
                constructor.setAccessible(true);
                slotAccessoryConstructor = constructor;
            }

            Object slot = constructor.newInstance(inventory, index, type,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS,
                    player);
            return slot instanceof Slot ? (Slot) slot : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not create Aether accessory side slot", exception);
            return null;
        }
    }

    private static Object accessoryType(int index) {
        if (index < 0 || index >= ACCESSORY_TYPES.length) {
            return null;
        }

        Object[] cached = accessoryTypes;
        if (cached == null) {
            cached = new Object[ACCESSORY_TYPES.length];
            Class<?> typeClass = accessoryTypeClass();
            if (typeClass == null) {
                return null;
            }
            for (int slot = 0; slot < ACCESSORY_TYPES.length; slot++) {
                cached[slot] = enumConstant(typeClass, ACCESSORY_TYPES[slot]);
            }
            accessoryTypes = cached;
        }
        return cached[index];
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> enumClass, String name) {
        if (enumClass == null || name == null || !Enum.class.isAssignableFrom(enumClass)) {
            return null;
        }
        return Enum.valueOf((Class<? extends Enum>) enumClass.asSubclass(Enum.class), name.toUpperCase(Locale.ROOT));
    }

    private static Object aetherApi() throws ReflectiveOperationException {
        Method method = getAetherApiInstanceMethod;
        if (method == null) {
            Class<?> apiClass = Class.forName(AETHER_API_CLASS, false, AetherSideSlotsBridge.class.getClassLoader());
            method = apiClass.getMethod("getInstance");
            method.setAccessible(true);
            getAetherApiInstanceMethod = method;
        }
        return method.invoke(null);
    }

    private static Class<?> slotAccessoryClass() {
        Class<?> cached = slotAccessoryClass;
        if (cached != null) {
            return cached;
        }
        try {
            cached = Class.forName(SLOT_ACCESSORY_CLASS, false, AetherSideSlotsBridge.class.getClassLoader());
            slotAccessoryClass = cached;
            return cached;
        } catch (ClassNotFoundException | LinkageError exception) {
            logFailure("Could not resolve Aether SlotAccessory class", exception);
            return null;
        }
    }

    private static Class<?> accessoryTypeClass() {
        Class<?> cached = accessoryTypeClass;
        if (cached != null) {
            return cached;
        }
        try {
            cached = Class.forName(ACCESSORY_TYPE_CLASS, false, AetherSideSlotsBridge.class.getClassLoader());
            accessoryTypeClass = cached;
            return cached;
        } catch (ClassNotFoundException | LinkageError exception) {
            logFailure("Could not resolve Aether AccessoryType class", exception);
            return null;
        }
    }

    private static boolean enabledAndPresent() {
        if (!GpomEarlyConfig.baublesSideSlotsAetherEnabled()) {
            return false;
        }

        Boolean cached = present;
        if (cached != null) {
            return cached;
        }

        boolean loaded = false;
        try {
            loaded = Loader.isModLoaded(MOD_ID);
        } catch (RuntimeException ignored) {
        }
        if (!loaded) {
            loaded = classPresent(SLOT_ACCESSORY_CLASS) && classPresent(AETHER_API_CLASS);
        }
        present = loaded;
        return loaded;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, AetherSideSlotsBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static void logFailure(String message, Throwable exception) {
        if (!loggedFailure) {
            loggedFailure = true;
            GPOM.LOGGER.warn("[GPOM Baubles] {}", message, exception);
        }
    }
}
