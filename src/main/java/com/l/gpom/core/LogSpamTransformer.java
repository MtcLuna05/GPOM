package com.l.gpom.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LogSpamTransformer implements IClassTransformer {
    private static final boolean UCW_TEXTURE_STITCH_STDOUT = Boolean.parseBoolean(System.getProperty(
            "gpom.ucw.suppressTextureStitchStdout",
            "true"
    ));
    private static final boolean VINTAGEFIX_MODEL_ERROR_SPAM = Boolean.parseBoolean(System.getProperty(
            "gpom.vintageFix.suppressUcwModelErrorSpam",
            "true"
    ));
    private static final boolean VINTAGEFIX_SKIP_UCW_DEFINITION_EARLY_MODEL_LOAD = Boolean.parseBoolean(System.getProperty(
            "gpom.vintageFix.skipUcwDefinitionEarlyModelLoad",
            "true"
    ));
    private static final boolean CRAFTTWEAKER_FUNCTION_TYPE_STDOUT = Boolean.parseBoolean(System.getProperty(
            "gpom.crafttweaker.suppressFunctionTypeStdout",
            "true"
    ));
    private static final boolean CTM_UNKNOWN_RENDER_LAYER = Boolean.parseBoolean(System.getProperty(
            "gpom.ctm.tolerateUnknownRenderLayer",
            "true"
    ));
    private static final boolean CTM_TEXTURE_METADATA_ERROR_SPAM = Boolean.parseBoolean(System.getProperty(
            "gpom.ctm.suppressTextureMetadataErrorSpam",
            "true"
    ));

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = transformedName != null ? transformedName : name;
        if (UCW_TEXTURE_STITCH_STDOUT
                && !(className != null && className.startsWith("com.l.gpom."))
                && "pl.asie.ucw.UCWProxyClient".equals(className)
                && TargetedModVersions.isUnlimitedChiselWorksClass(className)) {
            return patchUcwTextureStitchSpam(basicClass);
        }
        if (VINTAGEFIX_SKIP_UCW_DEFINITION_EARLY_MODEL_LOAD
                && "org.embeddedt.vintagefix.mixin.dynamic_resources.MixinModelManager".equals(className)
                && TargetedModVersions.isVintageFixClass(className)) {
            return patchVintageFixEarlyModelPathFilter(basicClass);
        }
        if (VINTAGEFIX_MODEL_ERROR_SPAM
                && "org.embeddedt.vintagefix.mixin.dynamic_resources.MixinModelLoaderEarlyView".equals(className)
                && TargetedModVersions.isVintageFixClass(className)) {
            return patchVintageFixUcwModelErrorSpam(basicClass);
        }
        if (VINTAGEFIX_MODEL_ERROR_SPAM
                && "org.embeddedt.vintagefix.dynamicresources.model.DynamicModelProvider".equals(className)
                && TargetedModVersions.isVintageFixClass(className)) {
            return patchVintageFixDynamicModelProviderSpam(basicClass);
        }
        if (VINTAGEFIX_MODEL_ERROR_SPAM
                && "org.embeddedt.vintagefix.dynamicresources.model.DynamicBakedModelProvider".equals(className)
                && TargetedModVersions.isVintageFixClass(className)) {
            return patchVintageFixDynamicBakedModelProviderSpam(basicClass);
        }
        if (CRAFTTWEAKER_FUNCTION_TYPE_STDOUT
                && "stanhebben.zenscript.parser.expression.ParsedExpressionFunction".equals(className)) {
            return patchCraftTweakerFunctionTypeStdout(basicClass);
        }
        if (CTM_UNKNOWN_RENDER_LAYER
                && "team.chisel.ctm.client.texture.IMetadataSectionCTM$V1".equals(className)
                && TargetedModVersions.isConnectedTexturesModClass(className)) {
            return patchCtmUnknownRenderLayer(basicClass);
        }
        if (CTM_TEXTURE_METADATA_ERROR_SPAM
                && "team.chisel.ctm.client.util.TextureMetadataHandler".equals(className)
                && TargetedModVersions.isConnectedTexturesModClass(className)) {
            return patchCtmTextureMetadataSpam(basicClass);
        }

        return basicClass;
    }

    private static byte[] patchCtmUnknownRenderLayer(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("fromJson".equals(method.name)
                        && "(Lcom/google/gson/JsonObject;)Lteam/chisel/ctm/client/texture/IMetadataSectionCTM;".equals(method.desc)) {
                    changed |= replaceCtmRenderLayerValueOf(method);
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

    private static boolean replaceCtmRenderLayerValueOf(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if ("net/minecraft/util/BlockRenderLayer".equals(methodInsn.owner)
                        && "valueOf".equals(methodInsn.name)
                        && "(Ljava/lang/String;)Lnet/minecraft/util/BlockRenderLayer;".equals(methodInsn.desc)) {
                    methodInsn.owner = "com/l/gpom/optimization/ModelLogSpamSuppressor";
                    methodInsn.name = "ctmBlockRenderLayerValueOf";
                    methodInsn.desc = "(Ljava/lang/String;)Lnet/minecraft/util/BlockRenderLayer;";
                    methodInsn.itf = false;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static byte[] patchCtmTextureMetadataSpam(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("onTextureStitch".equals(method.name)
                        && "(Lteam/chisel/ctm/api/event/TextureCollectedEvent;)V".equals(method.desc)) {
                    changed |= replaceCtmTextureMetadataPrintStackTrace(method);
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

    private static boolean replaceCtmTextureMetadataPrintStackTrace(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                if ("java/io/IOException".equals(methodInsn.owner)
                        && "printStackTrace".equals(methodInsn.name)
                        && "()V".equals(methodInsn.desc)) {
                    method.instructions.set(insn, new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ModelLogSpamSuppressor",
                            "suppressCtmTextureMetadataError",
                            "(Ljava/lang/Throwable;)V",
                            false
                    ));
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static byte[] patchVintageFixEarlyModelPathFilter(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("lambda$doEarlyModelLoading$0".equals(method.name)
                        && "(Ljava/lang/String;)Z".equals(method.desc)) {
                    InsnList guard = new InsnList();
                    LabelNode continueLabel = new LabelNode();
                    guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    guard.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ModelLogSpamSuppressor",
                            "isVintageFixUcwDefinitionPath",
                            "(Ljava/lang/String;)Z",
                            false
                    ));
                    guard.add(new JumpInsnNode(Opcodes.IFEQ, continueLabel));
                    guard.add(new InsnNode(Opcodes.ICONST_0));
                    guard.add(new InsnNode(Opcodes.IRETURN));
                    guard.add(continueLabel);
                    method.instructions.insert(guard);
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

    private static byte[] patchCraftTweakerFunctionTypeStdout(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("compile".equals(method.name)
                        && "(Lstanhebben/zenscript/compiler/IEnvironmentMethod;Lstanhebben/zenscript/type/ZenType;)Lstanhebben/zenscript/expression/partial/IPartialExpression;".equals(method.desc)) {
                    changed |= removePrintlnBlocks(method);
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

    private static byte[] patchUcwTextureStitchSpam(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("onTextureStitchPre".equals(method.name)
                        && "(Lnet/minecraftforge/client/event/TextureStitchEvent$Pre;)V".equals(method.desc)) {
                    changed |= removePrintlnBlocks(method);
                    changed |= replaceUcwRemappingErrors(method);
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

    private static boolean replaceUcwRemappingErrors(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isLoggerErrorThrowable(insn)) {
                AbstractInsnNode start = findStaticLoggerStart(insn, "pl/asie/ucw/UnlimitedChiselWorks");
                int throwableLocal = previousAloadVar(insn);
                if (start != null && throwableLocal >= 0) {
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 28));
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, throwableLocal));
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ModelLogSpamSuppressor",
                            "suppressVintageFixDynamicModelError",
                            "(Ljava/lang/Object;Ljava/lang/Throwable;)V",
                            false
                    ));
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static boolean removePrintlnBlocks(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isPrintln(insn)) {
                FieldInsnNode start = findSystemOutStart(insn);
                if (start != null) {
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static byte[] patchVintageFixDynamicBakedModelProviderSpam(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("lambda$static$0".equals(method.name)
                        && "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;".equals(method.desc)) {
                    changed |= replaceVintageFixMissingTextureWarnings(method);
                }
                changed |= replaceVintageFixBakedModelErrors(method);
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

    private static boolean replaceVintageFixBakedModelErrors(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isVintageFixBakedModelErrorLog(insn)) {
                AbstractInsnNode start = findStaticLoggerStart(insn, "org/embeddedt/vintagefix/dynamicresources/model/DynamicBakedModelProvider");
                if (start != null) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    if ("(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodInsn.desc)) {
                        int throwableLocal = previousAloadVar(insn);
                        if (throwableLocal < 0) {
                            insn = next;
                            continue;
                        }
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, throwableLocal));
                    } else {
                        replacement.add(new InsnNode(Opcodes.ACONST_NULL));
                    }
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ModelLogSpamSuppressor",
                            "suppressVintageFixDynamicModelError",
                            "(Ljava/lang/Object;Ljava/lang/Throwable;)V",
                            false
                    ));
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static boolean replaceVintageFixMissingTextureWarnings(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isVintageFixMissingTextureWarn(insn)) {
                AbstractInsnNode start = findStaticLoggerStart(insn, "org/embeddedt/vintagefix/dynamicresources/model/DynamicBakedModelProvider");
                if (start != null) {
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ModelLogSpamSuppressor",
                            "suppressVintageFixMissingTexture",
                            "(Ljava/lang/Object;)V",
                            false
                    ));
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static byte[] patchVintageFixUcwModelErrorSpam(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("useVintageSecretSauce".equals(method.name)
                        && "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;".equals(method.desc)) {
                    changed |= replaceVintageFixLoggerError(method);
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

    private static boolean replaceVintageFixLoggerError(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isVintageFixUcwModelErrorLog(insn)) {
                AbstractInsnNode start = findStaticLoggerStart(insn, "org/embeddedt/vintagefix/VintageFix");
                if (start != null) {
                    InsnList replacement = new InsnList();
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    replacement.add(new VarInsnNode(Opcodes.ALOAD, 3));
                    replacement.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/l/gpom/optimization/ModelLogSpamSuppressor",
                            "suppressVintageFixUcwModelError",
                            "(Ljava/lang/Object;Ljava/lang/Throwable;)V",
                            false
                    ));
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static byte[] patchVintageFixDynamicModelProviderSpam(byte[] basicClass) {
        try {
            ClassNode node = new ClassNode();
            new ClassReader(basicClass).accept(node, 0);
            boolean changed = false;
            for (MethodNode method : node.methods) {
                if ("loadModelFromBlockstateOrInventory".equals(method.name)
                        && "(Lnet/minecraft/util/ResourceLocation;)Lnet/minecraftforge/client/model/IModel;".equals(method.desc)) {
                    changed |= replaceVintageFixDynamicModelProviderErrors(method);
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

    private static boolean replaceVintageFixDynamicModelProviderErrors(MethodNode method) {
        boolean changed = false;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (isVintageFixDynamicModelErrorLog(insn)) {
                AbstractInsnNode start = findStaticLoggerStart(insn, "org/embeddedt/vintagefix/dynamicresources/model/DynamicModelProvider");
                if (start != null) {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    InsnList replacement = new InsnList();
                    if ("(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodInsn.desc)) {
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        replacement.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/ModelLogSpamSuppressor",
                                "suppressVintageFixDynamicModelError",
                                "(Ljava/lang/Object;Ljava/lang/Throwable;)V",
                                false
                        ));
                    } else {
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        replacement.add(new VarInsnNode(Opcodes.ALOAD, 4));
                        replacement.add(new MethodInsnNode(
                                Opcodes.INVOKESTATIC,
                                "com/l/gpom/optimization/ModelLogSpamSuppressor",
                                "suppressVintageFixDynamicModelItemError",
                                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Throwable;)V",
                                false
                        ));
                    }
                    method.instructions.insertBefore(start, replacement);
                    AbstractInsnNode cursor = start;
                    while (cursor != next) {
                        AbstractInsnNode remove = cursor;
                        cursor = cursor.getNext();
                        method.instructions.remove(remove);
                    }
                    changed = true;
                }
            }
            insn = next;
        }
        return changed;
    }

    private static boolean isPrintln(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEVIRTUAL) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "java/io/PrintStream".equals(methodInsn.owner)
                && "println".equals(methodInsn.name)
                && "(Ljava/lang/String;)V".equals(methodInsn.desc);
    }

    private static boolean isVintageFixUcwModelErrorLog(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "org/apache/logging/log4j/Logger".equals(methodInsn.owner)
                && "error".equals(methodInsn.name)
                && "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodInsn.desc);
    }

    private static boolean isVintageFixDynamicModelErrorLog(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "org/apache/logging/log4j/Logger".equals(methodInsn.owner)
                && "error".equals(methodInsn.name)
                && ("(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodInsn.desc)
                || "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodInsn.desc));
    }

    private static boolean isVintageFixBakedModelErrorLog(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "org/apache/logging/log4j/Logger".equals(methodInsn.owner)
                && "error".equals(methodInsn.name)
                && ("(Ljava/lang/String;Ljava/lang/Object;)V".equals(methodInsn.desc)
                || "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V".equals(methodInsn.desc))
                && (hasNearbyPreviousLdc(insn, "Error occured while loading model {}", 8)
                || hasNearbyPreviousLdc(insn, "Failed to load model {}", 8)
                || hasNearbyPreviousLdc(insn, "Error occured while baking model {}", 8));
    }

    private static boolean isLoggerErrorThrowable(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "org/apache/logging/log4j/Logger".equals(methodInsn.owner)
                && "error".equals(methodInsn.name)
                && "(Ljava/lang/String;Ljava/lang/Throwable;)V".equals(methodInsn.desc);
    }

    private static boolean isVintageFixMissingTextureWarn(AbstractInsnNode insn) {
        if (!(insn instanceof MethodInsnNode) || insn.getOpcode() != Opcodes.INVOKEINTERFACE) {
            return false;
        }
        MethodInsnNode methodInsn = (MethodInsnNode) insn;
        return "org/apache/logging/log4j/Logger".equals(methodInsn.owner)
                && "warn".equals(methodInsn.name)
                && "(Ljava/lang/String;Ljava/lang/Object;)V".equals(methodInsn.desc)
                && hasPreviousLdc(insn, "Texture {} was not discovered during texture pass");
    }

    private static boolean hasPreviousLdc(AbstractInsnNode insn, String value) {
        for (AbstractInsnNode cursor = insn.getPrevious(); cursor != null; cursor = cursor.getPrevious()) {
            if (cursor instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode) cursor).cst;
                return value.equals(constant);
            }
            if (cursor instanceof MethodInsnNode || cursor instanceof FieldInsnNode) {
                return false;
            }
        }
        return false;
    }

    private static boolean hasNearbyPreviousLdc(AbstractInsnNode insn, String value, int maxInstructions) {
        int scanned = 0;
        for (AbstractInsnNode cursor = insn.getPrevious(); cursor != null && scanned++ < maxInstructions; cursor = cursor.getPrevious()) {
            if (cursor instanceof LdcInsnNode && value.equals(((LdcInsnNode) cursor).cst)) {
                return true;
            }
        }
        return false;
    }

    private static FieldInsnNode findSystemOutStart(AbstractInsnNode println) {
        for (AbstractInsnNode cursor = println.getPrevious(); cursor != null; cursor = cursor.getPrevious()) {
            if (cursor instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) cursor;
                if (fieldInsn.getOpcode() == Opcodes.GETSTATIC
                        && "java/lang/System".equals(fieldInsn.owner)
                        && "out".equals(fieldInsn.name)
                        && "Ljava/io/PrintStream;".equals(fieldInsn.desc)) {
                    return fieldInsn;
                }
            }
        }
        return null;
    }

    private static FieldInsnNode findStaticLoggerStart(AbstractInsnNode loggerCall, String owner) {
        for (AbstractInsnNode cursor = loggerCall.getPrevious(); cursor != null; cursor = cursor.getPrevious()) {
            if (cursor instanceof FieldInsnNode) {
                FieldInsnNode fieldInsn = (FieldInsnNode) cursor;
                if (fieldInsn.getOpcode() == Opcodes.GETSTATIC
                        && owner.equals(fieldInsn.owner)
                        && "LOGGER".equals(fieldInsn.name)
                        && "Lorg/apache/logging/log4j/Logger;".equals(fieldInsn.desc)) {
                    return fieldInsn;
                }
            }
        }
        return null;
    }

    private static int previousAloadVar(AbstractInsnNode insn) {
        AbstractInsnNode previous = insn.getPrevious();
        if (previous instanceof VarInsnNode && previous.getOpcode() == Opcodes.ALOAD) {
            return ((VarInsnNode) previous).var;
        }
        return -1;
    }
}
