package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import com.l.gpom.util.GpomCaches;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class OpenComputersSettingsOptimizations {
    private static final int LEGACY_MAGIC = 0x4F435347; // OCSG
    private static final int LEGACY_VERSION = 1;
    private static final int BINARY_MAGIC = 0x4F435342; // OCSB
    private static final int BINARY_VERSION = 2;
    private static final int MAX_FIELDS = 512;
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.openComputersSettingsCache", "true"));
    private static final ConcurrentMap<String, Field> FIELD_CACHE = new ConcurrentHashMap<String, Field>();
    private static final ConcurrentMap<Class<?>, Field[]> INSTANCE_FIELDS_CACHE = new ConcurrentHashMap<Class<?>, Field[]>();
    private static volatile boolean cacheHitThisRun;

    private static final byte TAG_NULL = 0;
    private static final byte TAG_BOOLEAN = 1;
    private static final byte TAG_INTEGER = 2;
    private static final byte TAG_LONG = 3;
    private static final byte TAG_FLOAT = 4;
    private static final byte TAG_DOUBLE = 5;
    private static final byte TAG_STRING = 6;
    private static final byte TAG_INT_ARRAY = 7;
    private static final byte TAG_DOUBLE_ARRAY = 8;
    private static final byte TAG_STRING_LIST = 9;
    private static final byte TAG_INTEGER_LIST = 10;
    private static final byte TAG_INTERNET_RULES = 11;
    private static final byte TAG_GAME_PROFILE = 12;
    private static final byte TAG_TUPLE2 = 13;
    private static final byte TAG_TUPLE2_ARRAY = 14;
    private static final byte TAG_ENUM = 15;
    private static final byte TAG_ENUM_ARRAY = 16;
    private static final byte TAG_JAVA_SERIALIZED = 127;

    private OpenComputersSettingsOptimizations() {
    }

    public static boolean loadCached(Object settingsModule, File settingsFile) {
        if (!ENABLED || settingsModule == null || settingsFile == null) {
            return false;
        }

        long started = System.nanoTime();
        try {
            ClassLoader loader = settingsModule.getClass().getClassLoader();
            Class<?> settingsClass = Class.forName("li.cil.oc.Settings", false, loader);
            String signature = signature(settingsClass, settingsFile);
            if (signature == null) {
                return false;
            }

            File cacheFile = cacheFile();
            if (!cacheFile.isFile()) {
                return false;
            }

            CacheImage image = readCache(cacheFile, signature, loader);
            if (image == null) {
                return false;
            }

            Object settings = UnsafeAccess.allocateInstance(settingsClass);
            restoreSettingsFields(settings, image, loader);
            writeModuleSettings(settingsModule, settings);
            cacheHitThisRun = true;

            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[OpenComputers Optimizations] Loaded Settings cache in {} ms",
                        (System.nanoTime() - started) / 1_000_000L
                );
            }
            return true;
        } catch (Throwable throwable) {
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[OpenComputers Optimizations] Settings cache load failed; using stock loader", throwable);
            }
            return false;
        }
    }

    public static void saveCache(Object settingsModule, File settingsFile) {
        if (!ENABLED || settingsModule == null || settingsFile == null || cacheHitThisRun) {
            return;
        }

        long started = System.nanoTime();
        File cacheFile = cacheFile();
        File parent = cacheFile.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[OpenComputers Optimizations] Failed to create Settings cache directory {}", parent);
            }
            return;
        }

        File tmp = new File(parent != null ? parent : cacheFile.getAbsoluteFile().getParentFile(), cacheFile.getName() + ".tmp");
        try {
            Class<?> settingsClass = Class.forName("li.cil.oc.Settings", false, settingsModule.getClass().getClassLoader());
            String signature = signature(settingsClass, settingsFile);
            if (signature == null) {
                return;
            }

            Object settings = readModuleSettings(settingsModule);
            if (settings == null) {
                return;
            }

            CacheImage image = captureSettings(settings);
            writeBinaryCache(tmp, signature, image);

            if (cacheFile.isFile() && !cacheFile.delete()) {
                if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[OpenComputers Optimizations] Failed to replace old Settings cache {}", cacheFile);
                }
                return;
            }
            if (!tmp.renameTo(cacheFile)) {
                if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                    GPOM.LOGGER.warn("[OpenComputers Optimizations] Failed to move Settings cache into place {}", cacheFile);
                }
                return;
            }

            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.info(
                        "[OpenComputers Optimizations] Saved Settings cache in {} ms",
                        (System.nanoTime() - started) / 1_000_000L
                );
            }
        } catch (Throwable throwable) {
            if (tmp.isFile() && !tmp.delete()) {
                tmp.deleteOnExit();
            }
            if (GpomEarlyConfig.cacheInfoLogsEnabled()) {
                GPOM.LOGGER.warn("[OpenComputers Optimizations] Failed to save Settings cache {}; will rebuild next launch", cacheFile, throwable);
            }
        }
    }

    private static CacheImage readCache(File cacheFile, String signature, ClassLoader loader) throws Exception {
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(cacheFile)))) {
            int magic = input.readInt();
            if (magic == BINARY_MAGIC) {
                int version = input.readInt();
                String cachedSignature = readString(input);
                if (version != BINARY_VERSION || !signature.equals(cachedSignature)) {
                    return null;
                }
                return readBinaryImage(input, loader);
            }
        }
        CacheImage legacy = readLegacyCache(cacheFile, signature, loader);
        if (legacy != null) {
            rewriteLegacyCache(cacheFile, signature, legacy);
        }
        return legacy;
    }

    private static CacheImage readLegacyCache(File cacheFile, String signature, ClassLoader loader) throws Exception {
        try (LoaderObjectInputStream input = new LoaderObjectInputStream(
                new BufferedInputStream(new FileInputStream(cacheFile)), loader)) {
            int magic = input.readInt();
            int version = input.readInt();
            String cachedSignature = input.readUTF();
            if (magic != LEGACY_MAGIC || version != LEGACY_VERSION || !signature.equals(cachedSignature)) {
                return null;
            }
            Object object = input.readObject();
            if (!(object instanceof CacheImage)) {
                return null;
            }
            return (CacheImage) object;
        }
    }

    private static void rewriteLegacyCache(File cacheFile, String signature, CacheImage image) {
        File parent = cacheFile.getParentFile();
        File tmp = new File(parent != null ? parent : cacheFile.getAbsoluteFile().getParentFile(), cacheFile.getName() + ".tmp");
        try {
            writeBinaryCache(tmp, signature, image);
            if (cacheFile.isFile() && !cacheFile.delete()) {
                return;
            }
            if (!tmp.renameTo(cacheFile) && tmp.isFile()) {
                tmp.delete();
            }
        } catch (Throwable ignored) {
            if (tmp.isFile()) {
                tmp.delete();
            }
        }
    }

    private static CacheImage readBinaryImage(DataInputStream input, ClassLoader loader) throws Exception {
        String renderedConfig = readString(input);
        int count = input.readInt();
        if (count <= 0 || count > MAX_FIELDS) {
            throw new InvalidObjectException("Invalid Settings binary field count: " + count);
        }
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (int i = 0; i < count; i++) {
            String name = readString(input);
            if (values.containsKey(name)) {
                throw new InvalidObjectException("Duplicate Settings binary field: " + name);
            }
            values.put(name, readValue(input, loader));
        }
        return new CacheImage(renderedConfig, values);
    }

    private static void writeBinaryCache(File file, String signature, CacheImage image) throws Exception {
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            output.writeInt(BINARY_MAGIC);
            output.writeInt(BINARY_VERSION);
            writeString(output, signature);
            writeString(output, image.renderedConfig);
            output.writeInt(image.fieldValues.size());
            for (Map.Entry<String, Object> entry : image.fieldValues.entrySet()) {
                writeString(output, entry.getKey());
                writeValue(output, entry.getValue());
            }
        }
    }

    private static CacheImage captureSettings(Object settings) throws Exception {
        Class<?> settingsClass = settings.getClass();
        Field[] fields = instanceFields(settingsClass);
        if (fields.length <= 0 || fields.length > MAX_FIELDS) {
            throw new InvalidObjectException("Unexpected Settings field count: " + fields.length);
        }

        String renderedConfig = null;
        Map<String, Object> values = new LinkedHashMap<String, Object>();
        for (Field field : fields) {
            field.setAccessible(true);
            Object value = field.get(settings);
            if ("config".equals(field.getName())) {
                renderedConfig = renderConfig(value, settingsClass.getClassLoader());
            } else {
                values.put(field.getName(), captureFieldValue(field, value));
            }
        }
        if (renderedConfig == null) {
            throw new InvalidObjectException("Settings.config was not captured");
        }
        return new CacheImage(renderedConfig, values);
    }

    private static void restoreSettingsFields(Object settings, CacheImage image, ClassLoader loader) throws Exception {
        if (image == null || image.fieldValues == null || image.fieldValues.size() > MAX_FIELDS) {
            throw new InvalidObjectException("Invalid Settings cache image");
        }

        Field[] fields = instanceFields(settings.getClass());
        for (Field field : fields) {
            field.setAccessible(true);
            Object value;
            if ("config".equals(field.getName())) {
                value = lazyConfig(image.renderedConfig, loader);
            } else if (!image.fieldValues.containsKey(field.getName())) {
                throw new InvalidObjectException("Missing cached Settings field " + field.getName());
            } else {
                value = restoreFieldValue(field, image.fieldValues.get(field.getName()), loader);
            }
            UnsafeAccess.putField(field, settings, value);
        }
    }

    private static Field[] instanceFields(Class<?> type) {
        Field[] cached = INSTANCE_FIELDS_CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        Field[] fields = type.getDeclaredFields();
        int count = 0;
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                count++;
            }
        }
        Field[] result = new Field[count];
        int index = 0;
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                result[index++] = field;
            }
        }
        Arrays.sort(result, new Comparator<Field>() {
            @Override
            public int compare(Field left, Field right) {
                return left.getName().compareTo(right.getName());
            }
        });
        Field[] previous = INSTANCE_FIELDS_CACHE.putIfAbsent(type, result);
        if (previous != null) {
            return previous;
        }
        return result;
    }

    private static Object captureFieldValue(Field field, Object value) throws Exception {
        if (value == null) {
            return null;
        }
        if ("internetFilteringRules".equals(field.getName())) {
            return InternetRulesSnapshot.capture(value);
        }
        if ("fakePlayerProfile".equals(field.getName())) {
            return GameProfileSnapshot.capture(value);
        }
        if (!(value instanceof Serializable) && !value.getClass().isArray()) {
            throw new NotSerializableException(field.getName() + "=" + value.getClass().getName());
        }
        return value;
    }

    private static Object restoreFieldValue(Field field, Object value, ClassLoader loader) throws Exception {
        if (value == null) {
            return null;
        }
        if ("internetFilteringRules".equals(field.getName())) {
            if (!(value instanceof InternetRulesSnapshot)) {
                throw new InvalidObjectException("Invalid internetFilteringRules snapshot");
            }
            return ((InternetRulesSnapshot) value).restore(field.getType());
        }
        if ("fakePlayerProfile".equals(field.getName())) {
            if (!(value instanceof GameProfileSnapshot)) {
                throw new InvalidObjectException("Invalid fakePlayerProfile snapshot");
            }
            return ((GameProfileSnapshot) value).restore(loader);
        }
        return value;
    }

    private static Object readModuleSettings(Object module) throws Exception {
        Field field = field(module.getClass(), "settings");
        return field.get(module);
    }

    private static void writeModuleSettings(Object module, Object settings) throws Exception {
        Field field = field(module.getClass(), "settings");
        UnsafeAccess.putField(field, module, settings);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        String key = owner.getName() + '#' + name;
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        FIELD_CACHE.putIfAbsent(key, field);
        return field;
    }

    private static String renderConfig(Object config, ClassLoader loader) throws Exception {
        if (config == null) {
            throw new InvalidObjectException("Settings.config is null");
        }
        Class<?> optionsClass = Class.forName("com.typesafe.config.ConfigRenderOptions", false, loader);
        Object options = optionsClass.getMethod("defaults").invoke(null);
        options = optionsClass.getMethod("setJson", boolean.class).invoke(options, Boolean.FALSE);
        options = optionsClass.getMethod("setOriginComments", boolean.class).invoke(options, Boolean.FALSE);
        Class<?> configClass = Class.forName("com.typesafe.config.Config", false, loader);
        Class<?> configObjectClass = Class.forName("com.typesafe.config.ConfigObject", false, loader);
        Object root = configClass.getMethod("root").invoke(config);
        return (String) configObjectClass.getMethod("render", optionsClass).invoke(root, options);
    }

    private static Object lazyConfig(String renderedConfig, ClassLoader loader) throws Exception {
        Class<?> configClass = Class.forName("com.typesafe.config.Config", false, loader);
        return Proxy.newProxyInstance(
                configClass.getClassLoader(),
                new Class<?>[]{configClass},
                new LazyConfigInvocationHandler(renderedConfig, loader)
        );
    }

    private static void writeValue(DataOutputStream output, Object value) throws Exception {
        if (value == null) {
            output.writeByte(TAG_NULL);
        } else if (value instanceof Boolean) {
            output.writeByte(TAG_BOOLEAN);
            output.writeBoolean(((Boolean) value).booleanValue());
        } else if (value instanceof Integer) {
            output.writeByte(TAG_INTEGER);
            output.writeInt(((Integer) value).intValue());
        } else if (value instanceof Long) {
            output.writeByte(TAG_LONG);
            output.writeLong(((Long) value).longValue());
        } else if (value instanceof Float) {
            output.writeByte(TAG_FLOAT);
            output.writeFloat(((Float) value).floatValue());
        } else if (value instanceof Double) {
            output.writeByte(TAG_DOUBLE);
            output.writeDouble(((Double) value).doubleValue());
        } else if (value instanceof String) {
            output.writeByte(TAG_STRING);
            writeString(output, (String) value);
        } else if (value instanceof int[]) {
            output.writeByte(TAG_INT_ARRAY);
            int[] array = (int[]) value;
            output.writeInt(array.length);
            for (int element : array) {
                output.writeInt(element);
            }
        } else if (value instanceof double[]) {
            output.writeByte(TAG_DOUBLE_ARRAY);
            double[] array = (double[]) value;
            output.writeInt(array.length);
            for (double element : array) {
                output.writeDouble(element);
            }
        } else if (value instanceof InternetRulesSnapshot) {
            output.writeByte(TAG_INTERNET_RULES);
            writeStringArray(output, ((InternetRulesSnapshot) value).rules);
        } else if (value instanceof GameProfileSnapshot) {
            output.writeByte(TAG_GAME_PROFILE);
            GameProfileSnapshot profile = (GameProfileSnapshot) value;
            output.writeBoolean(profile.id != null);
            if (profile.id != null) {
                output.writeLong(profile.id.getMostSignificantBits());
                output.writeLong(profile.id.getLeastSignificantBits());
            }
            writeString(output, profile.name);
        } else if (value instanceof List && writeKnownList(output, (List<?>) value)) {
            return;
        } else if (isTuple2(value)) {
            output.writeByte(TAG_TUPLE2);
            writeValue(output, tupleElement(value, "_1"));
            writeValue(output, tupleElement(value, "_2"));
        } else if (value.getClass().isArray() && value.getClass().getComponentType().isEnum()) {
            output.writeByte(TAG_ENUM_ARRAY);
            writeString(output, value.getClass().getComponentType().getName());
            int length = Array.getLength(value);
            output.writeInt(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                writeString(output, element == null ? null : ((Enum<?>) element).name());
            }
        } else if (value instanceof Enum) {
            output.writeByte(TAG_ENUM);
            writeString(output, value.getClass().getName());
            writeString(output, ((Enum<?>) value).name());
        } else {
            writeSerializedValue(output, value);
        }
    }

    private static Object readValue(DataInputStream input, ClassLoader loader) throws Exception {
        byte tag = input.readByte();
        switch (tag) {
            case TAG_NULL:
                return null;
            case TAG_BOOLEAN:
                return Boolean.valueOf(input.readBoolean());
            case TAG_INTEGER:
                return Integer.valueOf(input.readInt());
            case TAG_LONG:
                return Long.valueOf(input.readLong());
            case TAG_FLOAT:
                return Float.valueOf(input.readFloat());
            case TAG_DOUBLE:
                return Double.valueOf(input.readDouble());
            case TAG_STRING:
                return readString(input);
            case TAG_INT_ARRAY:
                return readIntArray(input);
            case TAG_DOUBLE_ARRAY:
                return readDoubleArray(input);
            case TAG_STRING_LIST:
                return readStringList(input);
            case TAG_INTEGER_LIST:
                return readIntegerList(input);
            case TAG_INTERNET_RULES:
                return new InternetRulesSnapshot(readStringArray(input));
            case TAG_GAME_PROFILE:
                UUID id = null;
                if (input.readBoolean()) {
                    id = new UUID(input.readLong(), input.readLong());
                }
                return new GameProfileSnapshot(id, readString(input));
            case TAG_TUPLE2:
                return newTuple2(loader, readValue(input, loader), readValue(input, loader));
            case TAG_TUPLE2_ARRAY:
                return readTuple2Array(input, loader);
            case TAG_ENUM:
                return readEnum(input, loader);
            case TAG_ENUM_ARRAY:
                return readEnumArray(input, loader);
            case TAG_JAVA_SERIALIZED:
                return readSerializedValue(input, loader);
            default:
                throw new InvalidObjectException("Unknown Settings binary value tag: " + tag);
        }
    }

    private static boolean writeKnownList(DataOutputStream output, List<?> list) throws Exception {
        boolean strings = true;
        boolean integers = true;
        for (Object element : list) {
            strings &= element == null || element instanceof String;
            integers &= element == null || element instanceof Integer;
        }
        if (strings) {
            output.writeByte(TAG_STRING_LIST);
            output.writeInt(list.size());
            for (Object element : list) {
                writeString(output, (String) element);
            }
            return true;
        }
        if (integers) {
            output.writeByte(TAG_INTEGER_LIST);
            output.writeInt(list.size());
            for (Object element : list) {
                output.writeBoolean(element != null);
                if (element != null) {
                    output.writeInt(((Integer) element).intValue());
                }
            }
            return true;
        }
        return false;
    }

    private static List<String> readStringList(DataInputStream input) throws IOException {
        int length = checkedLength(input.readInt());
        ArrayList<String> result = new ArrayList<String>(length);
        for (int i = 0; i < length; i++) {
            result.add(readString(input));
        }
        return result;
    }

    private static List<Integer> readIntegerList(DataInputStream input) throws IOException {
        int length = checkedLength(input.readInt());
        ArrayList<Integer> result = new ArrayList<Integer>(length);
        for (int i = 0; i < length; i++) {
            result.add(input.readBoolean() ? Integer.valueOf(input.readInt()) : null);
        }
        return result;
    }

    private static void writeSerializedValue(DataOutputStream output, Object value) throws Exception {
        if (!(value instanceof Serializable)) {
            throw new NotSerializableException(value.getClass().getName());
        }
        output.writeByte(TAG_JAVA_SERIALIZED);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(bytes)) {
            objectOutput.writeObject(value);
        }
        byte[] data = bytes.toByteArray();
        output.writeInt(data.length);
        output.write(data);
    }

    private static Object readSerializedValue(DataInputStream input, ClassLoader loader) throws Exception {
        int length = checkedBlobLength(input.readInt());
        byte[] data = new byte[length];
        input.readFully(data);
        try (LoaderObjectInputStream objectInput = new LoaderObjectInputStream(new ByteArrayInputStream(data), loader)) {
            return objectInput.readObject();
        }
    }

    private static boolean isTuple2(Object value) {
        return value != null && "scala.Tuple2".equals(value.getClass().getName());
    }

    private static Object tupleElement(Object value, String name) throws Exception {
        return value.getClass().getMethod(name).invoke(value);
    }

    private static Object newTuple2(ClassLoader loader, Object left, Object right) throws Exception {
        Class<?> tuple = Class.forName("scala.Tuple2", false, loader);
        return tuple.getConstructor(Object.class, Object.class).newInstance(left, right);
    }

    private static Object readTuple2Array(DataInputStream input, ClassLoader loader) throws Exception {
        int length = checkedLength(input.readInt());
        Class<?> tuple = Class.forName("scala.Tuple2", false, loader);
        Object array = Array.newInstance(tuple, length);
        for (int i = 0; i < length; i++) {
            Array.set(array, i, newTuple2(loader, readValue(input, loader), readValue(input, loader)));
        }
        return array;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readEnum(DataInputStream input, ClassLoader loader) throws Exception {
        Class<?> type = Class.forName(readString(input), false, loader);
        String name = readString(input);
        return name == null ? null : Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object readEnumArray(DataInputStream input, ClassLoader loader) throws Exception {
        Class<?> type = Class.forName(readString(input), false, loader).asSubclass(Enum.class);
        int length = checkedLength(input.readInt());
        Object array = Array.newInstance(type, length);
        for (int i = 0; i < length; i++) {
            String name = readString(input);
            Array.set(array, i, name == null ? null : Enum.valueOf((Class<? extends Enum>) type, name));
        }
        return array;
    }

    private static void writeStringArray(DataOutputStream output, String[] array) throws IOException {
        if (array == null) {
            output.writeInt(-1);
            return;
        }
        output.writeInt(array.length);
        for (String value : array) {
            writeString(output, value);
        }
    }

    private static String[] readStringArray(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            return null;
        }
        length = checkedLength(length);
        String[] result = new String[length];
        for (int i = 0; i < length; i++) {
            result[i] = readString(input);
        }
        return result;
    }

    private static int[] readIntArray(DataInputStream input) throws IOException {
        int length = checkedLength(input.readInt());
        int[] array = new int[length];
        for (int i = 0; i < length; i++) {
            array[i] = input.readInt();
        }
        return array;
    }

    private static double[] readDoubleArray(DataInputStream input) throws IOException {
        int length = checkedLength(input.readInt());
        double[] array = new double[length];
        for (int i = 0; i < length; i++) {
            array[i] = input.readDouble();
        }
        return array;
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        if (value == null) {
            output.writeInt(-1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0) {
            return null;
        }
        length = checkedBlobLength(length);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int checkedLength(int length) throws InvalidObjectException {
        if (length < 0 || length > MAX_FIELDS * 16) {
            throw new InvalidObjectException("Invalid Settings binary collection length: " + length);
        }
        return length;
    }

    private static int checkedBlobLength(int length) throws InvalidObjectException {
        if (length < 0 || length > 8 * 1024 * 1024) {
            throw new InvalidObjectException("Invalid Settings binary blob length: " + length);
        }
        return length;
    }

    private static String signature(Class<?> settingsClass, File settingsFile) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        updateString(digest, "gpom-opencomputers-settings-v" + LEGACY_VERSION);
        updateResource(digest, settingsClass, "/application.conf");
        updateFile(digest, settingsFile);
        return hex(digest.digest());
    }

    private static void updateResource(MessageDigest digest, Class<?> type, String name) throws IOException {
        InputStream input = type.getResourceAsStream(name);
        if (input == null) {
            updateString(digest, "resource-missing:" + name);
            return;
        }
        try {
            updateString(digest, "resource:" + name);
            updateStream(digest, input);
        } finally {
            input.close();
        }
    }

    private static void updateFile(MessageDigest digest, File file) throws IOException {
        if (file == null || !file.isFile()) {
            updateString(digest, "file-missing");
            return;
        }
        updateString(digest, "file:" + file.length());
        FileInputStream input = new FileInputStream(file);
        try {
            updateStream(digest, input);
        } finally {
            input.close();
        }
    }

    private static void updateStream(MessageDigest digest, InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            digest.update(buffer, 0, read);
        }
        digest.update((byte) 0);
    }

    private static void updateString(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            chars[i * 2] = alphabet[value >>> 4];
            chars[i * 2 + 1] = alphabet[value & 15];
        }
        return new String(chars);
    }

    private static File cacheFile() {
        return GpomCaches.file("opencomputers-settings", "settings-v1.dat");
    }

    private static final class CacheImage implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String renderedConfig;
        private final Map<String, Object> fieldValues;

        private CacheImage(String renderedConfig, Map<String, Object> fieldValues) {
            this.renderedConfig = renderedConfig;
            this.fieldValues = fieldValues;
        }
    }

    private static final class InternetRulesSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String[] rules;

        private InternetRulesSnapshot(String[] rules) {
            this.rules = rules;
        }

        private static InternetRulesSnapshot capture(Object array) throws Exception {
            int length = Array.getLength(array);
            String[] rules = new String[length];
            for (int i = 0; i < length; i++) {
                Object rule = Array.get(array, i);
                if (rule != null) {
                    rules[i] = (String) rule.getClass().getMethod("ruleString").invoke(rule);
                }
            }
            return new InternetRulesSnapshot(rules);
        }

        private Object restore(Class<?> arrayType) throws Exception {
            Class<?> component = arrayType.getComponentType();
            if (component == null) {
                throw new InvalidObjectException("Expected InternetFilteringRule array");
            }
            Constructor<?> constructor = component.getConstructor(String.class);
            Object array = Array.newInstance(component, rules.length);
            for (int i = 0; i < rules.length; i++) {
                if (rules[i] != null) {
                    Array.set(array, i, constructor.newInstance(rules[i]));
                }
            }
            return array;
        }
    }

    private static final class GameProfileSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        private final UUID id;
        private final String name;

        private GameProfileSnapshot(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        private static GameProfileSnapshot capture(Object profile) throws Exception {
            Object properties = profile.getClass().getMethod("getProperties").invoke(profile);
            if (properties != null) {
                Object empty = properties.getClass().getMethod("isEmpty").invoke(properties);
                if (Boolean.FALSE.equals(empty)) {
                    throw new NotSerializableException("GameProfile has properties");
                }
            }
            UUID id = (UUID) profile.getClass().getMethod("getId").invoke(profile);
            String name = (String) profile.getClass().getMethod("getName").invoke(profile);
            return new GameProfileSnapshot(id, name);
        }

        private Object restore(ClassLoader loader) throws Exception {
            Class<?> type = Class.forName("com.mojang.authlib.GameProfile", false, loader);
            return type.getConstructor(UUID.class, String.class).newInstance(id, name);
        }
    }

    private static final class LazyConfigInvocationHandler implements InvocationHandler, Serializable {
        private static final long serialVersionUID = 1L;
        private final String renderedConfig;
        private transient ClassLoader loader;
        private transient Object delegate;

        private LazyConfigInvocationHandler(String renderedConfig, ClassLoader loader) {
            this.renderedConfig = renderedConfig;
            this.loader = loader;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if ("toString".equals(name) && method.getParameterTypes().length == 0) {
                return "GPOM lazy OpenComputers Config";
            }
            if ("hashCode".equals(name) && method.getParameterTypes().length == 0) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(name) && method.getParameterTypes().length == 1) {
                return proxy == args[0];
            }
            try {
                return method.invoke(delegate(), args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }

        private Object delegate() throws Exception {
            Object cached = delegate;
            if (cached != null) {
                return cached;
            }
            ClassLoader activeLoader = loader != null ? loader : Thread.currentThread().getContextClassLoader();
            Class<?> factory = Class.forName("com.typesafe.config.ConfigFactory", true, activeLoader);
            cached = factory.getMethod("parseString", String.class).invoke(null, renderedConfig);
            delegate = cached;
            return cached;
        }
    }

    private static final class LoaderObjectInputStream extends ObjectInputStream {
        private final ClassLoader loader;

        private LoaderObjectInputStream(InputStream input, ClassLoader loader) throws IOException {
            super(input);
            this.loader = loader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String name = desc.getName();
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
                return super.resolveClass(desc);
            }
        }
    }

    private static final class UnsafeAccess {
        private static final Object UNSAFE;
        private static final Method ALLOCATE_INSTANCE;
        private static final Method OBJECT_FIELD_OFFSET;
        private static final Method PUT_OBJECT;
        private static final Method PUT_INT;
        private static final Method PUT_BOOLEAN;
        private static final Method PUT_DOUBLE;
        private static final Method PUT_FLOAT;
        private static final Method PUT_LONG;
        private static final ConcurrentMap<Field, Long> FIELD_OFFSETS = new ConcurrentHashMap<Field, Long>();

        static {
            Object unsafe = null;
            Method allocateInstance = null;
            Method objectFieldOffset = null;
            Method putObject = null;
            Method putInt = null;
            Method putBoolean = null;
            Method putDouble = null;
            Method putFloat = null;
            Method putLong = null;
            try {
                Class<?> type = Class.forName("sun.misc.Unsafe");
                Field field = type.getDeclaredField("theUnsafe");
                field.setAccessible(true);
                unsafe = field.get(null);
                allocateInstance = type.getMethod("allocateInstance", Class.class);
                objectFieldOffset = type.getMethod("objectFieldOffset", Field.class);
                putObject = type.getMethod("putObject", Object.class, long.class, Object.class);
                putInt = type.getMethod("putInt", Object.class, long.class, int.class);
                putBoolean = type.getMethod("putBoolean", Object.class, long.class, boolean.class);
                putDouble = type.getMethod("putDouble", Object.class, long.class, double.class);
                putFloat = type.getMethod("putFloat", Object.class, long.class, float.class);
                putLong = type.getMethod("putLong", Object.class, long.class, long.class);
            } catch (Throwable ignored) {
                unsafe = null;
            }
            UNSAFE = unsafe;
            ALLOCATE_INSTANCE = allocateInstance;
            OBJECT_FIELD_OFFSET = objectFieldOffset;
            PUT_OBJECT = putObject;
            PUT_INT = putInt;
            PUT_BOOLEAN = putBoolean;
            PUT_DOUBLE = putDouble;
            PUT_FLOAT = putFloat;
            PUT_LONG = putLong;
        }

        private static Object allocateInstance(Class<?> type) throws Exception {
            if (UNSAFE == null || ALLOCATE_INSTANCE == null) {
                throw new IllegalStateException("sun.misc.Unsafe unavailable");
            }
            return ALLOCATE_INSTANCE.invoke(UNSAFE, type);
        }

        private static void putField(Field field, Object target, Object value) throws Exception {
            if (UNSAFE == null || OBJECT_FIELD_OFFSET == null) {
                field.set(target, value);
                return;
            }

            long offset = fieldOffset(field);
            Class<?> type = field.getType();
            if (type == int.class) {
                PUT_INT.invoke(UNSAFE, target, offset, ((Number) value).intValue());
            } else if (type == boolean.class) {
                PUT_BOOLEAN.invoke(UNSAFE, target, offset, ((Boolean) value).booleanValue());
            } else if (type == double.class) {
                PUT_DOUBLE.invoke(UNSAFE, target, offset, ((Number) value).doubleValue());
            } else if (type == float.class) {
                PUT_FLOAT.invoke(UNSAFE, target, offset, ((Number) value).floatValue());
            } else if (type == long.class) {
                PUT_LONG.invoke(UNSAFE, target, offset, ((Number) value).longValue());
            } else {
                PUT_OBJECT.invoke(UNSAFE, target, offset, value);
            }
        }

        private static long fieldOffset(Field field) throws Exception {
            Long cached = FIELD_OFFSETS.get(field);
            if (cached != null) {
                return cached.longValue();
            }
            Long offset = (Long) OBJECT_FIELD_OFFSET.invoke(UNSAFE, field);
            Long previous = FIELD_OFFSETS.putIfAbsent(field, offset);
            return previous != null ? previous.longValue() : offset.longValue();
        }
    }
}
