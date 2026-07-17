package com.l.gpom.compat.baubles;

import com.l.gpom.GPOM;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import baubles.api.IBauble;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CosmeticArmorSideSlotsBridge {
    private static final String MOD_ID = "cosmeticarmorreworked";
    private static final String MAIN_CLASS = "lain.mods.cos.CosmeticArmorReworked";
    private static final String CLIENT_MANAGER_CLASS = "lain.mods.cos.client.InventoryManagerClient";
    private static final String NETWORK_PACKET_CLASS = "lain.mods.cos.network.NetworkPacket";
    private static final String PACKET_SYNC_COS_ARMOR_CLASS = "lain.mods.cos.network.packet.PacketSyncCosArmor";
    private static final String[] SLOT_NAMES = {
            "Cosmetic Boots",
            "Cosmetic Leggings",
            "Cosmetic Chestplate",
            "Cosmetic Helmet"
    };
    private static final String[] SLOT_TEXTURES = {
            "minecraft:items/empty_armor_slot_boots",
            "minecraft:items/empty_armor_slot_leggings",
            "minecraft:items/empty_armor_slot_chestplate",
            "minecraft:items/empty_armor_slot_helmet"
    };
    private static final EntityEquipmentSlot[] EQUIPMENT_SLOTS = {
            EntityEquipmentSlot.FEET,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.HEAD
    };
    private static final int[] SLOT_INDEXES_BY_VERTICAL_ORDER = {3, 2, 1, 0};
    private static volatile Field inventoryManagerField;
    private static volatile Method getServerInventoryMethod;
    private static volatile Method getClientInventoryMethod;
    private static volatile Method inventoryStackMethod;
    private static volatile Method stackItemMethod;
    private static volatile Field networkField;
    private static volatile java.lang.reflect.Constructor<?> packetSyncCosArmorConstructor;
    private static volatile Method networkSendToAllMethod;
    private static final ConcurrentMap<Class<?>, Method> VALID_ARMOR_METHODS = new ConcurrentHashMap<>();
    private static volatile Boolean present;
    private static boolean loggedFailure;

    private CosmeticArmorSideSlotsBridge() {
    }

    public static void prepare(Container container, EntityPlayer player) {
        if (!enabledAndPresent()) {
            return;
        }

        IInventory inventory = cosmeticInventory(player);
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
        for (Slot slot : cosmeticArmorSlots(container)) {
            existingIndexes.add(BaublesSideSlotsCommon.slotIndex(slot));
        }

        for (int index = 0; index < SLOT_NAMES.length; index++) {
            if (existingIndexes.contains(index)) {
                continue;
            }

            appender.add(new CosmeticArmorSlot(inventory, index, player));
        }
    }

    public static List<Slot> cosmeticArmorSlots(Container container) {
        List<Slot> slots = new ArrayList<>();
        if (!enabledAndPresent() || container == null) {
            return slots;
        }

        for (Object rawSlot : BaublesSideSlotsCommon.slots(container)) {
            if (rawSlot instanceof CosmeticArmorSlot) {
                slots.add((Slot) rawSlot);
            }
        }
        return slots;
    }

    public static boolean isCosmeticArmorSlot(Slot slot) {
        return enabledAndPresent() && slot instanceof CosmeticArmorSlot;
    }

    public static int cosmeticArmorSlotIndex(Slot slot) {
        return slot instanceof CosmeticArmorSlot ? ((CosmeticArmorSlot) slot).cosmeticSlotIndex : -1;
    }

    public static void resetClientState() {
    }

    public static String cosmeticArmorSlotName(Slot slot) {
        int index = cosmeticArmorSlotIndex(slot);
        if (index < 0 || index >= SLOT_NAMES.length) {
            return "";
        }
        return SLOT_NAMES[index];
    }

    public static boolean quickEquip(EntityPlayer player, Slot source, ItemStack sourceStack) {
        if (!enabledAndPresent()
                || player == null
                || source == null
                || BaublesSideSlotsCommon.isEmptyStack(sourceStack)) {
            return false;
        }

        IInventory inventory = cosmeticInventory(player);
        if (inventory == null) {
            return false;
        }

        for (int index : SLOT_INDEXES_BY_VERTICAL_ORDER) {
            if (!BaublesSideSlotsCommon.isEmptyStack(inventoryStack(inventory, index))) {
                continue;
            }

            Slot target = new CosmeticArmorSlot(inventory, index, player);
            if (!BaublesSideSlotsCommon.isSlotItemValid(target, sourceStack)) {
                continue;
            }

            if (BaublesSideSlotsCommon.moveOneItemToSlot(source, target, sourceStack)) {
                syncSlot(player, index);
                return true;
            }
        }
        return false;
    }

    public static void syncSlot(EntityPlayer player, int index) {
        if (!enabledAndPresent() || player == null || index < 0) {
            return;
        }

        try {
            Object network = cosmeticArmorNetwork();
            if (network == null) {
                return;
            }

            Object packet = syncPacket(player, index);
            if (packet == null) {
                return;
            }

            Method send = networkSendToAllMethod;
            if (send == null || !send.getDeclaringClass().isAssignableFrom(network.getClass())) {
                Class<?> packetType = Class.forName(NETWORK_PACKET_CLASS, false, CosmeticArmorSideSlotsBridge.class.getClassLoader());
                send = findMethod(network.getClass(), new Class<?>[] {packetType}, "sendToAll");
                networkSendToAllMethod = send;
            }
            if (send != null) {
                send.invoke(network, packet);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not sync Cosmetic Armor side slot", exception);
        }
    }

    private static Object cosmeticArmorNetwork() throws ReflectiveOperationException {
        Field field = networkField;
        if (field == null) {
            Class<?> type = Class.forName(MAIN_CLASS, false, CosmeticArmorSideSlotsBridge.class.getClassLoader());
            field = findField(type, "network");
            networkField = field;
        }
        return field == null ? null : field.get(null);
    }

    private static Object syncPacket(EntityPlayer player, int index) throws ReflectiveOperationException {
        java.lang.reflect.Constructor<?> constructor = packetSyncCosArmorConstructor;
        if (constructor == null) {
            Class<?> packetType = Class.forName(PACKET_SYNC_COS_ARMOR_CLASS, false, CosmeticArmorSideSlotsBridge.class.getClassLoader());
            constructor = packetType.getConstructor(EntityPlayer.class, int.class);
            constructor.setAccessible(true);
            packetSyncCosArmorConstructor = constructor;
        }
        return constructor.newInstance(player, index);
    }

    private static IInventory cosmeticInventory(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        UUID uuid = MinecraftMappingCompat.playerUniqueId(player);
        if (uuid == null) {
            return null;
        }

        try {
            Object manager = inventoryManager();
            if (manager == null) {
                return null;
            }

            Method method;
            boolean clientInventory = !(player instanceof EntityPlayerMP)
                    && CLIENT_MANAGER_CLASS.equals(manager.getClass().getName());
            if (clientInventory) {
                method = getClientInventoryMethod;
                if (method == null || !method.getDeclaringClass().isAssignableFrom(manager.getClass())) {
                    method = findMethod(manager.getClass(), new Class<?>[] {UUID.class}, "getCosArmorInventoryClient");
                    getClientInventoryMethod = method;
                }
            } else {
                method = getServerInventoryMethod;
                if (method == null || !method.getDeclaringClass().isAssignableFrom(manager.getClass())) {
                    method = findMethod(manager.getClass(), new Class<?>[] {UUID.class}, "getCosArmorInventory");
                    getServerInventoryMethod = method;
                }
            }

            Object value = method == null ? null : method.invoke(manager, uuid);
            if (!(value instanceof IInventory)) {
                return null;
            }
            return (IInventory) value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not resolve Cosmetic Armor inventory for side slots", exception);
            return null;
        }
    }

    private static Object inventoryManager() throws ReflectiveOperationException {
        Field field = inventoryManagerField;
        if (field == null) {
            Class<?> type = Class.forName(MAIN_CLASS, false, CosmeticArmorSideSlotsBridge.class.getClassLoader());
            field = findField(type, "invMan");
            inventoryManagerField = field;
        }
        return field == null ? null : field.get(null);
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
            logFailure("Could not read Cosmetic Armor inventory stack", exception);
            return BaublesSideSlotsCommon.emptyStack();
        }
    }

    private static boolean isValidArmorStack(ItemStack stack, EntityEquipmentSlot equipmentSlot, EntityPlayer player) {
        if (BaublesSideSlotsCommon.isEmptyStack(stack) || equipmentSlot == null) {
            return false;
        }
        try {
            Method getItem = stackItemMethod;
            if (getItem == null || !getItem.getDeclaringClass().isAssignableFrom(stack.getClass())) {
                getItem = findMethod(stack.getClass(), new Class<?>[0], "func_77973_b", "getItem");
                stackItemMethod = getItem;
            }
            Object item = getItem == null ? null : getItem.invoke(stack);
            if (!(item instanceof Item) || item instanceof IBauble) {
                // Functional Baubles such as Thaumcraft's Goggles of Revealing
                // must stay in the real Baubles slot; Cosmetic Armor is never
                // inspected by gameplay systems.
                return false;
            }
            Method validArmor = validArmorMethod(item.getClass());
            Object value = validArmor == null ? null : validArmor.invoke(item, stack, equipmentSlot, player);
            return value instanceof Boolean && (Boolean) value;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            logFailure("Could not validate Cosmetic Armor side slot item", exception);
            return false;
        }
    }

    private static Method validArmorMethod(Class<?> itemClass) {
        Method method = VALID_ARMOR_METHODS.get(itemClass);
        if (method != null) {
            return method;
        }

        method = findMethod(itemClass,
                new Class<?>[] {ItemStack.class, EntityEquipmentSlot.class, Entity.class},
                "isValidArmor");
        if (method != null) {
            Method existing = VALID_ARMOR_METHODS.putIfAbsent(itemClass, method);
            return existing == null ? method : existing;
        }
        return null;
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

    private static Field findField(Class<?> type, String... names) {
        try {
            return ReflectionLookup.findField(type, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean enabledAndPresent() {
        if (!GpomEarlyConfig.baublesSideSlotsCosmeticArmorEnabled()) {
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

        boolean available = loaded && classPresent(MAIN_CLASS);
        present = available;
        return available;
    }

    private static boolean classPresent(String className) {
        try {
            Class.forName(className, false, CosmeticArmorSideSlotsBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
            return false;
        }
    }

    private static void logFailure(String message, Throwable throwable) {
        if (loggedFailure) {
            return;
        }
        loggedFailure = true;
        GPOM.LOGGER.warn("[GPOM Baubles] {}", message, throwable);
    }

    private static final class CosmeticArmorSlot extends Slot {
        private final int cosmeticSlotIndex;
        private final EntityEquipmentSlot equipmentSlot;
        private final EntityPlayer player;

        private CosmeticArmorSlot(IInventory inventory, int cosmeticSlotIndex, EntityPlayer player) {
            super(inventory, cosmeticSlotIndex,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS,
                    BaublesSideSlotsCommon.HIDDEN_SLOT_POS);
            this.cosmeticSlotIndex = cosmeticSlotIndex;
            this.equipmentSlot = cosmeticSlotIndex >= 0 && cosmeticSlotIndex < EQUIPMENT_SLOTS.length
                    ? EQUIPMENT_SLOTS[cosmeticSlotIndex]
                    : null;
            this.player = player;
        }

        public int getSlotStackLimit() {
            return 1;
        }

        public int func_75219_a() {
            return 1;
        }

        public String getSlotTexture() {
            return textureName();
        }

        public String func_178171_c() {
            return textureName();
        }

        public boolean isItemValid(ItemStack stack) {
            return isValidArmorStack(stack, equipmentSlot, player);
        }

        public boolean func_75214_a(ItemStack stack) {
            return isItemValid(stack);
        }

        private String textureName() {
            if (cosmeticSlotIndex < 0 || cosmeticSlotIndex >= SLOT_TEXTURES.length) {
                return null;
            }
            return SLOT_TEXTURES[cosmeticSlotIndex];
        }
    }
}
