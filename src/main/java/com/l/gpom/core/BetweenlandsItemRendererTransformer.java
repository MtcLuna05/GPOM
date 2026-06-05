package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class BetweenlandsItemRendererTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.safeItemTileRenderers", "true"));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!"thebetweenlands.client.render.tile.RenderItemStackAsTileEntity".equals(className)
                || !TargetedModVersions.isBetweenlandsClass(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"func_192838_a".equals(method.name)
                        || !"(Lnet/minecraft/item/ItemStack;F)V".equals(method.desc)) {
                    continue;
                }
                changed |= redirectTileRendererCalls(method);
            }
            if (changed) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            }
        } catch (Throwable ignored) {
        }
        return basicClass;
    }

    private static boolean redirectTileRendererCalls(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn.getOpcode() != Opcodes.ACONST_NULL) {
                insn = next;
                continue;
            }

            MethodInsnNode renderCall = findFollowingTesrRenderCall(insn);
            if (renderCall == null) {
                insn = next;
                continue;
            }

            InsnList replacement = new InsnList();
            replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
            method.instructions.insertBefore(insn, replacement);
            method.instructions.remove(insn);

            method.instructions.set(renderCall, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/BetweenlandsItemRenderOptimizations",
                    "renderItemStack",
                    "(Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;Lnet/minecraft/item/ItemStack;DDDFIF)V",
                    false
            ));
            changed = true;
            insn = next;
        }
        return changed;
    }

    private static MethodInsnNode findFollowingTesrRenderCall(AbstractInsnNode start) {
        for (AbstractInsnNode cursor = start.getNext(); cursor != null; cursor = cursor.getNext()) {
            if (cursor.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode methodInsn = (MethodInsnNode) cursor;
                if ("net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer".equals(methodInsn.owner)
                        && "func_192841_a".equals(methodInsn.name)
                        && "(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V".equals(methodInsn.desc)) {
                    return methodInsn;
                }
                return null;
            }
            if (cursor.getOpcode() == Opcodes.ARETURN || cursor.getOpcode() == Opcodes.RETURN) {
                return null;
            }
        }
        return null;
    }
}
