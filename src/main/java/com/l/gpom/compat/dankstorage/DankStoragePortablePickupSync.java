package com.l.gpom.compat.dankstorage;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DankStoragePortablePickupSync {
    private static final String PORTABLE_CONTAINER = "com.tfar.dankstorage.container.AbstractPortableDankContainer";
    private static final String PORTABLE_HANDLER = "com.tfar.dankstorage.inventory.PortableDankHandler";
    private static final String DANK_ITEM_BLOCK = "com.tfar.dankstorage.block.DankItemBlock";
    private static final String DANK_BLOCK = "com.tfar.dankstorage.block.DankBlock";
    private static volatile Class<?> portableContainerClass;
    private static volatile Class<?> portableHandlerClass;
    private static volatile Class<?> dankItemBlockClass;
    private static volatile Method onItemPickupMethod;
    private static volatile Method readItemStackMethod;
    private static volatile Method inventorySizeMethod;
    private static volatile Method inventoryStackMethod;
    private static volatile Method inventoryDirtyMethod;
    private static volatile Method stackEmptyMethod;
    private static volatile Method stackItemMethod;
    private static volatile Field handlerField;
    private static volatile Field openContainerField;
    private static volatile Field inventoryField;
    private static volatile boolean registered;

    private DankStoragePortablePickupSync() {
    }

    public static void registerIfEnabled() {
        if (registered || !GpomEarlyConfig.dankStoragePortablePickupSyncEnabled() || !Loader.isModLoaded("dankstorage")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(DankStoragePortablePickupSync.class);
        if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
            GPOM.LOGGER.info("[GPOM Dank Storage] Registered optional pickup sync event bridge");
        }
    }

    @SubscribeEvent
    public static void onEntityItemPickup(EntityItemPickupEvent event) {
        handlePickup(event);
    }

    public static boolean handlePickup(EntityItemPickupEvent event) {
        if (!GpomEarlyConfig.dankStoragePortablePickupSyncEnabled() || event == null) {
            return false;
        }

        EntityPlayer player = event.getEntityPlayer();
        if (player == null) {
            return false;
        }

        Container openContainer = openContainer(player);
        InventoryPlayer inventory = inventory(player);
        if (inventory == null) {
            return false;
        }

        boolean portableOpen = isPortableContainer(openContainer);
        int size = inventorySize(inventory);
        if (size <= 0) {
            return false;
        }

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventoryStackInSlot(inventory, slot);
            if (!isEmpty(stack) && isDankItemBlock(stack) && invokeDankPickup(event, stack)) {
                event.setCanceled(true);
                if (portableOpen) {
                    refreshPortableContainer(openContainer);
                }
                markInventoryDirty(inventory);
                return true;
            }
        }
        return false;
    }

    public static boolean handlePickupWhilePortableOpen(EntityItemPickupEvent event) {
        if (!GpomEarlyConfig.dankStoragePortablePickupSyncEnabled() || event == null) {
            return false;
        }

        EntityPlayer player = event.getEntityPlayer();
        if (player == null || !isPortableContainer(openContainer(player))) {
            return false;
        }

        return handlePickup(event);
    }

    public static void refreshPortableContainer(Object container) {
        if (!GpomEarlyConfig.dankStoragePortablePickupSyncEnabled() || !isPortableContainer(container)) {
            return;
        }

        Object handler = handler(container);
        if (!isPortableHandler(handler)) {
            return;
        }

        try {
            Method method = readItemStackMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(handler.getClass())) {
                method = handler.getClass().getMethod("readItemStack");
                method.setAccessible(true);
                readItemStackMethod = method;
            }
            method.invoke(handler);
        } catch (Throwable ignored) {
        }
    }

    private static boolean invokeDankPickup(EntityItemPickupEvent event, ItemStack stack) {
        try {
            Method method = onItemPickupMethod;
            if (method == null) {
                Class<?> owner = Class.forName(DANK_BLOCK, false, DankStoragePortablePickupSync.class.getClassLoader());
                method = owner.getMethod("onItemPickup", EntityItemPickupEvent.class, ItemStack.class);
                method.setAccessible(true);
                onItemPickupMethod = method;
            }
            Object result = method.invoke(null, event, stack);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object handler(Object container) {
        try {
            Field field = handlerField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(container.getClass())) {
                Class<?> type = Class.forName("com.tfar.dankstorage.container.AbstractAbstractDankContainer", false,
                        DankStoragePortablePickupSync.class.getClassLoader());
                field = type.getField("handler");
                field.setAccessible(true);
                handlerField = field;
            }
            return field.get(container);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int inventorySize(InventoryPlayer inventory) {
        try {
            Method method = inventorySizeMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[0], "func_70302_i_", "getSizeInventory");
                inventorySizeMethod = method;
            }
            Object result = method.invoke(inventory);
            return result instanceof Number ? ((Number) result).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static ItemStack inventoryStackInSlot(InventoryPlayer inventory, int slot) {
        try {
            Method method = inventoryStackMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[]{int.class}, "func_70301_a", "getStackInSlot");
                inventoryStackMethod = method;
            }
            Object result = method.invoke(inventory, slot);
            return result instanceof ItemStack ? (ItemStack) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void markInventoryDirty(InventoryPlayer inventory) {
        try {
            Method method = inventoryDirtyMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[0], "func_70296_d", "markDirty");
                inventoryDirtyMethod = method;
            }
            method.invoke(inventory);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isEmpty(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        try {
            Method method = stackEmptyMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(stack.getClass())) {
                method = findMethod(stack.getClass(), new Class<?>[0], "func_190926_b", "isEmpty");
                stackEmptyMethod = method;
            }
            Object result = method.invoke(stack);
            return result instanceof Boolean ? (Boolean) result : false;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object item(ItemStack stack) {
        try {
            Method method = stackItemMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(stack.getClass())) {
                method = findMethod(stack.getClass(), new Class<?>[0], "func_77973_b", "getItem");
                stackItemMethod = method;
            }
            return method.invoke(stack);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Container openContainer(EntityPlayer player) {
        Object value = fieldValue(player, true);
        return value instanceof Container ? (Container) value : null;
    }

    private static InventoryPlayer inventory(EntityPlayer player) {
        Object value = fieldValue(player, false);
        return value instanceof InventoryPlayer ? (InventoryPlayer) value : null;
    }

    private static Object fieldValue(EntityPlayer player, boolean container) {
        try {
            Field field = container ? openContainerField : inventoryField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(player.getClass())) {
                field = findField(player.getClass(), container
                        ? new String[]{"field_71070_bA", "openContainer"}
                        : new String[]{"field_71071_by", "inventory"});
                if (container) {
                    openContainerField = field;
                } else {
                    inventoryField = field;
                }
            }
            return field.get(player);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isPortableContainer(Object value) {
        return isInstance(value, PORTABLE_CONTAINER, true);
    }

    private static boolean isPortableHandler(Object value) {
        return isInstance(value, PORTABLE_HANDLER, false);
    }

    private static boolean isDankItemBlock(ItemStack stack) {
        return stack != null && isInstance(item(stack), DANK_ITEM_BLOCK, false);
    }

    private static boolean isInstance(Object value, String className, boolean container) {
        if (value == null) {
            return false;
        }
        try {
            Class<?> type = container ? portableContainerClass : classFor(className);
            if (container && type == null) {
                type = Class.forName(className, false, DankStoragePortablePickupSync.class.getClassLoader());
                portableContainerClass = type;
            }
            return type != null && type.isInstance(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> classFor(String className) throws ClassNotFoundException {
        if (PORTABLE_HANDLER.equals(className)) {
            Class<?> type = portableHandlerClass;
            if (type == null) {
                type = Class.forName(className, false, DankStoragePortablePickupSync.class.getClassLoader());
                portableHandlerClass = type;
            }
            return type;
        }
        if (DANK_ITEM_BLOCK.equals(className)) {
            Class<?> type = dankItemBlockClass;
            if (type == null) {
                type = Class.forName(className, false, DankStoragePortablePickupSync.class.getClassLoader());
                dankItemBlockClass = type;
            }
            return type;
        }
        return Class.forName(className, false, DankStoragePortablePickupSync.class.getClassLoader());
    }

    private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
        Class<?> type = owner;
        while (type != null) {
            for (String name : names) {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException ignored) {
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchFieldException(String.join("/", names));
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        Class<?> type = owner;
        while (type != null) {
            for (String name : names) {
                try {
                    Method method = type.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method;
                } catch (NoSuchMethodException ignored) {
                }
            }
            for (Class<?> iface : type.getInterfaces()) {
                Method method = findMethodInInterface(iface, parameterTypes, names);
                if (method != null) {
                    return method;
                }
            }
            type = type.getSuperclass();
        }
        throw new NoSuchMethodException(String.join("/", names));
    }

    private static Method findMethodInInterface(Class<?> iface, Class<?>[] parameterTypes, String... names) {
        for (String name : names) {
            try {
                Method method = iface.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Class<?> parent : iface.getInterfaces()) {
            Method method = findMethodInInterface(parent, parameterTypes, names);
            if (method != null) {
                return method;
            }
        }
        return null;
    }
}
