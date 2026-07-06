package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class WirelessRedstoneServerAddonsTransformer implements IClassTransformer {
    private static final String TARGET = "codechicken.wirelessredstone.handler.WREventHandler";
    private static final String WORLD_TICK_DESC =
            "(Lnet/minecraftforge/fml/common/gameevent/TickEvent$WorldTickEvent;)V";
    private static final String ADDONS_OWNER = "codechicken/wirelessredstone/manager/RedstoneEtherAddons";
    private static final String SERVER_DESC =
            "()Lcodechicken/wirelessredstone/manager/RedstoneEtherServerAddons;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.wirelessRedstoneSkipNullServerAddonsWorldTickEnabled()) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, ClassReader.EXPAND_FRAMES);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("worldTick".equals(method.name) && WORLD_TICK_DESC.equals(method.desc)) {
                    changed |= patchWorldTick(method);
                }
            }
            if (!changed) {
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return "java/lang/Object";
                }
            };
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static boolean patchWorldTick(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !ADDONS_OWNER.equals(call.owner)
                    || !"server".equals(call.name)
                    || !SERVER_DESC.equals(call.desc)) {
                continue;
            }

            InsnList guard = new InsnList();
            LabelNode serverPresent = new LabelNode();
            guard.add(new InsnNode(Opcodes.DUP));
            guard.add(new JumpInsnNode(Opcodes.IFNONNULL, serverPresent));
            guard.add(new InsnNode(Opcodes.POP));
            guard.add(new InsnNode(Opcodes.RETURN));
            guard.add(serverPresent);
            method.instructions.insert(instruction, guard);
            changed = true;
        }
        return changed;
    }
}
