package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Applies optional-mod hot-loop fixes only when each exact target class is actually being defined. */
public final class OptionalModExceptionLoopTransformer implements IClassTransformer {
    private static final String GENDUSTRY = "net.bdew.gendustry.fluids.FluidSourceRegistry";
    private static final String GENDUSTRY_RESOURCE = "net/bdew/gendustry/fluids/FluidSourceRegistry.class";
    private static final String LAVACOW = "com.Fishmod.mod_LavaCow.util.ModEventHandler";
    private static final String LAVACOW_RESOURCE = "com/Fishmod/mod_LavaCow/util/ModEventHandler.class";
    private static final String HAMMERCORE_ITEM_COLOR = "com.zeitheron.hammercore.client.utils.ItemColorHelper";
    private static final String HAMMERCORE_ITEM_COLOR_RESOURCE =
            "com/zeitheron/hammercore/client/utils/ItemColorHelper.class";
    private static final String AE2_EXPORT_BUS = "appeng.parts.automation.PartExportBus";
    private static final String AE2_EXPORT_BUS_RESOURCE = "appeng/parts/automation/PartExportBus.class";
    private static final String HELPERS = "com/l/gpom/optimization/OptionalModExceptionLoopOptimizations";
    private static final String MAPPING = "com/l/gpom/compat/minecraft/MinecraftMappingCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
        }
        String className = transformedName != null ? transformedName : name;
        try {
            if (GENDUSTRY.equals(className)
                    && GpomEarlyConfig.gendustryFluidSourceLookupEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    GENDUSTRY_RESOURCE, "gendustry-1.6.5.8-mc1.12.2.jar")) {
                return transformGendustry(basicClass);
            }
            if (LAVACOW.equals(className)
                    && GpomEarlyConfig.lavaCowInheritedTargetFieldLookupEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    LAVACOW_RESOURCE, "Fish's Undead Rising-1.6.0.jar")) {
                return transformLavaCow(basicClass);
            }
            if (HAMMERCORE_ITEM_COLOR.equals(className)
                    && GpomEarlyConfig.hammerCoreItemColorNegativeLookupCacheEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    HAMMERCORE_ITEM_COLOR_RESOURCE, "HammerLib-1.12.2-12.2.50.jar")) {
                return transformHammerCoreItemColor(basicClass);
            }
            if (AE2_EXPORT_BUS.equals(className)
                    && GpomEarlyConfig.ae2BdlibPowerNegativeLookupCacheEnabled()
                    && GpomMixinConfigPlugin.resourcePresentInNamedJar(
                    AE2_EXPORT_BUS_RESOURCE, "ae2-uelu-0.56.7-cleanroom.1.jar")) {
                return transformAe2(basicClass);
            }
        } catch (Throwable ignored) {
            // Optional integrations fail closed: the original class bytes remain valid.
        }
        return basicClass;
    }

    private static byte[] transformGendustry(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getValue", "(Lnet/minecraft/item/ItemStack;)I");
        if (target == null || !containsCall(target, "scala/runtime/NonLocalReturnControl", "value$mcI$sp")) {
            return basicClass;
        }

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        InsnList code = target.instructions;
        LabelNode stackPresent = new LabelNode();
        LabelNode itemPresent = new LabelNode();
        LabelNode itemEntryPresent = new LabelNode();
        LabelNode exactMissing = new LabelNode();
        LabelNode wildcardMissing = new LabelNode();

        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, stackPresent));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(stackPresent);
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MAPPING, "itemStackItem",
                "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/Item;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 2));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, itemPresent));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(itemPresent);
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "net/bdew/gendustry/fluids/FluidSourceRegistry", "values",
                "()Lscala/collection/mutable/Map;", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 2));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "scala/collection/mutable/Map", "get",
                "(Ljava/lang/Object;)Lscala/Option;", true));
        code.add(new VarInsnNode(Opcodes.ASTORE, 3));
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new JumpInsnNode(Opcodes.IFNULL, wildcardMissing));
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "isDefined", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFNE, itemEntryPresent));
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(itemEntryPresent);
        code.add(new VarInsnNode(Opcodes.ALOAD, 3));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "get", "()Ljava/lang/Object;", false));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, "scala/collection/mutable/Map"));
        code.add(new VarInsnNode(Opcodes.ASTORE, 4));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, MAPPING, "itemStackDamage",
                "(Lnet/minecraft/item/ItemStack;)I", false));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 5));
        addMapLookup(code, 4, 5, 6, exactMissing);
        code.add(new VarInsnNode(Opcodes.ALOAD, 6));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "get", "()Ljava/lang/Object;", false));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "scala/runtime/BoxesRunTime", "unboxToInt",
                "(Ljava/lang/Object;)I", false));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(exactMissing);
        code.add(new LdcInsnNode(Integer.valueOf(32767)));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                "(I)Ljava/lang/Integer;", false));
        code.add(new VarInsnNode(Opcodes.ASTORE, 5));
        addMapLookup(code, 4, 5, 6, wildcardMissing);
        code.add(new VarInsnNode(Opcodes.ALOAD, 6));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "get", "()Ljava/lang/Object;", false));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "scala/runtime/BoxesRunTime", "unboxToInt",
                "(Ljava/lang/Object;)I", false));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(wildcardMissing);
        code.add(new InsnNode(Opcodes.ICONST_0));
        code.add(new InsnNode(Opcodes.IRETURN));
        return writeWithFrames(node);
    }

    private static void addMapLookup(InsnList code, int mapLocal, int keyLocal, int optionLocal, LabelNode missing) {
        code.add(new VarInsnNode(Opcodes.ALOAD, mapLocal));
        code.add(new VarInsnNode(Opcodes.ALOAD, keyLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "scala/collection/mutable/Map", "get",
                "(Ljava/lang/Object;)Lscala/Option;", true));
        code.add(new VarInsnNode(Opcodes.ASTORE, optionLocal));
        code.add(new VarInsnNode(Opcodes.ALOAD, optionLocal));
        code.add(new JumpInsnNode(Opcodes.IFNULL, missing));
        code.add(new VarInsnNode(Opcodes.ALOAD, optionLocal));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "scala/Option", "isDefined", "()Z", false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, missing));
    }

    private static byte[] transformLavaCow(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "onEntityJoinWorld",
                "(Lnet/minecraftforge/event/entity/EntityJoinWorldEvent;)V");
        if (target == null) {
            return basicClass;
        }
        LabelNode loopContinue = null;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEINTERFACE
                        && "java/util/Iterator".equals(call.owner)
                        && "hasNext".equals(call.name)
                        && "()Z".equals(call.desc)) {
                    loopContinue = precedingLabel(instruction);
                }
            }
        }
        if (loopContinue == null) {
            return basicClass;
        }

        int replacements = 0;
        boolean nullGuardInserted = false;
        for (AbstractInsnNode instruction : target.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "java/lang/Class".equals(call.owner)
                        && "getDeclaredField".equals(call.name)
                        && "(Ljava/lang/String;)Ljava/lang/reflect/Field;".equals(call.desc)) {
                    call.setOpcode(Opcodes.INVOKESTATIC);
                    call.owner = HELPERS;
                    call.name = "findInheritedDeclaredFieldOrNull";
                    call.desc = "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;";
                    call.itf = false;
                    AbstractInsnNode storeInstruction = nextOpcode(call);
                    if (storeInstruction instanceof VarInsnNode
                            && storeInstruction.getOpcode() == Opcodes.ASTORE) {
                        int fieldLocal = ((VarInsnNode) storeInstruction).var;
                        InsnList guard = new InsnList();
                        guard.add(new VarInsnNode(Opcodes.ALOAD, fieldLocal));
                        guard.add(new JumpInsnNode(Opcodes.IFNULL, loopContinue));
                        target.instructions.insert(storeInstruction, guard);
                        nullGuardInserted = true;
                    }
                    replacements++;
                }
            }
        }
        return replacements == 1 && nullGuardInserted ? writePreservingFrames(node) : basicClass;
    }

    private static byte[] transformHammerCoreItemColor(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getCustomColor", "(I)I");
        if (target == null
                || !containsCall(target, "java/lang/Class", "forName")
                || !containsCall(target, "com/zeitheron/hammercore/client/utils/ItemColorHelper", "getColorFromStack")) {
            return basicClass;
        }

        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        LabelNode fallback = new LabelNode();
        LabelNode quarkMissing = new LabelNode();
        InsnList code = target.instructions;
        code.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "target",
                "Lnet/minecraft/item/ItemStack;"
        ));
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPERS,
                "hammerCoreUsesCustomEnchantColor",
                "(Ljava/lang/Object;)Z",
                false
        ));
        code.add(new JumpInsnNode(Opcodes.IFNE, fallback));
        code.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "target",
                "Lnet/minecraft/item/ItemStack;"
        ));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPERS,
                "hammerCoreQuarkColor",
                "(Ljava/lang/Object;I)Ljava/lang/Integer;",
                false
        ));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new JumpInsnNode(Opcodes.IFNULL, quarkMissing));
        code.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Integer",
                "intValue",
                "()I",
                false
        ));
        code.add(new InsnNode(Opcodes.IRETURN));
        code.add(quarkMissing);
        code.add(new FrameNode(
                Opcodes.F_SAME1,
                0,
                null,
                1,
                new Object[] {"java/lang/Integer"}
        ));
        code.add(new InsnNode(Opcodes.POP));
        code.add(fallback);
        code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        code.add(new org.objectweb.asm.tree.FieldInsnNode(
                Opcodes.GETSTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "target",
                "Lnet/minecraft/item/ItemStack;"
        ));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/zeitheron/hammercore/client/utils/ItemColorHelper",
                "getColorFromStack",
                "(Lnet/minecraft/item/ItemStack;I)I",
                false
        ));
        code.add(new InsnNode(Opcodes.IRETURN));
        return writePreservingFrames(node);
    }

    private static byte[] transformAe2(byte[] basicClass) {
        ClassNode node = read(basicClass);
        MethodNode target = find(node, "getBdlibPowerSlot",
                "(Lnet/minecraft/tileentity/TileEntity;)Ljava/lang/Object;");
        if (target == null || !containsCall(target, "java/lang/Class", "getMethod")) {
            return basicClass;
        }
        target.instructions.clear();
        target.tryCatchBlocks.clear();
        if (target.localVariables != null) {
            target.localVariables.clear();
        }
        target.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        target.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPERS, "ae2BdlibPowerSlot",
                "(Ljava/lang/Object;)Ljava/lang/Object;", false));
        target.instructions.add(new InsnNode(Opcodes.ARETURN));
        return writePreservingFrames(node);
    }

    private static boolean containsCall(MethodNode method, String owner, String name) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (owner.equals(call.owner) && name.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static LabelNode precedingLabel(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getPrevious(); current != null; current = current.getPrevious()) {
            if (current instanceof LabelNode) {
                return (LabelNode) current;
            }
        }
        return null;
    }

    private static AbstractInsnNode nextOpcode(AbstractInsnNode instruction) {
        for (AbstractInsnNode current = instruction.getNext(); current != null; current = current.getNext()) {
            if (current.getOpcode() >= 0) {
                return current;
            }
        }
        return null;
    }

    private static MethodNode find(ClassNode node, String name, String descriptor) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        return node;
    }

    private static byte[] writeWithFrames(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static byte[] writePreservingFrames(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
