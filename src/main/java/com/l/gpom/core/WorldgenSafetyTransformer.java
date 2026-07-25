package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.List;

/** Exact-version guards for worldgen defects observed in the installed pack. */
public final class WorldgenSafetyTransformer implements IClassTransformer {
    private static final String BOP_CLIMATES = "biomesoplenty.api.enums.BOPClimates";
    private static final String CRIMSON_GENERATOR = "com.tage.crimson_warfare.world.CrimsonWorldGenerator";
    private static final String PILLAR_GENERATOR = "vazkii.pillar.WorldGenerator";
    private static final String PILLAR_HELPER = "com/l/gpom/compat/pillar/PillarWorldgenCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        try {
            if (BOP_CLIMATES.equals(className) && GpomEarlyConfig.bopClimateLookupGuardEnabled()) {
                return patchBopClimateLookup(basicClass);
            }
            if (CRIMSON_GENERATOR.equals(className) && GpomEarlyConfig.crimsonBoundarySpawnGuardEnabled()) {
                return patchCrimsonBoundarySpawns(basicClass);
            }
            if (PILLAR_GENERATOR.equals(className) && GpomEarlyConfig.pillarNeighborChunkGuardEnabled()) {
                return patchPillarPlacement(basicClass);
            }
        } catch (Throwable ignored) {
            // Optional exact-version patches must fail closed.
        }
        return basicClass;
    }

    private static byte[] patchBopClimateLookup(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        for (MethodNode method : node.methods) {
            if (!"lookup".equals(method.name)
                    || !"(I)Lbiomesoplenty/api/enums/BOPClimates;".equals(method.desc)) {
                continue;
            }
            InsnList replacement = new InsnList();
            LabelNode nonNegative = new LabelNode();
            LabelNode valid = new LabelNode();
            LabelNode load = new LabelNode();
            replacement.add(new VarInsnNode(Opcodes.ILOAD, 0));
            replacement.add(new JumpInsnNode(Opcodes.IFGE, nonNegative));
            replacement.add(new IntInsnNode(Opcodes.BIPUSH, 7));
            replacement.add(new VarInsnNode(Opcodes.ISTORE, 1));
            replacement.add(new JumpInsnNode(Opcodes.GOTO, load));
            replacement.add(nonNegative);
            replacement.add(new VarInsnNode(Opcodes.ILOAD, 0));
            replacement.add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    "biomesoplenty/api/enums/BOPClimates",
                    "values",
                    "[Lbiomesoplenty/api/enums/BOPClimates;"
            ));
            replacement.add(new InsnNode(Opcodes.ARRAYLENGTH));
            replacement.add(new JumpInsnNode(Opcodes.IF_ICMPLT, valid));
            replacement.add(new IntInsnNode(Opcodes.BIPUSH, 7));
            replacement.add(new VarInsnNode(Opcodes.ISTORE, 1));
            replacement.add(new JumpInsnNode(Opcodes.GOTO, load));
            replacement.add(valid);
            replacement.add(new VarInsnNode(Opcodes.ILOAD, 0));
            replacement.add(new VarInsnNode(Opcodes.ISTORE, 1));
            replacement.add(load);
            replacement.add(new FieldInsnNode(
                    Opcodes.GETSTATIC,
                    "biomesoplenty/api/enums/BOPClimates",
                    "values",
                    "[Lbiomesoplenty/api/enums/BOPClimates;"
            ));
            replacement.add(new VarInsnNode(Opcodes.ILOAD, 1));
            replacement.add(new InsnNode(Opcodes.AALOAD));
            replacement.add(new InsnNode(Opcodes.ARETURN));
            method.instructions = replacement;
            return writeNode(node);
        }
        return basicClass;
    }

    private static byte[] patchCrimsonBoundarySpawns(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        for (MethodNode method : node.methods) {
            if (!"generateSurface".equals(method.name)
                    || !"(Lnet/minecraft/world/World;Ljava/util/Random;II)V".equals(method.desc)) {
                continue;
            }
            List<AbstractInsnNode> instructions = java.util.Arrays.asList(method.instructions.toArray());
            int patched = 0;
            for (int i = 0; i + 3 < instructions.size(); i++) {
                AbstractInsnNode random = instructions.get(i);
                AbstractInsnNode lower = instructions.get(i + 1);
                AbstractInsnNode upper = instructions.get(i + 2);
                AbstractInsnNode call = instructions.get(i + 3);
                if (!(random instanceof VarInsnNode)
                        || ((VarInsnNode) random).var != 2
                        || !(lower instanceof IntInsnNode)
                        || ((IntInsnNode) lower).operand != -4
                        || upper.getOpcode() != Opcodes.ICONST_4
                        || !(call instanceof MethodInsnNode)
                        || call.getOpcode() != Opcodes.INVOKESTATIC
                        || !"net/minecraft/util/math/MathHelper".equals(((MethodInsnNode) call).owner)
                        || !"func_76136_a".equals(((MethodInsnNode) call).name)) {
                    continue;
                }
                ((IntInsnNode) lower).operand = -1;
                method.instructions.set(upper, new InsnNode(Opcodes.ICONST_1));
                patched++;
            }
            return patched == 2 ? writeNode(node) : basicClass;
        }
        return basicClass;
    }

    private static byte[] patchPillarPlacement(byte[] basicClass) {
        ClassNode node = readNode(basicClass);
        for (MethodNode method : node.methods) {
            if (!"generateStructure".equals(method.name)
                    || !"(Lvazkii/pillar/schema/StructureSchema;Ljava/util/Random;Lnet/minecraft/world/World;II)Lnet/minecraft/util/EnumActionResult;".equals(method.desc)) {
                continue;
            }
            AbstractInsnNode[] instructions = method.instructions.toArray();
            for (int i = 0; i < instructions.length; i++) {
                AbstractInsnNode instruction = instructions[i];
                if (!(instruction instanceof MethodInsnNode)
                        || instruction.getOpcode() != Opcodes.INVOKESTATIC
                        || !"vazkii/pillar/StructureGenerator".equals(((MethodInsnNode) instruction).owner)
                        || !"placeStructureAtPosition".equals(((MethodInsnNode) instruction).name)
                        || !((MethodInsnNode) instruction).desc.endsWith("IZ)Z")) {
                    continue;
                }
                AbstractInsnNode anchor = null;
                for (int j = i - 1; j >= 0 && i - j <= 9; j--) {
                    if (instructions[j].getOpcode() == Opcodes.ALOAD && ((VarInsnNode) instructions[j]).var == 2) {
                        anchor = instructions[j];
                        break;
                    }
                }
                if (anchor == null) {
                    return basicClass;
                }
                LabelNode continuePlacement = new LabelNode();
                InsnList guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD, 3));
                guard.add(new VarInsnNode(Opcodes.ALOAD, 10));
                guard.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        PILLAR_HELPER,
                        "canPlaceWithoutNeighborChunkGeneration",
                        "(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Z",
                        false
                ));
                guard.add(new JumpInsnNode(Opcodes.IFNE, continuePlacement));
                guard.add(new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "net/minecraft/util/EnumActionResult",
                        "FAIL",
                        "Lnet/minecraft/util/EnumActionResult;"
                ));
                guard.add(new InsnNode(Opcodes.ARETURN));
                guard.add(continuePlacement);
                method.instructions.insertBefore(anchor, guard);
                return writeNode(node);
            }
        }
        return basicClass;
    }

    private static ClassNode readNode(byte[] basicClass) {
        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        return node;
    }

    private static byte[] writeNode(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        return writer.toByteArray();
    }
}
