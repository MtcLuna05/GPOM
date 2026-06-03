package com.l.gpom.optimization;

import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import com.l.gpom.profiling.StartupProfiler;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class BetweenlandsParticleOptimizations {
    private static final boolean DEFER_PARTICLE_STITCHERS = Boolean.parseBoolean(System.getProperty("gpom.betweenlands.deferParticleStitchers", "true"));
    private static final AtomicBoolean DEFERRED = new AtomicBoolean();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static volatile boolean failureLogged;

    private BetweenlandsParticleOptimizations() {
    }

    public static void deferParticleStitchers() {
        if (DEFER_PARTICLE_STITCHERS && TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.render.particle.BLParticles")) {
            DEFERRED.set(true);
        } else {
            ensureParticleStitchersRegistered();
        }
    }

    public static void ensureParticleStitchersRegistered() {
        if (!DEFERRED.get() || REGISTERED.get() || !DEFER_PARTICLE_STITCHERS
                || !TargetedModVersions.isBetweenlandsClass("thebetweenlands.client.render.particle.BLParticles")) {
            return;
        }
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }
        long startedAt = StartupProfiler.beginProbe();
        try {
            ClassLoader loader = Launch.classLoader != null ? Launch.classLoader : BetweenlandsParticleOptimizations.class.getClassLoader();
            Class<?> particles = Class.forName("thebetweenlands.client.render.particle.BLParticles", true, loader);
            Class<?> textureStitcher = Class.forName("thebetweenlands.client.handler.TextureStitchHandler$TextureStitcher", true, loader);
            Class<?> textureHandler = Class.forName("thebetweenlands.client.handler.TextureStitchHandler", true, loader);
            Constructor<?> stitcherConstructor = textureStitcher.getConstructor(Consumer.class, ResourceLocation[].class);
            Method values = particles.getMethod("values");
            Method getFactory = particles.getMethod("getFactory");
            Method getStitcher = Class.forName("thebetweenlands.client.render.particle.ParticleFactory", true, loader).getMethod("getStitcher");
            Class<?> particleTextureStitcher = Class.forName("thebetweenlands.client.render.particle.ParticleTextureStitcher", true, loader);
            Method getTextures = particleTextureStitcher.getMethod("getTextures");
            Method shouldSplitAnimations = particleTextureStitcher.getMethod("shouldSplitAnimations");
            Method setSplitFrames = textureStitcher.getMethod("setSplitFrames", boolean.class);
            Method getFrames = textureStitcher.getMethod("getFrames");
            Method setFrames = particleTextureStitcher.getMethod("setFrames", Class.forName("[[Lthebetweenlands.client.handler.TextureStitchHandler$Frame;", false, loader));
            Field instanceField = textureHandler.getField("INSTANCE");
            Method registerTextureStitcher = textureHandler.getMethod("registerTextureStitcher", textureStitcher);
            Object handler = instanceField.get(null);

            Object[] particleValues = (Object[]) values.invoke(null);
            for (Object particle : particleValues) {
                Object factory = getFactory.invoke(particle);
                if (factory == null) {
                    continue;
                }
                final Object particleStitcher = getStitcher.invoke(factory);
                if (particleStitcher == null) {
                    continue;
                }
                ResourceLocation[] textures = (ResourceLocation[]) getTextures.invoke(particleStitcher);
                Consumer<Object> callback = new Consumer<Object>() {
                    @Override
                    public void accept(Object stitcher) {
                        try {
                            setFrames.invoke(particleStitcher, getFrames.invoke(stitcher));
                        } catch (ReflectiveOperationException exception) {
                            throw new RuntimeException("Failed to push Betweenlands particle stitcher frames", exception);
                        }
                    }
                };
                Object stitcher = stitcherConstructor.newInstance(callback, textures);
                Object split = shouldSplitAnimations.invoke(particleStitcher);
                setSplitFrames.invoke(stitcher, Boolean.TRUE.equals(split));
                registerTextureStitcher.invoke(handler, stitcher);
            }
        } catch (Throwable throwable) {
            REGISTERED.set(false);
            if (!failureLogged) {
                failureLogged = true;
                GPOM.LOGGER.warn("Betweenlands deferred particle stitcher registration failed", throwable);
            }
        } finally {
            StartupProfiler.endProbeAlways("BL deferred particle stitcher realization", startedAt);
        }
    }
}
