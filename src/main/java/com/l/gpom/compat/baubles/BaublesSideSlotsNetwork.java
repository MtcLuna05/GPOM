package com.l.gpom.compat.baubles;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import baubles.common.container.SlotBauble;
import com.l.gpom.Reference;
import com.l.gpom.config.GpomEarlyConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class BaublesSideSlotsNetwork {
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + "_baubles");
    private static boolean registered;

    private BaublesSideSlotsNetwork() {
    }

    public static void registerIfEnabled() {
        if (registered || !GpomEarlyConfig.baublesSideSlotsShiftRightClickEquipEnabled() || !Loader.isModLoaded("baubles")) {
            return;
        }
        registered = true;
        NETWORK.registerMessage(QuickEquipHandler.class, QuickEquipMessage.class, 0, Side.SERVER);
    }

    public static void sendQuickEquip(int windowId, int slotNumber) {
        if (!registered) {
            return;
        }
        NETWORK.sendToServer(new QuickEquipMessage(windowId, slotNumber));
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
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> quickEquip(player, message.windowId, message.slotNumber));
            return null;
        }
    }

    private static void quickEquip(EntityPlayerMP player, int windowId, int slotNumber) {
        if (!GpomEarlyConfig.baublesSideSlotsShiftRightClickEquipEnabled()) {
            return;
        }

        Container container = player.openContainer;
        java.util.List<Slot> slots = BaublesSideSlotsCommon.slots(container);
        if (container == null
                || BaublesSideSlotsCommon.windowId(container) != windowId
                || slotNumber < 0
                || slotNumber >= slots.size()) {
            return;
        }

        Slot source = slots.get(slotNumber);
        IInventory sourceInventory = BaublesSideSlotsCommon.slotInventory(source);
        if (!(sourceInventory instanceof InventoryPlayer)
                || !BaublesSideSlotsCommon.canTakeSlotStack(source, player)
                || !BaublesSideSlotsCommon.slotHasStack(source)) {
            return;
        }

        ItemStack sourceStack = BaublesSideSlotsCommon.slotStack(source);
        if (BaublesSideSlotsCommon.isEmptyStack(sourceStack)) {
            return;
        }

        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null) {
            return;
        }

        for (int index = 0; index < handler.getSlots(); index++) {
            if (!BaublesSideSlotsCommon.isEmptyStack(handler.getStackInSlot(index))) {
                continue;
            }

            SlotBauble target = new SlotBauble(player, handler, index, 0, 0);
            if (!BaublesSideSlotsCommon.isSlotItemValid(target, sourceStack)) {
                continue;
            }

            ItemStack moved = BaublesSideSlotsCommon.copyStack(sourceStack);
            BaublesSideSlotsCommon.setStackCount(moved, 1);
            BaublesSideSlotsCommon.shrinkStack(sourceStack, 1);
            if (BaublesSideSlotsCommon.isEmptyStack(sourceStack)) {
                BaublesSideSlotsCommon.putSlotStack(source, BaublesSideSlotsCommon.emptyStack());
            } else {
                BaublesSideSlotsCommon.slotChanged(source);
            }
            BaublesSideSlotsCommon.putSlotStack(target, moved);
            BaublesSideSlotsCommon.slotChanged(target);
            container.detectAndSendChanges();
            return;
        }
    }
}
