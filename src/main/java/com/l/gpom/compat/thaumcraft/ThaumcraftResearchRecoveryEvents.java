package com.l.gpom.compat.thaumcraft;

import com.l.gpom.GPOM;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ThaumcraftResearchRecoveryEvents {
    private static final String GOT_THAUMONOMICON = "!gotthaumonomicon";
    private static final String FIRST_STEPS = "FIRSTSTEPS";
    private static final String THAUMONOMICON_REGISTRY_NAME = "thaumcraft:thaumonomicon";
    private static final int DEBUG_LOG_LIMIT = Integer.getInteger("gpom.thaumcraftRecoveryLogLimit", 24);
    private static final ThaumcraftResearchRecoveryEvents INSTANCE = new ThaumcraftResearchRecoveryEvents();
    private static final Set<String> syncedKnownPlayers =
            Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    private static boolean registered;
    private static int debugLogCount;
    private static boolean firstServerTickLogged;
    private static volatile boolean disabled;
    private static volatile Item thaumonomiconItem;
    private static volatile Class<?> thaumonomiconItemClass;
    private static volatile Method getKnowledgeMethod;
    private static volatile Method isResearchKnownMethod;
    private static volatile Method isResearchCompleteMethod;
    private static volatile Method getResearchStageMethod;
    private static volatile Method addResearchMethod;
    private static volatile Method startResearchWithPopupMethod;
    private static volatile Method completeResearchMethod;
    private static volatile Method syncKnowledgeMethod;
    private static volatile Method getEntityWorldMethod;
    private static volatile Method getNameMethod;
    private static volatile Method inventorySizeMethod;
    private static volatile Method inventoryStackInSlotMethod;
    private static volatile Method itemStackIsEmptyMethod;
    private static volatile Method itemStackGetItemMethod;
    private static volatile Method itemStackGetCountMethod;
    private static volatile Field worldRemoteField;
    private static volatile Field ticksExistedField;
    private static volatile Field inventoryField;

    private ThaumcraftResearchRecoveryEvents() {
    }

    public static void register() {
        if (registered || !Loader.isModLoaded("thaumcraft")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        GPOM.LOGGER.info("[Thaumcraft Recovery] Registered Thaumonomicon inventory research recovery");
    }

    @SubscribeEvent
    public void recoverThaumonomiconResearch(LivingEvent.LivingUpdateEvent event) {
        if (disabled || event == null) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;

        try {
            if (!firstServerTickLogged) {
                firstServerTickLogged = true;
                debug("server-player tick path active for {}", playerName(player));
            }
            World world = entityWorld(player);
            if (world == null || isRemote(world) || ticksExisted(player) % 40 != 0) {
                return;
            }
            Object knowledge = knowledge(player);
            boolean knownBefore = isResearchKnown(knowledge, GOT_THAUMONOMICON);
            ThaumonomiconScan scan = scanThaumonomicon(player);
            if (knownBefore) {
                String name = playerName(player);
                if (scan.found && syncedKnownPlayers.add(name)) {
                    syncKnowledge(knowledge, player);
                    GPOM.LOGGER.info(
                            "[Thaumcraft Recovery] Synced known {} to {} because a Thaumonomicon is present ({})",
                            GOT_THAUMONOMICON,
                            name,
                            scan.source
                    );
                } else {
                    debug("{} already has {}; inventoryFound={} source={}", name, GOT_THAUMONOMICON, scan.found, scan.source);
                }
                if (scan.found) {
                    recoverFirstSteps(knowledge, player, name, scan.source);
                    refreshFirstStepsProgress(knowledge, player, name, scan.source);
                }
                return;
            }
            if (!scan.found) {
                debug("{} does not have a detected Thaumonomicon in {} scanned inventory slots", playerName(player), scan.slotsScanned);
                return;
            }

            boolean added = addResearch(knowledge, GOT_THAUMONOMICON);
            boolean knownAfter = isResearchKnown(knowledge, GOT_THAUMONOMICON);
            if (added || knownAfter) {
                syncKnowledge(knowledge, player);
            }
            recoverFirstSteps(knowledge, player, playerName(player), scan.source);
            refreshFirstStepsProgress(knowledge, player, playerName(player), scan.source);
            GPOM.LOGGER.info(
                    "[Thaumcraft Recovery] Granted {} to {} from {} ({}, added={}, knownAfter={})",
                    GOT_THAUMONOMICON,
                    playerName(player),
                    scan.source,
                    scan.itemDescription,
                    added,
                    knownAfter
            );
        } catch (ReflectiveOperationException throwable) {
            disabled = true;
            GPOM.LOGGER.warn("[Thaumcraft Recovery] Disabled Thaumonomicon inventory research recovery after reflective failure", throwable);
        } catch (Throwable throwable) {
            disabled = true;
            GPOM.LOGGER.warn("[Thaumcraft Recovery] Disabled Thaumonomicon inventory research recovery after failure", throwable);
        }
    }

    private static void recoverFirstSteps(Object knowledge, EntityPlayerMP player, String playerName, String source)
            throws ReflectiveOperationException {
        if (isResearchKnown(knowledge, FIRST_STEPS)) {
            return;
        }
        boolean started = startResearchWithPopup(player, FIRST_STEPS);
        boolean knownAfter = isResearchKnown(knowledge, FIRST_STEPS);
        if (started || knownAfter) {
            syncKnowledge(knowledge, player);
            GPOM.LOGGER.info(
                    "[Thaumcraft Recovery] Started {} for {} because {} is known and a Thaumonomicon is present ({}, started={}, knownAfter={})",
                    FIRST_STEPS,
                    playerName,
                    GOT_THAUMONOMICON,
                    source,
                    started,
                    knownAfter
            );
        } else {
            debug("Could not start {} for {} even though {} is known and inventoryFound=true", FIRST_STEPS, playerName, GOT_THAUMONOMICON);
        }
    }

    private static void refreshFirstStepsProgress(Object knowledge, EntityPlayerMP player, String playerName, String source)
            throws ReflectiveOperationException {
        if (!isResearchKnown(knowledge, FIRST_STEPS) || isResearchComplete(knowledge, FIRST_STEPS)) {
            return;
        }
        int stageBefore = getResearchStage(knowledge, FIRST_STEPS);
        boolean progressed = completeResearch(player, FIRST_STEPS);
        int stageAfter = getResearchStage(knowledge, FIRST_STEPS);
        if (progressed || stageAfter != stageBefore) {
            syncKnowledge(knowledge, player);
            GPOM.LOGGER.info(
                    "[Thaumcraft Recovery] Rechecked {} progress for {} after {} (progressed={}, stageBefore={}, stageAfter={}, complete={})",
                    FIRST_STEPS,
                    playerName,
                    source,
                    progressed,
                    stageBefore,
                    stageAfter,
                    isResearchComplete(knowledge, FIRST_STEPS)
            );
        } else {
            debug("No {} progress for {} after {}; stage={}", FIRST_STEPS, playerName, source, stageBefore);
        }
    }

    private static ThaumonomiconScan scanThaumonomicon(EntityPlayer player) throws ReflectiveOperationException {
        Item item = thaumonomiconItemOrNull();
        Class<?> itemClass = thaumonomiconItemClassOrNull();
        IInventory inventory = inventory(player);
        int size = inventorySize(inventory);
        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventoryStackInSlot(inventory, slot);
            Item stackItem = itemStackItem(stack);
            if (isThaumonomicon(stack, stackItem, item, itemClass)) {
                return ThaumonomiconScan.found("inventory slot " + slot, stackItem, itemStackCount(stack), size);
            }
        }
        return ThaumonomiconScan.missing(size);
    }

    private static boolean isThaumonomicon(ItemStack stack, Item stackItem, Item referenceItem, Class<?> referenceClass)
            throws ReflectiveOperationException {
        if (stack == null || itemStackIsEmpty(stack)) {
            return false;
        }
        if (stackItem == null) {
            return false;
        }
        if (stackItem == referenceItem || referenceClass != null && referenceClass.isInstance(stackItem)) {
            return true;
        }
        return stackItem.getRegistryName() != null
                && THAUMONOMICON_REGISTRY_NAME.equals(stackItem.getRegistryName().toString());
    }

    private static Item thaumonomiconItemOrNull() {
        Item item = thaumonomiconItem;
        if (item != null) {
            return item;
        }
        try {
            Field field = Class.forName("thaumcraft.api.items.ItemsTC").getField("thaumonomicon");
            Object value = field.get(null);
            if (value instanceof Item) {
                item = (Item) value;
                thaumonomiconItem = item;
                return item;
            }
            debug("ItemsTC.thaumonomicon was {}, falling back to class/registry-name matching", value);
        } catch (ReflectiveOperationException throwable) {
            debug("Could not resolve ItemsTC.thaumonomicon ({}), falling back to class/registry-name matching", throwable.toString());
        }
        return null;
    }

    private static Class<?> thaumonomiconItemClassOrNull() {
        Class<?> itemClass = thaumonomiconItemClass;
        if (itemClass != null) {
            return itemClass;
        }
        try {
            itemClass = Class.forName("thaumcraft.common.items.curios.ItemThaumonomicon");
            thaumonomiconItemClass = itemClass;
            return itemClass;
        } catch (ClassNotFoundException throwable) {
            debug("Could not resolve ItemThaumonomicon class ({}), falling back to registry-name matching", throwable.toString());
            return null;
        }
    }

    private static Object knowledge(EntityPlayer player) throws ReflectiveOperationException {
        Method method = getKnowledgeMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.api.capabilities.ThaumcraftCapabilities")
                    .getMethod("getKnowledge", EntityPlayer.class);
            method.setAccessible(true);
            getKnowledgeMethod = method;
        }
        return method.invoke(null, player);
    }

    private static boolean isResearchKnown(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = isResearchKnownMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("isResearchKnown", String.class);
            method.setAccessible(true);
            isResearchKnownMethod = method;
        }
        return (Boolean) method.invoke(knowledge, key);
    }

    private static boolean isResearchComplete(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = isResearchCompleteMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("isResearchComplete", String.class);
            method.setAccessible(true);
            isResearchCompleteMethod = method;
        }
        return (Boolean) method.invoke(knowledge, key);
    }

    private static int getResearchStage(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = getResearchStageMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("getResearchStage", String.class);
            method.setAccessible(true);
            getResearchStageMethod = method;
        }
        Object value = method.invoke(knowledge, key);
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static boolean addResearch(Object knowledge, String key) throws ReflectiveOperationException {
        Method method = addResearchMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("addResearch", String.class);
            method.setAccessible(true);
            addResearchMethod = method;
        }
        Object value = method.invoke(knowledge, key);
        return !(value instanceof Boolean) || (Boolean) value;
    }

    private static boolean startResearchWithPopup(EntityPlayer player, String key) throws ReflectiveOperationException {
        Method method = startResearchWithPopupMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.common.lib.research.ResearchManager")
                    .getMethod("startResearchWithPopup", EntityPlayer.class, String.class);
            method.setAccessible(true);
            startResearchWithPopupMethod = method;
        }
        Object value = method.invoke(null, player, key);
        return value instanceof Boolean && (Boolean) value;
    }

    private static boolean completeResearch(EntityPlayer player, String key) throws ReflectiveOperationException {
        Method method = completeResearchMethod;
        if (method == null) {
            method = Class.forName("thaumcraft.common.lib.research.ResearchManager")
                    .getMethod("completeResearch", EntityPlayer.class, String.class);
            method.setAccessible(true);
            completeResearchMethod = method;
        }
        Object value = method.invoke(null, player, key);
        return value instanceof Boolean && (Boolean) value;
    }

    private static void syncKnowledge(Object knowledge, EntityPlayerMP player) throws ReflectiveOperationException {
        Method method = syncKnowledgeMethod;
        if (method == null) {
            method = knowledge.getClass().getMethod("sync", EntityPlayerMP.class);
            method.setAccessible(true);
            syncKnowledgeMethod = method;
        }
        method.invoke(knowledge, player);
    }

    private static World entityWorld(EntityPlayer player) throws ReflectiveOperationException {
        Method method = getEntityWorldMethod;
        if (method == null) {
            method = findMethod(player.getClass(), "func_130014_f_", "getEntityWorld");
            method.setAccessible(true);
            getEntityWorldMethod = method;
        }
        Object value = method.invoke(player);
        return value instanceof World ? (World) value : null;
    }

    private static boolean isRemote(World world) throws ReflectiveOperationException {
        Field field = worldRemoteField;
        if (field == null) {
            field = findField(world.getClass(), "field_72995_K", "isRemote");
            field.setAccessible(true);
            worldRemoteField = field;
        }
        return field.getBoolean(world);
    }

    private static int ticksExisted(EntityPlayer player) throws ReflectiveOperationException {
        Field field = ticksExistedField;
        if (field == null) {
            field = findField(player.getClass(), "field_70173_aa", "ticksExisted");
            field.setAccessible(true);
            ticksExistedField = field;
        }
        return field.getInt(player);
    }

    private static IInventory inventory(EntityPlayer player) throws ReflectiveOperationException {
        Field field = inventoryField;
        if (field == null) {
            field = findField(player.getClass(), "field_71071_by", "inventory");
            field.setAccessible(true);
            inventoryField = field;
        }
        Object value = field.get(player);
        if (!(value instanceof IInventory)) {
            throw new NoSuchFieldException("EntityPlayer.inventory");
        }
        return (IInventory) value;
    }

    private static int inventorySize(IInventory inventory) throws ReflectiveOperationException {
        Method method = inventorySizeMethod;
        if (method == null) {
            method = findMethod(inventory.getClass(), "func_70302_i_", "getSizeInventory");
            method.setAccessible(true);
            inventorySizeMethod = method;
        }
        return (Integer) method.invoke(inventory);
    }

    private static ItemStack inventoryStackInSlot(IInventory inventory, int slot) throws ReflectiveOperationException {
        Method method = inventoryStackInSlotMethod;
        if (method == null) {
            method = findMethodWithParameters(inventory.getClass(), "func_70301_a", "getStackInSlot", int.class);
            method.setAccessible(true);
            inventoryStackInSlotMethod = method;
        }
        Object value = method.invoke(inventory, slot);
        return value instanceof ItemStack ? (ItemStack) value : null;
    }

    private static boolean itemStackIsEmpty(ItemStack stack) throws ReflectiveOperationException {
        Method method = itemStackIsEmptyMethod;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_190926_b", "isEmpty");
            method.setAccessible(true);
            itemStackIsEmptyMethod = method;
        }
        return (Boolean) method.invoke(stack);
    }

    private static Item itemStackItem(ItemStack stack) throws ReflectiveOperationException {
        if (stack == null || itemStackIsEmpty(stack)) {
            return null;
        }
        Method method = itemStackGetItemMethod;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_77973_b", "getItem");
            method.setAccessible(true);
            itemStackGetItemMethod = method;
        }
        Object value = method.invoke(stack);
        return value instanceof Item ? (Item) value : null;
    }

    private static int itemStackCount(ItemStack stack) throws ReflectiveOperationException {
        Method method = itemStackGetCountMethod;
        if (method == null) {
            method = findMethod(stack.getClass(), "func_190916_E", "getCount");
            method.setAccessible(true);
            itemStackGetCountMethod = method;
        }
        Object value = method.invoke(stack);
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static String playerName(EntityPlayer player) throws ReflectiveOperationException {
        Method method = getNameMethod;
        if (method == null) {
            method = findMethod(player.getClass(), "func_70005_c_", "getName");
            method.setAccessible(true);
            getNameMethod = method;
        }
        Object value = method.invoke(player);
        return value instanceof String ? (String) value : "<unknown>";
    }

    private static Method findMethod(Class<?> type, String... names) throws NoSuchMethodException {
        return ReflectionLookup.findMethod(type, names);
    }

    private static Method findMethodWithParameters(Class<?> type, String firstName, String secondName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return ReflectionLookup.findMethod(type, new String[] {firstName, secondName}, parameterTypes);
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        return ReflectionLookup.findField(type, names);
    }

    private static void debug(String message, Object... args) {
        if (debugLogCount >= DEBUG_LOG_LIMIT) {
            return;
        }
        debugLogCount++;
        GPOM.LOGGER.info("[Thaumcraft Recovery] " + message, args);
    }

    private static final class ThaumonomiconScan {
        private final boolean found;
        private final String source;
        private final String itemDescription;
        private final int slotsScanned;

        private ThaumonomiconScan(boolean found, String source, String itemDescription, int slotsScanned) {
            this.found = found;
            this.source = source;
            this.itemDescription = itemDescription;
            this.slotsScanned = slotsScanned;
        }

        private static ThaumonomiconScan found(String source, Item item, int count, int slotsScanned) {
            String registryName = item == null || item.getRegistryName() == null ? "<no registry name>" : item.getRegistryName().toString();
            String itemClass = item == null ? "<no item>" : item.getClass().getName();
            return new ThaumonomiconScan(true, source, registryName + " class=" + itemClass + " count=" + count, slotsScanned);
        }

        private static ThaumonomiconScan missing(int slotsScanned) {
            return new ThaumonomiconScan(false, "<none>", "<none>", slotsScanned);
        }
    }
}
