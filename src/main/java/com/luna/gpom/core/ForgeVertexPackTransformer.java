package com.luna.gpom.core;

import com.luna.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Routes Forge's generic vertex packer to a branch-hoisted, byte-for-byte equivalent implementation. */
public final class ForgeVertexPackTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraftforge.client.model.pipeline.LightUtil";
    private static final String PACK_DESCRIPTOR =
            "([F[ILnet/minecraft/client/renderer/vertex/VertexFormat;II)V";
    private static final String HELPER = "com/luna/gpom/optimization/ForgeVertexPackOptimizations";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.forgeVertexPackingEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className)) {
            return basicClass;
        }

        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!"pack".equals(method.name) || !PACK_DESCRIPTOR.equals(method.desc)) {
                continue;
            }
            method.instructions.clear();
            method.tryCatchBlocks.clear();
            if (method.localVariables != null) {
                method.localVariables.clear();
            }
            InsnList code = method.instructions;
            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            code.add(new VarInsnNode(Opcodes.ALOAD, 1));
            code.add(new VarInsnNode(Opcodes.ALOAD, 2));
            code.add(new VarInsnNode(Opcodes.ILOAD, 3));
            code.add(new VarInsnNode(Opcodes.ILOAD, 4));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HELPER, "pack", PACK_DESCRIPTOR, false));
            code.add(new InsnNode(Opcodes.RETURN));
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            return writer.toByteArray();
        }
        return basicClass;
    }
}
