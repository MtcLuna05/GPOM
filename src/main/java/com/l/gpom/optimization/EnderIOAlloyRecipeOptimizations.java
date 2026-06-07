package com.l.gpom.optimization;

import com.enderio.core.common.util.NNList;
import com.l.gpom.core.TargetedModVersions;
import crazypants.enderio.base.recipe.IManyToOneRecipe;
import crazypants.enderio.base.recipe.IRecipe;
import crazypants.enderio.base.recipe.MachineRecipeInput;
import crazypants.enderio.base.recipe.RecipeLevel;
import crazypants.enderio.base.recipe.lookup.TriItemLookup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class EnderIOAlloyRecipeOptimizations {
    private static final boolean FAST_ALLOY_LOOKUP = Boolean.parseBoolean(System.getProperty("gpom.enderio.fastAlloyLookup", "true"));
    private static final int SIDE_LOOKUP_THRESHOLD = Integer.getInteger("gpom.enderio.fastAlloyLookup.threshold", 0);
    private static final Map<TriItemLookup<IManyToOneRecipe>, Set<IManyToOneRecipe>> SIDE_RECIPES = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Field lookupField;
    private static volatile Method itemMethod;
    private static volatile Method metaMethod;

    private EnderIOAlloyRecipeOptimizations() {
    }

    public static void addRecipeToLookup(TriItemLookup<IManyToOneRecipe> lookup, IManyToOneRecipe recipe) {
        if (!FAST_ALLOY_LOOKUP || lookup == null || recipe == null || !TargetedModVersions.isEnderIOClass("crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager")) {
            addRecipeToLookupVanilla(lookup, recipe);
            return;
        }

        NNList<List<ItemStack>> alternatives = recipe.getInputStackAlternatives();
        if (shouldSideStore(recipe, alternatives)) {
            sideRecipes(lookup).add(recipe);
            return;
        }

        addRecipeToLookupDeduped(lookup, recipe, alternatives);
    }

    public static IRecipe getRecipeForInputs(Object manager, RecipeLevel level, NNList<MachineRecipeInput> inputs) {
        TriItemLookup<IManyToOneRecipe> lookup = lookup(manager);
        IRecipe recipe = findRecipe(level, inputs, lookup == null ? null : lookup.getRecipesLMRI(inputs));
        if (recipe != null) {
            return recipe;
        }
        return findRecipe(level, inputs, sideRecipesOrNull(lookup));
    }

    public static boolean isValidInput(Object manager, RecipeLevel level, MachineRecipeInput input) {
        TriItemLookup<IManyToOneRecipe> lookup = lookup(manager);
        Item item = input == null ? null : itemOf(input.item);
        if (item != null && lookup != null && hasValidInput(level, input, lookup.getRecipes(item))) {
            return true;
        }
        return input != null && input.item != null && hasValidInput(level, input, sideRecipesOrNull(lookup));
    }

    public static boolean isValidRecipeComponents(Object manager, RecipeLevel level, NNList<ItemStack> components) {
        TriItemLookup<IManyToOneRecipe> lookup = lookup(manager);
        if (lookup != null && hasValidRecipeComponents(level, components, lookup.getRecipesL(components))) {
            return true;
        }
        return hasValidRecipeComponents(level, components, sideRecipesOrNull(lookup));
    }

    public static float getExperienceForOutput(Object manager, ItemStack output) {
        if (output == null) {
            return 0.0F;
        }
        Item outputItem = itemOf(output);
        if (outputItem == null) {
            return 0.0F;
        }
        NNList<IManyToOneRecipe> recipes = getRecipes(manager);
        for (IManyToOneRecipe recipe : recipes) {
            ItemStack recipeOutput = recipe.getOutput();
            if (itemOf(recipeOutput) == outputItem && metaOf(recipeOutput) == metaOf(output)) {
                return recipe.getOutputs()[0].getExperiance();
            }
        }
        return 0.0F;
    }

    public static NNList<IManyToOneRecipe> getRecipes(Object manager) {
        NNList<IManyToOneRecipe> result = new NNList<>();
        TriItemLookup<IManyToOneRecipe> lookup = lookup(manager);
        if (lookup != null) {
            Iterator<IManyToOneRecipe> iterator = lookup.iterator();
            while (iterator.hasNext()) {
                addIdentityOnce(result, iterator.next());
            }
            Iterable<IManyToOneRecipe> side = sideRecipesOrNull(lookup);
            if (side != null) {
                for (IManyToOneRecipe recipe : side) {
                    addIdentityOnce(result, recipe);
                }
            }
        }
        return result;
    }

    public static int rebuild(Object manager) {
        TriItemLookup<IManyToOneRecipe> rebuilt = new TriItemLookup<>();
        int count = 0;
        for (IManyToOneRecipe recipe : getRecipes(manager)) {
            addRecipeToLookup(rebuilt, recipe);
            count++;
        }
        setLookup(manager, rebuilt);
        return count;
    }

    private static boolean shouldSideStore(IManyToOneRecipe recipe, NNList<List<ItemStack>> alternatives) {
        return recipe.isSynthetic() && estimatedLookupRows(alternatives) > SIDE_LOOKUP_THRESHOLD;
    }

    private static long estimatedLookupRows(NNList<List<ItemStack>> alternatives) {
        if (alternatives == null) {
            return 0L;
        }
        int size = alternatives.size();
        if (size == 3) {
            return 6L * uniqueItemCount(alternatives.get(0)) * uniqueItemCount(alternatives.get(1)) * uniqueItemCount(alternatives.get(2));
        }
        if (size == 2) {
            return 2L * uniqueItemCount(alternatives.get(0)) * uniqueItemCount(alternatives.get(1));
        }
        if (size == 1) {
            return uniqueItemCount(alternatives.get(0));
        }
        return 0L;
    }

    private static int uniqueItemCount(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return 0;
        }
        Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack stack : stacks) {
            Item item = itemOf(stack);
            if (item != null) {
                seen.add(item);
            }
        }
        return seen.size();
    }

    private static void addRecipeToLookupDeduped(TriItemLookup<IManyToOneRecipe> lookup, IManyToOneRecipe recipe, NNList<List<ItemStack>> alternatives) {
        if (alternatives == null) {
            return;
        }
        if (alternatives.size() == 3) {
            List<Item> first = uniqueItems(alternatives.get(0));
            List<Item> second = uniqueItems(alternatives.get(1));
            List<Item> third = uniqueItems(alternatives.get(2));
            if (first.isEmpty() || second.isEmpty() || third.isEmpty()) {
                return;
            }
            for (Item a : first) {
                for (Item b : second) {
                    for (Item c : third) {
                        addAllPermutations(lookup, recipe, a, b, c);
                    }
                }
            }
        } else if (alternatives.size() == 2) {
            List<Item> first = uniqueItems(alternatives.get(0));
            List<Item> second = uniqueItems(alternatives.get(1));
            if (first.isEmpty() || second.isEmpty()) {
                return;
            }
            for (Item a : first) {
                for (Item b : second) {
                    lookup.addRecipe(recipe, a, b);
                    lookup.addRecipe(recipe, b, a);
                }
            }
        } else if (alternatives.size() == 1) {
            for (Item item : uniqueItems(alternatives.get(0))) {
                lookup.addRecipe(recipe, item);
            }
        }
    }

    private static void addRecipeToLookupVanilla(TriItemLookup<IManyToOneRecipe> lookup, IManyToOneRecipe recipe) {
        if (lookup == null || recipe == null) {
            return;
        }
        addRecipeToLookupDeduped(lookup, recipe, recipe.getInputStackAlternatives());
    }

    private static void addAllPermutations(TriItemLookup<IManyToOneRecipe> lookup, IManyToOneRecipe recipe, Item a, Item b, Item c) {
        lookup.addRecipe(recipe, a, b, c);
        lookup.addRecipe(recipe, a, c, b);
        lookup.addRecipe(recipe, b, a, c);
        lookup.addRecipe(recipe, b, c, a);
        lookup.addRecipe(recipe, c, a, b);
        lookup.addRecipe(recipe, c, b, a);
    }

    private static List<Item> uniqueItems(List<ItemStack> stacks) {
        List<Item> result = new ArrayList<>();
        if (stacks == null) {
            return result;
        }
        Set<Item> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack stack : stacks) {
            Item item = itemOf(stack);
            if (item != null && seen.add(item)) {
                result.add(item);
            }
        }
        return result;
    }

    private static IRecipe findRecipe(RecipeLevel level, NNList<MachineRecipeInput> inputs, Iterable<IManyToOneRecipe> recipes) {
        if (recipes == null) {
            return null;
        }
        for (IManyToOneRecipe recipe : recipes) {
            if (level.canMake(recipe.getRecipeLevel()) && recipe.isInputForRecipe(inputs)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean hasValidInput(RecipeLevel level, MachineRecipeInput input, Iterable<IManyToOneRecipe> recipes) {
        if (recipes == null) {
            return false;
        }
        for (IManyToOneRecipe recipe : recipes) {
            if (!level.canMake(recipe.getRecipeLevel())) {
                continue;
            }
            for (crazypants.enderio.base.recipe.IRecipeInput recipeInput : recipe.getInputs()) {
                if (recipeInput.isInput(input.item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasValidRecipeComponents(RecipeLevel level, NNList<ItemStack> components, Iterable<IManyToOneRecipe> recipes) {
        if (recipes == null) {
            return false;
        }
        for (IManyToOneRecipe recipe : recipes) {
            if (level.canMake(recipe.getRecipeLevel()) && recipe.isValidRecipeComponents(components)) {
                return true;
            }
        }
        return false;
    }

    private static void addIdentityOnce(NNList<IManyToOneRecipe> recipes, IManyToOneRecipe recipe) {
        for (IManyToOneRecipe existing : recipes) {
            if (existing == recipe) {
                return;
            }
        }
        recipes.add(recipe);
    }

    private static Set<IManyToOneRecipe> sideRecipes(TriItemLookup<IManyToOneRecipe> lookup) {
        synchronized (SIDE_RECIPES) {
            return SIDE_RECIPES.computeIfAbsent(lookup, key -> Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    private static Iterable<IManyToOneRecipe> sideRecipesOrNull(TriItemLookup<IManyToOneRecipe> lookup) {
        if (lookup == null) {
            return null;
        }
        synchronized (SIDE_RECIPES) {
            return SIDE_RECIPES.get(lookup);
        }
    }

    @SuppressWarnings("unchecked")
    private static TriItemLookup<IManyToOneRecipe> lookup(Object manager) {
        if (manager == null) {
            return null;
        }
        try {
            Field field = lookupField;
            if (field == null) {
                field = manager.getClass().getDeclaredField("lookup");
                field.setAccessible(true);
                lookupField = field;
            }
            return (TriItemLookup<IManyToOneRecipe>) field.get(manager);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setLookup(Object manager, TriItemLookup<IManyToOneRecipe> lookup) {
        if (manager == null) {
            return;
        }
        try {
            Field field = lookupField;
            if (field == null) {
                field = manager.getClass().getDeclaredField("lookup");
                field.setAccessible(true);
                lookupField = field;
            }
            field.set(manager, lookup);
        } catch (Throwable ignored) {
            // Leave the old lookup in place if the exact-version reflective field changes.
        }
    }

    private static Item itemOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        try {
            Method method = itemMethod;
            if (method == null) {
                method = resolveMethod(ItemStack.class, "getItem", "func_77973_b");
                itemMethod = method;
            }
            Object item = method.invoke(stack);
            return item instanceof Item ? (Item) item : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int metaOf(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        try {
            Method method = metaMethod;
            if (method == null) {
                method = resolveMethod(ItemStack.class, "getMetadata", "getItemDamage", "func_77952_i");
                metaMethod = method;
            }
            Object meta = method.invoke(stack);
            return meta instanceof Number ? ((Number) meta).intValue() : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static Method resolveMethod(Class<?> owner, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                Method method = owner.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                // Try the next runtime name.
            }
        }
        throw new NoSuchMethodException(owner.getName() + '.' + java.util.Arrays.toString(names));
    }
}
