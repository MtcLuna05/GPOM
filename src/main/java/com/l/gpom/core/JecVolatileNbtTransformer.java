package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class JecVolatileNbtTransformer implements IClassTransformer {
    private static final String TARGET = "me.towdium.jecalculation.data.label.labels.LItemStack";
    private static final String HELPER = "com/l/gpom/compat/jecalculation/JecVolatileNbtLabels";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.jecalculationFuzzyVolatileItemNbtEnabled()) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("<init>".equals(method.name)) {
                    changed |= injectNormalizeHook(method);
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

    private static boolean injectNormalizeHook(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() != Opcodes.RETURN) {
                continue;
            }
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HELPER,
                    "normalize",
                    "(Ljava/lang/Object;)V",
                    false
            ));
            method.instructions.insertBefore(insn, hook);
            changed = true;
        }
        return changed;
    }
}
