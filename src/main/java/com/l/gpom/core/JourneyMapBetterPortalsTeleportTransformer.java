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

public final class JourneyMapBetterPortalsTeleportTransformer implements IClassTransformer {
    private static final String TARGET_CLASS = "journeymap.common.feature.JourneyMapTeleport";
    private static final String TARGET_METHOD = "teleportEntity";
    private static final String TARGET_DESC = "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Ljourneymap/common/feature/Location;F)Z";
    private static final String HELPER = "com/l/gpom/compat/journeymap/JourneyMapBetterPortalsTeleportCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.betterPortalsJourneyMapWaypointTeleportTransitionEnabled()) {
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
                    insertBetterPortalsTransitionGuard(method);
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

    private static void insertBetterPortalsTransitionGuard(MethodNode method) {
        LabelNode continueJourneyMapTeleport = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 2));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 3));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 4));
        guard.add(new VarInsnNode(Opcodes.FLOAD, 5));
        guard.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HELPER,
                "tryTeleportWithBetterPortalsTransition",
                "(Lnet/minecraft/server/MinecraftServer;Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Ljava/lang/Object;F)Z",
                false
        ));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, continueJourneyMapTeleport));
        guard.add(new InsnNode(Opcodes.ICONST_1));
        guard.add(new InsnNode(Opcodes.IRETURN));
        guard.add(continueJourneyMapTeleport);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insert(guard);
    }
}
