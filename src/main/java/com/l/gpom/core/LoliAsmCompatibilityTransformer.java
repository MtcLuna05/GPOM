package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LoliAsmCompatibilityTransformer implements IClassTransformer {
    private static final String ISTATEFUL = "zone.rong.loliasm.common.crashes.IStateful";
    private static final String ISTATEFUL_INTERNAL = "zone/rong/loliasm/common/crashes/IStateful";
    private static final String REGISTRY_HELPER = "com/l/gpom/compat/LoliAsmStatefulRegistry";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.loliAsmThreadSafeStatefulRegistryEnabled()) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!ISTATEFUL.equals(className) || !TargetedModVersions.isLoliAsmClass(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
                    replaceStaticInitializer(method);
                    changed = true;
                } else if ("register".equals(method.name) && "()V".equals(method.desc)) {
                    replaceRegister(method);
                    changed = true;
                }
            }
            if (changed) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            }
        } catch (Throwable ignored) {
        }

        return basicClass;
    }

    private static void replaceStaticInitializer(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables.clear();

        InsnList instructions = method.instructions;
        instructions.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap"));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/util/concurrent/ConcurrentHashMap",
                "<init>",
                "()V",
                false
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "java/util/Collections",
                "newSetFromMap",
                "(Ljava/util/Map;)Ljava/util/Set;",
                false
        ));
        instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC,
                ISTATEFUL_INTERNAL,
                "INSTANCES",
                "Ljava/util/Set;"
        ));
        instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static void replaceRegister(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables.clear();

        InsnList instructions = method.instructions;
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                ISTATEFUL_INTERNAL,
                "INSTANCES",
                "Ljava/util/Set;"
        ));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                REGISTRY_HELPER,
                "register",
                "(Ljava/util/Set;Ljava/lang/Object;)V",
                false
        ));
        instructions.add(new InsnNode(Opcodes.RETURN));
    }
}
