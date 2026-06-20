package com.l.gpom.compat.hei;

import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import mezz.jei.ingredients.Ingredients;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class HeiCraftableRecipeSorter {
    private static final int WILDCARD_DAMAGE = 32767;
    private static final Method MC_GET_MINECRAFT = findMethod(Minecraft.class, new Class<?>[0], "func_71410_x", "getMinecraft");
    private static final Field MC_PLAYER = findField(Minecraft.class, "field_71439_g", "player");
    private static final Field PLAYER_INVENTORY = findField(EntityPlayer.class, "field_71071_by", "inventory");
    private static final Field INVENTORY_MAIN = findField(InventoryPlayer.class, "field_70462_a", "mainInventory");
    private static final Field INVENTORY_OFFHAND = findField(InventoryPlayer.class, "field_184439_c", "offHandInventory");
    private static final Method INVENTORY_SIZE = findMethod(InventoryPlayer.class, new Class<?>[0], "func_70302_i_", "getSizeInventory");
    private static final Method INVENTORY_STACK_IN_SLOT = findMethod(InventoryPlayer.class, new Class<?>[]{int.class}, "func_70301_a", "getStackInSlot");
    private static final Method STACK_IS_EMPTY = findMethod(ItemStack.class, new Class<?>[0], "func_190926_b", "isEmpty");
    private static final Method STACK_GET_COUNT = findMethod(ItemStack.class, new Class<?>[0], "func_190916_E", "getCount");
    private static final Method STACK_GET_ITEM = findMethod(ItemStack.class, new Class<?>[0], "func_77973_b", "getItem");
    private static final Method STACK_GET_METADATA = findMethod(ItemStack.class, new Class<?>[0], "func_77960_j", "getMetadata");
    private static final Method STACK_GET_TAG = findMethod(ItemStack.class, new Class<?>[0], "func_77978_p", "getTagCompound");
    private static final List<List<ItemStack>> EMPTY_INPUTS = Collections.emptyList();
    private static final Map<IRecipeWrapper, List<List<ItemStack>>> INPUT_CACHE =
            Collections.synchronizedMap(new WeakHashMap<IRecipeWrapper, List<List<ItemStack>>>());

    private HeiCraftableRecipeSorter() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List sortRecipes(List recipes) {
        if (!Boolean.parseBoolean(System.getProperty("gpom.hei.craftableRecipesFirst.enabled", "true"))
                || recipes == null || recipes.size() < 2) {
            return recipes;
        }

        List<ItemStack> inventory = inventoryStacks();
        if (inventory.isEmpty()) {
            return recipes;
        }

        Object[] recipeValues = new Object[recipes.size()];
        int[] originalIndexes = new int[recipes.size()];
        int[] itemGroups = new int[recipes.size()];
        int[] matchedGroups = new int[recipes.size()];
        boolean shouldSort = false;
        for (int i = 0; i < recipes.size(); i++) {
            Object recipe = recipes.get(i);
            int[] score = score(recipe, inventory);
            recipeValues[i] = recipe;
            originalIndexes[i] = i;
            itemGroups[i] = score[0];
            matchedGroups[i] = score[1];
            if (score[0] > 0 && score[1] > 0) {
                shouldSort = true;
            }
        }

        if (!shouldSort) {
            return recipes;
        }

        sortScored(recipeValues, originalIndexes, itemGroups, matchedGroups);

        boolean changed = false;
        List sorted = new ArrayList(recipes.size());
        for (int i = 0; i < recipeValues.length; i++) {
            sorted.add(recipeValues[i]);
            if (originalIndexes[i] != i) {
                changed = true;
            }
        }
        return changed ? sorted : recipes;
    }

    private static int[] score(Object recipe, List<ItemStack> inventory) {
        if (!(recipe instanceof IRecipeWrapper)) {
            return new int[]{0, 0};
        }

        List<List<ItemStack>> inputs = recipeInputs((IRecipeWrapper) recipe);
        if (inputs.isEmpty()) {
            return new int[]{0, 0};
        }

        int[] remaining = new int[inventory.size()];
        for (int i = 0; i < inventory.size(); i++) {
            remaining[i] = count(inventory.get(i));
        }

        int matchedGroups = 0;
        int itemGroups = 0;
        for (List<ItemStack> alternatives : inputs) {
            if (alternatives.isEmpty()) {
                continue;
            }
            itemGroups++;
            if (consumeAnyAlternative(alternatives, inventory, remaining)) {
                matchedGroups++;
            }
        }

        if (itemGroups == 0) {
            return new int[]{0, 0};
        }
        return new int[]{itemGroups, matchedGroups};
    }

    private static List<List<ItemStack>> recipeInputs(IRecipeWrapper wrapper) {
        List<List<ItemStack>> cached = INPUT_CACHE.get(wrapper);
        if (cached != null) {
            return cached;
        }

        List<List<ItemStack>> created = EMPTY_INPUTS;
        try {
            Ingredients ingredients = new Ingredients();
            wrapper.getIngredients(ingredients);
            List<List<ItemStack>> rawInputs = ingredients.getInputs(VanillaTypes.ITEM);
            List<List<ItemStack>> itemGroups = new ArrayList<>();
            for (List<ItemStack> rawGroup : rawInputs) {
                List<ItemStack> group = new ArrayList<>();
                if (rawGroup != null) {
                    for (ItemStack stack : rawGroup) {
                        if (!isEmptyStack(stack) && item(stack) != null && count(stack) > 0) {
                            group.add(stack);
                        }
                    }
                }
                if (!group.isEmpty()) {
                    itemGroups.add(group);
                }
            }
            created = itemGroups.isEmpty() ? EMPTY_INPUTS : itemGroups;
        } catch (RuntimeException | LinkageError ignored) {
            created = EMPTY_INPUTS;
        }

        INPUT_CACHE.put(wrapper, created);
        return created;
    }

    private static boolean consumeAnyAlternative(List<ItemStack> alternatives,
                                                 List<ItemStack> inventory,
                                                 int[] remaining) {
        for (ItemStack required : alternatives) {
            int requiredCount = Math.max(1, count(required));
            if (available(required, requiredCount, inventory, remaining)) {
                consume(required, requiredCount, inventory, remaining);
                return true;
            }
        }
        return false;
    }

    private static boolean available(ItemStack required,
                                     int requiredCount,
                                     List<ItemStack> inventory,
                                     int[] remaining) {
        int found = 0;
        for (int i = 0; i < inventory.size(); i++) {
            if (remaining[i] <= 0) {
                continue;
            }
            if (matches(inventory.get(i), required)) {
                found += remaining[i];
                if (found >= requiredCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void consume(ItemStack required,
                                int requiredCount,
                                List<ItemStack> inventory,
                                int[] remaining) {
        int needed = requiredCount;
        for (int i = 0; i < inventory.size() && needed > 0; i++) {
            if (remaining[i] <= 0 || !matches(inventory.get(i), required)) {
                continue;
            }
            int used = Math.min(remaining[i], needed);
            remaining[i] -= used;
            needed -= used;
        }
    }

    private static boolean matches(ItemStack available, ItemStack required) {
        if (isEmptyStack(available) || isEmptyStack(required)) {
            return false;
        }
        Item availableItem = item(available);
        Item requiredItem = item(required);
        if (availableItem == null || availableItem != requiredItem) {
            return false;
        }

        int requiredMetadata = metadata(required);
        if (requiredMetadata != WILDCARD_DAMAGE && metadata(available) != requiredMetadata) {
            return false;
        }

        NBTTagCompound requiredTag = tag(required);
        return requiredTag == null || requiredTag.equals(tag(available));
    }

    private static List<ItemStack> inventoryStacks() {
        InventoryPlayer inventory = playerInventory();
        if (inventory == null) {
            return Collections.emptyList();
        }

        List<ItemStack> stacks = new ArrayList<>();
        addInventoryList(stacks, fieldValue(inventory, INVENTORY_MAIN));
        addInventoryList(stacks, fieldValue(inventory, INVENTORY_OFFHAND));
        if (stacks.isEmpty()) {
            addInventoryBySlots(stacks, inventory);
        }
        return stacks;
    }

    private static InventoryPlayer playerInventory() {
        Object minecraft = invoke(null, MC_GET_MINECRAFT);
        Object player = fieldValue(minecraft, MC_PLAYER);
        Object inventory = fieldValue(player, PLAYER_INVENTORY);
        return inventory instanceof InventoryPlayer ? (InventoryPlayer) inventory : null;
    }

    private static void addInventoryList(List<ItemStack> target, Object value) {
        if (!(value instanceof Iterable)) {
            return;
        }
        for (Object entry : (Iterable<?>) value) {
            if (entry instanceof ItemStack) {
                addInventoryStack(target, (ItemStack) entry);
            }
        }
    }

    private static void addInventoryBySlots(List<ItemStack> target, InventoryPlayer inventory) {
        Object sizeValue = invoke(inventory, INVENTORY_SIZE);
        if (!(sizeValue instanceof Integer)) {
            return;
        }
        int size = (Integer) sizeValue;
        for (int slot = 0; slot < size; slot++) {
            Object stack = invoke(inventory, INVENTORY_STACK_IN_SLOT, slot);
            if (stack instanceof ItemStack) {
                addInventoryStack(target, (ItemStack) stack);
            }
        }
    }

    private static void addInventoryStack(List<ItemStack> target, ItemStack stack) {
        if (!isEmptyStack(stack) && item(stack) != null && count(stack) > 0) {
            target.add(stack);
        }
    }

    private static void sortScored(Object[] recipes, int[] originalIndexes, int[] itemGroups, int[] matchedGroups) {
        for (int i = 1; i < recipes.length; i++) {
            Object recipe = recipes[i];
            int originalIndex = originalIndexes[i];
            int itemGroup = itemGroups[i];
            int matchedGroup = matchedGroups[i];
            int j = i - 1;
            while (j >= 0 && compareScores(itemGroup, matchedGroup, originalIndex,
                    itemGroups[j], matchedGroups[j], originalIndexes[j]) < 0) {
                recipes[j + 1] = recipes[j];
                originalIndexes[j + 1] = originalIndexes[j];
                itemGroups[j + 1] = itemGroups[j];
                matchedGroups[j + 1] = matchedGroups[j];
                j--;
            }
            recipes[j + 1] = recipe;
            originalIndexes[j + 1] = originalIndex;
            itemGroups[j + 1] = itemGroup;
            matchedGroups[j + 1] = matchedGroup;
        }
    }

    private static int compareScores(int leftGroups, int leftMatched, int leftIndex,
                                     int rightGroups, int rightMatched, int rightIndex) {
        boolean leftCraftable = leftGroups > 0 && leftGroups == leftMatched;
        boolean rightCraftable = rightGroups > 0 && rightGroups == rightMatched;
        if (leftCraftable != rightCraftable) {
            return leftCraftable ? -1 : 1;
        }
        if (leftMatched != rightMatched) {
            return rightMatched - leftMatched;
        }
        int leftMissing = leftGroups - leftMatched;
        int rightMissing = rightGroups - rightMatched;
        if (leftMissing != rightMissing) {
            return leftMissing - rightMissing;
        }
        return leftIndex - rightIndex;
    }

    private static boolean isEmptyStack(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        Object value = invoke(stack, STACK_IS_EMPTY);
        return value instanceof Boolean ? (Boolean) value : item(stack) == null || count(stack) <= 0;
    }

    private static int count(ItemStack stack) {
        Object value = invoke(stack, STACK_GET_COUNT);
        return value instanceof Integer ? (Integer) value : 1;
    }

    private static Item item(ItemStack stack) {
        Object value = invoke(stack, STACK_GET_ITEM);
        return value instanceof Item ? (Item) value : null;
    }

    private static int metadata(ItemStack stack) {
        Object value = invoke(stack, STACK_GET_METADATA);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static NBTTagCompound tag(ItemStack stack) {
        Object value = invoke(stack, STACK_GET_TAG);
        return value instanceof NBTTagCompound ? (NBTTagCompound) value : null;
    }

    private static Object fieldValue(Object target, Field field) {
        if (target == null || field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (IllegalAccessException | RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, Method method, Object... args) {
        if (method == null) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
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
                    // Try the next runtime/dev name, then the superclass.
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
                    // Try the next runtime/dev name, then the superclass.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

}
