package com.l.gpom.compat.hei;

import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.BlankRecipeWrapper;
import mezz.jei.api.recipe.wrapper.ICraftingRecipeWrapper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class SalisMundusRecipeWrapper extends BlankRecipeWrapper implements ICraftingRecipeWrapper {
    private static final ResourceLocation REGISTRY_NAME = new ResourceLocation("gpom", "thaumcraft_salis_mundus_info");
    private static final String[] ASPECTS = {"aer", "terra", "ignis", "aqua", "ordo", "perditio"};

    private static volatile Method setIntegerMethod;
    private static volatile Method setStringMethod;
    private static volatile Method setTagMethod;
    private static volatile Method appendTagMethod;
    private static volatile Method setStackTagCompoundMethod;

    private final List<List<ItemStack>> inputs;
    private final ItemStack output;

    private SalisMundusRecipeWrapper(List<List<ItemStack>> inputs, ItemStack output) {
        this.inputs = inputs;
        this.output = output;
    }

    static SalisMundusRecipeWrapper createOrNull() {
        Item bowl = item("minecraft:bowl");
        Item flint = item("minecraft:flint");
        Item redstone = item("minecraft:redstone");
        Item crystal = item("thaumcraft:crystal_essence");
        Item salisMundus = item("thaumcraft:salis_mundus");
        if (bowl == null || flint == null || redstone == null || crystal == null || salisMundus == null) {
            return null;
        }

        List<ItemStack> crystalChoices = new ArrayList<>(ASPECTS.length);
        for (String aspect : ASPECTS) {
            ItemStack stack = crystalStack(crystal, aspect);
            if (stack == null) {
                return null;
            }
            crystalChoices.add(stack);
        }
        List<List<ItemStack>> inputs = new ArrayList<>(6);
        inputs.add(single(new ItemStack(bowl)));
        inputs.add(single(new ItemStack(flint)));
        inputs.add(single(new ItemStack(redstone)));
        inputs.add(crystalChoices);
        inputs.add(crystalChoices);
        inputs.add(crystalChoices);
        return new SalisMundusRecipeWrapper(inputs, new ItemStack(salisMundus));
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInputLists(VanillaTypes.ITEM, inputs);
        ingredients.setOutput(VanillaTypes.ITEM, output);
    }

    @Override
    public ResourceLocation getRegistryName() {
        return REGISTRY_NAME;
    }

    @Override
    public List<String> getTooltipStrings(int mouseX, int mouseY) {
        return Collections.emptyList();
    }

    private static List<ItemStack> single(ItemStack stack) {
        return Collections.singletonList(stack);
    }

    private static Item item(String name) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(name));
    }

    private static ItemStack crystalStack(Item item, String aspect) {
        try {
            ItemStack stack = new ItemStack(item);
            NBTTagCompound root = new NBTTagCompound();
            NBTTagList aspects = new NBTTagList();
            NBTTagCompound entry = new NBTTagCompound();
            setInteger(entry, "amount", 1);
            setString(entry, "key", aspect);
            appendTag(aspects, entry);
            setTag(root, "Aspects", aspects);
            setStackTagCompound(stack, root);
            return stack;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setInteger(NBTTagCompound compound, String key, int value) throws Exception {
        Method method = setIntegerMethod;
        if (method == null) {
            method = findMethod(compound.getClass(), new Class<?>[]{String.class, int.class}, "func_74768_a", "setInteger");
            method.setAccessible(true);
            setIntegerMethod = method;
        }
        method.invoke(compound, key, value);
    }

    private static void setString(NBTTagCompound compound, String key, String value) throws Exception {
        Method method = setStringMethod;
        if (method == null) {
            method = findMethod(compound.getClass(), new Class<?>[]{String.class, String.class}, "func_74778_a", "setString");
            method.setAccessible(true);
            setStringMethod = method;
        }
        method.invoke(compound, key, value);
    }

    private static void setTag(NBTTagCompound compound, String key, NBTBase value) throws Exception {
        Method method = setTagMethod;
        if (method == null) {
            method = findMethod(compound.getClass(), new Class<?>[]{String.class, NBTBase.class}, "func_74782_a", "setTag");
            method.setAccessible(true);
            setTagMethod = method;
        }
        method.invoke(compound, key, value);
    }

    private static void appendTag(NBTTagList list, NBTBase value) throws Exception {
        Method method = appendTagMethod;
        if (method == null) {
            method = findMethod(list.getClass(), new Class<?>[]{NBTBase.class}, "func_74742_a", "appendTag");
            method.setAccessible(true);
            appendTagMethod = method;
        }
        method.invoke(list, value);
    }

    private static void setStackTagCompound(ItemStack stack, NBTTagCompound tag) throws Exception {
        Method method = setStackTagCompoundMethod;
        if (method == null) {
            method = findMethod(stack.getClass(), new Class<?>[]{NBTTagCompound.class}, "func_77982_d", "setTagCompound");
            method.setAccessible(true);
            setStackTagCompoundMethod = method;
        }
        method.invoke(stack, tag);
    }

    private static Method findMethod(Class<?> type, Class<?>[] parameterTypes, String... names) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            for (String name : names) {
                try {
                    return current.getDeclaredMethod(name, parameterTypes);
                } catch (NoSuchMethodException ignored) {
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(type.getName() + "." + String.join("/", names));
    }
}
