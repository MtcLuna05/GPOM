package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class InitPhaseDeepProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.initPhaseDeepProfiler", "true"));
    private static final boolean AOA_FAST_CLIENT_EVENT_REGISTRATION = Boolean.parseBoolean(System.getProperty("gpom.aoa3FastClientEventRegistration", "true"));
    private static final boolean AOA_LAZY_STRUCTURES = Boolean.parseBoolean(System.getProperty("gpom.aoa3.lazyStructures", "true"));
    private static final boolean AOA_STRUCTURE_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.aoaStructureDetailProfiler", "false"));
    private static final boolean AOA_JER_CALL_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.aoaJerCallProfiler", "false"));
    private static final boolean ABYSSALCRAFT_NECRO_CALL_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.abyssalcraftNecroCallProfiler", "false"));
    private static final boolean ABYSSALCRAFT_LAZY_NECRO_ICON_VERIFY = Boolean.parseBoolean(System.getProperty("gpom.abyssalcraft.lazyNecroIconVerify", "true"));
    private static final boolean ABYSSALCRAFT_FAST_CRAFTING_STACK = Boolean.parseBoolean(System.getProperty("gpom.abyssalcraft.fastCraftingStack", "true"));
    private static final boolean OPENCOMPUTERS_CALL_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.openComputersCallProfiler", "false"));
    private static final boolean OPENCOMPUTERS_INTEGRATION_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.openComputersIntegrationProfiler", "false"));
    private static final boolean ENDERIO_IMC_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.enderioImcProfiler", "true"));
    private static final boolean ENDERIO_IMC_CALL_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.enderioImcCallProfiler", "false"));
    private static final boolean ENDERIO_ALLOY_CALL_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.enderioAlloyCallProfiler", "false"));
    private static final boolean ENDERIO_FAST_ALLOY_LOOKUP = Boolean.parseBoolean(System.getProperty("gpom.enderio.fastAlloyLookup", "true"));
    private static final boolean CRAFTTWEAKER_FAST_RECIPE_REMOVAL = Boolean.parseBoolean(System.getProperty("gpom.crafttweaker.fastRecipeRemoval", "true"));
    private static final boolean THERMAL_EXPANSION_FAST_SAWMILL_CRAFTING_RESULT = Boolean.parseBoolean(System.getProperty("gpom.thermalexpansion.fastSawmillCraftingResult", "true"));
    private static final boolean BUILDCRAFT_DEFER_GUIDE_RELOAD = Boolean.parseBoolean(System.getProperty("gpom.buildcraft.deferGuideReload", "true"));
    private static final boolean IMMERSIVE_ENGINEERING_DEFER_MANUAL_INDEX = Boolean.parseBoolean(System.getProperty("gpom.immersiveengineering.deferManualIndex", "true"));
    private static final boolean IMMERSIVE_ENGINEERING_LAZY_MANUAL_CRAFTING_PAGES = Boolean.parseBoolean(System.getProperty("gpom.immersiveengineering.lazyManualCraftingPages", "true"));
    private static final boolean POST_INIT_TOP_MOD_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.postInitTopModProfiler", "true"));
    private static final boolean POST_INIT_TOP_CALL_DETAIL = Boolean.parseBoolean(System.getProperty("gpom.postInitTopCallProfiler", "true"));
    private static final Map<String, Target> TARGETS = createTargets();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null || TargetedModVersions.isGpomClass(className)) {
            return basicClass;
        }

        Target target = TARGETS.get(className);
        if (target == null && isAoAStructureDetailTarget(className)) {
            target = Target.aoaStructureDetail();
        } else if (target == null && isOpenComputersIntegrationDetailTarget(className)) {
            target = Target.openComputersIntegrationDetail();
        } else if (target == null && isCraftTweakerActionDetailTarget(className)) {
            target = Target.craftTweakerActionDetail();
        } else if (target == null && isImmersiveEngineeringCompatDetailTarget(className)) {
            target = Target.immersiveEngineeringCompatDetail();
        } else if (target == null && isThermalExpansionPluginDetailTarget(className)) {
            target = Target.thermalExpansionPluginDetail();
        }
        if (target == null || !target.isAvailable(className)) {
            return basicClass;
        }

        if (AOA_FAST_CLIENT_EVENT_REGISTRATION
                && "net.tslat.aoa3.common.ClientProxy".equals(className)
                && TargetedModVersions.isAdventOfAscensionClass(className)) {
            basicClass = patchAoAClientProxyEvents(basicClass);
        }
        if (AOA_LAZY_STRUCTURES
                && "net.tslat.aoa3.structure.StructuresHandler".equals(className)
                && TargetedModVersions.isAdventOfAscensionClass(className)) {
            basicClass = patchAoAStructureRegistration(basicClass);
        }
        if (ABYSSALCRAFT_LAZY_NECRO_ICON_VERIFY
                && "com.shinoow.abyssalcraft.api.necronomicon.NecroData$Page".equals(className)
                && TargetedModVersions.isAbyssalCraftClass(className)) {
            basicClass = patchAbyssalCraftPageVerify(basicClass);
        }
        if (ABYSSALCRAFT_FAST_CRAFTING_STACK
                && "com.shinoow.abyssalcraft.api.necronomicon.CraftingStack".equals(className)
                && TargetedModVersions.isAbyssalCraftClass(className)) {
            basicClass = patchAbyssalCraftCraftingStack(basicClass);
        }
        if (ENDERIO_FAST_ALLOY_LOOKUP
                && "crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager".equals(className)
                && TargetedModVersions.isEnderIOClass(className)) {
            basicClass = patchEnderIOAlloyLookup(basicClass);
        }
        if (CRAFTTWEAKER_FAST_RECIPE_REMOVAL
                && isCraftTweakerRecipeRemovalAction(className)
                && TargetedModVersions.isCraftTweakerClass(className)) {
            basicClass = patchCraftTweakerRecipeRemovalAction(basicClass, className);
        }
        if (THERMAL_EXPANSION_FAST_SAWMILL_CRAFTING_RESULT
                && "cofh.thermalexpansion.util.managers.machine.SawmillManager".equals(className)
                && TargetedModVersions.isThermalExpansionClass(className)) {
            basicClass = patchThermalExpansionSawmillManager(basicClass);
        }
        if (BUILDCRAFT_DEFER_GUIDE_RELOAD
                && "buildcraft.lib.BCLibProxy$ClientProxy".equals(className)
                && TargetedModVersions.isBuildCraftCoreClass(className)) {
            basicClass = patchBuildCraftClientProxy(basicClass);
        }
        if (BUILDCRAFT_DEFER_GUIDE_RELOAD
                && "buildcraft.lib.client.guide.GuiGuide".equals(className)
                && TargetedModVersions.isBuildCraftCoreClass(className)) {
            basicClass = patchBuildCraftGuiGuide(basicClass);
        }
        if (IMMERSIVE_ENGINEERING_DEFER_MANUAL_INDEX
                && "blusunrize.immersiveengineering.client.ClientProxy".equals(className)
                && TargetedModVersions.isImmersiveEngineeringClass(className)) {
            basicClass = patchImmersiveEngineeringClientProxy(basicClass);
        }
        if (IMMERSIVE_ENGINEERING_DEFER_MANUAL_INDEX
                && "blusunrize.lib.manual.ManualInstance".equals(className)
                && TargetedModVersions.isImmersiveEngineeringClass(className)) {
            basicClass = patchImmersiveEngineeringManualInstance(basicClass);
        }
        if (IMMERSIVE_ENGINEERING_LAZY_MANUAL_CRAFTING_PAGES
                && ("blusunrize.lib.manual.ManualPages$Crafting".equals(className)
                || "blusunrize.lib.manual.ManualPages$CraftingMulti".equals(className))
                && TargetedModVersions.isImmersiveEngineeringClass(className)) {
            basicClass = patchImmersiveEngineeringManualCraftingPage(basicClass);
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            int writerFlags = "com.shinoow.abyssalcraft.api.necronomicon.CraftingStack".equals(className)
                    ? ClassWriter.COMPUTE_FRAMES
                    : ClassWriter.COMPUTE_MAXS;
            ClassWriter writer = new ClassWriter(reader, writerFlags);
            reader.accept(new ProfilingClassVisitor(writer, className, target), 0);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchAoAClientProxyEvents(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"registerClientEvents".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                InsnList instructions = new InsnList();
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/AoAPreInitOptimizations",
                        "registerClientEventsFastOrFallback",
                        "()V",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.RETURN));
                method.instructions = instructions;
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 1;
                method.maxStack = 0;
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchAoAStructureRegistration(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"registerStructures".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                java.util.List<String> structures = new java.util.ArrayList<>();
                for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof org.objectweb.asm.tree.TypeInsnNode) {
                        org.objectweb.asm.tree.TypeInsnNode typeInsn = (org.objectweb.asm.tree.TypeInsnNode) insn;
                        if (typeInsn.getOpcode() == Opcodes.NEW && typeInsn.desc.startsWith("net/tslat/aoa3/structure/")) {
                            structures.add(typeInsn.desc.replace('/', '.'));
                        }
                    }
                }
                if (structures.size() < 300) {
                    continue;
                }

                InsnList instructions = new InsnList();
                pushInt(instructions, structures.size());
                instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
                for (int i = 0; i < structures.size(); i++) {
                    instructions.add(new InsnNode(Opcodes.DUP));
                    pushInt(instructions, i);
                    instructions.add(new org.objectweb.asm.tree.LdcInsnNode(structures.get(i)));
                    instructions.add(new InsnNode(Opcodes.AASTORE));
                }
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/AoAStructureOptimizations",
                        "registerLazyStructures",
                        "([Ljava/lang/String;)V",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.RETURN));
                method.instructions = instructions;
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 0;
                method.maxStack = 4;
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static void pushInt(InsnList instructions, int value) {
        if (value >= -1 && value <= 5) {
            instructions.add(new InsnNode(Opcodes.ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.SIPUSH, value));
        } else {
            instructions.add(new org.objectweb.asm.tree.LdcInsnNode(value));
        }
    }

    private static byte[] patchAbyssalCraftPageVerify(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"verify".equals(method.name) || !"(Ljava/lang/Object;)Ljava/lang/Object;".equals(method.desc)) {
                    continue;
                }
                InsnList instructions = new InsnList();
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/AbyssalCraftNecronomiconOptimizations",
                        "verifyIconLazy",
                        "(Ljava/lang/Object;)Ljava/lang/Object;",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.ARETURN));
                method.instructions = instructions;
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 2;
                method.maxStack = 1;
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchAbyssalCraftCraftingStack(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name) || !"(Ljava/lang/Object;)V".equals(method.desc)) {
                    continue;
                }
                org.objectweb.asm.tree.LabelNode done = new org.objectweb.asm.tree.LabelNode();
                InsnList instructions = new InsnList();
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESPECIAL,
                        "java/lang/Object",
                        "<init>",
                        "()V",
                        false
                ));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 9));
                instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
                instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        "com/shinoow/abyssalcraft/api/necronomicon/CraftingStack",
                        "recipe",
                        "[Ljava/lang/Object;"
                ));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new org.objectweb.asm.tree.JumpInsnNode(Opcodes.IFNULL, done));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/AbyssalCraftNecronomiconOptimizations",
                        "convertToStack",
                        "(Ljava/lang/Object;)Lnet/minecraft/item/ItemStack;",
                        false
                ));
                instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ASTORE, 2));
                instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        "com/shinoow/abyssalcraft/api/necronomicon/CraftingStack",
                        "output",
                        "Lnet/minecraft/item/ItemStack;"
                ));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/AbyssalCraftNecronomiconOptimizations",
                        "recipeForOutput",
                        "(Lnet/minecraft/item/ItemStack;)[Ljava/lang/Object;",
                        false
                ));
                instructions.add(new org.objectweb.asm.tree.FieldInsnNode(
                        Opcodes.PUTFIELD,
                        "com/shinoow/abyssalcraft/api/necronomicon/CraftingStack",
                        "recipe",
                        "[Ljava/lang/Object;"
                ));
                instructions.add(done);
                instructions.add(new org.objectweb.asm.tree.FrameNode(
                        Opcodes.F_FULL,
                        3,
                        new Object[]{
                                "com/shinoow/abyssalcraft/api/necronomicon/CraftingStack",
                                "java/lang/Object",
                                Opcodes.TOP
                        },
                        0,
                        null
                ));
                instructions.add(new InsnNode(Opcodes.RETURN));
                method.instructions = instructions;
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 3;
                method.maxStack = 3;
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchEnderIOAlloyLookup(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("addRecipeToLookup".equals(method.name)
                        && "(Lcrazypants/enderio/base/recipe/lookup/TriItemLookup;Lcrazypants/enderio/base/recipe/IManyToOneRecipe;)V".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "addRecipeToLookup",
                            "(Lcrazypants/enderio/base/recipe/lookup/TriItemLookup;Lcrazypants/enderio/base/recipe/IManyToOneRecipe;)V",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.RETURN));
                    replaceMethod(method, instructions, 2, 2);
                    changed = true;
                } else if ("getRecipeForInputs".equals(method.name)
                        && "(Lcrazypants/enderio/base/recipe/RecipeLevel;Lcom/enderio/core/common/util/NNList;)Lcrazypants/enderio/base/recipe/IRecipe;".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "getRecipeForInputs",
                            "(Ljava/lang/Object;Lcrazypants/enderio/base/recipe/RecipeLevel;Lcom/enderio/core/common/util/NNList;)Lcrazypants/enderio/base/recipe/IRecipe;",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.ARETURN));
                    replaceMethod(method, instructions, 3, 3);
                    changed = true;
                } else if ("isValidInput".equals(method.name)
                        && "(Lcrazypants/enderio/base/recipe/RecipeLevel;Lcrazypants/enderio/base/recipe/MachineRecipeInput;)Z".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "isValidInput",
                            "(Ljava/lang/Object;Lcrazypants/enderio/base/recipe/RecipeLevel;Lcrazypants/enderio/base/recipe/MachineRecipeInput;)Z",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.IRETURN));
                    replaceMethod(method, instructions, 3, 3);
                    changed = true;
                } else if ("isValidRecipeComponents".equals(method.name)
                        && "(Lcrazypants/enderio/base/recipe/RecipeLevel;Lcom/enderio/core/common/util/NNList;)Z".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 2));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "isValidRecipeComponents",
                            "(Ljava/lang/Object;Lcrazypants/enderio/base/recipe/RecipeLevel;Lcom/enderio/core/common/util/NNList;)Z",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.IRETURN));
                    replaceMethod(method, instructions, 3, 3);
                    changed = true;
                } else if ("getExperienceForOutput".equals(method.name)
                        && "(Lnet/minecraft/item/ItemStack;)F".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 1));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "getExperienceForOutput",
                            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;)F",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.FRETURN));
                    replaceMethod(method, instructions, 2, 2);
                    changed = true;
                } else if ("getRecipes".equals(method.name)
                        && "()Lcom/enderio/core/common/util/NNList;".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "getRecipes",
                            "(Ljava/lang/Object;)Lcom/enderio/core/common/util/NNList;",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.ARETURN));
                    replaceMethod(method, instructions, 1, 1);
                    changed = true;
                } else if ("rebuild".equals(method.name) && "()I".equals(method.desc)) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/EnderIOAlloyRecipeOptimizations",
                            "rebuild",
                            "(Ljava/lang/Object;)I",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.IRETURN));
                    replaceMethod(method, instructions, 1, 1);
                    changed = true;
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static boolean isCraftTweakerRecipeRemovalAction(String className) {
        return "crafttweaker.mc1120.recipes.MCRecipeManager$ActionRemoveShapedRecipes".equals(className)
                || "crafttweaker.mc1120.recipes.MCRecipeManager$ActionRemoveShapelessRecipes".equals(className)
                || "crafttweaker.mc1120.recipes.MCRecipeManager$ActionRemoveRecipesNoIngredients".equals(className);
    }

    private static byte[] patchCraftTweakerRecipeRemovalAction(byte[] basicClass, String className) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            String helperMethod;
            if (className.endsWith("$ActionRemoveShapedRecipes")) {
                helperMethod = "applyRemoveShaped";
            } else if (className.endsWith("$ActionRemoveShapelessRecipes")) {
                helperMethod = "applyRemoveShapeless";
            } else if (className.endsWith("$ActionRemoveRecipesNoIngredients")) {
                helperMethod = "applyRemoveNoIngredients";
            } else {
                return basicClass;
            }
            for (MethodNode method : node.methods) {
                if (!"apply".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                InsnList instructions = new InsnList();
                instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/CraftTweakerRecipeRemovalOptimizations",
                        helperMethod,
                        "(Ljava/lang/Object;)V",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.RETURN));
                replaceMethod(method, instructions, 1, 1);
                changed = true;
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchThermalExpansionSawmillManager(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"initialize".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                            && "cofh/core/util/helpers/ItemHelper".equals(methodInsn.owner)
                            && "getCraftingResult".equals(methodInsn.name)
                            && "(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Lnet/minecraft/item/ItemStack;".equals(methodInsn.desc)) {
                        methodInsn.owner = "com/l/gpom/optimization/ThermalExpansionRecipeOptimizations";
                        methodInsn.name = "getSawmillCraftingResult";
                        methodInsn.itf = false;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchBuildCraftClientProxy(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"fmlPostInit".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.getOpcode() == Opcodes.INVOKEINTERFACE
                            && "net/minecraft/client/resources/IReloadableResourceManager".equals(methodInsn.owner)
                            && "func_110542_a".equals(methodInsn.name)
                            && "(Lnet/minecraft/client/resources/IResourceManagerReloadListener;)V".equals(methodInsn.desc)) {
                        methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                        methodInsn.owner = "com/l/gpom/optimization/BuildCraftGuideOptimizations";
                        methodInsn.name = "registerGuideReloadListenerDeferred";
                        methodInsn.desc = "(Lnet/minecraft/client/resources/IReloadableResourceManager;Lnet/minecraft/client/resources/IResourceManagerReloadListener;)V";
                        methodInsn.itf = false;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchBuildCraftGuiGuide(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name) || !"(Lbuildcraft/lib/guide/GuideBook;)V".equals(method.desc)) {
                    continue;
                }
                for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.getOpcode() == Opcodes.INVOKESPECIAL
                            && "net/minecraft/client/gui/GuiScreen".equals(methodInsn.owner)
                            && "<init>".equals(methodInsn.name)
                            && "()V".equals(methodInsn.desc)) {
                        InsnList instructions = new InsnList();
                        instructions.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/BuildCraftGuideOptimizations",
                                "ensureGuideReady",
                                "()V",
                                false
                        ));
                        method.instructions.insert(methodInsn, instructions);
                        changed = true;
                        break;
                    }
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchImmersiveEngineeringClientProxy(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"postInitEnd".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && "blusunrize/lib/manual/ManualInstance".equals(methodInsn.owner)
                            && "indexRecipes".equals(methodInsn.name)
                            && "()V".equals(methodInsn.desc)) {
                        methodInsn.setOpcode(Opcodes.INVOKESTATIC);
                        methodInsn.owner = "com/l/gpom/optimization/ImmersiveEngineeringManualOptimizations";
                        methodInsn.name = "deferManualIndex";
                        methodInsn.desc = "(Ljava/lang/Object;)V";
                        methodInsn.itf = false;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchImmersiveEngineeringManualInstance(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (("getGui".equals(method.name) && "()Lblusunrize/lib/manual/gui/GuiManual;".equals(method.desc))
                        || ("getManualLink".equals(method.name) && "(Lnet/minecraft/item/ItemStack;)Lblusunrize/lib/manual/ManualInstance$ManualLink;".equals(method.desc))) {
                    InsnList instructions = new InsnList();
                    instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ImmersiveEngineeringManualOptimizations",
                            "ensureManualIndex",
                            "(Ljava/lang/Object;)V",
                            false
                    ));
                    method.instructions.insert(instructions);
                    changed = true;
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchImmersiveEngineeringManualCraftingPage(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name)
                        || !"(Lblusunrize/lib/manual/ManualInstance;Ljava/lang/String;[Ljava/lang/Object;)V".equals(method.desc)) {
                    continue;
                }

                for (org.objectweb.asm.tree.AbstractInsnNode instruction = method.instructions.getFirst();
                     instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) instruction;
                    if (!"recalculateCraftingRecipes".equals(methodInsn.name) || !"()V".equals(methodInsn.desc)) {
                        continue;
                    }
                    org.objectweb.asm.tree.AbstractInsnNode previous = instruction.getPrevious();
                    if (previous != null && previous.getOpcode() == Opcodes.ALOAD) {
                        method.instructions.remove(previous);
                    }
                    method.instructions.remove(instruction);
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static void replaceMethod(MethodNode method, InsnList instructions, int maxLocals, int maxStack) {
        method.instructions = instructions;
        method.tryCatchBlocks.clear();
        method.localVariables.clear();
        method.maxLocals = maxLocals;
        method.maxStack = maxStack;
    }

    private static Map<String, Target> createTargets() {
        Map<String, Target> targets = new HashMap<>();

        add(targets, ModTarget.AOA, "net.tslat.aoa3.advent.AdventOfAscension",
                "fmlPreInit", "registerEvents", "registerCapabilities", "fmlInit");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.utils.ModUtil", "preInitTasks");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.common.ServerProxy", "preInit");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.common.ClientProxy", "preInit", "registerClientEvents");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.utils.PacketUtil", "init", "registerPackets");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.common.registration.LootSystemRegister", "<clinit>", "<init>", "registerCustomObjects");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.common.registration.DimensionRegister", "<clinit>", "preInit");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.common.registration.AdvancementTriggerRegister", "<clinit>", "registerTriggers");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.hooks.ThirdPartyInteractions", "preInit", "init");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.structure.StructuresHandler", "<clinit>", "registerStructures", "registerStructure");
        add(targets, ModTarget.AOA, "net.tslat.aoa3.hooks.jer.JerHooks",
                "<clinit>", "init", "integrateWorldGen", "integrateCrops", "integrateDungeonLoot", "integrateMobDrops");

        add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.client.ClientProxy",
                "postInit", "postInitEnd");
        add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.lib.manual.ManualInstance",
                "getGui", "getManualLink", "indexRecipes");
        add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.lib.manual.ManualPages$Crafting",
                "<init>", "recalculateCraftingRecipes", "initPage", "renderPage", "listForSearch");
        add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.lib.manual.ManualPages$CraftingMulti",
                "<init>", "recalculateCraftingRecipes", "initPage", "renderPage", "listForSearch");

        add(targets, ModTarget.ABYSSALCRAFT, "com.shinoow.abyssalcraft.AbyssalCraft", "Init", "lambda$Init$1");
        add(targets, ModTarget.ABYSSALCRAFT, "com.shinoow.abyssalcraft.init.MiscHandler", "init");
        add(targets, ModTarget.ABYSSALCRAFT, "com.shinoow.abyssalcraft.common.handlers.InternalNecroDataHandler",
                "registerInternalPages", "addPages", "addInternalPages", "setupPatreonData");
        add(targets, ModTarget.ABYSSALCRAFT, "com.shinoow.abyssalcraft.api.necronomicon.CraftingStack",
                "<init>", "getCraftingRecipe", "getFirstArray", "getSecondArray", "getThirdArray");
        add(targets, ModTarget.ABYSSALCRAFT, "com.shinoow.abyssalcraft.api.necronomicon.NecroData$Page",
                "<init>", "verify");

        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.OpenComputers", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.OpenComputers$", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.common.Proxy", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.client.Proxy", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.integration.Mods$",
                "<clinit>", "<init>", "init", "li$cil$oc$integration$Mods$$tryInit");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.common.Loot$", "<clinit>", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.common.Achievement$", "<clinit>", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.common.capabilities.Capabilities", "<clinit>", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.client.ColorHandler$", "<clinit>", "init");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.common.recipe.Recipes$",
                "<clinit>", "init", "parseIngredient", "parseFluidIngredient", "li$cil$oc$common$recipe$Recipes$$addRecipe", "addRecipe");
        add(targets, ModTarget.OPENCOMPUTERS, "li.cil.oc.integration.minecraft.RecipeHandler$",
                "<clinit>", "init", "addShapedRecipe", "addShapelessRecipe", "addFurnaceRecipe");

        if (ENDERIO_IMC_DETAIL) {
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.EnderIO",
                    "onImc", "processImc");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.material.recipes.MaterialOredicts",
                    "<clinit>", "init", "checkOreRegistrations");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.RecipeLoader",
                    "<clinit>", "addRecipes", "handleIMCRecipes", "readUserFile", "readCoreFile", "addIMCRecipe", "recipeError");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.RecipeFactory",
                    "<init>", "getResource", "placeXSD", "createFolder", "listXMLFiles", "readCoreFile", "copyCore",
                    "createFileUser", "readFileUser", "readFileIMC", "printContentsOnError", "readStax", "cleanFolder",
                    "copyCore_dontMakeShittyCoreModsPlease_thisIncludesShittyMixins");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Aliases",
                    "<clinit>", "<init>", "readResolve", "register", "addRecipes", "setAttribute", "setElement", "enforceValidity");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Recipes",
                    "<clinit>", "<init>", "readResolve", "register", "unregister", "addRecipes", "setAttribute", "setElement",
                    "addRecipe", "enforceValidity");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Recipe",
                    "<clinit>", "<init>", "readResolve", "register", "unregister", "setAttribute", "setElement",
                    "enforceValidity", "get");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Crafting",
                    "<clinit>", "<init>", "readResolve", "register", "setAttribute", "setElement", "enforceValidity", "mkRL");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Sagmilling",
                    "<clinit>", "<init>", "readResolve", "register", "setAttribute", "setElement", "enforceValidity");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Alloying",
                    "<clinit>", "<init>", "readResolve", "register", "setAttribute", "setElement", "enforceValidity");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.AbstractCrafting",
                    "<clinit>", "<init>", "readResolve", "register", "setAttribute", "setElement", "enforceValidity",
                    "getOutput", "getOutputs", "checkOutputCount");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.ItemIntegerAmount",
                    "<clinit>", "<init>", "readResolve", "getThing", "getAmount", "isSame", "isValid", "enforceValidity",
                    "setAllowDelaying", "setAttribute", "setElement");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.config.recipes.xml.Output",
                    "<clinit>", "<init>", "readResolve", "getItemStack", "setAttribute", "setElement", "enforceValidity");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.MachineRecipeRegistry",
                    "<clinit>", "<init>", "addRecipe", "addRecipesForInput", "registerRecipe", "getRecipesForInput");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.sagmill.SagMillRecipeManager",
                    "<clinit>", "<init>", "addRecipe", "addRecipesForInput", "getInstance");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager",
                    "<clinit>", "<init>", "getInstance", "create", "remap", "addRecipe", "addDedupedRecipe",
                    "addSyntheticRecipe", "needsSynthetics", "addRecipeToLookup", "addJEIIntegration", "dupeCheckRecipe",
                    "getRecipeForInputs", "isValidInput", "isValidRecipeComponents", "getExperienceForOutput", "getRecipes",
                    "rebuild");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.BasicManyToOneRecipe",
                    "<clinit>", "<init>", "isValidRecipeComponents", "getOutput", "isValidInput", "isValid",
                    "getEnergyRequired", "getBonusType", "getOutputs", "getInputStacks", "isInputForRecipe", "getInputs",
                    "getInputFluidStacks", "getRecipeComponentFromInput", "getInputStackAlternatives", "isSynthetic",
                    "setSynthetic", "isDedupeInput", "setDedupeInput", "getRecipeLevel");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.Recipe",
                    "<clinit>", "<init>", "isInputForRecipe", "isAnyInput", "getMinNumInputs", "isValidInput",
                    "getInputForStack", "getInputStacks", "getInputStackAlternatives", "getInputFluidStacks",
                    "getInputs", "getOutputs", "hasOuput", "getEnergyRequired", "isValid", "isSynthetic",
                    "getRecipeLevel");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.RecipeInput",
                    "<clinit>", "<init>", "getInput", "isInput", "getEquivelentInputs", "getMulitplier", "getSlotNumber",
                    "copy", "setCount", "getStackSize");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.ThingsRecipeInput",
                    "<clinit>", "<init>", "getInput", "isInput", "getEquivelentInputs", "getMulitplier", "getSlotNumber",
                    "copy", "setCount", "getStackSize");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.lookup.TriItemLookup",
                    "<clinit>", "<init>", "makeNode1", "makeNode2", "makeNode3", "addRecipe", "getRecipesL",
                    "getRecipesLMRI", "getRecipes3", "getRecipes", "iterator");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.lookup.ItemRecipeNode",
                    "<clinit>", "<init>", "getRecipes", "getNext", "makeNext", "iterator");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.recipe.lookup.ItemRecipeLeafNode",
                    "<clinit>", "<init>", "getRecipes", "addRecipe");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.capacitor.CapacitorKeyRegistry",
                    "<clinit>", "<init>", "validate", "add", "register");
            add(targets, ModTarget.ENDERIO, "crazypants.enderio.base.integration.railcraft.RailcraftUtil",
                    "<clinit>", "registerFuels");
        }
        if (POST_INIT_TOP_MOD_DETAIL) {
            add(targets, ModTarget.CRAFTTWEAKER, "crafttweaker.mc1120.CraftTweaker",
                    "onPostInit", "onFMLLoadComplete", "applyActions");
            add(targets, ModTarget.CRAFTTWEAKER, "crafttweaker.CraftTweakerAPI",
                    "<clinit>", "registerClass", "registerGlobalSymbol", "registerBracketHandler");
            add(targets, ModTarget.CRAFTTWEAKER, "crafttweaker.mc1120.proxies.CommonProxy",
                    "registerReloadListener");
            add(targets, ModTarget.CRAFTTWEAKER, "crafttweaker.runtime.ScriptLoader",
                    "<clinit>", "<init>", "loadScript", "loadScriptByName", "execute");
            add(targets, ModTarget.CRAFTTWEAKER, "crafttweaker.runtime.providers.ScriptProviderDirectory",
                    "<clinit>", "<init>", "getScripts", "getRoot");

            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.ImmersiveEngineering",
                    "postInit", "loadComplete");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.CommonProxy",
                    "postInit", "postInitEnd");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.client.ClientProxy",
                    "postInit", "postInitEnd", "handleMineralManual", "formatToTable_ExcavatorMinerals",
                    "addChangelogToManual", "addVersionToManual", "formatToTable_ItemIntHashmap");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.lib.manual.ManualInstance",
                    "getGui", "getManualLink", "indexRecipes");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.IEContent",
                    "<clinit>", "postInit");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.util.compat.IECompatModule",
                    "<clinit>", "doModulesPostInit", "doModulesLoadComplete", "postInit", "loadComplete");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.IERecipes",
                    "<clinit>", "postInitOreDictRecipes", "addOreProcessingRecipe", "addOreDictCrusherRecipe",
                    "addItemToOreDictCrusherRecipe", "addOreDictAlloyingRecipe", "addOreDictArcAlloyingRecipe");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.util.IEPotions",
                    "<clinit>", "init");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.util.IEVillagerHandler",
                    "<clinit>", "initIEVillagerTrades");
            add(targets, ModTarget.IMMERSIVEENGINEERING, "blusunrize.immersiveengineering.common.util.commands.CommandShaders",
                    "<clinit>", "init");

            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.BCLib",
                    "postInit", "registerTag", "startBatch", "endBatch");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.BCLibProxy$ClientProxy",
                    "fmlPostInit");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.client.guide.GuiGuide",
                    "<clinit>", "<init>");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.gui.config.GuiConfigManager",
                    "<clinit>", "loadFromConfigFile", "readFromJson", "writeToJson");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.core.BCCore",
                    "postInit", "registerTag", "startBatch", "endBatch");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.BCLibRegistries",
                    "<clinit>", "fmlPostInit", "fmlInit", "fmlPreInit", "postInit", "reloadRegistries");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.registry.TagManager",
                    "<clinit>", "getItem", "getTag", "hasTag", "getMultiTag", "registerTag", "startBatch", "endBatch",
                    "prependTag", "prependTags", "set", "setTab");
            add(targets, ModTarget.BUILDCRAFT, "buildcraft.lib.registry.RegistryConfig",
                    "<clinit>", "isEnabled", "hasItemBeenDisabled", "hasBlockBeenDisabled", "hasObjectBeenDisabled",
                    "getCategory", "getMod", "getActiveMod");

            add(targets, ModTarget.THAUMCRAFT, "thaumcraft.common.Thaumcraft",
                    "postInit");
            add(targets, ModTarget.THAUMCRAFT, "thaumcraft.proxies.CommonProxy",
                    "postInit");
            add(targets, ModTarget.THAUMCRAFT, "thaumcraft.common.config.ConfigAspects",
                    "postInit", "registerItemAspects");
            add(targets, ModTarget.THAUMCRAFT, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager",
                    "getObjectTags", "generateTags", "generateTagsFromCrucibleRecipes", "generateTagsFromInfusionRecipes",
                    "generateTagsFromCraftingRecipes", "getAspectsFromIngredients", "generateTagsFromRecipes");

            add(targets, ModTarget.THERMAL_EXPANSION, "cofh.thermalexpansion.ThermalExpansion",
                    "postInit", "loadComplete", "initManagers", "refreshManagers", "registerHandlers");
            add(targets, ModTarget.THERMAL_EXPANSION, "cofh.thermalexpansion.proxy.Proxy",
                    "postInit");
            add(targets, ModTarget.THERMAL_EXPANSION, "cofh.thermalexpansion.init.TEPlugins",
                    "<clinit>", "preInit", "initialize");
            add(targets, ModTarget.THERMAL_EXPANSION, "cofh.thermalexpansion.util.managers.machine.SawmillManager",
                    "<clinit>", "initialize", "addRecipe", "addRecycleRecipe");

            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.ExpandedEquivalence",
                    "<init>", "construct", "preInit", "init", "postInit", "loadComplete",
                    "lambda$loadComplete$5", "lambda$postInit$4", "lambda$preInit$3", "lambda$preInit$0");
            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.exp.Expansion",
                    "<clinit>", "<init>", "createExpansionList", "registerExpansion", "preInit$", "preInit",
                    "init", "postInit", "registerEMC", "getMappers", "getConfig");
            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.api.js.JSExpansion",
                    "<clinit>", "<init>", "invoke", "shouldBeEnabled", "contruct", "getCfgEMC",
                    "postInit", "getMappers", "getLogger");
            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.api.js.JSExpansion$MapperAcceptor",
                    "<init>", "addMapper");
            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.api.js.JSExpansion$MapperAcceptor$JSEMCMapper",
                    "<init>", "getName", "register", "doLogRegistration");
            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.exp.hammercore.AbstractEMCMapper",
                    "<clinit>", "<init>", "register", "lambda$register$3", "lambda$null$2",
                    "lambda$register$1", "lambda$register$0");
            add(targets, ModTarget.EXPANDED_EQUIVALENCE, "tk.zeitheron.expequiv.api.IEMC",
                    "<clinit>", "map", "multiMap", "register", "fake");
        }

        return targets;
    }

    private static void add(Map<String, Target> targets, ModTarget modTarget, String className, String... methods) {
        targets.put(className, new Target(modTarget, new HashSet<>(Arrays.asList(methods))));
    }

    private static boolean isAoAStructureDetailTarget(String className) {
        return AOA_STRUCTURE_DETAIL
                && className.startsWith("net.tslat.aoa3.structure.")
                && className.indexOf('$') < 0
                && !className.equals("net.tslat.aoa3.structure.AoAStructure")
                && !className.equals("net.tslat.aoa3.structure.StructuresHandler")
                && TargetedModVersions.isAdventOfAscensionClass(className);
    }

    private static boolean isOpenComputersIntegrationDetailTarget(String className) {
        return OPENCOMPUTERS_INTEGRATION_DETAIL
                && className.startsWith("li.cil.oc.integration.")
                && !className.contains("$$anon")
                && !className.contains("$anon")
                && !className.equals("li.cil.oc.integration.ModProxy")
                && !className.equals("li.cil.oc.integration.Mods")
                && !className.equals("li.cil.oc.integration.Mods$")
                && TargetedModVersions.isOpenComputersClass(className);
    }

    private static boolean isCraftTweakerActionDetailTarget(String className) {
        return POST_INIT_TOP_MOD_DETAIL
                && className.startsWith("crafttweaker.mc1120.")
                && (className.contains(".actions.")
                || className.contains(".recipes.")
                || className.contains(".brewing.")
                || className.contains(".liquid."))
                && TargetedModVersions.isCraftTweakerClass(className);
    }

    private static boolean isImmersiveEngineeringCompatDetailTarget(String className) {
        return POST_INIT_TOP_MOD_DETAIL
                && className.startsWith("blusunrize.immersiveengineering.common.util.compat.")
                && !className.contains(".jei.")
                && !className.endsWith(".IECompatModule")
                && TargetedModVersions.isImmersiveEngineeringClass(className);
    }

    private static boolean isThermalExpansionPluginDetailTarget(String className) {
        return POST_INIT_TOP_MOD_DETAIL
                && className.startsWith("cofh.thermalexpansion.plugins.")
                && !className.contains(".jei.")
                && TargetedModVersions.isThermalExpansionClass(className);
    }

    private static boolean isPostInitTopCallDetailMethod(String className, String methodName) {
        if (className == null || methodName == null) {
            return false;
        }
        if (className.equals("crafttweaker.mc1120.CraftTweaker")) {
            return methodName.equals("onPostInit") || methodName.equals("applyActions");
        }
        if (className.equals("blusunrize.immersiveengineering.ImmersiveEngineering")
                || className.equals("blusunrize.immersiveengineering.common.CommonProxy")
                || className.equals("blusunrize.immersiveengineering.client.ClientProxy")
                || className.equals("blusunrize.immersiveengineering.common.util.compat.IECompatModule")) {
            return methodName.equals("postInit") || methodName.equals("postInitEnd")
                    || methodName.equals("handleMineralManual") || methodName.equals("addChangelogToManual")
                    || methodName.equals("addVersionToManual") || methodName.equals("formatToTable_ExcavatorMinerals")
                    || methodName.equals("formatToTable_ItemIntHashmap") || methodName.equals("doModulesPostInit")
                    || methodName.equals("loadComplete") || methodName.equals("doModulesLoadComplete");
        }
        if (className.equals("buildcraft.lib.BCLib")
                || className.equals("buildcraft.lib.BCLibProxy$ClientProxy")
                || className.equals("buildcraft.lib.gui.config.GuiConfigManager")) {
            return methodName.equals("postInit") || methodName.equals("fmlPostInit")
                    || methodName.equals("loadFromConfigFile") || methodName.equals("readFromJson")
                    || methodName.equals("writeToJson");
        }
        if (className.equals("cofh.thermalexpansion.ThermalExpansion")) {
            return false;
        }
        if (className.equals("thaumcraft.common.config.ConfigAspects")
                || className.equals("thaumcraft.proxies.CommonProxy")) {
            return methodName.equals("postInit") || methodName.equals("registerItemAspects");
        }
        if (className.equals("tk.zeitheron.expequiv.ExpandedEquivalence")) {
            return methodName.equals("postInit") || methodName.equals("loadComplete")
                    || methodName.equals("lambda$loadComplete$5") || methodName.equals("lambda$postInit$4");
        }
        if (className.equals("tk.zeitheron.expequiv.api.js.JSExpansion")
                || className.equals("tk.zeitheron.expequiv.api.js.JSExpansion$MapperAcceptor")
                || className.equals("tk.zeitheron.expequiv.api.js.JSExpansion$MapperAcceptor$JSEMCMapper")
                || className.equals("tk.zeitheron.expequiv.exp.hammercore.AbstractEMCMapper")) {
            return methodName.equals("postInit") || methodName.equals("getMappers")
                    || methodName.equals("register") || methodName.equals("addMapper")
                    || methodName.equals("invoke");
        }
        return false;
    }

    private enum ModTarget {
        AOA("AOA") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isAdventOfAscensionClass(className);
            }
        },
        ABYSSALCRAFT("ABYSS") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isAbyssalCraftClass(className);
            }
        },
        OPENCOMPUTERS("OC") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isOpenComputersClass(className);
            }
        },
        ENDERIO("EIO") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isEnderIOClass(className);
            }
        },
        CRAFTTWEAKER("CT") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isCraftTweakerClass(className);
            }
        },
        IMMERSIVEENGINEERING("IE") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isImmersiveEngineeringClass(className);
            }
        },
        BUILDCRAFT("BC") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isBuildCraftCoreClass(className);
            }
        },
        THAUMCRAFT("TC") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isThaumcraftClass(className);
            }
        },
        THERMAL_EXPANSION("TE") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isThermalExpansionClass(className);
            }
        },
        EXPANDED_EQUIVALENCE("EXPEQUIV") {
            @Override
            boolean isAvailable(String className) {
                return TargetedModVersions.isExpandedEquivalenceClass(className);
            }
        };

        private final String labelPrefix;

        ModTarget(String labelPrefix) {
            this.labelPrefix = labelPrefix;
        }

        abstract boolean isAvailable(String className);
    }

    private static final class Target {
        private final ModTarget modTarget;
        private final Set<String> methods;
        private final boolean aoaStructureDetail;

        private Target(ModTarget modTarget, Set<String> methods) {
            this(modTarget, methods, false);
        }

        private Target(ModTarget modTarget, Set<String> methods, boolean aoaStructureDetail) {
            this.modTarget = modTarget;
            this.methods = methods;
            this.aoaStructureDetail = aoaStructureDetail;
        }

        private static Target aoaStructureDetail() {
            return new Target(ModTarget.AOA, new HashSet<>(Arrays.asList("<clinit>", "<init>")), true);
        }

        private static Target openComputersIntegrationDetail() {
            return new Target(ModTarget.OPENCOMPUTERS, new HashSet<>(Arrays.asList("<clinit>", "<init>", "initialize")));
        }

        private static Target craftTweakerActionDetail() {
            return new Target(ModTarget.CRAFTTWEAKER, new HashSet<>(Arrays.asList("<clinit>", "<init>", "apply", "describe")));
        }

        private static Target immersiveEngineeringCompatDetail() {
            return new Target(ModTarget.IMMERSIVEENGINEERING, new HashSet<>(Arrays.asList("<clinit>", "<init>", "postInit", "loadComplete", "registerRecipes")));
        }

        private static Target thermalExpansionPluginDetail() {
            return new Target(ModTarget.THERMAL_EXPANSION, new HashSet<>(Arrays.asList("<clinit>", "<init>", "preInit", "initialize", "initializeDelegate")));
        }

        private boolean isAvailable(String className) {
            return modTarget.isAvailable(className);
        }
    }

    private static final class ProfilingClassVisitor extends ClassVisitor {
        private final String className;
        private final Target target;

        private ProfilingClassVisitor(ClassVisitor delegate, String className, Target target) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
            this.target = target;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null || !target.methods.contains(name)) {
                return visitor;
            }
            String[] labels = labelsFor(className, target, name, desc);
            boolean profileJerCalls = AOA_JER_CALL_DETAIL && className.equals("net.tslat.aoa3.hooks.jer.JerHooks");
            boolean profileAoAStructureInstantiations = AOA_STRUCTURE_DETAIL
                    && className.equals("net.tslat.aoa3.structure.StructuresHandler")
                    && name.equals("registerStructures");
            boolean profileAbyssalCraftCalls = ABYSSALCRAFT_NECRO_CALL_DETAIL
                    && (className.equals("com.shinoow.abyssalcraft.common.handlers.InternalNecroDataHandler")
                    || className.equals("com.shinoow.abyssalcraft.api.necronomicon.NecroData$Page"));
            boolean profileOpenComputersCalls = OPENCOMPUTERS_CALL_DETAIL
                    && (className.equals("li.cil.oc.common.Proxy")
                    || className.equals("li.cil.oc.client.Proxy")
                    || className.equals("li.cil.oc.integration.Mods$"));
            boolean profileEnderIOImcCalls = ENDERIO_IMC_CALL_DETAIL
                    && className.equals("crazypants.enderio.base.EnderIO")
                    && name.equals("onImc");
            boolean profileEnderIOAlloyCalls = ENDERIO_ALLOY_CALL_DETAIL
                    && (className.equals("crazypants.enderio.base.config.recipes.xml.Alloying")
                    || className.equals("crazypants.enderio.base.recipe.alloysmelter.AlloyRecipeManager"));
            boolean profilePostInitTopCalls = POST_INIT_TOP_CALL_DETAIL && isPostInitTopCallDetailMethod(className, name);
            return new TimedMethodVisitor(
                    visitor,
                    labels,
                    profileJerCalls,
                    profileAoAStructureInstantiations,
                    profileAbyssalCraftCalls,
                    profileOpenComputersCalls,
                    profileEnderIOImcCalls,
                    profileEnderIOAlloyCalls,
                    profilePostInitTopCalls);
        }

        private static String[] labelsFor(String className, Target target, String name, String desc) {
            String precise = target.modTarget.labelPrefix + ' ' + className + '.' + name + desc;
            if (!target.aoaStructureDetail) {
                return new String[] {precise};
            }

            String simpleMethod = name.equals("<clinit>") ? "<clinit>" : "<init>";
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packageLabel = "AOA structure package " + packageName.substring("net.tslat.aoa3.structure.".length()) + '.' + simpleMethod;
            String allLabel = "AOA structure all " + simpleMethod;
            return new String[] {allLabel, packageLabel, precise};
        }
    }

    private static final class TimedMethodVisitor extends MethodVisitor {
        private final String[] labels;
        private final boolean profileJerCalls;
        private final boolean profileAoAStructureInstantiations;
        private final boolean profileAbyssalCraftCalls;
        private final boolean profileOpenComputersCalls;
        private final boolean profileEnderIOImcCalls;
        private final boolean profileEnderIOAlloyCalls;
        private final boolean profilePostInitTopCalls;
        private boolean entered;
        private String pendingAoAStructureType;

        private TimedMethodVisitor(
                MethodVisitor delegate,
                String[] labels,
                boolean profileJerCalls,
                boolean profileAoAStructureInstantiations,
                boolean profileAbyssalCraftCalls,
                boolean profileOpenComputersCalls,
                boolean profileEnderIOImcCalls,
                boolean profileEnderIOAlloyCalls,
                boolean profilePostInitTopCalls) {
            super(Opcodes.ASM9, delegate);
            this.labels = labels;
            this.profileJerCalls = profileJerCalls;
            this.profileAoAStructureInstantiations = profileAoAStructureInstantiations;
            this.profileAbyssalCraftCalls = profileAbyssalCraftCalls;
            this.profileOpenComputersCalls = profileOpenComputersCalls;
            this.profileEnderIOImcCalls = profileEnderIOImcCalls;
            this.profileEnderIOAlloyCalls = profileEnderIOAlloyCalls;
            this.profilePostInitTopCalls = profilePostInitTopCalls;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            entered = true;
            beginLabels(labels);
        }

        @Override
        public void visitInsn(int opcode) {
            if (entered && isExit(opcode)) {
                endLabels(labels);
            }
            super.visitInsn(opcode);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String callLabel = jerCallLabel(opcode, owner, name, desc);
            if (callLabel != null) {
                beginLabel(callLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabel(callLabel);
                return;
            }
            callLabel = abyssalCraftCallLabel(opcode, owner, name, desc);
            if (callLabel != null) {
                beginLabel(callLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabel(callLabel);
                return;
            }
            callLabel = openComputersCallLabel(opcode, owner, name, desc);
            if (callLabel != null) {
                beginLabel(callLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabel(callLabel);
                return;
            }
            callLabel = enderIOCallLabel(owner, name, desc);
            if (callLabel != null) {
                beginLabel(callLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabel(callLabel);
                return;
            }
            callLabel = enderIOAlloyCallLabel(owner, name, desc);
            if (callLabel != null) {
                beginLabel(callLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabel(callLabel);
                return;
            }
            callLabel = postInitTopCallLabel(owner, name, desc);
            if (callLabel != null) {
                beginLabel(callLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabel(callLabel);
                return;
            }
            String[] structureLabels = aoaStructureInstantiationLabels(opcode, owner, name);
            if (structureLabels != null) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endLabels(structureLabels);
                pendingAoAStructureType = null;
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (profileAoAStructureInstantiations && opcode == Opcodes.NEW && isAoAStructureInternalName(type)) {
                pendingAoAStructureType = type;
                beginLabels(aoaStructureInstantiationLabels(type));
            }
            super.visitTypeInsn(opcode, type);
        }

        private String jerCallLabel(int opcode, String owner, String name, String desc) {
            if (!profileJerCalls || opcode != Opcodes.INVOKEINTERFACE || owner == null || name == null) {
                return null;
            }
            if (!owner.startsWith("jeresources/api/") || !name.startsWith("register")) {
                return null;
            }
            return "AOA JER call " + owner.replace('/', '.') + '.' + name + desc;
        }

        private String abyssalCraftCallLabel(int opcode, String owner, String name, String desc) {
            if (!profileAbyssalCraftCalls || owner == null || name == null) {
                return null;
            }
            if (opcode == Opcodes.INVOKESPECIAL
                    && "<init>".equals(name)
                    && (owner.equals("com/shinoow/abyssalcraft/api/necronomicon/NecroData$Page")
                    || owner.equals("com/shinoow/abyssalcraft/api/necronomicon/CraftingStack")
                    || owner.equals("net/minecraft/item/ItemStack"))) {
                return "ABYSS call " + owner.replace('/', '.') + ".<init>" + desc;
            }
            if (owner.equals("com/shinoow/abyssalcraft/common/handlers/InternalNecroDataHandler")
                    && (name.equals("addPages")
                    || name.equals("addInternalPages")
                    || name.equals("setupPatreonData")
                    || name.equals("verifyImageURL"))) {
                return "ABYSS call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("com/shinoow/abyssalcraft/api/internal/IInternalNecroDataHandler")
                    && name.equals("verifyImageURL")) {
                return "ABYSS call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if ((owner.equals("net/minecraft/client/resources/IResourceManager") && name.equals("func_110536_a"))
                    || (owner.equals("net/minecraft/client/resources/IResource") && name.equals("func_110527_b"))
                    || (owner.equals("net/minecraft/client/renderer/texture/TextureUtil") && name.equals("func_177053_a"))) {
                return "ABYSS call " + owner.replace('/', '.') + '.' + name + desc;
            }
            return null;
        }

        private String postInitTopCallLabel(String owner, String name, String desc) {
            if (!profilePostInitTopCalls || owner == null || name == null || owner.startsWith("java/")) {
                return null;
            }
            String className = owner.replace('/', '.');
            if (owner.startsWith("crafttweaker/mc1120/recipes/MCRecipeManager$Action")
                    && (name.equals("apply") || name.equals("describe"))) {
                return "CT call " + className + '.' + name + desc;
            }
            if (owner.equals("crafttweaker/CraftTweakerAPI") && name.equals("apply")) {
                return "CT call " + className + '.' + name + desc;
            }
            if (owner.equals("blusunrize/immersiveengineering/common/IERecipes")
                    || owner.equals("blusunrize/immersiveengineering/common/CommonProxy")
                    || owner.equals("blusunrize/immersiveengineering/common/util/compat/IECompatModule")) {
                return "IE call " + className + '.' + name + desc;
            }
            if (owner.startsWith("blusunrize/immersiveengineering/common/util/compat/")
                    && !owner.contains("/jei/")
                    && (name.equals("postInit") || name.equals("loadComplete") || name.equals("registerRecipes"))) {
                return "IE call " + className + '.' + name + desc;
            }
            if ((owner.startsWith("blusunrize/immersiveengineering/common/util/")
                    || owner.startsWith("blusunrize/immersiveengineering/common/blocks/")
                    || owner.startsWith("blusunrize/immersiveengineering/common/items/")
                    || owner.startsWith("blusunrize/immersiveengineering/common/crafting/"))
                    && !owner.contains("/client/")
                    && !owner.contains("/jei/")) {
                return "IE call " + className + '.' + name + desc;
            }
            if (owner.equals("blusunrize/immersiveengineering/api/ManualHelper")
                    || owner.equals("blusunrize/immersiveengineering/api/IEApi")
                    || owner.equals("blusunrize/immersiveengineering/api/tool/ExcavatorHandler")
                    || owner.equals("blusunrize/lib/manual/ManualInstance")) {
                return "IE manual call " + className + '.' + name + desc;
            }
            if (owner.startsWith("blusunrize/lib/manual/ManualPages$")
                    || owner.startsWith("blusunrize/immersiveengineering/client/manual/")) {
                return "IE manual call " + className + '.' + name + desc;
            }
            if ((owner.equals("net/minecraftforge/oredict/OreDictionary") && name.equals("getOres"))
                    || owner.equals("net/minecraft/client/resources/I18n")
                    || owner.equals("net/minecraft/client/gui/FontRenderer")) {
                return "IE manual call " + className + '.' + name + desc;
            }
            if (owner.equals("buildcraft/lib/registry/TagManager")
                    || owner.equals("buildcraft/lib/registry/RegistryConfig")
                    || owner.equals("buildcraft/lib/BCLibRegistries")
                    || owner.equals("buildcraft/lib/gui/config/GuiConfigManager")) {
                return "BC call " + className + '.' + name + desc;
            }
            if (owner.equals("net/minecraft/client/resources/IReloadableResourceManager")
                    && name.equals("func_110542_a")) {
                return "BC call " + className + '.' + name + desc;
            }
            if ((owner.startsWith("buildcraft/lib/")
                    || owner.startsWith("buildcraft/core/"))
                    && !owner.contains("/client/")
                    && !owner.contains("/render/")) {
                return "BC call " + className + '.' + name + desc;
            }
            if (owner.equals("cofh/thermalexpansion/init/TEPlugins")
                    || owner.equals("cofh/thermalexpansion/proxy/Proxy")
                    || owner.startsWith("cofh/thermalexpansion/util/managers/")
                    || owner.startsWith("cofh/thermalexpansion/plugins/")) {
                return "TE call " + className + '.' + name + desc;
            }
            if ((owner.equals("cofh/core/util/helpers/ItemHelper") && name.equals("getCraftingResult"))
                    || (owner.equals("net/minecraft/item/crafting/CraftingManager") && name.equals("func_82787_a"))) {
                return "TE call " + className + '.' + name + desc;
            }
            if (owner.equals("tk/zeitheron/expequiv/exp/Expansion")
                    || owner.equals("tk/zeitheron/expequiv/api/js/JSExpansion")
                    || owner.equals("tk/zeitheron/expequiv/api/js/JSExpansion$MapperAcceptor")
                    || owner.equals("tk/zeitheron/expequiv/api/js/JSExpansion$MapperAcceptor$JSEMCMapper")
                    || owner.equals("tk/zeitheron/expequiv/exp/hammercore/AbstractEMCMapper")
                    || owner.equals("tk/zeitheron/expequiv/api/IEMCMapper")
                    || owner.equals("tk/zeitheron/expequiv/api/IEMC")) {
                return "EXPEQUIV call " + className + '.' + name + desc;
            }
            if (owner.startsWith("tk/zeitheron/expequiv/api/js/")
                    || owner.startsWith("tk/zeitheron/expequiv/utils/")) {
                return "EXPEQUIV call " + className + '.' + name + desc;
            }
            if (owner.startsWith("moze_intel/projecte/api/")
                    || owner.startsWith("moze_intel/projecte/impl/")) {
                return "EXPEQUIV ProjectE call " + className + '.' + name + desc;
            }
            if (owner.startsWith("com/zeitheron/hammercore/lib/nashorn/")
                    || owner.startsWith("javax/script/")) {
                return "EXPEQUIV script call " + className + '.' + name + desc;
            }
            return null;
        }

        private String enderIOCallLabel(String owner, String name, String desc) {
            if (!profileEnderIOImcCalls || owner == null || name == null) {
                return null;
            }
            if (owner.equals("crazypants/enderio/base/EnderIO") && name.equals("processImc")) {
                return "EIO IMC call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/material/recipes/MaterialOredicts")
                    && (name.equals("init") || name.equals("checkOreRegistrations"))) {
                return "EIO IMC call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/config/recipes/RecipeLoader") && name.equals("addRecipes")) {
                return "EIO IMC call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/capacitor/CapacitorKeyRegistry") && name.equals("validate")) {
                return "EIO IMC call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/integration/railcraft/RailcraftUtil") && name.equals("registerFuels")) {
                return "EIO IMC call " + owner.replace('/', '.') + '.' + name + desc;
            }
            return null;
        }

        private String enderIOAlloyCallLabel(String owner, String name, String desc) {
            if (!profileEnderIOAlloyCalls || owner == null || name == null || owner.startsWith("java/")) {
                return null;
            }
            if (owner.equals("crazypants/enderio/base/recipe/alloysmelter/AlloyRecipeManager")
                    && (name.equals("addRecipe")
                    || name.equals("addDedupedRecipe")
                    || name.equals("addSyntheticRecipe")
                    || name.equals("needsSynthetics")
                    || name.equals("addRecipeToLookup")
                    || name.equals("addJEIIntegration")
                    || name.equals("dupeCheckRecipe"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/recipe/BasicManyToOneRecipe")
                    && ("<init>".equals(name)
                    || name.equals("setSynthetic")
                    || name.equals("setDedupeInput")
                    || name.equals("getInputStackAlternatives")
                    || name.equals("getInputStacks"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/recipe/Recipe")
                    && ("<init>".equals(name)
                    || name.equals("getInputStackAlternatives")
                    || name.equals("getInputs")
                    || name.equals("getOutputs")
                    || name.equals("getRecipeLevel")
                    || name.equals("getEnergyRequired")
                    || name.equals("getBonusType"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if (owner.equals("crazypants/enderio/base/recipe/lookup/TriItemLookup")
                    && (name.equals("addRecipe")
                    || name.equals("getRecipes")
                    || name.equals("getRecipes3")
                    || name.equals("iterator"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if ((owner.equals("crazypants/enderio/base/recipe/lookup/ItemRecipeNode")
                    || owner.equals("crazypants/enderio/base/recipe/lookup/ItemRecipeLeafNode"))
                    && (name.equals("makeNext") || name.equals("addRecipe") || name.equals("getRecipes") || name.equals("getNext"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if ((owner.equals("crazypants/enderio/base/recipe/ThingsRecipeInput")
                    || owner.equals("crazypants/enderio/base/recipe/RecipeInput"))
                    && ("<init>".equals(name)
                    || name.equals("setCount")
                    || name.equals("getEquivelentInputs")
                    || name.equals("getInput")
                    || name.equals("copy"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            if ((owner.equals("crazypants/enderio/base/config/recipes/xml/ItemIntegerAmount")
                    || owner.equals("crazypants/enderio/base/config/recipes/xml/Output")
                    || owner.equals("crazypants/enderio/base/config/recipes/xml/AbstractCrafting"))
                    && (name.equals("getThing")
                    || name.equals("getAmount")
                    || name.equals("getOutput")
                    || name.equals("getItemStack"))) {
                return "EIO alloy call " + owner.replace('/', '.') + '.' + name + desc;
            }
            return null;
        }

        private String openComputersCallLabel(int opcode, String owner, String name, String desc) {
            if (!profileOpenComputersCalls || owner == null || name == null || "<init>".equals(name)) {
                return null;
            }
            if (owner.startsWith("org/apache/logging/log4j/")) {
                return null;
            }
            if (owner.equals("li/cil/oc/OpenComputers$") && (name.equals("log") || name.equals("channel"))) {
                return null;
            }
            if (owner.startsWith("li/cil/oc/")
                    || owner.startsWith("net/minecraftforge/")
                    || owner.startsWith("org/lwjgl/opengl/GLContext")) {
                return "OC call " + owner.replace('/', '.') + '.' + name + desc;
            }
            return null;
        }

        private String[] aoaStructureInstantiationLabels(int opcode, String owner, String name) {
            if (!profileAoAStructureInstantiations
                    || opcode != Opcodes.INVOKESPECIAL
                    || pendingAoAStructureType == null
                    || owner == null
                    || !owner.equals(pendingAoAStructureType)
                    || !"<init>".equals(name)) {
                return null;
            }
            return aoaStructureInstantiationLabels(owner);
        }

        private static boolean isAoAStructureInternalName(String type) {
            return type != null
                    && type.startsWith("net/tslat/aoa3/structure/")
                    && type.indexOf('$') < 0
                    && !type.equals("net/tslat/aoa3/structure/AoAStructure")
                    && !type.equals("net/tslat/aoa3/structure/StructuresHandler");
        }

        private static String[] aoaStructureInstantiationLabels(String internalName) {
            String className = internalName.replace('/', '.');
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packageLabel = "AOA structure instantiate package "
                    + packageName.substring("net.tslat.aoa3.structure.".length());
            return new String[] {
                    "AOA structure instantiate all",
                    packageLabel,
                    "AOA structure instantiate " + className
            };
        }

        private void beginLabels(String[] probeLabels) {
            for (String probeLabel : probeLabels) {
                beginLabel(probeLabel);
            }
        }

        private void endLabels(String[] probeLabels) {
            for (int i = probeLabels.length - 1; i >= 0; i--) {
                endLabel(probeLabels[i]);
            }
        }

        private void beginLabel(String probeLabel) {
            super.visitLdcInsn(probeLabel);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private void endLabel(String probeLabel) {
            super.visitLdcInsn(probeLabel);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
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
}
