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
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
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
    private static final boolean PARALLEL_SEARCH_BUILD = Boolean.parseBoolean(System.getProperty("gpom.hei.parallelSearchBuild", "true"));
    private static final boolean SKIP_RECIPE_PROGRESS = Boolean.parseBoolean(System.getProperty("gpom.hei.skipRecipeProgress", "true"));
    private static final boolean SKIP_SEARCH_PROGRESS = Boolean.parseBoolean(System.getProperty("gpom.hei.skipSearchProgress", "true"));
    private static final boolean SKIP_PLUGIN_PROGRESS = Boolean.parseBoolean(System.getProperty("gpom.hei.skipPluginProgress", "true"));
    private static final boolean DISABLE_TOOLTIP_SEARCH_INDEX = Boolean.parseBoolean(System.getProperty("gpom.hei.disableTooltipSearchIndex", "true"));
    private static final boolean FAST_JER_ENCHANTMENTS = Boolean.parseBoolean(System.getProperty("gpom.hei.fastJerEnchantments", "true"));
    private static final boolean FAST_JER_VILLAGERS = Boolean.parseBoolean(System.getProperty("gpom.hei.fastJerVillagers", "true"));
    private static final boolean FAST_JER_LOOT_REFLECTION = Boolean.parseBoolean(System.getProperty("gpom.hei.fastJerLootReflection", "true"));
    private static final boolean FAST_HEI_FALLBACK_SUBTYPES = Boolean.parseBoolean(System.getProperty("gpom.hei.fastFallbackSubtypes", "true"));
    private static final boolean FAST_HEI_CREATIVE_TABS_ONLY_ITEM_ENUMERATION = Boolean.parseBoolean(System.getProperty("gpom.hei.creativeTabsOnlyItemEnumeration", "true"));
    private static final boolean FAST_HEI_FLUID_HANDLER_ITEMS = Boolean.parseBoolean(System.getProperty("gpom.hei.fastFluidHandlerItemEnumeration", "true"));
    private static final boolean FAST_EXTRATREES_LUMBERMILL = Boolean.parseBoolean(System.getProperty("gpom.hei.fastExtraTreesLumbermill", "true"));
    private static final boolean FAST_ENDERIO_TANK = Boolean.parseBoolean(System.getProperty("gpom.hei.fastEnderIOTank", "true"));
    private static final boolean FAST_TE_TRANSPOSER_CONTAINERS = Boolean.parseBoolean(System.getProperty("gpom.hei.fastThermalTransposerContainers", "true"));
    private static final boolean LOG_HEI_REGISTRY_ONLY_ITEMS = Boolean.parseBoolean(System.getProperty("gpom.hei.logRegistryOnlyItems", "true"));
    private static final boolean PLUGIN_PROFILER = Boolean.parseBoolean(System.getProperty("gpom.hei.pluginProfiler", "true"));
    private static final boolean HOT_METHOD_PROFILER = Boolean.parseBoolean(System.getProperty("gpom.hei.hotMethodProfiler", "false"));
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
        boolean supportedHeiDetailClass = isSupportedHeiDetailTargetClass(className);

        if (!supportedHeiClass && !supportedJerClass && !supportedHeiDetailClass && !heiAvailable) {
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
        if (supportedHeiClass && "mezz.jei.recipes.RecipeRegistry".equals(className)) {
            basicClass = patchRecipeRegistryGpomProgress(basicClass);
            basicClass = patchRecipeRegistryBulkVisibleCache(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.startup.ModRegistry".equals(className)) {
            basicClass = patchSynchronizedMethods(basicClass, modRegistrySynchronizedMethods());
        }
        if (supportedHeiClass && "mezz.jei.recipes.RecipeTransferRegistry".equals(className)) {
            basicClass = patchSynchronizedMethods(basicClass, recipeTransferRegistrySynchronizedMethods());
        }
        if (supportedHeiClass && "mezz.jei.startup.StackHelper".equals(className)) {
            basicClass = patchSynchronizedMethods(basicClass, stackHelperSynchronizedMethods());
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
        if (supportedHeiClass && "mezz.jei.startup.JeiStarter".equals(className)) {
            basicClass = patchJeiStarterParallelPluginRegistration(basicClass);
        }
        if (supportedJerClass && "jeresources.jei.enchantment.EnchantmentMaker".equals(className) && FAST_JER_ENCHANTMENTS) {
            basicClass = patchJerEnchantmentMaker(basicClass);
        }
        if (supportedJerClass && "jeresources.util.VillagersHelper".equals(className) && FAST_JER_VILLAGERS) {
            basicClass = patchJerVillagersHelper(basicClass);
        }
        if (supportedJerClass && "jeresources.collection.TradeList".equals(className) && FAST_JER_VILLAGERS) {
            basicClass = patchJerTradeList(basicClass);
        }
        if (supportedJerClass && "jeresources.util.LootTableHelper".equals(className) && FAST_JER_LOOT_REFLECTION) {
            basicClass = patchJerLootTableHelper(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory".equals(className) && FAST_HEI_FALLBACK_SUBTYPES) {
            basicClass = patchHeiItemStackListFactory(basicClass);
        }
        if (supportedHeiClass && "mezz.jei.plugins.vanilla.ingredients.item.ItemStackListFactory".equals(className) && FAST_HEI_CREATIVE_TABS_ONLY_ITEM_ENUMERATION) {
            basicClass = patchHeiItemStackListFactoryCreate(basicClass);
        }
        if (supportedHeiDetailClass && FAST_HEI_FLUID_HANDLER_ITEMS && isFluidHandlerItemEnumerationTarget(className)) {
            basicClass = patchFluidHandlerItemEnumeration(basicClass);
        }
        if (supportedHeiDetailClass && FAST_HEI_FLUID_HANDLER_ITEMS
                && "forestry.factory.recipes.jei.bottler.BottlerRecipeMaker".equals(className)) {
            basicClass = patchForestryBottlerFastPath(basicClass);
        }
        if (supportedHeiDetailClass && FAST_EXTRATREES_LUMBERMILL
                && "binnie.extratrees.integration.jei.lumbermill.LumbermillRecipeMaker".equals(className)) {
            basicClass = patchExtraTreesLumbermillFastPath(basicClass);
        }
        if (supportedHeiDetailClass && FAST_ENDERIO_TANK
                && "crazypants.enderio.machines.integration.jei.TankRecipeCategory".equals(className)) {
            basicClass = patchEnderIOTankRegisterFastPath(basicClass);
        }
        if (supportedHeiDetailClass && FAST_ENDERIO_TANK
                && "crazypants.enderio.machines.integration.jei.TankRecipeCategory$TankRecipeWrapperSimple".equals(className)) {
            basicClass = patchEnderIOTankCompressedWrapperIngredients(basicClass);
        }
        if (supportedHeiDetailClass && FAST_TE_TRANSPOSER_CONTAINERS
                && "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeWrapperContainer".equals(className)) {
            basicClass = patchThermalTransposerContainerWrapper(basicClass);
        }

        Set<MethodKey> methods = TARGETS.get(name);
        if (methods == null) {
            methods = TARGETS.get(className);
        }

        if (methods != null && !supportedHeiClass && !supportedJerClass && !supportedHeiDetailClass) {
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

    private static byte[] patchRecipeRegistryGpomProgress(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"addRecipes".equals(method.name)
                        || !"(Ljava/util/List;Lmezz/jei/collect/ListMultiMap;)V".equals(method.desc)) {
                    continue;
                }

                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.ASTORE && ((VarInsnNode) insn).var == 3) {
                        InsnList begin = new InsnList();
                        begin.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "beginRecipeRegistryBulk",
                                "()V",
                                false
                        ));
                        begin.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        begin.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        begin.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "beginRecipeProgress",
                                "(Ljava/lang/Object;Ljava/util/List;)V",
                                false
                        ));
                        method.instructions.insert(insn, begin);
                        changed = true;
                        break;
                    }
                }

                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.INVOKESPECIAL) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if ("mezz/jei/recipes/RecipeRegistry".equals(methodInsn.owner)
                                && "addRecipe".equals(methodInsn.name)
                                && "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;)V".equals(methodInsn.desc)) {
                            InsnList step = new InsnList();
                            step.add(new InsnNode(Opcodes.DUP));
                            step.add(new MethodInsnNode(
                                    Opcodes.INVOKESTATIC,
                                    "com/l/gpom/optimization/HeiOptimizations",
                                    "stepRecipeProgress",
                                    "(Ljava/lang/Object;)V",
                                    false
                            ));
                            method.instructions.insertBefore(insn, step);
                            changed = true;
                        }
                    } else if (insn.getOpcode() == Opcodes.RETURN) {
                        InsnList finish = new InsnList();
                        finish.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "finishRecipeProgress",
                                "()V",
                                false
                        ));
                        finish.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "finishRecipeRegistryBulk",
                                "()V",
                                false
                        ));
                        method.instructions.insertBefore(insn, finish);
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

    private static byte[] patchRecipeRegistryBulkVisibleCache(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"addRecipeUnchecked".equals(method.name)
                        || !"(Ljava/lang/Object;Lmezz/jei/api/recipe/IRecipeWrapper;Lmezz/jei/api/recipe/IRecipeCategory;)V".equals(method.desc)) {
                    continue;
                }

                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if ("java/util/List".equals(methodInsn.owner)
                            && "clear".equals(methodInsn.name)
                            && "()V".equals(methodInsn.desc)
                            && previousInstructionIsVisibleCacheGet(methodInsn)) {
                        methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "clearRecipeCategoriesVisibleCache";
                        methodInsn.desc = "(Ljava/util/List;)V";
                        methodInsn.itf = false;
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

    private static boolean previousInstructionIsVisibleCacheGet(AbstractInsnNode insn) {
        AbstractInsnNode cursor = insn.getPrevious();
        while (cursor != null && cursor.getOpcode() < 0) {
            cursor = cursor.getPrevious();
        }
        return cursor instanceof FieldInsnNode
                && "mezz/jei/recipes/RecipeRegistry".equals(((FieldInsnNode) cursor).owner)
                && "recipeCategoriesVisibleCache".equals(((FieldInsnNode) cursor).name)
                && "Ljava/util/List;".equals(((FieldInsnNode) cursor).desc);
    }

    private static byte[] patchSynchronizedMethods(byte[] basicClass, Set<MethodKey> methods) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (methods.contains(new MethodKey(method.name, method.desc))
                        && (method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    changed = true;
                }
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static Set<MethodKey> modRegistrySynchronizedMethods() {
        Set<MethodKey> methods = new HashSet<>();
        methods.add(new MethodKey("addRecipeCategories", "([Lmezz/jei/api/recipe/IRecipeCategory;)V"));
        methods.add(new MethodKey("addRecipeHandlers", "([Lmezz/jei/api/recipe/IRecipeHandler;)V"));
        methods.add(new MethodKey("addRecipes", "(Ljava/util/Collection;)V"));
        methods.add(new MethodKey("addRecipes", "(Ljava/util/Collection;Ljava/lang/String;)V"));
        methods.add(new MethodKey("handleRecipes", "(Ljava/lang/Class;Lmezz/jei/api/recipe/IRecipeWrapperFactory;Ljava/lang/String;)V"));
        methods.add(new MethodKey("addRecipeClickArea", "(Ljava/lang/Class;IIII[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addRecipeCatalyst", "(Ljava/lang/Object;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addRecipeCategoryCraftingItem", "(Lnet/minecraft/item/ItemStack;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addAdvancedGuiHandlers", "([Lmezz/jei/api/gui/IAdvancedGuiHandler;)V"));
        methods.add(new MethodKey("addGlobalGuiHandlers", "([Lmezz/jei/api/gui/IGlobalGuiHandler;)V"));
        methods.add(new MethodKey("addGuiScreenHandler", "(Ljava/lang/Class;Lmezz/jei/api/gui/IGuiScreenHandler;)V"));
        methods.add(new MethodKey("addGhostIngredientHandler", "(Ljava/lang/Class;Lmezz/jei/api/gui/IGhostIngredientHandler;)V"));
        methods.add(new MethodKey("addDescription", "(Ljava/util/List;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addDescription", "(Lnet/minecraft/item/ItemStack;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addIngredientInfo", "(Ljava/lang/Object;Lmezz/jei/api/recipe/IIngredientType;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addIngredientInfo", "(Ljava/lang/Object;Ljava/lang/Class;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addIngredientInfo", "(Ljava/util/List;Lmezz/jei/api/recipe/IIngredientType;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addIngredientInfo", "(Ljava/util/List;Ljava/lang/Class;[Ljava/lang/String;)V"));
        methods.add(new MethodKey("addAnvilRecipe", "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Ljava/util/List;)V"));
        methods.add(new MethodKey("addRecipeRegistryPlugin", "(Lmezz/jei/api/recipe/IRecipeRegistryPlugin;)V"));
        methods.add(new MethodKey("getRecipeTransferRegistry", "()Lmezz/jei/api/recipe/transfer/IRecipeTransferRegistry;"));
        methods.add(new MethodKey("createRecipeRegistry", "(Lmezz/jei/ingredients/IngredientRegistry;)Lmezz/jei/recipes/RecipeRegistry;"));
        return methods;
    }

    private static Set<MethodKey> recipeTransferRegistrySynchronizedMethods() {
        Set<MethodKey> methods = new HashSet<>();
        methods.add(new MethodKey("addRecipeTransferHandler", "(Ljava/lang/Class;Ljava/lang/String;IIII)V"));
        methods.add(new MethodKey("addRecipeTransferHandler", "(Lmezz/jei/api/recipe/transfer/IRecipeTransferInfo;)V"));
        methods.add(new MethodKey("addRecipeTransferHandler", "(Lmezz/jei/api/recipe/transfer/IRecipeTransferHandler;Ljava/lang/String;)V"));
        methods.add(new MethodKey("addUniversalRecipeTransferHandler", "(Lmezz/jei/api/recipe/transfer/IRecipeTransferHandler;)V"));
        methods.add(new MethodKey("getRecipeTransferHandlers", "()Lcom/google/common/collect/ImmutableTable;"));
        return methods;
    }

    private static Set<MethodKey> stackHelperSynchronizedMethods() {
        Set<MethodKey> methods = new HashSet<>();
        methods.add(new MethodKey("getUniqueIdentifierForStack", "(Lnet/minecraft/item/ItemStack;)Ljava/lang/String;"));
        methods.add(new MethodKey("getUniqueIdentifierForStack", "(Lnet/minecraft/item/ItemStack;Lmezz/jei/startup/StackHelper$UidMode;)Ljava/lang/String;"));
        return methods;
    }

    private static byte[] patchJeiStarterParallelPluginRegistration(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"registerPlugins".equals(method.name)
                        || !"(Ljava/util/List;Lmezz/jei/startup/ModRegistry;)V".equals(method.desc)) {
                    continue;
                }
                LabelNode original = new LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "registerPluginsMaybeParallel",
                        "(Ljava/util/List;Ljava/lang/Object;)Z",
                        false
                ));
                instructions.add(new JumpInsnNode(Opcodes.IFEQ, original));
                instructions.add(new InsnNode(Opcodes.RETURN));
                instructions.add(original);
                instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(instructions);
                changed = true;
            }
            if (changed) {
                return writeNode(node);
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
                method.instructions.insert(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "beginJerVillagerTradeCache",
                        "()V",
                        false
                ));
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
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        method.instructions.insertBefore(insn, new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "finishJerVillagerTradeCache",
                                "()V",
                                false
                        ));
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

    private static byte[] patchJerTradeList(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"addITradeList".equals(method.name)
                        || !"(Lnet/minecraft/entity/passive/EntityVillager$ITradeList;)V".equals(method.desc)) {
                    continue;
                }
                LabelNode original = new LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "addJerTradeListCached",
                        "(Ljava/util/List;Ljava/lang/Object;)Z",
                        false
                ));
                instructions.add(new JumpInsnNode(Opcodes.IFEQ, original));
                instructions.add(new InsnNode(Opcodes.RETURN));
                instructions.add(original);
                instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(instructions);
                changed = true;
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
                if ("toDrops".equals(method.name)
                        && "(Lnet/minecraft/world/storage/loot/LootTable;)Ljava/util/List;".equals(method.desc)) {
                    LabelNode original = new LabelNode();
                    InsnNode cachedReturn = new InsnNode(Opcodes.ARETURN);
                    int cacheLocal = method.maxLocals;
                    InsnList cacheCheck = new InsnList();
                    cacheCheck.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    cacheCheck.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/HeiOptimizations",
                            "cachedJerLootTableDrops",
                            "(Lnet/minecraft/world/storage/loot/LootTable;)Ljava/util/List;",
                            false
                    ));
                    cacheCheck.add(new VarInsnNode(Opcodes.ASTORE, cacheLocal));
                    cacheCheck.add(new VarInsnNode(Opcodes.ALOAD, cacheLocal));
                    cacheCheck.add(new JumpInsnNode(Opcodes.IFNULL, original));
                    cacheCheck.add(new VarInsnNode(Opcodes.ALOAD, cacheLocal));
                    cacheCheck.add(cachedReturn);
                    cacheCheck.add(original);
                    cacheCheck.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                    method.instructions.insert(cacheCheck);
                    method.maxLocals = Math.max(method.maxLocals, cacheLocal + 1);
                    changed = true;

                    for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                        if (insn == cachedReturn || insn.getOpcode() != Opcodes.ARETURN) {
                            continue;
                        }
                        InsnList cacheStore = new InsnList();
                        cacheStore.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        cacheStore.add(new InsnNode(Opcodes.SWAP));
                        cacheStore.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "cacheJerLootTableDrops",
                                "(Lnet/minecraft/world/storage/loot/LootTable;Ljava/util/List;)Ljava/util/List;",
                                false
                        ));
                        method.instructions.insertBefore(insn, cacheStore);
                    }
                }
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

    private static byte[] patchHeiItemStackListFactoryCreate(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"create".equals(method.name)
                        || !"(Lmezz/jei/startup/StackHelper;)Ljava/util/List;".equals(method.desc)) {
                    continue;
                }

                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
                    AbstractInsnNode nextLoopInsn = insn.getNext();
                    if (insn.getOpcode() != Opcodes.GETSTATIC) {
                        insn = nextLoopInsn;
                        continue;
                    }
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    if (!"net/minecraftforge/fml/common/registry/ForgeRegistries".equals(fieldInsn.owner)
                            || (!"BLOCKS".equals(fieldInsn.name) && !"ITEMS".equals(fieldInsn.name))) {
                        insn = nextLoopInsn;
                        continue;
                    }

                    AbstractInsnNode next = insn.getNext();
                    while (next != null && next.getOpcode() < 0) {
                        next = next.getNext();
                    }
                    if (!(next instanceof MethodInsnNode)) {
                        insn = nextLoopInsn;
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) next;
                    if (!"net/minecraftforge/registries/IForgeRegistry".equals(methodInsn.owner)
                            || !"iterator".equals(methodInsn.name)
                            || !"()Ljava/util/Iterator;".equals(methodInsn.desc)) {
                        insn = nextLoopInsn;
                        continue;
                    }

                    method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/HeiOptimizations",
                            "emptyIterator",
                            "()Ljava/util/Iterator;",
                            false
                    ));
                    method.instructions.remove(methodInsn);
                    changed = true;
                    insn = nextLoopInsn;
                }

                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.ARETURN) {
                        continue;
                    }
                    InsnList addMissing = new InsnList();
                    addMissing.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    addMissing.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    addMissing.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    addMissing.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/HeiOptimizations",
                            "addMissingRegistryItemStacks",
                            "(Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Set;)Ljava/util/List;",
                            false
                    ));
                    method.instructions.insertBefore(insn, addMissing);
                    changed = true;

                    if (LOG_HEI_REGISTRY_ONLY_ITEMS) {
                        InsnList logging = new InsnList();
                        logging.add(new InsnNode(Opcodes.DUP));
                        logging.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/HeiOptimizations",
                                "logRegistryOnlyItems",
                                "(Ljava/util/List;)V",
                                false
                        ));
                        method.instructions.insertBefore(insn, logging);
                    }

                    InsnList saveCache = new InsnList();
                    saveCache.add(new InsnNode(Opcodes.DUP));
                    saveCache.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/HeiOptimizations",
                            "saveCachedItemStacks",
                            "(Ljava/util/List;)V",
                            false
                    ));
                    method.instructions.insertBefore(insn, saveCache);
                }
                insertHeiItemStackCacheLoad(method);
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static void insertHeiItemStackCacheLoad(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.ASTORE || !(((VarInsnNode) insn).var == 3)) {
                continue;
            }

            LabelNode cacheMiss = new LabelNode();
            InsnList instructions = new InsnList();
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
            instructions.add(new VarInsnNode(Opcodes.ALOAD, 3));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/HeiOptimizations",
                    "loadCachedItemStacks",
                    "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/Set;)Ljava/util/List;",
                    false
            ));
            instructions.add(new InsnNode(Opcodes.DUP));
            instructions.add(new JumpInsnNode(Opcodes.IFNULL, cacheMiss));
            instructions.add(new InsnNode(Opcodes.ARETURN));
            instructions.add(cacheMiss);
            instructions.add(new FrameNode(
                    Opcodes.F_FULL,
                    4,
                    new Object[]{
                            "mezz/jei/plugins/vanilla/ingredients/item/ItemStackListFactory",
                            "mezz/jei/startup/StackHelper",
                            "java/util/ArrayList",
                            "java/util/HashSet"
                    },
                    1,
                    new Object[]{"java/util/List"}
            ));
            instructions.add(new InsnNode(Opcodes.POP));
            method.instructions.insert(insn, instructions);
            return;
        }
    }

    private static byte[] patchFluidHandlerItemEnumeration(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            String fluidHandlerReplacement = fluidHandlerItemIngredientReplacement(node.name.replace('/', '.'));
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (!"mezz/jei/api/ingredients/IIngredientRegistry".equals(methodInsn.owner)) {
                        continue;
                    }
                    if ("getIngredients".equals(methodInsn.name)
                            && "(Ljava/lang/Class;)Ljava/util/List;".equals(methodInsn.desc)) {
                        methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = fluidHandlerReplacement;
                        methodInsn.desc = "(Ljava/lang/Object;Ljava/lang/Class;)Ljava/util/List;";
                        methodInsn.itf = false;
                        changed = true;
                    } else if ("getAllIngredients".equals(methodInsn.name)
                            && "(Lmezz/jei/api/recipe/IIngredientType;)Ljava/util/Collection;".equals(methodInsn.desc)) {
                        methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                        methodInsn.owner = "com/l/gpom/optimization/HeiOptimizations";
                        methodInsn.name = "getFluidHandlerItemIngredientsForType";
                        methodInsn.desc = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Collection;";
                        methodInsn.itf = false;
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

    private static byte[] patchForestryBottlerFastPath(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"getBottlerRecipes".equals(method.name)
                        || !"(Lmezz/jei/api/ingredients/IIngredientRegistry;)Ljava/util/List;".equals(method.desc)) {
                    continue;
                }

                LabelNode original = new LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "fastForestryBottlerRecipes",
                        "(Ljava/lang/Object;)Ljava/util/List;",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.DUP));
                instructions.add(new JumpInsnNode(Opcodes.IFNULL, original));
                instructions.add(new InsnNode(Opcodes.ARETURN));
                instructions.add(original);
                instructions.add(new FrameNode(
                        Opcodes.F_SAME1,
                        0,
                        null,
                        1,
                        new Object[]{"java/util/List"}
                ));
                instructions.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(instructions);
                changed = true;
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchExtraTreesLumbermillFastPath(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"create".equals(method.name)
                        || !"(Lmezz/jei/api/IJeiHelpers;)Ljava/util/List;".equals(method.desc)) {
                    continue;
                }

                LabelNode original = new LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "fastExtraTreesLumbermillRecipes",
                        "(Ljava/lang/Object;)Ljava/util/List;",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.DUP));
                instructions.add(new JumpInsnNode(Opcodes.IFNULL, original));
                instructions.add(new InsnNode(Opcodes.ARETURN));
                instructions.add(original);
                instructions.add(new FrameNode(
                        Opcodes.F_SAME1,
                        0,
                        null,
                        1,
                        new Object[]{"java/util/List"}
                ));
                instructions.add(new InsnNode(Opcodes.POP));
                method.instructions.insert(instructions);
                changed = true;
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchEnderIOTankRegisterFastPath(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"register".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }

                LabelNode original = new LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "fastEnderIOTankRegister",
                        "()Z",
                        false
                ));
                instructions.add(new JumpInsnNode(Opcodes.IFEQ, original));
                instructions.add(new InsnNode(Opcodes.RETURN));
                instructions.add(original);
                instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(instructions);
                changed = true;
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchEnderIOTankCompressedWrapperIngredients(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"getIngredients".equals(method.name)
                        || !"(Lmezz/jei/api/ingredients/IIngredients;)V".equals(method.desc)) {
                    continue;
                }

                LabelNode original = new LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "applyCompressedEnderIOTankIngredients",
                        "(Ljava/lang/Object;Ljava/lang/Object;)Z",
                        false
                ));
                instructions.add(new JumpInsnNode(Opcodes.IFEQ, original));
                instructions.add(new InsnNode(Opcodes.RETURN));
                instructions.add(original);
                instructions.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                method.instructions.insert(instructions);
                changed = true;
            }
            if (changed) {
                return writeNode(node);
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static byte[] patchThermalTransposerContainerWrapper(byte[] basicClass) {
        try {
            ClassNode node = readNode(basicClass);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name)
                        || !"(Lmezz/jei/api/IGuiHelper;Lnet/minecraft/item/ItemStack;Ljava/lang/String;)V".equals(method.desc)) {
                    continue;
                }

                method.instructions.clear();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
                InsnList replacement = method.instructions;
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "cofh/thermalexpansion/plugins/jei/machine/transposer/TransposerRecipeWrapper",
                        "<init>",
                        "()V",
                        false
                ));
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 2));
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 3));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/HeiOptimizations",
                        "initThermalTransposerContainerWrapper",
                        "(Ljava/lang/Object;Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Ljava/lang/String;)V",
                        false
                ));
                replacement.add(new InsnNode(Opcodes.RETURN));
                changed = true;
            }
            if (changed) {
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

    private static boolean isSupportedHeiDetailTargetClass(String className) {
        if (className == null) {
            return false;
        }
        if (className.startsWith("cofh.thermalexpansion.plugins.jei.")) {
            return TargetedModVersions.isThermalExpansionClass(className);
        }
        if (className.startsWith("binnie.extratrees.integration.jei.")) {
            return TargetedModVersions.isBinnieModsClass(className);
        }
        if (className.startsWith("forestry.factory.recipes.jei.")) {
            return TargetedModVersions.isForestryClass(className);
        }
        if (className.startsWith("com.valkyrieofnight.et.m_plugins.jei.")) {
            return TargetedModVersions.isEnvironmentalTechClass(className);
        }
        if (className.startsWith("li.cil.oc.integration.jei.")) {
            return TargetedModVersions.isOpenComputersClass(className);
        }
        if (className.startsWith("moze_intel.projecte.integration.jei.")) {
            return TargetedModVersions.isProjectEClass(className);
        }
        if (className.startsWith("com.rwtema.extrautils2.crafting.jei.")) {
            return TargetedModVersions.isExtraUtilities2Class(className);
        }
        return className.startsWith("crazypants.enderio.machines.integration.jei.")
                && TargetedModVersions.isEnderIOClass(className);
    }

    private static boolean isFluidHandlerItemEnumerationTarget(String className) {
        return "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryFill".equals(className)
                || "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryExtract".equals(className)
                || "forestry.factory.recipes.jei.bottler.BottlerRecipeMaker".equals(className);
    }

    private static String fluidHandlerItemIngredientReplacement(String className) {
        if ("cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryFill".equals(className)) {
            return "getFillableFluidHandlerItemIngredients";
        }
        if ("cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryExtract".equals(className)) {
            return "getDrainableFluidHandlerItemIngredients";
        }
        return "getFluidHandlerItemIngredients";
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
        if (HOT_METHOD_PROFILER) {
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
        }

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
        add(targets, "jeresources.collection.TradeList", "addITradeList", "(Lnet/minecraft/entity/passive/EntityVillager$ITradeList;)V");
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

        addThermalExpansionJeiTargets(targets);
        addExtraTreesJeiTargets(targets);
        addEnvironmentalTechJeiTargets(targets);
        addForestryFactoryJeiTargets(targets);
        addEnderIOMachinesJeiTargets(targets);
        addOpenComputersJeiTargets(targets);
        addExtraUtilitiesJeiTargets(targets);
        addProjectEJeiTargets(targets);

        return targets;
    }

    private static void add(Map<String, Set<MethodKey>> targets, String className, String methodName, String descriptor) {
        targets.computeIfAbsent(className, key -> new HashSet<>()).add(new MethodKey(methodName, descriptor));
    }

    private static void addThermalExpansionJeiTargets(Map<String, Set<MethodKey>> targets) {
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.furnace.FurnaceRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.pulverizer.PulverizerRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.sawmill.SawmillRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.smelter.SmelterRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.insolator.InsolatorRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.compactor.CompactorRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.crucible.CrucibleRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.refinery.RefineryRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategory", false);
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryFill", "initialize", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryFill", "getRecipes", "(Lmezz/jei/api/IGuiHelper;Lmezz/jei/api/ingredients/IIngredientRegistry;)Ljava/util/List;");
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryExtract", "initialize", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeCategoryExtract", "getRecipes", "(Lmezz/jei/api/IGuiHelper;Lmezz/jei/api/ingredients/IIngredientRegistry;)Ljava/util/List;");
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeWrapper", "<init>", "(Lmezz/jei/api/IGuiHelper;Lcofh/thermalexpansion/util/managers/machine/TransposerManager$TransposerRecipe;Ljava/lang/String;)V");
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeWrapperContainer", "<init>", "(Lmezz/jei/api/IGuiHelper;Lnet/minecraft/item/ItemStack;Ljava/lang/String;)V");
        add(targets, "cofh.thermalexpansion.plugins.jei.machine.transposer.TransposerRecipeWrapperMulti", "<init>", "(Lmezz/jei/api/IGuiHelper;Ljava/util/List;Ljava/lang/String;)V");
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.charger.ChargerRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.centrifuge.CentrifugeRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.crafter.CrafterRecipeCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.brewer.BrewerRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.enchanter.EnchanterRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.precipitator.PrecipitatorRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.machine.extruder.ExtruderRecipeCategory", true);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.dynamo.steam.SteamFuelCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.dynamo.magmatic.MagmaticFuelCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.dynamo.compression.CompressionFuelCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.dynamo.reactant.ReactantFuelCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.dynamo.enervation.EnervationFuelCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.dynamo.numismatic.NumismaticFuelCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.device.factorizer.FactorizerRecipeCategory", false);
        addThermalExpansionCategory(targets, "cofh.thermalexpansion.plugins.jei.device.coolant.CoolantCategory", false);
        add(targets, "cofh.thermalexpansion.plugins.jei.Descriptions", "register", "(Lmezz/jei/api/IModRegistry;)V");
    }

    private static void addThermalExpansionCategory(Map<String, Set<MethodKey>> targets, String className, boolean hasGetRecipes) {
        add(targets, className, "register", "(Lmezz/jei/api/recipe/IRecipeCategoryRegistration;)V");
        add(targets, className, "initialize", "(Lmezz/jei/api/IModRegistry;)V");
        if (hasGetRecipes) {
            add(targets, className, "getRecipes", "(Lmezz/jei/api/IGuiHelper;)Ljava/util/List;");
        }
    }

    private static void addExtraTreesJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "binnie.extratrees.integration.jei.lumbermill.LumbermillRecipeMaker", "create", "(Lmezz/jei/api/IJeiHelpers;)Ljava/util/List;");
        add(targets, "binnie.extratrees.integration.jei.lumbermill.LumbermillRecipeWrapper", "<init>", "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)V");
        add(targets, "binnie.extratrees.integration.jei.fruitpress.FruitPressRecipeMaker", "create", "()Ljava/util/List;");
        add(targets, "binnie.extratrees.integration.jei.brewery.BreweryRecipeMaker", "create", "()Ljava/util/List;");
        add(targets, "binnie.extratrees.integration.jei.distillery.DistilleryRecipeMaker", "create", "()Ljava/util/List;");
    }

    private static void addEnvironmentalTechJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "com.valkyrieofnight.et.m_plugins.jei.multiblocks.voidminer.VoidMinerRecipeMaker", "getRecipes", "(Lcom/valkyrieofnight/et/m_multiblocks/m_voidminer/registry/ITargetableRegistry;Lmezz/jei/api/IJeiHelpers;)Ljava/util/List;");
    }

    private static void addForestryFactoryJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "forestry.factory.recipes.jei.bottler.BottlerRecipeMaker", "getBottlerRecipes", "(Lmezz/jei/api/ingredients/IIngredientRegistry;)Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.bottler.BottlerRecipeMaker", "hasDrainProperty", "(Lnet/minecraftforge/fluids/capability/IFluidHandler;)Z");
        add(targets, "forestry.factory.recipes.jei.bottler.BottlerRecipeMaker", "hasFillProperty", "(Lnet/minecraftforge/fluids/capability/IFluidHandler;)Z");
        add(targets, "forestry.factory.recipes.jei.bottler.BottlerRecipeWrapper", "<init>", "(Lnet/minecraft/item/ItemStack;Lnet/minecraftforge/fluids/FluidStack;Lnet/minecraft/item/ItemStack;Z)V");
        add(targets, "forestry.factory.recipes.jei.carpenter.CarpenterRecipeMaker", "getCarpenterRecipes", "()Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.centrifuge.CentrifugeRecipeMaker", "getCentrifugeRecipe", "()Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.fabricator.FabricatorRecipeMaker", "getFabricatorRecipes", "()Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.fermenter.FermenterRecipeMaker", "getFermenterRecipes", "(Lmezz/jei/api/recipe/IStackHelper;)Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.moistener.MoistenerRecipeMaker", "getMoistenerRecipes", "()Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.rainmaker.RainmakerRecipeMaker", "getRecipes", "()Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.squeezer.SqueezerRecipeMaker", "getSqueezerRecipes", "()Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.squeezer.SqueezerRecipeMaker", "getSqueezerContainerRecipes", "(Lmezz/jei/api/ingredients/IIngredientRegistry;)Ljava/util/List;");
        add(targets, "forestry.factory.recipes.jei.squeezer.SqueezerContainerRecipeWrapper", "<init>", "(Lforestry/factory/recipes/ISqueezerContainerRecipe;Lnet/minecraft/item/ItemStack;)V");
        add(targets, "forestry.factory.recipes.jei.still.StillRecipeMaker", "getStillRecipes", "()Ljava/util/List;");
    }

    private static void addEnderIOMachinesJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "crazypants.enderio.machines.integration.jei.AlloyRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.CombustionRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.CrafterRecipeTransferHandler", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.EnchanterRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.PainterRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.sagmill.SagMillRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.SagMillGrindingBallCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.SliceAndSpliceRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.SolarPanelRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.SoulBinderRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.StirlingRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.TankRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.VatRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.WiredChargerRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.WeatherObeliskRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.ZombieGeneratorRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.EnderGeneratorRecipeCategory", "register", "()V");
        add(targets, "crazypants.enderio.machines.integration.jei.LavaGeneratorRecipeCategory", "register", "()V");
    }

    private static void addProjectEJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "moze_intel.projecte.integration.jei.PEJeiPlugin", "refresh", "()V");
        add(targets, "moze_intel.projecte.integration.jei.PEJeiPlugin", "lambda$refresh$0", "()V");
        add(targets, "moze_intel.projecte.integration.jei.mappers.JEICompatMapper", "<init>", "(Ljava/lang/String;)V");
        add(targets, "moze_intel.projecte.integration.jei.mappers.JEICompatMapper", "clear", "()V");
        add(targets, "moze_intel.projecte.integration.jei.mappers.JEICompatMapper", "addRecipe", "(Lmezz/jei/api/recipe/IRecipeWrapper;)V");
        add(targets, "moze_intel.projecte.integration.jei.mappers.JEIFuelMapper", "refresh", "()V");
    }

    private static void addOpenComputersJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "li.cil.oc.integration.jei.ModPluginOpenComputers", "registerCategories", "(Lmezz/jei/api/recipe/IRecipeCategoryRegistration;)V");
        add(targets, "li.cil.oc.integration.jei.ModPluginOpenComputers", "register", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "li.cil.oc.integration.jei.ModPluginOpenComputers", "registerIngredients", "(Lmezz/jei/api/ingredients/IModIngredientRegistration;)V");
        add(targets, "li.cil.oc.integration.jei.ModPluginOpenComputers", "registerItemSubtypes", "(Lmezz/jei/api/ISubtypeRegistry;)V");
        add(targets, "li.cil.oc.integration.jei.ModPluginOpenComputers", "useNBT$1", "(Lscala/collection/Seq;Lmezz/jei/api/ISubtypeRegistry;)V");
        add(targets, "li.cil.oc.integration.jei.ModPluginOpenComputers$$anonfun$register$1", "apply", "(Lscala/Function0;)V");
    }

    private static void addExtraUtilitiesJeiTargets(Map<String, Set<MethodKey>> targets) {
        add(targets, "com.rwtema.extrautils2.crafting.jei.XUJEIPlugin", "registerItemSubtypes", "(Lmezz/jei/api/ISubtypeRegistry;)V");
        add(targets, "com.rwtema.extrautils2.crafting.jei.XUJEIPlugin", "register", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "com.rwtema.extrautils2.crafting.jei.XUJEIPlugin", "onRuntimeAvailable", "(Lmezz/jei/api/IJeiRuntime;)V");
        add(targets, "com.rwtema.extrautils2.crafting.jei.XUJEIPlugin", "lambda$register$0", "(Lcom/rwtema/extrautils2/api/machine/Machine;Lcom/rwtema/extrautils2/api/machine/IMachineRecipe;Lorg/apache/commons/lang3/tuple/Pair;)Lcom/rwtema/extrautils2/crafting/jei/JEIMachine$JEIMachineRecipe;");
        add(targets, "com.rwtema.extrautils2.crafting.jei.JEIMachine", "addJEI", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "com.rwtema.extrautils2.crafting.jei.JEIIndexerTransfer", "addJEI", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "com.rwtema.extrautils2.crafting.jei.JEIResonatorHandler", "addJEI", "(Lmezz/jei/api/IModRegistry;)V");
        add(targets, "com.rwtema.extrautils2.crafting.jei.JEITerraformerHandler", "addJEI", "(Lmezz/jei/api/IModRegistry;)V");
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
