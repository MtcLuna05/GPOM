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
    private static final String[] BETTER_PORTALS_CLIENT_WORLD_TARGETS = {
            "de/johni0702/minecraft/view/impl/client/ClientWorldsManagerImpl.class"
    };
    private static final String[] BETTER_PORTALS_SERVER_WORLD_TARGETS = {
            "de/johni0702/minecraft/view/impl/ViewAPIImplKt.class",
            "de/johni0702/minecraft/view/impl/mixin/MixinPlayerChunkMap.class"
    };
    private static final String[] AGRICRAFT_CHANNEL_TARGETS = {
            "com/infinityraider/agricraft/blocks/irrigation/AbstractBlockWaterChannel.class",
            "com/infinityraider/agricraft/api/v1/misc/IAgriConnectable.class"
    };
    private static final String[] SFM_SEARCH_TARGETS = {
            "vswe/superfactory/util/SearchUtil.class"
    };
    private static final String[] SFM_LOGIN_TARGETS = {
            "vswe/superfactory/client/IndexItemsOnLogin.class",
            "vswe/superfactory/util/SearchUtil.class"
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
        if (mixinClassName.equals("com.l.gpom.mixin.betterportals.MixinClientWorldsManagerImplDimensionHandoffCleanup")) {
            boolean present = allResourcesPresent(BETTER_PORTALS_CLIENT_WORLD_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM BetterPortals Handoff Cleanup] mixin={} targetsPresent={} aggressiveClientWorldCleanup={}",
                        mixinClassName, present, GpomEarlyConfig.betterPortalsCleanupClientWorldsEnabled());
            }
            return present;
        }
        if (mixinClassName.equals("com.l.gpom.mixin.betterportals.MixinClientWorldsManagerImplDestroyMainViewGuard")) {
            boolean present = allResourcesPresent(BETTER_PORTALS_CLIENT_WORLD_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM BetterPortals Guard] mixin={} targetsPresent={}",
                        mixinClassName, present);
            }
            return present;
        }
        if (mixinClassName.equals("com.l.gpom.mixin.betterportals.MixinPlayerChunkMapMissingWorldManagerGuard")) {
            boolean present = allResourcesPresent(BETTER_PORTALS_SERVER_WORLD_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM BetterPortals Guard] mixin={} targetsPresent={}",
                        mixinClassName, present);
            }
            return present;
        }
        if (mixinClassName.equals("com.l.gpom.mixin.agricraft.MixinWorldAgriCraftChannelBulkPlacement")) {
            boolean enabled = GpomEarlyConfig.agriCraftRefreshChannelsAfterBulkPlacementEnabled();
            boolean present = enabled && allResourcesPresent(AGRICRAFT_CHANNEL_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM AgriCraft Channels] mixin={} enabled={} targetsPresent={}",
                        mixinClassName, enabled, present);
            }
            return present;
        }
        if (mixinClassName.equals("com.l.gpom.mixin.sfm.MixinSearchUtilLightweightCache")) {
            boolean enabled = GpomEarlyConfig.sfmLightweightSearchCacheEnabled();
            boolean present = enabled && allResourcesPresent(SFM_SEARCH_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM SFM] mixin={} enabled={} targetsPresent={}",
                        mixinClassName, enabled, present);
            }
            return present;
        }
        if (mixinClassName.equals("com.l.gpom.mixin.sfm.MixinIndexItemsOnLoginLightweightCache")) {
            boolean enabled = GpomEarlyConfig.sfmLightweightSearchCacheEnabled();
            boolean present = enabled && allResourcesPresent(SFM_LOGIN_TARGETS);
            if (LOGGED.add(mixinClassName)) {
                GPOM.LOGGER.info("[GPOM SFM] mixin={} enabled={} targetsPresent={}",
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
