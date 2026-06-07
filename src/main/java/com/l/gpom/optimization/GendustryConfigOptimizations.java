package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomCaches;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class GendustryConfigOptimizations {
    private static final int MAGIC = 0x47454E44; // GEND
    private static final int VERSION = 3;
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.gendustryConfigCache", "true"));
    private static final ConcurrentMap<String, Method> METHOD_CACHE = new ConcurrentHashMap<String, Method>();
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();
    private static final ConcurrentMap<String, Object> MODULE_CACHE = new ConcurrentHashMap<String, Object>();
    private static volatile boolean cacheHitThisRun;

    private GendustryConfigOptimizations() {
    }

    public static boolean loadCached(Object tuningLoaderModule) {
        if (!ENABLED || tuningLoaderModule == null) {
            return false;
        }

        long started = System.nanoTime();
        try {
            ClassLoader loader = tuningLoaderModule.getClass().getClassLoader();
            Object loaderObject = invokeGetter(tuningLoaderModule, "loader");
            File configDir = gendustryConfigDir(loader);
            if (loaderObject == null || configDir == null || !configDir.isDirectory()) {
                return false;
            }

            String signature = signature(loader, configDir);
            if (signature == null) {
                return false;
            }

            File cacheFile = cacheFile();
            if (!cacheFile.isFile()) {
                return false;
            }

            CacheImage image;
            try (LoaderObjectInputStream input = new LoaderObjectInputStream(
                    new BufferedInputStream(new FileInputStream(cacheFile)), loader)) {
                int magic = input.readInt();
                int version = input.readInt();
                String cachedSignature = input.readUTF();
                if (magic != MAGIC || version != VERSION || !signature.equals(cachedSignature)) {
                    return false;
                }
                Object object = input.readObject();
                if (!(object instanceof CacheImage)) {
                    return false;
                }
                image = (CacheImage) object;
            }

            restore(loaderObject, image, loader);
            cacheHitThisRun = true;
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[Gendustry Optimizations] Loaded tuning config cache in {} ms",
                        (System.nanoTime() - started) / 1_000_000L
                );
            }
            return true;
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[Gendustry Optimizations] Tuning config cache load failed; using stock loader", throwable);
            }
            return false;
        }
    }

    public static void saveCache(Object tuningLoaderModule) {
        if (!ENABLED || tuningLoaderModule == null || cacheHitThisRun) {
            return;
        }

        long started = System.nanoTime();
        File cacheFile = cacheFile();
        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[Gendustry Optimizations] Failed to create tuning config cache directory {}", parent);
            }
            return;
        }

        File tmp = new File(parent != null ? parent : cacheFile.getAbsoluteFile().getParentFile(), cacheFile.getName() + ".tmp");
        CacheImage image = null;
        try {
            ClassLoader loader = tuningLoaderModule.getClass().getClassLoader();
            Object loaderObject = invokeGetter(tuningLoaderModule, "loader");
            File configDir = gendustryConfigDir(loader);
            if (loaderObject == null || configDir == null || !configDir.isDirectory()) {
                return;
            }

            String signature = signature(loader, configDir);
            if (signature == null) {
                return;
            }

            image = capture(loaderObject, loader);
            try (ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
                output.writeInt(MAGIC);
                output.writeInt(VERSION);
                output.writeUTF(signature);
                output.writeObject(image);
            }

            if (cacheFile.isFile() && !cacheFile.delete()) {
                if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[Gendustry Optimizations] Failed to replace old tuning config cache {}", cacheFile);
                }
                return;
            }
            if (!tmp.renameTo(cacheFile)) {
                if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[Gendustry Optimizations] Failed to move tuning config cache into place {}", cacheFile);
                }
                return;
            }

            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[Gendustry Optimizations] Saved tuning config cache in {} ms",
                        (System.nanoTime() - started) / 1_000_000L
                );
            }
        } catch (Throwable throwable) {
            if (tmp.isFile() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[Gendustry Optimizations] Failed to save tuning config cache {}; will rebuild next launch", cacheFile, throwable);
                if (image != null) {
                    logSerializationDiagnostics(image);
                }
            }
        }
    }

    private static CacheImage capture(Object loaderObject, ClassLoader loader) throws Exception {
        return new CacheImage(
                invokeGetter(tuningModule(loader), "raw"),
                invokeGetter(loaderObject, "recipeStatements"),
                invokeGetter(loaderObject, "mutations"),
                invokeGetter(module(loader, "net.bdew.gendustry.custom.CustomFlowerAlleles$"), "definitions"),
                invokeGetter(module(loader, "net.bdew.gendustry.custom.CustomHives$"), "definitions")
        );
    }

    private static void restore(Object loaderObject, CacheImage image, ClassLoader loader) throws Exception {
        if (image == null) {
            throw new IOException("Invalid empty Gendustry cache image");
        }
        invokeSetter(tuningModule(loader), "raw_$eq", image.tuningRaw);
        invokeSetter(loaderObject, "recipeStatements_$eq", image.recipeStatements);
        invokeSetter(loaderObject, "mutations_$eq", image.mutations);
        invokeSetter(module(loader, "net.bdew.gendustry.custom.CustomFlowerAlleles$"), "definitions_$eq", image.flowerAlleleDefinitions);
        invokeSetter(module(loader, "net.bdew.gendustry.custom.CustomHives$"), "definitions_$eq", image.hiveDefinitions);
    }

    private static String signature(ClassLoader loader, File configDir) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateString(digest, "gpom-gendustry-config-cache-v" + VERSION);
        updateResource(digest, loader, "net/bdew/gendustry/config/loader/TuningLoader$.class");
        updateResource(digest, loader, "net/bdew/gendustry/config/loader/Loader.class");
        updateResource(digest, loader, "net/bdew/gendustry/config/Tuning$.class");
        byte[] listBytes = resourceBytes(loader, "assets/gendustry/config/files.lst");
        if (listBytes == null) {
            return null;
        }
        updateNamedBytes(digest, "resource:assets/gendustry/config/files.lst", listBytes);

        List<String> internalFiles = internalConfigFiles(listBytes);
        for (String name : internalFiles) {
            String path = "assets/gendustry/config/" + name;
            byte[] bytes = resourceBytes(loader, path);
            if (bytes == null) {
                return null;
            }
            updateNamedBytes(digest, "resource:" + path, bytes);
            updateFile(digest, "override:" + name, new File(new File(configDir, "overrides"), name));
        }

        List<File> userConfigs = userConfigFiles(configDir);
        updateString(digest, "user-config-count:" + userConfigs.size());
        for (File file : userConfigs) {
            updateFile(digest, "user:" + file.getName(), file);
        }
        return hex(digest.digest());
    }

    private static List<String> internalConfigFiles(byte[] listBytes) throws IOException {
        List<String> result = new ArrayList<String>();
        String text = new String(listBytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                result.add(trimmed);
            }
        }
        Collections.sort(result);
        return result;
    }

    private static List<File> userConfigFiles(File configDir) {
        File[] files = configDir.listFiles();
        List<File> result = new ArrayList<File>();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".cfg")) {
                result.add(file);
            }
        }
        Collections.sort(result, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return left.getName().compareTo(right.getName());
            }
        });
        return result;
    }

    private static void updateResource(MessageDigest digest, ClassLoader loader, String path) throws IOException {
        byte[] bytes = resourceBytes(loader, path);
        if (bytes == null) {
            throw new IOException("Missing resource " + path);
        }
        updateNamedBytes(digest, "resource:" + path, bytes);
    }

    private static byte[] resourceBytes(ClassLoader loader, String path) throws IOException {
        try (InputStream input = loader.getResourceAsStream(path)) {
            if (input == null) {
                return null;
            }
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void updateFile(MessageDigest digest, String label, File file) throws IOException {
        updateString(digest, label);
        if (file == null || !file.isFile()) {
            updateString(digest, "missing");
            return;
        }
        updateString(digest, "present");
        updateString(digest, Long.toString(file.length()));
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
    }

    private static void updateNamedBytes(MessageDigest digest, String label, byte[] bytes) {
        updateString(digest, label);
        updateString(digest, Integer.toString(bytes.length));
        digest.update(bytes);
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) ((bytes.length >>> 24) & 0xFF));
        digest.update((byte) ((bytes.length >>> 16) & 0xFF));
        digest.update((byte) ((bytes.length >>> 8) & 0xFF));
        digest.update((byte) (bytes.length & 0xFF));
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xFF;
            if (unsigned < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(unsigned));
        }
        return builder.toString();
    }

    private static Object tuningModule(ClassLoader loader) throws Exception {
        return module(loader, "net.bdew.gendustry.config.Tuning$");
    }

    private static Object module(ClassLoader loader, String className) throws Exception {
        Object cached = MODULE_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        Class<?> type = Class.forName(className, false, loader);
        Field module = publicField(type, "MODULE$");
        Object value = module.get(null);
        Object previous = MODULE_CACHE.putIfAbsent(className, value);
        return previous != null ? previous : value;
    }

    private static File gendustryConfigDir(ClassLoader loader) throws Exception {
        Object instance = module(loader, "net.bdew.gendustry.Gendustry$");
        Object value = invokeGetter(instance, "configDir");
        return value instanceof File ? (File) value : null;
    }

    private static Object invokeGetter(Object target, String name) throws Exception {
        Method method = publicMethod(target.getClass(), name);
        return method.invoke(target);
    }

    private static void invokeSetter(Object target, String name, Object value) throws Exception {
        Method cached = METHOD_CACHE.get(methodKey(target.getClass(), name, 1));
        if (cached != null && (value == null || cached.getParameterTypes()[0].isInstance(value))) {
            cached.invoke(target, value);
            return;
        }
        for (Method method : target.getClass().getMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 1
                    && (value == null || method.getParameterTypes()[0].isInstance(value))) {
                method.setAccessible(true);
                METHOD_CACHE.putIfAbsent(methodKey(target.getClass(), name, 1), method);
                method.invoke(target, value);
                return;
            }
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }

    private static Method publicMethod(Class<?> type, String name) throws NoSuchMethodException {
        String key = methodKey(type, name, 0);
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method method = type.getMethod(name);
        method.setAccessible(true);
        Method previous = METHOD_CACHE.putIfAbsent(key, method);
        return previous != null ? previous : method;
    }

    private static Field publicField(Class<?> type, String name) throws NoSuchFieldException {
        String key = type.getName() + '#' + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Field field = type.getField(name);
        field.setAccessible(true);
        Field previous = FIELD_CACHE.putIfAbsent(key, field);
        return previous != null ? previous : field;
    }

    private static String methodKey(Class<?> type, String name, int arity) {
        return type.getName() + '#' + name + '/' + arity;
    }

    private static void logSerializationDiagnostics(CacheImage image) {
        checkSerializable("tuningRaw", image.tuningRaw);
        checkSerializable("recipeStatements", image.recipeStatements);
        checkSerializable("mutations", image.mutations);
        checkSerializable("flowerAlleleDefinitions", image.flowerAlleleDefinitions);
        checkSerializable("hiveDefinitions", image.hiveDefinitions);
    }

    private static void checkSerializable(String name, Object value) {
        try (ObjectOutputStream output = new ObjectOutputStream(new ByteArrayOutputStream())) {
            output.writeObject(value);
            GPOM.LOGGER.info("[Gendustry Optimizations] Cache member {} is serializable", name);
        } catch (Throwable throwable) {
            GPOM.LOGGER.warn("[Gendustry Optimizations] Cache member {} is not serializable: {}", name, throwable.toString());
        }
    }

    private static File cacheFile() {
        return GpomCaches.file("gendustry-config", "tuning-v3.dat");
    }

    private static final class CacheImage implements Serializable {
        private static final long serialVersionUID = 1L;

        private final Object tuningRaw;
        private final Object recipeStatements;
        private final Object mutations;
        private final Object flowerAlleleDefinitions;
        private final Object hiveDefinitions;

        private CacheImage(Object tuningRaw, Object recipeStatements, Object mutations, Object flowerAlleleDefinitions, Object hiveDefinitions) {
            this.tuningRaw = tuningRaw;
            this.recipeStatements = recipeStatements;
            this.mutations = mutations;
            this.flowerAlleleDefinitions = flowerAlleleDefinitions;
            this.hiveDefinitions = hiveDefinitions;
        }
    }

    private static final class LoaderObjectInputStream extends ObjectInputStream {
        private final ClassLoader loader;

        private LoaderObjectInputStream(InputStream input, ClassLoader loader) throws IOException {
            super(input);
            this.loader = loader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor) throws IOException, ClassNotFoundException {
            String name = descriptor.getName();
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
                return super.resolveClass(descriptor);
            }
        }
    }
}
