package com.l.gpom.util;

import java.io.File;
import java.io.IOException;

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
}
