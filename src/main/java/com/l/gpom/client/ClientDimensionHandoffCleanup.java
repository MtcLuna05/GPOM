package com.l.gpom.client;

import com.l.gpom.util.ReflectionFields;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public final class ClientDimensionHandoffCleanup {
    private static final String ASTRAL_EFFECT_HANDLER = "hellfirepvp.astralsorcery.client.effect.EffectHandler";

    private static volatile Method clearParticleEffectsMethod;
    private static volatile Method astralEffectCleanupMethod;
    private static volatile boolean astralEffectCleanupMissing;

    private ClientDimensionHandoffCleanup() {
    }

    // Keep this scoped to visual/transient state; BetterPortals may still own live view worlds during swaps.
    public static void cleanup(String reason) {
        Minecraft minecraft = ClientAccess.minecraft();
        if (minecraft == null) {
            return;
        }

        Runnable cleanup = () -> {
            clearMinecraftParticles(minecraft);
            cleanupAstralEffects();
        };
        if (ClientAccess.isMinecraftThread(minecraft)) {
            cleanup.run();
        } else {
            ClientAccess.schedule(minecraft, cleanup);
        }
    }

    private static void clearMinecraftParticles(Minecraft minecraft) {
        Object effectRenderer = ReflectionFields.get(minecraft, "effectRenderer", "field_71452_i", "effectRenderer");
        if (effectRenderer == null) {
            return;
        }

        try {
            Method method = clearParticleEffectsMethod;
            if (method == null) {
                method = findMethod(effectRenderer.getClass(), new Class<?>[] {World.class}, "func_78870_a", "clearEffects");
                clearParticleEffectsMethod = method;
            }
            if (method != null) {
                method.invoke(effectRenderer, currentWorld(minecraft));
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object currentWorld(Minecraft minecraft) {
        return ReflectionFields.get(minecraft, "world", "field_71441_e", "world");
    }

    private static void cleanupAstralEffects() {
        if (astralEffectCleanupMissing) {
            return;
        }

        try {
            Class<?> effectHandler = loadClass(ASTRAL_EFFECT_HANDLER);
            if (effectHandler == null) {
                astralEffectCleanupMissing = true;
                return;
            }

            Method method = astralEffectCleanupMethod;
            if (method == null) {
                method = findMethod(effectHandler, new Class<?>[0], "cleanUp");
                astralEffectCleanupMethod = method;
            }
            if (method != null) {
                method.invoke(null);
            }
            clearAstralStaticEffectCollections(effectHandler);
        } catch (Throwable ignored) {
        }
    }

    private static void clearAstralStaticEffectCollections(Class<?> effectHandler) {
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "complexEffects", "complexEffects"));
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "toAddBuffer", "toAddBuffer"));
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "fastRenderDepthParticles", "fastRenderDepthParticles"));
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "fastRenderParticles", "fastRenderParticles"));
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "fastRenderGatewayParticles", "fastRenderGatewayParticles"));
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "fastRenderLightnings", "fastRenderLightnings"));
        clearNestedCollections(ReflectionFields.getStatic(effectHandler, "objects", "objects"));
    }

    private static void clearNestedCollections(Object value) {
        if (value instanceof Map) {
            for (Object nested : ((Map<?, ?>) value).values()) {
                clearNestedCollections(nested);
            }
            ((Map<?, ?>) value).clear();
            return;
        }
        if (value instanceof Collection) {
            ((Collection<?>) value).clear();
        }
    }

    private static Method findMethod(Class<?> owner, Class<?>[] parameters, String... names) {
        Class<?> current = owner;
        while (current != null) {
            for (String name : names) {
                try {
                    Method method = current.getDeclaredMethod(name, parameters);
                    method.setAccessible(true);
                    return method;
                } catch (Throwable ignored) {
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> loadClass(String className) {
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            if (contextLoader != null) {
                return Class.forName(className, false, contextLoader);
            }
        } catch (Throwable ignored) {
        }
        try {
            return Class.forName(className, false, ClientDimensionHandoffCleanup.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
