package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class HeiStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.heiProfiler", "true"));
    private static final boolean ASYNC_TOOLTIP_SEARCH = Boolean.parseBoolean(System.getProperty("gpom.hei.asyncTooltipSearch", "false"));
    private static final boolean PARALLEL_SEARCH_BUILD = Boolean.parseBoolean(System.getProperty("gpom.hei.parallelSearchBuild", "false"));
    private static final boolean SKIP_RECIPE_PROGRESS = Boolean.parseBoolean(System.getProperty("gpom.hei.skipRecipeProgress", "true"));
    private static final boolean SKIP_SEARCH_PROGRESS = Boolean.parseBoolean(System.getProperty("gpom.hei.skipSearchProgress", "true"));
    private static final boolean SKIP_PLUGIN_PROGRESS = Boolean.parseBoolean(System.getProperty("gpom.hei.skipPluginProgress", "false"));
    private static final boolean DISABLE_TOOLTIP_SEARCH_INDEX = Boolean.parseBoolean(System.getProperty("gpom.hei.disableTooltipSearchIndex", "true"));
    private static final boolean FAST_JER_ENCHANTMENTS = Boolean.parseBoolean(System.getProperty("gpom.hei.fastJerEnchantments", "true"));
    private static final boolean FAST_JER_VILLAGERS = Boolean.parseBoolean(System.getProperty("gpom.hei.fastJerVillagers", "true"));
    private static final boolean FAST_JER_LOOT_REFLECTION = Boolean.parseBoolean(System.getProperty("gpom.hei.fastJerLootReflection", "true"));
    private static final boolean FAST_HEI_FALLBACK_SUBTYPES = Boolean.parseBoolean(System.getProperty("gpom.hei.fastFallbackSubtypes", "true"));
    private static final boolean PLUGIN_PROFILER = Boolean.parseBoolean(System.getProperty("gpom.hei.pluginProfiler", "true"));
    private static final Map<String, Set<MethodKey>> TARGETS = createTargets();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null || (className != null && className.startsWith("com.l.gpom."))) {
            return basicClass;
        }
        boolean supportedHeiClass = className != null && className.startsWith("mezz.jei.") && TargetedModVersions.isHadEnoughItemsClass(className);
        boolean supportedJerClass = className != null && className.startsWith("jeresources.") && TargetedModVersions.isJustEnoughResourcesClass(className);
        boolean heiAvailable = TargetedModVersions.isHadEnoughItemsClass("mezz.jei.startup.JeiStarter");

        if (!supportedHeiClass && !supportedJerClass && !heiAvailable) {
            return basicClass;
        }

        if (supportedHeiClass && "mezz.jei.search.PrefixInfo".equals(className) && ASYNC_TOOLTIP_SEARCH) {
            return patchTooltipPrefixAsync(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.search.AsyncPrefixedSearchable".equals(className) && PARALLEL_SEARCH_BUILD) {
            return patchAsyncSearchExecutor(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.recipes.RecipeRegistry".equals(className) && SKIP_RECIPE_PROGRESS) {
            basicClass = patchRecipeRegistrySkipProgress(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.search.PrefixedSearchable".equals(className) && SKIP_SEARCH_PROGRESS) {
            basicClass = patchPrefixedSearchableSkipProgress(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.config.Config".equals(className) && DISABLE_TOOLTIP_SEARCH_INDEX) {
            basicClass = patchConfigDisableTooltipSearchIndex(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.startup.JeiStarter".equals(className) && SKIP_PLUGIN_PROGRESS) {
            basicClass = patchJeiStarterSkipPluginProgress(basicClass);
        }
        if (supportedJerClass && "jeresources.jei.enchantment.EnchantmentMaker".equals(className) && FAST_JER_ENCHANTMENTS) {
            basicClass = patchJerEnchantmentMaker(basicClass);
        }
        if (supportedJerClass && "jeresources.util.VillagersHelper".equals(className) && FAST_JER_VILLAGERS) {
            basicClass = patchJerVillagersHelper(basicClass);
        }
        if (supportedJerClass && "jeresources.util.LootTableHelper".equals(className) && FAST_JER_LOOT_REFLECTION) {
            basicClass = patchJerLootTableHelper(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory".equals(className) && FAST_HEI_FALLBACK_SUBTYPES) {
            basicClass = patchHeiItemStackListFactory(basicClass);
        }

        Set<MethodKey> methods = TARGETS.get(name);
        if (methods == null) {
            methods = TARGETS.get(className);
        }

        if (methods != null && !supportedHeiClass && !supportedJerClass) {
            methods = null;
        }

        boolean possibleJeiPlugin = PLUGIN_PROFILER && heiAvailable && containsAscii(basicClass, "mezz/jei/api/IModPlugin");
        if (methods == null && !possibleJeiPlugin) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            boolean plugin = possibleJeiPlugin && implementsJeiPlugin(reader);

            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new HeiClassVisitor(writer, className, methods, plugin), 0);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchTooltipPrefixAsync(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"<clinit>".equals(method.name)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode && "tooltip".equals(((LdcInsnNode) insn).cst)) {
                        AbstractInsnNode cursor = insn.getPrevious();
                        while (cursor != null) {
                            if (cursor.getOpcode() == Opcodes.ICONST_0) {
                                method.instructions.set(cursor, new InsnNode(Opcodes.ICONST_1));
                                return writeNode(node);
                            }
                            cursor = cursor.getPrevious();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchAsyncSearchExecutor(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"startService".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.INVOKESTATIC) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if ("java/util/concurrent/Executors".equals(methodInsn.owner)
                                && "newSingleThreadExecutor".equals(methodInsn.name)
                                && "()Ljava/util/concurrent/ExecutorService;".equals(methodInsn.desc)) {
                            method.instructions.insertBefore(insn, new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "com/l/gpom/optimization/HeiOptimizations",
                                    "searchWorkerCount",
                                    "()I",
                                    false
                            ));
                            methodInsn.name = "newFixedThreadPool";
                            methodInsn.desc = "(I)Ljava/util/concurrent/ExecutorService;";
                            return writeNode(node);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchRecipeRegistrySkipProgress(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"addRecipes".equals(method.name)
                        || !"(Ljava/util/List;Lmezz/jei/collect/ListMultiMap;)V".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("mezz/jei/config/Config".equals(methodInsn.owner)
                            && "skipShowingProgressBar".equals(methodInsn.name)
                            && "()Z".equals(methodInsn.desc)) {
                        method.instructions.set(insn, new InsnNode(Opcodes.ICONST_1));
                        return writeNode(node);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchPrefixedSearchableSkipProgress(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"submitAll".equals(method.name)
                        || !"(Lnet/minecraft/util/NonNullList;)V".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.GETSTATIC) {
                        continue;
                    }
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if ("mezz/jei/ingredients/IngredientFilter".equals(fieldInsn.owner)
                            && "rebuild".equals(fieldInsn.name)
                            && "Z".equals(fieldInsn.desc)) {
                        method.instructions.set(insn, new InsnNode(Opcodes.ICONST_1));
                        return writeNode(node);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchConfigDisableTooltipSearchIndex(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"getTooltipSearchMode".equals(method.name)
                        || !"()Lmezz/jei/config/Config$SearchMode;".equals(method.desc)) {
                    continue;
                }
                method.instructions.clear();
                method.instructions.add(new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "mezz/jei/config/Config$SearchMode",
                        "DISABLED",
                        "Lmezz/jei/config/Config$SearchMode;"
                ));
                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchJeiStarterSkipPluginProgress(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            Set<String> pluginLifecycleMethods = new HashSet<>();
            pluginLifecycleMethods.add("registerItemSubtypes");
            pluginLifecycleMethods.add("registerIngredients");
            pluginLifecycleMethods.add("registerCategories");
            pluginLifecycleMethods.add("registerPlugins");
            pluginLifecycleMethods.add("sendRuntime");

            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!pluginLifecycleMethods.contains(method.name)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("mezz/jei/config/Config".equals(methodInsn.owner)
                            && "skipShowingProgressBar".equals(methodInsn.name)
                            && "()Z".equals(methodInsn.desc)) {
                        method.instructions.set(insn, new InsnNode(Opcodes.ICONST_1));
                        changed = true;
                    }
                }
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchJerEnchantmentMaker(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"createRecipes".equals(method.name) || !"(Ljava/util/Collection;)Ljava/util/List;".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("jeresources/jei/enchantment/EnchantmentWrapper".equals(methodInsn.owner)
                            && "create".equals(methodInsn.name)
                            && "(Lnet/minecraft/item/ItemStack;)Ljeresources/jei/enchantment/EnchantmentWrapper;".equals(methodInsn.desc)) {
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "createJerEnchantmentWrapper";
                        methodInsn.desc = "(Lnet/minecraft/item/ItemStack;)Ljava/lang/Object;";
                        return writeNode(node);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchJerVillagersHelper(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"initRegistry".equals(method.name)
                        || !"(Ljeresources/registry/VillagerRegistry;)V".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (!"jeresources/util/VillagersHelper".equals(methodInsn.owner)) {
                        continue;
                    }
                    if ("getCareers".equals(methodInsn.name)
                            && "(Lnet/minecraftforge/fml/common/registry/VillagerRegistry$VillagerProfession;)Ljava/util/List;".equals(methodInsn.desc)) {
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "fastJerVillagerCareers";
                        methodInsn.desc = "(Ljava/lang/Object;)Ljava/util/List;";
                        changed = true;
                    } else if ("getTrades".equals(methodInsn.name)
                            && "(Lnet/minecraftforge/fml/common/registry/VillagerRegistry$VillagerCareer;)Ljava/util/List;".equals(methodInsn.desc)) {
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "fastJerVillagerTrades";
                        methodInsn.desc = "(Ljava/lang/Object;)Ljava/util/List;";
                        changed = true;
                    } else if ("getId".equals(methodInsn.name)
                            && "(Lnet/minecraftforge/fml/common/registry/VillagerRegistry$VillagerCareer;)I".equals(methodInsn.desc)) {
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "fastJerVillagerCareerId";
                        methodInsn.desc = "(Ljava/lang/Object;)I";
                        changed = true;
                    }
                }
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchJerLootTableHelper(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKESTATIC) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("jeresources/util/ReflectionHelper".equals(methodInsn.owner)
                            && "getPrivateValue".equals(methodInsn.name)
                            && "(Ljava/lang/Class;Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;".equals(methodInsn.desc)) {
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "fastJerPrivateValue";
                        changed = true;
                    }
                }
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchHeiItemStackListFactory(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            for (MethodNode method : node.methods) {
                if (!"addFallbackSubtypeInterpreter".equals(method.name)
                        || !"(Lnet/minecraft/item/ItemStack;)V".equals(method.desc)) {
                    continue;
                }

                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
                InsnList replacement = method.instructions;
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new FieldInsnNode(
                        Opcodes.GETFIELD,
                        "mezz/jei/plugins/vanilla/ingredients/item/ItemStackListFactory",
                        "subtypeRegistry",
                        "Lmezz/jei/api/ISubtypeRegistry;"
                ));
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "fastHeiFallbackSubtypeInterpreter",
                        "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;)V",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.RETURN));
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static ClassNode readNode(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        return node;
    }

    private static byte[] writeNode(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static boolean implementsJeiPlugin(ClassReader reader) {
        for (String iface : reader.getInterfaces()) {
            if ("mezz/jei/api/IModPlugin".equals(iface)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAscii(byte[] bytes, String needle) {
        if (bytes == null || needle == null || needle.isEmpty() || bytes.length < needle.length()) {
            return false;
        }
        int limit = bytes.length - needle.length();
        int first = needle.charAt(0);
        for (int i = 0; i <= limit; i++) {
            if ((bytes[i] & 0xFF) != first) {
                continue;
            }
            int j = 1;
            while (j < needle.length() && (bytes[i + j] & 0xFF) == needle.charAt(j)) {
                j++;
            }
            if (j == needle.length()) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Set<MethodKey>> createTargets() {
        Map<String, Set<MethodKey>> targets = new HashMap<>();
        add(targets, "mezz.jei.startup.ProxyCommonClient", "loadComplete", "(Lnet/minecraftforge/fml/common/event/FMLLoadCompleteEvent;)V");
        add(targets, "mezz.jei.startup.ProxyCommonClient", "reloadItemList", "()V");

        add(targets, "mezz.jei.startup.JeiStarter", "start", "(Ljava/util/List;Lmezz/jei/gui/textures/Textures;)V");
        add(targets, "mezz.jei.startup.JeiStarter", "load", "(Ljava/util/List;Lmezz/jei/gui/textures/Textures;Z)V");
        add(targets, "mezz.jei.startup.JeiStarter", "registerItemSubtypes", "(Ljava/util/List;Lmezz/jei/runtime/SubtypeRegistry;)V");
        add(targets, "mezz.jei.startup.JeiStarter", "registerIngredients", "(Ljava/util/List;)Lmezz/jei/startup/ModIngredientRegistration;");
        add(targets, "mezz.jei.startup.JeiStarter", "registerCategories", "(Ljava/util/List;Lmezz/jei/startup/ModRegistry;)V");
        add(targets, "mezz.jei.startup.JeiStarter", "registerPlugins", "(Ljava/util/List;Lmezz/jei/startup/ModRegistry;)V");
        add(targets, "mezz.jei.startup.JeiStarter", "sendRuntime", "(Ljava/util/List;Lmezz/jei/api/IJeiRuntime;)V");

        add(targets, "mezz.jei.startup.ModRegistry", "createRecipeRegistry", "(Lmezz/jei/ingredients/IngredientRegistry;)Lmezz/jei/recipes/RecipeRegistry;");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipes", "(Ljava/util/Collection;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipes", "(Ljava/util/Collection;Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "handleRecipes", "(Ljava/lang/Class;Lmezz/jei/api/recipe/IRecipeWrapperFactory;Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipeCategories", "([Lmezz/jei/api/recipe/IRecipeCategory;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipeHandlers", "([Lmezz/jei/api/recipe/IRecipeHandler;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipeCatalyst", "(Ljava/lang/Object;[Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipeRegistryPlugin", "(Lmezz/jei/api/recipe/IRecipeRegistryPlugin;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addRecipeClickArea", "(Ljava/lang/Class;IIII[Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addIngredientInfo", "(Ljava/lang/Object;Lmezz/jei/api/recipe/IIngredientType;[Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addIngredientInfo", "(Ljava/lang/Object;Ljava/lang/Class;[Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addIngredientInfo", "(Ljava/util/List;Lmezz/jei/api/recipe/IIngredientType;[Ljava/lang/String;)V");
        add(targets, "mezz.jei.startup.ModRegistry", "addIngredientInfo", "(Ljava/util/List;Ljava/lang/Class;[Ljava/lang/String;)V");

        add(targets, "mezz.jei.startup.ModIngredientRegistration", "register", "(Lmezz/jei/api/recipe/IIngredientType;Ljava/util/Collection;Lmezz/jei/api/ingredients/IIngredientHelper;Lmezz/jei/api/ingredients/IIngredientRenderer;)V");
        add(targets, "mezz.jei.startup.ModIngredientRegistration", "createIngredientRegistry", "(Lmezz/jei/startup/IModIdHelper;Lmezz/jei/ingredients/IngredientBlacklistInternal;)Lmezz/jei/ingredients/IngredientRegistry;");
        add(targets, "mezz.jei.startup.ModIngredientRegistration", "createIngredientSet", "(Lmezz/jei/api/recipe/IIngredientType;Ljava/util/Collection;)Lmezz/jei/util/IngredientSet;");

        add(targets, "mezz.jei.ingredients.IngredientRegistry", "getAllIngredients", "(Lmezz/jei/api/recipe/IIngredientType;)Ljava/util/Collection;");
        add(targets, "mezz.jei.ingredients.IngredientRegistry", "getAllIngredients", "(Ljava/lang/Class;)Ljava/util/Collection;");
        add(targets, "mezz.jei.ingredients.IngredientRegistry", "<init>", "(Lmezz/jei/startup/IModIdHelper;Lmezz/jei/ingredients/IngredientBlacklistInternal;Ljava/util/Map;Lcom/google/common/collect/ImmutableMap;Lcom/google/common/collect/ImmutableMap;)V");
        add(targets, "mezz.jei.ingredients.IngredientRegistry", "getStackProperties", "(Lnet/minecraft/item/ItemStack;)V");

        add(targets, "mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory", "create", "(Lmezz/jei/startup/StackHelper;)Ljava/util/List;");
        add(targets, "mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory", "addItemAndSubItems", "(Lmezz/jei/startup/StackHelper;Lnet/minecraft/item/Item;Ljava/util/List;Ljava/util/Set;)V");
        add(targets, "mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory", "addBlockAndSubBlocks", "(Lmezz/jei/startup/StackHelper;Lnet/minecraft/block/Block;Ljava/util/List;Ljava/util/Set;)V");
        add(targets, "mezz.jei.plugins.vanilla.ingredients.fluid.FluidStackListFactory", "create", "()Ljava/util/List;");
        add(targets, "mezz.jei.plugins.vanilla.ingredients.enchant.EnchantDataListFactory", "create", "()Ljava/util/List;");

        add(targets, "mezz.jei.recipes.RecipeRegistry", "<init>", "(Ljava/util/List;Ljava/util/List;Lmezz/jei/collect/ListMultiMap;Lcom/google/common/collect/ImmutableTable;Ljava/util/List;Lmezz/jei/collect/ListMultiMap;Lmezz/jei/collect/ListMultiMap;Lmezz/jei/collect/ListMultiMap;Lmezz/jei/ingredients/IngredientRegistry;Ljava/util/List;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "addRecipes", "(Ljava/util/List;Lmezz/jei/collect/ListMultiMap;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "addRecipe", "(Ljava/lang/Object;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "addRecipe", "(Lmezz/jei/api/recipe/IRecipeWrapper;Ljava/lang/String;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "addRecipe", "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "addRecipe", "(Ljava/lang/Object;Lmezz/jei/api/recipe/IRecipeWrapper;Lmezz/jei/api/recipe/IRecipeCategory;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "addRecipeUnchecked", "(Ljava/lang/Object;Lmezz/jei/api/recipe/IRecipeWrapper;Lmezz/jei/api/recipe/IRecipeCategory;)V");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "getRecipeWrapper", "(Ljava/lang/Object;Ljava/lang/String;)Lmezz/jei/api/recipe/IRecipeWrapper;");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "getRecipeWrapper", "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)Lmezz/jei/api/recipe/IRecipeWrapper;");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "getRecipeHandler", "(Ljava/lang/Class;Ljava/lang/String;)Lmezz/jei/api/recipe/IRecipeHandler;");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "getRecipeHandlers", "(Ljava/lang/Class;)Ljava/util/List;");
        add(targets, "mezz.jei.recipes.RecipeRegistry", "getIngredients", "(Lmezz/jei/api/recipe/IRecipeWrapper;)Lmezz/jei/ingredients/Ingredients;");

        add(targets, "mezz.jei.recipes.RecipeMap", "addRecipeCategory", "(Lmezz/jei/api/recipe/IRecipeCategory;Ljava/lang/Object;Lmezz/jei/api/ingredients/IIngredientHelper;)V");
        add(targets, "mezz.jei.recipes.RecipeMap", "addRecipe", "(Lmezz/jei/api/recipe/IRecipeWrapper;Lmezz/jei/api/recipe/IRecipeCategory;Ljava/util/Map;)V");
        add(targets, "mezz.jei.recipes.RecipeMap", "addRecipe", "(Lmezz/jei/api/recipe/IRecipeWrapper;Lmezz/jei/api/recipe/IRecipeCategory;Lmezz/jei/api/recipe/IIngredientType;Ljava/util/List;)V");

        add(targets, "mezz.jei.ingredients.IngredientFilter", "<init>", "(Lmezz/jei/ingredients/IngredientBlacklistInternal;Lnet/minecraft/util/NonNullList;)V");
        add(targets, "mezz.jei.ingredients.IngredientFilter", "addIngredients", "(Lnet/minecraft/util/NonNullList;)V");
        add(targets, "mezz.jei.ingredients.IngredientFilter", "block", "()V");

        add(targets, "mezz.jei.search.ElementSearch", "<init>", "()V");
        add(targets, "mezz.jei.search.ElementSearch", "addAll", "(Lnet/minecraft/util/NonNullList;)V");
        add(targets, "mezz.jei.search.ElementSearch", "block", "()V");

        add(targets, "mezz.jei.search.PrefixedSearchable", "submitAll", "(Lnet/minecraft/util/NonNullList;)V");
        add(targets, "mezz.jei.search.PrefixedSearchable", "stop", "()V");
        add(targets, "mezz.jei.search.AsyncPrefixedSearchable", "submitAll", "(Lnet/minecraft/util/NonNullList;)V");
        add(targets, "mezz.jei.search.AsyncPrefixedSearchable", "stop", "()V");

        add(targets, "jeresources.proxy.CommonProxy", "initCompatibility", "()V");
        add(targets, "jeresources.compatibility.Compatibility", "init", "()V");
        add(targets, "jeresources.compatibility.DungeonRegistryImpl", "commit", "()V");
        add(targets, "jeresources.compatibility.MobRegistryImpl", "commit", "()V");
        add(targets, "jeresources.compatibility.PlantRegistryImpl", "commit", "()V");
        add(targets, "jeresources.compatibility.WorldGenRegistryImpl", "commit", "()V");
        add(targets, "jeresources.compatibility.minecraft.MinecraftCompat", "init", "(Z)V");
        add(targets, "jeresources.compatibility.minecraft.MinecraftCompat", "registerVanillaMobs", "()V");
        add(targets, "jeresources.compatibility.minecraft.MinecraftCompat", "registerDungeonLoot", "()V");
        add(targets, "jeresources.compatibility.minecraft.MinecraftCompat", "registerOres", "()V");
        add(targets, "jeresources.compatibility.minecraft.MinecraftCompat", "registerVanillaPlants", "()V");
        add(targets, "jeresources.json.WorldGenAdapter", "hasWorldGenDIYData", "()Z");
        add(targets, "jeresources.json.WorldGenAdapter", "readDIYData", "()Z");
        add(targets, "jeresources.util.VillagersHelper", "initRegistry", "(Ljeresources/registry/VillagerRegistry;)V");
        add(targets, "jeresources.registry.EnchantmentRegistry", "removeAll", "([Ljava/lang/String;)V");
        add(targets, "jeresources.util.LootTableHelper", "getAllMobLootTables", "(Lnet/minecraft/world/World;)Ljava/util/Map;");
        add(targets, "jeresources.util.LootTableHelper", "toDrops", "(Lnet/minecraft/world/storage/loot/LootTable;)Ljava/util/List;");
        add(targets, "jeresources.util.LootTableHelper", "toDrops", "(Lnet/minecraft/world/World;Lnet/minecraft/util/ResourceLocation;)Ljava/util/List;");
        add(targets, "jeresources.util.MobTableBuilder", "add", "(Lnet/minecraft/util/ResourceLocation;Ljava/lang/Class;)V");
        add(targets, "jeresources.util.MobTableBuilder", "add", "(Lnet/minecraft/util/ResourceLocation;Ljava/lang/Class;Ljeresources/util/MobTableBuilder$EntityPropertySetter;)V");
        add(targets, "jeresources.util.MobTableBuilder", "construct", "(Lnet/minecraft/world/World;Ljava/lang/Class;)Lnet/minecraft/entity/EntityLivingBase;");
        add(targets, "jeresources.util.MobTableBuilder", "getConstructor", "(Ljava/lang/Class;)Ljava/lang/reflect/Constructor;");
        add(targets, "jeresources.jei.enchantment.EnchantmentMaker", "createRecipes", "(Ljava/util/Collection;)Ljava/util/List;");
        add(targets, "jeresources.registry.WorldGenRegistry", "getWorldGen", "()Ljava/util/List;");
        add(targets, "jeresources.registry.PlantRegistry", "getAllPlants", "()Ljava/util/List;");
        add(targets, "jeresources.registry.MobRegistry", "getMobs", "()Ljava/util/List;");
        add(targets, "jeresources.registry.DungeonRegistry", "getDungeons", "()Ljava/util/List;");
        add(targets, "jeresources.registry.VillagerRegistry", "getVillagers", "()Ljava/util/List;");

        return targets;
    }

    private static void add(Map<String, Set<MethodKey>> targets, String className, String methodName, String descriptor) {
        targets.computeIfAbsent(className, key -> new HashSet<>()).add(new MethodKey(methodName, descriptor));
    }

    private static final class HeiClassVisitor extends ClassVisitor {
        private final String className;
        private final Set<MethodKey> methods;
        private final boolean plugin;

        private HeiClassVisitor(ClassVisitor delegate, String className, Set<MethodKey> methods, boolean plugin) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
            this.methods = methods;
            this.plugin = plugin;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null) {
                return null;
            }
            if (methods != null && methods.contains(new MethodKey(name, desc))) {
                return new TimedMethodVisitor(visitor, "HEI " + className + '.' + name);
            }
            if (plugin && isPluginMethod(name, desc)) {
                return new TimedMethodVisitor(visitor, "HEI plugin " + className + '.' + name);
            }
            return visitor;
        }

        private static boolean isPluginMethod(String name, String desc) {
            return ("registerItemSubtypes".equals(name) && "(Lmezz/jei/api/ISubtypeRegistry;)V".equals(desc))
                    || ("registerSubtypes".equals(name) && "(Lmezz/jei/api/ISubtypeRegistry;)V".equals(desc))
                    || ("registerIngredients".equals(name) && "(Lmezz/jei/api/ingredients/IModIngredientRegistration;)V".equals(desc))
                    || ("registerCategories".equals(name) && "(Lmezz/jei/api/recipe/IRecipeCategoryRegistration;)V".equals(desc))
                    || ("register".equals(name) && "(Lmezz/jei/api/IModRegistry;)V".equals(desc))
                    || ("onRuntimeAvailable".equals(name) && "(Lmezz/jei/api/IJeiRuntime;)V".equals(desc));
        }
    }

    private static final class TimedMethodVisitor extends MethodVisitor {
        private final String label;
        private boolean entered;

        private TimedMethodVisitor(MethodVisitor delegate, String label) {
            super(Opcodes.ASM9, delegate);
            this.label = label;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            entered = true;
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if (entered && isExit(opcode)) {
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
            }
            super.visitInsn(opcode);
        }

        private static boolean isExit(int opcode) {
            return opcode == Opcodes.RETURN
                    || opcode == Opcodes.IRETURN
                    || opcode == Opcodes.LRETURN
                    || opcode == Opcodes.FRETURN
                    || opcode == Opcodes.DRETURN
                    || opcode == Opcodes.ARETURN
                    || opcode == Opcodes.ATHROW;
        }
    }

    private static final class MethodKey {
        private final String name;
        private final String descriptor;

        private MethodKey(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MethodKey)) {
                return false;
            }
            MethodKey methodKey = (MethodKey) other;
            return name.equals(methodKey.name) && descriptor.equals(methodKey.descriptor);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + descriptor.hashCode();
        }
    }
}
