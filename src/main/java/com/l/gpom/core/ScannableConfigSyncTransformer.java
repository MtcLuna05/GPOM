package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
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
    private static final String SETTINGS_CLASS = "li.cil.scannable.common.config.Settings";
    private static final String SETTINGS_METHOD = "setServerSettings";
    private static final String SETTINGS_DESC = "(Lli/cil/scannable/common/config/ServerSettings;)V";
    private static final String SETTINGS_HELPER = "com/l/gpom/compat/scannable/ScannableConfigSyncOptimization";
    private static final String SCAN_RESULT_PROVIDER_BLOCK_CLASS = "li.cil.scannable.client.scanning.ScanResultProviderBlock";
    private static final String REBUILD_ORE_CACHE_METHOD = "rebuildOreCache";
    private static final String REBUILD_ORE_CACHE_DESC = "()V";
    private static final String BIT_SET_CLASS = "java/util/BitSet";
    private static final String BIT_SET_SET_DESC = "(I)V";
    private static final String ORE_CACHE_HELPER = "com/l/gpom/compat/scannable/ScannableOreCacheGuard";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (SETTINGS_CLASS.equals(className) && GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled()) {
            return transformSettings(basicClass);
        }
        if (SCAN_RESULT_PROVIDER_BLOCK_CLASS.equals(className)
                && (GpomEarlyConfig.scannableSkipNegativeOreCacheIdsEnabled()
                || GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled())) {
            return transformOreCache(basicClass);
        }
        return basicClass;
    }

    private static byte[] transformSettings(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (SETTINGS_METHOD.equals(method.name) && SETTINGS_DESC.equals(method.desc)) {
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

    private static byte[] transformOreCache(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (GpomEarlyConfig.scannableSkipNegativeOreCacheIdsEnabled()) {
                    changed |= replaceBitSetSets(method);
                }
                if (GpomEarlyConfig.scannableSkipRedundantConfigOreCacheRebuildsEnabled()
                        && REBUILD_ORE_CACHE_METHOD.equals(method.name)
                        && REBUILD_ORE_CACHE_DESC.equals(method.desc)) {
                    changed |= insertOreCacheRebuildGuard(method);
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
                SETTINGS_HELPER,
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

    private static boolean insertOreCacheRebuildGuard(MethodNode method) {
        if (hasHelperCall(method, "shouldSkipRebuildOreCache")) {
            return false;
        }

        LabelNode rebuild = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                SETTINGS_HELPER,
                "shouldSkipRebuildOreCache",
                "(Ljava/lang/Object;)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, rebuild));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(rebuild);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);

        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                InsnList mark = new InsnList();
                mark.add(new VarInsnNode(Opcodes.ALOAD, 0));
                mark.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        SETTINGS_HELPER,
                        "markRebuildOreCache",
                        "(Ljava/lang/Object;)V",
                        false
                ));
                method.instructions.insertBefore(instruction, mark);
            }
        }
        return true;
    }

    private static boolean replaceBitSetSets(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && BIT_SET_CLASS.equals(call.owner)
                    && "set".equals(call.name)
                    && BIT_SET_SET_DESC.equals(call.desc)) {
                method.instructions.set(call, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        ORE_CACHE_HELPER,
                        "safeSet",
                        "(Ljava/util/BitSet;I)V",
                        false
                ));
                changed = true;
            }
        }
        return changed;
    }

    private static boolean hasHelperCall(MethodNode method, String helperMethod) {
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (SETTINGS_HELPER.equals(call.owner) && helperMethod.equals(call.name)) {
                    return true;
                }
            }
        }
        return false;
    }
}
