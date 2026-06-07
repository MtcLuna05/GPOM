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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class HammerCoreConstructionTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.hammercore.constructionOptimizations", "true"));
    private static final boolean DISABLE_MOD_SOURCE_NETWORK_CHECK = Boolean.parseBoolean(System.getProperty("gpom.hammercore.disableModSourceNetworkCheck", "true"));
    private static final String MOD_SOURCE_ADAPTER = "com.zeitheron.hammercore.utils.java.io.win32.ModSourceAdapter";
    private static final String MOD_SOURCE_ADAPTER_INTERNAL = "com/zeitheron/hammercore/utils/java/io/win32/ModSourceAdapter";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || !DISABLE_MOD_SOURCE_NETWORK_CHECK || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!MOD_SOURCE_ADAPTER.equals(className) || !TargetedModVersions.isHammerCoreClass(className)) {
            return basicClass;
        }

        try {
            return patchModSourceAdapterInitializer(basicClass);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[HammerCoreConstructionTransformer] Failed to patch HammerLib ModSourceAdapter; continuing with original bytecode", throwable);
            return basicClass;
        }
    }

    private static byte[] patchModSourceAdapterInitializer(byte[] basicClass) {
        ClassNode classNode = new ClassNode();
        ClassReader reader = new ClassReader(basicClass);
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

        for (MethodNode method : classNode.methods) {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
                InsnList instructions = new InsnList();
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "java/util/Collections",
                        "emptyList",
                        "()Ljava/util/List;",
                        false
                ));
                instructions.add(new FieldInsnNode(
                        Opcodes.PUTSTATIC,
                        MOD_SOURCE_ADAPTER_INTERNAL,
                        "ILLEGAL_SITES",
                        "Ljava/util/List;"
                ));
                instructions.add(new InsnNode(Opcodes.RETURN));

                method.instructions.clear();
                method.instructions.add(instructions);
                method.tryCatchBlocks.clear();
                if (method.localVariables != null) {
                    method.localVariables.clear();
                }
                method.maxStack = 1;
                method.maxLocals = 0;

                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                classNode.accept(writer);
                GPOM.LOGGER.info("[HammerCoreConstructionTransformer] Disabled HammerLib startup mod-source network check");
                return writer.toByteArray();
            }
        }

        return basicClass;
    }
}
