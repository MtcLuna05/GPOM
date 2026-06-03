package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BetweenlandsStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.betweenlandsProfiler", "true"));
    private static final boolean FAST_ENTITY_REGISTRATION = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.fastEntityRegistration", "true"));
    private static final boolean DIRECT_BLOCK_REGISTRY_PREINIT = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.directBlockRegistryPreInit", "true"));
    private static final boolean LAZY_CLIENT_ENTITY_RENDERERS = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.lazyClientEntityRenderers", "true"));
    private static final boolean LAZY_CLIENT_TILE_RENDERERS = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.lazyClientTileRenderers", "true"));
    private static final boolean DEFER_PARTICLE_STITCHERS = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.deferParticleStitchers", "true"));
    private static final boolean BLOCK_FIELD_ACCESS_PROFILER = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.blockFieldAccessProfiler", "false"));
    private static final boolean LAZY_BLOCK_FIELD_ACCESSORS = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.lazyBlockFieldAccessors", "true"));
    private static final boolean LAZY_BLOCK_CONSTRUCTION = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.lazyBlockConstruction", "true"));
    private static final boolean BLOCK_CONSTRUCTOR_PROFILER = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.blockConstructorProfiler", "false"));
    private static final Map<String, Set<MethodKey>> TARGETS = createTargets();
    private static final Set<String> EAGER_BLOCK_FIELDS = createEagerBlockFields();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null || (className != null && className.startsWith("com.l.gpom.")) || !TargetedModVersions.isBetweenlandsClass(className)) {
            return basicClass;
        }

        if ("thebetweenlands.common.registries.EntityRegistry".equals(className) && FAST_ENTITY_REGISTRATION) {
            basicClass = patchEntityRegistry(basicClass);
        }
        if ("thebetweenlands.common.registries.BlockRegistry".equals(className)
                && (DIRECT_BLOCK_REGISTRY_PREINIT || LAZY_BLOCK_CONSTRUCTION)) {
            basicClass = patchBlockRegistry(basicClass);
        }
        if ("thebetweenlands.client.proxy.ClientProxy".equals(className)
                && (LAZY_CLIENT_ENTITY_RENDERERS || LAZY_CLIENT_TILE_RENDERERS || DEFER_PARTICLE_STITCHERS)) {
            basicClass = patchClientProxyRenderers(basicClass);
        }

        Set<MethodKey> methods = TARGETS.get(className);
        boolean profileBlockConstructors = BLOCK_CONSTRUCTOR_PROFILER
                && className != null
                && className.startsWith("thebetweenlands.common.block.");
        boolean profileBlockFieldAccess = BLOCK_FIELD_ACCESS_PROFILER
                && className != null
                && !"thebetweenlands.common.registries.BlockRegistry".equals(className)
                && className.startsWith("thebetweenlands.");
        if (methods == null && !profileBlockConstructors && !profileBlockFieldAccess) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new BetweenlandsClassVisitor(writer, className, methods, profileBlockConstructors, profileBlockFieldAccess), 0);
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

        add(targets, "thebetweenlands.common.registries.CapabilityRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.CapabilityRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.StorageRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.StorageRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.CustomRecipeRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.CustomRecipeRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.AdvancementCriterionRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.AdvancementCriterionRegistry", "preInit", "()V");

        add(targets, "thebetweenlands.common.registries.LootTableRegistry", "<clinit>", "()V");
        add(targets, "thebetweenlands.common.registries.LootTableRegistry", "preInit", "()V");

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
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler", "onTextureStitchPre", "(Lnet/minecraftforge/client/event/TextureStitchEvent$Pre;)V");
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler$TextureStitcher", "<init>", "([Lnet/minecraft/util/ResourceLocation;)V");
        add(targets, "thebetweenlands.client.handler.TextureStitchHandler$TextureStitcher", "<init>", "(Ljava/util/function/Consumer;[Lnet/minecraft/util/ResourceLocation;)V");

        return targets;
    }

    private static Set<String> createEagerBlockFields() {
        Set<String> fields = new HashSet<>();
        fields.add("THATCH");
        fields.add("MUD_BRICKS");
        fields.add("SILT_GLASS_JAR");
        fields.add("FISHING_TACKLE_BOX");
        fields.add("ROOT");
        fields.add("MUD_BRICK_SHINGLES");
        fields.add("COMPACTED_MUD");
        fields.add("GLOWING_GOOP");
        return fields;
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

    private static byte[] patchClientProxyRenderers(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            MethodNode preInit = null;
            for (MethodNode method : node.methods) {
                if ("preInit".equals(method.name) && "()V".equals(method.desc)) {
                    preInit = method;
                    break;
                }
            }
            if (preInit == null) {
                return basicClass;
            }

            if (LAZY_CLIENT_ENTITY_RENDERERS) {
                AbstractInsnNode start = null;
                AbstractInsnNode firstTileRenderer = null;
                int renderRegistrations = 0;
                for (AbstractInsnNode insn = preInit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (start == null && insn.getOpcode() >= 0) {
                        start = insn;
                    }
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode methodInsn = (MethodInsnNode) insn;
                        if (methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                                && "net/minecraftforge/fml/client/registry/RenderingRegistry".equals(methodInsn.owner)
                                && "registerEntityRenderingHandler".equals(methodInsn.name)
                                && "(Ljava/lang/Class;Lnet/minecraftforge/fml/client/registry/IRenderFactory;)V".equals(methodInsn.desc)) {
                            renderRegistrations++;
                        }
                        if (isTileRendererBind(methodInsn)) {
                            firstTileRenderer = findRegistrationStart(insn);
                            break;
                        }
                    }
                }

                if (start != null && firstTileRenderer != null && renderRegistrations == 121) {
                    InsnList replacement = new InsnList();
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/BetweenlandsClientRendererOptimizations",
                            "registerEntityRenderers",
                            "()V",
                            false
                    ));
                    preInit.instructions.insertBefore(start, replacement);

                    AbstractInsnNode current = start;
                    while (current != null && current != firstTileRenderer) {
                        AbstractInsnNode next = current.getNext();
                        preInit.instructions.remove(current);
                        current = next;
                    }
                }
            }

            if (LAZY_CLIENT_TILE_RENDERERS) {
                List<TileRendererRegistration> tileRenderers = collectTileRendererRegistrations(preInit);
                if (tileRenderers.size() == 49) {
                    InsnList replacement = new InsnList();
                    for (TileRendererRegistration registration : tileRenderers) {
                        replacement.add(new LdcInsnNode(registration.tileClassName));
                        replacement.add(new LdcInsnNode(registration.rendererClassName));
                        replacement.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/BetweenlandsClientRendererOptimizations",
                                "registerTileEntityRenderer",
                                "(Ljava/lang/String;Ljava/lang/String;)V",
                                false
                        ));
                    }

                    AbstractInsnNode first = tileRenderers.get(0).start;
                    AbstractInsnNode end = tileRenderers.get(tileRenderers.size() - 1).endExclusive;
                    preInit.instructions.insertBefore(first, replacement);

                    AbstractInsnNode current = first;
                    while (current != null && current != end) {
                        AbstractInsnNode next = current.getNext();
                        preInit.instructions.remove(current);
                        current = next;
                    }
                }
            }

            if (DEFER_PARTICLE_STITCHERS) {
                patchClientProxyParticleStitchers(preInit);
            }

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static void patchClientProxyParticleStitchers(MethodNode preInit) {
        AbstractInsnNode start = null;
        AbstractInsnNode end = null;
        for (AbstractInsnNode insn = preInit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if (start == null
                        && methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                        && "thebetweenlands/client/render/particle/BLParticles".equals(methodInsn.owner)
                        && "values".equals(methodInsn.name)
                        && "()[Lthebetweenlands/client/render/particle/BLParticles;".equals(methodInsn.desc)) {
                    start = insn;
                }
                if (start != null
                        && methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                        && "thebetweenlands/client/proxy/ClientProxy".equals(methodInsn.owner)
                        && "registerEventHandlersPreInit".equals(methodInsn.name)
                        && "()V".equals(methodInsn.desc)) {
                    end = insn;
                    break;
                }
            }
        }
        if (start == null || end == null) {
            return;
        }

        InsnList replacement = new InsnList();
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                "com/l/gpom/optimization/BetweenlandsParticleOptimizations",
                "deferParticleStitchers",
                "()V",
                false
        ));
        preInit.instructions.insertBefore(start, replacement);
        AbstractInsnNode current = start;
        while (current != null && current != end) {
            AbstractInsnNode next = current.getNext();
            preInit.instructions.remove(current);
            current = next;
        }
        preInit.instructions.insertBefore(end, new VarInsnNode(Opcodes.ALOAD, 0));
    }

    private static boolean isTileRendererBind(MethodInsnNode methodInsn) {
        return methodInsn.getOpcode() == Opcodes.INVOKESTATIC
                && "net/minecraftforge/fml/client/registry/ClientRegistry".equals(methodInsn.owner)
                && "bindTileEntitySpecialRenderer".equals(methodInsn.name)
                && "(Ljava/lang/Class;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V".equals(methodInsn.desc);
    }

    private static List<TileRendererRegistration> collectTileRendererRegistrations(MethodNode preInit) {
        List<TileRendererRegistration> registrations = new ArrayList<>();
        for (AbstractInsnNode insn = preInit.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode) || !isTileRendererBind((MethodInsnNode) insn)) {
                continue;
            }
            TileRendererRegistration registration = readTileRendererRegistration((MethodInsnNode) insn);
            if (registration == null) {
                return Collections.emptyList();
            }
            registrations.add(registration);
        }
        return registrations;
    }

    private static TileRendererRegistration readTileRendererRegistration(MethodInsnNode bindCall) {
        AbstractInsnNode start = findRegistrationStart(bindCall);
        if (start == null) {
            return null;
        }

        String tileClassName = null;
        String rendererInternalName = null;
        boolean noArgConstructor = false;
        for (AbstractInsnNode current = start; current != null; current = current.getNext()) {
            if (current instanceof LdcInsnNode) {
                Object value = ((LdcInsnNode) current).cst;
                if (value instanceof Type) {
                    String internalName = ((Type) value).getInternalName();
                    if (internalName != null && internalName.startsWith("thebetweenlands/common/tile/")) {
                        tileClassName = dotClassName(internalName);
                    }
                }
            } else if (current instanceof TypeInsnNode && current.getOpcode() == Opcodes.NEW) {
                String internalName = ((TypeInsnNode) current).desc;
                if (internalName != null && internalName.startsWith("thebetweenlands/client/render/tile/")) {
                    rendererInternalName = internalName;
                }
            } else if (current instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) current;
                if (methodInsn.getOpcode() == Opcodes.INVOKESPECIAL
                        && "<init>".equals(methodInsn.name)
                        && "()V".equals(methodInsn.desc)
                        && rendererInternalName != null
                        && rendererInternalName.equals(methodInsn.owner)) {
                    noArgConstructor = true;
                }
            }
            if (current == bindCall) {
                break;
            }
        }

        if (tileClassName == null || rendererInternalName == null || !noArgConstructor) {
            return null;
        }
        return new TileRendererRegistration(start, bindCall.getNext(), tileClassName, dotClassName(rendererInternalName));
    }

    private static org.objectweb.asm.tree.AbstractInsnNode findRegistrationStart(org.objectweb.asm.tree.AbstractInsnNode registrationCall) {
        for (org.objectweb.asm.tree.AbstractInsnNode current = registrationCall; current != null; current = current.getPrevious()) {
            if (current instanceof org.objectweb.asm.tree.LdcInsnNode) {
                Object value = ((org.objectweb.asm.tree.LdcInsnNode) current).cst;
                if (value instanceof Type) {
                    String internalName = ((Type) value).getInternalName();
                    if (internalName != null && internalName.startsWith("thebetweenlands/common/tile/")) {
                        return current;
                    }
                }
            }
        }
        return registrationCall;
    }

    private static byte[] patchBlockRegistry(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);

            Set<String> blockFields = new HashSet<>();
            for (FieldNode field : node.fields) {
                if (isExactBlockField(field)) {
                    blockFields.add(field.name);
                }
            }

            Set<String> generatedLazyFields = LAZY_BLOCK_CONSTRUCTION
                    ? patchBlockRegistryClinitForLazyConstruction(node, blockFields)
                    : Collections.emptySet();

            MethodNode preInit = null;
            for (MethodNode method : node.methods) {
                if ("preInit".equals(method.name) && "()V".equals(method.desc)) {
                    preInit = method;
                    break;
                }
            }
            if (preInit == null) {
                return basicClass;
            }

            InsnList instructions = new InsnList();
            instructions.add(new LdcInsnNode(countRegistrableBlockFields(node)));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/BetweenlandsOptimizations",
                    "beginBlockRegistryDirectPreInit",
                    "(I)V",
                    false
            ));
            for (FieldNode field : node.fields) {
                if (!isRegistrableBlockField(field)) {
                    continue;
                }
                String registryName = field.name.toLowerCase(Locale.ENGLISH);
                instructions.add(new LdcInsnNode(registryName));
                instructions.add(new LdcInsnNode(field.name));
                instructions.add(new LdcInsnNode(registryName));
                if (generatedLazyFields.contains(field.name)) {
                    String[] labels = lazyBlockMaterializationLabels(field.name);
                    addBeginProbes(instructions, labels);
                    instructions.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "thebetweenlands/common/registries/BlockRegistry",
                            lazyBlockMethodName(field.name),
                            "()Lnet/minecraft/block/Block;",
                            false
                    ));
                    addEndProbes(instructions, labels);
                } else {
                    instructions.add(new FieldInsnNode(
                            Opcodes.GETSTATIC,
                            "thebetweenlands/common/registries/BlockRegistry",
                            field.name,
                            field.desc
                    ));
                }
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/BetweenlandsOptimizations",
                        "recordBlockRegistryDirectPreInit",
                        "(Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/block/Block;)Lnet/minecraft/block/Block;",
                        false
                ));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "thebetweenlands/common/registries/BlockRegistry",
                        "registerBlock",
                        "(Ljava/lang/String;Lnet/minecraft/block/Block;)V",
                        false
                ));
            }
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/BetweenlandsOptimizations",
                    "logBlockRegistryDirectPreInitSummary",
                    "()V",
                    false
            ));
            instructions.add(new InsnNode(Opcodes.RETURN));

            preInit.instructions = instructions;
            preInit.tryCatchBlocks.clear();
            preInit.localVariables.clear();
            preInit.maxLocals = 0;
            preInit.maxStack = 4;

            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static boolean isExactBlockField(FieldNode field) {
        return (field.access & Opcodes.ACC_STATIC) != 0
                && "Lnet/minecraft/block/Block;".equals(field.desc);
    }

    private static boolean isRegistrableBlockField(FieldNode field) {
        return (field.access & Opcodes.ACC_STATIC) != 0
                && ("Lnet/minecraft/block/Block;".equals(field.desc)
                || (field.desc != null && field.desc.startsWith("Lthebetweenlands/common/block/")));
    }

    private static int countRegistrableBlockFields(ClassNode node) {
        int count = 0;
        for (FieldNode field : node.fields) {
            if (isRegistrableBlockField(field)) {
                count++;
            }
        }
        return count;
    }

    private static Set<String> patchBlockRegistryClinitForLazyConstruction(ClassNode node, Set<String> blockFields) {
        MethodNode clinit = null;
        for (MethodNode method : node.methods) {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
                clinit = method;
                break;
            }
        }
        if (clinit == null) {
            return Collections.emptySet();
        }

        Set<String> lazyFields = new HashSet<>(blockFields);
        lazyFields.removeAll(EAGER_BLOCK_FIELDS);
        Set<String> generatedFields = new HashSet<>();
        for (FieldNode field : node.fields) {
            if (lazyFields.contains(field.name)) {
                field.access &= ~Opcodes.ACC_FINAL;
            }
        }

        for (AbstractInsnNode current = clinit.instructions.getFirst(); current != null;) {
            AbstractInsnNode nextCurrent = current.getNext();
            if (!(current instanceof FieldInsnNode)) {
                current = nextCurrent;
                continue;
            }
            FieldInsnNode putstatic = (FieldInsnNode) current;
            if (putstatic.getOpcode() != Opcodes.PUTSTATIC
                    || !"thebetweenlands/common/registries/BlockRegistry".equals(putstatic.owner)
                    || !"Lnet/minecraft/block/Block;".equals(putstatic.desc)
                    || !lazyFields.contains(putstatic.name)) {
                current = nextCurrent;
                continue;
            }

            AbstractInsnNode start = findBlockInitializerStart(putstatic);
            if (start == null) {
                current = nextCurrent;
                continue;
            }

            MethodNode lazyMethod = createLazyBlockMethod(putstatic.name, start, putstatic, lazyFields);
            node.methods.add(lazyMethod);
            generatedFields.add(putstatic.name);

            InsnList replacement = new InsnList();
            replacement.add(new InsnNode(Opcodes.ACONST_NULL));
            replacement.add(new FieldInsnNode(
                    Opcodes.PUTSTATIC,
                    "thebetweenlands/common/registries/BlockRegistry",
                    putstatic.name,
                    "Lnet/minecraft/block/Block;"
            ));
            AbstractInsnNode replacementEnd = replacement.getLast();
            clinit.instructions.insertBefore(start, replacement);

            AbstractInsnNode next = putstatic.getNext();
            for (AbstractInsnNode remove = start; remove != next;) {
                AbstractInsnNode removeNext = remove.getNext();
                clinit.instructions.remove(remove);
                remove = removeNext;
            }
            current = replacementEnd.getNext();
        }

        rewriteClinitLazyBlockReads(clinit, generatedFields);
        return generatedFields;
    }

    private static void rewriteClinitLazyBlockReads(MethodNode clinit, Set<String> generatedFields) {
        if (generatedFields.isEmpty()) {
            return;
        }
        for (AbstractInsnNode current = clinit.instructions.getFirst(); current != null;) {
            AbstractInsnNode next = current.getNext();
            if (!(current instanceof FieldInsnNode)) {
                current = next;
                continue;
            }
            FieldInsnNode fieldInsn = (FieldInsnNode) current;
            if (fieldInsn.getOpcode() == Opcodes.GETSTATIC
                    && "thebetweenlands/common/registries/BlockRegistry".equals(fieldInsn.owner)
                    && "Lnet/minecraft/block/Block;".equals(fieldInsn.desc)
                    && generatedFields.contains(fieldInsn.name)) {
                clinit.instructions.set(current, new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "thebetweenlands/common/registries/BlockRegistry",
                        lazyBlockMethodName(fieldInsn.name),
                        "()Lnet/minecraft/block/Block;",
                        false
                ));
            }
            current = next;
        }
    }

    private static AbstractInsnNode findBlockInitializerStart(AbstractInsnNode putstatic) {
        for (AbstractInsnNode current = putstatic.getPrevious(); current != null; current = current.getPrevious()) {
            if (current instanceof FieldInsnNode && current.getOpcode() == Opcodes.PUTSTATIC) {
                return null;
            }
            if (current instanceof TypeInsnNode
                    && current.getOpcode() == Opcodes.NEW
                    && ((TypeInsnNode) current).desc != null
                    && ((TypeInsnNode) current).desc.startsWith("thebetweenlands/common/block/")) {
                return current;
            }
        }
        return null;
    }

    private static MethodNode createLazyBlockMethod(String fieldName, AbstractInsnNode start, FieldInsnNode putstatic, Set<String> lazyFields) {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                lazyBlockMethodName(fieldName),
                "()Lnet/minecraft/block/Block;",
                null,
                null
        );
        LabelNode construct = new LabelNode();
        method.instructions.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                "thebetweenlands/common/registries/BlockRegistry",
                fieldName,
                "Lnet/minecraft/block/Block;"
        ));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNULL, construct));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.instructions.add(construct);
        method.instructions.add(new FrameNode(
                Opcodes.F_SAME1,
                0,
                null,
                1,
                new Object[] {"net/minecraft/block/Block"}
        ));
        method.instructions.add(new InsnNode(Opcodes.POP));
        method.instructions.add(cloneBlockInitializer(start, putstatic, fieldName, lazyFields));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new FieldInsnNode(
                Opcodes.PUTSTATIC,
                "thebetweenlands/common/registries/BlockRegistry",
                fieldName,
                "Lnet/minecraft/block/Block;"
        ));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxLocals = 0;
        method.maxStack = 6;
        return method;
    }

    private static InsnList cloneBlockInitializer(AbstractInsnNode start, FieldInsnNode putstatic, String fieldName, Set<String> lazyFields) {
        Map<LabelNode, LabelNode> labels = new HashMap<>();
        for (AbstractInsnNode current = start; current != putstatic; current = current.getNext()) {
            if (current instanceof LabelNode) {
                labels.put((LabelNode) current, new LabelNode());
            }
        }

        InsnList cloned = new InsnList();
        for (AbstractInsnNode current = start; current != putstatic; current = current.getNext()) {
            AbstractInsnNode copy = current.clone(labels);
            if (copy instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) copy;
                if (fieldInsn.getOpcode() == Opcodes.GETSTATIC
                        && "thebetweenlands/common/registries/BlockRegistry".equals(fieldInsn.owner)
                        && "Lnet/minecraft/block/Block;".equals(fieldInsn.desc)
                        && lazyFields.contains(fieldInsn.name)
                        && !fieldName.equals(fieldInsn.name)) {
                    copy = new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "thebetweenlands/common/registries/BlockRegistry",
                            lazyBlockMethodName(fieldInsn.name),
                            "()Lnet/minecraft/block/Block;",
                            false
                    );
                }
            }
            cloned.add(copy);
        }
        return cloned;
    }

    private static String lazyBlockMethodName(String fieldName) {
        return "gpom$lazyBlock$" + fieldName;
    }

    private static final class TileRendererRegistration {
        private final AbstractInsnNode start;
        private final AbstractInsnNode endExclusive;
        private final String tileClassName;
        private final String rendererClassName;

        private TileRendererRegistration(AbstractInsnNode start, AbstractInsnNode endExclusive, String tileClassName, String rendererClassName) {
            this.start = start;
            this.endExclusive = endExclusive;
            this.tileClassName = tileClassName;
            this.rendererClassName = rendererClassName;
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
                "com/l/gpom/optimization/BetweenlandsOptimizations",
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
                "com/l/gpom/optimization/BetweenlandsOptimizations",
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

    private static void beginProbe(MethodVisitor visitor, String label) {
        visitor.visitLdcInsn(label);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
    }

    private static void endProbe(MethodVisitor visitor, String label) {
        visitor.visitLdcInsn(label);
        visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
    }

    private static void addBeginProbes(InsnList instructions, String[] labels) {
        if (labels == null) {
            return;
        }
        for (String label : labels) {
            instructions.add(new LdcInsnNode(label));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/profiling/StartupProfiler",
                    "beginNamedProbe",
                    "(Ljava/lang/String;)V",
                    false
            ));
        }
    }

    private static void addEndProbes(InsnList instructions, String[] labels) {
        if (labels == null) {
            return;
        }
        for (int index = labels.length - 1; index >= 0; index--) {
            instructions.add(new LdcInsnNode(labels[index]));
            instructions.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/profiling/StartupProfiler",
                    "endNamedProbe",
                    "(Ljava/lang/String;)V",
                    false
            ));
        }
    }

    private static void beginProbes(MethodVisitor visitor, String[] labels) {
        if (labels == null) {
            return;
        }
        for (String label : labels) {
            beginProbe(visitor, label);
        }
    }

    private static void endProbes(MethodVisitor visitor, String[] labels) {
        if (labels == null) {
            return;
        }
        for (int index = labels.length - 1; index >= 0; index--) {
            endProbe(visitor, labels[index]);
        }
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

    private static String dotClassName(String internalName) {
        return internalName.replace('/', '.');
    }

    private static String bucketAfterPrefix(String internalName, String prefix) {
        String rest = internalName.substring(prefix.length());
        int slash = rest.indexOf('/');
        return slash < 0 ? "root" : rest.substring(0, slash).replace('/', '.');
    }

    private static String[] blockInstantiationLabels(String internalName) {
        return new String[] {
                "BL BlockRegistry.<clinit> block instantiation all",
                "BL BlockRegistry.<clinit> block package " + bucketAfterPrefix(internalName, "thebetweenlands/common/block/"),
                "BL BlockRegistry.<clinit> block class " + dotClassName(internalName)
        };
    }

    private static String[] lazyBlockMaterializationLabels(String fieldName) {
        return new String[] {
                "BL BlockRegistry.preInit lazy block materialization all",
                "BL BlockRegistry.preInit lazy block materialization field " + fieldName
        };
    }

    private static String[] itemInstantiationLabels(String internalName) {
        return new String[] {
                "BL ItemRegistry.<clinit> item instantiation all",
                "BL ItemRegistry.<clinit> item package " + bucketAfterPrefix(internalName, "thebetweenlands/common/item/"),
                "BL ItemRegistry.<clinit> item class " + dotClassName(internalName)
        };
    }

    private static String[] resourceLocationLabels(String className) {
        return new String[] {
                "BL registry ResourceLocation.<init> all",
                "BL registry ResourceLocation.<init> in " + className
        };
    }

    private static String[] entityRegistrationLabels(String internalName) {
        return new String[] {
                "BL EntityRegistry.preInit entity registration all",
                "BL EntityRegistry.preInit entity package " + bucketAfterPrefix(internalName, "thebetweenlands/common/entity/"),
                "BL EntityRegistry.preInit entity class " + dotClassName(internalName)
        };
    }

    private static String[] renderRegistrationLabels(String internalName) {
        return new String[] {
                "BL ClientProxy.preInit render handler all",
                "BL ClientProxy.preInit render handler package " + bucketAfterPrefix(internalName, "thebetweenlands/common/entity/"),
                "BL ClientProxy.preInit render handler class " + dotClassName(internalName)
        };
    }

    private static String[] tileRendererRegistrationLabels(String internalName) {
        return new String[] {
                "BL ClientProxy.preInit tile renderer all",
                "BL ClientProxy.preInit tile renderer package " + bucketAfterPrefix(internalName, "thebetweenlands/common/tile/"),
                "BL ClientProxy.preInit tile renderer class " + dotClassName(internalName)
        };
    }

    private static final class BetweenlandsClassVisitor extends ClassVisitor {
        private final String className;
        private final Set<MethodKey> methods;
        private final boolean profileBlockConstructors;
        private final boolean profileBlockFieldAccess;

        private BetweenlandsClassVisitor(ClassVisitor delegate, String className, Set<MethodKey> methods, boolean profileBlockConstructors, boolean profileBlockFieldAccess) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
            this.methods = methods;
            this.profileBlockConstructors = profileBlockConstructors;
            this.profileBlockFieldAccess = profileBlockFieldAccess;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null) {
                return visitor;
            }
            if (methods != null && methods.contains(new MethodKey(name, desc))) {
                visitor = new TimedMethodVisitor(visitor, "BL " + className + '.' + name);
            }
            if (profileBlockConstructors && ("<init>".equals(name) || "<clinit>".equals(name))) {
                visitor = new TimedMethodVisitor(visitor, "BL block " + className + '.' + name);
            }
            if ("thebetweenlands.common.registries.Registries".equals(className)
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                visitor = new RegistryPreInitCallVisitor(visitor);
            }
            if ("thebetweenlands.common.registries.BlockRegistry".equals(className)
                    && "<clinit>".equals(name)
                    && "()V".equals(desc)) {
                visitor = new BlockRegistryClinitVisitor(visitor);
            }
            if ("thebetweenlands.common.registries.ItemRegistry".equals(className)
                    && "<clinit>".equals(name)
                    && "()V".equals(desc)) {
                visitor = new ItemRegistryClinitVisitor(visitor);
            }
            if ("thebetweenlands.common.registries.ModelRegistry".equals(className)
                    && "<clinit>".equals(name)
                    && "()V".equals(desc)) {
                visitor = new ModelRegistryClinitVisitor(visitor);
            }
            if (className != null
                    && className.startsWith("thebetweenlands.common.registries.")
                    && ("<clinit>".equals(name) || "preInit".equals(name))) {
                visitor = new RegistryResourceLocationVisitor(visitor, className);
            }
            if ("thebetweenlands.common.registries.BlockRegistry".equals(className)
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                visitor = new BlockRegistryPreInitVisitor(visitor);
            }
            if ("thebetweenlands.common.registries.ModelRegistry".equals(className)
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                visitor = new ModelRegistryPreInitVisitor(visitor);
            }
            if ("thebetweenlands.common.registries.EntityRegistry".equals(className)
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                visitor = new EntityRegistryPreInitVisitor(visitor);
            }
            if ("thebetweenlands.client.proxy.ClientProxy".equals(className)
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                visitor = new ClientProxyPreInitVisitor(visitor);
            }
            if (DEFER_PARTICLE_STITCHERS
                    && "thebetweenlands.client.handler.TextureStitchHandler".equals(className)
                    && "onTextureStitchPre".equals(name)
                    && "(Lnet/minecraftforge/client/event/TextureStitchEvent$Pre;)V".equals(desc)) {
                visitor = new TextureStitchPreVisitor(visitor);
            }
            if (profileBlockFieldAccess) {
                visitor = new BlockRegistryFieldAccessVisitor(visitor, className + '.' + name);
            }
            return visitor;
        }
    }

    private static final class TextureStitchPreVisitor extends MethodVisitor {
        private TextureStitchPreVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/BetweenlandsParticleOptimizations",
                    "ensureParticleStitchersRegistered",
                    "()V",
                    false
            );
        }
    }

    private static final class BlockRegistryFieldAccessVisitor extends MethodVisitor {
        private final String context;

        private BlockRegistryFieldAccessVisitor(MethodVisitor delegate, String context) {
            super(Opcodes.ASM9, delegate);
            this.context = context;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETSTATIC
                    && "thebetweenlands/common/registries/BlockRegistry".equals(owner)
                    && "Lnet/minecraft/block/Block;".equals(desc)) {
                mv.visitLdcInsn(context);
                mv.visitLdcInsn(name);
                mv.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/BetweenlandsOptimizations",
                        "recordBlockRegistryFieldAccess",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        false
                );
                if (LAZY_BLOCK_FIELD_ACCESSORS) {
                    mv.visitLdcInsn(name);
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/BetweenlandsOptimizations",
                            "lazyBlockField",
                            "(Ljava/lang/String;)Lnet/minecraft/block/Block;",
                            false
                    );
                    return;
                }
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }
    }

    private static final class BlockRegistryClinitVisitor extends MethodVisitor {
        private String activeBlockType;
        private String[] activeLabels;

        private BlockRegistryClinitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && type != null && type.startsWith("thebetweenlands/common/block/")) {
                closeActiveBlockInstantiation();
                activeBlockType = type;
                activeLabels = blockInstantiationLabels(type);
                beginProbes(mv, activeLabels);
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (activeBlockType != null
                    && opcode == Opcodes.INVOKESPECIAL
                    && activeBlockType.equals(owner)
                    && "<init>".equals(name)) {
                closeActiveBlockInstantiation();
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (isExit(opcode)) {
                closeActiveBlockInstantiation();
            }
            super.visitInsn(opcode);
        }

        private void closeActiveBlockInstantiation() {
            if (activeLabels == null) {
                return;
            }
            endProbes(mv, activeLabels);
            activeBlockType = null;
            activeLabels = null;
        }
    }

    private static final class ItemRegistryClinitVisitor extends MethodVisitor {
        private String activeItemType;
        private String[] activeLabels;

        private ItemRegistryClinitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && type != null && type.startsWith("thebetweenlands/common/item/")) {
                closeActiveItemInstantiation();
                activeItemType = type;
                activeLabels = itemInstantiationLabels(type);
                beginProbes(mv, activeLabels);
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (activeItemType != null
                    && opcode == Opcodes.INVOKESPECIAL
                    && activeItemType.equals(owner)
                    && "<init>".equals(name)) {
                closeActiveItemInstantiation();
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (isExit(opcode)) {
                closeActiveItemInstantiation();
            }
            super.visitInsn(opcode);
        }

        private void closeActiveItemInstantiation() {
            if (activeLabels == null) {
                return;
            }
            endProbes(mv, activeLabels);
            activeItemType = null;
            activeLabels = null;
        }
    }

    private static final class RegistryResourceLocationVisitor extends MethodVisitor {
        private final String className;
        private String[] activeLabels;

        private RegistryResourceLocationVisitor(MethodVisitor delegate, String className) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && "net/minecraft/util/ResourceLocation".equals(type)) {
                closeActiveResourceLocation();
                activeLabels = resourceLocationLabels(className);
                beginProbes(mv, activeLabels);
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (activeLabels != null
                    && opcode == Opcodes.INVOKESPECIAL
                    && "net/minecraft/util/ResourceLocation".equals(owner)
                    && "<init>".equals(name)) {
                closeActiveResourceLocation();
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (isExit(opcode)) {
                closeActiveResourceLocation();
            }
            super.visitInsn(opcode);
        }

        private void closeActiveResourceLocation() {
            if (activeLabels == null) {
                return;
            }
            endProbes(mv, activeLabels);
            activeLabels = null;
        }
    }

    private static final class ModelRegistryClinitVisitor extends MethodVisitor {
        private String activeModelType;
        private String[] activeLabels;

        private ModelRegistryClinitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && isBetweenlandsClientModelType(type)) {
                closeActiveModelConstruction();
                activeModelType = type;
                activeLabels = new String[] {
                        "BL ModelRegistry.<clinit> model construction all",
                        "BL ModelRegistry.<clinit> model construction class " + dotClassName(type)
                };
                beginProbes(mv, activeLabels);
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String callLabel = modelRegistryClinitCallLabel(opcode, owner, name, desc);
            if (callLabel != null) {
                beginProbe(mv, callLabel);
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (activeModelType != null
                    && opcode == Opcodes.INVOKESPECIAL
                    && activeModelType.equals(owner)
                    && "<init>".equals(name)) {
                closeActiveModelConstruction();
            }
            if (callLabel != null) {
                endProbe(mv, callLabel);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (isExit(opcode)) {
                closeActiveModelConstruction();
            }
            super.visitInsn(opcode);
        }

        private void closeActiveModelConstruction() {
            if (activeLabels == null) {
                return;
            }
            endProbes(mv, activeLabels);
            activeModelType = null;
            activeLabels = null;
        }

        private static boolean isBetweenlandsClientModelType(String type) {
            return type != null
                    && (type.startsWith("thebetweenlands/client/render/model/baked/")
                    || type.startsWith("thebetweenlands/client/render/model/loader/")
                    || type.startsWith("thebetweenlands/client/render/model/entity/"));
        }

        private static String modelRegistryClinitCallLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "thebetweenlands/client/render/model/baked/ModelFromModelBase$Builder".equals(owner)
                    && "build".equals(name)
                    && "()Lthebetweenlands/client/render/model/baked/ModelFromModelBase;".equals(desc)) {
                return "BL ModelRegistry.<clinit> ModelFromModelBase.Builder.build calls";
            }
            if (opcode == Opcodes.INVOKESPECIAL
                    && "thebetweenlands/util/ModelConverter".equals(owner)
                    && "<init>".equals(name)) {
                return "BL ModelRegistry.<clinit> ModelConverter.<init> calls";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "thebetweenlands/util/ModelConverter".equals(owner)
                    && "getModel".equals(name)) {
                return "BL ModelRegistry.<clinit> ModelConverter.getModel calls";
            }
            return null;
        }
    }

    private static final class ModelRegistryPreInitVisitor extends MethodVisitor {
        private ModelRegistryPreInitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = modelRegistryPreInitLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(mv, label);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endProbe(mv, label);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private static String modelRegistryPreInitLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "java/lang/Class".equals(owner)
                    && "getDeclaredFields".equals(name)
                    && "()[Ljava/lang/reflect/Field;".equals(desc)) {
                return "BL ModelRegistry.preInit getDeclaredFields";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "java/lang/reflect/Field".equals(owner)
                    && "get".equals(name)
                    && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(desc)) {
                return "BL ModelRegistry.preInit Field.get calls";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "thebetweenlands/common/registries/ModelRegistry".equals(owner)
                    && "registerModel".equals(name)
                    && "(Lnet/minecraftforge/client/model/IModel;Lnet/minecraft/util/ResourceLocation;)V".equals(desc)) {
                return "BL ModelRegistry.preInit registerModel calls";
            }
            return null;
        }
    }

    private static final class BlockRegistryPreInitVisitor extends MethodVisitor {
        private BlockRegistryPreInitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = blockRegistryPreInitLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(mv, label);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endProbe(mv, label);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private static String blockRegistryPreInitLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "java/lang/Class".equals(owner)
                    && "getDeclaredFields".equals(name)
                    && "()[Ljava/lang/reflect/Field;".equals(desc)) {
                return "BL BlockRegistry.preInit getDeclaredFields";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "java/lang/reflect/Field".equals(owner)
                    && "get".equals(name)
                    && "(Ljava/lang/Object;)Ljava/lang/Object;".equals(desc)) {
                return "BL BlockRegistry.preInit Field.get calls";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "thebetweenlands/common/registries/BlockRegistry".equals(owner)
                    && "registerBlock".equals(name)
                    && "(Ljava/lang/String;Lnet/minecraft/block/Block;)V".equals(desc)) {
                return "BL BlockRegistry.preInit registerBlock calls";
            }
            return null;
        }
    }

    private static final class EntityRegistryPreInitVisitor extends MethodVisitor {
        private String[] activeLabels;

        private EntityRegistryPreInitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type) {
                Type type = (Type) value;
                String internalName = type.getInternalName();
                if (internalName != null && internalName.startsWith("thebetweenlands/common/entity/")) {
                    closeActiveEntityRegistration();
                    activeLabels = entityRegistrationLabels(internalName);
                    beginProbes(mv, activeLabels);
                }
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (activeLabels != null
                    && opcode == Opcodes.INVOKESTATIC
                    && "thebetweenlands/common/registries/EntityRegistry".equals(owner)
                    && "registerEntity".equals(name)) {
                closeActiveEntityRegistration();
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (isExit(opcode)) {
                closeActiveEntityRegistration();
            }
            super.visitInsn(opcode);
        }

        private void closeActiveEntityRegistration() {
            if (activeLabels == null) {
                return;
            }
            endProbes(mv, activeLabels);
            activeLabels = null;
        }
    }

    private static final class ClientProxyPreInitVisitor extends MethodVisitor {
        private String[] activeRenderRegistrationLabels;
        private String[] activeTileRendererLabels;

        private ClientProxyPreInitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type) {
                Type type = (Type) value;
                String internalName = type.getInternalName();
                if (internalName != null && internalName.startsWith("thebetweenlands/common/entity/")) {
                    closeActiveRenderRegistration();
                    activeRenderRegistrationLabels = renderRegistrationLabels(internalName);
                    beginProbes(mv, activeRenderRegistrationLabels);
                } else if (internalName != null && internalName.startsWith("thebetweenlands/common/tile/")) {
                    closeActiveTileRendererRegistration();
                    activeTileRendererLabels = tileRendererRegistrationLabels(internalName);
                    beginProbes(mv, activeTileRendererLabels);
                }
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String callLabel = clientProxyPreInitCallLabel(opcode, owner, name, desc);
            if (callLabel != null) {
                beginProbe(mv, callLabel);
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (opcode == Opcodes.INVOKESTATIC
                    && "net/minecraftforge/fml/client/registry/RenderingRegistry".equals(owner)
                    && "registerEntityRenderingHandler".equals(name)
                    && "(Ljava/lang/Class;Lnet/minecraftforge/fml/client/registry/IRenderFactory;)V".equals(desc)) {
                closeActiveRenderRegistration();
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "net/minecraftforge/fml/client/registry/ClientRegistry".equals(owner)
                    && "bindTileEntitySpecialRenderer".equals(name)
                    && "(Ljava/lang/Class;Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;)V".equals(desc)) {
                closeActiveTileRendererRegistration();
            }
            if (callLabel != null) {
                endProbe(mv, callLabel);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (isExit(opcode)) {
                closeActiveRenderRegistration();
                closeActiveTileRendererRegistration();
            }
            super.visitInsn(opcode);
        }

        private void closeActiveRenderRegistration() {
            if (activeRenderRegistrationLabels == null) {
                return;
            }
            endProbes(mv, activeRenderRegistrationLabels);
            activeRenderRegistrationLabels = null;
        }

        private void closeActiveTileRendererRegistration() {
            if (activeTileRendererLabels == null) {
                return;
            }
            endProbes(mv, activeTileRendererLabels);
            activeTileRendererLabels = null;
        }

        private static String clientProxyPreInitCallLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKEINTERFACE
                    && "net/minecraft/client/resources/IReloadableResourceManager".equals(owner)
                    && "func_110542_a".equals(name)
                    && "(Lnet/minecraft/client/resources/IResourceManagerReloadListener;)V".equals(desc)) {
                return "BL ClientProxy.preInit add reload listener calls";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "thebetweenlands/client/render/particle/BLParticles".equals(owner)
                    && "values".equals(name)
                    && "()[Lthebetweenlands/client/render/particle/BLParticles;".equals(desc)) {
                return "BL ClientProxy.preInit BLParticles.values";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "thebetweenlands/client/render/particle/ParticleFactory".equals(owner)
                    && "getStitcher".equals(name)
                    && "()Lthebetweenlands/client/render/particle/ParticleTextureStitcher;".equals(desc)) {
                return "BL ClientProxy.preInit particle getStitcher calls";
            }
            if (opcode == Opcodes.INVOKESPECIAL
                    && "thebetweenlands/client/handler/TextureStitchHandler$TextureStitcher".equals(owner)
                    && "<init>".equals(name)) {
                return "BL ClientProxy.preInit particle TextureStitcher.<init> calls";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "thebetweenlands/client/handler/TextureStitchHandler".equals(owner)
                    && "registerTextureStitcher".equals(name)
                    && "(Lthebetweenlands/client/handler/TextureStitchHandler$TextureStitcher;)V".equals(desc)) {
                return "BL ClientProxy.preInit register particle texture stitcher calls";
            }
            if (opcode == Opcodes.INVOKESPECIAL
                    && "net/minecraft/client/gui/FontRenderer".equals(owner)
                    && "<init>".equals(name)) {
                return "BL ClientProxy.preInit pixelLove FontRenderer.<init>";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "thebetweenlands/common/herblore/book/HLEntryRegistry".equals(owner)
                    && "init".equals(name)
                    && "()V".equals(desc)) {
                return "BL ClientProxy.preInit HLEntryRegistry.init";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "thebetweenlands/client/handler/WeedwoodRowboatHandler".equals(owner)
                    && "init".equals(name)
                    && "()V".equals(desc)) {
                return "BL ClientProxy.preInit WeedwoodRowboatHandler.init";
            }
            if (opcode == Opcodes.INVOKESPECIAL
                    && "thebetweenlands/client/proxy/ClientProxy".equals(owner)
                    && "loadRiftVariants".equals(name)
                    && "()V".equals(desc)) {
                return "BL ClientProxy.preInit loadRiftVariants";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "thebetweenlands/client/proxy/ClientProxy".equals(owner)
                    && "registerEventHandlersPreInit".equals(name)
                    && "()V".equals(desc)) {
                return "BL ClientProxy.preInit registerEventHandlersPreInit call";
            }
            return null;
        }
    }

    private static final class RegistryPreInitCallVisitor extends MethodVisitor {
        private RegistryPreInitCallVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (opcode == Opcodes.INVOKESTATIC
                    && owner != null
                    && owner.startsWith("thebetweenlands/common/registries/")
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                String label = "BL Registries.preInit -> " + owner.replace('/', '.') + ".preInit";
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }
    }

    private static final class TimedMethodVisitor extends MethodVisitor {
        private final String label;
        private boolean entered;

        private TimedMethodVisitor(MethodVisitor delegate, String label) {
            super(Opcodes.ASM9, delegate);
            this.label = label;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            entered = true;
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        @Override
        public void visitInsn(int opcode) {
            if (entered && isExit(opcode)) {
                endProbe(mv, label);
                if ("BL thebetweenlands.common.TheBetweenlands.preInit".equals(label)) {
                    mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/BetweenlandsOptimizations",
                            "logBlockRegistryFieldAccessSummary",
                            "()V",
                            false
                    );
                }
            }
            super.visitInsn(opcode);
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
