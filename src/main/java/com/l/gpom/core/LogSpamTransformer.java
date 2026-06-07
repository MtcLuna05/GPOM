package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class LogSpamTransformer implements IClassTransformer {
    private static final boolean UCW_TEXTURE_STITCH_STDOUT = Boolean.parseBoolean(System.getProperty(
            "gpom.ucw.suppressTextureStitchStdout",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (UCW_TEXTURE_STITCH_STDOUT
                && !(className != null && className.startsWith("com.l.gpom."))
                && "pl.asie.ucw.UCWProxyClient".equals(className)
                && TargetedModVersions.isUnlimitedChiselWorksClass(className)) {
            return patchUcwTextureStitchStdout(basicClass);
        }

        return basicClass;
    }

    private static byte[] patchUcwTextureStitchStdout(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("onTextureStitchPre".equals(method.name)
                        && "(Lnet/minecraftforge/client/event/TextureStitchEvent$Pre;)V".equals(method.desc)) {
                    changed |= removePrintlnBlocks(method);
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

    private static boolean removePrintlnBlocks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isPrintln(insn)) {
                FieldInsnNode start = findSystemOutStart(insn);
                if (start != null) {
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static boolean isPrintln(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "java/io/PrintStream".equals(methodInsn.owner)
                && "println".equals(methodInsn.name)
                && "(Ljava/lang/String;)V".equals(methodInsn.desc);
    }

    private static FieldInsnNode findSystemOutStart(AbstractInsnNode println) {
        for (AbstractInsnNode cursor = println.getPrevious(); cursor != null; cursor = cursor.getPrevious()) {
            if (cursor instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) cursor;
                if (fieldInsn.getOpcode() == Opcodes.GETSTATIC
                        && "java/lang/System".equals(fieldInsn.owner)
                        && "out".equals(fieldInsn.name)
                        && "Ljava/io/PrintStream;".equals(fieldInsn.desc)) {
                    return fieldInsn;
                }
            }
        }
        return null;
    }
}
