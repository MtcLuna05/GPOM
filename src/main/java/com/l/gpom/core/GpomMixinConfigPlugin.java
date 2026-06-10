package com.l.gpom.core;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

public final class GpomMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();
    private static final String[] BAUBLES_TARGETS = {
            "baubles/api/BaublesApi.class",
            "baubles/common/container/SlotBauble.class"
    };

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".baubles.")) {
            boolean enabled = GpomEarlyConfig.baublesSideSlotsEnabled();
            boolean present = enabled && allResourcesPresent(BAUBLES_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM Baubles] mixin={} enabled={} baublesPresent={}",
                        mixinClassName, enabled, present);
            }
            return present;
        }
        return true;
    }

    private static boolean allResourcesPresent(String[] resources) {
        for (String resource : resources) {
            if (!resourcePresent(resource)) {
                return false;
            }
        }
        return true;
    }

    private static boolean resourcePresent(String resource) {
        return resourcePresent(GpomMixinConfigPlugin.class.getClassLoader(), resource)
                || resourcePresent(Thread.currentThread().getContextClassLoader(), resource)
                || resourcePresent(Launch.classLoader, resource)
                || ClassLoader.getSystemResource(resource) != null
                || resourcePresentInModsDirectory(resource);
    }

    private static boolean resourcePresent(ClassLoader loader, String resource) {
        try {
            return loader != null && loader.getResource(resource) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean resourcePresentInModsDirectory(String resource) {
        File modsDirectory = new File(System.getProperty("user.dir", "."), "mods");
        if (!modsDirectory.isDirectory()) {
            return false;
        }

        File[] files = modsDirectory.listFiles((dir, name) -> {
            String lower = name.toLowerCase(java.util.Locale.ROOT);
            return lower.endsWith(".jar") || lower.endsWith(".zip");
        });
        if (files == null) {
            return false;
        }

        for (File file : files) {
            try (JarFile jar = new JarFile(file)) {
                if (jar.getEntry(resource) != null) {
                    return true;
                }
            } catch (IOException | RuntimeException ignored) {
            }
        }
        return false;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
