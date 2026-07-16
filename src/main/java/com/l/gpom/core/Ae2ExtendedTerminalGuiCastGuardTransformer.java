package com.l.gpom.core;

import com.l.gpom.GPOM;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class Ae2ExtendedTerminalGuiCastGuardTransformer implements IClassTransformer {
    private static final String TARGET = "appeng.container.implementations.ContainerMEMonitorable";
    private static final String GUI_HOST_DESC = "Lappeng/util/IConfigManagerHost;";
    private static final String GUI_MONITORABLE = "appeng/client/gui/implementations/GuiMEMonitorable";
    private static final String UPDATE_DESC = "(Ljava/util/List;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, ClassReader.EXPAND_FRAMES);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("postGenericUpdate".equals(method.name) && UPDATE_DESC.equals(method.desc)) {
                    rewritePostGenericUpdate(node, method);
                    changed = true;
                }
            }
            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return "java/lang/Object";
                }
            };
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM AE2 Extended Terminal Guard] Patched ContainerMEMonitorable.postGenericUpdate");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AE2 Extended Terminal Guard] Failed to patch ContainerMEMonitorable; continuing with original bytecode", throwable);
            return basicClass;
        }
    }

    private static void rewritePostGenericUpdate(ClassNode node, MethodNode method) {
        LabelNode compatibleGui = new LabelNode();
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "gui", GUI_HOST_DESC));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, GUI_MONITORABLE));
        instructions.add(new JumpInsnNode(Opcodes.IFNE, compatibleGui));
        instructions.add(new InsnNode(Opcodes.POP));
        instructions.add(new InsnNode(Opcodes.RETURN));
        instructions.add(compatibleGui);
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, GUI_MONITORABLE));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                GUI_MONITORABLE,
                "postGenericUpdate",
                UPDATE_DESC,
                false
        ));
        instructions.add(new InsnNode(Opcodes.RETURN));

        method.instructions.clear();
        method.instructions.add(instructions);
        method.tryCatchBlocks.clear();
        method.localVariables = null;
    }
}
