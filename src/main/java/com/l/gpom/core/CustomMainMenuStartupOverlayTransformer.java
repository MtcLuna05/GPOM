package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class CustomMainMenuStartupOverlayTransformer implements IClassTransformer {
    private static final String TARGET_CLASS = "lumien.custommainmenu.gui.GuiCustom";
    private static final String TARGET_OWNER = "lumien/custommainmenu/gui/GuiCustom";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!TARGET_CLASS.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (("func_73863_a".equals(method.name) || "drawScreen".equals(method.name))
                        && "(IIF)V".equals(method.desc)) {
                    changed |= injectStartupOverlay(method);
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

    private static boolean injectStartupOverlay(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(insn, renderCall());
                changed = true;
            }
        }
        return changed;
    }

    private static InsnList renderCall() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_OWNER, "field_146294_l", "I"));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new FieldInsnNode(Opcodes.GETFIELD, TARGET_OWNER, "field_146295_m", "I"));
        instructions.add(new LdcInsnNode(TARGET_CLASS));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/l/gpom/client/MainMenuStartupOverlay",
                "render",
                "(IILjava/lang/String;)V",
                false
        ));
        return instructions;
    }
}
