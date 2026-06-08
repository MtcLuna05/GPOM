package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class Ae2MultipartGridHostTransformer implements IClassTransformer {
    private static final String GRID_NODE = "appeng.me.GridNode";
    private static final String FIND_GRID_HOST_DESC = "(Lnet/minecraft/world/World;III)Lappeng/api/networking/IGridHost;";
    private static final String BLOCK_POS = "net/minecraft/util/math/BlockPos";
    private static final String WORLD = "net/minecraft/world/World";
    private static final String GRID_HOST = "appeng/api/networking/IGridHost";
    private static final String BRIDGE = "com/l/gpom/compat/multipart/ae2/Ae2MultipartGridHostBridge";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!GRID_NODE.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("findGridHost".equals(method.name) && FIND_GRID_HOST_DESC.equals(method.desc)) {
                    replaceFindGridHost(method);
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static void replaceFindGridHost(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables.clear();

        LabelNode loaded = new LabelNode();
        LabelNode fallback = new LabelNode();
        LabelNode noHost = new LabelNode();

        InsnList instructions = method.instructions;
        instructions.add(new TypeInsnNode(Opcodes.NEW, BLOCK_POS));
        instructions.add(new InsnNode(Opcodes.DUP));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, BLOCK_POS, "<init>", "(III)V", false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 5));

        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_175667_e", "(Lnet/minecraft/util/math/BlockPos;)Z", false));
        instructions.add(new JumpInsnNode(Opcodes.IFNE, loaded));
        instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        instructions.add(new InsnNode(Opcodes.ARETURN));

        instructions.add(loaded);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, WORLD, "func_175625_s", "(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;", false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 6));

        instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        instructions.add(new TypeInsnNode(Opcodes.INSTANCEOF, GRID_HOST));
        instructions.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, GRID_HOST));
        instructions.add(new InsnNode(Opcodes.ARETURN));

        instructions.add(fallback);
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 6));
        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "findGridHost", "(Lnet/minecraft/tileentity/TileEntity;)Lappeng/api/networking/IGridHost;", false));
        instructions.add(new VarInsnNode(Opcodes.ASTORE, 7));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        instructions.add(new JumpInsnNode(Opcodes.IFNULL, noHost));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        instructions.add(new InsnNode(Opcodes.ARETURN));

        instructions.add(noHost);
        instructions.add(new InsnNode(Opcodes.ACONST_NULL));
        instructions.add(new InsnNode(Opcodes.ARETURN));

        method.maxLocals = Math.max(method.maxLocals, 8);
        method.maxStack = Math.max(method.maxStack, 6);
    }
}
