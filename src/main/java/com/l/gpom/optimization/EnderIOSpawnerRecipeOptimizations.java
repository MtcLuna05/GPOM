package com.l.gpom.optimization;

import com.enderio.core.common.util.NNList;
import com.l.gpom.GPOM;
import com.l.gpom.core.TargetedModVersions;
import crazypants.enderio.base.config.recipes.xml.Entity;
import crazypants.enderio.util.CapturedMob;
import net.minecraft.util.ResourceLocation;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class EnderIOSpawnerRecipeOptimizations {
    private static final boolean FAST_ENTITY_VALIDATION = Boolean.parseBoolean(System.getProperty("gpom.enderio.fastSpawnerEntityValidation", "true"));
    private static final boolean TARGET_ENDERIO = TargetedModVersions.isEnderIOClass("crazypants.enderio.base.config.recipes.xml.Entity");
    private static final Map<Entity, Boolean> ENTITY_VALID_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static volatile Field nameField;
    private static volatile Field filterField;
    private static volatile ResourceLocation[] soulNames;
    private static volatile Set<ResourceLocation> soulNameSet;

    private EnderIOSpawnerRecipeOptimizations() {
    }

    public static boolean isEntityValid(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!FAST_ENTITY_VALIDATION || !TARGET_ENDERIO) {
            return isEntityValidUncached(entity);
        }

        Boolean cached;
        synchronized (ENTITY_VALID_CACHE) {
            cached = ENTITY_VALID_CACHE.get(entity);
        }
        if (cached != null) {
            return cached;
        }

        boolean valid = isEntityValidUncached(entity);
        synchronized (ENTITY_VALID_CACHE) {
            ENTITY_VALID_CACHE.put(entity, valid);
        }
        return valid;
    }

    private static boolean isEntityValidUncached(Entity entity) {
        if (entity.isDefault() || entity.isBoss()) {
            return true;
        }

        String name = entityName(entity);
        if (name != null && name.indexOf('*') < 0) {
            try {
                return soulNameSet().contains(new ResourceLocation(name.trim()));
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        Predicate<ResourceLocation> filter = entityFilter(entity);
        if (filter == null) {
            return false;
        }
        for (ResourceLocation soulName : soulNames()) {
            if (filter.test(soulName)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String entityName(Entity entity) {
        try {
            Optional<String> value = (Optional<String>) nameField().get(entity);
            return value.orElse(null);
        } catch (Throwable throwable) {
            warnOnce("read EnderIO spawner entity name", throwable);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Predicate<ResourceLocation> entityFilter(Entity entity) {
        try {
            return (Predicate<ResourceLocation>) filterField().get(entity);
        } catch (Throwable throwable) {
            warnOnce("read EnderIO spawner entity filter", throwable);
            return null;
        }
    }

    private static Set<ResourceLocation> soulNameSet() {
        Set<ResourceLocation> names = soulNameSet;
        if (names != null) {
            return names;
        }
        buildSoulNameCache();
        return soulNameSet;
    }

    private static ResourceLocation[] soulNames() {
        ResourceLocation[] names = soulNames;
        if (names != null) {
            return names;
        }
        buildSoulNameCache();
        return soulNames;
    }

    private static void buildSoulNameCache() {
        synchronized (EnderIOSpawnerRecipeOptimizations.class) {
            if (soulNames != null && soulNameSet != null) {
                return;
            }

            NNList<CapturedMob> souls = CapturedMob.getAllSouls();
            List<ResourceLocation> names = new ArrayList<>(souls.size());
            for (CapturedMob soul : souls) {
                ResourceLocation name = soul.getEntityName();
                if (name != null) {
                    names.add(name);
                }
            }
            Set<ResourceLocation> uniqueNames = new HashSet<>(names);
            soulNames = names.toArray(new ResourceLocation[0]);
            soulNameSet = Collections.unmodifiableSet(uniqueNames);
        }
    }

    private static Field nameField() throws NoSuchFieldException {
        Field field = nameField;
        if (field == null) {
            field = Entity.class.getDeclaredField("name");
            field.setAccessible(true);
            nameField = field;
        }
        return field;
    }

    private static Field filterField() throws NoSuchFieldException {
        Field field = filterField;
        if (field == null) {
            field = Entity.class.getDeclaredField("filter");
            field.setAccessible(true);
            filterField = field;
        }
        return field;
    }

    private static void warnOnce(String operation, Throwable throwable) {
        if (WARNED.compareAndSet(false, true)) {
            GPOM.LOGGER.warn("[EnderIO Optimizations] Could not {}; spawner entity validation fast path will be conservative", operation, throwable);
        }
    }
}
