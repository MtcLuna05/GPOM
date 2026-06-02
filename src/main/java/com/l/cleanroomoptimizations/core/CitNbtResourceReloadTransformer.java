package com.l.cleanroomoptimizations.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class CitNbtResourceReloadTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "cleanroomoptimizations.citnbt.deferPreinitReload",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!"com.sabrepotato.citnbt.resources.ExternalResourcePack".equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("injectExternalResources".equals(method.name) && "()V".equals(method.desc)) {
                    changed |= removeImmediateReloadCall(method);
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

    private static boolean removeImmediateReloadCall(MethodNode method) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            if ("net/minecraft/client/resources/SimpleReloadableResourceManager".equals(methodInsn.owner)
                    && "func_110541_a".equals(methodInsn.name)
                    && "(Ljava/util/List;)V".equals(methodInsn.desc)) {
                method.instructions.set(methodInsn, new InsnNode(org.objectweb.asm.Opcodes.POP2));
                return true;
            }
        }
        return false;
    }
}
