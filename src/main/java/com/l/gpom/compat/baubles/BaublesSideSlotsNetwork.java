package com.l.gpom.compat.baubles;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import baubles.common.container.SlotBauble;
import com.l.gpom.Reference;
import com.l.gpom.config.GpomEarlyConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class BaublesSideSlotsNetwork {
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + "_baubles");
    private static volatile Field serverHandlerPlayerField;
    private static volatile Field playerOpenContainerField;
    private static volatile Field playerInventoryField;
    private static volatile Field playerConnectionField;
    private static volatile Method addScheduledTaskMethod;
    private static volatile Method detectAndSendChangesMethod;
    private static volatile Method slotClickMethod;
    private static volatile Method sendContainerToPlayerMethod;
    private static volatile Method updateHeldItemMethod;
    private static volatile Method inventoryCarriedStackMethod;
    private static volatile Method sendPacketMethod;
    private static boolean registered;

    private BaublesSideSlotsNetwork() {
    }

    public static void registerIfEnabled() {
        if (registered || !GpomEarlyConfig.baublesSideSlotsEnabled() || !Loader.isModLoaded("baubles")) {
            return;
        }
        registered = true;
        NETWORK.registerMessage(QuickEquipHandler.class, QuickEquipMessage.class, 0, Side.SERVER);
        NETWORK.registerMessage(CosmeticSlotClickHandler.class, CosmeticSlotClickMessage.class, 1, Side.SERVER);
    }

    public static void sendQuickEquip(int windowId, int slotNumber) {
        if (!registered) {
            return;
        }
        NETWORK.sendToServer(new QuickEquipMessage(windowId, slotNumber));
    }

    public static void sendCosmeticSlotClick(int windowId, int slotNumber, int mouseButton, ClickType clickType) {
        if (!registered || clickType == null) {
            return;
        }
        NETWORK.sendToServer(new CosmeticSlotClickMessage(windowId, slotNumber, mouseButton, clickType.ordinal()));
    }

    public static final class QuickEquipMessage implements IMessage {
        private int windowId;
        private int slotNumber;

        public QuickEquipMessage() {
        }

        private QuickEquipMessage(int windowId, int slotNumber) {
            this.windowId = windowId;
            this.slotNumber = slotNumber;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            this.windowId = buffer.readInt();
            this.slotNumber = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeInt(slotNumber);
        }
    }

    public static final class QuickEquipHandler implements IMessageHandler<QuickEquipMessage, IMessage> {
        @Override
        public IMessage onMessage(QuickEquipMessage message, MessageContext context) {
            EntityPlayerMP player = serverPlayer(context);
            if (player != null) {
                scheduleOnServer(() -> quickEquip(player, message.windowId, message.slotNumber));
            }
            return null;
        }
    }

    public static final class CosmeticSlotClickMessage implements IMessage {
        private int windowId;
        private int slotNumber;
        private int mouseButton;
        private int clickType;

        public CosmeticSlotClickMessage() {
        }

        private CosmeticSlotClickMessage(int windowId, int slotNumber, int mouseButton, int clickType) {
            this.windowId = windowId;
            this.slotNumber = slotNumber;
            this.mouseButton = mouseButton;
            this.clickType = clickType;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            this.windowId = buffer.readInt();
            this.slotNumber = buffer.readInt();
            this.mouseButton = buffer.readInt();
            this.clickType = buffer.readInt();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeInt(slotNumber);
            buffer.writeInt(mouseButton);
            buffer.writeInt(clickType);
        }
    }

    public static final class CosmeticSlotClickHandler implements IMessageHandler<CosmeticSlotClickMessage, IMessage> {
        @Override
        public IMessage onMessage(CosmeticSlotClickMessage message, MessageContext context) {
            EntityPlayerMP player = serverPlayer(context);
            if (player != null) {
                scheduleOnServer(() -> clickCosmeticSlot(player,
                        message.windowId,
                        message.slotNumber,
                        message.mouseButton,
                        message.clickType));
            }
            return null;
        }
    }

    private static void quickEquip(EntityPlayerMP player, int windowId, int slotNumber) {
        if (!GpomEarlyConfig.baublesSideSlotsShiftRightClickEquipEnabled()) {
            return;
        }

        Container container = openContainer(player);
        if (container == null
                || BaublesSideSlotsCommon.windowId(container) != windowId
                || slotNumber < 0) {
            return;
        }

        java.util.List<Slot> slots = BaublesSideSlotsCommon.slots(container);
        if (slotNumber >= slots.size()) {
            return;
        }

        Slot source = slots.get(slotNumber);
        if (!BaublesSideSlotsCommon.isPlayerMainInventorySlot(source)
                || !BaublesSideSlotsCommon.canTakeSlotStack(source, player)
                || !BaublesSideSlotsCommon.slotHasStack(source)) {
            return;
        }

        ItemStack sourceStack = BaublesSideSlotsCommon.slotStack(source);
        if (BaublesSideSlotsCommon.isEmptyStack(sourceStack)) {
            return;
        }

        if (quickEquipVanillaArmor(container, source, sourceStack)) {
            syncAfterMutation(player, container);
            return;
        }

        boolean armorStack = BaublesSideSlotsCommon.isValidForVanillaArmorSlot(container, sourceStack);
        if (armorStack && CosmeticArmorSideSlotsBridge.quickEquip(player, source, sourceStack)) {
            syncAfterMutation(player, container);
            return;
        }

        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler != null && quickEquipBauble(player, source, sourceStack, handler)) {
            syncAfterMutation(player, container);
            return;
        }

        if (AetherSideSlotsBridge.quickEquip(player, source, sourceStack)) {
            syncAfterMutation(player, container);
            return;
        }

        if (CosmeticArmorSideSlotsBridge.quickEquip(player, source, sourceStack)) {
            syncAfterMutation(player, container);
        }
    }

    private static boolean quickEquipVanillaArmor(Container container, Slot source, ItemStack sourceStack) {
        Slot target = BaublesSideSlotsCommon.findEmptyVanillaArmorTarget(container, sourceStack);
        return target != null && BaublesSideSlotsCommon.moveOneItemToSlot(source, target, sourceStack);
    }

    private static void clickCosmeticSlot(EntityPlayerMP player, int windowId, int slotNumber, int mouseButton, int clickTypeOrdinal) {
        if (!GpomEarlyConfig.baublesSideSlotsEnabled() || !GpomEarlyConfig.baublesSideSlotsCosmeticArmorEnabled()) {
            return;
        }

        Container container = openContainer(player);
        if (container == null
                || BaublesSideSlotsCommon.windowId(container) != windowId
                || slotNumber < 0
                || mouseButton != 0) {
            return;
        }

        java.util.List<Slot> slots = BaublesSideSlotsCommon.slots(container);
        if (slotNumber >= slots.size()) {
            return;
        }

        Slot slot = slots.get(slotNumber);
        if (!CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)
                || !BaublesSideSlotsCommon.isSlotEnabled(slot)) {
            return;
        }

        ClickType clickType = clickType(clickTypeOrdinal);
        if (clickType == null || (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE)) {
            return;
        }

        if (clickType == ClickType.QUICK_MOVE) {
            if (moveSideRailSlotToPlayerInventory(container, slot, player)) {
                syncAfterMutation(player, container);
                CosmeticArmorSideSlotsBridge.syncSlot(player, CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot));
            }
            return;
        }

        slotClick(container, slotNumber, mouseButton, clickType, player);
        syncAfterMutation(player, container);
        CosmeticArmorSideSlotsBridge.syncSlot(player, CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot));
    }

    private static boolean moveSideRailSlotToPlayerInventory(Container container, Slot source, EntityPlayerMP player) {
        if (container == null
                || source == null
                || player == null
                || !BaublesSideSlotsCommon.canTakeSlotStack(source, player)) {
            return false;
        }

        ItemStack sourceStack = BaublesSideSlotsCommon.slotStack(source);
        if (BaublesSideSlotsCommon.isEmptyStack(sourceStack)) {
            return false;
        }

        Slot target = findEmptyPlayerInventoryTarget(container, sourceStack, 9, 36);
        if (target == null) {
            target = findEmptyPlayerInventoryTarget(container, sourceStack, 0, 9);
        }
        return target != null && BaublesSideSlotsCommon.moveOneItemToSlot(source, target, sourceStack);
    }

    private static Slot findEmptyPlayerInventoryTarget(Container container, ItemStack stack, int startIndex, int endIndex) {
        if (container == null || BaublesSideSlotsCommon.isEmptyStack(stack)) {
            return null;
        }

        for (Slot slot : BaublesSideSlotsCommon.slots(container)) {
            int slotIndex = BaublesSideSlotsCommon.slotIndex(slot);
            if (slotIndex < startIndex
                    || slotIndex >= endIndex
                    || !BaublesSideSlotsCommon.isPlayerMainInventorySlot(slot)
                    || BaublesSideSlotsCommon.slotHasStack(slot)
                    || !BaublesSideSlotsCommon.isSlotEnabled(slot)
                    || !BaublesSideSlotsCommon.isSlotItemValid(slot, stack)) {
                continue;
            }
            return slot;
        }
        return null;
    }

    private static ClickType clickType(int ordinal) {
        ClickType[] values = ClickType.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : null;
    }

    private static EntityPlayerMP serverPlayer(MessageContext context) {
        if (context == null) {
            return null;
        }

        try {
            NetHandlerPlayServer handler = context.getServerHandler();
            if (handler == null) {
                return null;
            }

            Field field = serverHandlerPlayerField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(handler.getClass())) {
                field = findField(handler.getClass(), "field_147369_b", "player");
                serverHandlerPlayerField = field;
            }
            Object value = field == null ? null : field.get(handler);
            return value instanceof EntityPlayerMP ? (EntityPlayerMP) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Container openContainer(EntityPlayerMP player) {
        if (player == null) {
            return null;
        }

        try {
            Field field = playerOpenContainerField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(player.getClass())) {
                field = findField(player.getClass(), "field_71070_bA", "openContainer");
                playerOpenContainerField = field;
            }
            Object value = field == null ? null : field.get(player);
            return value instanceof Container ? (Container) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static InventoryPlayer inventory(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        try {
            Field field = playerInventoryField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(player.getClass())) {
                field = findField(player.getClass(), "field_71071_by", "inventory");
                playerInventoryField = field;
            }
            Object value = field == null ? null : field.get(player);
            return value instanceof InventoryPlayer ? (InventoryPlayer) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void scheduleOnServer(Runnable task) {
        Object server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && schedule(server, task)) {
            return;
        }

        task.run();
    }

    private static boolean schedule(Object target, Runnable task) {
        if (target == null || task == null) {
            return false;
        }

        try {
            Method method = addScheduledTaskMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(target.getClass())) {
                method = findMethod(target.getClass(), new Class<?>[] {Runnable.class},
                        "func_152344_a",
                        "addScheduledTask");
                addScheduledTaskMethod = method;
            }
            if (method == null) {
                return false;
            }
            method.invoke(target, task);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void detectAndSendChanges(Container container) {
        if (container == null) {
            return;
        }

        try {
            Method method = detectAndSendChangesMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(container.getClass())) {
                method = findMethod(container.getClass(), new Class<?>[0],
                        "func_75142_b",
                        "detectAndSendChanges");
                detectAndSendChangesMethod = method;
            }
            if (method != null) {
                method.invoke(container);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void slotClick(Container container,
                                  int slotNumber,
                                  int mouseButton,
                                  ClickType clickType,
                                  EntityPlayerMP player) {
        if (container == null || clickType == null || player == null) {
            return;
        }

        try {
            Method method = slotClickMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(container.getClass())) {
                method = findMethod(container.getClass(), new Class<?>[] {
                                int.class,
                                int.class,
                                ClickType.class,
                                EntityPlayer.class
                        },
                        "func_184996_a",
                        "slotClick");
                slotClickMethod = method;
            }
            if (method != null) {
                method.invoke(container, slotNumber, mouseButton, clickType, player);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void sendContainerToPlayer(EntityPlayerMP player, Container container) {
        if (player == null || container == null) {
            return;
        }

        try {
            Method method = sendContainerToPlayerMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(player.getClass())) {
                method = findMethod(player.getClass(), new Class<?>[] {Container.class},
                        "func_71110_a",
                        "sendContainerToPlayer");
                sendContainerToPlayerMethod = method;
            }
            if (method != null) {
                method.invoke(player, container);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void updateHeldItem(EntityPlayerMP player) {
        if (player == null) {
            return;
        }

        try {
            Method method = updateHeldItemMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(player.getClass())) {
                method = findMethod(player.getClass(), new Class<?>[0],
                        "func_71113_k",
                        "updateHeldItem");
                updateHeldItemMethod = method;
            }
            if (method != null) {
                method.invoke(player);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void syncAfterMutation(EntityPlayerMP player, Container container) {
        detectAndSendChanges(container);
        updateHeldItem(player);
        sendContainerToPlayer(player, container);
        syncCarriedStack(player);
    }

    private static void syncCarriedStack(EntityPlayerMP player) {
        ItemStack carried = carriedStack(player);
        sendPacket(player, new SPacketSetSlot(-1, -1, BaublesSideSlotsCommon.copyStack(carried)));
    }

    private static ItemStack carriedStack(EntityPlayerMP player) {
        InventoryPlayer inventory = inventory(player);
        if (inventory == null) {
            return BaublesSideSlotsCommon.emptyStack();
        }

        try {
            Method method = inventoryCarriedStackMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[0],
                        "func_70445_o",
                        "getItemStack");
                inventoryCarriedStackMethod = method;
            }
            Object value = method == null ? null : method.invoke(inventory);
            return value instanceof ItemStack ? (ItemStack) value : BaublesSideSlotsCommon.emptyStack();
        } catch (Throwable ignored) {
            return BaublesSideSlotsCommon.emptyStack();
        }
    }

    private static void sendPacket(EntityPlayerMP player, Packet<?> packet) {
        if (player == null || packet == null) {
            return;
        }

        try {
            Field field = playerConnectionField;
            if (field == null || !field.getDeclaringClass().isAssignableFrom(player.getClass())) {
                field = findField(player.getClass(), "field_71135_a", "connection");
                playerConnectionField = field;
            }
            Object connection = field == null ? null : field.get(player);
            if (connection == null) {
                return;
            }

            Method method = sendPacketMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(connection.getClass())) {
                method = findMethod(connection.getClass(), new Class<?>[] {Packet.class},
                        "func_147359_a",
                        "sendPacket");
                sendPacketMethod = method;
            }
            if (method != null) {
                method.invoke(connection, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean quickEquipBauble(EntityPlayerMP player,
                                            Slot source,
                                            ItemStack sourceStack,
                                            IBaublesItemHandler handler) {
        for (int index = 0; index < handler.getSlots(); index++) {
            if (!BaublesSideSlotsCommon.isEmptyStack(handler.getStackInSlot(index))) {
                continue;
            }

            SlotBauble target = new SlotBauble(player, handler, index, 0, 0);
            if (!BaublesSideSlotsCommon.isSlotItemValid(target, sourceStack)) {
                continue;
            }

            return BaublesSideSlotsCommon.moveOneItemToSlot(source, target, sourceStack);
        }
        return false;
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
}
