package com.l.gpom.core;

import com.l.gpom.GPOM;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class OpenBlocksTankRenderTransformer implements IClassTransformer {
    private static final String TARGET = "openblocks.client.renderer.tileentity.TileEntityTankRenderer";
    private static final String ADD_VERTEX_DESC =
            "(Lnet/minecraft/client/renderer/BufferBuilder;DDDDDIIIIII)V";
    private static final String COMPAT_INTERNAL =
            "com/l/gpom/compat/openblocks/OpenBlocksTankRenderCompat";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return null;
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
                if ("addVertex".equals(method.name) && ADD_VERTEX_DESC.equals(method.desc)) {
                    method.instructions.insert(insetPositionCoordinates());
                    changed = true;
                    break;
                }
            }
            if (!changed) {
                GPOM.LOGGER.warn("[GPOM OpenBlocks Tank] addVertex signature not found; fluid inset not applied");
                return basicClass;
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS) {
                @Override
                protected String getCommonSuperClass(String type1, String type2) {
                    return "java/lang/Object";
                }
            };
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM OpenBlocks Tank] Inset fluid boundary vertices to avoid z-fighting");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM OpenBlocks Tank] Failed to inset fluid vertices; continuing with original bytecode", throwable);
            return basicClass;
        }
    }

    private static InsnList insetPositionCoordinates() {
        InsnList instructions = new InsnList();
        insetDoubleLocal(instructions, 1);
        insetDoubleLocal(instructions, 3);
        insetDoubleLocal(instructions, 5);
        return instructions;
    }

    private static void insetDoubleLocal(InsnList instructions, int local) {
        instructions.add(new VarInsnNode(Opcodes.DLOAD, local));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                COMPAT_INTERNAL,
                "insetBoundaryCoordinate",
                "(D)D",
                false
        ));
        instructions.add(new VarInsnNode(Opcodes.DSTORE, local));
    }
}
