package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ForcedResourceReloadTransformer implements IClassTransformer {
    private static final boolean CITNBT_ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.citnbt.deferPreinitReload",
            "true"
    ));
    private static final boolean UCW_ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.ucw.deferPreinitReload",
            "true"
    ));
    private static final boolean AQUA_ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.aquaacrobatics.deferPreinitReload",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if ((className != null && className.startsWith("com.l.gpom."))) {
            return basicClass;
        }
        Target target = getTarget(className);
        if (target == null || !target.enabled || !target.supportedVersion) {
            return basicClass;
        }

        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (target.methodName.equals(method.name) && target.methodDesc.equals(method.desc)) {
                    changed |= removeImmediateReloadCall(method, target);
                }
            }
            if (changed) {
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            }
        } catch (Throwable ignored) {
        }

        return basicClass;
    }

    private static Target getTarget(String className) {
        if ("com.sabrepotato.citnbt.resources.ExternalResourcePack".equals(className)) {
            return new Target(
                    CITNBT_ENABLED,
                    "injectExternalResources",
                    "()V",
                    "net/minecraft/client/resources/SimpleReloadableResourceManager",
                    "func_110541_a",
                    "(Ljava/util/List;)V",
                    TargetedModVersions.isCitNbtClass(className)
            );
        }
        if ("pl.asie.ucw.UCWProxyClient".equals(className)) {
            return new Target(
                    UCW_ENABLED,
                    "preInit",
                    "()V",
                    "net/minecraftforge/fml/client/FMLClientHandler",
                    "refreshResources",
                    "(Ljava/util/function/Predicate;)V",
                    TargetedModVersions.isUnlimitedChiselWorksClass(className)
            );
        }
        if ("com.fuzs.aquaacrobatics.proxy.ClientProxy".equals(className)) {
            return new Target(
                    AQUA_ENABLED,
                    "onPreInit",
                    "(Lnet/minecraftforge/fml/common/event/FMLPreInitializationEvent;)V",
                    "net/minecraftforge/fml/client/FMLClientHandler",
                    "refreshResources",
                    "([Lnet/minecraftforge/client/resource/IResourceType;)V",
                    TargetedModVersions.isAquaAcrobaticsClass(className)
            );
        }
        return null;
    }

    private static boolean removeImmediateReloadCall(MethodNode method, Target target) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode methodInsn = (MethodInsnNode) insn;
            if (target.owner.equals(methodInsn.owner)
                    && target.name.equals(methodInsn.name)
                    && target.desc.equals(methodInsn.desc)) {
                // All targeted reload calls are receiver + one reference argument.
                method.instructions.set(methodInsn, new InsnNode(Opcodes.POP2));
                changed = true;
            }
        }
        return changed;
    }

    private static final class Target {
        private final boolean enabled;
        private final String methodName;
        private final String methodDesc;
        private final String owner;
        private final String name;
        private final String desc;
        private final boolean supportedVersion;

        private Target(boolean enabled, String methodName, String methodDesc, String owner, String name, String desc, boolean supportedVersion) {
            this.enabled = enabled;
            this.methodName = methodName;
            this.methodDesc = methodDesc;
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.supportedVersion = supportedVersion;
        }
    }
}
