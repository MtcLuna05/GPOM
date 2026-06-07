package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.util.GpomCaches;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModularMachineryOptimizations {
    private static final Set<String> MANIFEST_RECORDED_STAGES = Collections.synchronizedSet(new HashSet<>());
    private static final AtomicBoolean LAZY_STRUCTURE_CACHE_REPORTED = new AtomicBoolean();
    private static final int CACHE_SCHEMA = 1;
    private static Field blockArrayCacheMapField;
    private static Field blockArrayUidField;
    private static Method blockArrayFlushTileBlocksCache;
    private static Method blockArrayRotateYccw;
    private static Method enumFacingRotateYccw;

    private ModularMachineryOptimizations() {
    }

    public static void recordCacheManifest(String stage) {
        if (!MANIFEST_RECORDED_STAGES.add(stage)) {
            return;
        }

        try {
            File minecraftDir = GpomCaches.instanceDirectory();
            ManifestSnapshot snapshot = buildManifestSnapshot(minecraftDir);
            File manifestFile = GpomCaches.file("modularmachinery", "modularmachinery-manifest.txt");
            String previous = readFirstLine(manifestFile);
            boolean hit = snapshot.signature.equals(previous);
            File parent = manifestFile.getParentFile();

            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                GPOM.LOGGER.info("[MM Optimizations] Could not create cache directory {}", parent);
            } else {
                Files.write(manifestFile.toPath(), Collections.singletonList(snapshot.signature), StandardCharsets.UTF_8);
            }

            GPOM.LOGGER.info(
                    "[MM Optimizations] {} manifest {}: files={}, bytes={}, signature={}",
                    stage,
                    hit ? "hit" : "miss",
                    snapshot.files,
                    snapshot.bytes,
                    snapshot.signature
            );
        } catch (Throwable throwable) {
            GPOM.LOGGER.info("[MM Optimizations] Could not build CraftTweaker-aware manifest", throwable);
        }
    }

    public static boolean skipMmStructureCacheBuild(java.util.Collection<?> machines) {
        try {
            Map<?, ?> cacheMap = blockArrayCacheMap();
            synchronized (cacheMap) {
                cacheMap.clear();
            }
            recordCacheManifest("structure-cache");
            if (LAZY_STRUCTURE_CACHE_REPORTED.compareAndSet(false, true)) {
                GPOM.LOGGER.info(
                        "[MM Optimizations] Deferring Modular Machinery structure cache build for {} machine(s)",
                        machines == null ? 0 : machines.size()
                );
            }
            return true;
        } catch (Throwable throwable) {
            GPOM.LOGGER.info("[MM Optimizations] Could not defer Modular Machinery structure cache build", throwable);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object getLazyBlockArrayCache(Object blockArray, Object facing) {
        if (blockArray == null || facing == null) {
            return null;
        }

        try {
            Map cacheMap = blockArrayCacheMap();
            Long uid = Long.valueOf(blockArrayUid(blockArray));
            synchronized (cacheMap) {
                EnumMap rotations = (EnumMap) cacheMap.get(uid);
                if (rotations == null) {
                    rotations = new EnumMap((Class<? extends Enum>) facing.getClass());
                    cacheMap.put(uid, rotations);
                }

                Object cached = rotations.get(facing);
                if (cached != null) {
                    return cached;
                }

                Object built = buildRotation(rotations, blockArray, facing);
                GPOM.LOGGER.info("[MM Optimizations] Lazily built Modular Machinery structure cache uid={} facing={}", uid, facing);
                return built;
            }
        } catch (Throwable throwable) {
            GPOM.LOGGER.info("[MM Optimizations] Lazy structure cache failed; returning original pattern", throwable);
            return blockArray;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object buildRotation(EnumMap rotations, Object original, Object targetFacing) throws ReflectiveOperationException {
        Object currentFacing = enumValue((Class<? extends Enum>) targetFacing.getClass(), "NORTH");
        Object currentArray = original;
        flushTileBlocksCache(currentArray);
        rotations.put((Enum) currentFacing, currentArray);

        while (!currentFacing.equals(targetFacing)) {
            currentFacing = rotateFacingYccw(currentFacing);
            currentArray = rotateBlockArrayYccw(currentArray);
            flushTileBlocksCache(currentArray);
            rotations.put((Enum) currentFacing, currentArray);
        }
        return currentArray;
    }

    private static Map<?, ?> blockArrayCacheMap() throws ReflectiveOperationException {
        Field field = blockArrayCacheMapField;
        if (field == null) {
            field = Class.forName("hellfirepvp.modularmachinery.common.util.BlockArrayCache")
                    .getDeclaredField("BLOCK_ARRAY_CACHE_MAP");
            field.setAccessible(true);
            blockArrayCacheMapField = field;
        }
        return (Map<?, ?>) field.get(null);
    }

    private static long blockArrayUid(Object blockArray) throws ReflectiveOperationException {
        Field field = blockArrayUidField;
        if (field == null) {
            field = findField(blockArray.getClass(), "uid");
            blockArrayUidField = field;
        }
        return field.getLong(blockArray);
    }

    private static void flushTileBlocksCache(Object blockArray) throws ReflectiveOperationException {
        Method method = blockArrayFlushTileBlocksCache;
        if (method == null) {
            method = Class.forName("hellfirepvp.modularmachinery.common.util.BlockArray")
                    .getMethod("flushTileBlocksCache");
            method.setAccessible(true);
            blockArrayFlushTileBlocksCache = method;
        }
        method.invoke(blockArray);
    }

    private static Object rotateBlockArrayYccw(Object blockArray) throws ReflectiveOperationException {
        Method method = blockArrayRotateYccw;
        if (method == null) {
            method = Class.forName("hellfirepvp.modularmachinery.common.util.BlockArray")
                    .getMethod("rotateYCCW");
            method.setAccessible(true);
            blockArrayRotateYccw = method;
        }
        return method.invoke(blockArray);
    }

    private static Object rotateFacingYccw(Object facing) throws ReflectiveOperationException {
        Method method = enumFacingRotateYccw;
        if (method == null) {
            method = facing.getClass().getMethod("func_176735_f");
            method.setAccessible(true);
            enumFacingRotateYccw = method;
        }
        return method.invoke(facing);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<? extends Enum> enumClass, String name) {
        return Enum.valueOf((Class) enumClass, name);
    }

    private static ManifestSnapshot buildManifestSnapshot(File minecraftDir) throws Exception {
        List<File> files = new ArrayList<>();
        addMatchingFiles(new File(minecraftDir, "mods"), files, "ModularMachinery", ".jar");
        addMatchingFiles(new File(minecraftDir, "mods"), files, "ModularMachinery-Addons", ".jar");
        addMatchingFiles(new File(minecraftDir, "mods"), files, "CraftTweaker", ".jar");
        addTree(new File(minecraftDir, "config/modularmachinery"), files);
        addTree(new File(minecraftDir, "scripts"), files);
        files.sort((left, right) -> relativePath(minecraftDir, left).compareTo(relativePath(minecraftDir, right)));

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        update(digest, "cro-mm-schema=" + CACHE_SCHEMA + '\n');

        long bytes = 0L;
        byte[] buffer = new byte[64 * 1024];
        for (File file : files) {
            String relative = relativePath(minecraftDir, file);
            update(digest, relative);
            update(digest, "\n");
            update(digest, Long.toString(file.length()));
            update(digest, "\n");
            bytes += file.length();

            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            update(digest, "\n");
        }
        return new ManifestSnapshot(hex(digest.digest()), files.size(), bytes);
    }

    private static void addMatchingFiles(File dir, List<File> files, String prefix, String suffix) {
        File[] listed = dir.listFiles();
        if (listed == null) {
            return;
        }
        for (File file : listed) {
            String name = file.getName();
            if (file.isFile() && name.startsWith(prefix) && name.endsWith(suffix)) {
                files.add(file);
            }
        }
    }

    private static void addTree(File root, List<File> files) throws IOException {
        if (!root.isDirectory()) {
            return;
        }
        Files.walk(root.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> files.add(path.toFile()));
    }

    private static String readFirstLine(File file) {
        try {
            if (!file.isFile()) {
                return null;
            }
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            return lines.isEmpty() ? null : lines.get(0);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String relativePath(File root, File file) {
        return root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
    }

    private static void update(MessageDigest digest, String text) {
        digest.update(text.getBytes(StandardCharsets.UTF_8));
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> cursor = type;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = alphabet[value >>> 4];
            chars[i * 2 + 1] = alphabet[value & 0x0F];
        }
        return new String(chars);
    }

    private static final class ManifestSnapshot {
        private final String signature;
        private final int files;
        private final long bytes;

        private ManifestSnapshot(String signature, int files, long bytes) {
            this.signature = signature;
            this.files = files;
            this.bytes = bytes;
        }
    }
}
