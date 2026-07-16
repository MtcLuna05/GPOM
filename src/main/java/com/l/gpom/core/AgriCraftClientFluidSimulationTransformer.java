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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class AgriCraftClientFluidSimulationTransformer implements IClassTransformer {
    private static final String CHANNEL = "com.infinityraider.agricraft.tiles.irrigation.TileEntityChannel";
    private static final String TANK = "com.infinityraider.agricraft.tiles.irrigation.TileEntityTank";
    private static final String WORLD_DESC = "Lnet/minecraft/world/World;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.agriCraftSkipClientFluidSimulationEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!CHANNEL.equals(className) && !TANK.equals(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, ClassReader.EXPAND_FRAMES);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("func_73660_a".equals(method.name) && "()V".equals(method.desc)) {
                    injectClientReturn(node.name, method);
                    changed = true;
                    break;
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
            GPOM.LOGGER.info("[GPOM AgriCraft Client Fluid] Patched {}", className);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM AgriCraft Client Fluid] Failed to patch {}; continuing with original bytecode", className, throwable);
            return basicClass;
        }
    }

    private static void injectClientReturn(String owner, MethodNode method) {
        LabelNode runOriginal = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, owner, "field_145850_b", WORLD_DESC));
        guard.add(new JumpInsnNode(Opcodes.IFNULL, runOriginal));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, owner, "field_145850_b", WORLD_DESC));
        guard.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/World", "field_72995_K", "Z"));
        guard.add(new JumpInsnNode(Opcodes.IFEQ, runOriginal));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(runOriginal);
        method.instructions.insert(guard);
    }
}
