package com.l.gpom.optimization;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AbyssalCraftNecronomiconOptimizations {
    private static final ResourceLocation MISSING_ICON = new ResourceLocation(
            "abyssalcraft",
            "textures/gui/necronomicon/missing.png"
    );
    private static final Map<String, RecipeSnapshot> CRAFTING_STACK_RECIPE_CACHE = new ConcurrentHashMap<String, RecipeSnapshot>();
    private static volatile boolean craftingStackRecipeCacheBuilt;
    private static volatile Method getInternalHandlerMethod;
    private static volatile Method verifyImageUrlMethod;
    private static volatile boolean reflectionUnavailable;

    private AbyssalCraftNecronomiconOptimizations() {
    }

    public static Object verifyIconLazy(Object icon) {
        if (icon instanceof String) {
            verifyImageUrl((String) icon);
            return icon;
        }

        if (icon instanceof ResourceLocation) {
            ResourceLocation location = (ResourceLocation) icon;
            if (MISSING_ICON.equals(location)) {
                return icon;
            }
            // Vanilla's resource pipeline already has missing-texture handling. Avoid
            // decoding every Necronomicon icon during init just to preflight it.
            return icon;
        }

        return icon;
    }

    public static ItemStack convertToStack(Object value) {
        if (value == null) {
            return ItemStack.EMPTY;
        }
        if (value instanceof ItemStack) {
            return copyStack((ItemStack) value);
        }
        if (value instanceof Item) {
            return new ItemStack((Item) value);
        }
        if (value instanceof Block) {
            return new ItemStack((Block) value);
        }
        if (value instanceof Ingredient) {
            ItemStack[] stacks = matchingStacks((Ingredient) value);
            return stacks.length > 0 ? copyStack(stacks[0]) : ItemStack.EMPTY;
        }
        if (value instanceof ItemStack[]) {
            ItemStack[] stacks = (ItemStack[]) value;
            return stacks.length > 0 ? copyStack(stacks[0]) : ItemStack.EMPTY;
        }
        if (value instanceof String) {
            NonNullList<ItemStack> stacks = OreDictionary.getOres((String) value);
            return stacks.isEmpty() ? ItemStack.EMPTY : copyStack(stacks.get(0));
        }
        if (value instanceof List) {
            List<?> stacks = (List<?>) value;
            if (!stacks.isEmpty() && stacks.get(0) instanceof ItemStack) {
                return copyStack((ItemStack) stacks.get(0));
            }
            return ItemStack.EMPTY;
        }
        throw new ClassCastException("Not a Item, Block, ItemStack, Ingredient, Array of ItemStacks, String or List of ItemStacks!");
    }

    public static Object[] recipeForOutput(ItemStack output) {
        if (output == null || stackEmpty(output)) {
            return new Object[9];
        }
        ensureRecipeCacheBuilt();
        RecipeSnapshot snapshot = CRAFTING_STACK_RECIPE_CACHE.get(recipeCacheKey(output));
        if (snapshot == null) {
            return new Object[9];
        }
        setStackCount(output, snapshot.outputCount);
        return snapshot.recipe.clone();
    }

    private static void ensureRecipeCacheBuilt() {
        if (craftingStackRecipeCacheBuilt) {
            return;
        }
        synchronized (CRAFTING_STACK_RECIPE_CACHE) {
            if (craftingStackRecipeCacheBuilt) {
                return;
            }
            for (IRecipe candidate : ForgeRegistries.RECIPES) {
                ItemStack candidateOutput = recipeOutput(candidate);
                if (candidateOutput == null || stackEmpty(candidateOutput)) {
                    continue;
                }

                Object[] ingredients = copyIngredients(recipeIngredients(candidate));
                if (recipeFootprint(candidate) == 4) {
                    ingredients = shiftTwoByTwoToTopLeft(ingredients);
                }
                CRAFTING_STACK_RECIPE_CACHE.put(recipeCacheKey(candidateOutput), new RecipeSnapshot(ingredients, stackCount(candidateOutput)));
            }
            craftingStackRecipeCacheBuilt = true;
        }
    }

    private static String recipeCacheKey(ItemStack stack) {
        Object item = stackItem(stack);
        ResourceLocation name = item instanceof Item ? ((Item) item).getRegistryName() : null;
        return String.valueOf(name) + ':' + stackMeta(stack);
    }

    private static Object[] copyIngredients(NonNullList<Ingredient> ingredients) {
        Object[] copied = new Object[9];
        if (ingredients == null) {
            return copied;
        }
        int limit = Math.min(copied.length, ingredients.size());
        for (int i = 0; i < limit; i++) {
            copied[i] = ingredients.get(i);
        }
        return copied;
    }

    private static Object[] shiftTwoByTwoToTopLeft(Object[] ingredients) {
        Object[] shifted = new Object[9];
        shifted[0] = ingredients[0];
        shifted[1] = ingredients[1];
        shifted[3] = ingredients[2];
        shifted[4] = ingredients[3];
        return shifted;
    }

    private static int recipeFootprint(IRecipe recipe) {
        Class<?> type = recipe.getClass();
        if (isInstanceOf(type, "net.minecraft.item.crafting.ShapedRecipes")) {
            int width = intField(recipe, type, "field_77577_c", "recipeWidth");
            int height = intField(recipe, type, "field_77576_b", "recipeHeight");
            return width > 0 && height > 0 ? width * height : -1;
        }
        if (isInstanceOf(type, "net.minecraftforge.oredict.ShapedOreRecipe")) {
            int width = intMethod(recipe, type, "getRecipeWidth");
            int height = intMethod(recipe, type, "getRecipeHeight");
            return width > 0 && height > 0 ? width * height : -1;
        }
        return -1;
    }

    private static boolean isInstanceOf(Class<?> type, String className) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            if (className.equals(current.getName())) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack recipeOutput(IRecipe recipe) {
        Object value = invokeFirst(recipe, "func_77571_b", "getRecipeOutput");
        return value instanceof ItemStack ? (ItemStack) value : ItemStack.EMPTY;
    }

    @SuppressWarnings("unchecked")
    private static NonNullList<Ingredient> recipeIngredients(IRecipe recipe) {
        Object value = invokeFirst(recipe, "func_192400_c", "getIngredients");
        return value instanceof NonNullList ? (NonNullList<Ingredient>) value : null;
    }

    private static ItemStack[] matchingStacks(Ingredient ingredient) {
        Object value = invokeFirst(ingredient, "func_193365_a", "getMatchingStacks");
        return value instanceof ItemStack[] ? (ItemStack[]) value : new ItemStack[0];
    }

    private static ItemStack copyStack(ItemStack stack) {
        Object value = invokeFirst(stack, "func_77946_l", "copy");
        return value instanceof ItemStack ? (ItemStack) value : ItemStack.EMPTY;
    }

    private static boolean stackEmpty(ItemStack stack) {
        Object value = invokeFirst(stack, "func_190926_b", "isEmpty");
        return value instanceof Boolean ? (Boolean) value : stack == ItemStack.EMPTY;
    }

    private static int stackCount(ItemStack stack) {
        Object value = invokeFirst(stack, "func_190916_E", "getCount");
        return value instanceof Number ? ((Number) value).intValue() : 1;
    }

    private static void setStackCount(ItemStack stack, int count) {
        invokeFirst(stack, new Class<?>[]{Integer.TYPE}, new Object[]{count}, "func_190920_e", "setCount");
    }

    private static int stackMeta(ItemStack stack) {
        Object value = invokeFirst(stack, "func_77960_j", "getMetadata");
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static Object stackItem(ItemStack stack) {
        return invokeFirst(stack, "func_77973_b", "getItem");
    }

    private static Object invokeFirst(Object target, String... names) {
        return invokeFirst(target, new Class<?>[0], new Object[0], names);
    }

    private static Object invokeFirst(Object target, Class<?>[] parameterTypes, Object[] args, String... names) {
        if (target == null) {
            return null;
        }
        Class<?> type = target.getClass();
        for (String name : names) {
            try {
                Method method = type.getMethod(name, parameterTypes);
                method.setAccessible(true);
                return method.invoke(target, args);
            } catch (Throwable ignored) {
                // Try declared methods and alternate mapping names.
            }
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Method method = current.getDeclaredMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (Throwable ignored) {
                    // Try interface, alternate mapping name, or superclass.
                }
            }
            Class<?>[] interfaces = type.getInterfaces();
            for (Class<?> iface : interfaces) {
                try {
                    Method method = iface.getMethod(name, parameterTypes);
                    method.setAccessible(true);
                    return method.invoke(target, args);
                } catch (Throwable ignored) {
                    // Try the next name.
                }
            }
        }
        return null;
    }

    private static int intField(Object target, Class<?> type, String... names) {
        for (String name : names) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                try {
                    Field field = current.getDeclaredField(name);
                    field.setAccessible(true);
                    return field.getInt(target);
                } catch (Throwable ignored) {
                    // Try the next MCP/SRG field name or superclass.
                }
            }
        }
        return -1;
    }

    private static int intMethod(Object target, Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method method = current.getDeclaredMethod(name);
                method.setAccessible(true);
                return ((Number) method.invoke(target)).intValue();
            } catch (Throwable ignored) {
                // Try the superclass.
            }
        }
        return -1;
    }

    private static void verifyImageUrl(String url) {
        if (reflectionUnavailable) {
            return;
        }
        try {
            Method getHandler = getInternalHandlerMethod;
            if (getHandler == null) {
                Class<?> api = Class.forName("com.shinoow.abyssalcraft.api.AbyssalCraftAPI");
                getHandler = api.getMethod("getInternalNDHandler");
                getInternalHandlerMethod = getHandler;
            }
            Object handler = getHandler.invoke(null);
            if (handler == null) {
                return;
            }
            Method verifier = verifyImageUrlMethod;
            if (verifier == null) {
                verifier = handler.getClass().getMethod("verifyImageURL", String.class);
                verifyImageUrlMethod = verifier;
            }
            verifier.invoke(handler, url);
        } catch (Throwable ignored) {
            reflectionUnavailable = true;
        }
    }

    private static final class RecipeSnapshot {
        private final Object[] recipe;
        private final int outputCount;

        private RecipeSnapshot(Object[] recipe, int outputCount) {
            this.recipe = recipe;
            this.outputCount = outputCount;
        }
    }
}
