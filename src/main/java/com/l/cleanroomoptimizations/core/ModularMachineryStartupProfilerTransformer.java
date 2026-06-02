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
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ModularMachineryStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("cleanroomoptimizations.mmProfiler", "true"));
    private static final boolean CACHE_MANIFEST = Boolean.parseBoolean(System.getProperty("cleanroomoptimizations.mm.cacheManifest", "true"));
    private static final boolean LAZY_STRUCTURE_CACHE = Boolean.parseBoolean(System.getProperty("cleanroomoptimizations.mm.lazyStructureCache", "true"));
    private static final Map<String, Set<MethodKey>> TARGETS = createTargets();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if ("hellfirepvp.modularmachinery.common.util.BlockArrayCache".equals(className) && LAZY_STRUCTURE_CACHE) {
            basicClass = patchBlockArrayCacheLazy(basicClass);
        }

        Set<MethodKey> methods = TARGETS.get(name);
        if (methods == null) {
            methods = TARGETS.get(className);
        }
        if (methods == null) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new MmClassVisitor(writer, className, methods), 0);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchBlockArrayCacheLazy(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("getBlockArrayCache".equals(method.name)
                        && "(Lhellfirepvp/modularmachinery/common/util/BlockArray;Lnet/minecraft/util/EnumFacing;)Lhellfirepvp/modularmachinery/common/util/BlockArray;".equals(method.desc)) {
                    replaceLazyGetter(method, "hellfirepvp/modularmachinery/common/util/BlockArray");
                    changed = true;
                } else if ("getBlockArrayCache".equals(method.name)
                        && "(Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;Lnet/minecraft/util/EnumFacing;)Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;".equals(method.desc)) {
                    replaceLazyGetter(method, "hellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray");
                    changed = true;
                } else if ("buildCache".equals(method.name)
                        && "(Ljava/util/Collection;)V".equals(method.desc)) {
                    replaceLazyBuild(method);
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

    private static void replaceLazyGetter(MethodNode method, String castType) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        InsnList replacement = method.instructions;
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/l/cleanroomoptimizations/profiling/ModularMachineryOptimizations",
                "getLazyBlockArrayCache",
                "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
                false
        ));
        replacement.add(new TypeInsnNode(Opcodes.CHECKCAST, castType));
        replacement.add(new InsnNode(Opcodes.ARETURN));
    }

    private static void replaceLazyBuild(MethodNode method) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        method.localVariables = null;
        InsnList replacement = method.instructions;
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/l/cleanroomoptimizations/profiling/ModularMachineryOptimizations",
                "skipMmStructureCacheBuild",
                "(Ljava/util/Collection;)Z",
                false
        ));
        replacement.add(new InsnNode(Opcodes.POP));
        replacement.add(new InsnNode(Opcodes.RETURN));
    }

    private static Map<String, Set<MethodKey>> createTargets() {
        Map<String, Set<MethodKey>> targets = new HashMap<>();
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineRegistry", "preloadMachines", "()V");
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineRegistry", "loadMachines", "(Lnet/minecraft/command/ICommandSender;)Ljava/util/Collection;");
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineRegistry", "registerMachines", "(Ljava/util/Collection;)V");
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineRegistry", "reloadMachine", "(Ljava/util/Collection;)V");

        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineLoader", "discoverDirectory", "(Ljava/io/File;)Ljava/util/Map;");
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineLoader", "registerMachines", "(Ljava/util/Collection;)Ljava/util/List;");
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineLoader", "loadMachines", "(Ljava/util/Collection;)Ljava/util/List;");
        add(targets, "hellfirepvp.modularmachinery.common.machine.MachineLoader", "prepareContext", "(Ljava/util/List;)V");

        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeRegistry", "loadRecipeRegistry", "(Lnet/minecraft/command/ICommandSender;Z)V");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeRegistry", "loadRecipes", "(Lnet/minecraft/command/ICommandSender;Ljava/util/Map;)Ljava/util/Map;");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeRegistry", "loadAndValidateRecipes", "(Ljava/util/Collection;Lhellfirepvp/modularmachinery/common/data/DataLoadProfiler;Ljava/util/Map;)Ljava/util/Map;");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeRegistry", "loadAdapters", "(Lnet/minecraft/command/ICommandSender;Ljava/util/Map;Ljava/util/List;)Ljava/util/Map;");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeRegistry", "registerRecipes", "(Ljava/util/Map;)V");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeRegistry", "reloadAdapters", "()V");

        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeLoader", "discoverDirectory", "(Ljava/io/File;)Ljava/util/Map;");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeLoader", "loadRecipes", "(Ljava/util/List;Ljava/util/List;)Ljava/util/Collection;");
        add(targets, "hellfirepvp.modularmachinery.common.crafting.RecipeLoader", "loadAdapterRecipes", "(Ljava/util/List;Ljava/util/List;)Ljava/util/List;");

        add(targets, "hellfirepvp.modularmachinery.common.util.BlockArrayCache", "buildCache", "(Ljava/util/Collection;)V");
        add(targets, "hellfirepvp.modularmachinery.common.util.BlockArrayCache", "buildMultiBlockModifierCache", "(Ljava/util/List;)V");
        add(targets, "hellfirepvp.modularmachinery.common.util.BlockArrayCache", "buildBlockArrayCache", "(Lhellfirepvp/modularmachinery/common/util/BlockArray;)V");
        add(targets, "hellfirepvp.modularmachinery.common.util.BlockArrayCache", "buildDynamicPatternCache", "(Lhellfirepvp/modularmachinery/common/machine/TaggedPositionBlockArray;)V");
        return targets;
    }

    private static void add(Map<String, Set<MethodKey>> targets, String className, String methodName, String descriptor) {
        targets.computeIfAbsent(className, key -> new HashSet<>()).add(new MethodKey(methodName, descriptor));
    }

    private static final class MmClassVisitor extends ClassVisitor {
        private final String className;
        private final Set<MethodKey> methods;

        private MmClassVisitor(ClassVisitor delegate, String className, Set<MethodKey> methods) {
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
            return new TimedMethodVisitor(visitor, "MM " + className + '.' + name, manifestStage(className, name));
        }

        private static String manifestStage(String className, String methodName) {
            if (!CACHE_MANIFEST) {
                return null;
            }
            if ("hellfirepvp.modularmachinery.common.crafting.RecipeRegistry".equals(className)
                    && "loadRecipeRegistry".equals(methodName)) {
                return "recipe-registry";
            }
            if ("hellfirepvp.modularmachinery.common.util.BlockArrayCache".equals(className)
                    && "buildCache".equals(methodName)) {
                return "structure-cache";
            }
            return null;
        }
    }

    private static final class TimedMethodVisitor extends MethodVisitor {
        private final String label;
        private final String manifestStage;
        private boolean entered;

        private TimedMethodVisitor(MethodVisitor delegate, String label, String manifestStage) {
            super(Opcodes.ASM5, delegate);
            this.label = label;
            this.manifestStage = manifestStage;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            entered = true;
            if (manifestStage != null) {
                super.visitLdcInsn(manifestStage);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/cleanroomoptimizations/profiling/ModularMachineryOptimizations", "recordCacheManifest", "(Ljava/lang/String;)V", false);
            }
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
