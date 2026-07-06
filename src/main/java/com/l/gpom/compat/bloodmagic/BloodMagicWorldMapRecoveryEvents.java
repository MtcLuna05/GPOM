package com.l.gpom.compat.bloodmagic;

import com.l.gpom.GPOM;
import com.l.gpom.util.ReflectionLookup;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public final class BloodMagicWorldMapRecoveryEvents {
    private static final BloodMagicWorldMapRecoveryEvents INSTANCE = new BloodMagicWorldMapRecoveryEvents();

    private static boolean registered;
    private static volatile boolean disabled;
    private static volatile Field bounceMapMapField;
    private static volatile Field filledHandMapMapField;
    private static volatile Field entityWorldField;
    private static volatile Method entityWorldMethod;
    private static int repairLogCount;

    private BloodMagicWorldMapRecoveryEvents() {
    }

    public static void register() {
        if (registered || !Loader.isModLoaded("bloodmagic")) {
            return;
        }
        registered = true;
        MinecraftForge.EVENT_BUS.register(INSTANCE);
        GPOM.LOGGER.info("[BloodMagic Recovery] Registered per-world map recovery");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void worldLoad(WorldEvent.Load event) {
        if (event != null) {
            ensureWorldMaps(event.getWorld(), "world load");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event != null && event.player != null) {
            try {
                ensureWorldMaps(entityWorld(event.player), "player tick");
            } catch (Throwable throwable) {
                disabled = true;
                GPOM.LOGGER.warn("[BloodMagic Recovery] Disabled per-world map recovery after player world lookup failure", throwable);
            }
        }
    }

    private static void ensureWorldMaps(World world, String source) {
        if (disabled || world == null) {
            return;
        }
        try {
            boolean repaired = false;
            repaired |= ensureMapEntry(field("bounceMapMap"), world);
            repaired |= ensureMapEntry(field("filledHandMapMap"), world);
            if (repaired && repairLogCount++ < 8) {
                GPOM.LOGGER.info("[BloodMagic Recovery] Recreated missing Blood Magic world maps during {}", source);
            }
        } catch (Throwable throwable) {
            disabled = true;
            GPOM.LOGGER.warn("[BloodMagic Recovery] Disabled per-world map recovery after failure", throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean ensureMapEntry(Field field, World world) throws ReflectiveOperationException {
        Map<Object, Object> root = (Map<Object, Object>) field.get(null);
        if (root == null) {
            root = new HashMap<>();
            field.set(null, root);
        }
        synchronized (root) {
            if (root.containsKey(world)) {
                return false;
            }
            root.put(world, new HashMap<>());
            return true;
        }
    }

    private static Field field(String name) throws ReflectiveOperationException {
        if ("bounceMapMap".equals(name)) {
            Field field = bounceMapMapField;
            if (field == null) {
                field = genericHandlerClass().getField(name);
                field.setAccessible(true);
                bounceMapMapField = field;
            }
            return field;
        }
        Field field = filledHandMapMapField;
        if (field == null) {
            field = genericHandlerClass().getField(name);
            field.setAccessible(true);
            filledHandMapMapField = field;
        }
        return field;
    }

    private static World entityWorld(Object entity) throws ReflectiveOperationException {
        try {
            Method method = entityWorldMethod;
            if (method == null) {
                method = findMethod(entity.getClass(), "func_130014_f_", "getEntityWorld");
                method.setAccessible(true);
                entityWorldMethod = method;
            }
            Object value = method.invoke(entity);
            if (value instanceof World) {
                return (World) value;
            }
        } catch (ReflectiveOperationException ignored) {
        }
        Field field = entityWorldField;
        if (field == null) {
            field = findField(entity.getClass(), "field_70170_p", "world");
            field.setAccessible(true);
            entityWorldField = field;
        }
        Object value = field.get(entity);
        return value instanceof World ? (World) value : null;
    }

    private static Method findMethod(Class<?> type, String... names) throws NoSuchMethodException {
        return ReflectionLookup.findMethod(type, names);
    }

    private static Field findField(Class<?> type, String... names) throws NoSuchFieldException {
        return ReflectionLookup.findField(type, names);
    }

    private static Class<?> genericHandlerClass() throws ClassNotFoundException {
        return Class.forName("WayofTime.bloodmagic.util.handler.event.GenericHandler");
    }
}
