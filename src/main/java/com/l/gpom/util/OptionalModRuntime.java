package com.l.gpom.util;

import net.minecraft.launchwrapper.Launch;

public final class OptionalModRuntime {
    private static final String AUSM_MAIN = "com/l/ausm/impl/MainMod";

    private static volatile Boolean ausmPresent;

    private OptionalModRuntime() {
    }

    public static boolean ausmPresent() {
        Boolean cached = ausmPresent;
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean present = classResourceExists(AUSM_MAIN);
        ausmPresent = Boolean.valueOf(present);
        return present;
    }

    public static boolean classResourceExists(String internalName) {
        String resource = internalName + ".class";
        if (resourcePresent(OptionalModRuntime.class.getClassLoader(), resource)) {
            return true;
        }
        try {
            if (Launch.classLoader != null && resourcePresent(Launch.classLoader, resource)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return resourcePresent(Thread.currentThread().getContextClassLoader(), resource);
    }

    private static boolean resourcePresent(ClassLoader loader, String resource) {
        if (loader == null) {
            return false;
        }
        try {
            return loader.getResource(resource) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
