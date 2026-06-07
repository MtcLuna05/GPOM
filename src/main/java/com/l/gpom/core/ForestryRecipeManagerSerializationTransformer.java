package com.l.gpom.core;

import com.l.gpom.GPOM;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ForestryRecipeManagerSerializationTransformer implements IClassTransformer {
    private static final String HELPER = "com/l/gpom/optimization/ForestryRecipeManagerOptimizations";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = normalize(transformedName != null ? transformedName : name);
        if (!isTargetRecipeManager(className)) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            int changed = 0;
            for (MethodNode method : node.methods) {
                changed += rewriteRecipeCollectionAccesses(method);
            }
            if (changed <= 0) {
                return basicClass;
            }

            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Serialized {} Forestry recipe collection access(es) in {}",
                    changed,
                    className
            );
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to install Forestry recipe serialization for {}", className, throwable);
            return basicClass;
        }
    }

    private static int rewriteRecipeCollectionAccesses(MethodNode method) {
        int changed = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) insn;
            if (rewriteMutation(call)) {
                changed++;
                continue;
            }
            if (rewriteSnapshotFactory(call)) {
                changed++;
                continue;
            }
            if (rewriteIteration(call)) {
                changed++;
            }
        }
        return changed;
    }

    private static boolean rewriteMutation(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEINTERFACE || !isRecipeCollectionOwner(call.owner)) {
            return false;
        }
        if ("add".equals(call.name) && "(Ljava/lang/Object;)Z".equals(call.desc)) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = "addRecipeCollection";
            call.desc = "(Ljava/util/Collection;Ljava/lang/Object;)Z";
            call.itf = false;
            return true;
        }
        if ("remove".equals(call.name) && "(Ljava/lang/Object;)Z".equals(call.desc)) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = "removeRecipeCollection";
            call.desc = "(Ljava/util/Collection;Ljava/lang/Object;)Z";
            call.itf = false;
            return true;
        }
        if ("clear".equals(call.name) && "()V".equals(call.desc)) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = "clearRecipeCollection";
            call.desc = "(Ljava/util/Collection;)V";
            call.itf = false;
            return true;
        }
        return false;
    }

    private static boolean rewriteSnapshotFactory(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKESTATIC || !"java/util/Collections".equals(call.owner)) {
            return false;
        }
        if ("unmodifiableSet".equals(call.name) && "(Ljava/util/Set;)Ljava/util/Set;".equals(call.desc)) {
            call.owner = HELPER;
            call.name = "immutableSetSnapshot";
            return true;
        }
        if ("unmodifiableCollection".equals(call.name)
                && "(Ljava/util/Collection;)Ljava/util/Collection;".equals(call.desc)) {
            call.owner = HELPER;
            call.name = "immutableCollectionSnapshot";
            return true;
        }
        return false;
    }

    private static boolean rewriteIteration(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEINTERFACE || !isRecipeCollectionOwner(call.owner)) {
            return false;
        }
        if ("iterator".equals(call.name) && "()Ljava/util/Iterator;".equals(call.desc)) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = "iteratorSnapshot";
            call.desc = "(Ljava/util/Collection;)Ljava/util/Iterator;";
            call.itf = false;
            return true;
        }
        if ("stream".equals(call.name) && "()Ljava/util/stream/Stream;".equals(call.desc)) {
            call.setOpcode(Opcodes.INVOKESTATIC);
            call.owner = HELPER;
            call.name = "streamSnapshot";
            call.desc = "(Ljava/util/Collection;)Ljava/util/stream/Stream;";
            call.itf = false;
            return true;
        }
        return false;
    }

    private static boolean isRecipeCollectionOwner(String owner) {
        return "java/util/Set".equals(owner) || "java/util/Collection".equals(owner);
    }

    private static boolean isTargetRecipeManager(String className) {
        return className != null
                && className.startsWith("forestry.factory.recipes.")
                && className.endsWith("RecipeManager")
                && TargetedModVersions.isForestryClass(className);
    }

    private static String normalize(String className) {
        return className == null ? null : className.replace('/', '.');
    }
}
