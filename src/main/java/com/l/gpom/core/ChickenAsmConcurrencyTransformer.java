package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.SynchronizedHashMap;
import com.l.gpom.util.SynchronizedHashSet;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;

public final class ChickenAsmConcurrencyTransformer implements IClassTransformer {
    private static final String CLASS_HIERARCHY_MANAGER = "codechicken.asm.ClassHierarchyManager";
    private static final String SUPER_CACHE = "codechicken.asm.ClassHierarchyManager$SuperCache";
    private static final String OBF_MAPPING = "codechicken.asm.ObfMapping";
    private static final String MULTIPART_REGISTRY = "codechicken.multipart.MultiPartRegistry$";
    private static final String HASH_MAP = "java/util/HashMap";
    private static final String HASH_SET = "java/util/HashSet";
    private static final String SYNC_HASH_MAP = "com/l/gpom/util/SynchronizedHashMap";
    private static final String SYNC_HASH_SET = "com/l/gpom/util/SynchronizedHashSet";
    private static volatile boolean runtimeCachesHardened;
    private static volatile boolean obfMappingPreloaded;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (CLASS_HIERARCHY_MANAGER.equals(className)) {
            return patchHierarchyManager(basicClass);
        }
        if (SUPER_CACHE.equals(className)) {
            return replaceCacheAllocation(basicClass, HASH_SET, SYNC_HASH_SET, "ChickenASM parent set");
        }
        if (MULTIPART_REGISTRY.equals(className)) {
            return synchronizeMultipartRegistry(basicClass);
        }
        return basicClass;
    }

    public static void hardenRuntimeCaches() {
        if (runtimeCachesHardened) {
            return;
        }
        synchronized (ChickenAsmConcurrencyTransformer.class) {
            if (runtimeCachesHardened) {
                return;
            }
            runtimeCachesHardened = true;
            try {
                Class<?> manager = Class.forName(CLASS_HIERARCHY_MANAGER, true, chickenAsmClassLoader());
                Field superclassesField = manager.getDeclaredField("superclasses");
                superclassesField.setAccessible(true);
                Object current = superclassesField.get(null);
                if (!(current instanceof SynchronizedHashMap)) {
                    SynchronizedHashMap<Object, Object> replacement = new SynchronizedHashMap<Object, Object>();
                    superclassesField.set(null, replacement);
                    current = replacement;
                    if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                        GPOM.LOGGER.info("[FmlParallelLoading] Hardened ChickenASM ClassHierarchyManager runtime cache");
                    }
                }
                int parentSets = hardenExistingSuperCaches(current);
                if (parentSets > 0 && GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.info("[FmlParallelLoading] Hardened {} existing ChickenASM parent cache(s)", parentSets);
                }
            } catch (Throwable throwable) {
                if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[FmlParallelLoading] Failed to harden ChickenASM runtime cache; continuing with existing cache", throwable);
                }
            }
        }
    }

    public static void preloadObfMapping() {
        if (obfMappingPreloaded) {
            return;
        }
        synchronized (ChickenAsmConcurrencyTransformer.class) {
            if (obfMappingPreloaded) {
                return;
            }
            try {
                Class.forName(OBF_MAPPING, true, chickenAsmClassLoader());
                obfMappingPreloaded = true;
                if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.info("[FmlParallelLoading] Preloaded ChickenASM ObfMapping on the main thread");
                }
            } catch (Throwable throwable) {
                if (GpomEarlyConfig.optimizationInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[FmlParallelLoading] Failed to preload ChickenASM ObfMapping; it may initialize lazily", throwable);
                }
            }
        }
    }

    private static int hardenExistingSuperCaches(Object caches) {
        if (!(caches instanceof Map)) {
            return 0;
        }
        Object[] snapshot;
        try {
            synchronized (caches) {
                snapshot = ((Map<?, ?>) caches).values().toArray();
            }
        } catch (Throwable ignored) {
            return 0;
        }
        Field parentsField = null;
        int hardened = 0;
        for (Object cache : snapshot) {
            if (cache == null) {
                continue;
            }
            try {
                if (parentsField == null) {
                    parentsField = cache.getClass().getDeclaredField("parents");
                    parentsField.setAccessible(true);
                }
                Object parents = parentsField.get(cache);
                if (parents instanceof SynchronizedHashSet || !(parents instanceof HashSet)) {
                    continue;
                }
                SynchronizedHashSet<Object> replacement = new SynchronizedHashSet<Object>();
                synchronized (parents) {
                    replacement.addAll((HashSet<?>) parents);
                }
                parentsField.set(cache, replacement);
                hardened++;
            } catch (Throwable ignored) {
                // Stale cache entries are disposable; future SuperCache allocations are patched by the transformer.
            }
        }
        return hardened;
    }

    private static ClassLoader chickenAsmClassLoader() {
        if (Launch.classLoader != null) {
            return Launch.classLoader;
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            return contextLoader;
        }
        return ChickenAsmConcurrencyTransformer.class.getClassLoader();
    }

    private static byte[] synchronizeMultipartRegistry(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);

            int replacements = 0;
            for (MethodNode method : node.methods) {
                if (isMultipartRegistrationMethod(method) && (method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    replacements++;
                }
            }

            if (replacements == 0) {
                return basicClass;
            }
            if (GpomEarlyConfig.fmlSchedulerLogsEnabled()) {
                GPOM.LOGGER.info("[FmlParallelLoading] Patched CodeChicken Multipart registry with {} synchronized registration method(s)", replacements);
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to patch CodeChicken Multipart registry; continuing with original registry", throwable);
            return basicClass;
        }
    }

    private static boolean isMultipartRegistrationMethod(MethodNode method) {
        if (method == null || (method.access & Opcodes.ACC_STATIC) != 0) {
            return false;
        }
        if ("registerParts".equals(method.name)) {
            return true;
        }
        return "registerConverter".equals(method.name) || "registerPlacementConverter".equals(method.name);
    }

    private static byte[] patchHierarchyManager(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);

            int cacheReplacements = 0;
            int synchronizedMethods = 0;
            for (MethodNode method : node.methods) {
                if (isHierarchyCacheMethod(method) && (method.access & Opcodes.ACC_SYNCHRONIZED) == 0) {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    synchronizedMethods++;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.NEW
                            && insn instanceof TypeInsnNode
                            && HASH_MAP.equals(((TypeInsnNode) insn).desc)) {
                        ((TypeInsnNode) insn).desc = SYNC_HASH_MAP;
                        cacheReplacements++;
                    } else if (insn.getOpcode() == Opcodes.INVOKESPECIAL
                            && insn instanceof MethodInsnNode
                            && HASH_MAP.equals(((MethodInsnNode) insn).owner)
                            && "<init>".equals(((MethodInsnNode) insn).name)
                            && "()V".equals(((MethodInsnNode) insn).desc)) {
                        ((MethodInsnNode) insn).owner = SYNC_HASH_MAP;
                        cacheReplacements++;
                    }
                }
            }

            if (cacheReplacements == 0 && synchronizedMethods == 0) {
                return basicClass;
            }
            GPOM.LOGGER.info(
                    "[FmlParallelLoading] Patched ChickenASM hierarchy manager with {} synchronized method(s) and {} synchronized cache allocation opcode(s)",
                    synchronizedMethods,
                    cacheReplacements
            );
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to patch ChickenASM hierarchy manager; continuing with original hierarchy cache", throwable);
            return basicClass;
        }
    }

    private static boolean isHierarchyCacheMethod(MethodNode method) {
        if (method == null || (method.access & Opcodes.ACC_STATIC) == 0) {
            return false;
        }
        return "classExtends".equals(method.name)
                || "declareClass".equals(method.name)
                || "declareReflection".equals(method.name)
                || "declareASM".equals(method.name)
                || "declare".equals(method.name)
                || "getOrCreateCache".equals(method.name)
                || "getSuperClass".equals(method.name);
    }

    private static byte[] replaceCacheAllocation(byte[] basicClass, String originalType, String replacementType, String label) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);

            int replacements = 0;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn.getOpcode() == Opcodes.NEW
                            && insn instanceof TypeInsnNode
                            && originalType.equals(((TypeInsnNode) insn).desc)) {
                        ((TypeInsnNode) insn).desc = replacementType;
                        replacements++;
                    } else if (insn.getOpcode() == Opcodes.INVOKESPECIAL
                            && insn instanceof MethodInsnNode
                            && originalType.equals(((MethodInsnNode) insn).owner)
                            && "<init>".equals(((MethodInsnNode) insn).name)
                            && "()V".equals(((MethodInsnNode) insn).desc)) {
                        ((MethodInsnNode) insn).owner = replacementType;
                        replacements++;
                    }
                }
            }

            if (replacements == 0) {
                return basicClass;
            }
            if (GpomEarlyConfig.fmlSchedulerLogsEnabled()) {
                GPOM.LOGGER.info("[FmlParallelLoading] Patched {} with {} synchronized cache allocation opcode(s)", label, replacements);
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[FmlParallelLoading] Failed to patch {}; continuing with original ChickenASM cache", label, throwable);
            return basicClass;
        }
    }
}
