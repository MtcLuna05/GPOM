package com.l.gpom.client;

import com.l.gpom.compat.advancedrocketry.AdvancedRocketryOxygenOverlayHandoffGuard;
import com.l.gpom.util.ReflectionFields;
import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import java.lang.reflect.Array;
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
            AdvancedRocketryOxygenOverlayHandoffGuard.beginGuard(reason);
        };
        if (ClientAccess.isMinecraftThread(minecraft)) {
            cleanup.run();
        } else {
            ClientAccess.schedule(minecraft, cleanup);
        }
    }

    private static void clearMinecraftParticles(Minecraft minecraft) {
        Object effectRenderer = ReflectionFields.get(minecraft, "effectRenderer", "effectRenderer", "field_71452_i", "j");
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

        clearCollectionArray(effectRenderer, "fxLayers", "fxLayers", "field_78876_b", "b");
        clearCollection(effectRenderer, "particleEmitters", "particleEmitters", "field_178933_d", "d");
    }

    private static Object currentWorld(Minecraft minecraft) {
        return ReflectionFields.get(minecraft, "world", "world", "field_71441_e", "f");
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
            clearAstralEffectCollections(effectHandler);
        } catch (Throwable ignored) {
        }
    }

    private static void clearAstralEffectCollections(Class<?> effectHandler) {
        clearStaticCollection(effectHandler, "toAddBuffer");
        clearStaticCollection(effectHandler, "fastRenderDepthParticles");
        clearStaticCollection(effectHandler, "fastRenderParticles");
        clearStaticCollection(effectHandler, "fastRenderGatewayParticles");
        clearStaticCollection(effectHandler, "fastRenderLightnings");

        // Preserve the render target/depth maps; AUSM expects those buckets to keep existing.
        Object complexEffects = ReflectionFields.getStatic(effectHandler, "complexEffects", "complexEffects");
        if (complexEffects instanceof Map) {
            for (Object byLayer : ((Map<?, ?>) complexEffects).values()) {
                clearNestedCollections(byLayer);
            }
        }

        Object objects = ReflectionFields.getStatic(effectHandler, "objects", "objects");
        if (objects instanceof Map) {
            ((Map<?, ?>) objects).clear();
        }

        Object instance = ReflectionFields.getStatic(effectHandler, "instance", "instance");
        if (instance != null) {
            ReflectionFields.set(instance, null, "uiGateway", "uiGateway");
            ReflectionFields.set(instance, null, "structurePreview", "structurePreview");
            ReflectionFields.set(instance, null, "influenceSizePreview", "influenceSizePreview");
        }
    }

    private static void clearStaticCollection(Class<?> owner, String name) {
        Object value = ReflectionFields.getStatic(owner, name, name);
        if (value instanceof Collection) {
            ((Collection<?>) value).clear();
        }
    }

    private static void clearCollection(Object owner, String purpose, String... names) {
        Object value = ReflectionFields.get(owner, purpose, names);
        if (value instanceof Collection) {
            ((Collection<?>) value).clear();
        }
    }

    private static void clearCollectionArray(Object owner, String purpose, String... names) {
        clearNestedCollections(ReflectionFields.get(owner, purpose, names));
    }

    private static void clearNestedCollections(Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection) {
            ((Collection<?>) value).clear();
            return;
        }
        if (value instanceof Map) {
            for (Object nested : ((Map<?, ?>) value).values()) {
                clearNestedCollections(nested);
            }
            return;
        }
        Class<?> type = value.getClass();
        if (!type.isArray()) {
            return;
        }
        int length = Array.getLength(value);
        for (int i = 0; i < length; i++) {
            clearNestedCollections(Array.get(value, i));
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
