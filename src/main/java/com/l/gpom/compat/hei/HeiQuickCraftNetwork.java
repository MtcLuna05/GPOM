package com.l.gpom.compat.hei;

import com.l.gpom.Reference;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomRemoteEnvironment;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HeiQuickCraftNetwork {
    private static final String FUSION_CONTAINER = "com.brandon3055.draconicevolution.inventory.ContainerFusionCraftingCore";
    private static final SimpleNetworkWrapper NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(Reference.MOD_ID + "_heiqc");
    private static boolean registered;

    private HeiQuickCraftNetwork() {
    }

    public static void registerIfNeeded() {
        if (registered
                || !GpomEarlyConfig.heiDraconicFusionTransferEnabled()
                || !Loader.isModLoaded("draconicevolution")) {
            return;
        }
        registered = true;
        NETWORK.registerMessage(DraconicFusionTransferHandlerServer.class, DraconicFusionTransferMessage.class, 0, Side.SERVER);
    }

    static boolean sendDraconicFusionTransfer(int windowId, List<List<ItemStack>> inputs) {
        if (!registered || inputs == null || inputs.isEmpty() || !GpomRemoteEnvironment.serverFeaturesAllowed()) {
            return false;
        }
        NETWORK.sendToServer(new DraconicFusionTransferMessage(windowId, inputs));
        return true;
    }

    public static final class DraconicFusionTransferMessage implements IMessage {
        private int windowId;
        private List<List<ItemStack>> inputs = Collections.emptyList();

        public DraconicFusionTransferMessage() {
        }

        private DraconicFusionTransferMessage(int windowId, List<List<ItemStack>> inputs) {
            this.windowId = windowId;
            this.inputs = inputs;
        }

        @Override
        public void fromBytes(ByteBuf buffer) {
            this.windowId = buffer.readInt();
            int inputCount = Math.min(64, Math.max(0, buffer.readShort()));
            List<List<ItemStack>> readInputs = new ArrayList<>(inputCount);
            for (int input = 0; input < inputCount; input++) {
                int optionCount = Math.min(512, Math.max(0, buffer.readShort()));
                List<ItemStack> options = new ArrayList<>(optionCount);
                for (int option = 0; option < optionCount; option++) {
                    ItemStack stack = ByteBufUtils.readItemStack(buffer);
                    if (!MinecraftMappingCompat.itemStackIsEmpty(stack)) {
                        MinecraftMappingCompat.itemStackSetCount(stack, Math.max(1, MinecraftMappingCompat.itemStackCount(stack)));
                        options.add(stack);
                    }
                }
                readInputs.add(options);
            }
            this.inputs = readInputs;
        }

        @Override
        public void toBytes(ByteBuf buffer) {
            buffer.writeInt(windowId);
            buffer.writeShort(inputs.size());
            for (List<ItemStack> options : inputs) {
                buffer.writeShort(options == null ? 0 : options.size());
                if (options == null) {
                    continue;
                }
                for (ItemStack stack : options) {
                    ByteBufUtils.writeItemStack(buffer, MinecraftMappingCompat.itemStackIsEmpty(stack) ? MinecraftMappingCompat.emptyStack() : stack);
                }
            }
        }
    }

    public static final class DraconicFusionTransferHandlerServer
            implements IMessageHandler<DraconicFusionTransferMessage, IMessage> {
        @Override
        public IMessage onMessage(DraconicFusionTransferMessage message, MessageContext context) {
            EntityPlayerMP player = HeiReflection.serverPlayer(context);
            if (player != null) {
                scheduleOnServer(() -> transfer(player, message.windowId, message.inputs));
            }
            return null;
        }
    }

    private static void transfer(EntityPlayerMP player, int windowId, List<List<ItemStack>> inputs) {
        if (player == null || inputs == null || inputs.size() < 2) {
            return;
        }

        Container container = HeiReflection.openContainer(player);
        if (container == null
                || HeiReflection.containerWindowId(container) != windowId
                || !FUSION_CONTAINER.equals(container.getClass().getName())) {
            return;
        }

        Object core = fieldValue(container, "tile");
        if (core == null || booleanMethod(core, "craftingInProgress")) {
            return;
        }

        invoke(core, "updateInjectors");
        List<Object> injectors = injectorList(core);
        if (injectors.isEmpty()) {
            return;
        }

        TransferPlan plan = buildPlan(player, container, core, injectors, inputs);
        if (plan == null || plan.moves.isEmpty()) {
            return;
        }

        for (Move move : plan.moves) {
            ItemStack placed = MinecraftMappingCompat.itemStackCopy(move.stack);
            MinecraftMappingCompat.itemStackSetCount(placed, move.count);
            consume(move.source, move.count);
            if (move.coreSlot) {
                invoke(core, "setStackInCore", new Class<?>[] {int.class, ItemStack.class}, 0, placed);
            } else {
                invoke(move.injector, "setStackInPedestal", new Class<?>[] {ItemStack.class}, placed);
                markDirty(move.injector);
            }
        }

        markDirty(core);
        invoke(core, "updateInjectors");
        HeiReflection.detectAndSendChanges(container);
        HeiReflection.updateHeldItem(player);
        HeiReflection.sendContainerToPlayer(player, container);
    }

    private static TransferPlan buildPlan(EntityPlayerMP player,
                                          Container container,
                                          Object core,
                                          List<Object> injectors,
                                          List<List<ItemStack>> inputs) {
        List<Slot> sources = playerInventorySlots(player, container);
        Map<Slot, Integer> plannedUse = new IdentityHashMap<>();
        TransferPlan plan = new TransferPlan();

        List<ItemStack> catalystOptions = inputs.get(0);
        if (catalystOptions == null || catalystOptions.isEmpty()) {
            return null;
        }

        ItemStack coreCatalyst = stackMethod(core, "getStackInCore", new Class<?>[] {int.class}, 0);
        if (!matchesAnyWithEnough(coreCatalyst, catalystOptions)) {
            if (!MinecraftMappingCompat.itemStackIsEmpty(coreCatalyst)) {
                return null;
            }
            SourceChoice source = findSource(sources, plannedUse, catalystOptions);
            if (source == null) {
                return null;
            }
            plannedUse.put(source.slot, plannedUse.getOrDefault(source.slot, 0) + source.count);
            plan.moves.add(Move.toCore(source.slot, source.stack, source.count));
        }

        Set<Object> usedInjectors = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int input = 1; input < inputs.size(); input++) {
            List<ItemStack> options = inputs.get(input);
            if (options == null || options.isEmpty()) {
                return null;
            }

            Object existing = matchingInjector(injectors, usedInjectors, options);
            if (existing != null) {
                usedInjectors.add(existing);
                continue;
            }

            Object empty = emptyInjector(injectors, usedInjectors);
            if (empty == null) {
                return null;
            }

            SourceChoice source = findSource(sources, plannedUse, options);
            if (source == null) {
                return null;
            }

            usedInjectors.add(empty);
            plannedUse.put(source.slot, plannedUse.getOrDefault(source.slot, 0) + source.count);
            plan.moves.add(Move.toInjector(source.slot, empty, source.stack, source.count));
        }

        return plan;
    }

    private static List<Slot> playerInventorySlots(EntityPlayerMP player, Container container) {
        List<Slot> slots = new ArrayList<>();
        for (Slot slot : HeiReflection.containerSlots(container)) {
            if (slot != null
                    && HeiReflection.slotInventory(slot) instanceof InventoryPlayer
                    && Boolean.TRUE.equals(MinecraftMappingCompat.invoke(slot, "slot.getHasStack",
                    MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS, "func_75216_d", "getHasStack"))
                    && Boolean.TRUE.equals(MinecraftMappingCompat.invoke(slot, "slot.canTakeStack",
                    new Class<?>[]{EntityPlayer.class}, new Object[]{player}, "func_82869_a", "canTakeStack"))) {
                slots.add(slot);
            }
        }
        return slots;
    }

    private static Object matchingInjector(List<Object> injectors, Set<Object> used, List<ItemStack> options) {
        for (Object injector : injectors) {
            if (injector == null || used.contains(injector)) {
                continue;
            }
            if (matchesAnyWithEnough(stackMethod(injector, "getStackInPedestal"), options)) {
                return injector;
            }
        }
        return null;
    }

    private static Object emptyInjector(List<Object> injectors, Set<Object> used) {
        for (Object injector : injectors) {
            if (injector == null || used.contains(injector)) {
                continue;
            }
            ItemStack stack = stackMethod(injector, "getStackInPedestal");
            if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
                return injector;
            }
        }
        return null;
    }

    private static SourceChoice findSource(List<Slot> sources,
                                           Map<Slot, Integer> plannedUse,
                                           List<ItemStack> options) {
        for (Slot slot : sources) {
            Object sourceValue = MinecraftMappingCompat.invoke(slot, "slot.getStack",
                    MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS, "func_75211_c", "getStack");
            ItemStack source = sourceValue instanceof ItemStack ? (ItemStack) sourceValue : null;
            if (MinecraftMappingCompat.itemStackIsEmpty(source)) {
                continue;
            }
            int alreadyPlanned = plannedUse.getOrDefault(slot, 0);
            for (ItemStack option : options) {
                int required = requiredCount(option);
                if (MinecraftMappingCompat.itemStackCount(source) - alreadyPlanned >= required && matches(source, option)) {
                    return new SourceChoice(slot, option, required);
                }
            }
        }
        return null;
    }

    private static boolean matchesAnyWithEnough(ItemStack stack, List<ItemStack> options) {
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return false;
        }
        for (ItemStack option : options) {
            if (MinecraftMappingCompat.itemStackCount(stack) >= requiredCount(option) && matches(stack, option)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(ItemStack stack, ItemStack option) {
        if (MinecraftMappingCompat.itemStackIsEmpty(stack) || MinecraftMappingCompat.itemStackIsEmpty(option)) {
            return false;
        }
        if (!OreDictionary.itemMatches(option, stack, false)) {
            return false;
        }
        Object sameItem = MinecraftMappingCompat.invokeStatic(ItemStack.class, "itemStack.areItemsEqual",
                new Class<?>[]{ItemStack.class, ItemStack.class}, new Object[]{stack, option},
                "func_179545_c", "areItemsEqual");
        if (!Boolean.TRUE.equals(sameItem)) {
            return false;
        }
        Object sameTags = MinecraftMappingCompat.invokeStatic(ItemStack.class, "itemStack.areItemStackTagsEqual",
                new Class<?>[]{ItemStack.class, ItemStack.class}, new Object[]{stack, option},
                "func_77970_a", "areItemStackTagsEqual");
        return MinecraftMappingCompat.itemStackTagCompound(option) == null || Boolean.TRUE.equals(sameTags);
    }

    private static int requiredCount(ItemStack option) {
        return MinecraftMappingCompat.itemStackIsEmpty(option) ? 1 : Math.max(1, MinecraftMappingCompat.itemStackCount(option));
    }

    private static void consume(Slot slot, int count) {
        Object value = MinecraftMappingCompat.invoke(slot, "slot.getStack",
                MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS, "func_75211_c", "getStack");
        ItemStack stack = value instanceof ItemStack ? (ItemStack) value : null;
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            return;
        }
        MinecraftMappingCompat.itemStackShrink(stack, count);
        if (MinecraftMappingCompat.itemStackIsEmpty(stack)) {
            MinecraftMappingCompat.invoke(slot, "slot.putStack", new Class<?>[]{ItemStack.class},
                    new Object[]{MinecraftMappingCompat.emptyStack()}, "func_75215_d", "putStack");
        } else {
            MinecraftMappingCompat.invoke(slot, "slot.onSlotChanged",
                    MinecraftMappingCompat.NO_TYPES, MinecraftMappingCompat.NO_ARGS, "func_75218_e", "onSlotChanged");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> injectorList(Object core) {
        Object value = invoke(core, "getInjectors");
        return value instanceof List ? (List<Object>) value : Collections.emptyList();
    }

    private static ItemStack stackMethod(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        Object value = invoke(target, name, parameterTypes, args);
        return value instanceof ItemStack ? (ItemStack) value : MinecraftMappingCompat.emptyStack();
    }

    private static ItemStack stackMethod(Object target, String name) {
        return stackMethod(target, name, new Class<?>[0]);
    }

    private static boolean booleanMethod(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Boolean && (Boolean) value;
    }

    private static Object invoke(Object target, String name, Object... args) {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int index = 0; index < args.length; index++) {
            parameterTypes[index] = args[index] == null ? Object.class : args[index].getClass();
        }
        return invoke(target, name, parameterTypes, args);
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }
        try {
            Method method = findMethod(target.getClass(), parameterTypes, name);
            return method == null ? null : method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object fieldValue(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            java.lang.reflect.Field field = findField(target.getClass(), name);
            return field == null ? null : field.get(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static java.lang.reflect.Field findField(Class<?> owner, String name) {
        Class<?> type = owner;
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameterTypes, String name) {
        Class<?> type = owner;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static void markDirty(Object target) {
        if (!(target instanceof TileEntity)) {
            return;
        }
        TileEntity tile = (TileEntity) target;
        tile.markDirty();
        World world = tile.getWorld();
        BlockPos pos = tile.getPos();
        if (world != null && pos != null) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    private static void scheduleOnServer(Runnable task) {
        Object server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            task.run();
            return;
        }
        FMLCommonHandler.instance().getMinecraftServerInstance().addScheduledTask(task);
    }

    private static final class TransferPlan {
        private final List<Move> moves = new ArrayList<>();
    }

    private static final class SourceChoice {
        private final Slot slot;
        private final ItemStack stack;
        private final int count;

        private SourceChoice(Slot slot, ItemStack stack, int count) {
            this.slot = slot;
            this.stack = stack;
            this.count = count;
        }
    }

    private static final class Move {
        private final Slot source;
        private final Object injector;
        private final ItemStack stack;
        private final int count;
        private final boolean coreSlot;

        private Move(Slot source, Object injector, ItemStack stack, int count, boolean coreSlot) {
            this.source = source;
            this.injector = injector;
            this.stack = stack;
            this.count = count;
            this.coreSlot = coreSlot;
        }

        private static Move toCore(Slot source, ItemStack stack, int count) {
            return new Move(source, null, stack, count, true);
        }

        private static Move toInjector(Slot source, Object injector, ItemStack stack, int count) {
            return new Move(source, injector, stack, count, false);
        }
    }
}
