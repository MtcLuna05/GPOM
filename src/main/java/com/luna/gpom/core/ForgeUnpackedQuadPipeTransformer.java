package com.luna.gpom.core;

import com.luna.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Hoists invariant vertex-format element counts out of Forge's per-quad element loop. */
public final class ForgeUnpackedQuadPipeTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraftforge.client.model.pipeline.UnpackedBakedQuad";
    private static final String TARGET_INTERNAL = "net/minecraftforge/client/model/pipeline/UnpackedBakedQuad";
    private static final String CONSUMER_INTERNAL = "net/minecraftforge/client/model/pipeline/IVertexConsumer";
    private static final String FORMAT_INTERNAL = "net/minecraft/client/renderer/vertex/VertexFormat";
    private static final String PIPE_DESCRIPTOR =
            "(Lnet/minecraftforge/client/model/pipeline/IVertexConsumer;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.forgeVertexPackingEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className)) {
            return basicClass;
        }

        ClassNode node = new ClassNode();
        new ClassReader(basicClass).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!"pipe".equals(method.name) || !PIPE_DESCRIPTOR.equals(method.desc)) {
                continue;
            }
            if (!hoistElementCounts(method)) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            return writer.toByteArray();
        }
        return basicClass;
    }

    private static boolean hoistElementCounts(MethodNode method) {
        AbstractInsnNode consumerStart = null;
        AbstractInsnNode consumerEnd = null;
        AbstractInsnNode sourceStart = null;
        AbstractInsnNode sourceEnd = null;

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!FORMAT_INTERNAL.equals(call.owner)
                    || !"func_177345_h".equals(call.name)
                    || !"()I".equals(call.desc)) {
                continue;
            }

            AbstractInsnNode receiver = previousOpcode(instruction);
            AbstractInsnNode receiverLoad = previousOpcode(receiver);
            if (receiver instanceof MethodInsnNode) {
                MethodInsnNode getFormat = (MethodInsnNode) receiver;
                if (consumerStart == null
                        && CONSUMER_INTERNAL.equals(getFormat.owner)
                        && "getVertexFormat".equals(getFormat.name)
                        && "()Lnet/minecraft/client/renderer/vertex/VertexFormat;".equals(getFormat.desc)
                        && isReferenceLoad(receiverLoad, 1)) {
                    consumerStart = receiverLoad;
                    consumerEnd = instruction;
                }
            } else if (receiver instanceof FieldInsnNode) {
                FieldInsnNode getFormat = (FieldInsnNode) receiver;
                if (sourceStart == null
                        && getFormat.getOpcode() == Opcodes.GETFIELD
                        && TARGET_INTERNAL.equals(getFormat.owner)
                        && "format".equals(getFormat.name)
                        && ("L" + FORMAT_INTERNAL + ";").equals(getFormat.desc)
                        && isReferenceLoad(receiverLoad, 0)) {
                    sourceStart = receiverLoad;
                    sourceEnd = instruction;
                }
            }
        }

        if (consumerStart == null || sourceStart == null) {
            return false;
        }

        int consumerCountLocal = method.maxLocals;
        int sourceCountLocal = consumerCountLocal + 1;
        method.maxLocals += 2;

        InsnList prelude = new InsnList();
        prelude.add(new VarInsnNode(Opcodes.ALOAD, 1));
        prelude.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                CONSUMER_INTERNAL,
                "getVertexFormat",
                "()Lnet/minecraft/client/renderer/vertex/VertexFormat;",
                true
        ));
        prelude.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FORMAT_INTERNAL, "func_177345_h", "()I", false));
        prelude.add(new VarInsnNode(Opcodes.ISTORE, consumerCountLocal));
        prelude.add(new VarInsnNode(Opcodes.ALOAD, 0));
        prelude.add(new FieldInsnNode(
                Opcodes.GETFIELD,
                TARGET_INTERNAL,
                "format",
                "L" + FORMAT_INTERNAL + ";"
        ));
        prelude.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, FORMAT_INTERNAL, "func_177345_h", "()I", false));
        prelude.add(new VarInsnNode(Opcodes.ISTORE, sourceCountLocal));
        method.instructions.insert(prelude);

        replaceRange(method.instructions, consumerStart, consumerEnd,
                new VarInsnNode(Opcodes.ILOAD, consumerCountLocal));
        replaceRange(method.instructions, sourceStart, sourceEnd,
                new VarInsnNode(Opcodes.ILOAD, sourceCountLocal));
        return true;
    }

    private static boolean isReferenceLoad(AbstractInsnNode instruction, int local) {
        return instruction instanceof VarInsnNode
                && instruction.getOpcode() == Opcodes.ALOAD
                && ((VarInsnNode) instruction).var == local;
    }

    private static AbstractInsnNode previousOpcode(AbstractInsnNode instruction) {
        if (instruction == null) {
            return null;
        }
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private static void replaceRange(InsnList instructions, AbstractInsnNode start,
                                     AbstractInsnNode end, AbstractInsnNode replacement) {
        instructions.insertBefore(start, replacement);
        AbstractInsnNode current = start;
        while (current != null) {
            AbstractInsnNode next = current.getNext();
            instructions.remove(current);
            if (current == end) {
                return;
            }
            current = next;
        }
    }
}
