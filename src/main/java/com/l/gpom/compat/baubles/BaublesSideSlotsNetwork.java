package com.l.gpom.compat.baubles;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import baubles.common.container.SlotBauble;
import com.l.gpom.GPOM;
import com.l.gpom.Reference;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.ReflectionLookup;
import com.l.gpom.util.GpomRemoteEnvironment;
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
    public static final int RAIL_TYPE_UNKNOWN = 0;
    public static final int RAIL_TYPE_BAUBLE = 1;
    public static final int RAIL_TYPE_AETHER = 2;
    public static final int RAIL_TYPE_COSMETIC_ARMOR = 3;
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
    private static volatile Method inventorySetCarriedStackMethod;
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
        if (!registered || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        NETWORK.sendToServer(new QuickEquipMessage(windowId, slotNumber));
    }

    public static void sendCosmeticSlotClick(int windowId, int slotNumber, int mouseButton, ClickType clickType) {
        sendSideRailSlotClick(windowId, slotNumber, RAIL_TYPE_UNKNOWN, -1, mouseButton, clickType, false);
    }

    public static void sendSideRailSlotClick(int windowId, int slotNumber, int mouseButton, ClickType clickType) {
        sendSideRailSlotClick(windowId, slotNumber, RAIL_TYPE_UNKNOWN, -1, mouseButton, clickType, false);
    }

    public static void sendSideRailSlotClick(int windowId, int slotNumber, int mouseButton, ClickType clickType, boolean emptyCursorPickup) {
        sendSideRailSlotClick(windowId, slotNumber, RAIL_TYPE_UNKNOWN, -1, mouseButton, clickType, emptyCursorPickup);
    }

    public static void sendSideRailSlotClick(int windowId,
                                             int slotNumber,
                                             int railType,
                                             int railIndex,
                                             int mouseButton,
                                             ClickType clickType,
                                             boolean emptyCursorPickup) {
        if (!registered || clickType == null || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return;
        }
        NETWORK.sendToServer(new CosmeticSlotClickMessage(windowId,
                slotNumber,
                railType,
                railIndex,
                mouseButton,
                clickType.ordinal(),
                emptyCursorPickup));
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
        private int railType;
        private int railIndex;
        private int mouseButton;
        private int clickType;
        private boolean emptyCursorPickup;

        public CosmeticSlotClickMessage() {
        }

        private CosmeticSlotClickMessage(int windowId,
                                         int slotNumber,
                                         int railType,
                                         int railIndex,
                                         int mouseButton,
                                         int clickType,
                                         boolean emptyCursorPickup) {
            this.windowId = windowId;
            this.slotNumber = slotNumber;
            this.railType = railType;
            this.railIndex = railIndex;
            this.mouseButton = mouseButton;
            this.clickType = clickType;
            this.emptyCursorPickup = emptyCursorPickup;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            this.windowId = buffer.readInt();
            this.slotNumber = buffer.readInt();
            this.railType = buffer.readInt();
            this.railIndex = buffer.readInt();
            this.mouseButton = buffer.readInt();
            this.clickType = buffer.readInt();
            this.emptyCursorPickup = buffer.readBoolean();
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeInt(slotNumber);
            buffer.writeInt(railType);
            buffer.writeInt(railIndex);
            buffer.writeInt(mouseButton);
            buffer.writeInt(clickType);
            buffer.writeBoolean(emptyCursorPickup);
        }
    }

    public static final class CosmeticSlotClickHandler implements IMessageHandler<CosmeticSlotClickMessage, IMessage> {
        @Override
        public IMessage onMessage(CosmeticSlotClickMessage message, MessageContext context) {
            EntityPlayerMP player = serverPlayer(context);
            if (player != null) {
                scheduleOnServer(() -> clickSideRailSlot(player,
                        message.windowId,
                        message.slotNumber,
                        message.railType,
                        message.railIndex,
                        message.mouseButton,
                        message.clickType,
                        message.emptyCursorPickup));
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

    private static void clickSideRailSlot(EntityPlayerMP player,
                                          int windowId,
                                          int slotNumber,
                                          int railType,
                                          int railIndex,
                                          int mouseButton,
                                          int clickTypeOrdinal,
                                          boolean emptyCursorPickup) {
        probe("server recv player={} window={} slot={} railType={} railIndex={} mouse={} clickOrdinal={} emptyCursorPickup={}",
                player, windowId, slotNumber, railType, railIndex, mouseButton, clickTypeOrdinal, emptyCursorPickup);
        if (!GpomEarlyConfig.baublesSideSlotsEnabled()) {
            probe("server reject: side slots disabled");
            return;
        }

        Container container = openContainer(player);
        if (container == null
                || BaublesSideSlotsCommon.windowId(container) != windowId
                || mouseButton != 0) {
            probe("server reject: container/window/mouse mismatch container={} actualWindow={} expectedWindow={} mouse={}",
                    container == null ? "null" : container.getClass().getName(),
                    container == null ? -1 : BaublesSideSlotsCommon.windowId(container),
                    windowId,
                    mouseButton);
            return;
        }

        java.util.List<Slot> slots = BaublesSideSlotsCommon.slots(container);
        Slot slot = findSideRailSlot(slots, slotNumber, railType, railIndex);
        if (!BaublesSideSlotsCommon.isSideRailSlot(slot) || !BaublesSideSlotsCommon.isSlotEnabled(slot)) {
            probe("server reject: unresolved or disabled side slot resolved={} requestedSlot={} requestedRailType={} requestedRailIndex={} sideSlotCount={} slotCount={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    slotNumber,
                    railType,
                    railIndex,
                    BaublesSideSlotsCommon.sideRailSlots(container).size(),
                    slots == null ? -1 : slots.size());
            return;
        }

        ClickType clickType = clickType(clickTypeOrdinal);
        if (clickType == null || (clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE)) {
            probe("server reject: unsupported click type ordinal={} resolved={}", clickTypeOrdinal, clickType);
            return;
        }

        probe("server resolved: container={} slot={} carried={}",
                container.getClass().getName(),
                BaublesSideSlotsCommon.slotDebug(slot),
                BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
        if (clickType == ClickType.QUICK_MOVE) {
            if (moveSideRailSlotToPlayerInventory(container, slot, player)) {
                syncAfterMutation(player, container);
                syncCosmeticSlotIfNeeded(player, slot);
                probe("server quick_move success: slot={} carried={}",
                        BaublesSideSlotsCommon.slotDebug(slot),
                        BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
            } else {
                probe("server quick_move failed: slot={} carried={}",
                        BaublesSideSlotsCommon.slotDebug(slot),
                        BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
            }
            return;
        }

        if (emptyCursorPickup && BaublesSideSlotsCommon.slotHasStack(slot) && pickupSideRailSlot(slot, player)) {
            syncAfterMutation(player, container);
            syncCosmeticSlotIfNeeded(player, slot);
            probe("server empty-cursor pickup success: slot={} carried={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
            return;
        }

        if (clickPickupSideRailSlot(slot, player)) {
            syncAfterMutation(player, container);
            syncCosmeticSlotIfNeeded(player, slot);
            probe("server pickup/place/swap success: slot={} carried={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
            return;
        }
        syncAfterMutation(player, container);
        syncCosmeticSlotIfNeeded(player, slot);
        probe("server pickup/place/swap failed and resynced: slot={} carried={}",
                BaublesSideSlotsCommon.slotDebug(slot),
                BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
    }

    private static Slot findSideRailSlot(java.util.List<Slot> slots, int slotNumber, int railType, int railIndex) {
        if (slots == null || slots.isEmpty()) {
            return null;
        }

        if (railType != RAIL_TYPE_UNKNOWN && railIndex >= 0) {
            for (Slot candidate : slots) {
                if (candidate != null
                        && BaublesSideSlotsCommon.isSideRailSlot(candidate)
                        && railType(candidate) == railType
                        && railIndex(candidate) == railIndex) {
                    return candidate;
                }
            }
        }

        if (slotNumber >= 0 && slotNumber < slots.size()) {
            Slot candidate = slots.get(slotNumber);
            if (BaublesSideSlotsCommon.isSideRailSlot(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static int railType(Slot slot) {
        if (slot instanceof SlotBauble) {
            return RAIL_TYPE_BAUBLE;
        }
        if (AetherSideSlotsBridge.isAccessorySlot(slot)) {
            return RAIL_TYPE_AETHER;
        }
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            return RAIL_TYPE_COSMETIC_ARMOR;
        }
        return RAIL_TYPE_UNKNOWN;
    }

    private static int railIndex(Slot slot) {
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

    private static void syncCosmeticSlotIfNeeded(EntityPlayerMP player, Slot slot) {
        if (CosmeticArmorSideSlotsBridge.isCosmeticArmorSlot(slot)) {
            CosmeticArmorSideSlotsBridge.syncSlot(player, CosmeticArmorSideSlotsBridge.cosmeticArmorSlotIndex(slot));
        }
    }

    private static boolean pickupSideRailSlot(Slot slot, EntityPlayerMP player) {
        if (slot == null
                || player == null
                || !BaublesSideSlotsCommon.canTakeSlotStack(slot, player)
                || !BaublesSideSlotsCommon.slotHasStack(slot)) {
            probe("pickup reject: slot/player/canTake/hasStack slot={} player={} canTake={} hasStack={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    player,
                    slot != null && player != null && BaublesSideSlotsCommon.canTakeSlotStack(slot, player),
                    slot != null && BaublesSideSlotsCommon.slotHasStack(slot));
            return false;
        }

        ItemStack stack = BaublesSideSlotsCommon.slotStack(slot);
        if (BaublesSideSlotsCommon.isEmptyStack(stack)) {
            probe("pickup reject: slot reported stack empty slot={}", BaublesSideSlotsCommon.slotDebug(slot));
            return false;
        }

        probe("pickup before decr: slot={} count={}", BaublesSideSlotsCommon.slotDebug(slot), BaublesSideSlotsCommon.stackCount(stack));
        ItemStack extracted = BaublesSideSlotsCommon.decrSlotStack(slot, BaublesSideSlotsCommon.stackCount(stack));
        if (BaublesSideSlotsCommon.isEmptyStack(extracted)) {
            probe("pickup reject: decrStackSize returned empty slot={} original={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(stack));
            return false;
        }
        BaublesSideSlotsCommon.onTakeSlotStack(slot, player, extracted);
        setCarriedStack(player, BaublesSideSlotsCommon.copyStack(extracted));
        probe("pickup after decr/onTake: extracted={} slot={} carried={}",
                BaublesSideSlotsCommon.stackDebug(extracted),
                BaublesSideSlotsCommon.slotDebug(slot),
                BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
        return true;
    }

    private static boolean clickPickupSideRailSlot(Slot slot, EntityPlayerMP player) {
        if (slot == null || player == null) {
            return false;
        }

        ItemStack carried = carriedStack(player);
        ItemStack slotStack = BaublesSideSlotsCommon.slotStack(slot);
        boolean carriedEmpty = BaublesSideSlotsCommon.isEmptyStack(carried);
        boolean slotEmpty = BaublesSideSlotsCommon.isEmptyStack(slotStack);

        if (carriedEmpty && slotEmpty) {
            probe("pickup click reject: carried and slot empty slot={} carried={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(carried));
            return false;
        }
        if (carriedEmpty) {
            return pickupSideRailSlot(slot, player);
        }
        if (!BaublesSideSlotsCommon.isSlotItemValid(slot, carried)) {
            probe("pickup click reject: carried stack invalid for slot slot={} carried={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(carried));
            return false;
        }
        if (slotEmpty) {
            probe("pickup click placing carried into empty slot slot={} carried={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(carried));
            BaublesSideSlotsCommon.putSlotStack(slot, BaublesSideSlotsCommon.copyStack(carried));
            setCarriedStack(player, BaublesSideSlotsCommon.emptyStack());
            BaublesSideSlotsCommon.slotChanged(slot);
            return true;
        }
        if (!BaublesSideSlotsCommon.canTakeSlotStack(slot, player)) {
            probe("pickup click reject: cannot take occupied slot for swap slot={} carried={}",
                    BaublesSideSlotsCommon.slotDebug(slot),
                    BaublesSideSlotsCommon.stackDebug(carried));
            return false;
        }

        probe("pickup click swapping slot={} carried={}",
                BaublesSideSlotsCommon.slotDebug(slot),
                BaublesSideSlotsCommon.stackDebug(carried));
        BaublesSideSlotsCommon.putSlotStack(slot, BaublesSideSlotsCommon.copyStack(carried));
        setCarriedStack(player, BaublesSideSlotsCommon.copyStack(slotStack));
        BaublesSideSlotsCommon.slotChanged(slot);
        return true;
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
        probe("sync before: container={} carried={}",
                container == null ? "null" : container.getClass().getName(),
                BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
        detectAndSendChanges(container);
        updateHeldItem(player);
        sendContainerToPlayer(player, container);
        syncCarriedStack(player);
        probe("sync after: container={} carried={}",
                container == null ? "null" : container.getClass().getName(),
                BaublesSideSlotsCommon.stackDebug(carriedStack(player)));
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

    private static void setCarriedStack(EntityPlayerMP player, ItemStack stack) {
        InventoryPlayer inventory = inventory(player);
        if (inventory == null) {
            probe("set carried failed: inventory null player={} stack={}", player, BaublesSideSlotsCommon.stackDebug(stack));
            return;
        }

        try {
            Method method = inventorySetCarriedStackMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(inventory.getClass())) {
                method = findMethod(inventory.getClass(), new Class<?>[] {ItemStack.class},
                        "func_70437_b",
                        "setItemStack");
                inventorySetCarriedStackMethod = method;
            }
            if (method != null) {
                method.invoke(inventory, stack == null ? BaublesSideSlotsCommon.emptyStack() : stack);
            } else {
                probe("set carried failed: setItemStack method missing inventory={} stack={}",
                        inventory.getClass().getName(),
                        BaublesSideSlotsCommon.stackDebug(stack));
            }
        } catch (Throwable throwable) {
            probe("set carried failed: {} stack={}", throwable.toString(), BaublesSideSlotsCommon.stackDebug(stack));
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

    private static void probe(String message, Object... args) {
        if (GpomEarlyConfig.baublesSideSlotsDeepProbeEnabled()) {
            GPOM.LOGGER.info("[GPOM Baubles Probe] " + message, args);
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        try {
            return ReflectionLookup.findField(owner, names);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String... names) {
        try {
            return ReflectionLookup.findMethod(owner, names, parameterTypes);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
