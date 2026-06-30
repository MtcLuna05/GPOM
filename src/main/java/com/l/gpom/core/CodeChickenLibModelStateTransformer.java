package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class CodeChickenLibModelStateTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.codeChickenLib.repairNullModelErrorState",
            "true"
    ));
    private static final String MODEL_BAKERY = "codechicken.lib.model.bakery.ModelBakery";
    private static final String GET_CACHED_MODEL_DESC =
            "(Lnet/minecraftforge/common/property/IExtendedBlockState;)Lnet/minecraft/client/renderer/block/model/IBakedModel;";
    private static final String HELPER = "com/l/gpom/compat/codechicken/CodeChickenLibModelStateCompat";
    private static final String REPAIR_DESC =
            "(Lnet/minecraftforge/common/property/IExtendedBlockState;)Lnet/minecraftforge/common/property/IExtendedBlockState;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!MODEL_BAKERY.equals(className) || !TargetedModVersions.isCodeChickenLibClass(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            for (MethodNode method : node.methods) {
                if ("getCachedModel".equals(method.name) && GET_CACHED_MODEL_DESC.equals(method.desc)) {
                    patchGetCachedModel(method);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    return writer.toByteArray();
                }
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static void patchGetCachedModel(MethodNode method) {
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "repairNullModelErrorState",
                REPAIR_DESC,
                false
        ));
        guard.add(new VarInsnNode(Opcodes.ASTORE, 0));
        method.instructions.insert(guard);
    }
}
