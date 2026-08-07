package com.l.gpom.compat.foundation;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/** Caches Foundation's repeated per-class manifest reads for a live JAR handle. */
public final class FoundationClassLoaderCompat {
    private static final Object NO_MANIFEST = new Object();
    private static final Map<JarFile, Object> MANIFESTS = Collections.synchronizedMap(new WeakHashMap<JarFile, Object>());

    private FoundationClassLoaderCompat() {
    }

    public static Manifest cachedManifest(JarFile jarFile) throws IOException {
        Object cached = MANIFESTS.get(jarFile);
        if (cached != null) {
            return cached == NO_MANIFEST ? null : (Manifest) cached;
        }
        Manifest manifest = jarFile.getManifest();
        MANIFESTS.put(jarFile, manifest == null ? NO_MANIFEST : manifest);
        return manifest;
    }
}
