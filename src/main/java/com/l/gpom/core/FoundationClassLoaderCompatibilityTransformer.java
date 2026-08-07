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

/** Avoids Foundation's repeated JarFile manifest lookup while defining classes. */
public final class FoundationClassLoaderCompatibilityTransformer implements IClassTransformer {
    private static final String TARGET = "top.outlands.foundation.boot.ActualClassLoader";
    private static final String JAR_FILE = "java/util/jar/JarFile";
    private static final String HELPER = "com/l/gpom/compat/foundation/FoundationClassLoaderCompat";
    private static final String MANIFEST_DESC = "()Ljava/util/jar/Manifest;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !GpomEarlyConfig.foundationCacheJarManifestsEnabled()) {
            return basicClass;
        }
        String className = transformedName != null ? transformedName : name;
        if (!TARGET.equals(className)) {
            return basicClass;
        }
        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);
            int replacements = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                     instruction = instruction.getNext()) {
                    if (!(instruction instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.getOpcode() == Opcodes.INVOKEVIRTUAL
                            && JAR_FILE.equals(call.owner)
                            && "getManifest".equals(call.name)
                            && MANIFEST_DESC.equals(call.desc)) {
                        call.setOpcode(Opcodes.INVOKESTATIC);
                        call.owner = HELPER;
                        call.name = "cachedManifest";
                        call.desc = "(Ljava/util/jar/JarFile;)Ljava/util/jar/Manifest;";
                        call.itf = false;
                        replacements++;
                    }
                }
            }
            if (replacements == 0) {
                return basicClass;
            }
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            GPOM.LOGGER.info("[GPOM Foundation] Cached {} ActualClassLoader JarFile manifest lookup(s)", replacements);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[GPOM Foundation] Could not patch ActualClassLoader manifest lookups", throwable);
            return basicClass;
        }
    }
}
