package com.l.gpom.core;

import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.fml.common.Loader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public final class BetterPortalsCompatibilityTransformer implements IClassTransformer {
    private static final String ENTITY_RENDERER_NO_OF = "de.johni0702.minecraft.view.impl.mixin.MixinEntityRenderer_NoOF";
    private static final String ENTITY_RENDERER_OF = "de.johni0702.minecraft.view.impl.mixin.MixinEntityRenderer_OF";
    private static final String BETTER_PORTALS_ROOT = "de.johni0702.minecraft.";
    private static final String KOTLIN_EXTENSIONS = ".ExtensionsKt";
    private static final String AETHER_PACKAGE = "de.johni0702.minecraft.betterportals.impl.aether.";
    private static final String AETHER_HAS_AETHER = "de.johni0702.minecraft.betterportals.impl.aether.common.ExtensionsKt$hasAether$2";
    private static final String THIS_INTERNAL = "com/l/gpom/core/BetterPortalsCompatibilityTransformer";
    private static final String LEGACY_AETHER_PREFIX = "com/legacy/aether/";
    private static final String CURRENT_AETHER_PREFIX = "com/gildedgames/the_aether/";
    private static final String LEGACY_AETHER_PORTAL = LEGACY_AETHER_PREFIX + "blocks/portal/BlockAetherPortal";
    private static final String CURRENT_AETHER_PORTAL = CURRENT_AETHER_PREFIX + "blocks/portal/BlockAetherPortal";
    private static final String FUTURES = "com/google/common/util/concurrent/Futures";
    private static final String MORE_EXECUTORS = "com/google/common/util/concurrent/MoreExecutors";
    private static final String OLD_ADD_CALLBACK_DESC = "(Lcom/google/common/util/concurrent/ListenableFuture;Lcom/google/common/util/concurrent/FutureCallback;)V";
    private static final String NEW_ADD_CALLBACK_DESC = "(Lcom/google/common/util/concurrent/ListenableFuture;Lcom/google/common/util/concurrent/FutureCallback;Ljava/util/concurrent/Executor;)V";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String FRUSTUM_TARGET = "net.minecraft.client.renderer.culling.Frustum";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }

        String className = normalizedClassName(transformedName != null ? transformedName : name);
        boolean patchEntityRenderer = GpomEarlyConfig.betterPortalsMissingNewTargetFixEnabled()
                && (ENTITY_RENDERER_NO_OF.equals(className) || ENTITY_RENDERER_OF.equals(className));
        boolean patchLegacyAether = GpomEarlyConfig.betterPortalsSkipLegacyAetherBridgeIfMissingEnabled()
                && AETHER_HAS_AETHER.equals(className);
        boolean patchGuavaAddCallback = GpomEarlyConfig.betterPortalsGuavaAddCallbackFixEnabled()
                && isBetterPortalsExtensionsClass(className);
        boolean remapLegacyAether = GpomEarlyConfig.betterPortalsRemapLegacyAetherBridgeEnabled()
                && isBetterPortalsAetherClass(className)
                && shouldRemapBetterPortalsAetherBridge();
        if (!patchEntityRenderer && !patchLegacyAether && !patchGuavaAddCallback && !remapLegacyAether) {
            return basicClass;
        }

        try {
            boolean changed = false;
            byte[] transformed = basicClass;
            if (patchEntityRenderer || patchLegacyAether || patchGuavaAddCallback) {
                ClassNode node = new ClassNode();
                new ClassReader(transformed).accept(node, 0);
                if (patchEntityRenderer) {
                    for (MethodNode method : node.methods) {
                        changed |= patchMethodAnnotations(method);
                    }
                }
                if (patchLegacyAether) {
                    for (MethodNode method : node.methods) {
                        changed |= patchLegacyAetherProbe(method);
                    }
                }
                if (patchGuavaAddCallback) {
                    for (MethodNode method : node.methods) {
                        changed |= patchGuavaAddCallback(method);
                    }
                }
                if (changed) {
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    transformed = writer.toByteArray();
                }
            }
            if (remapLegacyAether) {
                transformed = remapLegacyAetherBridge(transformed);
                changed = true;
            }
            return changed ? transformed : basicClass;
        } catch (Throwable ignored) {
            return basicClass;
        }
    }

    public static boolean isBetterPortalsAetherBridgeAvailable() {
        if (!isModLoaded("aether_legacy")) {
            return false;
        }
        if (classResourceExists(LEGACY_AETHER_PORTAL)) {
            return true;
        }
        return GpomEarlyConfig.betterPortalsRemapLegacyAetherBridgeEnabled()
                && classResourceExists(CURRENT_AETHER_PORTAL);
    }

    private static boolean shouldRemapBetterPortalsAetherBridge() {
        return !classResourceExists(LEGACY_AETHER_PORTAL)
                && classResourceExists(CURRENT_AETHER_PORTAL);
    }

    private static boolean isModLoaded(String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean classResourceExists(String internalName) {
        String resource = internalName + ".class";
        ClassLoader ownLoader = BetterPortalsCompatibilityTransformer.class.getClassLoader();
        if (ownLoader != null && ownLoader.getResource(resource) != null) {
            return true;
        }
        if (Launch.classLoader != null && Launch.classLoader.getResource(resource) != null) {
            return true;
        }
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        return contextLoader != null && contextLoader.getResource(resource) != null;
    }

    private static boolean isBetterPortalsAetherClass(String className) {
        return className != null && className.startsWith(AETHER_PACKAGE);
    }

    private static boolean isBetterPortalsExtensionsClass(String className) {
        return className != null
                && className.startsWith(BETTER_PORTALS_ROOT)
                && className.endsWith(KOTLIN_EXTENSIONS);
    }

    private static byte[] remapLegacyAetherBridge(byte[] basicClass) {
        ClassReader reader = new ClassReader(basicClass);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassRemapper(writer, new Remapper() {
            @Override
            public String map(String internalName) {
                if (internalName != null && internalName.startsWith(LEGACY_AETHER_PREFIX)) {
                    return CURRENT_AETHER_PREFIX + internalName.substring(LEGACY_AETHER_PREFIX.length());
                }
                return internalName;
            }
        }), 0);
        return writer.toByteArray();
    }

    private static String normalizedClassName(String className) {
        return className == null ? null : className.replace('/', '.');
    }

    private static boolean patchMethodAnnotations(MethodNode method) {
        if (method == null || method.visibleAnnotations == null) {
            return false;
        }

        boolean changed = false;
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (annotation == null || !REDIRECT.equals(annotation.desc)) {
                continue;
            }
            AnnotationNode at = nestedAnnotation(annotation, "at");
            if (isNewInjectionPoint(at) && stringValue(at, "target") == null) {
                putValue(at, "target", FRUSTUM_TARGET);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean patchLegacyAetherProbe(MethodNode method) {
        if (method == null || !"invoke".equals(method.name) || !"()Z".equals(method.desc)) {
            return false;
        }

        method.tryCatchBlocks.clear();
        if (method.localVariables != null) {
            method.localVariables.clear();
        }
        while (method.instructions.size() > 0) {
            method.instructions.remove(method.instructions.getFirst());
        }

        InsnList replacement = new InsnList();
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                THIS_INTERNAL,
                "isBetterPortalsAetherBridgeAvailable",
                "()Z",
                false
        ));
        replacement.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(replacement);
        method.maxStack = 1;
        method.maxLocals = 1;
        return true;
    }

    private static boolean patchGuavaAddCallback(MethodNode method) {
        if (method == null || !"logFailure".equals(method.name)) {
            return false;
        }

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }

            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !FUTURES.equals(call.owner)
                    || !"addCallback".equals(call.name)
                    || !OLD_ADD_CALLBACK_DESC.equals(call.desc)) {
                continue;
            }

            method.instructions.insertBefore(call, new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    MORE_EXECUTORS,
                    "directExecutor",
                    "()Ljava/util/concurrent/Executor;",
                    false
            ));
            call.desc = NEW_ADD_CALLBACK_DESC;
            return true;
        }
        return false;
    }

    private static boolean isNewInjectionPoint(AnnotationNode annotation) {
        return annotation != null
                && AT.equals(annotation.desc)
                && "NEW".equals(stringValue(annotation, "value"));
    }

    private static AnnotationNode nestedAnnotation(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        return value instanceof AnnotationNode ? (AnnotationNode) value : null;
    }

    private static String stringValue(AnnotationNode annotation, String key) {
        Object value = value(annotation, key);
        if (!(value instanceof String)) {
            return null;
        }
        String string = (String) value;
        return string.isEmpty() ? null : string;
    }

    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null || key == null) {
            return null;
        }
        for (int index = 0; index + 1 < annotation.values.size(); index += 2) {
            Object name = annotation.values.get(index);
            if (key.equals(name)) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static void putValue(AnnotationNode annotation, String key, Object value) {
        if (annotation.values == null) {
            annotation.values = new ArrayList<>();
        }
        List<Object> values = annotation.values;
        for (int index = 0; index + 1 < values.size(); index += 2) {
            if (key.equals(values.get(index))) {
                values.set(index + 1, value);
                return;
            }
        }
        values.add(key);
        values.add(value);
    }

}
