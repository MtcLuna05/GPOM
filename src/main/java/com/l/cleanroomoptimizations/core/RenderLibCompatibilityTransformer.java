package com.l.cleanroomoptimizations.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.ClassWriter;

public final class RenderLibCompatibilityTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "cleanroomoptimizations.renderlib.nullCapsTimerFix",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!"meldexun.renderlib.util.timer.TimerEventHandler".equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, ClassReader.EXPAND_FRAMES);
            for (MethodNode method : node.methods) {
                if ("tryCreateGLTimer".equals(method.name)
                        && "(Ljava/lang/String;I)Lmeldexun/renderlib/util/timer/ITimer;".equals(method.desc)) {
                    patchTryCreateGLTimer(method);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    return writer.toByteArray();
                }
            }
        } catch (Throwable ignored) {
        }

        return basicClass;
    }

    private static void patchTryCreateGLTimer(MethodNode method) {
        LabelNode capsAvailable = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "meldexun/renderlib/util/GLUtil",
                "CAPS",
                "Lorg/lwjgl/opengl/ContextCapabilities;"
        ));
        guard.add(new JumpInsnNode(Opcodes.IFNONNULL, capsAvailable));
        guard.add(new TypeInsnNode(Opcodes.NEW, "meldexun/renderlib/util/timer/DummyTimer"));
        guard.add(new InsnNode(Opcodes.DUP));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new LdcInsnNode("?"));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "meldexun/renderlib/util/timer/DummyTimer",
                "<init>",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                false
        ));
        guard.add(new InsnNode(Opcodes.ARETURN));
        guard.add(capsAvailable);
        method.instructions.insert(guard);
    }
}
