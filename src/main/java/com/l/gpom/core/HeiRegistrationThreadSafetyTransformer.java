package com.l.gpom.core;

import com.l.gpom.GPOM;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class HeiRegistrationThreadSafetyTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.hei.registrationThreadSafety.enabled",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!isTarget(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);

            int changed = 0;
            for (MethodNode method : node.methods) {
                if (shouldSynchronize(className, method) && markSynchronized(method)) {
                    changed++;
                }
            }

            if (changed <= 0) {
                return basicClass;
            }

            GPOM.LOGGER.info("[HEI Optimizations] Serialized {} method(s) on {}", changed, className);
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HEI Optimizations] Failed to install HEI registration thread-safety patch on {}; continuing with original bytecode", className, throwable);
            return basicClass;
        }
    }

    private static boolean isTarget(String className) {
        return "mezz.jei.ingredients.IngredientBlacklistInternal".equals(className)
                || "mezz.jei.ingredients.IngredientBlacklist".equals(className)
                || "mezz.jei.ingredients.ItemBlacklist".equals(className)
                || "mezz.jei.startup.ModRegistry".equals(className)
                || "mezz.jei.recipes.RecipeTransferRegistry".equals(className);
    }

    private static boolean shouldSynchronize(String className, MethodNode method) {
        if (method.name.startsWith("<") || (method.access & Opcodes.ACC_STATIC) != 0) {
            return false;
        }
        if (!isPublic(method)) {
            return false;
        }
        if ("mezz.jei.startup.ModRegistry".equals(className)) {
            return isModRegistryRegistrationMethod(method.name);
        }
        if ("mezz.jei.recipes.RecipeTransferRegistry".equals(className)) {
            return isRecipeTransferRegistryMethod(method.name);
        }
        return isBlacklistMethod(method.name);
    }

    private static boolean isModRegistryRegistrationMethod(String name) {
        return "addRecipeCategories".equals(name)
                || "addRecipeHandlers".equals(name)
                || "addRecipes".equals(name)
                || "handleRecipes".equals(name)
                || "addRecipeClickArea".equals(name)
                || "addRecipeCatalyst".equals(name)
                || "addRecipeCategoryCraftingItem".equals(name)
                || "addAdvancedGuiHandlers".equals(name)
                || "addGlobalGuiHandlers".equals(name)
                || "addGuiScreenHandler".equals(name)
                || "addGhostIngredientHandler".equals(name)
                || "addDescription".equals(name)
                || "addIngredientInfo".equals(name)
                || "addAnvilRecipe".equals(name)
                || "getRecipeTransferRegistry".equals(name)
                || "addRecipeRegistryPlugin".equals(name);
    }

    private static boolean isRecipeTransferRegistryMethod(String name) {
        return "addRecipeTransferHandler".equals(name)
                || "addUniversalRecipeTransferHandler".equals(name)
                || "getRecipeTransferHandlers".equals(name);
    }

    private static boolean isBlacklistMethod(String name) {
        return "addIngredientToBlacklist".equals(name)
                || "removeIngredientFromBlacklist".equals(name)
                || "isIngredientBlacklisted".equals(name)
                || "isIngredientBlacklistedByApi".equals(name)
                || "addItemToBlacklist".equals(name)
                || "removeItemFromBlacklist".equals(name)
                || "isItemBlacklisted".equals(name);
    }

    private static boolean isPublic(MethodNode method) {
        return (method.access & Opcodes.ACC_PUBLIC) != 0;
    }

    private static boolean markSynchronized(MethodNode method) {
        if ((method.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
            return false;
        }
        method.access |= Opcodes.ACC_SYNCHRONIZED;
        return true;
    }
}
