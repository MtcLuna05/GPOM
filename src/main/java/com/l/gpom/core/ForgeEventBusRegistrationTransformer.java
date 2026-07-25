package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Removes Forge's exception-driven legacy EventBus registration probe. */
public final class ForgeEventBusRegistrationTransformer implements IClassTransformer {
    private static final String EVENT_BUS = "net.minecraftforge.fml.common.eventhandler.EventBus";
    private static final String REGISTER_DESC = "(Ljava/lang/Object;)V";
    private static final String CLASS = "java/lang/Class";
    private static final String GET_DECLARED_METHOD = "getDeclaredMethod";
    private static final String GET_DECLARED_METHOD_DESC = "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;";
    private static final String HELPER = "com/l/gpom/optimization/ForgeEventBusRegistrationOptimizations";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.forgeEventBusSkipLegacyForceClassLoadingProbeEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!EVENT_BUS.equals(className)) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            if (!patchRegister(node)) {
                GPOM.LOGGER.warn("[GPOM EventBus] Expected legacy force-class-loading probe was not found; keeping Forge EventBus unchanged");
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM EventBus] Removed Forge's exception-driven legacy event-handler class-loading probe");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM EventBus] Failed to optimize Forge EventBus registration; keeping Forge behavior", throwable);
            return basicClass;
        }
    }

    private static boolean patchRegister(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (!"register".equals(method.name) || !REGISTER_DESC.equals(method.desc)) {
                continue;
            }
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                        || !CLASS.equals(call.owner)
                        || !GET_DECLARED_METHOD.equals(call.name)
                        || !GET_DECLARED_METHOD_DESC.equals(call.desc)) {
                    continue;
                }
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = HELPER;
                call.name = "skipLegacyForceClassLoadingProbe";
                call.desc = "(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;";
                call.itf = false;
                return true;
            }
        }
        return false;
    }
}
