package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class AgriCraftOptimizations {
    private static final boolean FAST_JSON_IO = Boolean.parseBoolean(System.getProperty("gpom.agricraft.fastJsonIo", "true"));
    private static final boolean FAST_RESOURCE_SCAN = Boolean.parseBoolean(System.getProperty("gpom.agricraft.fastResourceScan", "true"));
    private static final boolean SKIP_JSON_WRITEBACK = Boolean.parseBoolean(System.getProperty("gpom.agricraft.skipJsonWriteback", "true"));
    private static final ConcurrentHashMap<String, Boolean> LOGGED = new ConcurrentHashMap<String, Boolean>();

    private AgriCraftOptimizations() {
    }

    public static void copyResourceFastOrStock(String resource, Path target, boolean force) {
        if (copyResourceFast(resource, target, force)) {
            return;
        }
        copyResourceStock(resource, target, force);
    }

    public static boolean copyResourceFast(String resource, Path target, boolean force) {
        if (!FAST_JSON_IO || resource == null || target == null) {
            return false;
        }
        try {
            if (!force && Files.exists(target, new LinkOption[0])) {
                return true;
            }

            byte[] source = readResource(resource);
            if (source == null) {
                return false;
            }
            if (force && Files.isRegularFile(target, new LinkOption[0]) && sameBytes(target, source)) {
                return true;
            }

            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            OutputStream output = Files.newOutputStream(
                    target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                output.write(source);
            } finally {
                output.close();
            }
            return true;
        } catch (Throwable throwable) {
            logOnce("copy-resource", "AgriCraft JSON resource fast-copy failed; falling back to stock copy", throwable);
            return false;
        }
    }

    public static boolean shouldWriteBackJson(boolean writeback) {
        return writeback && !SKIP_JSON_WRITEBACK;
    }

    public static Set<String> findResourcesFastOrStock(Predicate<String> predicate) {
        if (predicate == null) {
            return Collections.emptySet();
        }
        if (FAST_RESOURCE_SCAN) {
            try {
                Set<String> resources = findResourcesInAgriCraftContainer(predicate);
                if (!resources.isEmpty()) {
                    return resources;
                }
            } catch (Throwable throwable) {
                logOnce("resource-scan", "AgriCraft JSON resource scan fast path failed; falling back to Reflections", throwable);
            }
        }
        return findResourcesStock(predicate);
    }

    public static Set<String> findResourcesFromGuavaFastOrStock(Object guavaPredicate) {
        if (guavaPredicate == null) {
            return Collections.emptySet();
        }
        final Method applyMethod = findPredicateApplyMethod(guavaPredicate);
        if (applyMethod == null) {
            return Collections.emptySet();
        }
        final Object predicateObject = guavaPredicate;
        return findResourcesFastOrStock(new Predicate<String>() {
            @Override
            public boolean test(String value) {
                try {
                    return Boolean.TRUE.equals(applyMethod.invoke(predicateObject, value));
                } catch (Throwable throwable) {
                    throw new IllegalStateException("Unable to evaluate AgriCraft resource predicate", throwable);
                }
            }
        });
    }

    private static void copyResourceStock(String resource, Path target, boolean force) {
        try {
            if (resource == null || target == null) {
                return;
            }
            if (!force && Files.exists(target, new LinkOption[0])) {
                return;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] source = readResource(resource);
            if (source == null) {
                throw new IllegalStateException("Missing AgriCraft resource " + resource);
            }
            OutputStream output = Files.newOutputStream(
                    target,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                output.write(source);
            } finally {
                output.close();
            }
        } catch (Throwable throwable) {
            logOnce("copy-resource-stock", "AgriCraft JSON resource stock-copy fallback failed", throwable);
        }
    }

    private static Set<String> findResourcesInAgriCraftContainer(Predicate<String> predicate) throws Exception {
        Set<String> resources = new HashSet<String>();
        Set<String> scanned = new HashSet<String>();
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        scanClassResource(context, "com/agricraft/agricore/util/ResourceHelper.class", predicate, resources, scanned);
        scanClassResource(context, "com/infinityraider/agricraft/core/CoreHandler.class", predicate, resources, scanned);
        ClassLoader ownLoader = AgriCraftOptimizations.class.getClassLoader();
        if (ownLoader != context) {
            scanClassResource(ownLoader, "com/agricraft/agricore/util/ResourceHelper.class", predicate, resources, scanned);
            scanClassResource(ownLoader, "com/infinityraider/agricraft/core/CoreHandler.class", predicate, resources, scanned);
        }
        return resources;
    }

    private static void scanClassResource(
            ClassLoader loader,
            String classResource,
            Predicate<String> predicate,
            Set<String> resources,
            Set<String> scanned
    ) throws Exception {
        if (loader == null) {
            return;
        }
        Enumeration<URL> urls = loader.getResources(classResource);
        while (urls.hasMoreElements()) {
            scanContainerUrl(urls.nextElement(), classResource, predicate, resources, scanned);
        }
    }

    private static void scanContainerUrl(
            URL url,
            String classResource,
            Predicate<String> predicate,
            Set<String> resources,
            Set<String> scanned
    ) throws Exception {
        if (url == null) {
            return;
        }
        if ("jar".equals(url.getProtocol())) {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            connection.setUseCaches(false);
            String key = connection.getJarFileURL().toExternalForm();
            if (!scanned.add(key)) {
                return;
            }
            JarFile jarFile = connection.getJarFile();
            try {
                scanJar(jarFile, predicate, resources);
            } finally {
                jarFile.close();
            }
            return;
        }
        if ("file".equals(url.getProtocol())) {
            File classFile = new File(URLDecoder.decode(url.getPath(), "UTF-8"));
            String suffix = classResource.replace('/', File.separatorChar);
            String absolutePath = classFile.getAbsolutePath();
            if (!absolutePath.endsWith(suffix)) {
                return;
            }
            File root = new File(absolutePath.substring(0, absolutePath.length() - suffix.length()));
            String key = root.getAbsolutePath();
            if (scanned.add(key)) {
                scanDirectory(root, root, predicate, resources);
            }
        }
    }

    private static void scanJar(JarFile jarFile, Predicate<String> predicate, Set<String> resources) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (predicate.test(name)) {
                resources.add(name);
            }
        }
    }

    private static void scanDirectory(File root, File current, Predicate<String> predicate, Set<String> resources) {
        File[] children = current.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                scanDirectory(root, child, predicate, resources);
                continue;
            }
            String name = root.toURI().relativize(child.toURI()).getPath();
            if (predicate.test(name)) {
                resources.add(name);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> findResourcesStock(final Predicate<String> predicate) {
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            if (loader == null) {
                loader = AgriCraftOptimizations.class.getClassLoader();
            }
            Class<?> scannerType = Class.forName("org.reflections.scanners.Scanner", false, loader);
            Class<?> resourcesScannerType = Class.forName("org.reflections.scanners.ResourcesScanner", false, loader);
            Object resourcesScanner = resourcesScannerType.getConstructor().newInstance();
            Object scanners = Array.newInstance(scannerType, 1);
            Array.set(scanners, 0, resourcesScanner);

            Class<?> reflectionsType = Class.forName("org.reflections.Reflections", false, loader);
            Constructor<?> constructor = reflectionsType.getConstructor(String.class, scanners.getClass());
            Object reflections = constructor.newInstance(null, scanners);

            Class<?> guavaPredicateType = Class.forName("com.google.common.base.Predicate", false, loader);
            Object guavaPredicate = Proxy.newProxyInstance(
                    guavaPredicateType.getClassLoader() == null ? loader : guavaPredicateType.getClassLoader(),
                    new Class<?>[]{guavaPredicateType},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            String name = method.getName();
                            if (("apply".equals(name) || "test".equals(name)) && args != null && args.length == 1) {
                                return Boolean.valueOf(predicate.test(String.valueOf(args[0])));
                            }
                            if ("toString".equals(name)) {
                                return "GPOM AgriCraft resource predicate bridge";
                            }
                            if ("hashCode".equals(name)) {
                                return Integer.valueOf(System.identityHashCode(proxy));
                            }
                            if ("equals".equals(name) && args != null && args.length == 1) {
                                return Boolean.valueOf(proxy == args[0]);
                            }
                            return null;
                        }
                    }
            );
            Object resources = reflectionsType.getMethod("getResources", guavaPredicateType).invoke(reflections, guavaPredicate);
            if (resources instanceof Set) {
                return (Set<String>) resources;
            }
        } catch (Throwable throwable) {
            logOnce("resource-scan-stock", "AgriCraft JSON resource Reflections fallback failed", throwable);
        }
        return Collections.emptySet();
    }

    private static Method findPredicateApplyMethod(Object predicate) {
        try {
            Method method = predicate.getClass().getMethod("apply", Object.class);
            method.setAccessible(true);
            return method;
        } catch (Throwable ignored) {
            try {
                Method method = predicate.getClass().getMethod("test", Object.class);
                method.setAccessible(true);
                return method;
            } catch (Throwable throwable) {
                logOnce("resource-scan-predicate", "AgriCraft JSON resource predicate bridge failed", throwable);
                return null;
            }
        }
    }

    private static byte[] readResource(String resource) throws Exception {
        InputStream input = null;
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null) {
            input = context.getResourceAsStream(resource);
        }
        if (input == null) {
            ClassLoader ownLoader = AgriCraftOptimizations.class.getClassLoader();
            if (ownLoader != null) {
                input = ownLoader.getResourceAsStream(resource);
            }
        }
        if (input == null) {
            input = AgriCraftOptimizations.class.getResourceAsStream(resource.startsWith("/") ? resource : "/" + resource);
        }
        if (input == null) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(1024, input.available()));
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static boolean sameBytes(Path path, byte[] expected) throws Exception {
        if (Files.size(path) != expected.length) {
            return false;
        }
        InputStream input = Files.newInputStream(path, StandardOpenOption.READ);
        try {
            byte[] buffer = new byte[8192];
            int offset = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                for (int i = 0; i < read; i++) {
                    if (offset >= expected.length || buffer[i] != expected[offset++]) {
                        return false;
                    }
                }
            }
            return offset == expected.length;
        } finally {
            input.close();
        }
    }

    private static void logOnce(String key, String message, Throwable throwable) {
        if (GpomEarlyConfig.optimizationInfoLogsEnabled() && LOGGED.putIfAbsent(key, Boolean.TRUE) == null) {
            GPOM.LOGGER.warn(message, throwable);
        }
    }
}
