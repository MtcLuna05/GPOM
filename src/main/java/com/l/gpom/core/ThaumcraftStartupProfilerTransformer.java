package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ThaumcraftStartupProfilerTransformer implements IClassTransformer {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.thaumcraftProfiler", "true"));
    private static final boolean ASPECT_EVENT_PROFILER = Boolean.parseBoolean(System.getProperty("gpom.thaumcraftProfiler.aspectEvents", "true"));
    private static final Map<String, Set<MethodKey>> TARGETS = createTargets();

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENABLED || basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (className == null || (className != null && className.startsWith("com.l.gpom."))) {
            return basicClass;
        }
        boolean supportedThaumcraftClass = className != null && className.startsWith("thaumcraft.") && TargetedModVersions.isThaumcraftClass(className);
        boolean supportedThaumcraftAvailable = TargetedModVersions.isThaumcraftTargetAvailable();
        Set<MethodKey> methods = TARGETS.get(className);
        boolean targetedPackage = className != null
                && (supportedThaumcraftClass
                || (supportedThaumcraftAvailable && className.startsWith("vazkii.botania.common.integration.thaumcraft.")));
        boolean possibleAspectEventHandler = ASPECT_EVENT_PROFILER
                && supportedThaumcraftAvailable
                && containsAscii(basicClass, "thaumcraft/api/aspects/AspectRegistryEvent");
        if (methods != null && !targetedPackage) {
            methods = null;
        }
        if (methods == null && !targetedPackage && !possibleAspectEventHandler) {
            return basicClass;
        }

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
            ThaumcraftClassVisitor visitor = new ThaumcraftClassVisitor(writer, className, methods);
            reader.accept(visitor, 0);
            return visitor.changed ? writer.toByteArray() : basicClass;
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    private static Map<String, Set<MethodKey>> createTargets() {
        Map<String, Set<MethodKey>> targets = new HashMap<>();

        add(targets, "thaumcraft.proxies.CommonProxy", "postInit", "(Lnet/minecraftforge/fml/common/event/FMLPostInitializationEvent;)V");

        add(targets, "thaumcraft.api.ThaumcraftApi", "exists", "(Lnet/minecraft/item/ItemStack;)Z");

        add(targets, "thaumcraft.common.config.ConfigAspects", "postInit", "()V");
        add(targets, "thaumcraft.common.config.ConfigAspects", "registerItemAspects", "()V");
        add(targets, "thaumcraft.common.config.ConfigAspects", "registerEntityAspects", "()V");

        add(targets, "thaumcraft.api.aspects.AspectEventProxy", "registerObjectTag", "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)V");
        add(targets, "thaumcraft.api.aspects.AspectEventProxy", "registerObjectTag", "(Ljava/lang/String;Lthaumcraft/api/aspects/AspectList;)V");
        add(targets, "thaumcraft.api.aspects.AspectEventProxy", "registerComplexObjectTag", "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)V");
        add(targets, "thaumcraft.api.aspects.AspectEventProxy", "registerComplexObjectTag", "(Ljava/lang/String;Lthaumcraft/api/aspects/AspectList;)V");

        add(targets, "thaumcraft.api.aspects.AspectHelper", "getObjectAspects", "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.api.aspects.AspectHelper", "generateTags", "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;");

        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "getObjectTags", "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "getObjectTags", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "generateTags", "(Lnet/minecraft/item/ItemStack;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "generateTags", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "generateTagsFromCrucibleRecipes", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "generateTagsFromInfusionRecipes", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "generateTagsFromCraftingRecipes", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "getAspectsFromIngredients", "(Lnet/minecraft/util/NonNullList;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/crafting/IRecipe;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");
        add(targets, "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager", "generateTagsFromRecipes", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;");

        add(targets, "vazkii.botania.common.integration.thaumcraft.TCAspects", "registerAspects", "()V");
        add(targets, "vazkii.botania.common.integration.thaumcraft.TCAspects", "registerItemAspects", "()V");
        add(targets, "vazkii.botania.common.integration.thaumcraft.TCAspects", "registerComplex", "(Lnet/minecraft/item/ItemStack;Lthaumcraft/api/aspects/AspectList;)V");

        return targets;
    }

    private static void add(Map<String, Set<MethodKey>> targets, String className, String methodName, String descriptor) {
        targets.computeIfAbsent(className, ignored -> new HashSet<>()).add(new MethodKey(methodName, descriptor));
    }

    private static boolean containsAscii(byte[] bytes, String needle) {
        if (bytes == null || needle == null || needle.isEmpty() || bytes.length < needle.length()) {
            return false;
        }
        int limit = bytes.length - needle.length();
        int first = needle.charAt(0);
        for (int i = 0; i <= limit; i++) {
            if ((bytes[i] & 0xFF) != first) {
                continue;
            }
            int j = 1;
            while (j < needle.length() && (bytes[i + j] & 0xFF) == needle.charAt(j)) {
                j++;
            }
            if (j == needle.length()) {
                return true;
            }
        }
        return false;
    }

    private static final class ThaumcraftClassVisitor extends ClassVisitor {
        private final String className;
        private final Set<MethodKey> methods;
        private boolean changed;

        private ThaumcraftClassVisitor(ClassVisitor delegate, String className, Set<MethodKey> methods) {
            super(Opcodes.ASM9, delegate);
            this.className = className;
            this.methods = methods;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            if (patchExists(className, name, desc)) {
                changed = true;
                return null;
            }
            if (patchCraftingRecipeScan(className, name, desc)) {
                changed = true;
                return null;
            }

            MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (visitor == null) {
                return null;
            }

            if (methods != null && methods.contains(new MethodKey(name, desc))) {
                changed = true;
                return new TimedMethodVisitor(visitor, "THAUM " + className + '.' + name, cacheScope(className, name, desc), generatedTagCache(className, name, desc));
            }
            if (ASPECT_EVENT_PROFILER && desc != null && desc.contains("Lthaumcraft/api/aspects/AspectRegistryEvent;")) {
                changed = true;
                return new TimedMethodVisitor(visitor, "THAUM aspect event " + className + '.' + name, false, false);
            }
            return visitor;
        }

        @Override
        public void visitEnd() {
            if (methods != null && methods.contains(new MethodKey("exists", "(Lnet/minecraft/item/ItemStack;)Z"))
                    && "thaumcraft.api.ThaumcraftApi".equals(className)) {
                MethodVisitor visitor = super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "exists", "(Lnet/minecraft/item/ItemStack;)Z", null, null);
                visitor.visitCode();
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/optimization/ThaumcraftAspectCache", "objectTagExists", "(Ljava/lang/Object;)Z", false);
                visitor.visitInsn(Opcodes.IRETURN);
                visitor.visitMaxs(1, 1);
                visitor.visitEnd();
            }
            if (methods != null && methods.contains(new MethodKey("generateTagsFromCraftingRecipes", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;"))
                    && "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager".equals(className)) {
                MethodVisitor visitor = super.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "generateTagsFromCraftingRecipes", "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;", null, null);
                visitor.visitCode();
                String label = "THAUM " + className + ".generateTagsFromCraftingRecipes";
                visitor.visitLdcInsn(label);
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
                visitor.visitVarInsn(Opcodes.ALOAD, 0);
                visitor.visitVarInsn(Opcodes.ALOAD, 1);
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/optimization/ThaumcraftRecipeIndex", "generateTagsFromCraftingRecipes", "(Ljava/lang/Object;Ljava/util/ArrayList;)Ljava/lang/Object;", false);
                visitor.visitTypeInsn(Opcodes.CHECKCAST, "thaumcraft/api/aspects/AspectList");
                visitor.visitLdcInsn(label);
                visitor.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                visitor.visitInsn(Opcodes.ARETURN);
                visitor.visitMaxs(2, 2);
                visitor.visitEnd();
            }
            super.visitEnd();
        }

        private static boolean patchExists(String className, String name, String desc) {
            return "thaumcraft.api.ThaumcraftApi".equals(className)
                    && "exists".equals(name)
                    && "(Lnet/minecraft/item/ItemStack;)Z".equals(desc);
        }

        private static boolean patchCraftingRecipeScan(String className, String name, String desc) {
            return "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager".equals(className)
                    && "generateTagsFromCraftingRecipes".equals(name)
                    && "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;".equals(desc);
        }

        private static boolean cacheScope(String className, String name, String desc) {
            return "thaumcraft.common.config.ConfigAspects".equals(className)
                    && "postInit".equals(name)
                    && "()V".equals(desc);
        }

        private static boolean generatedTagCache(String className, String name, String desc) {
            return "thaumcraft.common.lib.crafting.ThaumcraftCraftingManager".equals(className)
                    && "generateTags".equals(name)
                    && "(Lnet/minecraft/item/ItemStack;Ljava/util/ArrayList;)Lthaumcraft/api/aspects/AspectList;".equals(desc);
        }
    }

    private static final class TimedMethodVisitor extends MethodVisitor {
        private final String label;
        private final boolean cacheScope;
        private final boolean generatedTagCache;
        private boolean entered;

        private TimedMethodVisitor(MethodVisitor delegate, String label, boolean cacheScope, boolean generatedTagCache) {
            super(Opcodes.ASM9, delegate);
            this.label = label;
            this.cacheScope = cacheScope;
            this.generatedTagCache = generatedTagCache;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            entered = true;
            super.visitLdcInsn(label);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "beginNamedProbe", "(Ljava/lang/String;)V", false);
            if (cacheScope) {
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/optimization/ThaumcraftAspectCache", "beginPostInit", "()V", false);
            }
            if (generatedTagCache) {
                Label miss = new Label();
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitVarInsn(Opcodes.ALOAD, 1);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/optimization/ThaumcraftAspectCache", "getGenerated", "(Ljava/lang/Object;Ljava/util/ArrayList;)Ljava/lang/Object;", false);
                super.visitInsn(Opcodes.DUP);
                super.visitJumpInsn(Opcodes.IFNULL, miss);
                super.visitTypeInsn(Opcodes.CHECKCAST, "thaumcraft/api/aspects/AspectList");
                super.visitLdcInsn(label);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/profiling/StartupProfiler", "endNamedProbe", "(Ljava/lang/String;)V", false);
                super.visitInsn(Opcodes.ARETURN);
                super.visitLabel(miss);
                super.visitInsn(Opcodes.POP);
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (entered && isExit(opcode)) {
                if (generatedTagCache && opcode == Opcodes.ARETURN) {
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitVarInsn(Opcodes.ALOAD, 1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/optimization/ThaumcraftAspectCache", "storeGenerated", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/ArrayList;)Ljava/lang/Object;", false);
                    super.visitTypeInsn(Opcodes.CHECKCAST, "thaumcraft/api/aspects/AspectList");
                }
                if (cacheScope) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/l/gpom/optimization/ThaumcraftAspectCache", "endPostInit", "()V", false);
                }
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
