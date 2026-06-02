package com.l.cleanroomoptimizations.profiling;

import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityRegistry;

public final class BetweenlandsOptimizations {
    private static final String MOD_ID = "thebetweenlands";
    private static Object betweenlandsInstance;

    private BetweenlandsOptimizations() {
    }

    public static void registerModEntity(Class<? extends Entity> entityClass, String name, int id, int range, int updateFrequency, boolean sendVelocityUpdates) {
        EntityRegistry.registerModEntity(
                new ResourceLocation(MOD_ID, name),
                entityClass,
                MOD_ID + '.' + name,
                id,
                betweenlandsInstance(),
                range,
                updateFrequency,
                sendVelocityUpdates
        );
    }

    public static void registerEgg(String name, int primaryColor, int secondaryColor) {
        EntityRegistry.registerEgg(new ResourceLocation(MOD_ID, name), primaryColor, secondaryColor);
    }

    private static Object betweenlandsInstance() {
        Object instance = betweenlandsInstance;
        if (instance != null) {
            return instance;
        }

        try {
            instance = Class.forName("thebetweenlands.common.TheBetweenlands").getField("instance").get(null);
            betweenlandsInstance = instance;
            return instance;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
