package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;

public final class RandomThingsRenderUtilsTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.randomThings.tolerateRenderUtilsAoLinkageError",
            "true"
    ));
    private static final String TARGET = "lumien.randomthings.util.client.RenderUtils";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
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
                if (!"<clinit>".equals(method.name)) {
                    continue;
                }
                for (TryCatchBlockNode handler : method.tryCatchBlocks) {
                    if ("java/lang/Exception".equals(handler.type)) {
                        handler.type = "java/lang/Throwable";
                        changed = true;
                    }
                }
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if ("java/lang/Exception".equals(call.owner)
                                && "printStackTrace".equals(call.name)
                                && "()V".equals(call.desc)) {
                            call.owner = "java/lang/Throwable";
                            changed = true;
                        }
                    }
                }
                for (LocalVariableNode local : method.localVariables) {
                    if ("Ljava/lang/Exception;".equals(local.desc)) {
                        local.desc = "Ljava/lang/Throwable;";
                    }
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
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }
}
