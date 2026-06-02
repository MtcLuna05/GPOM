package com.l.cleanroomoptimizations.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BetweenlandsStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("cleanroomoptimizations.betweenlandsProfiler", "true"));
    private static final boolean FAST_ENTITY_REGISTRATION = Boolean.parseBoolean(System.getProperty("cleanroomoptimizations.betweenlands.fastEntityRegistration", "true"));
    private static final Map<String, Set<MethodKey>> TARGETS = createTargets();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if ("thebetweenlands.common.registries.EntityRegistry".equals(className) && FAST_ENTITY_REGISTRATION) {
            basicClass = patchEntityRegistry(basicClass);
        }

        Set<MethodKey> methods = TARGETS.get(className);
        if (methods == null) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new BetweenlandsClassVisitor(writer, className, methods), 0);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static Map<String, Set<MethodKey>> createTargets() {
        Map<String, Set<MethodKey>> targets = new HashMap<>();

        add(targets, "thebetweenlands.common.TheBetweenlands", "preInit", "(Lnet/minecraftforge/fml/common/event/FMLPreInitializationEvent;)V");

        add(targets, "thebetweenlands.common.registries.Registries", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.Registries", "preInit", "()V");
        add(targets, "thebetweenlands.common.registries.Registries", "init", "()V");

        add(targets, "thebetweenlands.common.registries.FluidRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.FluidRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.BlockRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.BlockRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.ItemRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.ItemRegistry", "preInit", "()V");
        add(targets, "thebetweenlands.common.registries.ItemRegistry", "registerItemTypes", "()V");
        add(targets, "thebetweenlands.common.registries.ItemRegistry", "registerOreDictionary", "()V");

        add(targets, "thebetweenlands.common.registries.EntityRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.EntityRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.SoundRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.SoundRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.ModelRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.ModelRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.AmbienceRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.AmbienceRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.client.proxy.ClientProxy", "<init>", "()V");
        add(targets, "thebetweenlands.client.proxy.ClientProxy", "registerItemAndBlockRenderers", "()V");
        add(targets, "thebetweenlands.client.proxy.ClientProxy", "preInit", "()V");
        add(targets, "thebetweenlands.client.proxy.ClientProxy", "registerEventHandlersPreInit", "()V");
        add(targets, "thebetweenlands.client.proxy.ClientProxy", "registerEventHandlers", "()V");
        add(targets, "thebetweenlands.client.proxy.ClientProxy", "loadRiftVariants", "()V");

        add(targets, "thebetweenlands.client.render.particle.BLParticles", "<clinit>", "()V");
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler", "<clinit>", "()V");
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler", "<init>", "()V");
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler$TextureStitcher", "<init>", "([Lnet/minecraft/util/ResourceLocation;)V");
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler$TextureStitcher", "<init>", "(Ljava/util/function/Consumer;[Lnet/minecraft/util/ResourceLocation;)V");

        return targets;
    }

    private static void add(Map<String, Set<MethodKey>> targets, String className, String methodName, String descriptor) {
        targets.computeIfAbsent(className, ignored -> new HashSet<>()).add(new MethodKey(methodName, descriptor));
    }

    private static byte[] patchEntityRegistry(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"registerEntity".equals(method.name)) {
                    continue;
                }
                if ("(Ljava/lang/Class;Ljava/lang/String;IIZ)V".equals(method.desc)) {
                    replaceRegisterModEntity(method, 2, 3, 4);
                    changed = true;
                } else if ("(Ljava/lang/Class;Ljava/lang/String;)V".equals(method.desc)) {
                    replaceRegisterModEntity(method, 64, 3, true);
                    changed = true;
                } else if ("(Ljava/lang/Class;Ljava/lang/String;IIIIZ)V".equals(method.desc)) {
                    replaceRegisterLivingEntity(method, 4, 5, 6, 2, 3);
                    changed = true;
                } else if ("(Ljava/lang/Class;Ljava/lang/String;II)V".equals(method.desc)) {
                    replaceRegisterLivingEntity(method, 64, 3, true, 2, 3);
                    changed = true;
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

    private static void replaceRegisterModEntity(MethodNode method, int rangeLocal, int updateLocal, int velocityLocal) {
        method.instructions = new InsnList();
        method.tryCatchBlocks.clear();
        loadClassNameId(method.instructions);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, rangeLocal));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, updateLocal));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, velocityLocal));
        callRegisterModEntity(method.instructions);
        incrementId(method.instructions);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static void replaceRegisterModEntity(MethodNode method, int range, int updateFrequency, boolean sendVelocityUpdates) {
        method.instructions = new InsnList();
        method.tryCatchBlocks.clear();
        loadClassNameId(method.instructions);
        pushInt(method.instructions, range);
        pushInt(method.instructions, updateFrequency);
        method.instructions.add(new InsnNode(sendVelocityUpdates ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        callRegisterModEntity(method.instructions);
        incrementId(method.instructions);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static void replaceRegisterLivingEntity(MethodNode method, int rangeLocal, int updateLocal, int velocityLocal, int primaryLocal, int secondaryLocal) {
        method.instructions = new InsnList();
        method.tryCatchBlocks.clear();
        loadClassNameId(method.instructions);
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, rangeLocal));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, updateLocal));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, velocityLocal));
        callRegisterModEntity(method.instructions);
        incrementId(method.instructions);
        callRegisterEgg(method.instructions, primaryLocal, secondaryLocal);
        incrementId(method.instructions);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static void replaceRegisterLivingEntity(MethodNode method, int range, int updateFrequency, boolean sendVelocityUpdates, int primaryLocal, int secondaryLocal) {
        method.instructions = new InsnList();
        method.tryCatchBlocks.clear();
        loadClassNameId(method.instructions);
        pushInt(method.instructions, range);
        pushInt(method.instructions, updateFrequency);
        method.instructions.add(new InsnNode(sendVelocityUpdates ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
        callRegisterModEntity(method.instructions);
        incrementId(method.instructions);
        callRegisterEgg(method.instructions, primaryLocal, secondaryLocal);
        incrementId(method.instructions);
        method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    private static void loadClassNameId(InsnList instructions) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "thebetweenlands/common/registries/EntityRegistry", "id", "I"));
    }

    private static void callRegisterModEntity(InsnList instructions) {
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/l/cleanroomoptimizations/profiling/BetweenlandsOptimizations",
                "registerModEntity",
                "(Ljava/lang/Class;Ljava/lang/String;IIIZ)V",
                false
        ));
    }

    private static void callRegisterEgg(InsnList instructions, int primaryLocal, int secondaryLocal) {
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, primaryLocal));
        instructions.add(new VarInsnNode(Opcodes.ILOAD, secondaryLocal));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/l/cleanroomoptimizations/profiling/BetweenlandsOptimizations",
                "registerEgg",
                "(Ljava/lang/String;II)V",
                false
        ));
    }

    private static void incrementId(InsnList instructions) {
        instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "thebetweenlands/common/registries/EntityRegistry", "id", "I"));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new InsnNode(Opcodes.IADD));
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, "thebetweenlands/common/registries/EntityRegistry", "id", "I"));
    }

    private static void pushInt(InsnList instructions, int value) {
        if (value >= -1 && value <= 5) {
            instructions.add(new InsnNode(Opcodes.ICONST_0 + value));
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            instructions.add(new IntInsnNode(Opcodes.BIPUSH, value));
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            instructions.add(new IntInsnNode(Opcodes.SIPUSH, value));
        } else {
            throw new IllegalArgumentException("Unsupported integer constant: " + value);
        }
    }

    private static final class BetweenlandsClassVisitor extends ClassVisitor {
        private final String className;
        private final Set<MethodKey> methods;

        private BetweenlandsClassVisitor(ClassVisitor delegate, String className, Set<MethodKey> methods) {
            super(Opcodes.ASM5, delegate);
            this.className = className;
            this.methods = methods;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null || !methods.contains(new MethodKey(name, desc))) {
                return visitor;
            }
            return new TimedMethodVisitor(visitor, "BL " + className + '.' + name);
        }
    }

    private static final class TimedMethodVisitor extends MethodVisitor {
        private final String label;
        private boolean entered;

        private TimedMethodVisitor(MethodVisitor delegate, String label) {
            super(Opcodes.ASM5, delegate);
            this.label = label;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            entered = true;
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/cleanroomoptimizations/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if (entered && isExit(opcode)) {
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/cleanroomoptimizations/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
            }
            super.visitInsn(opcode);
        }

        private static boolean isExit(int opcode) {
            return opcode == Opcodes.RETURN
                    || opcode == Opcodes.IRETURN
                    || opcode == Opcodes.LRETURN
                    || opcode == Opcodes.FRETURN
                    || opcode == Opcodes.DRETURN
                    || opcode == Opcodes.ARETURN
                    || opcode == Opcodes.ATHROW;
        }
    }

    private static final class MethodKey {
        private final String name;
        private final String descriptor;

        private MethodKey(String name, String descriptor) {
            this.name = name;
            this.descriptor = descriptor;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof MethodKey)) {
                return false;
            }
            MethodKey methodKey = (MethodKey) other;
            return name.equals(methodKey.name) && descriptor.equals(methodKey.descriptor);
        }

        @Override
        public int hashCode() {
            return 31 * name.hashCode() + descriptor.hashCode();
        }
    }
}
