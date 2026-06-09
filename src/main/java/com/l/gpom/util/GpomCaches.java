package com.l.gpom.util;

import com.l.gpom.config.GpomEarlyConfig;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public final class GpomCaches {
    private static final String CACHE_ROOT = "gpom";

    private GpomCaches() {
    }

    public static File directory(String purpose) {
        String safePurpose = sanitizePurpose(purpose);
        return new File(new File(instanceDirectory(), "caches"), CACHE_ROOT + File.separator + safePurpose);
    }

    public static File file(String purpose, String fileName) {
        File directory = directory(purpose);
        File file = new File(directory, sanitizeFileName(fileName));
        try {
            File canonicalDirectory = directory.getCanonicalFile();
            File canonicalFile = file.getCanonicalFile();
            String directoryPath = canonicalDirectory.getPath() + File.separator;
            if (canonicalFile.getPath().startsWith(directoryPath)) {
                return canonicalFile;
            }
            return new File(canonicalDirectory, "cache.dat");
        } catch (IOException ignored) {
            return file.getAbsoluteFile();
        }
    }

    public static File instanceDirectory() {
        try {
            return new File(System.getProperty("user.dir", ".")).getCanonicalFile();
        } catch (IOException ignored) {
            return new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        }
    }

    public static boolean cacheInvalidationDenied(File file) {
        return cacheInvalidationDenied(file == null ? null : file.getName());
    }

    public static boolean cacheInvalidationDenied(String label) {
        Set<String> denylist = GpomEarlyConfig.cacheInvalidationDenylist();
        if (denylist.isEmpty()) {
            return false;
        }
        String normalized = normalize(label);
        String fileName = fileName(normalized);
        String stem = stripArchiveExtension(fileName);
        for (String denied : denylist) {
            String entry = normalize(denied);
            if (entry.isEmpty()) {
                continue;
            }
            if (matches(entry, normalized) || matches(entry, fileName) || matches(entry, stem)) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizePurpose(String purpose) {
        if (purpose == null) {
            return "misc";
        }
        String trimmed = purpose.trim();
        return trimmed.isEmpty() ? "misc" : trimmed.replace('\\', '_').replace('/', '_');
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "cache.dat";
        }
        String trimmed = fileName.trim();
        if (trimmed.isEmpty() || ".".equals(trimmed) || "..".equals(trimmed)) {
            return "cache.dat";
        }
        return trimmed.replace('\\', '_').replace('/', '_');
    }

    private static boolean matches(String entry, String candidate) {
        if (candidate.isEmpty()) {
            return false;
        }
        return candidate.equals(entry)
                || candidate.startsWith(entry + "-")
                || candidate.startsWith(entry + "_")
                || candidate.startsWith(entry + ".");
    }

    private static String fileName(String label) {
        int slash = Math.max(label.lastIndexOf('/'), label.lastIndexOf('\\'));
        return slash >= 0 ? label.substring(slash + 1) : label;
    }

    private static String stripArchiveExtension(String value) {
        if (value.endsWith(".jar") || value.endsWith(".zip")) {
            return value.substring(0, value.length() - 4);
        }
        return value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('\\', '/');
    }
}
