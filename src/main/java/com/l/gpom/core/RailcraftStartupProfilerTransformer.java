package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RailcraftStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.railcraftProfiler", "true"));
    private static final boolean LAZY_ITEM_CONDITIONS = Boolean.parseBoolean(System.getProperty("gpom.railcraftLazyItemConditions", "false"));
    private static final boolean DEFER_MODULE_IC2_CONTAINERS = Boolean.parseBoolean(System.getProperty("gpom.railcraft.deferModuleIC2Containers", "false"));
    private static final boolean DEFER_MODULE_CONTAINERS = Boolean.parseBoolean(System.getProperty(
            "gpom.railcraft.deferModuleContainers",
            System.getProperty("gpom.railcraft.deferModuleIC2Containers", "false")));
    private static final boolean LAZY_CART_CONFIG = Boolean.parseBoolean(System.getProperty("gpom.railcraft.lazyCartConfig", "false"));
    private static final Map<String, Set<String>> EXACT_TARGETS = createExactTargets();
    private static final Set<String> MODULE_METHODS = set("<clinit>", "<init>", "construction", "loadConfig", "preInit", "init", "postInit", "initClient");
    private static final Set<String> PLUGIN_METHODS = set("<clinit>", "<init>", "preInit", "init", "postInit", "register", "registerAll", "registerRecipes", "registerHandlers", "registerEvents", "registerModels", "load", "loadConfig", "process");
    private static final Set<String> MODEL_RESOURCE_METHODS = set("<clinit>", "<init>", "initializeClient", "onResourceManagerReload", "registerItemModel", "registerBlockItemModel", "registerComplexItemModel", "getModel", "loadModel", "bake", "create", "load", "loadTextures");
    private static final Set<String> OBJECT_CONTAINER_METHODS = set("<clinit>", "<init>", "initializeDefinition", "finalizeDefinition", "initializeClient", "defineRecipes", "registerTileEntities", "register", "registerAll");
    private static final String BRICK_THEME_OWNER = "mods/railcraft/common/blocks/aesthetics/brick/BrickTheme";
    private static final String[] BRICK_THEME_VALUES = {
            "ABYSSAL", "BLEACHEDBONE", "BLOODSTAINED", "FROSTBOUND", "INFERNAL",
            "JADED", "QUARRIED", "SANDY", "BADLANDS", "NETHER", "RED_NETHER",
            "ANDESITE", "DIORITE", "GRANITE", "PEARLIZED"
    };
    private static final String[] NON_VANILLA_CART_CONFIG_TAGS = {
            "cart_spawner", "bore", "cart_cargo", "cart_chest_metals", "cart_chest_void",
            "cart_ic2_batbox", "cart_ic2_cesu", "cart_ic2_mfe", "cart_ic2_mfsu",
            "cart_gift", "cart_jukebox", "cart_bed", "mow_track_layer",
            "mow_track_relayer", "mow_track_remover", "mow_undercutter", "cart_pumpkin",
            "cart_redstone_flux", "cart_tank", "cart_tnt_wood", "cart_trade_station",
            "cart_work", "locomotive_steam_solid", "locomotive_electric",
            "locomotive_creative", "cart_worldspike_standard", "cart_worldspike_admin",
            "cart_worldspike_personal"
    };

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null
                || (className != null && className.startsWith("com.l.gpom."))
                || !className.startsWith("mods.railcraft.")
                || !TargetedModVersions.isRailcraftClass(className)) {
            return basicClass;
        }

        Set<String> methods = methodsFor(className);
        if (methods == null) {
            return basicClass;
        }

        if (DEFER_MODULE_CONTAINERS && className.startsWith("mods.railcraft.common.modules.Module")) {
            basicClass = patchModuleContainerAdds(basicClass);
        } else if (DEFER_MODULE_IC2_CONTAINERS && "mods.railcraft.common.modules.ModuleIC2".equals(className)) {
            basicClass = patchModuleIC2Constructor(basicClass);
        }
        if (LAZY_CART_CONFIG && "mods.railcraft.common.core.RailcraftConfig".equals(className)) {
            basicClass = patchRailcraftConfigLoadCarts(basicClass);
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            reader.accept(new ProfilingClassVisitor(writer, className, methods), 0);
            return writer.toByteArray();
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static byte[] patchRailcraftConfigLoadCarts(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"loadCarts".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                InsnList replacement = new InsnList();
                replacement.add(new FieldInsnNode(
                        Opcodes.GETSTATIC,
                        "mods/railcraft/common/core/RailcraftConfig",
                        "configEntity",
                        "Lnet/minecraftforge/common/config/Configuration;"
                ));
                replacement.add(new LdcInsnNode("entities"));
                replacement.add(new LdcInsnNode("Disable individual entities here."));
                replacement.add(new MethodInsnNode(
                        Opcodes.INVOKEVIRTUAL,
                        "net/minecraftforge/common/config/Configuration",
                        "addCustomCategoryComment",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        false
                ));
                for (String tag : NON_VANILLA_CART_CONFIG_TAGS) {
                    replacement.add(new LdcInsnNode(tag));
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "mods/railcraft/common/core/RailcraftConfig",
                            "loadEntityProperty",
                            "(Ljava/lang/String;)V",
                            false
                    ));
                }
                replacement.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
                method.instructions.clear();
                method.instructions.add(replacement);
                changed = true;
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

    private static byte[] patchModuleIC2Constructor(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                int group = 0;
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (methodInsn.getOpcode() != Opcodes.INVOKEVIRTUAL
                            || !"mods/railcraft/common/modules/RailcraftModulePayload".equals(methodInsn.owner)
                            || !"add".equals(methodInsn.name)
                            || !"([Lmods/railcraft/common/core/IRailcraftObjectContainer;)V".equals(methodInsn.desc)) {
                        continue;
                    }
                    AbstractInsnNode start = findModulePayloadAddStart(insn);
                    if (start == null) {
                        continue;
                    }
                    group++;
                    String variant = group == 1 ? "base" : "classic";
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    replacement.add(new LdcInsnNode(variant));
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                            "deferModuleIC2Containers",
                            "(Ljava/lang/Object;Ljava/lang/String;)V",
                            false
                    ));
                    AbstractInsnNode resume = replacement.getLast();
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode current = start;
                    while (current != null) {
                        AbstractInsnNode next = current.getNext();
                        method.instructions.remove(current);
                        if (current == insn) {
                            break;
                        }
                        current = next;
                    }
                    changed = true;
                    insn = resume;
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

    private static byte[] patchModuleContainerAdds(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"<init>".equals(method.name) || !"()V".equals(method.desc)) {
                    continue;
                }
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (!(insn instanceof MethodInsnNode)) {
                        continue;
                    }
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    if (!isModulePayloadAdd(methodInsn)) {
                        continue;
                    }
                    AbstractInsnNode start = findModulePayloadAddStart(insn);
                    if (start == null) {
                        continue;
                    }
                    String descriptors = collectContainerDescriptors(start, insn);
                    if (descriptors == null || descriptors.isEmpty()) {
                        continue;
                    }
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    replacement.add(new LdcInsnNode(descriptors));
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                            "deferModuleContainers",
                            "(Ljava/lang/Object;Ljava/lang/String;)V",
                            false
                    ));
                    AbstractInsnNode resume = replacement.getLast();
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode current = start;
                    while (current != null) {
                        AbstractInsnNode next = current.getNext();
                        method.instructions.remove(current);
                        if (current == insn) {
                            break;
                        }
                        current = next;
                    }
                    changed = true;
                    insn = resume;
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

    private static boolean isModulePayloadAdd(MethodInsnNode methodInsn) {
        return methodInsn.getOpcode() == Opcodes.INVOKEVIRTUAL
                && ("mods/railcraft/common/modules/RailcraftModulePayload".equals(methodInsn.owner)
                || methodInsn.owner.startsWith("mods/railcraft/common/modules/Module"))
                && "add".equals(methodInsn.name)
                && "([Lmods/railcraft/common/core/IRailcraftObjectContainer;)V".equals(methodInsn.desc);
    }

    private static String collectContainerDescriptors(AbstractInsnNode start, AbstractInsnNode addCall) {
        List<String> descriptors = new ArrayList<>();
        for (AbstractInsnNode current = start; current != null; current = current.getNext()) {
            if (current != addCall && current instanceof MethodInsnNode) {
                return null;
            }
            if (current instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) current;
                if (isBrickThemeValues(fieldInsn)) {
                    for (String theme : BRICK_THEME_VALUES) {
                        descriptors.add(BRICK_THEME_OWNER.replace('/', '.') + "#" + theme);
                    }
                    continue;
                }
                if (fieldInsn.getOpcode() != Opcodes.GETSTATIC || !isRailcraftContainer(fieldInsn.owner)) {
                    return null;
                }
                descriptors.add(fieldInsn.owner.replace('/', '.') + "#" + fieldInsn.name);
            }
            if (current == addCall) {
                break;
            }
        }
        if (descriptors.isEmpty()) {
            return null;
        }
        return String.join(";", descriptors);
    }

    private static boolean isBrickThemeValues(FieldInsnNode fieldInsn) {
        return fieldInsn.getOpcode() == Opcodes.GETSTATIC
                && BRICK_THEME_OWNER.equals(fieldInsn.owner)
                && "VALUES".equals(fieldInsn.name)
                && "[Lmods/railcraft/common/blocks/aesthetics/brick/BrickTheme;".equals(fieldInsn.desc);
    }

    private static AbstractInsnNode findModulePayloadAddStart(AbstractInsnNode addCall) {
        for (AbstractInsnNode current = addCall.getPrevious(); current != null; current = current.getPrevious()) {
            if (current instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) current;
                if ("<init>".equals(methodInsn.name)) {
                    return null;
                }
            }
            if (current instanceof VarInsnNode) {
                VarInsnNode varInsn = (VarInsnNode) current;
                if (varInsn.getOpcode() == Opcodes.ALOAD && varInsn.var == 0) {
                    return current;
                }
            }
        }
        return null;
    }

    private static boolean isRailcraftContainer(String owner) {
        return "mods/railcraft/common/carts/RailcraftCarts".equals(owner)
                || "mods/railcraft/common/blocks/RailcraftBlocks".equals(owner)
                || "mods/railcraft/common/items/RailcraftItems".equals(owner)
                || "mods/railcraft/common/fluids/RailcraftFluids".equals(owner)
                || "mods/railcraft/common/blocks/tracks/outfitted/TrackKits".equals(owner)
                || "mods/railcraft/common/items/potion/RailcraftPotions".equals(owner)
                || "mods/railcraft/common/items/potion/RailcraftPotionTypes".equals(owner);
    }

    private static Set<String> methodsFor(String className) {
        Set<String> exact = EXACT_TARGETS.get(className);
        if (exact != null) {
            return exact;
        }
        if (className.startsWith("mods.railcraft.common.modules.Module")
                || className.startsWith("mods.railcraft.common.modules.RailcraftModuleManager$Stage")) {
            return MODULE_METHODS;
        }
        if (className.startsWith("mods.railcraft.common.plugins.")) {
            return PLUGIN_METHODS;
        }
        if (className.startsWith("mods.railcraft.client.render.models.resource.")
                || className.startsWith("mods.railcraft.client.util.textures.")) {
            return MODEL_RESOURCE_METHODS;
        }
        if (className.startsWith("mods.railcraft.common.blocks.")
                || className.startsWith("mods.railcraft.common.items.")
                || className.startsWith("mods.railcraft.common.fluids.")
                || className.startsWith("mods.railcraft.common.carts.")) {
            return OBJECT_CONTAINER_METHODS;
        }
        return null;
    }

    private static Map<String, Set<String>> createExactTargets() {
        Map<String, Set<String>> targets = new HashMap<>();
        targets.put("mods.railcraft.common.core.Railcraft", set("<clinit>", "<init>", "preInit", "init", "postInit", "processIMCRequests"));
        targets.put("mods.railcraft.common.core.CommonProxy", set("<clinit>", "<init>", "initializeClient", "finalizeClient"));
        targets.put("mods.railcraft.client.core.ClientProxy", set("<clinit>", "<init>", "initializeClient", "finalizeClient", "onResourceManagerReload", "bindTESR", "lambda$initializeClient$0", "lambda$initializeClient$1"));
        targets.put("mods.railcraft.common.core.RailcraftConfig", set(
                "<clinit>", "preInit", "postInit", "saveConfigs", "loadClient", "loadEnchantment", "loadBlockTweaks", "loadItemTweaks",
                "loadRoutingTweaks", "loadCartTweaks", "loadRecipeOption", "loadWorldGen", "loadFluids", "loadCarts", "loadEntityProperty",
                "loadBlocks", "loadBlockProperty", "loadBlockFeature", "loadItems", "loadItemProperty", "loadBoreMineableBlocks", "loadRecipeProperty"));
        targets.put("mods.railcraft.common.modules.RailcraftModuleManager", set(
                "<clinit>", "loadModules", "preInit", "init", "postInit", "processStage", "setStage", "isConfigured",
                "getDependencies", "getSoftDependencies", "getAllDependencies", "getModuleName", "isModuleEnabled", "isObjectDefined"));
        targets.put("mods.railcraft.common.modules.RailcraftModulePayload", set("<clinit>", "<init>", "add", "getObjects", "getModuleEventHandler"));
        targets.put("mods.railcraft.common.blocks.RailcraftBlocks", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.items.RailcraftItems", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.fluids.RailcraftFluids", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.carts.RailcraftCarts", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.blocks.aesthetics.brick.BrickTheme", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.blocks.tracks.outfitted.TrackKits", set(
                "<clinit>", "<init>", "register", "defineRecipes", "isEnabled", "isLoaded", "getObject", "getDef"));
        targets.put("mods.railcraft.common.items.potion.RailcraftPotions", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.items.potion.RailcraftPotionTypes", OBJECT_CONTAINER_METHODS);
        targets.put("mods.railcraft.common.blocks.machine.MachineTileRegistry", set("<clinit>", "registerTileEntities"));
        targets.put("mods.railcraft.common.util.sounds.SoundRegistry", set("<clinit>", "register", "registerSounds"));
        targets.put("mods.railcraft.common.util.sounds.RailcraftSoundEvents", set("<clinit>", "register", "registerSounds"));
        targets.put("mods.railcraft.api.tracks.TrackRegistry", set("<clinit>", "registerTrack", "registerTrackSpec", "register"));
        targets.put("mods.railcraft.common.plugins.forge.RailcraftRegistry", set("<clinit>", "register", "registerAll", "registerBlock", "registerItem", "registerTileEntity"));
        return targets;
    }

    private static Set<String> set(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    private static final class ProfilingClassVisitor extends ClassVisitor {
        private final String className;
        private final Set<String> methodNames;

        private ProfilingClassVisitor(ClassVisitor delegate, String className, Set<String> methodNames) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
            this.methodNames = methodNames;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null || !methodNames.contains(name)) {
                return visitor;
            }
            MethodVisitor timed = new TimedMethodVisitor(visitor, "RC " + className + '.' + name + desc);
            if (className.startsWith("mods.railcraft.common.modules.Module") && "<init>".equals(name)) {
                return new RailcraftModuleConstructorVisitor(timed, className);
            }
            if ("mods.railcraft.common.modules.RailcraftModuleManager".equals(className)
                    && "loadModules".equals(name)
                    && "(Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)V".equals(desc)) {
                return new RailcraftModuleManagerLoadModulesVisitor(timed);
            }
            if ("mods.railcraft.common.modules.RailcraftModuleManager".equals(className)
                    && "preInit".equals(name)
                    && "()V".equals(desc)) {
                return new RailcraftModuleManagerPreInitVisitor(timed);
            }
            if ("mods.railcraft.common.modules.RailcraftModuleManager".equals(className)
                    && "processStage".equals(name)
                    && "(Lmods/railcraft/common/modules/RailcraftModuleManager$Stage;)V".equals(desc)) {
                return new RailcraftProcessStageVisitor(timed);
            }
            if ("mods.railcraft.common.core.RailcraftConfig".equals(className)
                    && ("loadCarts".equals(name) || "loadBlocks".equals(name) || "loadItems".equals(name))) {
                return new RailcraftConfigLoadVisitor(timed, name);
            }
            if ("mods.railcraft.client.core.ClientProxy".equals(className)
                    && ("initializeClient".equals(name) || "lambda$initializeClient$0".equals(name) || "lambda$initializeClient$1".equals(name))) {
                return new RailcraftClientInitializeVisitor(timed, name);
            }
            if (LAZY_ITEM_CONDITIONS && "<init>".equals(name) && isRailcraftLazyItemConditionTarget(className)) {
                return new RailcraftLazyItemConditionVisitor(timed, className);
            }
            if ("<clinit>".equals(name) && isRailcraftEnumContainer(className)) {
                return new RailcraftEnumClinitVisitor(timed, className);
            }
            if ("mods.railcraft.common.blocks.tracks.outfitted.TrackKits".equals(className)) {
                return new RailcraftTrackKitsVisitor(timed, className);
            }
            return timed;
        }

        private static boolean isRailcraftEnumContainer(String className) {
            return "mods.railcraft.common.carts.RailcraftCarts".equals(className)
                    || "mods.railcraft.common.blocks.RailcraftBlocks".equals(className)
                    || "mods.railcraft.common.items.RailcraftItems".equals(className)
                    || "mods.railcraft.common.fluids.RailcraftFluids".equals(className)
                    || "mods.railcraft.common.blocks.tracks.outfitted.TrackKits".equals(className)
                    || "mods.railcraft.common.blocks.aesthetics.brick.BrickTheme".equals(className);
        }

        private static boolean isRailcraftLazyItemConditionTarget(String className) {
            return "mods.railcraft.common.items.RailcraftItems$9".equals(className)
                    || "mods.railcraft.common.items.RailcraftItems$10".equals(className);
        }
    }

    private static final class RailcraftProcessStageVisitor extends MethodVisitor {
        private RailcraftProcessStageVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            super.visitVarInsn(opcode, var);
            if (opcode == Opcodes.ISTORE && var == 4) {
                super.visitVarInsn(Opcodes.ALOAD, 3);
                super.visitVarInsn(Opcodes.ILOAD, 4);
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                        "realizeDeferredModuleContainersForStage",
                        "(Ljava/lang/Object;ZLjava/lang/Object;)V",
                        false
                );
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitVarInsn(Opcodes.ALOAD, 3);
                super.visitVarInsn(Opcodes.ILOAD, 4);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                        "beginModuleStageProbe",
                        "(Ljava/lang/Object;Ljava/lang/Object;Z)V",
                        false
                );
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "mods/railcraft/common/modules/RailcraftModuleManager$Stage".equals(owner)
                    && "passToModule".equals(name)
                    && "(Lmods/railcraft/api/core/IRailcraftModule$ModuleEventHandler;)V".equals(desc)) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitVarInsn(Opcodes.ALOAD, 3);
                super.visitVarInsn(Opcodes.ILOAD, 4);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                        "endModuleStageProbe",
                        "(Ljava/lang/Object;Ljava/lang/Object;Z)V",
                        false
                );
            }
        }

        @Override
        public void visitCode() {
            super.visitCode();
            super.visitVarInsn(Opcodes.ALOAD, 0);
            super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                    "realizeDeferredModuleIC2Containers",
                    "(Ljava/lang/Object;)V",
                    false
            );
        }
    }

    private static final class RailcraftModuleManagerLoadModulesVisitor extends MethodVisitor {
        private RailcraftModuleManagerLoadModulesVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = loadModulesLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(label);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endProbe(label);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private static String loadModulesLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "net/minecraftforge/fml/common/discovery/ASMDataTable".equals(owner)
                    && "getAll".equals(name)
                    && "(Ljava/lang/String;)Ljava/util/Set;".equals(desc)) {
                return "RC RailcraftModuleManager.loadModules ASMDataTable.getAll";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "java/lang/Class".equals(owner)
                    && "forName".equals(name)
                    && "(Ljava/lang/String;)Ljava/lang/Class;".equals(desc)) {
                return "RC RailcraftModuleManager.loadModules Class.forName";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "java/lang/Class".equals(owner)
                    && "newInstance".equals(name)
                    && "()Ljava/lang/Object;".equals(desc)) {
                return "RC RailcraftModuleManager.loadModules Class.newInstance";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "mods/railcraft/common/modules/RailcraftModuleManager".equals(owner)
                    && "getModuleName".equals(name)
                    && "(Ljava/lang/Class;)Ljava/lang/String;".equals(desc)) {
                return "RC RailcraftModuleManager.loadModules getModuleName";
            }
            return null;
        }

        private void beginProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private void endProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }
    }

    private static final class RailcraftModuleManagerPreInitVisitor extends MethodVisitor {
        private RailcraftModuleManagerPreInitVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = preInitLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(label);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endProbe(label);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private static String preInitLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKESTATIC
                    && "mods/railcraft/common/modules/RailcraftModuleManager".equals(owner)
                    && "isConfigured".equals(name)
                    && "(Ljava/io/File;Lmods/railcraft/api/core/IRailcraftModule;)Z".equals(desc)) {
                return "RC RailcraftModuleManager.preInit isConfigured calls";
            }
            if (opcode == Opcodes.INVOKEINTERFACE
                    && "mods/railcraft/api/core/IRailcraftModule".equals(owner)
                    && "checkPrerequisites".equals(name)
                    && "()V".equals(desc)) {
                return "RC RailcraftModuleManager.preInit checkPrerequisites calls";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "mods/railcraft/common/modules/RailcraftModuleManager".equals(owner)
                    && "getDependencies".equals(name)
                    && "(Ljava/lang/Class;)Ljava/util/Set;".equals(desc)) {
                return "RC RailcraftModuleManager.preInit getDependencies calls";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "mods/railcraft/common/modules/RailcraftModuleManager".equals(owner)
                    && "getAllDependencies".equals(name)
                    && "(Ljava/lang/Class;Ljava/util/Set;)Ljava/util/Set;".equals(desc)) {
                return "RC RailcraftModuleManager.preInit getAllDependencies calls";
            }
            if (opcode == Opcodes.INVOKESTATIC
                    && "mods/railcraft/common/modules/RailcraftModuleManager".equals(owner)
                    && "processStage".equals(name)
                    && "(Lmods/railcraft/common/modules/RailcraftModuleManager$Stage;)V".equals(desc)) {
                return "RC RailcraftModuleManager.preInit processStage calls";
            }
            return null;
        }

        private void beginProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private void endProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }
    }

    private static final class RailcraftConfigLoadVisitor extends MethodVisitor {
        private final String methodName;

        private RailcraftConfigLoadVisitor(MethodVisitor delegate, String methodName) {
            super(Opcodes.ASM9, delegate);
            this.methodName = methodName;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            String label = configFieldLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(label);
                super.visitFieldInsn(opcode, owner, name, desc);
                endProbe(label);
                return;
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = configMethodLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(label);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endProbe(label);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private String configFieldLabel(int opcode, String owner, String name, String desc) {
            if (opcode != Opcodes.GETSTATIC || !"VALUES".equals(name)) {
                return null;
            }
            if ("loadCarts".equals(methodName)
                    && "mods/railcraft/common/carts/RailcraftCarts".equals(owner)
                    && "[Lmods/railcraft/common/carts/RailcraftCarts;".equals(desc)) {
                return "RC RailcraftConfig.loadCarts RailcraftCarts.VALUES";
            }
            if ("loadBlocks".equals(methodName)
                    && "mods/railcraft/common/blocks/RailcraftBlocks".equals(owner)
                    && "[Lmods/railcraft/common/blocks/RailcraftBlocks;".equals(desc)) {
                return "RC RailcraftConfig.loadBlocks RailcraftBlocks.VALUES";
            }
            if ("loadBlocks".equals(methodName)
                    && "mods/railcraft/common/blocks/tracks/outfitted/TrackKits".equals(owner)
                    && "[Lmods/railcraft/common/blocks/tracks/outfitted/TrackKits;".equals(desc)) {
                return "RC RailcraftConfig.loadBlocks TrackKits.VALUES";
            }
            if ("loadItems".equals(methodName)
                    && "mods/railcraft/common/items/RailcraftItems".equals(owner)
                    && "[Lmods/railcraft/common/items/RailcraftItems;".equals(desc)) {
                return "RC RailcraftConfig.loadItems RailcraftItems.VALUES";
            }
            return null;
        }

        private String configMethodLabel(int opcode, String owner, String name, String desc) {
            if ("loadCarts".equals(methodName)) {
                if (opcode == Opcodes.INVOKESTATIC
                        && "mods/railcraft/common/core/RailcraftConfig".equals(owner)
                        && "loadEntityProperty".equals(name)) {
                    return "RC RailcraftConfig.loadCarts loadEntityProperty calls";
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "mods/railcraft/common/carts/RailcraftCarts".equals(owner)
                        && ("isVanillaCart".equals(name) || "getBaseTag".equals(name))) {
                    return "RC RailcraftConfig.loadCarts RailcraftCarts." + name + " calls";
                }
            }
            if ("loadBlocks".equals(methodName)) {
                if (opcode == Opcodes.INVOKESTATIC
                        && "mods/railcraft/common/core/RailcraftConfig".equals(owner)
                        && ("loadBlockProperty".equals(name) || "loadBlockFeature".equals(name))) {
                    return "RC RailcraftConfig.loadBlocks " + name + " calls";
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "mods/railcraft/common/blocks/RailcraftBlocks".equals(owner)
                        && ("getBaseTag".equals(name) || "getVariantClass".equals(name))) {
                    return "RC RailcraftConfig.loadBlocks RailcraftBlocks." + name + " calls";
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "java/lang/Class".equals(owner)
                        && "getEnumConstants".equals(name)) {
                    return "RC RailcraftConfig.loadBlocks variant getEnumConstants calls";
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "mods/railcraft/common/blocks/tracks/outfitted/TrackKits".equals(owner)
                        && "getRegistryName".equals(name)) {
                    return "RC RailcraftConfig.loadBlocks TrackKits.getRegistryName calls";
                }
            }
            if ("loadItems".equals(methodName)) {
                if (opcode == Opcodes.INVOKESTATIC
                        && "mods/railcraft/common/core/RailcraftConfig".equals(owner)
                        && "loadItemProperty".equals(name)) {
                    return "RC RailcraftConfig.loadItems loadItemProperty calls";
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "mods/railcraft/common/items/RailcraftItems".equals(owner)
                        && ("getBaseTag".equals(name) || "isOverridable".equals(name))) {
                    return "RC RailcraftConfig.loadItems RailcraftItems." + name + " calls";
                }
            }
            return null;
        }

        private void beginProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private void endProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }
    }

    private static final class RailcraftClientInitializeVisitor extends MethodVisitor {
        private final String methodName;

        private RailcraftClientInitializeVisitor(MethodVisitor delegate, String methodName) {
            super(Opcodes.ASM9, delegate);
            this.methodName = methodName;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = clientLabel(opcode, owner, name, desc);
            if (label != null) {
                beginProbe(label);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                endProbe(label);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private String clientLabel(int opcode, String owner, String name, String desc) {
            if ("initializeClient".equals(methodName)) {
                if (opcode == Opcodes.INVOKESTATIC
                        && "net/minecraftforge/client/model/ModelLoaderRegistry".equals(owner)
                        && "registerLoader".equals(name)) {
                    return "RC ClientProxy.initializeClient ModelLoaderRegistry.registerLoader calls";
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "net/minecraftforge/fml/common/eventhandler/EventBus".equals(owner)
                        && "register".equals(name)) {
                    return "RC ClientProxy.initializeClient EventBus.register calls";
                }
                if (opcode == Opcodes.INVOKESTATIC
                        && "mods/railcraft/common/core/RailcraftObjects".equals(owner)
                        && ("processItems".equals(name) || "processBlocks".equals(name))) {
                    return "RC ClientProxy.initializeClient RailcraftObjects." + name;
                }
                if (opcode == Opcodes.INVOKEVIRTUAL
                        && "mods/railcraft/client/render/models/resource/JSONModelRenderer".equals(owner)
                        && "registerModel".equals(name)) {
                    return "RC ClientProxy.initializeClient JSONModelRenderer.registerModel calls";
                }
                if (opcode == Opcodes.INVOKESTATIC
                        && "net/minecraftforge/fml/client/registry/RenderingRegistry".equals(owner)
                        && "registerEntityRenderingHandler".equals(name)) {
                    return "RC ClientProxy.initializeClient RenderingRegistry.registerEntityRenderingHandler calls";
                }
            }
            if ("lambda$initializeClient$0".equals(methodName)) {
                if (opcode == Opcodes.INVOKEINTERFACE
                        && "mods/railcraft/common/blocks/IRailcraftBlock".equals(owner)
                        && "initializeClient".equals(name)) {
                    return "RC ClientProxy.initializeClient block.initializeClient calls";
                }
                if (opcode == Opcodes.INVOKEINTERFACE
                        && "mods/railcraft/common/blocks/IRailcraftItemBlock".equals(owner)
                        && "initializeClient".equals(name)) {
                    return "RC ClientProxy.initializeClient itemBlock.initializeClient calls";
                }
            }
            if ("lambda$initializeClient$1".equals(methodName)) {
                if (opcode == Opcodes.INVOKEINTERFACE
                        && "mods/railcraft/common/blocks/IRailcraftBlock".equals(owner)
                        && "getStack".equals(name)) {
                    return "RC ClientProxy.initializeClient block.getStack calls";
                }
                if (opcode == Opcodes.INVOKEINTERFACE
                        && "mods/railcraft/common/blocks/IRailcraftBlock".equals(owner)
                        && "registerItemModel".equals(name)) {
                    return "RC ClientProxy.initializeClient block.registerItemModel calls";
                }
            }
            return null;
        }

        private void beginProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private void endProbe(String label) {
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }
    }

    private static final class RailcraftLazyItemConditionVisitor extends MethodVisitor {
        private final String className;
        private String pendingOwner;
        private String pendingField;

        private RailcraftLazyItemConditionVisitor(MethodVisitor delegate, String className) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETSTATIC && isLazyConditionDependency(className, owner, name, desc)) {
                pendingOwner = owner;
                pendingField = name;
                super.visitLdcInsn("RC " + className + ".<init> lazy condition " + owner.replace('/', '.') + '.' + name);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
                super.visitLdcInsn(owner.replace('/', '.'));
                super.visitLdcInsn(name);
                super.visitLdcInsn("RC " + className + ".<init> lazy condition " + owner.replace('/', '.') + '.' + name);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                return;
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            if (pendingOwner != null
                    && opcode == Opcodes.INVOKEVIRTUAL
                    && "mods/railcraft/common/core/InitializationConditional".equals(owner)
                    && "add".equals(name)
                    && "(Lmods/railcraft/common/core/IRailcraftObjectContainer;)Lmods/railcraft/common/core/InitializationConditional;".equals(desc)) {
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/RailcraftInitializationOptimizations",
                        "addLazyContainerCondition",
                        "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                        false
                );
                pendingOwner = null;
                pendingField = null;
                return;
            }
            pendingOwner = null;
            pendingField = null;
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private static boolean isLazyConditionDependency(String className, String owner, String name, String desc) {
            if ("mods.railcraft.common.items.RailcraftItems$9".equals(className)) {
                return "mods/railcraft/common/blocks/RailcraftBlocks".equals(owner)
                        && "BLEACHED_BONE_BRICK".equals(name)
                        && "Lmods/railcraft/common/blocks/RailcraftBlocks;".equals(desc);
            }
            if ("mods.railcraft.common.items.RailcraftItems$10".equals(className)) {
                return "mods/railcraft/common/carts/RailcraftCarts".equals(owner)
                        && "BORE".equals(name)
                        && "Lmods/railcraft/common/carts/RailcraftCarts;".equals(desc);
            }
            return false;
        }
    }

    private static final class RailcraftEnumClinitVisitor extends MethodVisitor {
        private final String className;
        private final String owner;
        private boolean pendingConstant;
        private boolean allProbeOpen;
        private String constantLabel;

        private RailcraftEnumClinitVisitor(MethodVisitor delegate, String className) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
            this.owner = className.replace('.', '/');
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            if (opcode == Opcodes.NEW && owner.equals(type) && !pendingConstant) {
                pendingConstant = true;
                allProbeOpen = true;
                constantLabel = null;
                beginLabel("RC " + className + ".<clinit> enum constant all");
            }
            super.visitTypeInsn(opcode, type);
        }

        @Override
        public void visitLdcInsn(Object value) {
            if (pendingConstant && constantLabel == null && value instanceof String) {
                constantLabel = "RC " + className + ".<clinit> enum constant " + value;
                beginLabel(constantLabel);
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            super.visitFieldInsn(opcode, owner, name, desc);
            if (pendingConstant
                    && opcode == Opcodes.PUTSTATIC
                    && this.owner.equals(owner)
                    && desc != null
                    && desc.equals('L' + this.owner + ';')) {
                closeConstantProbe();
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (pendingConstant && TimedMethodVisitor.isExit(opcode)) {
                closeConstantProbe();
            }
            super.visitInsn(opcode);
        }

        private void closeConstantProbe() {
            if (constantLabel != null) {
                endLabel(constantLabel);
            }
            if (allProbeOpen) {
                endLabel("RC " + className + ".<clinit> enum constant all");
            }
            pendingConstant = false;
            allProbeOpen = false;
            constantLabel = null;
        }

        private void beginLabel(String probeLabel) {
            super.visitLdcInsn(probeLabel);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private void endLabel(String probeLabel) {
            super.visitLdcInsn(probeLabel);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }
    }

    private static final class RailcraftTrackKitsVisitor extends MethodVisitor {
        private final String className;

        private RailcraftTrackKitsVisitor(MethodVisitor delegate, String className) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETSTATIC && RailcraftModuleConstructorVisitor.isRailcraftContainer(owner)) {
                String label = "RC " + className + " GETSTATIC " + owner.replace('/', '.') + '.' + name;
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
                super.visitFieldInsn(opcode, owner, name, desc);
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                return;
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = trackKitsLabel(opcode, owner, name, desc);
            if (label != null) {
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);
        }

        private static String trackKitsLabel(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "mods/railcraft/api/tracks/TrackKit$Builder".equals(owner)
                    && "build".equals(name)
                    && "()Lmods/railcraft/api/tracks/TrackKit;".equals(desc)) {
                return "RC TrackKits.register TrackKit.Builder.build";
            }
            if (opcode == Opcodes.INVOKEVIRTUAL
                    && "mods/railcraft/api/tracks/TrackRegistry".equals(owner)
                    && "register".equals(name)
                    && "(Lnet/minecraft/util/IStringSerializable;)V".equals(desc)) {
                return "RC TrackKits.register TrackRegistry.register";
            }
            if (opcode == Opcodes.INVOKEINTERFACE
                    && "java/util/function/Supplier".equals(owner)
                    && "get".equals(name)
                    && "()Ljava/lang/Object;".equals(desc)) {
                return "RC TrackKits.defineRecipes recipeSupplier.get";
            }
            return null;
        }
    }

    private static final class RailcraftModuleConstructorVisitor extends MethodVisitor {
        private final String className;

        private RailcraftModuleConstructorVisitor(MethodVisitor delegate, String className) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            if (opcode == Opcodes.GETSTATIC && isRailcraftContainer(owner)) {
                String label = "RC " + className + ".<init> GETSTATIC " + owner.replace('/', '.') + '.' + name;
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
                super.visitFieldInsn(opcode, owner, name, desc);
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                return;
            }
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        private static boolean isRailcraftContainer(String owner) {
            return "mods/railcraft/common/carts/RailcraftCarts".equals(owner)
                    || "mods/railcraft/common/blocks/RailcraftBlocks".equals(owner)
                    || "mods/railcraft/common/items/RailcraftItems".equals(owner)
                    || "mods/railcraft/common/fluids/RailcraftFluids".equals(owner)
                    || "mods/railcraft/common/blocks/tracks/outfitted/TrackKits".equals(owner)
                    || "mods/railcraft/common/items/potion/RailcraftPotions".equals(owner)
                    || "mods/railcraft/common/items/potion/RailcraftPotionTypes".equals(owner)
                    || "mods/railcraft/common/blocks/aesthetics/brick/BrickTheme".equals(owner);
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
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
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
}
