package com.l.gpom.diagnostics;

import appeng.api.AEApi;
import appeng.helpers.ItemStackHelper;
import appeng.helpers.PatternHelper;
import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Ae2PatternDiagnostics {
    private static final Ae2PatternDiagnostics INSTANCE = new Ae2PatternDiagnostics();
    private static final Field WORLD_IS_REMOTE = findField(World.class, "isRemote", "field_72995_K");
    private static final Field WORLD_PROVIDER = findField(World.class, "provider", "field_73011_w");
    private static final Method PROVIDER_GET_DIMENSION = findMethod(WorldProvider.class, "getDimension", "func_186058_p");
    private static final Method STACK_IS_EMPTY = findMethod(ItemStack.class, "isEmpty", "func_190926_b");
    private static final Method STACK_COPY = findMethod(ItemStack.class, "copy", "func_77946_l");
    private static final Method STACK_GET_COUNT = findMethod(ItemStack.class, "getCount", "func_190916_E");
    private static final Method STACK_SET_COUNT = findMethod(ItemStack.class, "setCount", "func_190920_e", int.class);
    private static final Method STACK_SET_TAG_COMPOUND = findMethod(ItemStack.class, "setTagCompound", "func_77982_d", NBTTagCompound.class);
    private static final Method STACK_GET_ITEM = findMethod(ItemStack.class, "getItem", "func_77973_b");
    private static final Method STACK_GET_METADATA = findMethod(ItemStack.class, "getMetadata", "func_77960_j");
    private static final Method RECIPE_GET_OUTPUT = findMethod(IRecipe.class, "getRecipeOutput", "func_77571_b");
    private static final Method RECIPE_CAN_FIT = findMethod(IRecipe.class, "canFit", "func_194133_a", int.class, int.class);
    private static final Method RECIPE_GET_INGREDIENTS = findMethod(IRecipe.class, "getIngredients", "func_192400_c");
    private static final Method RECIPE_MATCHES = findMethod(IRecipe.class, "matches", "func_77569_a", InventoryCrafting.class, World.class);
    private static final Method INGREDIENT_MATCHING_STACKS = findMethod(Ingredient.class, "getMatchingStacks", "func_193365_a");
    private static final Method INVENTORY_SET_SLOT = findMethod(InventoryCrafting.class, "setInventorySlotContents", "func_70299_a", int.class, ItemStack.class);
    private static final Method NBT_SET_TAG = findMethod(NBTTagCompound.class, "setTag", "func_74782_a", String.class, NBTBase.class);
    private static final Method NBT_SET_BOOLEAN = findMethod(NBTTagCompound.class, "setBoolean", "func_74757_a", String.class, boolean.class);
    private static final Method NBT_LIST_APPEND_TAG = findMethod(NBTTagList.class, "appendTag", "func_74742_a", NBTBase.class);
    private static final Method STACKS_EQUAL = findMethod(ItemStack.class, "areItemStacksEqual", "func_77989_b", ItemStack.class, ItemStack.class);
    private static final ItemStack EMPTY_STACK = findEmptyStack();
    private static final Ingredient EMPTY_INGREDIENT = findEmptyIngredient();
    private static volatile boolean registered;
    private static volatile boolean ran;

    private Ae2PatternDiagnostics() {
    }

    public static void register() {
        if (registered || !GpomEarlyConfig.ae2PatternDiagnosticsEnabled()) {
            return;
        }
        if (!Loader.isModLoaded("appliedenergistics2")) {
            GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Requested pattern diagnostics, but appliedenergistics2 is not loaded");
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        GPOM.LOGGER.info("[GPOM AE2 PatternDiag] Registered one-shot world-load crafting pattern validator");
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        if (world == null || isRemote(world) || dimension(world) != 0 || ran) {
            return;
        }
        ran = true;
        try {
            run(world);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Pattern diagnostic scan failed", throwable);
        }
    }

    private static void run(World world) {
        Optional<ItemStack> encodedPattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
        if (!encodedPattern.isPresent() || isEmptyStack(encodedPattern.get())) {
            GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Cannot run: AE2 encoded pattern item is unavailable");
            return;
        }

        int maxFailures = GpomEarlyConfig.ae2PatternDiagnosticsMaxFailures();
        boolean logMismatches = GpomEarlyConfig.ae2PatternDiagnosticsLogMismatchedOutputs();
        Counters counters = new Counters();
        long startedAt = System.nanoTime();

        GPOM.LOGGER.info(
                "[GPOM AE2 PatternDiag] Starting scan: recipes={}, maxFailureLogs={}, logMismatchedOutputs={}",
                ForgeRegistries.RECIPES.getValuesCollection().size(),
                maxFailures,
                logMismatches
        );

        for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
            counters.scanned++;
            Attempt attempt;
            try {
                attempt = buildAttempt(recipe, world);
            } catch (Throwable throwable) {
                counters.failure("attempt-exception");
                logFailure(counters, maxFailures, recipe, "attempt-exception", null, null, null, throwable);
                continue;
            }

            if (attempt.skipReason != null) {
                counters.skipped(attempt.skipReason);
                continue;
            }

            if (attempt.failureReason != null) {
                counters.failure(attempt.failureReason);
                logFailure(counters, maxFailures, recipe, attempt.failureReason, attempt.inputs, attempt.matchedRecipe, attempt.matchedOutput, attempt.failure);
                continue;
            }

            if (GpomEarlyConfig.ae2PatternDiagnosticsSkipRecipeFunctions() && usesRecipeFunction(recipe)) {
                counters.skipped("recipe-function");
                continue;
            }

            if (logMismatches && attempt.outputMismatch) {
                counters.mismatches++;
                logFailure(counters, maxFailures, recipe, "canonical-input-output-mismatch", attempt.inputs, attempt.matchedRecipe, attempt.matchedOutput, null);
            }

            try {
                ItemStack pattern = encodedPattern(encodedPattern.get(), attempt.inputs, attempt.matchedOutput);
                PatternHelper helper = new PatternHelper(pattern, world);
                if (!helper.isCraftable() || helper.getOutputs().length == 0) {
                    counters.failure("pattern-helper-not-craftable");
                    logFailure(counters, maxFailures, recipe, "pattern-helper-not-craftable", attempt.inputs, attempt.matchedRecipe, attempt.matchedOutput, null);
                } else {
                    counters.valid++;
                }
            } catch (Throwable throwable) {
                counters.failure("pattern-helper-rejected");
                logFailure(counters, maxFailures, recipe, "pattern-helper-rejected", attempt.inputs, attempt.matchedRecipe, attempt.matchedOutput, throwable);
            }
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        GPOM.LOGGER.info(
                "[GPOM AE2 PatternDiag] Finished scan in {} ms: scanned={}, valid={}, failures={}, mismatches={}, skipped={}, failureReasons={}, skipReasons={}, suppressedFailureLogs={}",
                elapsedMs,
                counters.scanned,
                counters.valid,
                counters.failures,
                counters.mismatches,
                counters.skipped,
                counters.failuresByReason,
                counters.skipsByReason,
                Math.max(0, counters.failureLogCandidates - maxFailures)
        );
    }

    private static Attempt buildAttempt(IRecipe recipe, World world) {
        Attempt attempt = new Attempt();
        attempt.expectedOutput = safeRecipeOutput(recipe);
        if (isEmptyStack(attempt.expectedOutput)) {
            attempt.skipReason = "empty-recipe-output";
            return attempt;
        }
        if (!canFit3x3(recipe)) {
            attempt.skipReason = "does-not-fit-3x3";
            return attempt;
        }

        NonNullList<Ingredient> ingredients = recipeIngredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) {
            attempt.skipReason = "no-ingredients";
            return attempt;
        }

        attempt.inputs = new ItemStack[9];
        for (int i = 0; i < attempt.inputs.length; i++) {
            attempt.inputs[i] = emptyStack();
        }

        boolean shaped = recipe instanceof IShapedRecipe;
        int width = shaped ? ((IShapedRecipe) recipe).getRecipeWidth() : 3;
        int height = shaped ? ((IShapedRecipe) recipe).getRecipeHeight() : 3;
        if (width <= 0 || height <= 0 || width > 3 || height > 3) {
            attempt.skipReason = "invalid-grid-size";
            return attempt;
        }

        int shapelessSlot = 0;
        for (int index = 0; index < ingredients.size(); index++) {
            Ingredient ingredient = ingredients.get(index);
            if (isEmptyIngredient(ingredient)) {
                continue;
            }
            ItemStack stack = firstStack(ingredient);
            if (isEmptyStack(stack)) {
                attempt.failureReason = "ingredient-has-no-matching-stacks";
                attempt.failure = new IllegalStateException("ingredient index " + index + " has no concrete matching stacks");
                return attempt;
            }

            int slot;
            if (shaped) {
                if (index >= width * height) {
                    attempt.skipReason = "shaped-ingredient-overflow";
                    return attempt;
                }
                int row = index / width;
                int column = index % width;
                slot = row * 3 + column;
            } else {
                if (shapelessSlot >= 9) {
                    attempt.skipReason = "too-many-shapeless-ingredients";
                    return attempt;
                }
                slot = shapelessSlot++;
            }
            attempt.inputs[slot] = stack;
        }

        InventoryCrafting inventory = inventory(attempt.inputs);
        attempt.recipeMatchesOwnGrid = recipeMatches(recipe, inventory, world);
        if (!attempt.recipeMatchesOwnGrid) {
            attempt.failureReason = "canonical-input-no-match";
            return attempt;
        }

        // Do not call CraftingManager.findMatchingRecipe/getCraftingResult here.
        // CraftTweaker recipe functions execute from those paths and often require real crafting context.
        attempt.matchedRecipe = recipe;
        attempt.matchedOutput = copyStack(attempt.expectedOutput);
        return attempt;
    }

    private static ItemStack safeRecipeOutput(IRecipe recipe) {
        try {
            ItemStack output = recipeOutput(recipe);
            return copyStack(output);
        } catch (Throwable ignored) {
            return emptyStack();
        }
    }

    private static boolean canFit3x3(IRecipe recipe) {
        try {
            return recipeCanFit(recipe, 3, 3);
        } catch (Throwable ignored) {
            return true;
        }
    }

    private static boolean isEmptyIngredient(Ingredient ingredient) {
        return ingredient == null || ingredient == EMPTY_INGREDIENT || matchingStacks(ingredient).length == 0;
    }

    private static ItemStack firstStack(Ingredient ingredient) {
        try {
            ItemStack[] stacks = matchingStacks(ingredient);
            for (ItemStack stack : stacks) {
                if (stack != null && !isEmptyStack(stack)) {
                    ItemStack copy = copyStack(stack);
                    if (count(copy) <= 0) {
                        setCount(copy, 1);
                    }
                    return copy;
                }
            }
        } catch (Throwable ignored) {
            return emptyStack();
        }
        return emptyStack();
    }

    private static InventoryCrafting inventory(ItemStack[] inputs) {
        InventoryCrafting inventory = new InventoryCrafting(new Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                return false;
            }
        }, 3, 3);
        for (int i = 0; i < inputs.length; i++) {
            setInventorySlot(inventory, i, copyStack(inputs[i]));
        }
        return inventory;
    }

    private static ItemStack encodedPattern(ItemStack template, ItemStack[] inputs, ItemStack output) {
        ItemStack pattern = copyStack(template);
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList in = new NBTTagList();
        NBTTagList out = new NBTTagList();
        for (ItemStack input : inputs) {
            appendTag(in, itemTag(input));
        }
        appendTag(out, itemTag(output));
        setTag(tag, "in", in);
        setTag(tag, "out", out);
        setBoolean(tag, "crafting", true);
        setBoolean(tag, "substitute", true);
        setTagCompound(pattern, tag);
        return pattern;
    }

    private static NBTBase itemTag(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (stack != null && !isEmptyStack(stack)) {
            ItemStackHelper.stackWriteToNBT(stack, tag);
        }
        return tag;
    }

    private static boolean sameStack(ItemStack left, ItemStack right) {
        try {
            if (STACKS_EQUAL != null) {
                Object value = STACKS_EQUAL.invoke(null, left, right);
                return value instanceof Boolean && (Boolean) value;
            }
        } catch (Throwable ignored) {
        }
        return left == right || (isEmptyStack(left) && isEmptyStack(right));
    }

    private static void logFailure(Counters counters, int maxFailures, IRecipe recipe, String reason, ItemStack[] inputs, IRecipe matchedRecipe, ItemStack matchedOutput, Throwable throwable) {
        counters.failureLogCandidates++;
        if (counters.failureLogCandidates > maxFailures) {
            return;
        }
        GPOM.LOGGER.warn(
                "[GPOM AE2 PatternDiag] {} recipe={} class={} expected={} matchedRecipe={} matchedOutput={} inputs={} cause={}",
                reason,
                recipeName(recipe),
                recipe.getClass().getName(),
                stackName(safeRecipeOutput(recipe)),
                recipeName(matchedRecipe),
                stackName(matchedOutput),
                inputsName(inputs),
                throwable == null ? "<none>" : throwable.getClass().getName() + ": " + throwable.getMessage()
        );
    }

    private static String recipeName(IRecipe recipe) {
        if (recipe == null) {
            return "<none>";
        }
        ResourceLocation name = recipe.getRegistryName();
        return name == null ? "<unnamed>" : name.toString();
    }

    private static String stackName(ItemStack stack) {
        if (stack == null || isEmptyStack(stack)) {
            return "<empty>";
        }
        Item item = item(stack);
        ResourceLocation itemName = item == null ? null : item.getRegistryName();
        return count(stack) + "x" + (itemName == null ? "<unregistered>" : itemName.toString()) + "@" + metadata(stack);
    }

    private static String inputsName(ItemStack[] inputs) {
        if (inputs == null) {
            return "<none>";
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < inputs.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(i).append('=').append(stackName(inputs[i]));
        }
        return builder.append(']').toString();
    }

    private static final class Attempt {
        private String skipReason;
        private String failureReason;
        private Throwable failure;
        private ItemStack expectedOutput = emptyStack();
        private ItemStack matchedOutput = emptyStack();
        private ItemStack[] inputs;
        private IRecipe matchedRecipe;
        private boolean recipeMatchesOwnGrid;
        private boolean outputMismatch;
    }

    @SuppressWarnings("unchecked")
    private static NonNullList<Ingredient> recipeIngredients(IRecipe recipe) {
        if (recipe == null || RECIPE_GET_INGREDIENTS == null) {
            return NonNullList.create();
        }
        try {
            Object value = RECIPE_GET_INGREDIENTS.invoke(recipe);
            return value instanceof NonNullList ? (NonNullList<Ingredient>) value : NonNullList.create();
        } catch (Throwable throwable) {
            return NonNullList.create();
        }
    }

    private static ItemStack recipeOutput(IRecipe recipe) {
        if (recipe == null || RECIPE_GET_OUTPUT == null) {
            return emptyStack();
        }
        try {
            Object value = RECIPE_GET_OUTPUT.invoke(recipe);
            return value instanceof ItemStack ? (ItemStack) value : emptyStack();
        } catch (Throwable throwable) {
            return emptyStack();
        }
    }

    private static boolean recipeCanFit(IRecipe recipe, int width, int height) {
        if (recipe == null || RECIPE_CAN_FIT == null) {
            return true;
        }
        try {
            Object value = RECIPE_CAN_FIT.invoke(recipe, width, height);
            return !(value instanceof Boolean) || (Boolean) value;
        } catch (Throwable throwable) {
            return true;
        }
    }

    private static boolean recipeMatches(IRecipe recipe, InventoryCrafting inventory, World world) {
        if (recipe == null || RECIPE_MATCHES == null) {
            return false;
        }
        try {
            Object value = RECIPE_MATCHES.invoke(recipe, inventory, world);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable throwable) {
            return false;
        }
    }

    private static boolean usesRecipeFunction(IRecipe recipe) {
        IRecipe current = recipe;
        for (int depth = 0; current != null && depth < 4; depth++) {
            if (hasCraftTweakerRecipeFunction(current)) {
                return true;
            }

            IRecipe wrapped = wrappedRecipeStageRecipe(current);
            if (wrapped == null || wrapped == current) {
                return false;
            }
            current = wrapped;
        }
        return false;
    }

    private static boolean hasCraftTweakerRecipeFunction(IRecipe recipe) {
        if (recipe == null || !isInstance(recipe, "crafttweaker.mc1120.recipes.MCRecipeBase")) {
            return false;
        }
        try {
            Method method = recipe.getClass().getMethod("hasRecipeFunction");
            method.setAccessible(true);
            Object value = method.invoke(recipe);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable throwable) {
            String className = recipe.getClass().getName();
            return className.equals("crafttweaker.mc1120.recipes.MCRecipeShaped")
                    || className.equals("crafttweaker.mc1120.recipes.MCRecipeShapeless");
        }
    }

    private static IRecipe wrappedRecipeStageRecipe(IRecipe recipe) {
        if (recipe == null || !isInstance(recipe, "com.blamejared.recipestages.recipes.RecipeStage")) {
            return null;
        }
        try {
            Method method = recipe.getClass().getMethod("getRecipe");
            method.setAccessible(true);
            Object value = method.invoke(recipe);
            return value instanceof IRecipe ? (IRecipe) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isInstance(Object value, String className) {
        if (value == null || className == null) {
            return false;
        }
        Class<?> type = value.getClass();
        try {
            Class<?> expected = Class.forName(className, false, type.getClassLoader());
            return expected.isInstance(value);
        } catch (ClassNotFoundException | LinkageError ignored) {
            while (type != null) {
                if (className.equals(type.getName())) {
                    return true;
                }
                type = type.getSuperclass();
            }
            return false;
        }
    }

    private static ItemStack[] matchingStacks(Ingredient ingredient) {
        if (ingredient == null || INGREDIENT_MATCHING_STACKS == null) {
            return new ItemStack[0];
        }
        try {
            Object value = INGREDIENT_MATCHING_STACKS.invoke(ingredient);
            return value instanceof ItemStack[] ? (ItemStack[]) value : new ItemStack[0];
        } catch (Throwable throwable) {
            return new ItemStack[0];
        }
    }

    private static boolean isRemote(World world) {
        if (world == null || WORLD_IS_REMOTE == null) {
            return false;
        }
        try {
            return WORLD_IS_REMOTE.getBoolean(world);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Could not read World.isRemote; assuming server world", throwable);
            return false;
        }
    }

    private static int dimension(World world) {
        if (world == null || WORLD_PROVIDER == null || PROVIDER_GET_DIMENSION == null) {
            return Integer.MIN_VALUE;
        }
        try {
            Object provider = WORLD_PROVIDER.get(world);
            if (provider instanceof WorldProvider) {
                Object value = PROVIDER_GET_DIMENSION.invoke(provider);
                return value instanceof Integer ? (Integer) value : Integer.MIN_VALUE;
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Could not read world dimension; skipping diagnostic world", throwable);
        }
        return Integer.MIN_VALUE;
    }

    private static ItemStack emptyStack() {
        return EMPTY_STACK;
    }

    private static ItemStack findEmptyStack() {
        Field field = findFieldQuiet(ItemStack.class, "field_190927_a", "EMPTY");
        if (field != null) {
            try {
                Object value = field.get(null);
                if (value instanceof ItemStack) {
                    return (ItemStack) value;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            return new ItemStack((Item) null, 0, 0);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Could not create an empty ItemStack fallback", throwable);
            return null;
        }
    }

    private static Ingredient findEmptyIngredient() {
        Field field = findFieldQuiet(Ingredient.class, "field_193370_a", "EMPTY");
        if (field != null) {
            try {
                Object value = field.get(null);
                if (value instanceof Ingredient) {
                    return (Ingredient) value;
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static ItemStack copyStack(ItemStack stack) {
        if (stack == null || isEmptyStack(stack) || STACK_COPY == null) {
            return emptyStack();
        }
        try {
            Object value = STACK_COPY.invoke(stack);
            return value instanceof ItemStack ? (ItemStack) value : emptyStack();
        } catch (Throwable throwable) {
            return emptyStack();
        }
    }

    private static boolean isEmptyStack(ItemStack stack) {
        if (stack == null) {
            return true;
        }
        if (STACK_IS_EMPTY != null) {
            try {
                Object value = STACK_IS_EMPTY.invoke(stack);
                return value instanceof Boolean && (Boolean) value;
            } catch (Throwable ignored) {
            }
        }
        return EMPTY_STACK != null && stack == EMPTY_STACK;
    }

    private static int count(ItemStack stack) {
        if (stack == null || STACK_GET_COUNT == null) {
            return 0;
        }
        try {
            Object value = STACK_GET_COUNT.invoke(stack);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void setCount(ItemStack stack, int count) {
        if (stack == null || STACK_SET_COUNT == null) {
            return;
        }
        try {
            STACK_SET_COUNT.invoke(stack, count);
        } catch (Throwable ignored) {
        }
    }

    private static int metadata(ItemStack stack) {
        if (stack == null || STACK_GET_METADATA == null) {
            return 0;
        }
        try {
            Object value = STACK_GET_METADATA.invoke(stack);
            return value instanceof Integer ? (Integer) value : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Item item(ItemStack stack) {
        if (stack == null || STACK_GET_ITEM == null) {
            return null;
        }
        try {
            Object value = STACK_GET_ITEM.invoke(stack);
            return value instanceof Item ? (Item) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setInventorySlot(InventoryCrafting inventory, int slot, ItemStack stack) {
        if (inventory == null || INVENTORY_SET_SLOT == null) {
            return;
        }
        try {
            ItemStack value = stack == null ? emptyStack() : stack;
            if (value != null) {
                INVENTORY_SET_SLOT.invoke(inventory, slot, value);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setTag(NBTTagCompound tag, String key, NBTBase value) {
        if (tag == null || key == null || value == null || NBT_SET_TAG == null) {
            return;
        }
        try {
            NBT_SET_TAG.invoke(tag, key, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setBoolean(NBTTagCompound tag, String key, boolean value) {
        if (tag == null || key == null || NBT_SET_BOOLEAN == null) {
            return;
        }
        try {
            NBT_SET_BOOLEAN.invoke(tag, key, value);
        } catch (Throwable ignored) {
        }
    }

    private static void appendTag(NBTTagList list, NBTBase value) {
        if (list == null || value == null || NBT_LIST_APPEND_TAG == null) {
            return;
        }
        try {
            NBT_LIST_APPEND_TAG.invoke(list, value);
        } catch (Throwable ignored) {
        }
    }

    private static void setTagCompound(ItemStack stack, NBTTagCompound tag) {
        if (stack == null || tag == null || STACK_SET_TAG_COMPOUND == null) {
            return;
        }
        try {
            STACK_SET_TAG_COMPOUND.invoke(stack, tag);
        } catch (Throwable ignored) {
        }
    }

    private static Field findField(Class<?> owner, String... names) {
        Field field = findFieldQuiet(owner, names);
        if (field != null) {
            return field;
        }
        GPOM.LOGGER.warn("[GPOM AE2 PatternDiag] Could not find field {} on {}", java.util.Arrays.toString(names), owner.getName());
        return null;
    }

    private static Field findFieldQuiet(Class<?> owner, String... names) {
        for (String name : names) {
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> owner, String mcpName, String srgName, Class<?>... parameterTypes) {
        Method method = findMethod(owner, mcpName, parameterTypes);
        if (method != null) {
            return method;
        }
        return findMethod(owner, srgName, parameterTypes);
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
        }
        try {
            Method method = owner.getMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class Counters {
        private int scanned;
        private int valid;
        private int skipped;
        private int failures;
        private int mismatches;
        private int failureLogCandidates;
        private final Map<String, Integer> failuresByReason = new LinkedHashMap<>();
        private final Map<String, Integer> skipsByReason = new LinkedHashMap<>();

        private void skipped(String reason) {
            skipped++;
            increment(skipsByReason, reason);
        }

        private void failure(String reason) {
            failures++;
            increment(failuresByReason, reason);
        }

        private static void increment(Map<String, Integer> counts, String key) {
            Integer current = counts.get(key);
            counts.put(key, current == null ? 1 : current + 1);
        }
    }
}
