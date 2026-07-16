package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class TwilightForestPortalDimensionTransformer implements IClassTransformer {
    private static final String TARGET = "twilightforest.TFTickHandler";
    private static final String PORTAL_BLOCK = "twilightforest.block.BlockTFPortal";
    private static final String HOOKS = "com/l/gpom/compat/twilightforest/TwilightForestPortalDimensionCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.twilightForestPortalCreationDimensionsEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className) && !PORTAL_BLOCK.equals(className)) {
            return basicClass;
        }
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = TARGET.equals(className)
                    ? patchTickHandler(node)
                    : patchPortalBlock(node);
            if (!changed) {
                return basicClass;
            }
            ClassWriter writer = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM Twilight Forest Portals] Patched {}", className);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Twilight Forest Portals] Failed to patch {}; continuing with original bytecode", className, throwable);
            return basicClass;
        }
    }

    private static boolean patchTickHandler(ClassNode node) {
        for (MethodNode method : node.methods) {
            if ("checkForPortalCreation".equals(method.name)
                    && "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;F)V".equals(method.desc)) {
                addScanProbe(method);
                boolean gateChanged = replaceOtherDimensionGate(method);
                boolean canFormChanged = replaceCanFormPortalCall(method);
                return gateChanged || canFormChanged;
            }
        }
        return false;
    }

    private static boolean patchPortalBlock(ClassNode node) {
        for (MethodNode method : node.methods) {
            if ("tryToCreatePortal".equals(method.name)
                    && "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(method.desc)) {
                return wrapTryToCreatePortalReturns(method);
            }
        }
        return false;
    }

    private static void addScanProbe(MethodNode method) {
        InsnList probe = new InsnList();
        probe.add(new VarInsnNode(Opcodes.ALOAD, 0));
        probe.add(new VarInsnNode(Opcodes.ALOAD, 1));
        probe.add(new VarInsnNode(Opcodes.FLOAD, 2));
        probe.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOKS,
                "probePortalScan",
                "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;F)V",
                false
        ));
        method.instructions.insert(probe);
        method.maxStack = Math.max(method.maxStack, 3);
    }

    private static boolean replaceOtherDimensionGate(MethodNode method) {
        for (AbstractInsnNode cursor = method.instructions.getFirst(); cursor != null; cursor = cursor.getNext()) {
            if (!(cursor instanceof FieldInsnNode)) {
                continue;
            }
            FieldInsnNode field = (FieldInsnNode) cursor;
            if (field.getOpcode() == Opcodes.GETSTATIC
                    && "twilightforest/TFConfig".equals(field.owner)
                && "allowPortalsInOtherDimensions".equals(field.name)
                    && "Z".equals(field.desc)) {
                InsnList replacement = new InsnList();
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOKS,
                        "allowPortalCreationInCurrentDimension",
                        "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;)Z",
                        false
                ));
                method.instructions.insertBefore(cursor, replacement);
                method.instructions.remove(cursor);
                method.maxStack = Math.max(method.maxStack, 2);
                return true;
            }
        }
        return false;
    }

    private static boolean replaceCanFormPortalCall(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode cursor = method.instructions.getFirst(); cursor != null; cursor = cursor.getNext()) {
            if (!(cursor instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) cursor;
            if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                    && "twilightforest/block/BlockTFPortal".equals(call.owner)
                    && "canFormPortal".equals(call.name)
                    && "(Lnet/minecraft/block/state/IBlockState;)Z".equals(call.desc)) {
                MethodInsnNode replacement = new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOKS,
                        "canFormPortalWithProbe",
                        "(Ljava/lang/Object;Lnet/minecraft/block/state/IBlockState;)Z",
                        false
                );
                method.instructions.insertBefore(call, replacement);
                method.instructions.remove(call);
                method.maxStack = Math.max(method.maxStack, 2);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean wrapTryToCreatePortalReturns(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode cursor = method.instructions.getFirst(); cursor != null; cursor = cursor.getNext()) {
            if (cursor.getOpcode() != Opcodes.IRETURN) {
                continue;
            }
            InsnList wrapper = new InsnList();
            wrapper.add(new InsnNode(Opcodes.DUP));
            wrapper.add(new VarInsnNode(Opcodes.ALOAD, 1));
            wrapper.add(new VarInsnNode(Opcodes.ALOAD, 2));
            wrapper.add(new VarInsnNode(Opcodes.ALOAD, 3));
            wrapper.add(new VarInsnNode(Opcodes.ALOAD, 4));
            wrapper.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOKS,
                    "logTryCreatePortalResult",
                    "(ZLnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/item/EntityItem;Lnet/minecraft/entity/player/EntityPlayer;)Z",
                    false
            ));
            method.instructions.insertBefore(cursor, wrapper);
            method.maxStack = Math.max(method.maxStack, 5);
            changed = true;
        }
        return changed;
    }

    private static final class SafeClassWriter extends ClassWriter {
        private SafeClassWriter(int flags) {
            super(flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            return "java/lang/Object";
        }
    }
}
