package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CraftTweakerPreInitOptimizations {
    private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("gpom.crafttweaker.fastZenRegister", "false"));
    private static final String ZEN_REGISTER = "crafttweaker.annotations.ZenRegister";
    private static final String ZEN_EXPANSION = "stanhebben.zenscript.annotations.ZenExpansion";
    private static final String ZEN_CLASS = "stanhebben.zenscript.annotations.ZenClass";
    private static final String BRACKET_HANDLER = "crafttweaker.annotations.BracketHandler";
    private static final String MOD_ONLY = "crafttweaker.annotations.ModOnly";
    private static final String MODS_ONLY = "crafttweaker.annotations.ModsOnly";
    private static final String ON_REGISTER = "crafttweaker.annotations.OnRegister";

    private static volatile Reflection reflection;
    private static volatile boolean fallbackLogged;

    private CraftTweakerPreInitOptimizations() {
    }

    public static boolean registerZenClasses(ASMDataTable asmData, ClassLoader loader) {
        if (!ENABLED || asmData == null || loader == null) {
            return false;
        }

        Reflection methods;
        try {
            methods = reflection(loader);
        } catch (Throwable throwable) {
            logFallback("CraftTweaker fast ZenRegister helper unavailable; using stock registration", throwable);
            return false;
        }

        Set<ASMDataTable.ASMData> registrations = asmData.getAll(ZEN_REGISTER);
        if (registrations == null || registrations.isEmpty()) {
            return true;
        }

        Set<String> expansions = classSet(asmData.getAll(ZEN_EXPANSION));
        Set<String> nativeClasses = classSet(asmData.getAll(ZEN_CLASS));
        Set<String> bracketHandlers = classSet(asmData.getAll(BRACKET_HANDLER));
        Map<String, ASMDataTable.ASMData> modOnly = firstByClass(asmData.getAll(MOD_ONLY));
        Map<String, List<ASMDataTable.ASMData>> modsOnly = allByClass(asmData.getAll(MODS_ONLY));
        Map<String, Set<String>> onRegisterMethods = methodsByClass(asmData.getAll(ON_REGISTER));

        int loaded = 0;
        int skipped = 0;
        int slowFallbacks = 0;
        for (ASMDataTable.ASMData data : registrations) {
            if (data == null || data.getClassName() == null) {
                continue;
            }
            String className = data.getClassName();
            try {
                Class<?> type = Class.forName(className, false, loader);
                if (!passesModFilters(type, modOnly.get(className), modsOnly.get(className), methods)) {
                    skipped++;
                    continue;
                }
                fastRegisterClass(
                        type,
                        expansions.contains(className),
                        nativeClasses.contains(className),
                        bracketHandlers.contains(className),
                        onRegisterMethods.get(className),
                        methods
                );
                loaded++;
            } catch (ClassNotFoundException throwable) {
                GPOM.LOGGER.warn("[CraftTweaker Optimizations] Could not load ZenRegister class {}", className, throwable);
            } catch (Throwable throwable) {
                slowFallbacks++;
                if (!trySlowRegister(className, loader, methods, throwable)) {
                    GPOM.LOGGER.warn("[CraftTweaker Optimizations] Fast ZenRegister failed for {}", className, throwable);
                }
            }
        }

        if (loaded > 0 || skipped > 0 || slowFallbacks > 0) {
            GPOM.LOGGER.info(
                    "[CraftTweaker Optimizations] Fast-registered {} ZenRegister class(es), skipped {}, slowFallbacks={}",
                    loaded,
                    skipped,
                    slowFallbacks
            );
        }
        return true;
    }

    private static boolean passesModFilters(Class<?> type,
                                            ASMDataTable.ASMData modOnlyData,
                                            List<ASMDataTable.ASMData> modsOnlyData,
                                            Reflection methods) throws Exception {
        List<String> singleMod = annotationValues(modOnlyData);
        if (!singleMod.isEmpty()) {
            return Loader.isModLoaded(singleMod.get(0));
        }
        if (modOnlyData != null) {
            Annotation annotation = type.getAnnotation(methods.modOnlyAnnotation);
            if (annotation != null) {
                String modId = (String) methods.modOnlyValue.invoke(annotation);
                return Loader.isModLoaded(modId);
            }
        }

        List<String> requiredMods = annotationValues(modsOnlyData);
        if (!requiredMods.isEmpty()) {
            for (String modId : requiredMods) {
                if (!Loader.isModLoaded(modId)) {
                    return false;
                }
            }
            return true;
        }
        if (modsOnlyData != null && !modsOnlyData.isEmpty()) {
            Annotation annotation = type.getAnnotation(methods.modsOnlyAnnotation);
            if (annotation != null) {
                String[] modIds = (String[]) methods.modsOnlyValue.invoke(annotation);
                for (String modId : modIds) {
                    if (!Loader.isModLoaded(modId)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void fastRegisterClass(Class<?> type,
                                          boolean isExpansion,
                                          boolean isNativeClass,
                                          boolean isBracketHandler,
                                          Set<String> onRegisterMethodNames,
                                          Reflection methods) throws Exception {
        boolean registered = false;
        if (isExpansion) {
            methods.registerExpansion.invoke(null, type);
            registered = true;
        }
        if (isNativeClass) {
            methods.registerNativeClass.invoke(null, type);
            registered = true;
        }
        if (isBracketHandler && methods.bracketHandlerType.isAssignableFrom(type)) {
            Object handler = type.newInstance();
            methods.registerBracketHandler.invoke(null, handler);
            registered = true;
        }
        if (!registered || onRegisterMethodNames == null || onRegisterMethodNames.isEmpty()) {
            return;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (!onRegisterMethodNames.contains(method.getName())) {
                continue;
            }
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers)
                    && Modifier.isPublic(modifiers)
                    && method.isAnnotationPresent(methods.onRegisterAnnotation)) {
                method.invoke(null);
            }
        }
    }

    private static boolean trySlowRegister(String className, ClassLoader loader, Reflection methods, Throwable originalFailure) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            methods.registerClass.invoke(null, type);
            return true;
        } catch (Throwable fallbackFailure) {
            fallbackFailure.addSuppressed(originalFailure);
            GPOM.LOGGER.warn("[CraftTweaker Optimizations] Slow ZenRegister fallback failed for {}", className, fallbackFailure);
            return false;
        }
    }

    private static Reflection reflection(ClassLoader loader) throws Exception {
        Reflection current = reflection;
        if (current != null) {
            return current;
        }
        synchronized (CraftTweakerPreInitOptimizations.class) {
            current = reflection;
            if (current != null) {
                return current;
            }
            Class<?> globalRegistry = Class.forName("crafttweaker.zenscript.GlobalRegistry", false, loader);
            Class<?> api = Class.forName("crafttweaker.CraftTweakerAPI", false, loader);
            Class<?> bracketHandlerType = Class.forName("crafttweaker.zenscript.IBracketHandler", false, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> modOnly = (Class<? extends Annotation>) Class.forName(MOD_ONLY, false, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> modsOnly = (Class<? extends Annotation>) Class.forName(MODS_ONLY, false, loader);
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> onRegister = (Class<? extends Annotation>) Class.forName(ON_REGISTER, false, loader);
            current = new Reflection(
                    globalRegistry.getMethod("registerExpansion", Class.class),
                    globalRegistry.getMethod("registerNativeClass", Class.class),
                    api.getMethod("registerBracketHandler", bracketHandlerType),
                    api.getMethod("registerClass", Class.class),
                    bracketHandlerType,
                    modOnly,
                    modOnly.getMethod("value"),
                    modsOnly,
                    modsOnly.getMethod("value"),
                    onRegister
            );
            reflection = current;
            return current;
        }
    }

    private static Set<String> classSet(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null) {
                result.add(entry.getClassName());
            }
        }
        return result;
    }

    private static Map<String, ASMDataTable.ASMData> firstByClass(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ASMDataTable.ASMData> result = new HashMap<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null && !result.containsKey(entry.getClassName())) {
                result.put(entry.getClassName(), entry);
            }
        }
        return result;
    }

    private static Map<String, List<ASMDataTable.ASMData>> allByClass(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<ASMDataTable.ASMData>> result = new HashMap<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null) {
                result.computeIfAbsent(entry.getClassName(), ignored -> new ArrayList<>()).add(entry);
            }
        }
        return result;
    }

    private static Map<String, Set<String>> methodsByClass(Set<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<String>> result = new HashMap<>();
        for (ASMDataTable.ASMData entry : data) {
            if (entry != null && entry.getClassName() != null && entry.getObjectName() != null) {
                result.computeIfAbsent(entry.getClassName(), ignored -> new HashSet<>()).add(entry.getObjectName());
            }
        }
        return result;
    }

    private static List<String> annotationValues(List<ASMDataTable.ASMData> data) {
        if (data == null || data.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (ASMDataTable.ASMData entry : data) {
            result.addAll(annotationValues(entry));
        }
        return result;
    }

    private static List<String> annotationValues(ASMDataTable.ASMData data) {
        if (data == null || data.getAnnotationInfo() == null) {
            return Collections.emptyList();
        }
        return stringValues(data.getAnnotationInfo().get("value"));
    }

    private static List<String> stringValues(Object value) {
        if (value == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        if (value instanceof String) {
            result.add((String) value);
        } else if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                addStringValue(result, item);
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                addStringValue(result, Array.get(value, i));
            }
        } else {
            addStringValue(result, value);
        }
        return result;
    }

    private static void addStringValue(List<String> values, Object value) {
        if (value instanceof String && !((String) value).isEmpty()) {
            values.add((String) value);
        }
    }

    private static void logFallback(String message, Throwable throwable) {
        if (!fallbackLogged) {
            fallbackLogged = true;
            GPOM.LOGGER.warn("[CraftTweaker Optimizations] " + message, throwable);
        }
    }

    private static final class Reflection {
        private final Method registerExpansion;
        private final Method registerNativeClass;
        private final Method registerBracketHandler;
        private final Method registerClass;
        private final Class<?> bracketHandlerType;
        private final Class<? extends Annotation> modOnlyAnnotation;
        private final Method modOnlyValue;
        private final Class<? extends Annotation> modsOnlyAnnotation;
        private final Method modsOnlyValue;
        private final Class<? extends Annotation> onRegisterAnnotation;

        private Reflection(Method registerExpansion,
                           Method registerNativeClass,
                           Method registerBracketHandler,
                           Method registerClass,
                           Class<?> bracketHandlerType,
                           Class<? extends Annotation> modOnlyAnnotation,
                           Method modOnlyValue,
                           Class<? extends Annotation> modsOnlyAnnotation,
                           Method modsOnlyValue,
                           Class<? extends Annotation> onRegisterAnnotation) {
            this.registerExpansion = registerExpansion;
            this.registerNativeClass = registerNativeClass;
            this.registerBracketHandler = registerBracketHandler;
            this.registerClass = registerClass;
            this.bracketHandlerType = bracketHandlerType;
            this.modOnlyAnnotation = modOnlyAnnotation;
            this.modOnlyValue = modOnlyValue;
            this.modsOnlyAnnotation = modsOnlyAnnotation;
            this.modsOnlyValue = modsOnlyValue;
            this.onRegisterAnnotation = onRegisterAnnotation;
        }
    }
}
