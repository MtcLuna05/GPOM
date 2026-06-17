package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class ScannableConfigSyncTransformer implements IClassTransformer {
    private static final String TARGET_CLASS = "li.cil.scannable.common.config.Settings";
    private static final String TARGET_METHOD = "setServerSettings";
    private static final String TARGET_DESC = "(Lli/cil/scannable/common/config/ServerSettings;)V";
    private static final String HELPER = "com/l/gpom/compat/scannable/ScannableConfigSyncOptimization";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled()) {
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
                if (TARGET_METHOD.equals(method.name) && TARGET_DESC.equals(method.desc)) {
                    insertRedundantSettingsGuard(method);
                    changed = true;
                    break;
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

    private static void insertRedundantSettingsGuard(MethodNode method) {
        LabelNode applySettings = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "shouldSkipSetServerSettings",
                "(Ljava/lang/Object;)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, applySettings));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(applySettings);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
    }
}
