package com.l.gpom.compat.hei;

import com.l.gpom.compat.minecraft.MinecraftMappingCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Uses JER's proven 1.12 entity renderer without linking GPOM to JER at class-load time. */
final class WootMobRenderCompat {
    private static final String JER_RENDER_HELPER = "jeresources.util.RenderHelper";
    private static final Map<String, EntityLivingBase> ENTITIES = new HashMap<>();
    private static final Set<String> FAILED_ENTITIES = new HashSet<>();

    private static World cachedWorld;
    private static Method scissor;
    private static Method renderEntity;
    private static Method stopScissor;
    private static boolean rendererUnavailable;

    private WootMobRenderCompat() {
    }

    static void render(Minecraft minecraft, String mobName, int mouseX, int mouseY) {
        if (minecraft == null || mobName == null || rendererUnavailable || FAILED_ENTITIES.contains(mobName)) {
            return;
        }
        World world = MinecraftMappingCompat.minecraftWorld(minecraft);
        if (world == null) {
            return;
        }
        try {
            ensureMethods();
            EntityLivingBase entity = entity(world, mobName);
            if (entity == null) {
                return;
            }

            float width = Math.max(0.25F, MinecraftMappingCompat.entityWidth(entity));
            float height = Math.max(0.25F, MinecraftMappingCompat.entityHeight(entity));
            float scale = Math.max(3.0F, Math.min(20.0F, Math.min(20.0F / width, 22.0F / height)));

            scissor.invoke(null, minecraft, 128, 0, 26, 24);
            try {
                renderEntity.invoke(null, 142, 23, scale,
                        142.0F - mouseX, 17.0F - mouseY, entity);
            } finally {
                stopScissor.invoke(null);
            }
        } catch (Throwable throwable) {
            FAILED_ENTITIES.add(mobName);
            WootJeiDiagnostics.error("Could not render JER-style entity preview for " + mobName, throwable);
        }
    }

    private static void ensureMethods() throws ReflectiveOperationException {
        if (renderEntity != null) {
            return;
        }
        try {
            Class<?> helper = Class.forName(JER_RENDER_HELPER, true,
                    WootMobRenderCompat.class.getClassLoader());
            scissor = helper.getMethod("scissor", Minecraft.class,
                    int.class, int.class, int.class, int.class);
            renderEntity = helper.getMethod("renderEntity", int.class, int.class,
                    float.class, float.class, float.class, EntityLivingBase.class);
            stopScissor = helper.getMethod("stopScissor");
        } catch (ReflectiveOperationException exception) {
            rendererUnavailable = true;
            throw exception;
        }
    }

    private static EntityLivingBase entity(World world, String mobName) {
        if (cachedWorld != world) {
            cachedWorld = world;
            ENTITIES.clear();
            FAILED_ENTITIES.clear();
        }
        EntityLivingBase cached = ENTITIES.get(mobName);
        if (cached != null) {
            return cached;
        }
        Entity entity = MinecraftMappingCompat.createEntityById(new ResourceLocation(mobName), world);
        if (!(entity instanceof EntityLivingBase)) {
            FAILED_ENTITIES.add(mobName);
            return null;
        }
        EntityLivingBase living = (EntityLivingBase) entity;
        ENTITIES.put(mobName, living);
        return living;
    }
}
