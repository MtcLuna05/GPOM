package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class TechRebornStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.techrebornProfiler", "true"));
    private static final boolean FAST_INDUSTRIAL_SAWMILL_MATCHING = Boolean.parseBoolean(System.getProperty("gpom.techreborn.fastIndustrialSawmillMatching", "true"));
    private static final Map<String, Set<String>> EXACT_TARGETS = createExactTargets();
    private static final Set<String> INIT_METHODS = set("<clinit>", "<init>", "init", "postInit", "register", "registerItem", "registerBlock", "registerBlockNoItem", "registerOreDict", "load", "loadConfig");
    private static final Set<String> RECIPE_METHODS = set("<clinit>", "<init>", "init", "postInit", "addRecipes", "addShapedRecipes", "addShapelessRecipes", "addMachineRecipes", "addIc2Recipes", "addVacuumFreezerRecipes", "register", "remove", "load");
    private static final Set<String> COMPAT_METHODS = set("<clinit>", "<init>", "init", "preInit", "postInit", "checkConfig", "register", "registerRecipes", "load", "loadConfig");
    private static final Set<String> CLIENT_RESOURCE_METHODS = set("<clinit>", "<init>", "preInit", "init", "registerModels", "registerItems", "registerBlocks", "register", "registerBlockstate", "registerBlockstateMultiItem", "setBlockStateMapper", "onResourceManagerReload", "loadModel", "bake", "create");

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null || (className != null && className.startsWith("com.l.gpom.")) || !isSupportedTargetClass(className)) {
            return basicClass;
        }

        Set<String> methods = methodsFor(className);
        if (methods == null) {
            return basicClass;
        }

        if (FAST_INDUSTRIAL_SAWMILL_MATCHING && "techreborn.init.recipes.IndustrialSawmillRecipes".equals(className)) {
            basicClass = patchIndustrialSawmillMatching(basicClass);
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

    private static byte[] patchIndustrialSawmillMatching(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if (!"findMatchingRecipe".equals(method.name)
                        || !"(Lnet/minecraft/inventory/InventoryCrafting;)Lnet/minecraft/item/ItemStack;".equals(method.desc)) {
                    continue;
                }
                InsnList instructions = new InsnList();
                instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                instructions.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        "com/l/gpom/optimization/TechRebornRecipeOptimizations",
                        "findMatchingIndustrialSawmillRecipe",
                        "(Lnet/minecraft/inventory/InventoryCrafting;)Lnet/minecraft/item/ItemStack;",
                        false
                ));
                instructions.add(new InsnNode(Opcodes.ARETURN));
                method.instructions = instructions;
                method.tryCatchBlocks.clear();
                method.localVariables.clear();
                method.maxLocals = 1;
                method.maxStack = 1;
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

    private static boolean isSupportedTargetClass(String className) {
        if (className.startsWith("techreborn.")) {
            return TargetedModVersions.isTechRebornClass(className);
        }
        return "reborncore.api.scriba.TileRegistrationManager".equals(className)
                && TargetedModVersions.isRebornCoreClass(className)
                && TargetedModVersions.isTechRebornClass("techreborn.Core");
    }

    private static Set<String> methodsFor(String className) {
        Set<String> exact = EXACT_TARGETS.get(className);
        if (exact != null) {
            return exact;
        }
        if (className.startsWith("techreborn.init.recipes.")) {
            return RECIPE_METHODS;
        }
        if (className.startsWith("techreborn.compat.")) {
            return COMPAT_METHODS;
        }
        if (className.startsWith("techreborn.client.")
                || className.startsWith("techreborn.proxies.ClientProxy")
                || className.startsWith("techreborn.events.FluidBlockModelHandler")) {
            return CLIENT_RESOURCE_METHODS;
        }
        if (className.startsWith("techreborn.world.config.")
                || className.startsWith("techreborn.world.TechRebornWorldGen")
                || className.startsWith("techreborn.world.TechRebornRetroGen")) {
            return INIT_METHODS;
        }
        return null;
    }

    private static Map<String, Set<String>> createExactTargets() {
        Map<String, Set<String>> targets = new HashMap<>();
        targets.put("techreborn.Core", set(
                "<clinit>", "<init>", "preinit", "init", "postinit", "serverStarting", "registerItems", "registerRecipes", "LoadPackets"));
        targets.put("techreborn.TechReborn", set("<clinit>", "<init>", "preInit", "init", "postInit", "serverStarting"));
        targets.put("techreborn.proxies.CommonProxy", set("<clinit>", "<init>", "preInit", "init", "postInit", "registerRenderers", "registerModels"));
        targets.put("techreborn.proxies.ClientProxy", CLIENT_RESOURCE_METHODS);
        targets.put("techreborn.config.ConfigTechReborn", set("<clinit>", "<init>"));
        targets.put("techreborn.init.ModBlocks", INIT_METHODS);
        targets.put("techreborn.init.ModItems", INIT_METHODS);
        targets.put("techreborn.init.ModFluids", INIT_METHODS);
        targets.put("techreborn.init.ModRecipes", RECIPE_METHODS);
        targets.put("techreborn.init.ModTileEntities", set("<clinit>", "<init>", "init", "initDataFixer", "getFromOldName"));
        targets.put("techreborn.init.ModSounds", set("<clinit>", "<init>", "init", "getSound"));
        targets.put("techreborn.init.ModLoot", INIT_METHODS);
        targets.put("techreborn.init.OreDict", set("<clinit>", "<init>", "init"));
        targets.put("techreborn.init.IC2Duplicates", set("<clinit>", "<init>", "deduplicate", "getStackBasedOnConfig", "isClassicMode", "isClassicalDedupe"));
        targets.put("techreborn.api.recipe.Recipes", set("<clinit>", "<init>"));
        targets.put("techreborn.api.TechRebornAPI", set("<clinit>", "<init>"));
        targets.put("techreborn.compat.CompatManager", COMPAT_METHODS);
        targets.put("techreborn.compat.CompatConfigs", COMPAT_METHODS);
        targets.put("techreborn.compat.CompatRegistryFactory", COMPAT_METHODS);
        targets.put("techreborn.client.RegisterItemJsons", CLIENT_RESOURCE_METHODS);
        targets.put("techreborn.client.render.ModelDynamicCell", CLIENT_RESOURCE_METHODS);
        targets.put("techreborn.client.render.ModelDynamicCell$DynamicCellLoader", CLIENT_RESOURCE_METHODS);
        targets.put("techreborn.client.render.ModelHelper", CLIENT_RESOURCE_METHODS);
        targets.put("techreborn.events.RegistryFix", set("<clinit>", "<init>", "fixBlocks", "fixItems", "fixRecipes"));
        targets.put("techreborn.events.TRRecipeHandler", set("<clinit>", "<init>", "hideEntry", "unlockTRRecipes", "isRecipeValid"));
        targets.put("reborncore.api.scriba.TileRegistrationManager", set("<clinit>", "<init>", "registerTiles"));
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
            MethodVisitor timed = new TimedMethodVisitor(visitor, "TR " + className + '.' + name + desc);
            if ("techreborn.Core".equals(className) && "preinit".equals(name)) {
                return new TechRebornPreinitCallSiteVisitor(timed);
            }
            if ("reborncore.api.scriba.TileRegistrationManager".equals(className) && "registerTiles".equals(name)) {
                return new RebornCoreTileRegistrationFastPathVisitor(new TileRegistrationCallSiteVisitor(timed));
            }
            return timed;
        }
    }

    private static final class RebornCoreTileRegistrationFastPathVisitor extends MethodVisitor {
        private RebornCoreTileRegistrationFastPathVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitCode() {
            super.visitCode();
            Label fallback = new Label();
            super.visitVarInsn(Opcodes.ALOAD, 0);
            super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "com/l/gpom/optimization/RebornCoreTileRegistrationOptimizations",
                    "tryRegisterTechRebornTiles",
                    "(Ljava/lang/Object;)Z",
                    false
            );
            super.visitJumpInsn(Opcodes.IFEQ, fallback);
            super.visitInsn(Opcodes.RETURN);
            super.visitLabel(fallback);
            super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
        }
    }

    private static final class TechRebornPreinitCallSiteVisitor extends MethodVisitor {
        private TechRebornPreinitCallSiteVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = preinitLabel(owner, name, desc);
            if (label == null) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private static String preinitLabel(String owner, String name, String desc) {
            if ("techreborn/init/ModBlocks".equals(owner) && "init".equals(name) && "()V".equals(desc)) {
                return "TR Core.preinit -> ModBlocks.init";
            }
            if ("reborncore/api/scriba/TileRegistrationManager".equals(owner) && "registerTiles".equals(name) && "()V".equals(desc)) {
                return "TR Core.preinit -> TileRegistrationManager.registerTiles";
            }
            if ("techreborn/init/ModTileEntities".equals(owner) && "init".equals(name) && "()V".equals(desc)) {
                return "TR Core.preinit -> ModTileEntities.init";
            }
            if ("techreborn/init/ModFluids".equals(owner) && "init".equals(name) && "()V".equals(desc)) {
                return "TR Core.preinit -> ModFluids.init";
            }
            if ("techreborn/init/ModTileEntities".equals(owner) && "initDataFixer".equals(name)) {
                return "TR Core.preinit -> ModTileEntities.initDataFixer";
            }
            if ("java/util/ArrayList".equals(owner) && "forEach".equals(name) && "(Ljava/util/function/Consumer;)V".equals(desc)) {
                return "TR Core.preinit -> compatModules.forEach";
            }
            if ("techreborn/proxies/CommonProxy".equals(owner) && "preInit".equals(name)) {
                return "TR Core.preinit -> CommonProxy.preInit";
            }
            if ("net/minecraftforge/fml/common/registry/EntityRegistry".equals(owner) && "registerModEntity".equals(name)) {
                return "TR Core.preinit -> EntityRegistry.registerModEntity";
            }
            return null;
        }
    }

    private static final class TileRegistrationCallSiteVisitor extends MethodVisitor {
        private TileRegistrationCallSiteVisitor(MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            String label = tileRegistrationLabel(owner, name, desc);
            if (label == null) {
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                return;
            }
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
            super.visitMethodInsn(opcode, owner, name, desc, itf);
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
        }

        private static String tileRegistrationLabel(String owner, String name, String desc) {
            if ("io/github/classgraph/ClassGraph".equals(owner) && "scan".equals(name)) {
                return "TR TileRegistrationManager.registerTiles ClassGraph.scan";
            }
            if ("io/github/classgraph/ScanResult".equals(owner) && "getClassesWithAnnotation".equals(name)) {
                return "TR TileRegistrationManager.registerTiles getClassesWithAnnotation";
            }
            if ("io/github/classgraph/ClassInfoList".equals(owner) && "filter".equals(name)) {
                return "TR TileRegistrationManager.registerTiles ClassInfoList.filter";
            }
            if ("io/github/classgraph/ClassInfoList".equals(owner) && "forEach".equals(name)) {
                return "TR TileRegistrationManager.registerTiles ClassInfoList.forEach";
            }
            return null;
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
