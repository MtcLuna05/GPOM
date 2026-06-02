package com.l.cleanroomoptimizations.core;

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

public final class LibVulpesCompatibilityTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "cleanroomoptimizations.libvulpes.lazyProductBlockLists",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!"zmaster587.libVulpes.api.material.MaterialRegistry".equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, ClassReader.EXPAND_FRAMES);
            for (MethodNode method : node.methods) {
                if ("getBlockListForProduct".equals(method.name)
                        && "(Lzmaster587/libVulpes/api/material/AllowedProducts;)Ljava/util/List;".equals(method.desc)) {
                    replaceGetBlockListForProduct(method);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    return writer.toByteArray();
                }
            }
        } catch (Throwable ignored) {
        }

        return basicClass;
    }

    private static void replaceGetBlockListForProduct(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables.clear();

        LabelNode existing = new LabelNode();
        InsnList instructions = method.instructions;
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "zmaster587/libVulpes/api/material/MaterialRegistry",
                "productBlockListMapping",
                "Ljava/util/HashMap;"
        ));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;",
                false
        ));
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/util/List"));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, existing));
        instructions.add(new InsnNode(Opcodes.POP));
        instructions.add(new TypeInsnNode(Opcodes.NEW, "java/util/ArrayList"));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESPECIAL,
                "java/util/ArrayList",
                "<init>",
                "()V",
                false
        ));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "zmaster587/libVulpes/api/material/MaterialRegistry",
                "productBlockListMapping",
                "Ljava/util/HashMap;"
        ));
        instructions.add(new InsnNode(Opcodes.SWAP));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new InsnNode(Opcodes.SWAP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/util/HashMap",
                "put",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
        ));
        instructions.add(new InsnNode(Opcodes.POP));
        instructions.add(existing);
        instructions.add(new InsnNode(Opcodes.ARETURN));
    }
}
