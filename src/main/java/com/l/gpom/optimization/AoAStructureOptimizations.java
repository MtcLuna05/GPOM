package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.tslat.aoa3.advent.AdventOfAscension;
import net.tslat.aoa3.structure.AoAStructure;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public final class AoAStructureOptimizations {
    private static final boolean LAZY_STRUCTURES = Boolean.parseBoolean(System.getProperty("gpom.aoa3.lazyStructures", "true"));
    private static final ConcurrentHashMap<String, AoAStructure> DELEGATES = new ConcurrentHashMap<>();
    private static volatile boolean fallbackLogged;

    private AoAStructureOptimizations() {
    }

    public static void registerLazyStructures(String[] classNames) {
        AdventOfAscension.logOptionalMessage("Registering structures...");
        if (!LAZY_STRUCTURES || classNames == null || !TargetedModVersions.isAdventOfAscensionClass("net.tslat.aoa3.structure.StructuresHandler")) {
            registerEager(classNames);
            return;
        }

        long startedAt = StartupProfiler.beginProbe();
        try {
            for (String className : classNames) {
                if (className == null || className.isEmpty()) {
                    continue;
                }
                String structureName = readStructureName(className);
                new LazyStructure(structureName, className);
            }
            StartupProfiler.endProbeAlways("AOA StructuresHandler.registerStructures lazy manifest", startedAt);
        } catch (Throwable throwable) {
            StartupProfiler.endProbeAlways("AOA StructuresHandler.registerStructures lazy manifest", startedAt);
            if (!fallbackLogged) {
                fallbackLogged = true;
                GPOM.LOGGER.warn("AoA3 lazy structure registration failed; falling back to eager structure construction", throwable);
            }
            registerEager(classNames);
        }
    }

    private static void registerEager(String[] classNames) {
        if (classNames == null) {
            return;
        }
        long startedAt = StartupProfiler.beginProbe();
        for (String className : classNames) {
            instantiate(className);
        }
        StartupProfiler.endProbeAlways("AOA StructuresHandler.registerStructures eager fallback", startedAt);
    }

    private static String readStructureName(String className) throws Exception {
        ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : AoAStructureOptimizations.class.getClassLoader();
        String resourceName = className.replace('.', '/') + ".class";
        try (InputStream input = loader.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new ClassNotFoundException(className);
            }
            ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name)) {
                    continue;
                }
                String pendingName = null;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                        pendingName = (String) ((LdcInsnNode) insn).cst;
                    } else if (insn instanceof MethodInsnNode) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if (methodInsn.getOpcode() == Opcodes.INVOKESPECIAL
                                && "net/tslat/aoa3/structure/AoAStructure".equals(methodInsn.owner)
                                && "<init>".equals(methodInsn.name)
                                && "(Ljava/lang/String;)V".equals(methodInsn.desc)
                                && pendingName != null) {
                            return pendingName;
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("Unable to read AoA3 structure name from " + className);
    }

    private static AoAStructure instantiate(String className) {
        try {
            ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : AoAStructureOptimizations.class.getClassLoader();
            Class<?> type = Class.forName(className, true, loader);
            if (!TargetedModVersions.isAdventOfAscensionClass(type)) {
                throw new ClassNotFoundException("Unsupported AoA3 structure source: " + className);
            }
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (AoAStructure) constructor.newInstance();
        } catch (Throwable throwable) {
            throw new RuntimeException("Unable to instantiate AoA3 structure " + className, throwable);
        }
    }

    private static final class LazyStructure extends AoAStructure {
        private final String className;

        private LazyStructure(String name, String className) {
            super(name);
            this.className = className;
        }

        @Override
        public boolean generate(World world, Random random, BlockPos position) {
            return delegate().generate(world, random, position);
        }

        @Override
        protected void build(World world, Random random, BlockPos position) {
            delegate().generate(world, random, position);
        }

        private AoAStructure delegate() {
            AoAStructure existing = DELEGATES.get(className);
            if (existing != null) {
                return existing;
            }
            synchronized (DELEGATES) {
                existing = DELEGATES.get(className);
                if (existing != null) {
                    return existing;
                }
                long startedAt = StartupProfiler.beginProbe();
                AoAStructure created = instantiate(className);
                DELEGATES.put(className, created);
                StartupProfiler.endProbeAlways("AOA lazy structure instantiate " + className, startedAt);
                return created;
            }
        }
    }
}
