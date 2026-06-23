package com.l.gpom.mixin.betterportals;

import com.l.gpom.GPOM;
import com.l.gpom.config.GpomEarlyConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(targets = "de.johni0702.minecraft.view.impl.server.ServerWorldsManagerImpl", remap = false)
public abstract class MixinServerWorldsManagerMissingTransferRepair {
    @Unique
    private static final String GPOM_BP_WORLDS_MANAGER_CLASS = "de.johni0702.minecraft.view.impl.server.ServerWorldsManagerImpl";
    @Unique
    private static final String GPOM_BP_WORLD_MANAGER_CLASS = "de.johni0702.minecraft.view.impl.server.ServerWorldManager";
    @Unique
    private static final Set<String> GPOM_LOGGED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    @Unique
    private static volatile Field gpom$entityWorldField;

    @Shadow
    public abstract Map getWorldManagers();

    @Shadow
    public abstract EntityPlayerMP getPlayer();

    @Inject(method = "updateActiveViews", at = @At("HEAD"))
    private void gpom$repairMissingBetterPortalsDimensionTransfer(CallbackInfo ci) {
        try {
            EntityPlayerMP player = getPlayer();
            if (player == null) {
                return;
            }

            World world = gpom$playerWorld(player);
            if (!(world instanceof WorldServer)) {
                return;
            }

            WorldServer playerWorld = (WorldServer) world;
            Map worldManagers = getWorldManagers();
            if (worldManagers == null || worldManagers.containsKey(playerWorld)) {
                return;
            }

            worldManagers.put(playerWorld, gpom$createWorldManager(playerWorld, player));
            if (GpomEarlyConfig.optimizationInfoLogsEnabled() && GPOM_LOGGED.add("repair-missed-transfer")) {
                GPOM.LOGGER.warn("[GPOM BetterPortals Guard] Repaired missed Better Portals server view transfer");
            }
        } catch (Throwable exception) {
            if (GpomEarlyConfig.optimizationInfoLogsEnabled() && GPOM_LOGGED.add("repair-missed-transfer-failed")) {
                GPOM.LOGGER.warn("[GPOM BetterPortals Guard] Failed to repair missed Better Portals server view transfer: {}",
                        exception.toString());
            }
        }
    }

    @Unique
    private static World gpom$playerWorld(EntityPlayerMP player) {
        Field field = gpom$entityWorldField;
        if (field == null) {
            field = gpom$findEntityWorldField(player.getClass());
            gpom$entityWorldField = field;
        }
        if (field == null) {
            return null;
        }
        try {
            Object value = field.get(player);
            return value instanceof World ? (World) value : null;
        } catch (IllegalAccessException | RuntimeException exception) {
            return null;
        }
    }

    @Unique
    private static Field gpom$findEntityWorldField(Class<?> type) {
        String[] names = {"field_70170_p", "world"};
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            for (String name : names) {
                try {
                    Field field = current.getDeclaredField(name);
                    if (World.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        return field;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (RuntimeException exception) {
                    return null;
                }
            }
        }
        return null;
    }

    @Unique
    private Object gpom$createWorldManager(WorldServer world, EntityPlayerMP player) {
        try {
            ClassLoader loader = getClass().getClassLoader();
            Class<?> worldsManagerClass = Class.forName(GPOM_BP_WORLDS_MANAGER_CLASS, false, loader);
            Class<?> worldManagerClass = Class.forName(GPOM_BP_WORLD_MANAGER_CLASS, false, loader);
            Constructor<?> constructor = worldManagerClass.getConstructor(worldsManagerClass, WorldServer.class, EntityPlayerMP.class);
            return constructor.newInstance(this, world, player);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Unable to construct Better Portals ServerWorldManager", exception);
        }
    }
}
