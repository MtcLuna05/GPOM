package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
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

public final class SfmLightweightSearchCacheTransformer implements IClassTransformer {
    private static final String LOGIN_CLASS = "vswe.superfactory.client.IndexItemsOnLogin";
    private static final String SEARCH_CLASS = "vswe.superfactory.util.SearchUtil";
    private static final String SEARCH_OWNER = "vswe/superfactory/util/SearchUtil";
    private static final String HELPER_OWNER = "com/l/gpom/compat/sfm/SfmLightweightSearchCache";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.sfmLightweightSearchCacheEnabled()) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (LOGIN_CLASS.equals(className)) {
            return patchLoginHandler(basicClass);
        }
        if (SEARCH_CLASS.equals(className)) {
            return patchSearchUtil(basicClass);
        }
        return basicClass;
    }

    private static byte[] patchLoginHandler(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("onPlayerLogin".equals(method.name)
                        && "(Lnet/minecraftforge/fml/common/network/FMLNetworkEvent$ClientConnectedToServerEvent;)V".equals(method.desc)) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    if (method.localVariables != null) {
                        method.localVariables.clear();
                    }
                    method.instructions.add(callDeferBuild());
                    method.instructions.add(new InsnNode(Opcodes.RETURN));
                    changed = true;
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM SFM] Patched SFM login item index handler");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM SFM] Failed to patch SFM login item index handler", throwable);
            return basicClass;
        }
    }

    private static byte[] patchSearchUtil(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("buildCache".equals(method.name) && "()V".equals(method.desc)) {
                    method.instructions.clear();
                    method.tryCatchBlocks.clear();
                    if (method.localVariables != null) {
                        method.localVariables.clear();
                    }
                    InsnList instructions = new InsnList();
                    instructions.add(new FieldInsnNode(
                            Opcodes.GETSTATIC,
                            SEARCH_OWNER,
                            "cache",
                            "Lcom/google/common/collect/Multimap;"
                    ));
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            HELPER_OWNER,
                            "buildCache",
                            "(Lcom/google/common/collect/Multimap;)V",
                            false
                    ));
                    instructions.add(new InsnNode(Opcodes.RETURN));
                    method.instructions.add(instructions);
                    changed = true;
                }
            }
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM SFM] Patched SFM SearchUtil lightweight cache builder");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM SFM] Failed to patch SFM SearchUtil lightweight cache builder", throwable);
            return basicClass;
        }
    }

    private static MethodInsnNode callDeferBuild() {
        return new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER_OWNER,
                "deferBuild",
                "()V",
                false
        );
    }
}
