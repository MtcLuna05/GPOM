package com.l.gpom.compat.torohealth;

import com.l.gpom.client.ClientAccess;
import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class ToroHealthCombatHealthClient {
    private static volatile Field toroProxyField;
    private static volatile Field toroGuiField;
    private static volatile Method toroSetEntityMethod;

    private ToroHealthCombatHealthClient() {
    }

    public static void apply(int entityId, float health, float maxHealth) {
        Minecraft minecraft = ClientAccess.minecraft();
        Runnable task = () -> applyNow(minecraft, entityId, health, maxHealth);
        if (!ClientAccess.schedule(minecraft, task)) {
            task.run();
        }
    }

    private static void applyNow(Minecraft minecraft, int entityId, float health, float maxHealth) {
        if (minecraft == null || entityId < 0 || Float.isNaN(health)) {
            return;
        }
        Object worldValue = MinecraftMappingCompat.fieldValue(minecraft, "minecraft.world", "field_71441_e", "world");
        if (!(worldValue instanceof World)) {
            return;
        }
        Entity entity = MinecraftMappingCompat.worldEntityById((World) worldValue, entityId);
        if (!(entity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        float localMax = MinecraftMappingCompat.livingMaxHealth(living);
        float limit = maxHealth > 0.0F ? Math.max(localMax, maxHealth) : localMax;
        MinecraftMappingCompat.livingSetHealth(living, Math.max(0.0F, Math.min(health, limit)));
        updateToroHealthTarget(living);
    }

    private static void updateToroHealthTarget(EntityLivingBase entity) {
        try {
            Object proxy = toroProxy();
            if (proxy == null) {
                return;
            }
            Object gui = toroGui(proxy);
            if (gui == null) {
                return;
            }
            Method method = toroSetEntityMethod;
            if (method == null || !method.getDeclaringClass().isAssignableFrom(gui.getClass())) {
                method = gui.getClass().getMethod("setEntity", EntityLivingBase.class);
                method.setAccessible(true);
                toroSetEntityMethod = method;
            }
            method.invoke(gui, entity);
        } catch (Throwable ignored) {
        }
    }

    private static Object toroProxy() throws ReflectiveOperationException {
        Field field = toroProxyField;
        if (field == null) {
            Class<?> mod = Class.forName("net.torocraft.torohealthmod.ToroHealthMod", false,
                    ToroHealthCombatHealthClient.class.getClassLoader());
            field = mod.getField("proxy");
            field.setAccessible(true);
            toroProxyField = field;
        }
        return field.get(null);
    }

    private static Object toroGui(Object proxy) throws ReflectiveOperationException {
        Field field = toroGuiField;
        if (field == null || !field.getDeclaringClass().isAssignableFrom(proxy.getClass())) {
            field = findField(proxy.getClass(), "entityStatusGUI");
            if (field == null) {
                return null;
            }
            field.setAccessible(true);
            toroGuiField = field;
        }
        return field.get(proxy);
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
