package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import top.outlands.foundation.boot.TransformerHolder;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ForgeEventSubscriptionTransformerOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(
            "gpom.forge.fastEventSubscriptionTransformer",
            "false"
    ));
    private static final String FORGE_TRANSFORMER = "net.minecraftforge.fml.common.asm.transformers.EventSubscriptionTransformer";
    private static final String EVENT_CLASS = "net/minecraftforge/fml/common/eventhandler/Event";
    private static final String EVENT_CLASS_BINARY = "net.minecraftforge.fml.common.eventhandler.Event";
    private static volatile boolean installed;

    private ForgeEventSubscriptionTransformerOptimizations() {
    }

    public static synchronized void install() {
        if (!ENABLED || installed) {
            return;
        }

        List<IClassTransformer> transformers = TransformerHolder.transformers;
        if (transformers == null) {
            return;
        }

        for (int i = 0; i < transformers.size(); i++) {
            IClassTransformer transformer = transformers.get(i);
            if (transformer instanceof FastEventSubscriptionTransformer) {
                installed = true;
                return;
            }
            if (transformer != null && FORGE_TRANSFORMER.equals(transformer.getClass().getName())) {
                transformers.set(i, new FastEventSubscriptionTransformer(transformer));
                installed = true;
                GPOM.LOGGER.info("[FmlParallelLoading] Installed fast Forge EventSubscriptionTransformer classifier");
                return;
            }
        }
    }

    private static final class FastEventSubscriptionTransformer implements IClassTransformer {
        private final IClassTransformer delegate;
        private final ConcurrentMap<String, Boolean> eventSubclassCache = new ConcurrentHashMap<String, Boolean>();
        private final Set<String> resolving = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

        private FastEventSubscriptionTransformer(IClassTransformer delegate) {
            this.delegate = delegate;
            eventSubclassCache.put(EVENT_CLASS, Boolean.TRUE);
            eventSubclassCache.put("java/lang/Object", Boolean.FALSE);
        }

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass) {
            if (basicClass == null) {
                return basicClass;
            }

            String className = name != null ? name : transformedName;
            if (className == null
                    || className.startsWith("com.l.gpom.")
                    || className.startsWith("$wrapper.com.l.gpom.")
                    || EVENT_CLASS_BINARY.equals(className)
                    || className.startsWith("net.minecraft.")
                    || className.indexOf('.') == -1) {
                return basicClass;
            }

            try {
                String superName = readSuperName(basicClass);
                Boolean eventSubclass = isEventSubclass(superName);
                if (Boolean.FALSE.equals(eventSubclass)) {
                    return basicClass;
                }
            } catch (Throwable ignored) {
                // Unknown hierarchy means delegate to Forge's original transformer for safety.
            }

            return delegate.transform(name, transformedName, basicClass);
        }

        private Boolean isEventSubclass(String internalName) {
            if (internalName == null) {
                return Boolean.FALSE;
            }
            if (EVENT_CLASS.equals(internalName)) {
                return Boolean.TRUE;
            }
            if (internalName.startsWith("java/") || internalName.startsWith("net/minecraft/")) {
                return Boolean.FALSE;
            }

            Boolean cached = eventSubclassCache.get(internalName);
            if (cached != null) {
                return cached;
            }
            if (!resolving.add(internalName)) {
                return null;
            }
            try {
                byte[] bytes;
                try {
                    bytes = getRawClassBytes(internalName);
                } catch (IOException ignored) {
                    return null;
                }
                if (bytes == null) {
                    return null;
                }
                Boolean result = isEventSubclass(readSuperName(bytes));
                if (result != null) {
                    eventSubclassCache.put(internalName, result);
                }
                return result;
            } finally {
                resolving.remove(internalName);
            }
        }

        private byte[] getRawClassBytes(String internalName) throws IOException {
            if (!(Launch.classLoader instanceof LaunchClassLoader)) {
                return null;
            }
            return ((LaunchClassLoader) Launch.classLoader).getClassBytes(internalName.replace('/', '.'));
        }

        private static String readSuperName(byte[] basicClass) {
            final String[] superName = new String[1];
            ClassReader reader = new ClassReader(basicClass);
            reader.accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public void visit(int version, int access, String name, String signature, String superNameValue, String[] interfaces) {
                    superName[0] = superNameValue;
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return superName[0];
        }
    }
}
